package com.tekmachain.exchange.service;

import com.tekmachain.exchange.Money;
import com.tekmachain.exchange.Pairs;
import com.tekmachain.exchange.domain.*;
import com.tekmachain.exchange.repo.OrderRepository;
import com.tekmachain.exchange.repo.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * A price-time-priority matching engine — the same core model a real exchange uses.
 *
 * <p>A taker order is matched against the best resting orders on the opposite side.
 * Trades execute at the <em>maker's</em> price (the resting order that was there
 * first). LIMIT orders reserve funds up front and rest on the book if not fully
 * filled; MARKET orders spend available funds and never rest. Every fill moves
 * base (TEK) and quote (USD/INR) between the two parties' exchange balances.</p>
 */
@Service
public class MatchingEngine {

    private static final List<OrderStatus> RESTING = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);
    private static final String BASE = Pairs.BASE; // TEK

    private final OrderRepository orders;
    private final TradeRepository trades;
    private final BalanceService balances;
    private final ExchangeLock lock;

    public MatchingEngine(OrderRepository orders, TradeRepository trades,
                          BalanceService balances, ExchangeLock lock) {
        this.orders = orders;
        this.trades = trades;
        this.balances = balances;
        this.lock = lock;
    }

    @Transactional
    public Order place(String address, String pair, Side side, OrderType type,
                       BigDecimal priceRaw, BigDecimal amountRaw) {
        synchronized (lock) {
            return doPlace(address, pair, side, type, priceRaw, amountRaw);
        }
    }

    private Order doPlace(String address, String pair, Side side, OrderType type,
                          BigDecimal priceRaw, BigDecimal amountRaw) {
        if (!Pairs.isSupported(pair)) {
            throw new IllegalArgumentException("Unsupported pair: " + pair);
        }
        BigDecimal amount = Money.norm(amountRaw);
        if (!Money.gtZero(amount)) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal price = null;
        if (type == OrderType.LIMIT) {
            price = Money.norm(priceRaw);
            if (!Money.gtZero(price)) {
                throw new IllegalArgumentException("Limit price must be positive");
            }
        }
        String quote = Pairs.quoteOf(pair);

        Order order = new Order();
        order.setAddress(address);
        order.setPair(pair);
        order.setSide(side);
        order.setType(type);
        order.setPrice(price);
        order.setAmount(amount);
        order.setCreatedAt(System.currentTimeMillis());
        order.setUpdatedAt(order.getCreatedAt());

        // Reserve funds for LIMIT orders.
        if (type == OrderType.LIMIT) {
            if (side == Side.BUY) {
                BigDecimal lockQuote = Money.norm(price.multiply(amount));
                balances.lock(address, quote, lockQuote);
                order.setLockedAmount(lockQuote);
            } else {
                balances.lock(address, BASE, amount);
                order.setLockedAmount(amount);
            }
        }
        order = orders.save(order);

        // Walk the opposite side of the book.
        List<Order> book = (side == Side.BUY)
                ? orders.findByPairAndSideAndStatusInOrderByPriceAscCreatedAtAsc(pair, Side.SELL, RESTING)
                : orders.findByPairAndSideAndStatusInOrderByPriceDescCreatedAtAsc(pair, Side.BUY, RESTING);

        for (Order maker : book) {
            if (order.remaining().signum() <= 0) {
                break;
            }
            if (type == OrderType.LIMIT) {
                boolean crosses = (side == Side.BUY)
                        ? maker.getPrice().compareTo(price) <= 0
                        : maker.getPrice().compareTo(price) >= 0;
                if (!crosses) {
                    break; // book is sorted; nothing better remains
                }
            }
            fill(order, maker, quote);
        }

        finalizeTaker(order, quote);
        return orders.save(order);
    }

    /** Execute as large a trade as possible between taker {@code order} and {@code maker}. */
    private void fill(Order order, Order maker, String quote) {
        BigDecimal execPrice = maker.getPrice();
        BigDecimal qty = order.remaining().min(maker.remaining());

        // MARKET takers are limited by their available funds.
        if (order.getType() == OrderType.MARKET) {
            if (order.getSide() == Side.BUY) {
                BigDecimal avail = balances.available(order.getAddress(), quote);
                BigDecimal affordable = avail.divide(execPrice, 8, RoundingMode.DOWN);
                qty = qty.min(affordable);
            } else {
                qty = qty.min(balances.available(order.getAddress(), BASE));
            }
            qty = Money.norm(qty);
            if (qty.signum() <= 0) {
                order.setStatus(OrderStatus.CANCELLED); // out of funds -> stop
                return;
            }
        }

        BigDecimal quoteAmt = Money.norm(execPrice.multiply(qty));

        if (order.getSide() == Side.BUY) {
            settleBuyerTaker(order, quote, qty, quoteAmt, execPrice);
            // maker is a SELL: delivers base from locked, receives quote
            balances.spendLocked(maker.getAddress(), BASE, qty);
            maker.setLockedAmount(Money.norm(maker.getLockedAmount().subtract(qty)));
            balances.credit(maker.getAddress(), quote, quoteAmt);
        } else {
            settleSellerTaker(order, quote, qty, quoteAmt);
            // maker is a BUY: pays quote from locked, receives base
            balances.spendLocked(maker.getAddress(), quote, quoteAmt);
            maker.setLockedAmount(Money.norm(maker.getLockedAmount().subtract(quoteAmt)));
            balances.credit(maker.getAddress(), BASE, qty);
        }

        order.setFilled(Money.norm(order.getFilled().add(qty)));
        maker.setFilled(Money.norm(maker.getFilled().add(qty)));
        maker.setStatus(maker.remaining().signum() <= 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
        maker.setUpdatedAt(System.currentTimeMillis());
        orders.save(maker);

        recordTrade(order, maker, execPrice, qty, quoteAmt);
    }

    private void settleBuyerTaker(Order order, String quote, BigDecimal qty,
                                  BigDecimal quoteAmt, BigDecimal execPrice) {
        if (order.getType() == OrderType.LIMIT) {
            balances.spendLocked(order.getAddress(), quote, quoteAmt);
            // refund the price improvement (locked at limit price, filled cheaper)
            BigDecimal improvement = Money.norm(order.getPrice().subtract(execPrice).multiply(qty));
            if (improvement.signum() > 0) {
                balances.unlock(order.getAddress(), quote, improvement);
            }
            order.setLockedAmount(Money.norm(order.getLockedAmount().subtract(quoteAmt.add(improvement))));
        } else {
            balances.spendAvailable(order.getAddress(), quote, quoteAmt);
        }
        balances.credit(order.getAddress(), BASE, qty);
    }

    private void settleSellerTaker(Order order, String quote, BigDecimal qty, BigDecimal quoteAmt) {
        if (order.getType() == OrderType.LIMIT) {
            balances.spendLocked(order.getAddress(), BASE, qty);
            order.setLockedAmount(Money.norm(order.getLockedAmount().subtract(qty)));
        } else {
            balances.spendAvailable(order.getAddress(), BASE, qty);
        }
        balances.credit(order.getAddress(), quote, quoteAmt);
    }

    private void finalizeTaker(Order order, String quote) {
        order.setUpdatedAt(System.currentTimeMillis());
        if (order.remaining().signum() <= 0) {
            order.setStatus(OrderStatus.FILLED);
            return;
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return; // MARKET ran out of funds mid-fill
        }
        if (order.getType() == OrderType.LIMIT) {
            order.setStatus(order.getFilled().signum() > 0 ? OrderStatus.PARTIALLY_FILLED : OrderStatus.OPEN);
        } else {
            // A MARKET order never rests: any unfilled remainder is cancelled.
            order.setStatus(order.getFilled().signum() > 0 ? OrderStatus.CANCELLED : OrderStatus.REJECTED);
        }
    }

    private void recordTrade(Order taker, Order maker, BigDecimal price, BigDecimal qty, BigDecimal quoteAmt) {
        Trade t = new Trade();
        t.setPair(taker.getPair());
        t.setPrice(price);
        t.setAmount(qty);
        t.setQuoteAmount(quoteAmt);
        t.setTakerSide(taker.getSide());
        t.setTakerAddress(taker.getAddress());
        t.setMakerAddress(maker.getAddress());
        t.setBuyOrderId(taker.getSide() == Side.BUY ? taker.getId() : maker.getId());
        t.setSellOrderId(taker.getSide() == Side.SELL ? taker.getId() : maker.getId());
        t.setCreatedAt(System.currentTimeMillis());
        trades.save(t);
    }

    @Transactional
    public Order cancel(String address, Long orderId) {
        synchronized (lock) {
            Order order = orders.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("No such order"));
            if (!order.getAddress().equals(address)) {
                throw new IllegalArgumentException("Order does not belong to this account");
            }
            if (!order.getStatus().isRestingOnBook()) {
                throw new IllegalStateException("Order is not open");
            }
            String quote = Pairs.quoteOf(order.getPair());
            if (order.getLockedAmount().signum() > 0) {
                balances.unlock(address, order.getSide() == Side.BUY ? quote : BASE, order.getLockedAmount());
            }
            order.setLockedAmount(BigDecimal.ZERO);
            order.setStatus(OrderStatus.CANCELLED);
            order.setUpdatedAt(System.currentTimeMillis());
            return orders.save(order);
        }
    }
}

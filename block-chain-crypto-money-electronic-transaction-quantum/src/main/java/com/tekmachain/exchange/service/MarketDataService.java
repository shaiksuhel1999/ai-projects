package com.tekmachain.exchange.service;

import com.tekmachain.exchange.Pairs;
import com.tekmachain.exchange.domain.Order;
import com.tekmachain.exchange.domain.OrderStatus;
import com.tekmachain.exchange.domain.Side;
import com.tekmachain.exchange.domain.Trade;
import com.tekmachain.exchange.dto.ExDtos;
import com.tekmachain.exchange.repo.OrderRepository;
import com.tekmachain.exchange.repo.TradeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only market views: the order book, recent trades, and a 24h ticker. */
@Service
public class MarketDataService {

    private static final List<OrderStatus> RESTING = List.of(OrderStatus.OPEN, OrderStatus.PARTIALLY_FILLED);

    private final OrderRepository orders;
    private final TradeRepository trades;

    public MarketDataService(OrderRepository orders, TradeRepository trades) {
        this.orders = orders;
        this.trades = trades;
    }

    @Transactional(readOnly = true)
    public ExDtos.OrderBookDto orderBook(String pair) {
        List<Order> resting = orders.findByPairAndStatusIn(pair, RESTING);

        List<ExDtos.BookLevel> bids = aggregate(resting, Side.BUY, true);
        List<ExDtos.BookLevel> asks = aggregate(resting, Side.SELL, false);

        BigDecimal bestBid = bids.isEmpty() ? null : bids.get(0).price();
        BigDecimal bestAsk = asks.isEmpty() ? null : asks.get(0).price();
        BigDecimal spread = (bestBid != null && bestAsk != null) ? bestAsk.subtract(bestBid) : null;
        return new ExDtos.OrderBookDto(pair, bids, asks, bestBid, bestAsk, spread);
    }

    private List<ExDtos.BookLevel> aggregate(List<Order> resting, Side side, boolean descending) {
        Map<BigDecimal, BigDecimal> byPrice = new LinkedHashMap<>();
        for (Order o : resting) {
            if (o.getSide() == side) {
                byPrice.merge(o.getPrice(), o.remaining(), BigDecimal::add);
            }
        }
        List<BigDecimal> prices = new ArrayList<>(byPrice.keySet());
        prices.sort(descending ? (a, b) -> b.compareTo(a) : BigDecimal::compareTo);

        List<ExDtos.BookLevel> levels = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (BigDecimal p : prices) {
            BigDecimal amt = byPrice.get(p);
            cumulative = cumulative.add(amt);
            levels.add(new ExDtos.BookLevel(p, amt, cumulative));
        }
        return levels;
    }

    @Transactional(readOnly = true)
    public List<ExDtos.TradeDto> recentTrades(String pair) {
        return trades.findTop50ByPairOrderByCreatedAtDesc(pair).stream().map(ExDtos::trade).toList();
    }

    @Transactional(readOnly = true)
    public ExDtos.TickerDto ticker(String pair) {
        long since = System.currentTimeMillis() - 24L * 3600 * 1000;
        List<Trade> window = trades.findByPairAndCreatedAtGreaterThanEqual(pair, since);

        ExDtos.OrderBookDto book = orderBook(pair);
        BigDecimal last = null, high = null, low = null, baseVol = BigDecimal.ZERO, quoteVol = BigDecimal.ZERO, first = null;

        // window is unordered; track by createdAt
        long lastTs = Long.MIN_VALUE, firstTs = Long.MAX_VALUE;
        for (Trade t : window) {
            if (t.getCreatedAt() >= lastTs) { lastTs = t.getCreatedAt(); last = t.getPrice(); }
            if (t.getCreatedAt() <= firstTs) { firstTs = t.getCreatedAt(); first = t.getPrice(); }
            high = (high == null || t.getPrice().compareTo(high) > 0) ? t.getPrice() : high;
            low = (low == null || t.getPrice().compareTo(low) < 0) ? t.getPrice() : low;
            baseVol = baseVol.add(t.getAmount());
            quoteVol = quoteVol.add(t.getQuoteAmount());
        }
        if (last == null) { // no trades in 24h: fall back to the most recent ever
            List<Trade> recent = trades.findTop50ByPairOrderByCreatedAtDesc(pair);
            if (!recent.isEmpty()) last = recent.get(0).getPrice();
        }
        BigDecimal changePct = (first != null && last != null && first.signum() > 0)
                ? last.subtract(first).divide(first, 6, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        return new ExDtos.TickerDto(pair, last, changePct, high, low, baseVol, quoteVol,
                book.bestBid(), book.bestAsk());
    }

    public List<String> pairs() {
        return Pairs.SUPPORTED;
    }
}

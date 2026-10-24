package com.tekmachain;

import com.tekmachain.domain.Wallet;
import com.tekmachain.exchange.domain.Order;
import com.tekmachain.exchange.domain.OrderStatus;
import com.tekmachain.exchange.domain.OrderType;
import com.tekmachain.exchange.domain.Side;
import com.tekmachain.exchange.service.BalanceService;
import com.tekmachain.exchange.service.ExchangeService;
import com.tekmachain.exchange.service.MarketDataService;
import com.tekmachain.exchange.service.MatchingEngine;
import com.tekmachain.service.MiningService;
import com.tekmachain.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the exchange: the price-time matching engine (fills, price improvement,
 * market orders, cancels) and the on-chain deposit/withdraw bridge.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ExchangeTest {

    private static final String PAIR = "TEK/USD";

    @Autowired MatchingEngine engine;
    @Autowired MarketDataService market;
    @Autowired BalanceService balances;
    @Autowired ExchangeService exchange;
    @Autowired WalletService wallets;
    @Autowired MiningService mining;

    private static BigDecimal n(String v) { return new BigDecimal(v); }
    private BigDecimal avail(String a, String asset) { return balances.available(a, asset); }
    private BigDecimal locked(String a, String asset) { return balances.get(a, asset).getLocked(); }
    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, n(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void limitOrdersMatchAndSettle() {
        balances.credit("alice", "TEK", n("100"));
        balances.credit("bob", "USD", n("10000"));

        Order sell = engine.place("alice", PAIR, Side.SELL, OrderType.LIMIT, n("50"), n("10"));
        assertEquals(OrderStatus.OPEN, sell.getStatus());
        eq("10", locked("alice", "TEK"));      // 10 TEK reserved
        eq("90", avail("alice", "TEK"));

        Order buy = engine.place("bob", PAIR, Side.BUY, OrderType.LIMIT, n("50"), n("10"));
        assertEquals(OrderStatus.FILLED, buy.getStatus());

        eq("500", avail("alice", "USD"));       // Alice sold 10 @ 50 = 500 USD
        eq("0", locked("alice", "TEK"));
        eq("90", avail("alice", "TEK"));
        eq("10", avail("bob", "TEK"));         // Bob received 10 TEK
        eq("9500", avail("bob", "USD"));        // 10000 - 500
        eq("0", locked("bob", "USD"));
        assertTrue(market.orderBook(PAIR).asks().isEmpty(), "book cleared");
        eq("50", market.ticker(PAIR).lastPrice());
    }

    @Test
    void takerGetsMakerPriceAndRefund() {
        balances.credit("alice", "TEK", n("10"));
        balances.credit("bob", "USD", n("10000"));

        engine.place("alice", PAIR, Side.SELL, OrderType.LIMIT, n("50"), n("10"));   // ask 50
        Order buy = engine.place("bob", PAIR, Side.BUY, OrderType.LIMIT, n("60"), n("10")); // bid 60

        assertEquals(OrderStatus.FILLED, buy.getStatus());
        eq("10", avail("bob", "TEK"));
        eq("9500", avail("bob", "USD"));        // charged 500 (maker price 50), refunded the 100 improvement
        eq("0", locked("bob", "USD"));
        eq("500", avail("alice", "USD"));
    }

    @Test
    void marketBuyIsBoundedByAvailableFunds() {
        balances.credit("alice", "TEK", n("10"));
        balances.credit("carol", "USD", n("300"));

        engine.place("alice", PAIR, Side.SELL, OrderType.LIMIT, n("50"), n("10"));    // ask 50, size 10
        Order mkt = engine.place("carol", PAIR, Side.BUY, OrderType.MARKET, null, n("10"));

        // Carol can only afford 6 TEK (300 / 50); the rest is cancelled.
        eq("6", mkt.getFilled());
        assertEquals(OrderStatus.CANCELLED, mkt.getStatus());
        eq("6", avail("carol", "TEK"));
        eq("0", avail("carol", "USD"));
    }

    @Test
    void cancelReleasesLockedFunds() {
        balances.credit("alice", "TEK", n("10"));
        Order sell = engine.place("alice", PAIR, Side.SELL, OrderType.LIMIT, n("50"), n("10"));
        eq("10", locked("alice", "TEK"));

        Order cancelled = engine.cancel("alice", sell.getId());
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        eq("0", locked("alice", "TEK"));
        eq("10", avail("alice", "TEK"));
    }

    @Test
    void placingWithoutFundsIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> engine.place("broke", PAIR, Side.SELL, OrderType.LIMIT, n("50"), n("10")));
    }

    @Test
    void depositAndWithdrawBridgeChainAndExchange() {
        Wallet alice = wallets.create("Alice");
        mining.mine(alice.getAddress());                    // Alice: 50 TEK on-chain

        exchange.depositTek(alice.getAddress(), n("20"));  // sign transfer to hot wallet
        mining.mine(alice.getAddress());                    // confirm it
        exchange.reconcileDeposits();                       // credit the exchange balance

        eq("20", avail(alice.getAddress(), "TEK"));

        String msg = exchange.withdrawTek(alice.getAddress(), n("5"));
        assertNotNull(msg);
        eq("15", avail(alice.getAddress(), "TEK"));        // debited immediately
    }
}

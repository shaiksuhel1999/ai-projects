package com.tekmachain;

import com.tekmachain.market.MarketDataFeedService;
import com.tekmachain.market.MarketPrice;
import com.tekmachain.market.MarketPriceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The crypto market feed caches real USD prices and records each coin's move since
 * the previous sync. Exercised through the unit-testable {@code applyUsdPrices} seam
 * so it needs no network (the live Coinbase fetch is disabled in tests).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class MarketFeedTest {

    @Autowired MarketDataFeedService market;
    @Autowired MarketPriceRepository repo;

    private MarketPrice coin(String code) {
        return repo.findById(code).orElseThrow();
    }

    @Test
    void seedsReferencePricesOnStartup() {
        assertFalse(repo.findAll().isEmpty(), "tracked coins are seeded so the table is never empty");
        assertEquals("Bitcoin", coin("BTC").getName());
    }

    @Test
    void appliesLivePricesAndComputesChangeSinceLastSync() {
        BigDecimal before = coin("BTC").getUsdPrice();
        assertTrue(before.signum() > 0);

        int updated = market.applyUsdPrices(Map.of(
                "BTC", new BigDecimal("70000"),
                "ETH", new BigDecimal("3600")));
        assertTrue(updated >= 2);

        MarketPrice btc = coin("BTC");
        assertEquals(0, new BigDecimal("70000").compareTo(btc.getUsdPrice()), "price updated to the live value");

        // change% = (70000 - seed 65000) / 65000 * 100 ~= +7.6923%
        BigDecimal expected = new BigDecimal("70000").subtract(before)
                .divide(before, 8, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, java.math.RoundingMode.HALF_UP);
        assertEquals(0, expected.compareTo(btc.getChangePct()), "records the move since the previous sync");
        assertTrue(btc.getChangePct().signum() > 0, "price rose, so change is positive");
    }

    @Test
    void ignoresUnknownAndNonPositivePrices() {
        int updated = market.applyUsdPrices(Map.of(
                "BTC", new BigDecimal("66000"),
                "NOPE", new BigDecimal("123"),
                "ETH", new BigDecimal("-1")));
        assertEquals(1, updated, "only the known, positive price counts");
    }
}

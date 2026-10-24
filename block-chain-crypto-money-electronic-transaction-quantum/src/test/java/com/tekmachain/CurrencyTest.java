package com.tekmachain;

import com.tekmachain.currency.CurrencyService;
import com.tekmachain.currency.FxRateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The world-currency catalogue seeds from CSV and converts correctly through USD. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CurrencyTest {

    @Autowired CurrencyService currencies;
    @Autowired FxRateService fx;

    @Test
    void seedsManyCurrenciesAndConverts() {
        assertTrue(currencies.all().size() > 100, "should seed 100+ world currencies");
        assertEquals(0, BigDecimal.ONE.compareTo(currencies.require("USD").getUsdRate()), "USD is the base");
        assertTrue(currencies.exists("JPY") && currencies.exists("BRL") && currencies.exists("NGN"));

        // 100 USD -> INR at 83.2 = 8320
        BigDecimal inr = currencies.convert(new BigDecimal("100"), "USD", "INR");
        assertEquals(0, new BigDecimal("8320").compareTo(inr), "100 USD should be 8320 INR");

        // round-trip stays ~1 USD
        BigDecimal back = currencies.convert(inr, "INR", "USD");
        assertTrue(back.subtract(new BigDecimal("100")).abs().compareTo(new BigDecimal("0.01")) < 0);

        assertThrows(IllegalArgumentException.class, () -> currencies.require("ZZZ"));
    }

    @Test
    void liveFxFeedUpdatesRates() {
        // Simulate a feed pushing new USD rates; the table should reflect them.
        int updated = fx.applyUsdRates(Map.of("INR", new BigDecimal("90.5"), "EUR", new BigDecimal("0.95")));
        assertTrue(updated >= 2);
        assertEquals(0, new BigDecimal("90.5").compareTo(currencies.require("INR").getUsdRate()));
        assertEquals(0, new BigDecimal("0.95").compareTo(currencies.require("EUR").getUsdRate()));
        // Conversion now uses the live rate: 1 USD -> 90.5 INR
        assertEquals(0, new BigDecimal("90.5").compareTo(currencies.convert(BigDecimal.ONE, "USD", "INR")));
    }
}

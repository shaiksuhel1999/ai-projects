package com.tekmachain.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps the {@link Currency} table's USD rates fresh from a live FX feed
 * (default: open.er-api.com, no API key). Falls back gracefully to the seeded
 * rates when the network is unavailable, so the app always works offline.
 */
@Service
public class FxRateService {

    private static final Logger log = LoggerFactory.getLogger(FxRateService.class);

    private final CurrencyRepository currencies;
    private final RestClient http;

    @Value("${tekmachain.fx.enabled:true}")
    private boolean enabled;

    @Value("${tekmachain.fx.url:https://open.er-api.com/v6/latest/USD}")
    private String feedUrl;

    private volatile long lastSyncAt = 0;
    private volatile String source = "seed";   // "seed" until a live sync succeeds

    public FxRateService(CurrencyRepository currencies) {
        this.currencies = currencies;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(4));
        f.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    public record Status(boolean enabled, boolean live, String source, long lastSyncAt, String feedUrl) {
    }

    public Status status() {
        return new Status(enabled, "live".equals(source), source, lastSyncAt, feedUrl);
    }

    /** Refresh from the live feed. Returns how many currencies were updated (0 if unavailable). */
    public int refresh() {
        if (!enabled) {
            return 0;
        }
        Optional<Map<String, BigDecimal>> rates = fetchUsdRates();
        if (rates.isEmpty()) {
            log.info("FX feed unavailable — keeping current rates");
            return 0;
        }
        int updated = applyUsdRates(rates.get());
        lastSyncAt = System.currentTimeMillis();
        source = "live";
        log.info("FX feed: updated {} currency rates from {}", updated, feedUrl);
        return updated;
    }

    /** Applies a map of {@code code -> units-per-USD} to the currency table. Unit-testable. */
    @Transactional
    public int applyUsdRates(Map<String, BigDecimal> ratesPerUsd) {
        List<Currency> all = currencies.findAll();
        int updated = 0;
        long now = System.currentTimeMillis();
        for (Currency c : all) {
            BigDecimal r = ratesPerUsd.get(c.getCode());
            if (r != null && r.signum() > 0) {
                c.setUsdRate(r);
                c.setUpdatedAt(now);
                updated++;
            }
        }
        currencies.saveAll(all);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, BigDecimal>> fetchUsdRates() {
        try {
            Map<String, Object> body = http.get().uri(feedUrl).retrieve().body(Map.class);
            if (body == null || body.get("rates") == null) {
                return Optional.empty();
            }
            Map<String, Object> raw = (Map<String, Object>) body.get("rates");
            Map<String, BigDecimal> out = new java.util.HashMap<>();
            raw.forEach((k, v) -> {
                if (v instanceof Number num) {
                    out.put(k, BigDecimal.valueOf(num.doubleValue()));
                }
            });
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (RuntimeException e) {
            log.debug("FX fetch failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Pull rates shortly after startup, then on a schedule. */
    @Scheduled(initialDelayString = "${tekmachain.fx.initial-delay-ms:8000}",
            fixedDelayString = "${tekmachain.fx.refresh-ms:21600000}")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.debug("Scheduled FX refresh failed: {}", e.toString());
        }
    }
}

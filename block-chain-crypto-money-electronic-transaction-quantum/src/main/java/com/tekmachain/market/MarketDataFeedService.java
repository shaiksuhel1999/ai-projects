package com.tekmachain.market;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keeps a small table of real-world crypto prices (BTC, ETH, …) fresh from
 * Coinbase's public, no-key exchange-rates endpoint, on a schedule — a threshold
 * sync that gives the app genuine live-market data alongside the fictional Tekma
 * Coin. Like {@code FxRateService}, it degrades gracefully: if the network is
 * unavailable it keeps the last-known (or seeded reference) prices, so the app
 * always works offline. These prices are display-only and never tradable.
 */
@Service
public class MarketDataFeedService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataFeedService.class);

    /** The coins we track, with human names and a rough seed price for offline use. */
    private static final Map<String, String[]> TRACKED = new LinkedHashMap<>() {{
        put("BTC", new String[]{"Bitcoin", "65000"});
        put("ETH", new String[]{"Ethereum", "3400"});
        put("BNB", new String[]{"BNB", "600"});
        put("SOL", new String[]{"Solana", "150"});
        put("XRP", new String[]{"XRP", "0.55"});
        put("ADA", new String[]{"Cardano", "0.45"});
        put("DOGE", new String[]{"Dogecoin", "0.12"});
        put("AVAX", new String[]{"Avalanche", "28"});
        put("DOT", new String[]{"Polkadot", "6.5"});
        put("LINK", new String[]{"Chainlink", "14"});
        put("MATIC", new String[]{"Polygon", "0.60"});
        put("LTC", new String[]{"Litecoin", "70"});
    }};

    private final MarketPriceRepository repo;
    private final RestClient http;

    @Value("${tekmachain.market.enabled:true}")
    private boolean enabled;

    @Value("${tekmachain.market.url:https://api.coinbase.com/v2/exchange-rates?currency=USD}")
    private String feedUrl;

    private volatile long lastSyncAt = 0;
    private volatile String source = "seed";   // "seed" until a live sync succeeds

    public MarketDataFeedService(MarketPriceRepository repo) {
        this.repo = repo;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(4));
        f.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder().requestFactory(f).build();
    }

    /** Seed the tracked coins with reference prices the first time, so the table is never empty. */
    @PostConstruct
    @Transactional
    public void seed() {
        if (repo.count() > 0) {
            return;
        }
        List<MarketPrice> seed = TRACKED.entrySet().stream()
                .map(e -> new MarketPrice(e.getKey(), e.getValue()[0], new BigDecimal(e.getValue()[1])))
                .toList();
        repo.saveAll(seed);
        log.info("Seeded {} reference market prices", seed.size());
    }

    public record Status(boolean enabled, boolean live, String source, long lastSyncAt, String feedUrl) {
    }

    public Status status() {
        return new Status(enabled, "live".equals(source), source, lastSyncAt, feedUrl);
    }

    /** Refresh from the live feed. Returns how many prices were updated (0 if unavailable). */
    public int refresh() {
        if (!enabled) {
            return 0;
        }
        Optional<Map<String, BigDecimal>> prices = fetchUsdPrices();
        if (prices.isEmpty()) {
            log.info("Market feed unavailable — keeping current prices");
            return 0;
        }
        int updated = applyUsdPrices(prices.get());
        lastSyncAt = System.currentTimeMillis();
        source = "live";
        log.info("Market feed: updated {} crypto prices from Coinbase", updated);
        return updated;
    }

    /**
     * Applies a map of {@code code -> USD price} to the table, recording each coin's
     * move since the previous sync as {@code changePct}. Unit-testable (no network).
     */
    @Transactional
    public int applyUsdPrices(Map<String, BigDecimal> pricesUsd) {
        List<MarketPrice> all = repo.findAll();
        int updated = 0;
        long now = System.currentTimeMillis();
        for (MarketPrice m : all) {
            BigDecimal p = pricesUsd.get(m.getCode());
            if (p == null || p.signum() <= 0) {
                continue;
            }
            BigDecimal prev = m.getUsdPrice();
            if (prev != null && prev.signum() > 0) {
                m.setChangePct(p.subtract(prev)
                        .divide(prev, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP));
            }
            m.setUsdPrice(p);
            m.setUpdatedAt(now);
            updated++;
        }
        repo.saveAll(all);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, BigDecimal>> fetchUsdPrices() {
        try {
            Map<String, Object> body = http.get().uri(feedUrl).retrieve().body(Map.class);
            Object data = body == null ? null : body.get("data");
            if (!(data instanceof Map<?, ?> dataMap) || !(dataMap.get("rates") instanceof Map<?, ?> rates)) {
                return Optional.empty();
            }
            // Coinbase returns rates as "units of coin per 1 USD"; USD price = 1 / rate.
            Map<String, BigDecimal> out = new java.util.HashMap<>();
            for (String code : TRACKED.keySet()) {
                Object raw = rates.get(code);
                if (raw == null) {
                    continue;
                }
                BigDecimal rate = new BigDecimal(raw.toString());
                if (rate.signum() > 0) {
                    out.put(code, BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP));
                }
            }
            return out.isEmpty() ? Optional.empty() : Optional.of(out);
        } catch (RuntimeException e) {
            log.debug("Market fetch failed: {}", e.toString());
            return Optional.empty();
        }
    }

    /** Pull prices shortly after startup, then on a schedule (the threshold sync). */
    @Scheduled(initialDelayString = "${tekmachain.market.initial-delay-ms:5000}",
            fixedDelayString = "${tekmachain.market.refresh-ms:120000}")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.debug("Scheduled market refresh failed: {}", e.toString());
        }
    }
}

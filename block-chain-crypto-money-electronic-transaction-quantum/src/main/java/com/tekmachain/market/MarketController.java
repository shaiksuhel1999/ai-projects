package com.tekmachain.market;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Read-only access to the live real-world crypto market data. These coins are
 * reference prices only — they are not tradable on TekmaChain.
 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketDataFeedService market;
    private final MarketPriceRepository repo;

    public MarketController(MarketDataFeedService market, MarketPriceRepository repo) {
        this.market = market;
        this.repo = repo;
    }

    @GetMapping
    public List<MarketPrice> list() {
        return repo.findAll().stream()
                .sorted((a, b) -> b.getUsdPrice().compareTo(a.getUsdPrice()))
                .toList();
    }

    /** Live market-feed status: seeded vs live, and when it last synced. */
    @GetMapping("/status")
    public MarketDataFeedService.Status status() {
        return market.status();
    }

    /** Force a live market refresh now. */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        int updated = market.refresh();
        return Map.of("updated", updated, "status", market.status());
    }
}

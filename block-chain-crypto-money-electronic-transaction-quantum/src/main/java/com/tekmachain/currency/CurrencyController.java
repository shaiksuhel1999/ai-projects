package com.tekmachain.currency;

import com.tekmachain.exchange.service.MarketDataService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/currencies")
public class CurrencyController {

    private final CurrencyService currencies;
    private final MarketDataService market;
    private final FxRateService fx;

    public CurrencyController(CurrencyService currencies, MarketDataService market, FxRateService fx) {
        this.currencies = currencies;
        this.market = market;
        this.fx = fx;
    }

    @GetMapping
    public List<Currency> list() {
        return currencies.all();
    }

    /** Live FX feed status: whether rates are seeded or live, and when they last synced. */
    @GetMapping("/status")
    public FxRateService.Status status() {
        return fx.status();
    }

    /** Force a live FX refresh now. */
    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        int updated = fx.refresh();
        return Map.of("updated", updated, "status", fx.status());
    }

    @GetMapping("/convert")
    public Map<String, Object> convert(@RequestParam String from, @RequestParam String to,
                                       @RequestParam BigDecimal amount) {
        BigDecimal result = currencies.convert(amount, from, to);
        return Map.of("from", from, "to", to, "amount", amount, "result", result);
    }

    /** The reference price of 1 TEK in USD (last TEK/USD trade, or 1.00 before any trade). */
    @GetMapping("/tek-usd")
    public Map<String, Object> tekUsd() {
        return Map.of("tekUsd", tekUsdPrice());
    }

    /** Value of a TEK amount in a given currency = amount x TEK/USD x (units per USD). */
    @GetMapping("/tek")
    public Map<String, Object> tekIn(@RequestParam String code, @RequestParam(defaultValue = "1") BigDecimal amount) {
        Currency c = currencies.require(code);
        BigDecimal value = amount.multiply(tekUsdPrice()).multiply(c.getUsdRate()).setScale(8, RoundingMode.HALF_UP);
        return Map.of("code", code, "symbol", c.getSymbol(), "amount", amount, "value", value);
    }

    private BigDecimal tekUsdPrice() {
        BigDecimal last = market.ticker("TEK/USD").lastPrice();
        return last != null ? last : BigDecimal.ONE;
    }
}

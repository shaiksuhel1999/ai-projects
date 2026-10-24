package com.tekmachain.currency;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The world-currency catalogue and FX conversion. Rates are stored in the DB
 * (seeded from {@code currencies.csv}) as units-per-USD, so any pair converts
 * through USD.
 */
@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);

    private final CurrencyRepository repo;

    public CurrencyService(CurrencyRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (repo.count() > 0) {
            return;
        }
        List<Currency> loaded = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("currencies.csv").getInputStream(), StandardCharsets.UTF_8))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split(",", -1);
                if (p.length < 5) continue;
                loaded.add(new Currency(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(),
                        new BigDecimal(p[4].trim())));
            }
        } catch (Exception e) {
            log.error("Failed to seed currencies", e);
            return;
        }
        repo.saveAll(loaded);
        log.info("Seeded {} world currencies", loaded.size());
    }

    @Transactional(readOnly = true)
    public List<Currency> all() {
        return repo.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Currency require(String code) {
        return repo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown currency: " + code));
    }

    public boolean exists(String code) {
        return repo.existsById(code);
    }

    /** Convert an amount from one currency to another, routed through USD. */
    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, String from, String to) {
        BigDecimal fromRate = require(from).getUsdRate();
        BigDecimal toRate = require(to).getUsdRate();
        BigDecimal usd = amount.divide(fromRate, 12, RoundingMode.HALF_UP);
        return usd.multiply(toRate).setScale(8, RoundingMode.HALF_UP);
    }

    /** Update one rate (units per USD) — the hook an FX feed would call. */
    @Transactional
    public Currency updateRate(String code, BigDecimal usdRate) {
        Currency c = require(code);
        c.setUsdRate(usdRate);
        c.setUpdatedAt(System.currentTimeMillis());
        return repo.save(c);
    }
}

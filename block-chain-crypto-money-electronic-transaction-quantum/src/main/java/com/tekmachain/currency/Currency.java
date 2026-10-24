package com.tekmachain.currency;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A world currency and its conversion rate against USD ({@code usdRate} = how many
 * units of this currency equal 1 USD). Seeded from {@code currencies.csv}; a real
 * deployment would refresh these from an FX feed.
 */
@Entity
@Table(name = "currency")
public class Currency {

    @Id
    @Column(length = 8)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 8)
    private String symbol;

    private String country;

    @Column(name = "usd_rate", nullable = false, precision = 24, scale = 8)
    private BigDecimal usdRate;

    @Column(name = "updated_at")
    private long updatedAt;

    public Currency() {
    }

    public Currency(String code, String name, String symbol, String country, BigDecimal usdRate) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.country = country;
        this.usdRate = usdRate;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public BigDecimal getUsdRate() { return usdRate; }
    public void setUsdRate(BigDecimal usdRate) { this.usdRate = usdRate; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

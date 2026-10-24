package com.tekmachain.market;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * The latest real-world USD price of an external cryptocurrency (BTC, ETH, …),
 * synced from a live public feed and cached in the DB. This is <em>reference
 * market data only</em> — it is never tradable here and never wired to real money;
 * Tekma Coin remains the one fictional asset. {@code changePct} is the move since
 * the previous sync, which drives the live green/red ticker in the UI.
 */
@Entity
@Table(name = "market_price")
public class MarketPrice {

    @Id
    @Column(length = 16)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "usd_price", nullable = false, precision = 24, scale = 8)
    private BigDecimal usdPrice;

    @Column(name = "change_pct", precision = 12, scale = 4)
    private BigDecimal changePct;

    @Column(name = "updated_at")
    private long updatedAt;

    public MarketPrice() {
    }

    public MarketPrice(String code, String name, BigDecimal usdPrice) {
        this.code = code;
        this.name = name;
        this.usdPrice = usdPrice;
        this.changePct = BigDecimal.ZERO;
        this.updatedAt = System.currentTimeMillis();
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getUsdPrice() { return usdPrice; }
    public void setUsdPrice(BigDecimal usdPrice) { this.usdPrice = usdPrice; }

    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

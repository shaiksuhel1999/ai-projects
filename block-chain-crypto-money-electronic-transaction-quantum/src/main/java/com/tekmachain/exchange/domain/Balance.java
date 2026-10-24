package com.tekmachain.exchange.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A user's exchange balance in one asset (TEK / USD / INR). {@code available} can
 * be traded or withdrawn; {@code locked} is reserved by resting orders.
 */
@Entity
@Table(name = "ex_balance",
        uniqueConstraints = @UniqueConstraint(columnNames = {"address", "asset"}))
public class Balance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String asset;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal available = BigDecimal.ZERO;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal locked = BigDecimal.ZERO;

    public Balance() {
    }

    public Balance(String address, String asset) {
        this.address = address;
        this.asset = asset;
    }

    public Long getId() { return id; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getAsset() { return asset; }
    public void setAsset(String asset) { this.asset = asset; }

    public BigDecimal getAvailable() { return available; }
    public void setAvailable(BigDecimal available) { this.available = available; }

    public BigDecimal getLocked() { return locked; }
    public void setLocked(BigDecimal locked) { this.locked = locked; }

    public BigDecimal total() {
        return available.add(locked);
    }
}

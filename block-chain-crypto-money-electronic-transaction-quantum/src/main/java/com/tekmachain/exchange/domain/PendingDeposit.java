package com.tekmachain.exchange.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A TEK deposit into the exchange that is waiting for its on-chain transaction to
 * be mined. Once confirmed, the user's exchange TEK balance is credited.
 */
@Entity
@Table(name = "ex_pending_deposit")
public class PendingDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String txId;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean credited = false;

    @Column(nullable = false)
    private long createdAt;

    public PendingDeposit() {
    }

    public PendingDeposit(String txId, String address, BigDecimal amount) {
        this.txId = txId;
        this.address = address;
        this.amount = amount;
        this.createdAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public boolean isCredited() { return credited; }
    public void setCredited(boolean credited) { this.credited = credited; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

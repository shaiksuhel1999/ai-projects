package com.tekmachain.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * An Unspent Transaction Output — a coin currently sitting on the ledger,
 * spendable by whoever controls {@code address}. The set of all UTXOs IS the
 * balance sheet of the whole network; a wallet's balance is just the sum of the
 * UTXOs addressed to it.
 */
@Entity
@Table(name = "utxo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tx_id", "out_index"}))
public class Utxo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false)
    private String txId;

    @Column(name = "out_index", nullable = false)
    private int outIndex;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal amount;

    /** Block height that created this output, and whether it came from a coinbase. */
    @Column(name = "block_height", nullable = false)
    private int height;

    @Column(nullable = false)
    private boolean coinbase;

    public Utxo() {
    }

    public Utxo(String txId, int outIndex, String address, BigDecimal amount) {
        this(txId, outIndex, address, amount, 0, false);
    }

    public Utxo(String txId, int outIndex, String address, BigDecimal amount, int height, boolean coinbase) {
        this.txId = txId;
        this.outIndex = outIndex;
        this.address = address;
        this.amount = amount;
        this.height = height;
        this.coinbase = coinbase;
    }

    public Long getId() { return id; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public boolean isCoinbase() { return coinbase; }
    public void setCoinbase(boolean coinbase) { this.coinbase = coinbase; }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public int getOutIndex() { return outIndex; }
    public void setOutIndex(int outIndex) { this.outIndex = outIndex; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String outpoint() {
        return txId + ":" + outIndex;
    }
}

package com.tekmachain.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One output of a transaction: "pay {amount} TEK to {address}".
 * Once an output is confirmed in a block and not yet spent, it becomes a UTXO.
 */
@Entity
@Table(name = "tx_output")
public class TxOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Position of this output inside its transaction (0-based). */
    @Column(name = "out_index", nullable = false)
    private int index;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal amount;

    public TxOutput() {
    }

    public TxOutput(int index, String address, BigDecimal amount) {
        this.index = index;
        this.address = address;
        this.amount = amount;
    }

    public Long getId() { return id; }

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

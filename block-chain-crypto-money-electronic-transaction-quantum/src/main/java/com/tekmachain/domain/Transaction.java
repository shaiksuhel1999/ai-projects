package com.tekmachain.domain;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.crypto.SigningData;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A signed movement of coins in the UTXO model: it consumes previous outputs
 * (inputs) and creates new ones (outputs). A "coinbase" transaction has no
 * inputs and mints the block reward + fees to the miner.
 */
@Entity
@Table(name = "transaction")
public class Transaction {

    /** Transaction id = SHA-256 of the transaction's contents. */
    @Id
    @Column(name = "tx_id", length = 64)
    private String txId;

    @Column(nullable = false)
    private long timestamp;

    @Column(nullable = false)
    private boolean coinbase;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "tx_id")
    @OrderBy("id ASC")
    private List<TxInput> inputs = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "tx_id")
    @OrderBy("index ASC")
    private List<TxOutput> outputs = new ArrayList<>();

    public Transaction() {
    }

    /**
     * The canonical bytes that get signed and hashed. They commit to every outpoint
     * being spent and every output being created — but NOT to the signatures
     * themselves (otherwise signing would be circular). See {@link SigningData} for
     * the exact binary layout; the in-browser signer produces identical bytes.
     */
    public byte[] signingBytes() {
        return SigningData.build(coinbase, timestamp,
                inputs.stream().map(i -> new SigningData.InRef(i.getPrevTxId(), i.getOutIndex())).toList(),
                outputs.stream().map(o -> new SigningData.OutRef(o.getAddress(), o.getAmount())).toList());
    }

    /** Computes and stores this transaction's id from its signing data. */
    public String computeAndSetTxId() {
        this.txId = CryptoUtil.sha256Hex(signingBytes());
        return this.txId;
    }

    public BigDecimal outputSum() {
        return outputs.stream().map(TxOutput::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getTxId() { return txId; }
    public void setTxId(String txId) { this.txId = txId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isCoinbase() { return coinbase; }
    public void setCoinbase(boolean coinbase) { this.coinbase = coinbase; }

    public List<TxInput> getInputs() { return inputs; }
    public void setInputs(List<TxInput> inputs) { this.inputs = inputs; }

    public List<TxOutput> getOutputs() { return outputs; }
    public void setOutputs(List<TxOutput> outputs) { this.outputs = outputs; }
}

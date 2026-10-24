package com.tekmachain.domain;

import jakarta.persistence.*;

/**
 * One input of a transaction: a pointer to a previous output being spent,
 * plus the signature and public key that prove the spender controls it.
 * ("outpoint" = prevTxId + outIndex, exactly like Bitcoin.)
 */
@Entity
@Table(name = "tx_input")
public class TxInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Id of the transaction whose output we are spending. */
    @Column(name = "prev_tx_id", nullable = false)
    private String prevTxId;

    /** Which output of that transaction. */
    @Column(name = "out_index", nullable = false)
    private int outIndex;

    /** ECDSA signature over the transaction's signing data. Empty for coinbase inputs. */
    @Column(length = 2000)
    private String signature;

    /** Public key of the owner, used to verify the signature and re-derive the address. */
    @Column(length = 2000)
    private String publicKey;

    /** Optional ML-DSA (Dilithium) signature + public key for quantum-safe hybrid spends. */
    @Column(name = "dilithium_signature", columnDefinition = "TEXT")
    private String dilithiumSignature;

    @Column(name = "dilithium_public_key", columnDefinition = "TEXT")
    private String dilithiumPublicKey;

    public TxInput() {
    }

    public TxInput(String prevTxId, int outIndex) {
        this.prevTxId = prevTxId;
        this.outIndex = outIndex;
    }

    public Long getId() { return id; }

    public String getPrevTxId() { return prevTxId; }
    public void setPrevTxId(String prevTxId) { this.prevTxId = prevTxId; }

    public int getOutIndex() { return outIndex; }
    public void setOutIndex(int outIndex) { this.outIndex = outIndex; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getDilithiumSignature() { return dilithiumSignature; }
    public void setDilithiumSignature(String dilithiumSignature) { this.dilithiumSignature = dilithiumSignature; }

    public String getDilithiumPublicKey() { return dilithiumPublicKey; }
    public void setDilithiumPublicKey(String dilithiumPublicKey) { this.dilithiumPublicKey = dilithiumPublicKey; }

    /** "txid:index" — a stable key for this outpoint. */
    public String outpoint() {
        return prevTxId + ":" + outIndex;
    }
}

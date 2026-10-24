package com.tekmachain.domain;

import com.tekmachain.crypto.CryptoUtil;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A block in the chain. Its header (height, previousHash, merkleRoot, timestamp,
 * difficulty, nonce, minerAddress) is what Proof-of-Work hashes over; the body is
 * the list of transactions it confirms.
 */
@Entity
@Table(name = "block")
public class Block {

    /** Block height (0 = genesis). Also the primary key. */
    @Id
    private Long height;

    @Column(nullable = false)
    private long timestamp;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "merkle_root", nullable = false, length = 64)
    private String merkleRoot;

    @Column(nullable = false)
    private int difficulty;

    @Column(nullable = false)
    private long nonce;

    @Column(name = "miner_address")
    private String minerAddress;

    @Column(nullable = false, length = 64)
    private String hash;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JoinColumn(name = "block_height")
    private List<Transaction> transactions = new ArrayList<>();

    public Block() {
    }

    /** The exact string Proof-of-Work hashes. Changing any field changes the hash. */
    public String headerString() {
        return height + previousHash + merkleRoot + timestamp + difficulty + nonce + minerAddress;
    }

    public String computeHash() {
        return CryptoUtil.sha256Hex(headerString());
    }

    public Long getHeight() { return height; }
    public void setHeight(Long height) { this.height = height; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public String getMerkleRoot() { return merkleRoot; }
    public void setMerkleRoot(String merkleRoot) { this.merkleRoot = merkleRoot; }

    public int getDifficulty() { return difficulty; }
    public void setDifficulty(int difficulty) { this.difficulty = difficulty; }

    public long getNonce() { return nonce; }
    public void setNonce(long nonce) { this.nonce = nonce; }

    public String getMinerAddress() { return minerAddress; }
    public void setMinerAddress(String minerAddress) { this.minerAddress = minerAddress; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public List<Transaction> getTransactions() { return transactions; }
    public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }
}

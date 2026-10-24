package com.tekmachain.domain;

import jakarta.persistence.*;

/**
 * A wallet = a key pair + its derived address.
 *
 * <p><b>POC security note:</b> a real wallet never hands its private key to a
 * server. Here we store the private key so the node can sign on the user's behalf
 * from the browser demo. This is fine for a local, fictional-currency POC and is
 * exactly the kind of thing a later "real wallet" phase would move client-side.</p>
 */
@Entity
@Table(name = "wallet", indexes = @Index(name = "idx_wallet_owner", columnList = "owner"))
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    @Column(nullable = false, unique = true)
    private String address;

    @Column(name = "public_key", nullable = false, length = 2000)
    private String publicKey;

    @Column(name = "private_key", nullable = false, length = 2000)
    private String privateKey;

    /** Optional ML-DSA (Dilithium) keys — present only for quantum-safe hybrid wallets. */
    @Column(name = "dilithium_public_key", columnDefinition = "TEXT")
    private String dilithiumPublicKey;

    @Column(name = "dilithium_private_key", columnDefinition = "TEXT")
    private String dilithiumPrivateKey;

    /** The account that owns this wallet; null for system wallets (e.g. the exchange hot wallet). */
    @Column(name = "owner", length = 40)
    private String owner;

    public Wallet() {
    }

    public Wallet(String label, String address, String publicKey, String privateKey) {
        this.label = label;
        this.address = address;
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    public boolean isHybrid() {
        return dilithiumPublicKey != null && !dilithiumPublicKey.isBlank();
    }

    public Long getId() { return id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getDilithiumPublicKey() { return dilithiumPublicKey; }
    public void setDilithiumPublicKey(String dilithiumPublicKey) { this.dilithiumPublicKey = dilithiumPublicKey; }

    public String getDilithiumPrivateKey() { return dilithiumPrivateKey; }
    public void setDilithiumPrivateKey(String dilithiumPrivateKey) { this.dilithiumPrivateKey = dilithiumPrivateKey; }

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}

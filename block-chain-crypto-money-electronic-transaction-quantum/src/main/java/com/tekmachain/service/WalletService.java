package com.tekmachain.service;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.custody.KeyVault;
import com.tekmachain.domain.Utxo;
import com.tekmachain.domain.Wallet;
import com.tekmachain.repo.UtxoRepository;
import com.tekmachain.repo.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.List;

/**
 * Creating wallets and reading balances. A balance is simply the sum of the
 * UTXOs addressed to the wallet — the wallet itself never "holds" coins.
 */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final UtxoRepository utxos;
    private final KeyVault vault;
    private final BlockchainService blockchain;

    @org.springframework.beans.factory.annotation.Value("${tekmachain.coinbase-maturity:2}")
    private int coinbaseMaturity;

    public WalletService(WalletRepository wallets, UtxoRepository utxos, KeyVault vault, BlockchainService blockchain) {
        this.wallets = wallets;
        this.utxos = utxos;
        this.vault = vault;
        this.blockchain = blockchain;
    }

    private boolean mature(com.tekmachain.domain.Utxo u) {
        return !u.isCoinbase() || (blockchain.height() + 1 - u.getHeight()) >= coinbaseMaturity;
    }

    @Transactional
    public Wallet create(String label) {
        KeyPair kp = CryptoUtil.generateKeyPair();
        String publicKeyHex = CryptoUtil.encodePublicKey(kp.getPublic());
        String address = CryptoUtil.addressFromPublicKeyHex(publicKeyHex);
        String name = (label == null || label.isBlank()) ? "wallet-" + address.substring(0, 8) : label.trim();
        // Private key is sealed by the vault — never stored in plaintext.
        String sealed = vault.seal(CryptoUtil.encodePrivateKey(kp.getPrivate()));
        return wallets.save(new Wallet(name, address, publicKeyHex, sealed));
    }

    /** A quantum-safe wallet: ECDSA + ML-DSA keys, address bound to both, both keys sealed. */
    @Transactional
    public Wallet createHybrid(String label) {
        KeyPair ecdsa = CryptoUtil.generateKeyPair();
        KeyPair mldsa = CryptoUtil.generateDilithiumKeyPair();
        String ecdsaPub = CryptoUtil.encodePublicKey(ecdsa.getPublic());
        String dilithiumPub = CryptoUtil.encodeDilithiumPublic(mldsa.getPublic());
        String address = CryptoUtil.addressFromKeys(ecdsaPub, dilithiumPub);
        String name = (label == null || label.isBlank()) ? "hybrid-" + address.substring(0, 8) : label.trim();

        Wallet w = new Wallet(name, address, ecdsaPub, vault.seal(CryptoUtil.encodePrivateKey(ecdsa.getPrivate())));
        w.setDilithiumPublicKey(dilithiumPub);
        w.setDilithiumPrivateKey(vault.seal(CryptoUtil.encodeDilithiumPrivate(mldsa.getPrivate())));
        return wallets.save(w);
    }

    @Transactional(readOnly = true)
    public List<Wallet> findAll() {
        return wallets.findAll();
    }

    /** Wallets belonging to one account (excludes system wallets, which have no owner). */
    @Transactional(readOnly = true)
    public List<Wallet> ownedBy(String owner) {
        return owner == null ? List.of() : wallets.findByOwner(owner);
    }

    /**
     * Whether {@code user} may act as {@code address}. A custodial wallet (whose key
     * the server holds) may only be used by its owner; a system wallet (no owner) by
     * nobody; an address with no server wallet is a self-custody address and is
     * allowed (the server holds no key for it — it can only be moved with a signature).
     */
    @Transactional(readOnly = true)
    public boolean canActAs(String address, String user) {
        return wallets.findByAddress(address)
                .map(w -> user != null && user.equals(w.getOwner()))
                .orElse(true);
    }

    /** Assign a freshly created wallet to the account that made it. */
    @Transactional
    public Wallet assignOwner(Wallet w, String owner) {
        w.setOwner(owner);
        return wallets.save(w);
    }

    /**
     * One-time migration helper: assign every ownerless wallet to {@code owner},
     * except the system wallet at {@code excludeAddress} (the exchange hot wallet,
     * which must stay unowned). Returns how many were claimed.
     */
    @Transactional
    public int claimUnownedWallets(String owner, String excludeAddress) {
        List<Wallet> orphans = wallets.findByOwnerIsNull();
        int claimed = 0;
        for (Wallet w : orphans) {
            if (excludeAddress != null && excludeAddress.equals(w.getAddress())) {
                continue;
            }
            w.setOwner(owner);
            claimed++;
        }
        wallets.saveAll(orphans);
        return claimed;
    }

    @Transactional(readOnly = true)
    public Wallet require(String address) {
        return wallets.findByAddress(address)
                .orElseThrow(() -> new IllegalArgumentException("Unknown wallet address: " + address));
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(String address) {
        return utxos.findByAddress(address).stream()
                .map(Utxo::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Balance that can be spent right now (excludes not-yet-mature coinbase outputs). */
    @Transactional(readOnly = true)
    public BigDecimal spendableBalanceOf(String address) {
        return utxos.findByAddress(address).stream()
                .filter(this::mature).map(Utxo::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Coinbase rewards mined too recently to spend yet. */
    @Transactional(readOnly = true)
    public BigDecimal immatureBalanceOf(String address) {
        return utxos.findByAddress(address).stream()
                .filter(u -> !mature(u)).map(Utxo::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<Utxo> utxosOf(String address) {
        return utxos.findByAddress(address);
    }
}

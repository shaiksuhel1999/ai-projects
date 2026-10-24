package com.tekmachain.service;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.domain.*;
import com.tekmachain.repo.UtxoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds, signs and validates UTXO transactions.
 *
 * <p>Building a payment means: pick enough of the sender's unspent outputs to
 * cover amount + fee, spend them fully as inputs, and create outputs for the
 * recipient plus "change" back to the sender. The difference between inputs and
 * outputs is the fee the miner collects.</p>
 */
@Service
public class TransactionService {

    private final UtxoRepository utxos;
    private final WalletService walletService;
    private final MempoolService mempool;
    private final com.tekmachain.custody.KeyVault vault;
    private final BlockchainService blockchain;

    @org.springframework.beans.factory.annotation.Value("${tekmachain.coinbase-maturity:2}")
    private int coinbaseMaturity;

    public TransactionService(UtxoRepository utxos, WalletService walletService, MempoolService mempool,
                              com.tekmachain.custody.KeyVault vault, BlockchainService blockchain) {
        this.utxos = utxos;
        this.walletService = walletService;
        this.mempool = mempool;
        this.vault = vault;
        this.blockchain = blockchain;
    }

    /** A coinbase output is spendable only once it has {@code coinbaseMaturity} confirmations. */
    public boolean isSpendable(Utxo u) {
        return !u.isCoinbase() || (blockchain.height() + 1 - u.getHeight()) >= coinbaseMaturity;
    }

    static BigDecimal norm(BigDecimal v) {
        return v.setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * Creates a signed payment and drops it into the mempool.
     * Signing happens here (server-side) because this POC stores keys on the node.
     */
    @Transactional
    public Transaction createPayment(String from, String to, BigDecimal amountRaw, BigDecimal feeRaw) {
        BigDecimal amount = norm(amountRaw);
        BigDecimal fee = norm(feeRaw);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("Fee cannot be negative");
        }

        Wallet wallet = walletService.require(from);
        BigDecimal needed = amount.add(fee);

        // Gather spendable UTXOs, skipping any already reserved by pending transactions.
        List<Utxo> available = new ArrayList<>();
        BigDecimal gathered = BigDecimal.ZERO;
        for (Utxo u : utxos.findByAddress(from)) {
            if (mempool.isReserved(u.outpoint()) || !isSpendable(u)) {
                continue;   // skip reserved and not-yet-mature coinbase outputs
            }
            available.add(u);
            gathered = gathered.add(u.getAmount());
            if (gathered.compareTo(needed) >= 0) {
                break;
            }
        }
        if (gathered.compareTo(needed) < 0) {
            throw new IllegalStateException("Insufficient spendable balance: have " + norm(gathered)
                    + " TEK available, need " + needed + " TEK (amount + fee)");
        }

        Transaction tx = new Transaction();
        tx.setCoinbase(false);
        tx.setTimestamp(System.currentTimeMillis());

        List<TxInput> inputs = new ArrayList<>();
        for (Utxo u : available) {
            inputs.add(new TxInput(u.getTxId(), u.getOutIndex()));
        }
        tx.setInputs(inputs);

        List<TxOutput> outputs = new ArrayList<>();
        outputs.add(new TxOutput(0, to, amount));
        BigDecimal change = gathered.subtract(needed);
        if (change.signum() > 0) {
            outputs.add(new TxOutput(1, from, change));   // change back to sender
        }
        tx.setOutputs(outputs);

        // Sign the canonical signing data with the sender's private key.
        byte[] data = tx.signingBytes();
        // The vault signs with the sealed keys — plaintext never returns to this code.
        String signature = vault.signEcdsa(wallet.getPrivateKey(), data);
        String dilithiumSig = wallet.isHybrid()
                ? vault.signDilithium(wallet.getDilithiumPrivateKey(), data) : null;
        for (TxInput in : inputs) {
            in.setSignature(signature);
            in.setPublicKey(wallet.getPublicKey());
            if (wallet.isHybrid()) {
                in.setDilithiumSignature(dilithiumSig);
                in.setDilithiumPublicKey(wallet.getDilithiumPublicKey());
            }
        }

        tx.computeAndSetTxId();

        validate(tx); // fail fast before it reaches the mempool
        mempool.add(tx);
        return tx;
    }

    /**
     * Verifies a transaction against the current UTXO set and returns the fee it pays.
     * Coinbase transactions are validated by the miner instead (they mint new coins).
     */
    @Transactional(readOnly = true)
    public BigDecimal validate(Transaction tx) {
        if (tx.isCoinbase()) {
            return BigDecimal.ZERO;
        }
        if (tx.getInputs().isEmpty()) {
            throw new IllegalStateException("Non-coinbase transaction must have at least one input");
        }
        // The transaction id must be the hash of its own contents — otherwise a client
        // (via /transactions/raw) or a peer could smuggle in a tx whose id the full-chain
        // validator would later reject, stalling the chain.
        if (!CryptoUtil.sha256Hex(tx.signingBytes()).equals(tx.getTxId())) {
            throw new IllegalStateException("Transaction id does not match its contents");
        }
        byte[] data = tx.signingBytes();

        BigDecimal inputSum = BigDecimal.ZERO;
        List<String> spentHere = new ArrayList<>();
        for (TxInput in : tx.getInputs()) {
            if (spentHere.contains(in.outpoint())) {
                throw new IllegalStateException("Transaction spends " + in.outpoint() + " twice");
            }
            spentHere.add(in.outpoint());

            Utxo utxo = utxos.findByTxIdAndOutIndex(in.getPrevTxId(), in.getOutIndex())
                    .orElseThrow(() -> new IllegalStateException(
                            "Input " + in.outpoint() + " is not a spendable output"));

            if (!isSpendable(utxo)) {
                throw new IllegalStateException("Input " + in.outpoint()
                        + " spends an immature coinbase (matures at height " + (utxo.getHeight() + coinbaseMaturity) + ")");
            }
            if (!CryptoUtil.verify(data, in.getSignature(), in.getPublicKey())) {
                throw new IllegalStateException("Invalid ECDSA signature on input " + in.outpoint());
            }
            // Hybrid (quantum-safe) inputs must ALSO carry a valid ML-DSA signature.
            boolean hybrid = in.getDilithiumPublicKey() != null && !in.getDilithiumPublicKey().isBlank();
            if (hybrid && !CryptoUtil.verifyDilithium(data, in.getDilithiumSignature(), in.getDilithiumPublicKey())) {
                throw new IllegalStateException("Invalid ML-DSA signature on input " + in.outpoint());
            }
            String derived = CryptoUtil.addressFromKeys(in.getPublicKey(), in.getDilithiumPublicKey());
            if (!derived.equals(utxo.getAddress())) {
                throw new IllegalStateException("Input " + in.outpoint()
                        + " signed by a key that does not own it");
            }
            inputSum = inputSum.add(utxo.getAmount());
        }

        for (TxOutput out : tx.getOutputs()) {
            if (out.getAmount().signum() <= 0) {
                throw new IllegalStateException("Output amounts must be positive");
            }
        }

        BigDecimal outputSum = tx.outputSum();
        if (inputSum.compareTo(outputSum) < 0) {
            throw new IllegalStateException("Outputs (" + outputSum + ") exceed inputs (" + inputSum + ")");
        }
        return inputSum.subtract(outputSum);
    }
}

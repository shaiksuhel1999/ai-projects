package com.tekmachain.service;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.domain.Block;
import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.TxInput;
import com.tekmachain.domain.TxOutput;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates an entire candidate chain from genesis by replaying it into a fresh
 * in-memory UTXO set. This is the rule every node agrees on: a chain is only
 * acceptable if <em>every</em> block hashes correctly, links to its parent, meets
 * its Proof-of-Work target, has a correct Merkle root, and every transaction in it
 * spends only real, unspent, correctly-signed outputs without double-spending —
 * and each coinbase mints no more than the block reward plus the fees it collected.
 *
 * <p>A peer's chain is adopted only if it passes this and is longer than ours.</p>
 */
@Component
public class ChainValidator {

    private static final String ZERO_HASH = "0".repeat(64);

    private final Economics economics;
    private final DifficultyPolicy difficultyPolicy;

    @org.springframework.beans.factory.annotation.Value("${tekmachain.coinbase-maturity:2}")
    private int coinbaseMaturity;

    public ChainValidator(Economics economics, DifficultyPolicy difficultyPolicy) {
        this.economics = economics;
        this.difficultyPolicy = difficultyPolicy;
    }

    /** An unspent output during replay, remembering its origin for coinbase maturity. */
    public record UtxoEntry(String address, BigDecimal amount, int height, boolean coinbase) {
    }

    public record Result(boolean valid, List<String> problems, int length, Map<String, UtxoEntry> utxos) {
    }

    public Result validate(List<Dtos.BlockDto> dtos) {
        List<String> problems = new ArrayList<>();
        Map<String, UtxoEntry> utxos = new HashMap<>();

        if (dtos.isEmpty()) {
            return new Result(false, List.of("Empty chain"), 0, utxos);
        }

        List<Block> chain = dtos.stream().map(Mapper::blockEntity).toList();
        String previousHash = ZERO_HASH;
        for (int i = 0; i < chain.size(); i++) {
            Block b = chain.get(i);
            String where = "Block " + i;

            if (b.getHeight() == null || b.getHeight() != i) {
                problems.add(where + ": height should be " + i + " but is " + b.getHeight());
            }
            if (!b.getHash().equals(b.computeHash())) {
                problems.add(where + ": stored hash does not match its header (tampered).");
            }
            if (!b.getPreviousHash().equals(previousHash)) {
                problems.add(where + ": previousHash does not link to the block before it.");
            }
            // Enforce the difficulty rule: recompute what this height's difficulty must be.
            int expectedDiff = expectedDifficulty(chain, i);
            if (b.getDifficulty() != expectedDiff) {
                problems.add(where + ": wrong difficulty (expected " + expectedDiff + ", got " + b.getDifficulty() + ").");
            }
            if (i > 0 && !difficultyPolicy.meets(b.getHash(), b.getDifficulty())) {
                problems.add(where + ": hash does not meet the Proof-of-Work target.");
            }
            String expectedMerkle = CryptoUtil.merkleRoot(
                    b.getTransactions().stream().map(Transaction::getTxId).toList());
            if (!b.getMerkleRoot().equals(expectedMerkle)) {
                problems.add(where + ": Merkle root does not match its transactions.");
            }

            validateBlockTransactions(i, b, utxos, problems);
            previousHash = b.getHash();
        }

        return new Result(problems.isEmpty(), problems, dtos.size(), utxos);
    }

    /** The difficulty the block at index {@code i} must carry, recomputed from the chain. */
    private int expectedDifficulty(List<Block> chain, int i) {
        if (i == 0) {
            return difficultyPolicy.initialDifficulty();
        }
        Block prev = chain.get(i - 1);
        long windowStartTs = prev.getTimestamp();
        if (difficultyPolicy.isRetargetHeight(i)) {
            int idx = i - difficultyPolicy.retargetInterval();
            if (idx >= 0) windowStartTs = chain.get(idx).getTimestamp();
        }
        return difficultyPolicy.difficultyFor(i, prev.getDifficulty(), prev.getTimestamp(), windowStartTs);
    }

    private void validateBlockTransactions(int height, Block b,
                                           Map<String, UtxoEntry> utxos, List<String> problems) {
        List<Transaction> txs = b.getTransactions();
        String where = "Block " + height;

        if (height > 0) {
            if (txs.isEmpty() || !txs.get(0).isCoinbase()) {
                problems.add(where + ": first transaction must be the coinbase.");
            }
            for (int j = 1; j < txs.size(); j++) {
                if (txs.get(j).isCoinbase()) {
                    problems.add(where + ": only the first transaction may be a coinbase.");
                }
            }
        }

        Set<String> spentInBlock = new HashSet<>();
        Map<String, UtxoEntry> toAdd = new HashMap<>();
        BigDecimal fees = BigDecimal.ZERO;
        Transaction coinbase = null;

        for (Transaction tx : txs) {
            // transaction id must equal the hash of its own contents
            if (!tx.getTxId().equals(CryptoUtil.sha256Hex(tx.signingBytes()))) {
                problems.add(where + ": transaction id does not match its contents (" + shortId(tx.getTxId()) + ").");
            }

            if (tx.isCoinbase()) {
                coinbase = tx;
            } else {
                fees = fees.add(validateSpend(where, height, tx, utxos, spentInBlock, problems));
            }

            // queue this transaction's outputs to become new UTXOs
            for (TxOutput out : tx.getOutputs()) {
                if (out.getAmount().signum() <= 0) {
                    problems.add(where + ": output amounts must be positive.");
                }
                toAdd.put(tx.getTxId() + ":" + out.getIndex(),
                        new UtxoEntry(out.getAddress(), out.getAmount(), height, tx.isCoinbase()));
            }
        }

        if (coinbase != null) {
            // Supply so far = value in the UTXO set before this block (fees are conserved).
            BigDecimal mintedSoFar = utxos.values().stream()
                    .map(UtxoEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal minted = coinbase.outputSum();
            BigDecimal allowed = economics.effectiveReward(height, mintedSoFar).add(fees);
            if (minted.compareTo(allowed) > 0) {
                problems.add(where + ": coinbase mints " + minted + " but only " + allowed
                        + " (halving-scheduled reward + fees) is allowed.");
            }
        }

        // apply this block to the UTXO set: remove what it spent, add what it created
        for (String op : spentInBlock) {
            utxos.remove(op);
        }
        utxos.putAll(toAdd);
    }

    private BigDecimal validateSpend(String where, int height, Transaction tx, Map<String, UtxoEntry> utxos,
                                     Set<String> spentInBlock, List<String> problems) {
        byte[] data = tx.signingBytes();
        BigDecimal inputSum = BigDecimal.ZERO;

        for (TxInput in : tx.getInputs()) {
            String op = in.outpoint();
            if (spentInBlock.contains(op)) {
                problems.add(where + ": output " + op + " is spent twice.");
                continue;
            }
            UtxoEntry entry = utxos.get(op);
            if (entry == null) {
                problems.add(where + ": input " + op + " is not an unspent output.");
                continue;
            }
            // Coinbase maturity: a mined reward can't be spent for coinbaseMaturity blocks.
            if (entry.coinbase() && (height - entry.height()) < coinbaseMaturity) {
                problems.add(where + ": input " + op + " spends an immature coinbase.");
                continue;
            }
            if (!CryptoUtil.verify(data, in.getSignature(), in.getPublicKey())) {
                problems.add(where + ": invalid ECDSA signature on input " + op + ".");
                continue;
            }
            boolean hybrid = in.getDilithiumPublicKey() != null && !in.getDilithiumPublicKey().isBlank();
            if (hybrid && !CryptoUtil.verifyDilithium(data, in.getDilithiumSignature(), in.getDilithiumPublicKey())) {
                problems.add(where + ": invalid ML-DSA signature on input " + op + ".");
                continue;
            }
            if (!CryptoUtil.addressFromKeys(in.getPublicKey(), in.getDilithiumPublicKey()).equals(entry.address())) {
                problems.add(where + ": input " + op + " signed by a key that does not own it.");
                continue;
            }
            spentInBlock.add(op);
            inputSum = inputSum.add(entry.amount());
        }

        BigDecimal outputSum = tx.outputSum();
        if (inputSum.compareTo(outputSum) < 0) {
            problems.add(where + ": transaction outputs (" + outputSum + ") exceed inputs (" + inputSum + ").");
            return BigDecimal.ZERO;
        }
        return inputSum.subtract(outputSum);
    }

    private static String shortId(String id) {
        return id == null ? "null" : id.substring(0, Math.min(10, id.length()));
    }
}

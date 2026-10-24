package com.tekmachain.service;

import com.tekmachain.domain.*;
import com.tekmachain.repo.BlockRepository;
import com.tekmachain.repo.UtxoRepository;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Applies blocks to the local ledger. Two entry points beyond mining:
 * <ul>
 *   <li>{@link #tryAppendExternalBlock} — a peer's freshly-mined block that extends our tip.</li>
 *   <li>{@link #replaceChain} — adopt a peer's longer, fully-valid chain (fork resolution).</li>
 * </ul>
 */
@Service
public class ChainService {

    public enum AppendOutcome {ACCEPTED, NOT_EXTENDING, INVALID}

    private final BlockRepository blocks;
    private final UtxoRepository utxos;
    private final MempoolService mempool;
    private final TransactionService txService;
    private final ChainValidator validator;
    private final BlockchainService blockchain;
    private final ChainLock chainLock;
    private final Economics economics;
    private final DifficultyPolicy difficultyPolicy;

    public ChainService(BlockRepository blocks, UtxoRepository utxos, MempoolService mempool,
                        TransactionService txService, ChainValidator validator,
                        BlockchainService blockchain, ChainLock chainLock, Economics economics,
                        DifficultyPolicy difficultyPolicy) {
        this.blocks = blocks;
        this.utxos = utxos;
        this.mempool = mempool;
        this.txService = txService;
        this.validator = validator;
        this.blockchain = blockchain;
        this.chainLock = chainLock;
        this.economics = economics;
        this.difficultyPolicy = difficultyPolicy;
    }

    /** Spend the inputs and create the outputs of every transaction in a block at {@code height}. */
    @Transactional
    public void applyBlockToLedger(List<Transaction> txs, int height) {
        for (Transaction tx : txs) {
            if (!tx.isCoinbase()) {
                for (TxInput in : tx.getInputs()) {
                    utxos.deleteByTxIdAndOutIndex(in.getPrevTxId(), in.getOutIndex());
                }
            }
            for (TxOutput out : tx.getOutputs()) {
                utxos.save(new Utxo(tx.getTxId(), out.getIndex(), out.getAddress(), out.getAmount(),
                        height, tx.isCoinbase()));
            }
        }
    }

    /**
     * Accepts a peer's block only if it directly extends our current tip and every
     * rule holds. Anything else (behind us, ahead of us, or a fork) returns
     * NOT_EXTENDING so the caller can fall back to a full chain sync.
     */
    @Transactional
    public AppendOutcome tryAppendExternalBlock(Dtos.BlockDto dto) {
        synchronized (chainLock) {
            return doTryAppend(dto);
        }
    }

    private AppendOutcome doTryAppend(Dtos.BlockDto dto) {
        Block tip = blockchain.requireLastBlock();
        Block b = Mapper.blockEntity(dto);

        if (b.getHeight() != tip.getHeight() + 1 || !b.getPreviousHash().equals(tip.getHash())) {
            return AppendOutcome.NOT_EXTENDING;
        }
        // Enforce the difficulty rule for the incoming block, then check its PoW.
        long windowStartTs = tip.getTimestamp();
        if (difficultyPolicy.isRetargetHeight(b.getHeight())) {
            windowStartTs = blocks.findById(b.getHeight() - difficultyPolicy.retargetInterval())
                    .map(Block::getTimestamp).orElse(tip.getTimestamp());
        }
        int expectedDiff = difficultyPolicy.difficultyFor(b.getHeight(), tip.getDifficulty(), tip.getTimestamp(), windowStartTs);
        if (!b.getHash().equals(b.computeHash())
                || b.getDifficulty() != expectedDiff
                || !difficultyPolicy.meets(b.getHash(), b.getDifficulty())) {
            return AppendOutcome.INVALID;
        }
        String expectedMerkle = com.tekmachain.crypto.CryptoUtil.merkleRoot(
                b.getTransactions().stream().map(Transaction::getTxId).toList());
        if (!b.getMerkleRoot().equals(expectedMerkle)) {
            return AppendOutcome.INVALID;
        }

        List<Transaction> txs = b.getTransactions();
        if (txs.isEmpty() || !txs.get(0).isCoinbase()) {
            return AppendOutcome.INVALID;
        }

        BigDecimal fees = BigDecimal.ZERO;
        Set<String> spent = new HashSet<>();
        try {
            for (int j = 0; j < txs.size(); j++) {
                Transaction tx = txs.get(j);
                // Every tx id must be the hash of its own contents (coinbase included).
                if (!com.tekmachain.crypto.CryptoUtil.sha256Hex(tx.signingBytes()).equals(tx.getTxId())) {
                    return AppendOutcome.INVALID;
                }
                if (tx.isCoinbase()) {
                    if (j != 0) {
                        return AppendOutcome.INVALID;
                    }
                    continue;
                }
                for (TxInput in : tx.getInputs()) {
                    if (!spent.add(in.outpoint())) {
                        return AppendOutcome.INVALID; // double spend within the block
                    }
                }
                fees = fees.add(txService.validate(tx)); // checks UTXOs, signatures, sums
            }
        } catch (RuntimeException invalid) {
            return AppendOutcome.INVALID;
        }

        // Enforce the monetary policy: the coinbase can mint at most the (halving-scheduled,
        // supply-capped) reward for this height, plus the fees it collected.
        BigDecimal supplyBefore = utxos.findAll().stream()
                .map(Utxo::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal allowed = economics.effectiveReward(b.getHeight(), supplyBefore).add(fees);
        BigDecimal minted = txs.get(0).outputSum();
        if (minted.compareTo(allowed) > 0) {
            return AppendOutcome.INVALID;
        }

        blocks.save(b);
        applyBlockToLedger(txs, b.getHeight().intValue());
        mempool.remove(txs);
        return AppendOutcome.ACCEPTED;
    }

    /**
     * Fork resolution: adopt {@code candidate} if it fully validates and is strictly
     * longer than our chain. Rebuilds the whole UTXO set by replaying it.
     *
     * @return true if we switched chains
     */
    @Transactional
    public boolean replaceChain(List<Dtos.BlockDto> candidate) {
        synchronized (chainLock) {
            return doReplaceChain(candidate);
        }
    }

    private boolean doReplaceChain(List<Dtos.BlockDto> candidate) {
        ChainValidator.Result result = validator.validate(candidate);
        int currentLength = (int) (blockchain.height() + 1);
        if (!result.valid() || result.length() <= currentLength) {
            return false;
        }

        // Wipe and rebuild from the candidate.
        utxos.deleteAllInBatch();
        blocks.deleteAll();
        blocks.flush();

        for (Dtos.BlockDto dto : candidate) {
            blocks.save(Mapper.blockEntity(dto));
        }
        result.utxos().forEach((outpoint, entry) -> {
            int sep = outpoint.lastIndexOf(':');
            String txId = outpoint.substring(0, sep);
            int idx = Integer.parseInt(outpoint.substring(sep + 1));
            utxos.save(new Utxo(txId, idx, entry.address(), entry.amount(), entry.height(), entry.coinbase()));
        });

        mempool.clearAll();
        return true;
    }
}

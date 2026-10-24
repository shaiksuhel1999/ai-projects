package com.tekmachain.service;

import com.tekmachain.domain.*;
import com.tekmachain.repo.BlockRepository;
import com.tekmachain.repo.UtxoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Proof-of-Work mining: collect valid mempool transactions, add a coinbase that
 * mints the block reward + fees to the miner, then grind the nonce until the
 * block hash meets the difficulty target. Applying the block updates the UTXO set
 * atomically (spend inputs, create outputs).
 */
@Service
public class MiningService {

    private final BlockRepository blocks;
    private final MempoolService mempool;
    private final TransactionService txService;
    private final BlockchainService blockchain;
    private final ChainService chainService;
    private final ChainLock chainLock;
    private final UtxoRepository utxos;
    private final Economics economics;
    private final DifficultyPolicy difficultyPolicy;

    public MiningService(BlockRepository blocks, MempoolService mempool, TransactionService txService,
                         BlockchainService blockchain, ChainService chainService, ChainLock chainLock,
                         UtxoRepository utxos, Economics economics, DifficultyPolicy difficultyPolicy) {
        this.blocks = blocks;
        this.mempool = mempool;
        this.txService = txService;
        this.blockchain = blockchain;
        this.chainService = chainService;
        this.chainLock = chainLock;
        this.utxos = utxos;
        this.economics = economics;
        this.difficultyPolicy = difficultyPolicy;
    }

    /** The difficulty the next block must satisfy (after any retarget), for display. */
    public int currentDifficulty() {
        Block tip = blockchain.requireLastBlock();
        return nextDifficulty(tip);
    }

    private int nextDifficulty(Block previous) {
        long height = previous.getHeight() + 1;
        long windowStartTs = previous.getTimestamp();
        if (difficultyPolicy.isRetargetHeight(height)) {
            windowStartTs = blocks.findById(height - difficultyPolicy.retargetInterval())
                    .map(Block::getTimestamp).orElse(previous.getTimestamp());
        }
        return difficultyPolicy.difficultyFor(height, previous.getDifficulty(), previous.getTimestamp(), windowStartTs);
    }

    /** The reward actually mintable at a height, respecting halving + the supply cap. */
    public BigDecimal effectiveReward(long height) {
        return economics.effectiveReward(height, circulatingSupply());
    }

    /** Total coins in existence = sum of all unspent outputs (fees are conserved, so this equals coins minted). */
    public BigDecimal circulatingSupply() {
        return utxos.findAll().stream().map(Utxo::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Mines a single block that pays its reward + collected fees to {@code minerAddress}. */
    @Transactional
    public Block mine(String minerAddress) {
        synchronized (chainLock) {
            return doMine(minerAddress);
        }
    }

    private Block doMine(String minerAddress) {
        Block previous = blockchain.requireLastBlock();
        long height = previous.getHeight() + 1;

        // 1. Take valid transactions from the mempool (drop any that no longer validate).
        List<Transaction> selected = new ArrayList<>();
        List<Transaction> included = new ArrayList<>();
        BigDecimal fees = BigDecimal.ZERO;
        for (Transaction tx : mempool.snapshot()) {
            try {
                fees = fees.add(txService.validate(tx));
                selected.add(tx);
                included.add(tx);
            } catch (RuntimeException invalid) {
                included.add(tx); // still evict it from the mempool
            }
        }

        // 2. Coinbase transaction: reward + fees to the miner. Its fake input carries
        //    the height so every coinbase gets a unique transaction id.
        Transaction coinbase = new Transaction();
        coinbase.setCoinbase(true);
        coinbase.setTimestamp(System.currentTimeMillis());
        TxInput cbInput = new TxInput("COINBASE-" + height, (int) height);
        coinbase.getInputs().add(cbInput);
        // Reward follows the halving schedule and the supply cap; fees are always collectable.
        BigDecimal payout = TransactionService.norm(effectiveReward(height).add(fees));
        if (payout.signum() > 0) {
            coinbase.getOutputs().add(new TxOutput(0, minerAddress, payout));
        }
        coinbase.computeAndSetTxId();

        List<Transaction> blockTxs = new ArrayList<>();
        blockTxs.add(coinbase);
        blockTxs.addAll(selected);

        // 3. Build the header at the network-mandated (retargeted) difficulty and do PoW.
        int difficulty = nextDifficulty(previous);
        Block block = new Block();
        block.setHeight(height);
        block.setTimestamp(System.currentTimeMillis());
        block.setPreviousHash(previous.getHash());
        block.setMinerAddress(minerAddress);
        block.setDifficulty(difficulty);
        block.setMerkleRoot(com.tekmachain.crypto.CryptoUtil.merkleRoot(
                blockTxs.stream().map(Transaction::getTxId).toList()));
        block.setTransactions(blockTxs);

        long nonce = 0;
        String hash;
        while (true) {
            block.setNonce(nonce);
            hash = block.computeHash();
            if (difficultyPolicy.meets(hash, difficulty)) {
                break;
            }
            nonce++;
        }
        block.setHash(hash);

        // 4. Persist the block and update the UTXO set atomically.
        blocks.save(block);
        chainService.applyBlockToLedger(blockTxs, (int) height);
        mempool.remove(included);
        return block;
    }

    public int getDifficulty() {
        return currentDifficulty();
    }

    /** Reward the NEXT block would pay (after halving + cap), for display. */
    public BigDecimal getReward() {
        return effectiveReward(blockchain.height() + 1);
    }
}

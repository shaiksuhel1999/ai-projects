package com.tekmachain.service;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.domain.Block;
import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.TxInput;
import com.tekmachain.repo.BlockRepository;
import com.tekmachain.web.dto.Mapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Owns the chain itself: it creates the genesis block on first start, exposes the
 * chain, and validates its integrity (hash links, Merkle roots and Proof-of-Work).
 */
@Service
public class BlockchainService {

    private static final String ZERO_HASH = "0".repeat(64);
    /** Embedded in the genesis coinbase, the way Bitcoin's genesis carries a headline. */
    public static final String GENESIS_MESSAGE =
            "Tekma Coin genesis — a Bitcoin-inspired chain; money you can verify, not trust.";

    private final BlockRepository blocks;
    private final ChainValidator validator;
    private final DifficultyPolicy difficultyPolicy;

    public BlockchainService(BlockRepository blocks, ChainValidator validator, DifficultyPolicy difficultyPolicy) {
        this.blocks = blocks;
        this.validator = validator;
        this.difficultyPolicy = difficultyPolicy;
    }

    @PostConstruct
    @Transactional
    public void initGenesis() {
        if (blocks.count() > 0) {
            return;
        }
        // Genesis coinbase: no spendable outputs, but it carries the founding message
        // in its input (like Bitcoin's coinbase scriptSig).
        Transaction cb = new Transaction();
        cb.setCoinbase(true);
        cb.setTimestamp(0L);
        cb.getInputs().add(new TxInput(GENESIS_MESSAGE, 0));
        cb.computeAndSetTxId();

        Block genesis = new Block();
        genesis.setHeight(0L);
        genesis.setTimestamp(0L); // fixed so the genesis hash is deterministic
        genesis.setPreviousHash(ZERO_HASH);
        genesis.setMinerAddress("GENESIS");
        genesis.setDifficulty(difficultyPolicy.initialDifficulty());
        genesis.setNonce(0L);
        genesis.setTransactions(List.of(cb));
        genesis.setMerkleRoot(CryptoUtil.merkleRoot(List.of(cb.getTxId())));
        genesis.setHash(genesis.computeHash());
        blocks.save(genesis);
    }

    @Transactional(readOnly = true)
    public List<Block> chain() {
        return blocks.findAllByOrderByHeightAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Block> lastBlock() {
        return blocks.findTopByOrderByHeightDesc();
    }

    @Transactional(readOnly = true)
    public Block requireLastBlock() {
        return lastBlock().orElseThrow(() -> new IllegalStateException("Chain not initialised"));
    }

    @Transactional(readOnly = true)
    public Block requireBlock(long height) {
        return blocks.findById(height)
                .orElseThrow(() -> new IllegalArgumentException("No block at height " + height));
    }

    @Transactional(readOnly = true)
    public long height() {
        return lastBlock().map(Block::getHeight).orElse(-1L);
    }

    /**
     * Re-validates the whole chain from genesis by replaying it: hash links,
     * Merkle roots, Proof-of-Work, every transaction signature, no double-spends,
     * and correct coinbase rewards. Delegates to the same {@link ChainValidator}
     * that decides whether to adopt a peer's chain.
     */
    @Transactional(readOnly = true)
    public ValidationReport validateChain() {
        ChainValidator.Result r = validator.validate(chain().stream().map(Mapper::block).toList());
        return new ValidationReport(r.valid(), r.length(), r.problems());
    }

    public record ValidationReport(boolean valid, int length, List<String> problems) {
    }
}

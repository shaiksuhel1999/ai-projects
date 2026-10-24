package com.tekmachain;

import com.tekmachain.domain.Block;
import com.tekmachain.domain.Wallet;
import com.tekmachain.repo.BlockRepository;
import com.tekmachain.repo.UtxoRepository;
import com.tekmachain.service.*;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises Phase 5 consensus without two live HTTP nodes: build a real chain,
 * capture it as the DTOs a peer would gossip, wipe the local DB back to genesis
 * (a "fresh node"), then replay those DTOs through the real block-reception and
 * chain-adoption code — and confirm the ledger reconstructs perfectly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class P2PConsensusTest {

    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired MiningService mining;
    @Autowired BlockchainService blockchain;
    @Autowired ChainService chainService;
    @Autowired ChainValidator validator;
    @Autowired BlockRepository blockRepo;
    @Autowired UtxoRepository utxoRepo;

    private static BigDecimal tek(String v) {
        return new BigDecimal(v).setScale(8);
    }

    /** Alice mines, pays Bob, Alice mines again, Bob mines. Returns the chain as wire DTOs. */
    private List<Dtos.BlockDto> buildReferenceChain(Wallet alice, Wallet bob) {
        mining.mine(alice.getAddress());                                             // block 1: Alice +50
        transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("10"), tek("0.1"));
        mining.mine(alice.getAddress());                                             // block 2: tx confirms
        mining.mine(bob.getAddress());                                               // block 3: Bob +50
        return blockchain.chain().stream().map(Mapper::block).toList();
    }

    private void resetToGenesisOnly() {
        List<Long> nonGenesis = blockchain.chain().stream()
                .map(Block::getHeight).filter(h -> h > 0).toList();
        blockRepo.deleteAllById(nonGenesis);
        utxoRepo.deleteAll();
        assertEquals(0, blockchain.height(), "reset leaves only genesis");
    }

    @Test
    void validatorAcceptsGoodChainAndRejectsTampering() {
        Wallet alice = wallets.create("Alice");
        Wallet bob = wallets.create("Bob");
        List<Dtos.BlockDto> chain = buildReferenceChain(alice, bob);

        assertTrue(validator.validate(chain).valid(), "a well-formed chain must validate");

        // Tamper: inflate an output amount in block 3's coinbase.
        Dtos.BlockDto b3 = chain.get(3);
        Dtos.TransactionDto cb = b3.transactions().get(0);
        Dtos.TransactionDto forgedTx = new Dtos.TransactionDto(cb.txId(), cb.coinbase(), cb.timestamp(),
                cb.inputs(), List.of(new Dtos.TxOutputDto(0, cb.outputs().get(0).address(), tek("999999"))));
        Dtos.BlockDto forgedBlock = new Dtos.BlockDto(b3.height(), b3.timestamp(), b3.previousHash(),
                b3.merkleRoot(), b3.difficulty(), b3.nonce(), b3.minerAddress(), b3.hash(), List.of(forgedTx));
        List<Dtos.BlockDto> tampered = List.of(chain.get(0), chain.get(1), chain.get(2), forgedBlock);

        assertFalse(validator.validate(tampered).valid(), "an over-minting coinbase must be rejected");
    }

    @Test
    void freshNodeAdoptsLongerChainAndRebuildsUtxoSet() {
        Wallet alice = wallets.create("Alice");
        Wallet bob = wallets.create("Bob");
        List<Dtos.BlockDto> peerChain = buildReferenceChain(alice, bob); // length 4

        // Balances we expect the ledger to reconstruct.
        assertEquals(0, tek("90").compareTo(wallets.balanceOf(alice.getAddress())));
        assertEquals(0, tek("60").compareTo(wallets.balanceOf(bob.getAddress())));

        // Simulate a fresh node that only has genesis.
        resetToGenesisOnly();
        assertEquals(0, tek("0").compareTo(wallets.balanceOf(alice.getAddress())), "fresh node has no coins");

        // Adopt the peer's longer chain.
        assertTrue(chainService.replaceChain(peerChain), "longer valid chain is adopted");

        assertEquals(3, blockchain.height(), "chain rebuilt to height 3");
        assertEquals(0, tek("90").compareTo(wallets.balanceOf(alice.getAddress())), "Alice balance rebuilt");
        assertEquals(0, tek("60").compareTo(wallets.balanceOf(bob.getAddress())), "Bob balance rebuilt");
        assertTrue(blockchain.validateChain().valid(), "adopted chain validates");

        // A shorter chain must NOT replace the one we now hold.
        List<Dtos.BlockDto> shorter = peerChain.subList(0, 2);
        assertFalse(chainService.replaceChain(shorter), "a shorter chain is rejected");
    }

    @Test
    void nodeAcceptsGossipedBlocksOneByOne() {
        Wallet alice = wallets.create("Alice");
        Wallet bob = wallets.create("Bob");
        List<Dtos.BlockDto> peerChain = buildReferenceChain(alice, bob);

        resetToGenesisOnly();

        // Receive each block as if gossiped by a peer; each must extend our tip cleanly.
        assertEquals(ChainService.AppendOutcome.ACCEPTED, chainService.tryAppendExternalBlock(peerChain.get(1)));
        assertEquals(ChainService.AppendOutcome.ACCEPTED, chainService.tryAppendExternalBlock(peerChain.get(2)));
        assertEquals(ChainService.AppendOutcome.ACCEPTED, chainService.tryAppendExternalBlock(peerChain.get(3)));

        assertEquals(3, blockchain.height());
        assertEquals(0, tek("90").compareTo(wallets.balanceOf(alice.getAddress())));
        assertEquals(0, tek("60").compareTo(wallets.balanceOf(bob.getAddress())));

        // A block that does not extend the tip is not force-applied.
        assertEquals(ChainService.AppendOutcome.NOT_EXTENDING,
                chainService.tryAppendExternalBlock(peerChain.get(1)));
    }
}

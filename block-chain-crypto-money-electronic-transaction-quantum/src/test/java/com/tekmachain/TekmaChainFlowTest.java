package com.tekmachain;

import com.tekmachain.domain.Block;
import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.Wallet;
import com.tekmachain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end exercise of the ledger without the web server:
 * genesis -> mine -> sign a UTXO payment -> mine again -> verify balances,
 * Proof-of-Work and full-chain validation. This is the real proof the logic works.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class TekmaChainFlowTest {

    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired MiningService mining;
    @Autowired BlockchainService blockchain;
    @Autowired MempoolService mempool;

    private static BigDecimal tek(String v) {
        return new BigDecimal(v).setScale(8);
    }

    @Test
    void fullPaymentLifecycle() {
        // Genesis exists on startup.
        assertEquals(0, blockchain.height(), "chain starts at the genesis block");

        Wallet alice = wallets.create("Alice");
        Wallet bob = wallets.create("Bob");
        assertTrue(alice.getAddress().startsWith("TEK"));

        // 1. Mine a block to Alice -> she receives the block reward (coins enter existence).
        Block b1 = mining.mine(alice.getAddress());
        assertEquals(1, b1.getHeight());
        assertTrue(com.tekmachain.service.DifficultyPolicy.leadingZeroBits(b1.getHash()) >= b1.getDifficulty(),
                "block must meet PoW target");
        assertEquals(0, tek("50").compareTo(wallets.balanceOf(alice.getAddress())), "Alice mined 50 TEK");

        // 2. Alice pays Bob 10 TEK with a 0.1 fee -> lands in the mempool.
        Transaction tx = transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("10"), tek("0.1"));
        assertFalse(tx.isCoinbase());
        assertEquals(1, mempool.size(), "one pending transaction");

        // 3. Mine again (Alice mines) -> tx confirms; miner collects reward + fee.
        Block b2 = mining.mine(alice.getAddress());
        assertEquals(2, b2.getHeight());
        assertEquals(b1.getHash(), b2.getPreviousHash(), "blocks are hash-linked");
        assertEquals(0, mempool.size(), "mempool cleared after mining");

        // Balances: Bob has 10; Alice has 90 = change(39.9) + reward+fee(50.1).
        assertEquals(0, tek("10").compareTo(wallets.balanceOf(bob.getAddress())), "Bob received 10 TEK");
        assertEquals(0, tek("90").compareTo(wallets.balanceOf(alice.getAddress())), "Alice = change + reward + fee");

        // 4. The whole chain validates (hashes, links, Merkle roots, PoW).
        BlockchainService.ValidationReport report = blockchain.validateChain();
        assertTrue(report.valid(), () -> "chain should be valid, problems: " + report.problems());
        assertEquals(3, report.length(), "genesis + 2 mined blocks");
    }

    @Test
    void genesisCarriesTheFoundingMessage() {
        Block genesis = blockchain.chain().get(0);
        assertEquals(0, genesis.getHeight());
        String message = genesis.getTransactions().get(0).getInputs().get(0).getPrevTxId();
        assertTrue(message.contains("Tekma"), "genesis coinbase should embed the founding message");
    }

    @Test
    void cannotSpendMoreThanYouHave() {
        Wallet poor = wallets.create("Poor");
        Wallet other = wallets.create("Other");
        assertThrows(IllegalStateException.class,
                () -> transactions.createPayment(poor.getAddress(), other.getAddress(), tek("5"), tek("0")),
                "spending with no UTXOs must fail");
    }

    @Test
    void rejectsTransactionWithMismatchedId() {
        Wallet alice = wallets.create("Alice");
        mining.mine(alice.getAddress());
        Wallet bob = wallets.create("Bob");

        Transaction tx = transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("5"), tek("0"));
        tx.setTxId("deadbeef".repeat(8));   // a valid-length but wrong id, as a bad /raw client might send
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> transactions.validate(tx));
        assertTrue(ex.getMessage().contains("does not match"), "id-integrity check must fire");
    }
}

package com.tekmachain;

import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.Wallet;
import com.tekmachain.service.BlockchainService;
import com.tekmachain.service.MiningService;
import com.tekmachain.service.TransactionService;
import com.tekmachain.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coinbase maturity: a freshly mined reward can't be spent until it has enough
 * confirmations — a real Bitcoin rule that protects against spending coins from a
 * block that might be reorged away. Enabled here with a maturity of 3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {"tekmachain.coinbase-maturity=3"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class CoinbaseMaturityTest {

    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired MiningService mining;
    @Autowired BlockchainService blockchain;

    private static BigDecimal tek(String v) { return new BigDecimal(v).setScale(8); }

    @Test
    void immatureCoinbaseCannotBeSpentUntilItMatures() {
        Wallet alice = wallets.create("Alice");
        Wallet filler = wallets.create("Filler");
        Wallet bob = wallets.create("Bob");

        mining.mine(alice.getAddress());   // block 1: Alice's coinbase (50 TEK) at height 1

        // The coins exist in her total, but none are spendable yet.
        assertEquals(0, tek("50").compareTo(wallets.balanceOf(alice.getAddress())));
        assertEquals(0, tek("0").compareTo(wallets.spendableBalanceOf(alice.getAddress())), "immature: nothing spendable");
        assertEquals(0, tek("50").compareTo(wallets.immatureBalanceOf(alice.getAddress())));
        assertThrows(IllegalStateException.class,
                () -> transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("10"), tek("0")),
                "spending an immature coinbase must fail");

        // Advance the chain with blocks mined to someone else so Alice gets no new coins.
        mining.mine(filler.getAddress());  // block 2
        mining.mine(filler.getAddress());  // block 3  -> Alice's coinbase now has enough confirmations

        assertEquals(0, tek("50").compareTo(wallets.spendableBalanceOf(alice.getAddress())), "now mature");
        Transaction tx = transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("10"), tek("0"));
        assertNotNull(tx.getTxId(), "mature coinbase spends fine");
    }
}

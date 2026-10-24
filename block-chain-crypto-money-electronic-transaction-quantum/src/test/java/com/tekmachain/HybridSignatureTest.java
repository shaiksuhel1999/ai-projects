package com.tekmachain;

import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.TxInput;
import com.tekmachain.domain.Wallet;
import com.tekmachain.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase "hybrid": a quantum-safe wallet signs every input with BOTH ECDSA and
 * ML-DSA (Dilithium), the node verifies both, and the whole chain still validates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HybridSignatureTest {

    @Autowired WalletService wallets;
    @Autowired TransactionService transactions;
    @Autowired MiningService mining;
    @Autowired BlockchainService blockchain;

    private static BigDecimal tek(String v) { return new BigDecimal(v).setScale(8); }

    @Test
    void hybridWalletSignsWithBothAndValidates() {
        Wallet alice = wallets.createHybrid("Alice-Q");
        assertTrue(alice.isHybrid(), "wallet carries ML-DSA keys");

        mining.mine(alice.getAddress());                       // Alice: 50 TEK
        Wallet bob = wallets.create("Bob");

        Transaction tx = transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("10"), tek("0.1"));
        TxInput in = tx.getInputs().get(0);
        assertNotNull(in.getDilithiumSignature(), "input carries an ML-DSA signature");
        assertNotNull(in.getDilithiumPublicKey(), "input carries the ML-DSA public key");
        assertTrue(in.getDilithiumSignature().length() > in.getSignature().length() * 5,
                "ML-DSA signature dwarfs the ECDSA one");

        mining.mine(alice.getAddress());                       // Alice confirms the hybrid spend

        assertEquals(0, tek("10").compareTo(wallets.balanceOf(bob.getAddress())), "Bob received 10");
        // Alice = change 39.9 + reward+fee 50.1
        assertEquals(0, tek("90").compareTo(wallets.balanceOf(alice.getAddress())), "Alice change + reward");
        assertTrue(blockchain.validateChain().valid(), "chain with hybrid tx validates");
    }

    @Test
    void tamperedMldsaSignatureIsRejected() {
        Wallet alice = wallets.createHybrid("Alice-Q");
        mining.mine(alice.getAddress());
        Wallet bob = wallets.create("Bob");

        Transaction tx = transactions.createPayment(alice.getAddress(), bob.getAddress(), tek("5"), tek("0"));
        TxInput in = tx.getInputs().get(0);
        String sig = in.getDilithiumSignature();
        in.setDilithiumSignature((sig.charAt(0) == '0' ? '1' : '0') + sig.substring(1)); // flip one hex digit

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> transactions.validate(tx));
        assertTrue(ex.getMessage().contains("ML-DSA"), "ML-DSA verification must fail");
    }
}

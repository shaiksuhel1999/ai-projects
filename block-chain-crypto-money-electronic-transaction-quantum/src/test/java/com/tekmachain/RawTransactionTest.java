package com.tekmachain;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.crypto.SigningData;
import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.TxInput;
import com.tekmachain.domain.TxOutput;
import com.tekmachain.domain.Utxo;
import com.tekmachain.repo.UtxoRepository;
import com.tekmachain.service.MiningService;
import com.tekmachain.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors the self-custody {@code POST /api/transactions/raw} path on the server:
 * a client builds, signs and ids a transaction itself, and the node accepts it only
 * if the signature, ownership AND the transaction-id-integrity checks all hold.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class RawTransactionTest {

    @Autowired MiningService mining;
    @Autowired TransactionService transactions;
    @Autowired UtxoRepository utxos;

    private Transaction buildClientTx(KeyPair kp, String pub, String from, String to, Utxo u) {
        long ts = System.currentTimeMillis();
        List<SigningData.InRef> ins = List.of(new SigningData.InRef(u.getTxId(), u.getOutIndex()));
        List<SigningData.OutRef> outs = List.of(
                new SigningData.OutRef(to, new BigDecimal("10")),
                new SigningData.OutRef(from, new BigDecimal("40")));
        byte[] signingData = SigningData.build(false, ts, ins, outs);
        String sig = CryptoUtil.sign(signingData, kp.getPrivate());

        Transaction tx = new Transaction();
        tx.setCoinbase(false);
        tx.setTimestamp(ts);
        tx.setTxId(CryptoUtil.sha256Hex(signingData));
        TxInput in = new TxInput(u.getTxId(), u.getOutIndex());
        in.setSignature(sig);
        in.setPublicKey(pub);
        tx.getInputs().add(in);
        tx.getOutputs().add(new TxOutput(0, to, new BigDecimal("10").setScale(8)));
        tx.getOutputs().add(new TxOutput(1, from, new BigDecimal("40").setScale(8)));
        return tx;
    }

    @Test
    void nodeAcceptsAClientBuiltSignedTransaction() {
        KeyPair kp = CryptoUtil.generateKeyPair();
        String pub = CryptoUtil.encodePublicKey(kp.getPublic());
        String from = CryptoUtil.addressFromPublicKeyHex(pub);
        String to = CryptoUtil.addressFromPublicKeyHex(
                CryptoUtil.encodePublicKey(CryptoUtil.generateKeyPair().getPublic()));

        mining.mine(from);                           // give `from` a 50 TEK coinbase UTXO
        Utxo u = utxos.findByAddress(from).get(0);

        Transaction tx = buildClientTx(kp, pub, from, to, u);
        assertEquals(0, BigDecimal.ZERO.compareTo(transactions.validate(tx)), "50 in, 50 out -> zero fee, valid");

        // Tamper the client's transaction id: the node must reject it.
        tx.setTxId("0".repeat(64));
        assertThrows(IllegalStateException.class, () -> transactions.validate(tx));
    }
}

package com.tekmachain.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure cryptography checks — no Spring, no database. */
class CryptoUtilTest {

    @Test
    void signAndVerifyRoundTrip() {
        KeyPair kp = CryptoUtil.generateKeyPair();
        String pub = CryptoUtil.encodePublicKey(kp.getPublic());
        byte[] msg = "pay 10 TEK to Bob".getBytes(StandardCharsets.UTF_8);

        String sig = CryptoUtil.sign(msg, kp.getPrivate());

        assertTrue(CryptoUtil.verify(msg, sig, pub), "valid signature must verify");
        byte[] tampered = "pay 1000 TEK to Bob".getBytes(StandardCharsets.UTF_8);
        assertFalse(CryptoUtil.verify(tampered, sig, pub), "signature must fail on modified data");
    }

    @Test
    void addressIsDeterministicAndUnique() {
        KeyPair a = CryptoUtil.generateKeyPair();
        KeyPair b = CryptoUtil.generateKeyPair();
        String pubA = CryptoUtil.encodePublicKey(a.getPublic());
        String pubB = CryptoUtil.encodePublicKey(b.getPublic());

        String addrA1 = CryptoUtil.addressFromPublicKeyHex(pubA);
        String addrA2 = CryptoUtil.addressFromPublicKeyHex(pubA);

        assertEquals(addrA1, addrA2, "same key -> same address");
        assertTrue(addrA1.startsWith("TEK"), "address should be NVA-prefixed");
        assertNotEquals(addrA1, CryptoUtil.addressFromPublicKeyHex(pubB), "different keys -> different addresses");
    }

    @Test
    void merkleRootIsStableAndSensitive() {
        String r1 = CryptoUtil.merkleRoot(List.of("a", "b", "c", "d"));
        String r2 = CryptoUtil.merkleRoot(List.of("a", "b", "c", "d"));
        String r3 = CryptoUtil.merkleRoot(List.of("a", "b", "c", "X"));

        assertEquals(r1, r2);
        assertNotEquals(r1, r3, "changing one tx must change the Merkle root");
        assertEquals(64, r1.length(), "SHA-256 hex is 64 chars");
    }
}

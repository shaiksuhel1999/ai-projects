package com.tekmachain;

import com.tekmachain.crypto.CryptoUtil;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 7 interop proof. These vectors were generated entirely in the browser by
 * {@code tekma-crypto.js} (pure-JS secp256k1) from a fixed private key. This test
 * asserts the Java node accepts them — same public-key encoding, same address, and
 * a signature the node verifies — proving the client can sign without the server
 * ever seeing the private key.
 */
class ClientCryptoVectorTest {

    private static final String D_HEX = "1a2b3c4d5e6f00112233445566778899aabbccddeeff00112233445566778899";
    // Frozen vector: this exact string was signed in the browser — do not rebrand it.
    private static final String MSG = "NovaChain-selfcustody-test";
    private static final String JS_PUB =
            "3056301006072a8648ce3d020106052b8104000a0342000417859d291d5ddb7c19111880e35396e198a6f661fcf23097460040e2ca56cd0a044edfcdadca138d10adff9823aba2296971b4cf113651d805946db2aebb3c60";
    private static final String JS_ADDRESS = "TEKNMXBzUKyxLiEN1VFwj5Q27cSzeXx3mVyWo";
    private static final String JS_SIG =
            "30440220366a38e102fd1811f631d144da3811f46091a9282722aae3e1b0df8a53ad86cd02206183650b291411647f37e44b8f82a38ade11d05fae1925209acae004b9f74329";
    private static final String JS_TXID = "971e6e48500d5838d7f75eb11ea73564a4c01f27cde1b5302b0d6929e48dd235";

    @Test
    void browserGeneratedKeyAndSignatureInteroperateWithTheNode() throws Exception {
        byte[] msg = MSG.getBytes(StandardCharsets.UTF_8);

        // 1. The node can parse the browser's public key.
        assertDoesNotThrow(() -> CryptoUtil.publicKeyFromHex(JS_PUB));

        // 2. The node derives the SAME address the browser did.
        assertEquals(JS_ADDRESS, CryptoUtil.addressFromPublicKeyHex(JS_PUB), "address must match");

        // 3. The node VERIFIES a signature produced in the browser.
        assertTrue(CryptoUtil.verify(msg, JS_SIG, JS_PUB), "node must verify the browser signature");
        assertFalse(CryptoUtil.verify("tampered".getBytes(StandardCharsets.UTF_8), JS_SIG, JS_PUB));

        // 4. Same SHA-256 (used for transaction ids).
        assertEquals(JS_TXID, CryptoUtil.sha256Hex(MSG));

        // 5. Bonus: the public key the browser derived from d equals the one the node derives from d.
        ECNamedCurveParameterSpec sp = ECNamedCurveTable.getParameterSpec("secp256k1");
        ECPoint q = sp.getG().multiply(new BigInteger(D_HEX, 16)).normalize();
        KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
        PublicKey pub = kf.generatePublic(new ECPublicKeySpec(q, sp));
        assertEquals(JS_PUB, CryptoUtil.toHex(pub.getEncoded()), "browser public key must equal the node's");
    }
}

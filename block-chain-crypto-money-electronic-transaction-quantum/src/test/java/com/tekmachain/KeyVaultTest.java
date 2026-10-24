package com.tekmachain;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.custody.KeyVault;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

/** HSM-style custody: keys are sealed at rest and signing happens inside the vault. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KeyVaultTest {

    @Autowired KeyVault vault;

    @Test
    void sealsKeysAndSignsWithoutExposingThem() {
        KeyPair kp = CryptoUtil.generateKeyPair();
        String privHex = CryptoUtil.encodePrivateKey(kp.getPrivate());
        String pubHex = CryptoUtil.encodePublicKey(kp.getPublic());
        byte[] msg = "sign me".getBytes(StandardCharsets.UTF_8);

        String sealed = vault.seal(privHex);
        assertTrue(vault.isSealed(sealed), "blob is marked sealed");
        assertFalse(sealed.contains(privHex), "plaintext key must not appear in the sealed blob");

        // The vault signs using the sealed key; the result verifies under the public key.
        String sig = vault.signEcdsa(sealed, msg);
        assertTrue(CryptoUtil.verify(msg, sig, pubHex), "vault signature must verify");

        // Two seals of the same key differ (random IV) but both work.
        assertNotEquals(sealed, vault.seal(privHex));
    }

    @Test
    void acceptsLegacyPlaintextKeysForBackwardCompatibility() {
        KeyPair kp = CryptoUtil.generateKeyPair();
        String privHex = CryptoUtil.encodePrivateKey(kp.getPrivate());
        String pubHex = CryptoUtil.encodePublicKey(kp.getPublic());
        byte[] msg = "legacy".getBytes(StandardCharsets.UTF_8);

        // A pre-vault plaintext key (no "v1:" prefix) still signs.
        String sig = vault.signEcdsa(privHex, msg);
        assertTrue(CryptoUtil.verify(msg, sig, pubHex));
    }
}

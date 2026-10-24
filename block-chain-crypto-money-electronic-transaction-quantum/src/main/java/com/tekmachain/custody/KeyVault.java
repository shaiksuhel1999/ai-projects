package com.tekmachain.custody;

import com.tekmachain.crypto.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * A stand-in for an HSM / secure key-custody module.
 *
 * <p>Private keys are never held in plaintext at rest: they are sealed with
 * AES-256-GCM under a master key (the "HSM seal") and only ever decrypted inside
 * this component, which performs the signature and returns just the result — the
 * rest of the application never sees a raw private key. In production the master
 * key would live in a real HSM / KMS; here it comes from
 * {@code tekmachain.custody.master-key} (base64, 32 bytes) via env/secret.</p>
 */
@Component
public class KeyVault {

    private static final Logger log = LoggerFactory.getLogger(KeyVault.class);
    private static final String PREFIX = "v1:";       // marks a sealed blob (vs legacy plaintext)
    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    // A fixed development seal so restarts can still decrypt. OVERRIDE in production.
    private static final String DEV_KEY_B64 = "bm92YWNoYWluLWRldi1zZWFsLWtleS0zMmJ5dGVzLXgh";

    private final SecureRandom random = new SecureRandom();
    private SecretKeySpec masterKey;

    @Value("${tekmachain.custody.master-key:}")
    private String configuredKey;

    @jakarta.annotation.PostConstruct
    void init() {
        byte[] key;
        if (configuredKey != null && !configuredKey.isBlank()) {
            key = Base64.getDecoder().decode(configuredKey.trim());
        } else {
            key = Base64.getDecoder().decode(DEV_KEY_B64);
            log.warn("KeyVault is using the built-in DEVELOPMENT seal — set tekmachain.custody.master-key for production");
        }
        if (key.length != 32) {
            byte[] fixed = new byte[32];
            System.arraycopy(key, 0, fixed, 0, Math.min(key.length, 32));
            key = fixed;
        }
        this.masterKey = new SecretKeySpec(key, "AES");
    }

    /** Seal a hex-encoded private key for storage. */
    public String seal(String hexPrivateKey) {
        try {
            byte[] iv = new byte[IV_LEN];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(hexPrivateKey.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Key sealing failed", e);
        }
    }

    /** Unseal to the hex key. Legacy (unprefixed) values are treated as plaintext. */
    private String unseal(String blob) {
        if (blob == null) {
            throw new IllegalStateException("No key material");
        }
        if (!blob.startsWith(PREFIX)) {
            return blob; // backward compatible with any pre-vault plaintext keys
        }
        try {
            byte[] all = Base64.getDecoder().decode(blob.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LEN];
            System.arraycopy(all, 0, iv, 0, IV_LEN);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = c.doFinal(all, IV_LEN, all.length - IV_LEN);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Key unsealing failed (wrong master key?)", e);
        }
    }

    /** Sign with the sealed ECDSA key without ever exposing it to the caller. */
    public String signEcdsa(String sealedKey, byte[] data) {
        return CryptoUtil.sign(data, CryptoUtil.privateKeyFromHex(unseal(sealedKey)));
    }

    /** Sign with the sealed ML-DSA (Dilithium) key. */
    public String signDilithium(String sealedKey, byte[] data) {
        return CryptoUtil.signDilithium(data, unseal(sealedKey));
    }

    public boolean isSealed(String blob) {
        return blob != null && blob.startsWith(PREFIX);
    }
}

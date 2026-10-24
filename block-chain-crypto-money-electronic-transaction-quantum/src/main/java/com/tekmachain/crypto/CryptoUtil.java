package com.tekmachain.crypto;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * All the low-level cryptography TekmaChain needs, in one place.
 *
 * <ul>
 *   <li>SHA-256 &amp; RIPEMD-160 hashing (the same primitives Bitcoin uses)</li>
 *   <li>secp256k1 key pairs + ECDSA signing / verification (via Bouncy Castle,
 *       because the JDK removed secp256k1 from its built-in provider)</li>
 *   <li>Base58Check addresses that look like {@code NVA...}</li>
 *   <li>Merkle roots for block headers</li>
 * </ul>
 */
public final class CryptoUtil {

    public static final String CURVE = "secp256k1";
    private static final String BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    /** Version byte prepended before Base58Check so every address starts with "TEK". */
    private static final byte ADDRESS_VERSION = 0x35;

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (Security.getProvider("BCPQC") == null) {
            Security.addProvider(new BouncyCastlePQCProvider());
        }
    }

    private CryptoUtil() {
    }

    // ---------------------------------------------------------------------
    // Hashing
    // ---------------------------------------------------------------------

    public static byte[] sha256(byte[] input) {
        return digest("SHA-256", input);
    }

    public static String sha256Hex(String input) {
        return toHex(sha256(input.getBytes(StandardCharsets.UTF_8)));
    }

    public static String sha256Hex(byte[] input) {
        return toHex(sha256(input));
    }

    public static byte[] ripemd160(byte[] input) {
        return digest("RIPEMD160", input);
    }

    private static byte[] digest(String algorithm, byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm, "BC");
            return md.digest(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Missing algorithm " + algorithm, e);
        }
    }

    // ---------------------------------------------------------------------
    // Keys
    // ---------------------------------------------------------------------

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("ECDSA", "BC");
            g.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to generate secp256k1 key pair", e);
        }
    }

    public static String encodePrivateKey(PrivateKey key) {
        return toHex(key.getEncoded()); // PKCS#8
    }

    public static String encodePublicKey(PublicKey key) {
        return toHex(key.getEncoded()); // X.509
    }

    public static PrivateKey privateKeyFromHex(String hex) {
        try {
            KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
            return kf.generatePrivate(new PKCS8EncodedKeySpec(fromHex(hex)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Bad private key", e);
        }
    }

    public static PublicKey publicKeyFromHex(String hex) {
        try {
            KeyFactory kf = KeyFactory.getInstance("ECDSA", "BC");
            return kf.generatePublic(new X509EncodedKeySpec(fromHex(hex)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Bad public key", e);
        }
    }

    // ---------------------------------------------------------------------
    // Signatures
    // ---------------------------------------------------------------------

    public static String sign(byte[] data, PrivateKey key) {
        try {
            Signature s = Signature.getInstance("SHA256withECDSA", "BC");
            s.initSign(key);
            s.update(data);
            return toHex(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Signing failed", e);
        }
    }

    public static boolean verify(byte[] data, String signatureHex, String publicKeyHex) {
        try {
            Signature s = Signature.getInstance("SHA256withECDSA", "BC");
            s.initVerify(publicKeyFromHex(publicKeyHex));
            s.update(data);
            return s.verify(fromHex(signatureHex));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Addresses  (HASH160 -> Base58Check, Bitcoin style)
    // ---------------------------------------------------------------------

    public static String addressFromPublicKeyHex(String publicKeyHex) {
        return addressFromHash160(ripemd160(sha256(fromHex(publicKeyHex))));
    }

    /**
     * The address for a possibly-hybrid key. If {@code dilithiumPublicKeyHex} is
     * present, the address commits to BOTH keys — so a quantum attacker who forged
     * the ECDSA key still couldn't produce an address-matching spend without the
     * ML-DSA key too.
     */
    public static String addressFromKeys(String ecdsaPublicKeyHex, String dilithiumPublicKeyHex) {
        if (dilithiumPublicKeyHex == null || dilithiumPublicKeyHex.isBlank()) {
            return addressFromPublicKeyHex(ecdsaPublicKeyHex);
        }
        byte[] combined = concat(fromHex(ecdsaPublicKeyHex), fromHex(dilithiumPublicKeyHex));
        return addressFromHash160(ripemd160(sha256(combined)));
    }

    private static String addressFromHash160(byte[] pubKeyHash) {
        byte[] payload = new byte[1 + pubKeyHash.length];
        payload[0] = ADDRESS_VERSION;
        System.arraycopy(pubKeyHash, 0, payload, 1, pubKeyHash.length);

        byte[] checksum = sha256(sha256(payload));
        byte[] full = new byte[payload.length + 4];
        System.arraycopy(payload, 0, full, 0, payload.length);
        System.arraycopy(checksum, 0, full, payload.length, 4);
        return "TEK" + base58Encode(full);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    // ---------------------------------------------------------------------
    // ML-DSA / CRYSTALS-Dilithium  (post-quantum, for hybrid signatures)
    // ---------------------------------------------------------------------

    public static KeyPair generateDilithiumKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("Dilithium", "BCPQC");
            g.initialize(DilithiumParameterSpec.dilithium2, new SecureRandom());
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to generate Dilithium key pair", e);
        }
    }

    public static String encodeDilithiumPublic(PublicKey key) {
        return toHex(key.getEncoded());
    }

    public static String encodeDilithiumPrivate(PrivateKey key) {
        return toHex(key.getEncoded());
    }

    public static String signDilithium(byte[] data, String privateKeyHex) {
        try {
            KeyFactory kf = KeyFactory.getInstance("Dilithium", "BCPQC");
            PrivateKey key = kf.generatePrivate(new PKCS8EncodedKeySpec(fromHex(privateKeyHex)));
            Signature s = Signature.getInstance("Dilithium", "BCPQC");
            s.initSign(key);
            s.update(data);
            return toHex(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Dilithium signing failed", e);
        }
    }

    public static boolean verifyDilithium(byte[] data, String signatureHex, String publicKeyHex) {
        try {
            KeyFactory kf = KeyFactory.getInstance("Dilithium", "BCPQC");
            PublicKey key = kf.generatePublic(new X509EncodedKeySpec(fromHex(publicKeyHex)));
            Signature s = Signature.getInstance("Dilithium", "BCPQC");
            s.initVerify(key);
            s.update(data);
            return s.verify(fromHex(signatureHex));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Merkle root
    // ---------------------------------------------------------------------

    /** Folds a list of transaction ids into a single Merkle root hash. */
    public static String merkleRoot(List<String> txIds) {
        if (txIds == null || txIds.isEmpty()) {
            return sha256Hex("");
        }
        List<String> level = new ArrayList<>(txIds);
        while (level.size() > 1) {
            if (level.size() % 2 == 1) {
                level.add(level.get(level.size() - 1)); // duplicate the last, like Bitcoin
            }
            List<String> next = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                next.add(sha256Hex(level.get(i) + level.get(i + 1)));
            }
            level = next;
        }
        return level.get(0);
    }

    // ---------------------------------------------------------------------
    // Hex + Base58 helpers
    // ---------------------------------------------------------------------

    public static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static byte[] fromHex(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private static String base58Encode(byte[] input) {
        StringBuilder sb = new StringBuilder();
        BigInteger value = new BigInteger(1, input);
        BigInteger base = BigInteger.valueOf(58);
        while (value.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = value.divideAndRemainder(base);
            sb.insert(0, BASE58.charAt(divmod[1].intValue()));
            value = divmod[0];
        }
        for (byte b : input) { // preserve leading zero bytes as '1'
            if (b == 0) {
                sb.insert(0, '1');
            } else {
                break;
            }
        }
        return sb.toString();
    }
}

package com.tekmachain.pq;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.DilithiumParameterSpec;
import org.bouncycastle.pqc.jcajce.spec.FalconParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 9 — post-quantum research.
 *
 * <p>TekmaChain signs with secp256k1 ECDSA, exactly like Bitcoin. A large enough
 * quantum computer running Shor's algorithm could recover an ECDSA private key
 * from its public key, which would let it forge signatures and steal coins. The
 * defence is post-quantum signature schemes (NIST's ML-DSA / CRYSTALS-Dilithium,
 * Falcon, …) whose security does not rest on the discrete-log problem.</p>
 *
 * <p>This service benchmarks ECDSA against those schemes head-to-head — key sizes,
 * signature sizes and sign/verify speed — so the trade-offs are concrete rather
 * than abstract. (Post-quantum signatures are dramatically larger, which is the
 * real cost of putting them on a blockchain.)</p>
 */
@Service
public class PostQuantumService {

    private static final Logger log = LoggerFactory.getLogger(PostQuantumService.class);
    private static final byte[] MESSAGE = "TekmaChain post-quantum benchmark — sign this block header".getBytes();
    private static final int SIGN_ITERS = 20;
    private static final int KEYGEN_ITERS = 5;

    static {
        if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
        if (Security.getProvider("BCPQC") == null) Security.addProvider(new BouncyCastlePQCProvider());
    }

    public record PqRow(String algorithm, String family, boolean quantumResistant,
                        int publicKeyBytes, int privateKeyBytes, int signatureBytes,
                        double keygenMs, double signMs, double verifyMs, boolean verified) {
    }

    public record PqReport(List<PqRow> rows, String note) {
    }

    public PqReport benchmark() {
        List<PqRow> rows = new ArrayList<>();
        add(rows, "ECDSA (secp256k1)", "Classical", false,
                "ECDSA", "BC", new ECGenParameterSpec("secp256k1"), "SHA256withECDSA");
        add(rows, "ML-DSA / Dilithium-2", "Post-Quantum", true,
                "Dilithium", "BCPQC", DilithiumParameterSpec.dilithium2, "Dilithium");
        add(rows, "ML-DSA / Dilithium-3", "Post-Quantum", true,
                "Dilithium", "BCPQC", DilithiumParameterSpec.dilithium3, "Dilithium");
        add(rows, "Falcon-512", "Post-Quantum", true,
                "Falcon", "BCPQC", FalconParameterSpec.falcon_512, "Falcon");

        return new PqReport(rows,
                "ECDSA is compact and fast but its private key is recoverable by a quantum computer (Shor's algorithm). "
                        + "The post-quantum schemes remain secure, at the cost of much larger keys and signatures.");
    }

    private void add(List<PqRow> rows, String label, String family, boolean qr,
                     String kpgAlgo, String provider, AlgorithmParameterSpec spec, String sigAlgo) {
        try {
            rows.add(bench(label, family, qr, kpgAlgo, provider, spec, sigAlgo));
        } catch (Exception e) {
            log.warn("Skipping {} in PQ benchmark: {}", label, e.toString());
        }
    }

    private PqRow bench(String label, String family, boolean qr, String kpgAlgo, String provider,
                        AlgorithmParameterSpec spec, String sigAlgo) throws GeneralSecurityException {
        SecureRandom rnd = new SecureRandom();

        // keygen (averaged)
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(kpgAlgo, provider);
        kpg.initialize(spec, rnd);
        long t0 = System.nanoTime();
        KeyPair kp = null;
        for (int i = 0; i < KEYGEN_ITERS; i++) kp = kpg.generateKeyPair();
        double keygenMs = msPer(t0, KEYGEN_ITERS);

        Signature signer = Signature.getInstance(sigAlgo, provider);

        // warm up once
        signer.initSign(kp.getPrivate());
        signer.update(MESSAGE);
        byte[] sig = signer.sign();

        // sign (averaged)
        t0 = System.nanoTime();
        for (int i = 0; i < SIGN_ITERS; i++) {
            signer.initSign(kp.getPrivate());
            signer.update(MESSAGE);
            sig = signer.sign();
        }
        double signMs = msPer(t0, SIGN_ITERS);

        // verify (averaged)
        boolean verified;
        t0 = System.nanoTime();
        boolean ok = true;
        for (int i = 0; i < SIGN_ITERS; i++) {
            signer.initVerify(kp.getPublic());
            signer.update(MESSAGE);
            ok = signer.verify(sig);
        }
        double verifyMs = msPer(t0, SIGN_ITERS);
        verified = ok;

        return new PqRow(label, family, qr,
                kp.getPublic().getEncoded().length, kp.getPrivate().getEncoded().length, sig.length,
                round(keygenMs), round(signMs), round(verifyMs), verified);
    }

    private static double msPer(long startNanos, int iters) {
        return (System.nanoTime() - startNanos) / 1_000_000.0 / iters;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}

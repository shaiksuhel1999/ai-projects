package com.tekmachain;

import com.tekmachain.pq.PostQuantumService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Phase 9: the post-quantum benchmark actually produces verifying signatures. */
class PostQuantumTest {

    @Test
    void benchmarksClassicalAndPostQuantumSignatures() {
        PostQuantumService svc = new PostQuantumService();
        PostQuantumService.PqReport report = svc.benchmark();

        Map<String, PostQuantumService.PqRow> byName = report.rows().stream()
                .collect(Collectors.toMap(PostQuantumService.PqRow::algorithm, r -> r));

        assertTrue(byName.containsKey("ECDSA (secp256k1)"), "ECDSA must be benchmarked");
        assertTrue(byName.containsKey("ML-DSA / Dilithium-2"), "Dilithium must be available");

        // Every scheme that ran must have produced a signature that verifies.
        report.rows().forEach(r -> assertTrue(r.verified(), r.algorithm() + " signature must verify"));

        // The whole point: post-quantum signatures are much larger than ECDSA's.
        int ecdsaSig = byName.get("ECDSA (secp256k1)").signatureBytes();
        int dilithiumSig = byName.get("ML-DSA / Dilithium-2").signatureBytes();
        assertTrue(dilithiumSig > ecdsaSig * 10,
                "Dilithium signature (" + dilithiumSig + "B) should dwarf ECDSA's (" + ecdsaSig + "B)");

        // ECDSA is flagged as quantum-vulnerable; Dilithium as quantum-resistant.
        assertFalse(byName.get("ECDSA (secp256k1)").quantumResistant());
        assertTrue(byName.get("ML-DSA / Dilithium-2").quantumResistant());
    }
}

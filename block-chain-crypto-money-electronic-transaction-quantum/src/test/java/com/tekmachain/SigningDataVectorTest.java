package com.tekmachain;

import com.tekmachain.crypto.CryptoUtil;
import com.tekmachain.crypto.SigningData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-language consensus vector for the canonical transaction serialization.
 *
 * <p>The expected bytes and id below were produced by the in-browser signer's real
 * {@code buildSigningBytes} in {@code app.js} (run through Node, slicing the actual
 * shipped functions). This test asserts the Java {@link SigningData} emits the exact
 * same bytes and the same SHA-256 id — so a transaction signed in the browser hashes
 * and verifies identically on the node. If either side's byte layout drifts, this
 * fails. Note that "12.5" and "37.50000000" both round-trip to exact int64 satoshis
 * (1250000000 and 3750000000), which is the whole point of the binary amount field.</p>
 */
class SigningDataVectorTest {

    private static final long TIMESTAMP = 1700000000000L;

    private static final String EXPECTED_BYTES_HEX =
            "010000018bcfe5680000000002000000403937316536653438353030643538333864376637356562313165613733353634613463303166323763646531623533303262306436393239653438646432333500000000000000403030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303030303100000005000000020000002554454b4e4d58427a554b79784c69454e315646776a3551323763537a655878336d5679576f000000004a817c800000002554454b7139586b327257703473566e376d5974334c624838634636644a31615a306555674e00000000df847580";

    private static final String EXPECTED_TXID =
            "7b82e3b6242f17cd54516c3370d8c99e70d5db17ed578525173dbe8fb681e5c1";

    @Test
    void javaEmitsTheSameCanonicalBytesAndIdAsTheBrowser() {
        List<SigningData.InRef> inputs = List.of(
                new SigningData.InRef("971e6e48500d5838d7f75eb11ea73564a4c01f27cde1b5302b0d6929e48dd235", 0),
                new SigningData.InRef("0000000000000000000000000000000000000000000000000000000000000001", 5));
        List<SigningData.OutRef> outputs = List.of(
                new SigningData.OutRef("TEKNMXBzUKyxLiEN1VFwj5Q27cSzeXx3mVyWo", new BigDecimal("12.5")),
                new SigningData.OutRef("TEKq9Xk2rWp4sVn7mYt3LbH8cF6dJ1aZ0eUgN", new BigDecimal("37.50000000")));

        byte[] bytes = SigningData.build(false, TIMESTAMP, inputs, outputs);
        assertEquals(EXPECTED_BYTES_HEX, CryptoUtil.toHex(bytes),
                "Java canonical bytes must match the browser byte-for-byte");
        assertEquals(EXPECTED_TXID, SigningData.txId(false, TIMESTAMP, inputs, outputs),
                "same bytes -> same transaction id on every node");
    }

    @Test
    void amountsCanonicalizeToExactSatoshis() {
        assertEquals(1_250_000_000L, SigningData.toSatoshis(new BigDecimal("12.5")));
        assertEquals(3_750_000_000L, SigningData.toSatoshis(new BigDecimal("37.50000000")));
        assertEquals(SigningData.toSatoshis(new BigDecimal("39.9")),
                SigningData.toSatoshis(new BigDecimal("39.90000000")), "trailing zeros don't matter");
    }
}

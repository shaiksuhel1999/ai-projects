package com.tekmachain.crypto;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The one canonical way TekmaChain turns a transaction into the bytes that get
 * signed and hashed. Both the local entity model and the peer-to-peer chain
 * validator use this, so a transaction produced on one node hashes and verifies
 * identically on every other node — regardless of how JSON reshaped its numbers.
 *
 * <p>The format is a strict, length-prefixed binary layout (all integers
 * big-endian), NOT a human-readable string. This mirrors real Bitcoin: amounts
 * are exact 64-bit satoshi integers (never decimal text, so "39.9" vs
 * "39.90000000" can't diverge), and every variable-length field carries its byte
 * length up front (so a value can never inject a delimiter and reshape the
 * transaction). The in-browser signer in {@code app.js} builds the exact same
 * bytes, which is proven byte-for-byte by {@code SigningDataVectorTest}.</p>
 *
 * <pre>
 *   u8   kind            (0 = coinbase, 1 = tx)
 *   i64  timestamp
 *   u32  inputCount
 *     per input:  u32 prevTxIdLen, prevTxId bytes (UTF-8), u32 outIndex
 *   u32  outputCount
 *     per output: u32 addressLen, address bytes (UTF-8), i64 amount-in-satoshis
 * </pre>
 */
public final class SigningData {

    private static final BigDecimal SATS_PER_COIN = BigDecimal.valueOf(100_000_000L);

    /** One input's referenced outpoint as it appears in the signing data. */
    public record InRef(String prevTxId, int outIndex) {
    }

    /** One output as it appears in the signing data. */
    public record OutRef(String address, BigDecimal amount) {
    }

    private SigningData() {
    }

    /** Amounts are always compared at 8 decimals so "39.9" and "39.90000000" are the same. */
    public static BigDecimal normalize(BigDecimal v) {
        return v.setScale(8, RoundingMode.HALF_UP);
    }

    /** Exact whole-satoshi value of an amount (8-dp fixed point), like Bitcoin's int64 amounts. */
    public static long toSatoshis(BigDecimal v) {
        return normalize(v).multiply(SATS_PER_COIN).longValueExact();
    }

    /** The canonical bytes that a transaction's id and signatures commit to. */
    public static byte[] build(boolean coinbase, long timestamp,
                               List<InRef> inputs, List<OutRef> outputs) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        u8(out, coinbase ? 0 : 1);
        i64(out, timestamp);
        u32(out, inputs.size());
        for (InRef in : inputs) {
            lenPrefixed(out, in.prevTxId());
            u32(out, in.outIndex());
        }
        u32(out, outputs.size());
        for (OutRef o : outputs) {
            lenPrefixed(out, o.address());
            i64(out, toSatoshis(o.amount()));
        }
        return out.toByteArray();
    }

    /** The transaction id is the SHA-256 of the canonical signing bytes. */
    public static String txId(boolean coinbase, long timestamp,
                              List<InRef> inputs, List<OutRef> outputs) {
        return CryptoUtil.sha256Hex(build(coinbase, timestamp, inputs, outputs));
    }

    // ---- big-endian primitive writers ----

    private static void u8(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
    }

    private static void u32(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xff);
        out.write((v >>> 16) & 0xff);
        out.write((v >>> 8) & 0xff);
        out.write(v & 0xff);
    }

    private static void i64(ByteArrayOutputStream out, long v) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) ((v >>> shift) & 0xff));
        }
    }

    private static void lenPrefixed(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        u32(out, b.length);
        out.writeBytes(b);
    }
}

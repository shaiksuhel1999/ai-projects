package com.tekmachain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The Proof-of-Work difficulty rule — the heartbeat that keeps issuance honest.
 *
 * <p>Difficulty is expressed as the number of <b>leading zero bits</b> a block hash
 * must have (finer than counting hex digits). Every {@code retarget-interval} blocks
 * the network compares how long that window actually took against the target block
 * time and nudges difficulty up or down — clamped to at most a 4× swing per retarget,
 * exactly like Bitcoin — so faster hashpower makes blocks harder and stalls make them
 * easier. Because every node recomputes and enforces this, no miner can pick an easy
 * difficulty and race ahead.</p>
 */
@Component
public class DifficultyPolicy {

    /** Starting difficulty (leading zero bits). 16 ≈ 65k hashes/block on a laptop. */
    @Value("${tekmachain.difficulty:16}")
    private int initialDifficulty;

    @Value("${tekmachain.pow.retarget-interval:16}")
    private int retargetInterval;

    @Value("${tekmachain.pow.target-block-time-ms:30000}")
    private long targetBlockTimeMs;

    public int initialDifficulty() {
        return initialDifficulty;
    }

    public int retargetInterval() {
        return retargetInterval;
    }

    public boolean isRetargetHeight(long height) {
        return height > 0 && height % retargetInterval == 0;
    }

    /**
     * Difficulty for the block at {@code height}: unchanged from the previous block,
     * except at a retarget boundary where it adjusts from how long the last window took.
     * {@code windowStartTs} is the timestamp of the block {@code retargetInterval} back.
     */
    public int difficultyFor(long height, int prevDifficulty, long prevTs, long windowStartTs) {
        if (height == 0) {
            return initialDifficulty;
        }
        if (!isRetargetHeight(height)) {
            return prevDifficulty;
        }
        long actual = Math.max(1, prevTs - windowStartTs);
        long expected = (long) retargetInterval * targetBlockTimeMs;
        double ratio = (double) expected / actual;              // >1 means blocks came too fast
        ratio = Math.max(0.25, Math.min(4.0, ratio));           // clamp the swing to 4x
        int delta = (int) Math.round(Math.log(ratio) / Math.log(2)); // work scales ~2^bits
        return Math.max(1, prevDifficulty + delta);
    }

    /** A hash satisfies difficulty when it has at least that many leading zero bits. */
    public boolean meets(String hashHex, int difficulty) {
        return leadingZeroBits(hashHex) >= difficulty;
    }

    public static int leadingZeroBits(String hashHex) {
        int bits = 0;
        for (int i = 0; i < hashHex.length(); i++) {
            int nibble = Character.digit(hashHex.charAt(i), 16);
            if (nibble < 0) break;
            if (nibble == 0) {
                bits += 4;
            } else {
                bits += Integer.numberOfLeadingZeros(nibble) - 28; // clz within the low 4 bits
                break;
            }
        }
        return bits;
    }
}

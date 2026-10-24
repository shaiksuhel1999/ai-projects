package com.tekmachain;

import com.tekmachain.service.DifficultyPolicy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** The Proof-of-Work difficulty rule: leading-zero-bit counting + clamped retargeting. */
class DifficultyPolicyTest {

    private static void set(Object o, String f, Object v) throws Exception {
        Field fl = o.getClass().getDeclaredField(f);
        fl.setAccessible(true);
        fl.set(o, v);
    }

    private DifficultyPolicy policy(int initial, int interval, long blockTimeMs) throws Exception {
        DifficultyPolicy p = new DifficultyPolicy();
        set(p, "initialDifficulty", initial);
        set(p, "retargetInterval", interval);
        set(p, "targetBlockTimeMs", blockTimeMs);
        return p;
    }

    @Test
    void countsLeadingZeroBits() {
        assertEquals(16, DifficultyPolicy.leadingZeroBits("0000ffffffffffff"));
        assertEquals(0, DifficultyPolicy.leadingZeroBits("ffffffff"));
        assertEquals(4, DifficultyPolicy.leadingZeroBits("0fffffff"));  // one zero nibble
        assertEquals(7, DifficultyPolicy.leadingZeroBits("0100"));      // 0000 0001 -> 7 leading zeros
        assertEquals(11, DifficultyPolicy.leadingZeroBits("00100000")); // 8 + 3
    }

    @Test
    void difficultyIsUnchangedOffRetargetAndClampsOnRetarget() throws Exception {
        DifficultyPolicy p = policy(16, 4, 1000);   // target window = 4 * 1000 = 4000ms
        assertEquals(16, p.difficultyFor(0, 0, 0, 0), "genesis uses the initial difficulty");
        assertEquals(16, p.difficultyFor(1, 16, 100, 100), "non-retarget height inherits difficulty");

        // Blocks came far too fast -> harder, clamped to +2 bits (max 4x work).
        assertEquals(18, p.difficultyFor(4, 16, 500, 0));     // actual 500ms << 4000ms
        // Blocks came far too slow -> easier, clamped to -2 bits.
        assertEquals(14, p.difficultyFor(4, 16, 40000, 0));   // actual 40000ms >> 4000ms
        // On target -> unchanged.
        assertEquals(16, p.difficultyFor(4, 16, 4000, 0));
        // Never drops below 1.
        assertEquals(1, p.difficultyFor(4, 1, 40000, 0));
    }

    @Test
    void identifiesRetargetHeights() throws Exception {
        DifficultyPolicy p = policy(16, 4, 1000);
        assertFalse(p.isRetargetHeight(0));
        assertFalse(p.isRetargetHeight(3));
        assertTrue(p.isRetargetHeight(4));
        assertTrue(p.isRetargetHeight(8));
    }
}

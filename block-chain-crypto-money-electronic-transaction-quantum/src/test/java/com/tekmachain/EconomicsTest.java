package com.tekmachain;

import com.tekmachain.service.Economics;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/** Bitcoin-style monetary policy: halving schedule + hard supply cap. */
class EconomicsTest {

    private static void set(Object o, String f, Object v) throws Exception {
        Field fl = o.getClass().getDeclaredField(f);
        fl.setAccessible(true);
        fl.set(o, v);
    }

    private Economics economics(long interval, String reward, String cap) throws Exception {
        Economics e = new Economics();
        set(e, "initialReward", new BigDecimal(reward));
        set(e, "halvingInterval", interval);
        set(e, "maxSupply", new BigDecimal(cap));
        return e;
    }

    private static void eq(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void rewardHalvesEveryInterval() throws Exception {
        Economics e = economics(10, "50", "1000000");
        eq("50", e.rewardForHeight(0));
        eq("50", e.rewardForHeight(9));
        eq("25", e.rewardForHeight(10));
        eq("12.5", e.rewardForHeight(20));
        eq("6.25", e.rewardForHeight(35));
    }

    @Test
    void supplyCapClampsTheReward() throws Exception {
        Economics e = economics(10, "50", "1000");
        eq("50", e.effectiveReward(1, new BigDecimal("500")));   // plenty of room
        eq("10", e.effectiveReward(1, new BigDecimal("990")));   // only 10 left -> clamp
        eq("0", e.effectiveReward(1, new BigDecimal("1000")));   // cap reached -> nothing
        eq("0", e.effectiveReward(1, new BigDecimal("1200")));   // over cap -> nothing
    }

    @Test
    void countsBlocksUntilNextHalving() throws Exception {
        Economics e = economics(10, "50", "1000");
        assertEquals(3, e.blocksUntilHalving(7));
        assertEquals(10, e.blocksUntilHalving(10));
    }
}

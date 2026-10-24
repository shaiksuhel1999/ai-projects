package com.tekmachain;

import com.tekmachain.p2p.PeerRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** Horizontal-scaling behaviour of the peer registry: cap, gossip sampling, eviction. */
class PeerRegistryTest {

    private static void set(Object o, String field, Object value) throws Exception {
        Field f = o.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(o, value);
    }

    private PeerRegistry registry() throws Exception {
        PeerRegistry r = new PeerRegistry();
        set(r, "selfUrl", "http://localhost:8080");
        set(r, "maxPeers", 3);
        return r;
    }

    @Test
    void capsPeersAndIgnoresSelf() throws Exception {
        PeerRegistry r = registry();
        assertTrue(r.add("http://localhost:8081"));
        assertTrue(r.add("http://localhost:8082"));
        assertTrue(r.add("http://localhost:8083"));
        assertFalse(r.add("http://localhost:8084"), "cap of 3 reached");
        assertFalse(r.add("http://localhost:8080"), "never add ourselves");
        assertEquals(3, r.list().size());
    }

    @Test
    void gossipSampleIsBoundedSubset() throws Exception {
        PeerRegistry r = registry();
        r.add("http://localhost:8081");
        r.add("http://localhost:8082");
        r.add("http://localhost:8083");
        assertEquals(2, r.sample(2).size());
        assertTrue(r.list().containsAll(r.sample(2)));
        assertEquals(3, r.sample(10).size(), "sample never exceeds known peers");
    }

    @Test
    void evictsAfterRepeatedFailuresAndResetsOnSeen() throws Exception {
        PeerRegistry r = registry();
        r.add("http://localhost:8081");
        r.markFailed("http://localhost:8081");
        r.markFailed("http://localhost:8081");
        assertTrue(r.list().contains("http://localhost:8081"), "2 failures: still tracked");
        r.markFailed("http://localhost:8081");
        assertFalse(r.list().contains("http://localhost:8081"), "3 failures: evicted");

        r.add("http://localhost:8081");
        r.markFailed("http://localhost:8081");
        r.markSeen("http://localhost:8081");     // reset the counter
        r.markFailed("http://localhost:8081");
        r.markFailed("http://localhost:8081");
        assertTrue(r.list().contains("http://localhost:8081"), "seen reset the failure count");
    }
}

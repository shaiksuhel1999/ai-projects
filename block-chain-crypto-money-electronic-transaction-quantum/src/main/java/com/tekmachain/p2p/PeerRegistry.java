package com.tekmachain.p2p;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The set of peer nodes this node knows about, plus this node's own identity.
 * Peer URLs are base origins like {@code http://localhost:8002}.
 *
 * <p>To scale horizontally it caps how many peers it tracks, evicts peers that
 * stop responding, and hands out random subsets for gossip so broadcast cost stays
 * bounded as the mesh grows.</p>
 */
@Component
public class PeerRegistry {

    private static final int MAX_FAILURES = 3;

    private final Set<String> peers = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failures = new ConcurrentHashMap<>();
    private final String nodeId = UUID.randomUUID().toString().substring(0, 8);

    @Value("${tekmachain.node.self-url:}")
    private String selfUrl;

    @Value("${server.port:8080}")
    private int port;

    @Value("${tekmachain.node.peers:}")
    private String seedPeers;

    @Value("${tekmachain.node.max-peers:16}")
    private int maxPeers;

    @PostConstruct
    void init() {
        if (selfUrl == null || selfUrl.isBlank()) {
            selfUrl = "http://localhost:" + port;
        }
        selfUrl = normalize(selfUrl);
        if (seedPeers != null && !seedPeers.isBlank()) {
            for (String p : seedPeers.split(",")) {
                add(p.trim());
            }
        }
    }

    public String nodeId() {
        return nodeId;
    }

    public String selfUrl() {
        return selfUrl;
    }

    /** Adds a peer, ignoring blanks, our own address, and respecting the peer cap. */
    public boolean add(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String n = normalize(url);
        if (n.equals(selfUrl) || peers.contains(n)) {
            return false;
        }
        if (peers.size() >= maxPeers) {
            return false;   // mesh is full for this node; gossip will still reach others
        }
        failures.remove(n);
        return peers.add(n);
    }

    public void remove(String url) {
        String n = normalize(url);
        peers.remove(n);
        failures.remove(n);
    }

    public List<String> list() {
        return peers.stream().sorted().toList();
    }

    /** A random subset of peers to gossip to, so broadcast cost stays bounded. */
    public List<String> sample(int fanout) {
        List<String> all = new ArrayList<>(peers);
        if (all.size() <= fanout) {
            return all;
        }
        Collections.shuffle(all);
        return all.subList(0, fanout);
    }

    /** Mark a peer healthy (resets its failure count). */
    public void markSeen(String url) {
        failures.remove(normalize(url));
    }

    /** Mark a peer unresponsive; evict it after too many consecutive failures. */
    public void markFailed(String url) {
        String n = normalize(url);
        int f = failures.merge(n, 1, Integer::sum);
        if (f >= MAX_FAILURES) {
            remove(n);
        }
    }

    private static String normalize(String url) {
        String u = url.trim();
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "http://" + u;
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }
}

package com.tekmachain.p2p;

import com.tekmachain.domain.Transaction;
import com.tekmachain.service.BlockchainService;
import com.tekmachain.service.ChainService;
import com.tekmachain.service.MempoolService;
import com.tekmachain.service.TransactionService;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The peer-to-peer brain: it gossips transactions and blocks to peers, discovers
 * new peers, and periodically reconciles with the network by adopting the longest
 * valid chain. All peer I/O is best-effort — offline peers are simply skipped.
 */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private final PeerRegistry peers;
    private final NodeClient client;
    private final ChainService chainService;
    private final BlockchainService blockchain;
    private final MempoolService mempool;
    private final TransactionService txService;

    @org.springframework.beans.factory.annotation.Value("${tekmachain.node.gossip-fanout:6}")
    private int gossipFanout;

    private final ExecutorService syncExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tekmachain-sync");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean syncing = new AtomicBoolean(false);

    public NetworkService(PeerRegistry peers, NodeClient client, ChainService chainService,
                          BlockchainService blockchain, MempoolService mempool, TransactionService txService) {
        this.peers = peers;
        this.client = client;
        this.chainService = chainService;
        this.blockchain = blockchain;
        this.mempool = mempool;
        this.txService = txService;
    }

    // ---------------------------------------------------------------------
    // Outbound gossip (called by the local API controllers)
    // ---------------------------------------------------------------------

    public void broadcastTransaction(Dtos.TransactionDto tx) {
        for (String peer : peers.sample(gossipFanout)) {
            client.sendTransaction(peer, tx);
        }
    }

    public void broadcastBlock(Dtos.BlockDto block) {
        for (String peer : peers.sample(gossipFanout)) {
            client.sendBlock(peer, block);
        }
    }

    // ---------------------------------------------------------------------
    // Inbound gossip (called by NodeController)
    // ---------------------------------------------------------------------

    /** A transaction arrived from a peer. Validate, pool it, and forward it on — once. */
    public String receiveTransaction(Dtos.TransactionDto dto) {
        if (mempool.contains(dto.txId())) {
            return "known"; // already have it -> stop the flood here
        }
        Transaction tx = Mapper.txEntity(dto);
        try {
            txService.validate(tx);
            mempool.add(tx);
        } catch (RuntimeException e) {
            return "rejected: " + e.getMessage();
        }
        broadcastTransaction(dto); // forward to our peers
        return "accepted";
    }

    /** A block arrived from a peer. */
    public String receiveBlock(Dtos.BlockDto dto) {
        ChainService.AppendOutcome outcome = chainService.tryAppendExternalBlock(dto);
        switch (outcome) {
            case ACCEPTED -> {
                broadcastBlock(dto); // relay it onward
                return "accepted";
            }
            case INVALID -> {
                return "invalid";
            }
            default -> {
                triggerSync(); // we may be behind or on a shorter fork -> reconcile
                return "syncing";
            }
        }
    }

    // ---------------------------------------------------------------------
    // Discovery + longest-chain sync
    // ---------------------------------------------------------------------

    public void discoverPeers() {
        for (String peer : peers.list()) {
            var info = client.info(peer);               // liveness check
            if (info.isEmpty()) {
                peers.markFailed(peer);
                continue;
            }
            peers.markSeen(peer);
            client.register(peer, peers.selfUrl());     // tell them about us
            for (String discovered : client.peers(peer)) { // learn who they know
                peers.add(discovered);
            }
        }
    }

    /** Pull the longest valid chain the network is offering and adopt it if it beats ours. */
    public boolean syncWithPeers() {
        long localHeight = blockchain.height();
        String bestPeer = null;
        long bestHeight = localHeight;
        for (String peer : peers.list()) {
            var info = client.info(peer);
            if (info.isEmpty()) {
                peers.markFailed(peer);
                continue;
            }
            peers.markSeen(peer);
            if (info.get().height() > bestHeight) {
                bestHeight = info.get().height();
                bestPeer = peer;
            }
        }
        if (bestPeer == null) {
            return false;
        }
        List<Dtos.BlockDto> candidate = client.blocksFrom(bestPeer, 0);
        if (candidate.isEmpty()) {
            return false;
        }
        boolean switched = chainService.replaceChain(candidate);
        if (switched) {
            log.info("Adopted longer chain from {} (height {} -> {})", bestPeer, localHeight, bestHeight);
        }
        return switched;
    }

    public void triggerSync() {
        if (syncing.compareAndSet(false, true)) {
            syncExecutor.submit(() -> {
                try {
                    syncWithPeers();
                } catch (RuntimeException e) {
                    log.debug("sync failed: {}", e.getMessage());
                } finally {
                    syncing.set(false);
                }
            });
        }
    }

    /** Keep the node converged: re-discover peers and reconcile chains on a timer. */
    @Scheduled(initialDelayString = "${tekmachain.node.sync-initial-delay-ms:6000}",
            fixedDelayString = "${tekmachain.node.sync-interval-ms:12000}")
    public void periodic() {
        if (peers.list().isEmpty()) {
            return;
        }
        try {
            discoverPeers();
            syncWithPeers();
        } catch (RuntimeException e) {
            log.debug("periodic sync failed: {}", e.getMessage());
        }
    }
}

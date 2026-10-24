package com.tekmachain.web;

import com.tekmachain.p2p.NetworkService;
import com.tekmachain.p2p.NodeClient;
import com.tekmachain.p2p.PeerRegistry;
import com.tekmachain.service.BlockchainService;
import com.tekmachain.web.dto.Dtos;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Human/UI-facing view of the peer network: who we are, who we know (with their
 * live height), plus buttons to add a peer or force a sync.
 */
@RestController
@RequestMapping("/api")
public class NetworkController {

    private final PeerRegistry peers;
    private final NodeClient client;
    private final NetworkService network;
    private final BlockchainService blockchain;

    public NetworkController(PeerRegistry peers, NodeClient client,
                             NetworkService network, BlockchainService blockchain) {
        this.peers = peers;
        this.client = client;
        this.network = network;
        this.blockchain = blockchain;
    }

    @GetMapping("/peers")
    public Dtos.NetworkStatus status() {
        List<Dtos.PeerDto> list = peers.list().stream().map(url -> {
            var info = client.info(url);
            return info.map(i -> new Dtos.PeerDto(url, true, i.height(), i.tipHash()))
                    .orElseGet(() -> new Dtos.PeerDto(url, false, null, null));
        }).toList();
        return new Dtos.NetworkStatus(peers.nodeId(), peers.selfUrl(), list);
    }

    @PostMapping("/peers")
    public Dtos.NetworkStatus addPeer(@RequestBody Dtos.AddPeerRequest req) {
        peers.add(req.url());
        network.discoverPeers();
        network.triggerSync();
        return status();
    }

    @PostMapping("/sync")
    public Map<String, Object> sync() {
        boolean switched = network.syncWithPeers();
        return Map.of("switched", switched, "height", blockchain.height());
    }
}

package com.tekmachain.web;

import com.tekmachain.domain.Block;
import com.tekmachain.p2p.NetworkService;
import com.tekmachain.p2p.PeerRegistry;
import com.tekmachain.service.BlockchainService;
import com.tekmachain.service.MiningService;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Node-to-node endpoints. These are how TekmaChain peers gossip and synchronise;
 * a browser never needs them, but they are plain REST so you can inspect them too.
 */
@RestController
@RequestMapping("/api/node")
public class NodeController {

    private final NetworkService network;
    private final BlockchainService blockchain;
    private final MiningService mining;
    private final PeerRegistry peers;

    public NodeController(NetworkService network, BlockchainService blockchain,
                          MiningService mining, PeerRegistry peers) {
        this.network = network;
        this.blockchain = blockchain;
        this.mining = mining;
        this.peers = peers;
    }

    @GetMapping("/info")
    public Dtos.NodeInfo info() {
        Block tip = blockchain.requireLastBlock();
        long height = tip.getHeight();
        return new Dtos.NodeInfo(peers.nodeId(), height, tip.getHash(),
                (int) (height + 1), mining.getDifficulty());
    }

    @GetMapping("/blocks")
    public List<Dtos.BlockDto> blocks(@RequestParam(defaultValue = "0") long from) {
        return blockchain.chain().stream()
                .filter(b -> b.getHeight() >= from)
                .map(Mapper::block)
                .toList();
    }

    @PostMapping("/blocks")
    public Map<String, String> receiveBlock(@RequestBody Dtos.BlockDto block) {
        return Map.of("status", network.receiveBlock(block));
    }

    @PostMapping("/transactions")
    public Map<String, String> receiveTransaction(@RequestBody Dtos.TransactionDto tx) {
        return Map.of("status", network.receiveTransaction(tx));
    }

    @GetMapping("/peers")
    public List<String> peers() {
        return peers.list();
    }

    @PostMapping("/peers")
    public List<String> addPeer(@RequestBody Dtos.AddPeerRequest req) {
        peers.add(req.url());
        return peers.list();
    }
}

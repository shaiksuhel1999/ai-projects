package com.tekmachain.web;

import com.tekmachain.domain.Block;
import com.tekmachain.domain.Utxo;
import com.tekmachain.p2p.NetworkService;
import com.tekmachain.repo.UtxoRepository;
import com.tekmachain.service.BlockchainService;
import com.tekmachain.service.MempoolService;
import com.tekmachain.service.MiningService;
import com.tekmachain.web.dto.Dtos;
import com.tekmachain.web.dto.Mapper;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ChainController {

    private final BlockchainService blockchain;
    private final MiningService mining;
    private final MempoolService mempool;
    private final UtxoRepository utxos;
    private final NetworkService network;
    private final com.tekmachain.service.Economics economics;

    public ChainController(BlockchainService blockchain, MiningService mining,
                           MempoolService mempool, UtxoRepository utxos, NetworkService network,
                           com.tekmachain.service.Economics economics) {
        this.blockchain = blockchain;
        this.mining = mining;
        this.mempool = mempool;
        this.utxos = utxos;
        this.network = network;
        this.economics = economics;
    }

    @PostMapping("/mine")
    public Dtos.BlockDto mine(@RequestBody Dtos.MineRequest req) {
        Block block = mining.mine(req.minerAddress());
        Dtos.BlockDto dto = Mapper.block(block);
        network.broadcastBlock(dto);
        return dto;
    }

    @GetMapping("/blocks")
    public List<Dtos.BlockDto> blocks() {
        return blockchain.chain().stream().map(Mapper::block).toList();
    }

    @GetMapping("/blocks/{height}")
    public Dtos.BlockDto block(@PathVariable long height) {
        return Mapper.block(blockchain.requireBlock(height));
    }

    @GetMapping("/chain/validate")
    public BlockchainService.ValidationReport validate() {
        return blockchain.validateChain();
    }

    @GetMapping("/chain/info")
    public Dtos.ChainInfoDto info() {
        BigDecimal supply = utxos.findAll().stream()
                .map(Utxo::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long height = blockchain.height();
        return new Dtos.ChainInfoDto(
                height,
                mining.getDifficulty(),
                mining.getReward(),
                mempool.size(),
                supply,
                economics.maxSupply(),
                economics.halvingInterval(),
                economics.blocksUntilHalving(height),
                BlockchainService.GENESIS_MESSAGE);
    }
}

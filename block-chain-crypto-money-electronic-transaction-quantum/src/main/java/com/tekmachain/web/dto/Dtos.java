package com.tekmachain.web.dto;

import java.math.BigDecimal;
import java.util.List;

/** Request/response shapes for the REST API, grouped in one place. */
public final class Dtos {

    private Dtos() {
    }

    // ---- requests ----
    public record CreateWalletRequest(String label) {
    }

    public record SendRequest(String from, String to, BigDecimal amount, BigDecimal fee) {
    }

    public record MineRequest(String minerAddress) {
    }

    // ---- responses ----
    public record WalletDto(String label, String address, String publicKey, BigDecimal balance,
                            BigDecimal immature, boolean hybrid) {
    }

    public record UtxoDto(String txId, int outIndex, BigDecimal amount) {
    }

    public record TxInputDto(String prevTxId, int outIndex, String publicKey, String signature,
                             String dilithiumPublicKey, String dilithiumSignature) {
    }

    public record TxOutputDto(int index, String address, BigDecimal amount) {
    }

    public record TransactionDto(String txId, boolean coinbase, long timestamp,
                                 List<TxInputDto> inputs, List<TxOutputDto> outputs) {
    }

    public record BlockDto(long height, long timestamp, String previousHash, String merkleRoot,
                           int difficulty, long nonce, String minerAddress, String hash,
                           List<TransactionDto> transactions) {
    }

    public record ChainInfoDto(long height, int difficulty, BigDecimal blockReward,
                               int mempoolSize, BigDecimal circulatingSupply,
                               BigDecimal maxSupply, long halvingInterval, long blocksUntilHalving,
                               String genesisMessage) {
    }

    // ---- peer-to-peer ----
    public record NodeInfo(String nodeId, long height, String tipHash, int length, int difficulty) {
    }

    public record AddPeerRequest(String url) {
    }

    public record PeerDto(String url, boolean online, Long height, String tipHash) {
    }

    public record NetworkStatus(String nodeId, String selfUrl, List<PeerDto> peers) {
    }
}

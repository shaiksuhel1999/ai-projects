package com.tekmachain.p2p;

import com.tekmachain.web.dto.Dtos;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Thin HTTP client for node-to-node calls. Every call is best-effort: if a peer is
 * down or slow it returns empty / false instead of throwing, so one bad peer never
 * stalls gossip or sync.
 */
@Component
public class NodeClient {

    private final RestClient http;

    public NodeClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public Optional<Dtos.NodeInfo> info(String peer) {
        try {
            return Optional.ofNullable(
                    http.get().uri(peer + "/api/node/info").retrieve().body(Dtos.NodeInfo.class));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public List<Dtos.BlockDto> blocksFrom(String peer, long from) {
        try {
            Dtos.BlockDto[] arr = http.get().uri(peer + "/api/node/blocks?from=" + from)
                    .retrieve().body(Dtos.BlockDto[].class);
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public void sendBlock(String peer, Dtos.BlockDto block) {
        try {
            http.post().uri(peer + "/api/node/blocks").body(block).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
        }
    }

    public void sendTransaction(String peer, Dtos.TransactionDto tx) {
        try {
            http.post().uri(peer + "/api/node/transactions").body(tx).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
        }
    }

    public List<String> peers(String peer) {
        try {
            String[] arr = http.get().uri(peer + "/api/node/peers").retrieve().body(String[].class);
            return arr == null ? List.of() : Arrays.asList(arr);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    public void register(String peer, String selfUrl) {
        try {
            http.post().uri(peer + "/api/node/peers")
                    .body(new Dtos.AddPeerRequest(selfUrl)).retrieve().toBodilessEntity();
        } catch (RuntimeException ignored) {
        }
    }
}

package com.tekmachain;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TekmaChain — a Bitcoin-inspired cryptocurrency network (proof of concept).
 *
 * Everything is embedded: an in-process H2 database, a self-contained web wallet UI,
 * real secp256k1 ECDSA signatures, a UTXO ledger and Proof-of-Work mining.
 * Run with:  mvn spring-boot:run   then open http://localhost:8080
 */
@SpringBootApplication
@EnableScheduling
public class TekmaChainApplication {
    public static void main(String[] args) {
        SpringApplication.run(TekmaChainApplication.class, args);
    }
}

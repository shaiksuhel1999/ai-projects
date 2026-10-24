package com.tekmachain.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The monetary policy of Tekma Coin, in one place so the miner AND the validators
 * agree on it: a halving block-reward schedule and a hard supply cap — exactly the
 * Bitcoin-style rules. Because every node enforces this, no miner can mint more
 * than the schedule allows.
 */
@Component
public class Economics {

    @Value("${tekmachain.block-reward:50}")
    private BigDecimal initialReward;

    /** Blocks between reward halvings (Bitcoin: 210,000). */
    @Value("${tekmachain.halving-interval:210000}")
    private long halvingInterval;

    /** Hard cap on total supply (Bitcoin: 21,000,000). */
    @Value("${tekmachain.max-supply:21000000}")
    private BigDecimal maxSupply;

    /** Base reward at a height, halved every interval. */
    public BigDecimal rewardForHeight(long height) {
        long halvings = height / halvingInterval;
        if (halvings >= 63) {
            return BigDecimal.ZERO;
        }
        return initialReward.divide(BigDecimal.valueOf(1L << halvings), 8, RoundingMode.FLOOR);
    }

    /** Reward actually mintable at a height, capped so total supply never exceeds the max. */
    public BigDecimal effectiveReward(long height, BigDecimal mintedSoFar) {
        BigDecimal remaining = maxSupply.subtract(mintedSoFar);
        if (remaining.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return rewardForHeight(height).min(remaining);
    }

    public long blocksUntilHalving(long currentHeight) {
        long next = ((currentHeight / halvingInterval) + 1) * halvingInterval;
        return next - currentHeight;
    }

    public BigDecimal initialReward() { return initialReward; }
    public long halvingInterval() { return halvingInterval; }
    public BigDecimal maxSupply() { return maxSupply; }
}

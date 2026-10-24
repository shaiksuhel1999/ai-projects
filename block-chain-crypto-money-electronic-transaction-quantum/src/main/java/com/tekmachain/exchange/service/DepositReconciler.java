package com.tekmachain.exchange.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives {@link ExchangeService#reconcileDeposits()} on a timer. Kept separate so the
 * scheduled trigger can swallow transient failures (a DB blip, or a context shutting
 * down between tests) instead of letting them surface as a scheduled-task error every
 * few seconds — while the actual crediting stays transactional in {@code ExchangeService}.
 */
@Component
public class DepositReconciler {

    private static final Logger log = LoggerFactory.getLogger(DepositReconciler.class);

    private final ExchangeService exchange;

    public DepositReconciler(ExchangeService exchange) {
        this.exchange = exchange;
    }

    @Scheduled(fixedDelay = 4000)
    public void run() {
        try {
            exchange.reconcileDeposits();
        } catch (RuntimeException e) {
            log.debug("deposit reconcile skipped this cycle: {}", e.toString());
        }
    }
}

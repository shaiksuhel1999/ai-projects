package com.tekmachain.auth;

import com.tekmachain.exchange.service.ExchangeService;
import com.tekmachain.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startup migration for databases that predate accounts: wallets created before
 * the login feature have no owner and so belong to nobody. If
 * {@code tekmachain.claim-orphan-wallets-for} names an existing account, every
 * ownerless wallet (except the system exchange hot wallet) is assigned to it on
 * boot. Blank by default — a no-op for fresh databases.
 */
@Component
public class OrphanWalletClaimer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OrphanWalletClaimer.class);

    private final WalletService wallets;
    private final UserRepository users;
    private final ExchangeService exchange;

    @Value("${tekmachain.claim-orphan-wallets-for:}")
    private String claimFor;

    public OrphanWalletClaimer(WalletService wallets, UserRepository users, ExchangeService exchange) {
        this.wallets = wallets;
        this.users = users;
        this.exchange = exchange;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (claimFor == null || claimFor.isBlank()) {
            return;
        }
        String owner = claimFor.trim();
        if (!users.existsByUsername(owner)) {
            log.warn("tekmachain.claim-orphan-wallets-for='{}' but no such account exists — skipping wallet claim", owner);
            return;
        }
        int claimed = wallets.claimUnownedWallets(owner, exchange.hotWalletAddress());
        if (claimed > 0) {
            log.info("Assigned {} previously unowned wallet(s) to account '{}'", claimed, owner);
        }
    }
}

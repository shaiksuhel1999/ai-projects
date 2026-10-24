package com.tekmachain;

import com.tekmachain.domain.Wallet;
import com.tekmachain.exchange.service.ExchangeService;
import com.tekmachain.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pre-accounts migration claims ownerless wallets for a chosen account, but
 * must leave the system exchange hot wallet unowned.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class OrphanWalletClaimTest {

    @Autowired WalletService wallets;
    @Autowired ExchangeService exchange;

    @Test
    void claimsUnownedUserWalletsButNotTheHotWallet() {
        String hot = exchange.hotWalletAddress();
        assertNotNull(hot, "exchange hot wallet is bootstrapped at startup");

        Wallet a = wallets.create("Alice-legacy");   // created without an owner (owner == null)
        Wallet b = wallets.create("Bob-legacy");
        assertNull(a.getOwner());

        int claimed = wallets.claimUnownedWallets("alice", hot);

        assertEquals(2, claimed, "both legacy user wallets are claimed");
        assertEquals("alice", wallets.require(a.getAddress()).getOwner());
        assertEquals("alice", wallets.require(b.getAddress()).getOwner());
        assertNull(wallets.require(hot).getOwner(), "the system hot wallet stays unowned");

        // idempotent: nothing left to claim on a second run
        assertEquals(0, wallets.claimUnownedWallets("alice", hot));
    }
}

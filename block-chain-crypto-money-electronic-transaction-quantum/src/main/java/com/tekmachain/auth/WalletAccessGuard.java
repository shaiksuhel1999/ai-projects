package com.tekmachain.auth;

import com.tekmachain.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authorization helper shared by the write endpoints: it resolves the signed-in
 * user and refuses any attempt to operate on a wallet that isn't theirs. Without
 * this, an authenticated user could name someone else's custodial address and move
 * coins the server signs for — a horizontal privilege-escalation (IDOR).
 */
@Component
public class WalletAccessGuard {

    private final WalletService wallets;

    public WalletAccessGuard(WalletService wallets) {
        this.wallets = wallets;
    }

    /** The signed-in username, or null if the request is anonymous. */
    public String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) ? a.getName() : null;
    }

    /** Throws 403 unless the current user may act as {@code address}. */
    public void assertCanActAs(String address) {
        if (!wallets.canActAs(address, currentUser())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This wallet isn't linked to your account");
        }
    }
}

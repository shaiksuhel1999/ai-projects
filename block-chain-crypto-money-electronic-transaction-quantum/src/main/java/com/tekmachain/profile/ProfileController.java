package com.tekmachain.profile;

import com.tekmachain.auth.User;
import com.tekmachain.auth.UserRepository;
import com.tekmachain.domain.Wallet;
import com.tekmachain.exchange.domain.Balance;
import com.tekmachain.exchange.service.BalanceService;
import com.tekmachain.service.WalletService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

/**
 * The signed-in user's profile: account details, a portfolio of what they hold
 * (TEK on-chain across their wallets, plus assets held on the exchange), and their
 * linked bank / demat accounts.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository users;
    private final WalletService wallets;
    private final BalanceService balances;
    private final PaymentAccountService accounts;

    public ProfileController(UserRepository users, WalletService wallets,
                             BalanceService balances, PaymentAccountService accounts) {
        this.users = users;
        this.wallets = wallets;
        this.balances = balances;
        this.accounts = accounts;
    }

    // ---- response shapes ----
    public record AccountInfo(String username, String displayName, long createdAt) {
    }

    public record WalletHolding(String label, String address, boolean hybrid,
                                BigDecimal tek, BigDecimal spendable, BigDecimal immature) {
    }

    public record AssetHolding(String asset, BigDecimal available, BigDecimal locked, BigDecimal total) {
    }

    public record Totals(BigDecimal onChainTek, BigDecimal spendableTek, BigDecimal immatureTek, BigDecimal exchangeTek) {
    }

    public record ProfileView(AccountInfo account, List<WalletHolding> wallets,
                              List<AssetHolding> exchange, Totals totals,
                              List<PaymentAccountService.View> linkedAccounts) {
    }

    public record LinkAccountRequest(String kind, String holderName, String institution, String number, String code) {
    }

    @GetMapping
    public ProfileView profile() {
        String username = requireUser();
        User user = users.findByUsername(username).orElseThrow(() -> new AccessDeniedException("No account"));

        List<Wallet> owned = wallets.ownedBy(username);
        List<WalletHolding> holdings = new ArrayList<>();
        BigDecimal onChain = BigDecimal.ZERO, spendable = BigDecimal.ZERO, immature = BigDecimal.ZERO;

        // aggregate exchange balances across all of the user's addresses, by asset
        Map<String, BigDecimal[]> exByAsset = new LinkedHashMap<>();  // asset -> [available, locked]
        for (Wallet w : owned) {
            BigDecimal tek = wallets.balanceOf(w.getAddress());
            BigDecimal sp = wallets.spendableBalanceOf(w.getAddress());
            BigDecimal im = wallets.immatureBalanceOf(w.getAddress());
            holdings.add(new WalletHolding(w.getLabel(), w.getAddress(), w.isHybrid(), tek, sp, im));
            onChain = onChain.add(tek);
            spendable = spendable.add(sp);
            immature = immature.add(im);
            for (Balance b : balances.forAddress(w.getAddress())) {
                BigDecimal[] acc = exByAsset.computeIfAbsent(b.getAsset(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                acc[0] = acc[0].add(b.getAvailable());
                acc[1] = acc[1].add(b.getLocked());
            }
        }

        List<AssetHolding> exchange = new ArrayList<>();
        BigDecimal exchangeTek = BigDecimal.ZERO;
        for (var e : exByAsset.entrySet()) {
            BigDecimal available = e.getValue()[0], locked = e.getValue()[1], total = available.add(locked);
            if (total.signum() == 0) {
                continue;
            }
            exchange.add(new AssetHolding(e.getKey(), available, locked, total));
            if ("TEK".equals(e.getKey())) {
                exchangeTek = total;
            }
        }
        exchange.sort(Comparator.comparing((AssetHolding a) -> !"TEK".equals(a.asset())).thenComparing(AssetHolding::asset));

        return new ProfileView(
                new AccountInfo(user.getUsername(), user.getDisplayName(), user.getCreatedAt()),
                holdings, exchange,
                new Totals(onChain, spendable, immature, exchangeTek),
                accounts.list(username));
    }

    @GetMapping("/accounts")
    public List<PaymentAccountService.View> listAccounts() {
        return accounts.list(requireUser());
    }

    @PostMapping("/accounts")
    public PaymentAccountService.View linkAccount(@RequestBody LinkAccountRequest req) {
        return accounts.link(requireUser(), req.kind(), req.holderName(), req.institution(), req.number(), req.code());
    }

    @DeleteMapping("/accounts/{id}")
    public Map<String, Object> removeAccount(@PathVariable Long id) {
        accounts.remove(requireUser(), id);
        return Map.of("removed", true);
    }

    private String requireUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException("Sign in to view your profile");
        }
        return a.getName();
    }
}

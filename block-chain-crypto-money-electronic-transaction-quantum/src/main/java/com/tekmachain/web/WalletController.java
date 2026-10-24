package com.tekmachain.web;

import com.tekmachain.domain.Wallet;
import com.tekmachain.service.WalletService;
import com.tekmachain.web.dto.Dtos;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    /** The signed-in account, or null if the request is anonymous. */
    private String currentUser() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) ? a.getName() : null;
    }

    @GetMapping
    public List<Dtos.WalletDto> list() {
        return wallets.ownedBy(currentUser()).stream().map(this::toDto).toList();
    }

    @PostMapping
    public Dtos.WalletDto create(@RequestBody(required = false) Dtos.CreateWalletRequest req) {
        Wallet w = wallets.create(req == null ? null : req.label());
        return toDto(wallets.assignOwner(w, currentUser()));
    }

    /** Create a quantum-safe (hybrid ECDSA + ML-DSA) wallet. */
    @PostMapping("/hybrid")
    public Dtos.WalletDto createHybrid(@RequestBody(required = false) Dtos.CreateWalletRequest req) {
        Wallet w = wallets.createHybrid(req == null ? null : req.label());
        return toDto(wallets.assignOwner(w, currentUser()));
    }

    @GetMapping("/{address}")
    public Dtos.WalletDto get(@PathVariable String address) {
        return toDto(wallets.require(address));
    }

    @GetMapping("/{address}/utxos")
    public List<Dtos.UtxoDto> utxos(@PathVariable String address) {
        return wallets.utxosOf(address).stream()
                .map(u -> new Dtos.UtxoDto(u.getTxId(), u.getOutIndex(), u.getAmount()))
                .toList();
    }

    private Dtos.WalletDto toDto(Wallet w) {
        return new Dtos.WalletDto(w.getLabel(), w.getAddress(), w.getPublicKey(),
                wallets.balanceOf(w.getAddress()), wallets.immatureBalanceOf(w.getAddress()), w.isHybrid());
    }
}

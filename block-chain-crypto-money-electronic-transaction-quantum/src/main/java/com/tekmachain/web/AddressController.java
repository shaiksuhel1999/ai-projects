package com.tekmachain.web;

import com.tekmachain.service.WalletService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Address-level queries that work for ANY address — including self-custody wallets
 * whose keys live only in the user's browser and were never registered on the node.
 */
@RestController
@RequestMapping("/api/address")
public class AddressController {

    private final WalletService wallets;

    public AddressController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/{address}/balance")
    public Map<String, Object> balance(@PathVariable String address) {
        BigDecimal bal = wallets.balanceOf(address);
        return Map.of("address", address, "balance", bal);
    }
}

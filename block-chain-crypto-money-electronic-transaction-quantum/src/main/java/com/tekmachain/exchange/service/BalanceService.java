package com.tekmachain.exchange.service;

import com.tekmachain.exchange.Money;
import com.tekmachain.exchange.domain.Balance;
import com.tekmachain.exchange.repo.BalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The exchange's internal ledger of custodied balances. Every mutation keeps
 * {@code available} and {@code locked} consistent; orders reserve funds by moving
 * them from available to locked, and settle by debiting locked / crediting available.
 */
@Service
public class BalanceService {

    private final BalanceRepository balances;

    public BalanceService(BalanceRepository balances) {
        this.balances = balances;
    }

    @Transactional
    public Balance get(String address, String asset) {
        return balances.findByAddressAndAsset(address, asset)
                .orElseGet(() -> balances.save(new Balance(address, asset)));
    }

    @Transactional(readOnly = true)
    public BigDecimal available(String address, String asset) {
        return balances.findByAddressAndAsset(address, asset)
                .map(Balance::getAvailable).orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public List<Balance> forAddress(String address) {
        return balances.findByAddress(address);
    }

    /** available -> locked. Throws if there isn't enough available. */
    @Transactional
    public void lock(String address, String asset, BigDecimal amount) {
        Balance b = get(address, asset);
        if (b.getAvailable().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient " + asset + ": need " + Money.norm(amount)
                    + ", have " + Money.norm(b.getAvailable()));
        }
        b.setAvailable(Money.norm(b.getAvailable().subtract(amount)));
        b.setLocked(Money.norm(b.getLocked().add(amount)));
        balances.save(b);
    }

    /** locked -> available (e.g. on cancel). */
    @Transactional
    public void unlock(String address, String asset, BigDecimal amount) {
        Balance b = get(address, asset);
        BigDecimal amt = amount.min(b.getLocked());
        b.setLocked(Money.norm(b.getLocked().subtract(amt)));
        b.setAvailable(Money.norm(b.getAvailable().add(amt)));
        balances.save(b);
    }

    /** Remove locked funds that were spent in a fill. */
    @Transactional
    public void spendLocked(String address, String asset, BigDecimal amount) {
        Balance b = get(address, asset);
        b.setLocked(Money.norm(b.getLocked().subtract(amount)));
        balances.save(b);
    }

    /** Spend available funds directly (used by MARKET taker orders). */
    @Transactional
    public void spendAvailable(String address, String asset, BigDecimal amount) {
        Balance b = get(address, asset);
        if (b.getAvailable().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient " + asset);
        }
        b.setAvailable(Money.norm(b.getAvailable().subtract(amount)));
        balances.save(b);
    }

    @Transactional
    public void credit(String address, String asset, BigDecimal amount) {
        Balance b = get(address, asset);
        b.setAvailable(Money.norm(b.getAvailable().add(amount)));
        balances.save(b);
    }
}

package com.tekmachain.exchange.service;

import com.tekmachain.currency.CurrencyService;
import com.tekmachain.domain.Transaction;
import com.tekmachain.domain.Wallet;
import com.tekmachain.exchange.Money;
import com.tekmachain.exchange.domain.Balance;
import com.tekmachain.exchange.domain.PendingDeposit;
import com.tekmachain.exchange.repo.PendingDepositRepository;
import com.tekmachain.p2p.NetworkService;
import com.tekmachain.repo.UtxoRepository;
import com.tekmachain.repo.WalletRepository;
import com.tekmachain.service.TransactionService;
import com.tekmachain.service.WalletService;
import com.tekmachain.web.dto.Mapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * The bridge between the blockchain and the exchange's internal ledger.
 *
 * <ul>
 *   <li><b>Deposit TEK</b>: sign an on-chain transfer from the user's wallet to the
 *       exchange hot wallet; once it's mined, the user's exchange TEK is credited.</li>
 *   <li><b>Withdraw TEK</b>: debit the exchange balance and send TEK on-chain from
 *       the hot wallet back to the user's wallet.</li>
 *   <li><b>Fiat</b> (USD/INR): simulated instant credits/debits — there is no real bank.</li>
 * </ul>
 */
@Service
public class ExchangeService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);
    private static final String HOT_WALLET_LABEL = "Exchange Hot Wallet";
    private static final String TEK = "TEK";
    private static final BigDecimal DEPOSIT_FEE = new BigDecimal("0.01");

    private final WalletService walletService;
    private final WalletRepository wallets;
    private final TransactionService txService;
    private final UtxoRepository utxos;
    private final BalanceService balances;
    private final PendingDepositRepository pendingDeposits;
    private final NetworkService network;
    private final CurrencyService currencies;

    private volatile String hotWalletAddress;

    public ExchangeService(WalletService walletService, WalletRepository wallets, TransactionService txService,
                           UtxoRepository utxos, BalanceService balances,
                           PendingDepositRepository pendingDeposits, NetworkService network,
                           CurrencyService currencies) {
        this.walletService = walletService;
        this.wallets = wallets;
        this.txService = txService;
        this.utxos = utxos;
        this.balances = balances;
        this.pendingDeposits = pendingDeposits;
        this.network = network;
        this.currencies = currencies;
    }

    @PostConstruct
    @Transactional
    public void ensureHotWallet() {
        Wallet hot = wallets.findFirstByLabel(HOT_WALLET_LABEL)
                .orElseGet(() -> walletService.create(HOT_WALLET_LABEL));
        this.hotWalletAddress = hot.getAddress();
        log.info("Exchange hot wallet: {}", hotWalletAddress);
    }

    public String hotWalletAddress() {
        return hotWalletAddress;
    }

    @Transactional(readOnly = true)
    public List<Balance> account(String address) {
        return balances.forAddress(address);
    }

    // ---- TEK (on-chain) ----

    @Transactional
    public String depositTek(String address, BigDecimal amountRaw) {
        BigDecimal amount = Money.norm(amountRaw);
        if (!Money.gtZero(amount)) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
        walletService.require(address); // must be a known wallet
        Transaction tx = txService.createPayment(address, hotWalletAddress, amount, DEPOSIT_FEE);
        network.broadcastTransaction(Mapper.tx(tx));
        pendingDeposits.save(new PendingDeposit(tx.getTxId(), address, amount));
        return "Deposit of " + amount + " TEK submitted. Mine a block to confirm — it will be credited automatically.";
    }

    @Transactional
    public String withdrawTek(String address, BigDecimal amountRaw) {
        BigDecimal amount = Money.norm(amountRaw);
        if (!Money.gtZero(amount)) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }
        walletService.require(address);
        balances.spendAvailable(address, TEK, amount); // debit exchange balance up front
        try {
            Transaction tx = txService.createPayment(hotWalletAddress, address, amount, BigDecimal.ZERO);
            network.broadcastTransaction(Mapper.tx(tx));
            return "Withdrawal of " + amount + " TEK sent on-chain. Mine a block to confirm.";
        } catch (RuntimeException e) {
            balances.credit(address, TEK, amount); // refund on failure (e.g. hot wallet not yet funded)
            throw new IllegalStateException("Withdrawal failed: " + e.getMessage()
                    + " (the hot wallet may have unconfirmed deposits — mine a block and retry)");
        }
    }

    /** Credit confirmed TEK deposits. Driven on a timer by {@code DepositReconciler} so mining a block settles them. */
    @Transactional
    public void reconcileDeposits() {
        for (PendingDeposit d : pendingDeposits.findByCreditedFalse()) {
            boolean confirmed = utxos.findByTxIdAndOutIndex(d.getTxId(), 0)
                    .map(u -> u.getAddress().equals(hotWalletAddress)).orElse(false);
            if (confirmed) {
                balances.credit(d.getAddress(), TEK, d.getAmount());
                d.setCredited(true);
                pendingDeposits.save(d);
                log.info("Credited deposit {} TEK to {}", d.getAmount(), d.getAddress());
            }
        }
    }

    // ---- Fiat (simulated) ----

    @Transactional
    public String depositFiat(String address, String asset, BigDecimal amountRaw) {
        requireFiat(asset);
        BigDecimal amount = Money.norm(amountRaw);
        if (!Money.gtZero(amount)) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        balances.credit(address, asset, amount);
        return "Deposited " + amount + " " + asset + " (simulated).";
    }

    @Transactional
    public String withdrawFiat(String address, String asset, BigDecimal amountRaw) {
        requireFiat(asset);
        BigDecimal amount = Money.norm(amountRaw);
        if (!Money.gtZero(amount)) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        balances.spendAvailable(address, asset, amount);
        return "Withdrew " + amount + " " + asset + " (simulated).";
    }

    private void requireFiat(String asset) {
        if (TEK.equals(asset) || !currencies.exists(asset)) {
            throw new IllegalArgumentException("Not a known fiat currency: " + asset);
        }
    }
}

package com.tekmachain.exchange.web;

import com.tekmachain.exchange.domain.Balance;
import com.tekmachain.exchange.domain.Order;
import com.tekmachain.exchange.domain.OrderType;
import com.tekmachain.exchange.domain.Side;
import com.tekmachain.exchange.dto.ExDtos;
import com.tekmachain.exchange.repo.OrderRepository;
import com.tekmachain.exchange.service.ExchangeService;
import com.tekmachain.exchange.service.MarketDataService;
import com.tekmachain.exchange.service.MatchingEngine;
import com.tekmachain.auth.WalletAccessGuard;
import com.tekmachain.profile.PaymentAccount;
import com.tekmachain.profile.PaymentAccountService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange")
public class ExchangeController {

    private static final List<String> ASSETS = List.of("TEK", "USD", "INR");

    private final MatchingEngine engine;
    private final MarketDataService market;
    private final ExchangeService exchange;
    private final OrderRepository orders;
    private final PaymentAccountService accounts;
    private final WalletAccessGuard guard;

    public ExchangeController(MatchingEngine engine, MarketDataService market, ExchangeService exchange,
                              OrderRepository orders, PaymentAccountService accounts, WalletAccessGuard guard) {
        this.engine = engine;
        this.market = market;
        this.exchange = exchange;
        this.orders = orders;
        this.accounts = accounts;
        this.guard = guard;
    }

    // ---- market data ----

    @GetMapping("/pairs")
    public List<String> pairs() {
        return market.pairs();
    }

    @GetMapping("/orderbook")
    public ExDtos.OrderBookDto orderBook(@RequestParam String pair) {
        return market.orderBook(pair);
    }

    @GetMapping("/ticker")
    public ExDtos.TickerDto ticker(@RequestParam String pair) {
        return market.ticker(pair);
    }

    @GetMapping("/trades")
    public List<ExDtos.TradeDto> trades(@RequestParam String pair) {
        return market.recentTrades(pair);
    }

    @GetMapping("/hotwallet")
    public Map<String, String> hotWallet() {
        return Map.of("address", exchange.hotWalletAddress());
    }

    // ---- account ----

    @GetMapping("/account/{address}")
    public ExDtos.AccountDto account(@PathVariable String address) {
        guard.assertCanActAs(address);
        Map<String, Balance> byAsset = new java.util.HashMap<>();
        for (Balance b : exchange.account(address)) {
            byAsset.put(b.getAsset(), b);
        }
        List<ExDtos.BalanceDto> list = new ArrayList<>();
        for (String asset : ASSETS) {
            Balance b = byAsset.get(asset);
            list.add(b == null
                    ? new ExDtos.BalanceDto(asset, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
                    : ExDtos.balance(b));
        }
        return new ExDtos.AccountDto(address, list);
    }

    @GetMapping("/orders/{address}")
    public List<ExDtos.OrderDto> orders(@PathVariable String address) {
        guard.assertCanActAs(address);
        return orders.findByAddressOrderByCreatedAtDesc(address).stream().map(ExDtos::order).toList();
    }

    // ---- trading ----

    @PostMapping("/orders")
    public ExDtos.OrderDto place(@RequestBody ExDtos.PlaceOrderRequest req) {
        guard.assertCanActAs(req.address());
        Side side = Side.valueOf(req.side().trim().toUpperCase());
        OrderType type = OrderType.valueOf(req.type().trim().toUpperCase());
        Order o = engine.place(req.address(), req.pair(), side, type, req.price(), req.amount());
        return ExDtos.order(o);
    }

    @PostMapping("/orders/{id}/cancel")
    public ExDtos.OrderDto cancel(@PathVariable Long id, @RequestParam String address) {
        guard.assertCanActAs(address);
        return ExDtos.order(engine.cancel(address, id));
    }

    // ---- funding ----

    @PostMapping("/deposit/tek")
    public Map<String, String> depositTek(@RequestBody ExDtos.DepositCryptoRequest req) {
        guard.assertCanActAs(req.address());
        return Map.of("message", exchange.depositTek(req.address(), req.amount()));
    }

    @PostMapping("/withdraw/tek")
    public Map<String, String> withdrawTek(@RequestBody ExDtos.WithdrawCryptoRequest req) {
        guard.assertCanActAs(req.address());
        return Map.of("message", exchange.withdrawTek(req.address(), req.amount()));
    }

    @PostMapping("/deposit/fiat")
    public Map<String, String> depositFiat(@RequestBody ExDtos.FiatRequest req) {
        guard.assertCanActAs(req.address());
        PaymentAccount bank = accounts.requireBank(guard.currentUser(), req.bankAccountId());
        String msg = exchange.depositFiat(req.address(), req.asset(), req.amount());
        return Map.of("message", msg + " From " + bank.getInstitution() + " " + bank.maskedNumber() + ".");
    }

    @PostMapping("/withdraw/fiat")
    public Map<String, String> withdrawFiat(@RequestBody ExDtos.FiatRequest req) {
        guard.assertCanActAs(req.address());
        PaymentAccount bank = accounts.requireBank(guard.currentUser(), req.bankAccountId());
        String msg = exchange.withdrawFiat(req.address(), req.asset(), req.amount());
        return Map.of("message", msg + " To " + bank.getInstitution() + " " + bank.maskedNumber() + ".");
    }
}

package com.tekmachain.exchange.dto;

import com.tekmachain.exchange.domain.Balance;
import com.tekmachain.exchange.domain.Order;
import com.tekmachain.exchange.domain.Trade;

import java.math.BigDecimal;
import java.util.List;

/** Request/response shapes for the exchange API. */
public final class ExDtos {

    private ExDtos() {
    }

    // ---- requests ----
    public record PlaceOrderRequest(String address, String pair, String side, String type,
                                    BigDecimal price, BigDecimal amount) {
    }

    public record DepositCryptoRequest(String address, BigDecimal amount) {
    }

    public record WithdrawCryptoRequest(String address, BigDecimal amount) {
    }

    public record FiatRequest(String address, String asset, BigDecimal amount, Long bankAccountId) {
    }

    // ---- responses ----
    public record OrderDto(Long id, String address, String pair, String side, String type,
                           BigDecimal price, BigDecimal amount, BigDecimal filled,
                           BigDecimal remaining, String status, long createdAt) {
    }

    public record TradeDto(Long id, String pair, BigDecimal price, BigDecimal amount,
                           BigDecimal quoteAmount, String takerSide, long createdAt) {
    }

    public record BookLevel(BigDecimal price, BigDecimal amount, BigDecimal total) {
    }

    public record OrderBookDto(String pair, List<BookLevel> bids, List<BookLevel> asks,
                               BigDecimal bestBid, BigDecimal bestAsk, BigDecimal spread) {
    }

    public record TickerDto(String pair, BigDecimal lastPrice, BigDecimal changePct,
                            BigDecimal high, BigDecimal low, BigDecimal baseVolume,
                            BigDecimal quoteVolume, BigDecimal bestBid, BigDecimal bestAsk) {
    }

    public record BalanceDto(String asset, BigDecimal available, BigDecimal locked, BigDecimal total) {
    }

    public record AccountDto(String address, List<BalanceDto> balances) {
    }

    public static OrderDto order(Order o) {
        return new OrderDto(o.getId(), o.getAddress(), o.getPair(), o.getSide().name(), o.getType().name(),
                o.getPrice(), o.getAmount(), o.getFilled(), o.remaining(), o.getStatus().name(), o.getCreatedAt());
    }

    public static TradeDto trade(Trade t) {
        return new TradeDto(t.getId(), t.getPair(), t.getPrice(), t.getAmount(), t.getQuoteAmount(),
                t.getTakerSide().name(), t.getCreatedAt());
    }

    public static BalanceDto balance(Balance b) {
        return new BalanceDto(b.getAsset(), b.getAvailable(), b.getLocked(), b.total());
    }
}

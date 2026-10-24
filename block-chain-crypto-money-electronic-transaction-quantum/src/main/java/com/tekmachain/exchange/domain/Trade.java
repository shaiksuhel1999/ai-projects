package com.tekmachain.exchange.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** A completed match between a maker and a taker order. */
@Entity
@Table(name = "ex_trade")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pair;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal quoteAmount;

    private Long buyOrderId;
    private Long sellOrderId;

    private String makerAddress;
    private String takerAddress;

    @Enumerated(EnumType.STRING)
    private Side takerSide;

    @Column(nullable = false)
    private long createdAt;

    public Trade() {
    }

    public Long getId() { return id; }

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getQuoteAmount() { return quoteAmount; }
    public void setQuoteAmount(BigDecimal quoteAmount) { this.quoteAmount = quoteAmount; }

    public Long getBuyOrderId() { return buyOrderId; }
    public void setBuyOrderId(Long buyOrderId) { this.buyOrderId = buyOrderId; }

    public Long getSellOrderId() { return sellOrderId; }
    public void setSellOrderId(Long sellOrderId) { this.sellOrderId = sellOrderId; }

    public String getMakerAddress() { return makerAddress; }
    public void setMakerAddress(String makerAddress) { this.makerAddress = makerAddress; }

    public String getTakerAddress() { return takerAddress; }
    public void setTakerAddress(String takerAddress) { this.takerAddress = takerAddress; }

    public Side getTakerSide() { return takerSide; }
    public void setTakerSide(Side takerSide) { this.takerSide = takerSide; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

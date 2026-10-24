package com.tekmachain.exchange.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * A trading order. Base asset is always TEK; the quote asset comes from the pair
 * (e.g. TEK/USD → quote USD). {@code lockedAmount} tracks the funds still reserved
 * for this order (quote for BUY, base for SELL) so cancelling refunds exactly the
 * right amount.
 */
@Entity
@Table(name = "ex_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String pair;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderType type;

    /** Null for MARKET orders. */
    @Column(precision = 30, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal filled = BigDecimal.ZERO;

    @Column(nullable = false, precision = 30, scale = 8)
    private BigDecimal lockedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.OPEN;

    @Column(nullable = false)
    private long createdAt;

    private long updatedAt;

    public Order() {
    }

    public BigDecimal remaining() {
        return amount.subtract(filled);
    }

    public String quoteAsset() {
        return pair.substring(pair.indexOf('/') + 1);
    }

    public String baseAsset() {
        return pair.substring(0, pair.indexOf('/'));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public Side getSide() { return side; }
    public void setSide(Side side) { this.side = side; }

    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFilled() { return filled; }
    public void setFilled(BigDecimal filled) { this.filled = filled; }

    public BigDecimal getLockedAmount() { return lockedAmount; }
    public void setLockedAmount(BigDecimal lockedAmount) { this.lockedAmount = lockedAmount; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

package com.tekmachain.exchange.domain;

public enum OrderStatus {
    OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED;

    public boolean isRestingOnBook() {
        return this == OPEN || this == PARTIALLY_FILLED;
    }
}

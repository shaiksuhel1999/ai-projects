package com.tekmachain.exchange.domain;

public enum Side {
    BUY, SELL;

    public Side opposite() {
        return this == BUY ? SELL : BUY;
    }
}

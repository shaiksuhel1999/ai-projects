package com.tekmachain.exchange;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Consistent 8-decimal rounding for every exchange amount. */
public final class Money {

    private Money() {
    }

    public static BigDecimal norm(BigDecimal v) {
        return v == null ? null : v.setScale(8, RoundingMode.HALF_UP);
    }

    public static boolean gtZero(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}

package com.tekmachain.exchange;

import java.util.List;

/** The trading pairs TekmaChain's exchange lists. Base is always TEK. */
public final class Pairs {

    public static final String BASE = "TEK";
    public static final List<String> SUPPORTED =
            List.of("TEK/USD", "TEK/EUR", "TEK/GBP", "TEK/JPY", "TEK/INR");

    private Pairs() {
    }

    public static boolean isSupported(String pair) {
        return SUPPORTED.contains(pair);
    }

    public static String quoteOf(String pair) {
        return pair.substring(pair.indexOf('/') + 1);
    }
}

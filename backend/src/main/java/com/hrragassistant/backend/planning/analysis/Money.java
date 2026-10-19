package com.hrragassistant.backend.planning.analysis;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Formatting shared by the household analysis: "€12,600", "18.0%", "4.2 months". */
final class Money {

    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ENGLISH);

    private Money() {
    }

    static String eur(double v) {
        return (v < 0 ? "-€" : "€") + new DecimalFormat("#,##0", SYMBOLS).format(Math.abs(v));
    }

    static String pct(double ratio) {
        return new DecimalFormat("0.0", SYMBOLS).format(ratio * 100) + "%";
    }

    static String pctPoints(double pct) {
        return new DecimalFormat("0.00", SYMBOLS).format(pct) + "%";
    }

    static String months(double m) {
        return new DecimalFormat("0.0", SYMBOLS).format(m) + " months";
    }
}

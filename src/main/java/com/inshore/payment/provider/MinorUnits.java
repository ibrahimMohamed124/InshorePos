package com.inshore.payment.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Converts between our BigDecimal amounts and the integer "minor units" most providers use
 * (1000.50 EGP = 100050, 500 JPY = 500, 1.500 KWD = 1500). Meant to be used by provider adapters only -
 * business code never sees minor units.
 */
public final class MinorUnits {

    private MinorUnits() {
    }

    public static int fractionDigits(String currency) {
        int digits = Currency.getInstance(currency).getDefaultFractionDigits();
        if (digits < 0) {
            throw new IllegalArgumentException("currency has no minor unit: " + currency);
        }
        return digits;
    }

    /** Throws ArithmeticException instead of silently rounding money. */
    public static long toMinor(BigDecimal amount, String currency) {
        int digits = fractionDigits(currency);

        return amount.setScale(digits, RoundingMode.UNNECESSARY).movePointRight(digits).longValueExact();
    }

    public static BigDecimal fromMinor(long minor, String currency) {
        return BigDecimal.valueOf(minor, fractionDigits(currency));
    }
}

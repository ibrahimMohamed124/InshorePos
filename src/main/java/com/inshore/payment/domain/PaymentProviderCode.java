package com.inshore.payment.domain;

import java.util.Locale;

/**
 * Which payment provider handled a payment. Stored as a string. Only FAKE exists for now - a real
 * provider (STRIPE, ADYEN, ...) is added here together with its adapter under
 * {@code com.inshore.payment.provider}.
 */
public enum PaymentProviderCode {
    FAKE;

    public static PaymentProviderCode parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("payment provider is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown payment provider: " + value);
        }
    }
}

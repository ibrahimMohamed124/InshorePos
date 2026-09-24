package com.inshore.payment.domain;

public enum PaymentTransactionType {
    AUTHORIZATION,
    CAPTURE,
    /** Cancelling an authorization that was never captured. */
    VOID,
    REFUND
}

package com.inshore.payment.provider.fake;

/** What the fake gateway can be told to "do" to a payment. */
public enum FakeOutcome {
    SUCCESS,
    FAILURE,
    REQUIRES_ACTION,
    CANCEL,
    EXPIRE,
    REFUND
}

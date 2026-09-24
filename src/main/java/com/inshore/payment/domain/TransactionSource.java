package com.inshore.payment.domain;

/** Where a payment transaction came from - part of the audit trail. */
public enum TransactionSource {
    /** A user calling our API. */
    API,
    /** The provider told us through a webhook. */
    WEBHOOK,
    /** Something we did on our own (e.g. a manual sync). */
    SYSTEM
}

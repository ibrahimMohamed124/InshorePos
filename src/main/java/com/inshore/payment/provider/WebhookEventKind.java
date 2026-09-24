package com.inshore.payment.provider;

/** Provider-independent meaning of a webhook. Each adapter maps its own event names onto these. */
public enum WebhookEventKind {
    PAYMENT_PENDING,
    REQUIRES_ACTION,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    CANCELLED,
    EXPIRED,
    REFUND_SUCCEEDED,
    REFUND_FAILED
}

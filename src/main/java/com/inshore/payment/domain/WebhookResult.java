package com.inshore.payment.domain;

public enum WebhookResult {
    /** The event changed the payment. */
    APPLIED,
    /** Valid, but there was nothing to change (e.g. the payment was already in that state). */
    NO_OP,
    /** No payment matches the event. Stored for review, not applied. */
    UNKNOWN_PAYMENT,
    /** The event contradicts the payment's current state (or its amount). Stored for review, not applied. */
    INVALID_TRANSITION,
    /** Same provider event id seen before - ignored (not stored again). */
    DUPLICATE
}

package com.inshore.payment.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a {@link Payment}. Every status change goes through {@link Payment#transitionTo} which
 * consults {@link #canTransitionTo}; anything not listed here is rejected.
 * <pre>
 * CREATED -> PENDING | REQUIRES_ACTION | AUTHORIZED | CAPTURED | FAILED | CANCELLED | EXPIRED
 * PENDING -> REQUIRES_ACTION | AUTHORIZED | CAPTURED | FAILED | CANCELLED | EXPIRED
 * REQUIRES_ACTION -> PENDING | AUTHORIZED | CAPTURED | FAILED | CANCELLED | EXPIRED
 * AUTHORIZED -> CAPTURED | CANCELLED | EXPIRED
 * CAPTURED -> PARTIALLY_REFUNDED | REFUNDED
 * PARTIALLY_REFUNDED -> REFUNDED
 * FAILED / CANCELLED / EXPIRED / REFUNDED -> (terminal)
 * </pre>
 * CREATED may jump straight to any provider outcome because it means "we have not heard back from the
 * provider yet" - a webhook can legitimately arrive before our own call to the provider returns.
 */
public enum PaymentStatus {
    /** Saved on our side; the provider has not answered yet. */
    CREATED,
    /** The provider accepted it and is waiting for the outcome (webhook). */
    PENDING,
    /** The customer must do something first (3-D Secure, hosted checkout). */
    REQUIRES_ACTION,
    /** Funds are held but not yet collected (authorize + capture flow). */
    AUTHORIZED,
    /** Funds collected - this is what completes the order. */
    CAPTURED,
    /** The provider declined / the attempt failed for good. */
    FAILED,
    /** Cancelled before collection. */
    CANCELLED,
    /** The checkout or the authorization timed out. */
    EXPIRED,
    /** Part of the captured amount was given back (derived from the refund transactions). */
    PARTIALLY_REFUNDED,
    /** All of the captured amount was given back (derived from the refund transactions). */
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS = new EnumMap<>(PaymentStatus.class);

    static {
        TRANSITIONS.put(CREATED, EnumSet.of(PENDING, REQUIRES_ACTION, AUTHORIZED, CAPTURED, FAILED, CANCELLED, EXPIRED));
        TRANSITIONS.put(PENDING, EnumSet.of(REQUIRES_ACTION, AUTHORIZED, CAPTURED, FAILED, CANCELLED, EXPIRED));
        TRANSITIONS.put(REQUIRES_ACTION, EnumSet.of(PENDING, AUTHORIZED, CAPTURED, FAILED, CANCELLED, EXPIRED));
        TRANSITIONS.put(AUTHORIZED, EnumSet.of(CAPTURED, CANCELLED, EXPIRED));
        TRANSITIONS.put(CAPTURED, EnumSet.of(PARTIALLY_REFUNDED, REFUNDED));
        TRANSITIONS.put(PARTIALLY_REFUNDED, EnumSet.of(REFUNDED));
        TRANSITIONS.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS.put(EXPIRED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS.put(REFUNDED, EnumSet.noneOf(PaymentStatus.class));
    }

    /** Statuses that still "occupy" an order - a new payment for it is refused while one of these exists. */
    public static final Set<PaymentStatus> BLOCKING = EnumSet.complementOf(EnumSet.of(FAILED, CANCELLED, EXPIRED));

    /** Statuses in which money was actually collected (used to tell an order was paid online). */
    public static final Set<PaymentStatus> COLLECTED = EnumSet.of(CAPTURED, PARTIALLY_REFUNDED, REFUNDED);

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isRefundable() {
        return this == CAPTURED || this == PARTIALLY_REFUNDED;
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}

package com.inshore.payment.dto;

/** Result of creating a payment: the payment plus whether it was an idempotent replay of an earlier request. */
public record PaymentOutcome(PaymentResponse payment, boolean replayed) {
}

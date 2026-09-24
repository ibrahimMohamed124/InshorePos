package com.inshore.payment.provider;

public record CancelRequest(String providerPaymentId, String idempotencyKey) {
}

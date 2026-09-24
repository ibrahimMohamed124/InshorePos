package com.inshore.payment.provider;

import com.inshore.payment.domain.PaymentTransactionStatus;

/** PENDING = the provider accepted it and will confirm through a webhook. */
public record ProviderRefundResult(PaymentTransactionStatus status, String providerTransactionId, String failureCode) {
}

package com.inshore.payment.provider;

import com.inshore.payment.domain.PaymentStatus;

public record PaymentStatusResult(String providerPaymentId, PaymentStatus status, String failureCode) {
}

package com.inshore.payment.provider;

import com.inshore.payment.domain.PaymentTransactionStatus;

public record CaptureResult(PaymentTransactionStatus status, String providerTransactionId, String failureCode) {
}

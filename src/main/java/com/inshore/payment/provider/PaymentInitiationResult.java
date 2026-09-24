package com.inshore.payment.provider;

import com.inshore.payment.domain.PaymentStatus;

public record PaymentInitiationResult(
        String providerPaymentId,
        PaymentStatus status,
        String nextActionUrl,
        String cardBrand,
        String cardLast4,
        String failureCode
) {
}

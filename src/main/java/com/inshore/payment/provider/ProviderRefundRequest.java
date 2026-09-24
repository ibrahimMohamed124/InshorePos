package com.inshore.payment.provider;

import java.math.BigDecimal;

public record ProviderRefundRequest(
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String reason
) {
}

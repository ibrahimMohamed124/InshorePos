package com.inshore.payment.provider;

import com.inshore.order.domain.PaymentType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * @param paymentId our payment id: sent to the provider as the reference AND as its idempotency key
 * @param metadata  non-sensitive references (orderId, storeId, ...)
 */
public record PaymentRequest(
        UUID paymentId,
        BigDecimal amount,
        String currency,
        PaymentType method,
        String description,
        String returnUrl,
        Map<String, String> metadata
) {
}

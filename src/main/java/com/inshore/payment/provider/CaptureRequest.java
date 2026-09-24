package com.inshore.payment.provider;

import java.math.BigDecimal;

public record CaptureRequest(String providerPaymentId, BigDecimal amount, String currency, String idempotencyKey) {
}

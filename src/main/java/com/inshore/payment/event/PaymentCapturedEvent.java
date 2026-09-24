package com.inshore.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published (synchronously, inside the transaction that captured the payment) when money was collected.
 * The order module listens and completes the order - the payment module never calls it directly.
 */
public record PaymentCapturedEvent(UUID paymentId, Long orderId, Long storeId, BigDecimal amount) {
}

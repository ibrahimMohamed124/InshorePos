package com.inshore.payment.dto;

/**
 * Result of a refund request.
 *
 * @param replayed whether the idempotency key had been used before
 * @param refundId id of the accounting {@code Refund} row once the refund succeeded, else null
 *                 (a PENDING refund gets its accounting row when the provider confirms it)
 */
public record RefundOutcome(RefundResponse refund, boolean replayed, Long refundId) {
}

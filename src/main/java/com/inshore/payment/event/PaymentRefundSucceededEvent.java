package com.inshore.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published (synchronously, inside the same transaction) when a refund was confirmed by the provider.
 * The refund module listens, writes the accounting {@code Refund} row and reports its id back through
 * {@link #setRefundId} so it can be linked to the payment transaction.
 */
public class PaymentRefundSucceededEvent {

    private final UUID paymentId;
    private final UUID transactionId;
    private final Long orderId;
    private final BigDecimal amount;
    private final String reason;
    // null when the refund was started at the provider (not by one of our users)
    private final UUID requestedBy;
    private final UUID approvedBy;
    private Long refundId;

    public PaymentRefundSucceededEvent(UUID paymentId, UUID transactionId, Long orderId, BigDecimal amount,
                                       String reason, UUID requestedBy, UUID approvedBy) {
        this.paymentId = paymentId;
        this.transactionId = transactionId;
        this.orderId = orderId;
        this.amount = amount;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.approvedBy = approvedBy;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getReason() {
        return reason;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public UUID getApprovedBy() {
        return approvedBy;
    }

    public Long getRefundId() {
        return refundId;
    }

    public void setRefundId(Long refundId) {
        this.refundId = refundId;
    }
}

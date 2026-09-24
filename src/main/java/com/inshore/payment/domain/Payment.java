package com.inshore.payment.domain;

import com.inshore.branch.domain.Branch;
import com.inshore.order.domain.Order;
import com.inshore.order.domain.PaymentType;
import com.inshore.payment.exception.InvalidPaymentStateException;
import com.inshore.payment.exception.RefundAmountExceededException;
import com.inshore.store.domain.Store;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One attempt to collect the money for one {@link Order}. Tenant-scoped: {@link #store} is the tenant
 * (copied from the order's branch when the payment is created) and every lookup goes through it.
 * <p>
 * Money movements after creation (authorization, capture, refunds) are recorded as
 * {@link PaymentTransaction} rows; {@link #capturedAmount} / {@link #refundedAmount} are running totals
 * kept in step with them under a row lock. The status is only ever changed through
 * {@link #transitionTo}, {@link #recordCapture} and {@link #recordRefund}.
 */
@Entity
@Table(name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payments_store_idempotency", columnNames = {"store_id", "idempotency_key"}),
                @UniqueConstraint(name = "uq_payments_provider_payment", columnNames = {"provider", "provider_payment_id"})
        },
        indexes = {
                @Index(name = "idx_payments_order_id", columnList = "order_id"),
                @Index(name = "idx_payments_store_created_at", columnList = "store_id, created_at"),
                @Index(name = "idx_payments_status", columnList = "status")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @UuidGenerator
    private UUID id;

    // optimistic lock on top of the pessimistic row lock the services take
    @Version
    private Long version;

    // the tenant
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProviderCode provider;

    // null until the provider answers
    @Setter
    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    // SHA-256 of what the request asked for - the same key with a different request is a client bug
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentType paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(name = "captured_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    // where to send the customer (hosted checkout / 3-D Secure) - comes from the provider
    @Setter
    @Column(name = "next_action_url", length = 1024)
    private String nextActionUrl;

    // display metadata only, when the provider gives it. Never a card number / CVV.
    @Setter
    @Column(name = "card_brand", length = 20)
    private String cardBrand;

    @Setter
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Setter
    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Setter
    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Same status = nothing to do. Anything the state machine does not allow is rejected. */
    public void transitionTo(PaymentStatus target) {
        if (status == target) {
            return;
        }
        if (!status.canTransitionTo(target)) {
            throw new InvalidPaymentStateException(
                    "a " + status + " payment cannot move to " + target);
        }
        status = target;
    }

    /** Full capture only (v1): the collected amount is the payment amount. */
    public void recordCapture(BigDecimal collected) {
        transitionTo(PaymentStatus.CAPTURED);
        capturedAmount = collected;
    }

    /**
     * Adds a successful refund to the running total and derives PARTIALLY_REFUNDED / REFUNDED from it.
     * The status is never set by hand.
     */
    public void recordRefund(BigDecimal amount) {
        if (!status.isRefundable()) {
            throw new InvalidPaymentStateException("a " + status + " payment cannot be refunded");
        }
        BigDecimal total = refundedAmount.add(amount);
        if (total.compareTo(capturedAmount) > 0) {
            throw new RefundAmountExceededException(
                    "refund amount exceeds the refundable balance of " + refundableAmount());
        }
        refundedAmount = total;
        transitionTo(total.compareTo(capturedAmount) == 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
    }

    /** Captured minus already refunded (pending refunds are accounted for by the service, not here). */
    public BigDecimal refundableAmount() {
        return capturedAmount.subtract(refundedAmount);
    }
}

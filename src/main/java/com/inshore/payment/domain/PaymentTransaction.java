package com.inshore.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only money ledger of a {@link Payment}: authorization, capture, void and refund rows.
 * Rows are never deleted and the amount never changes; a row only moves once from PENDING to
 * SUCCEEDED/FAILED. Example: CAPTURE 10000, REFUND 3000, REFUND 2000 -> the payment is PARTIALLY_REFUNDED
 * with 5000 refunded.
 */
@Entity
@Table(name = "payment_transactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payment_tx_idempotency", columnNames = {"payment_id", "idempotency_key"}),
                @UniqueConstraint(name = "uq_payment_tx_provider_tx", columnNames = {"payment_id", "provider_transaction_id"})
        },
        indexes = {
                @Index(name = "idx_payment_tx_payment_created", columnList = "payment_id, created_at")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentTransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentTransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Setter
    @Column(name = "provider_transaction_id", length = 128)
    private String providerTransactionId;

    // required for refunds / captures so a retried request maps to the same row
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionSource source;

    // who did it (null when it came from the provider)
    @Column(name = "created_by")
    private UUID createdBy;

    // set when the refund came from a manager-approved refund request
    @Column(name = "approved_by")
    private UUID approvedBy;

    // link to the accounting Refund row (com.inshore.refund) once it exists
    @Setter
    @Column(name = "refund_id")
    private Long refundId;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

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

    public boolean isPending() {
        return status == PaymentTransactionStatus.PENDING;
    }

    public void markSucceeded(String providerTxId) {
        status = PaymentTransactionStatus.SUCCEEDED;
        if (providerTxId != null) {
            providerTransactionId = providerTxId;
        }
        failureCode = null;
    }

    public void markFailed(String code) {
        status = PaymentTransactionStatus.FAILED;
        failureCode = code;
    }
}

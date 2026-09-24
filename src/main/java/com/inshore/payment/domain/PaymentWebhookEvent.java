package com.inshore.payment.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Every webhook the provider sent us whose signature checked out. (provider, providerEventId) is unique,
 * so a provider retrying the same event can never be applied twice. Events with a bad signature are
 * rejected before this point and are not stored.
 */
@Entity
@Table(name = "payment_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_webhook_provider_event", columnNames = {"provider", "provider_event_id"})
        },
        indexes = {
                @Index(name = "idx_webhook_payment_id", columnList = "payment_id"),
                @Index(name = "idx_webhook_processed_received", columnList = "processed, received_at")
        })
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhookEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProviderCode provider;

    @Column(name = "provider_event_id", nullable = false, length = 128)
    private String providerEventId;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "provider_payment_id", length = 128)
    private String providerPaymentId;

    // null when no payment matched
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature_verified", nullable = false)
    private boolean signatureVerified;

    @Setter
    @Column(nullable = false)
    private boolean processed;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private WebhookResult result;

    @Setter
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Setter
    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}

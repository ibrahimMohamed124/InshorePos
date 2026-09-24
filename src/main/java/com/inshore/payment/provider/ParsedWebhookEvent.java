package com.inshore.payment.provider;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A verified webhook translated into Inshore terms.
 *
 * @param providerEventId      the provider's unique id for this event (an adapter whose provider has none must
 *                             derive a stable one, e.g. a hash of the body)
 * @param paymentReference     our payment id echoed back by the provider, when it does - lets us match an event
 *                             that arrives before we stored the provider's payment id
 * @param providerTransactionId refund / capture id, when the event is about one
 * @param amount               already converted from minor units; null when the event carries none
 */
public record ParsedWebhookEvent(
        String providerEventId,
        String eventType,
        WebhookEventKind kind,
        String providerPaymentId,
        String paymentReference,
        String providerTransactionId,
        BigDecimal amount,
        String currency,
        Instant occurredAt,
        String failureCode
) {
}

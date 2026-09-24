package com.inshore.payment.provider;

import com.inshore.payment.domain.PaymentProviderCode;

import java.util.Map;

/**
 * The only thing the rest of Inshore knows about a payment provider. Everything specific to Stripe /
 * Adyen / the fake gateway (its DTOs, minor-unit amounts, signature scheme) stays inside the adapter
 * that implements this interface; it talks to us in the internal models of this package.
 * <p>
 * Contract for adapters:
 * <ul>
 *   <li>Amounts arrive as {@code BigDecimal} + ISO currency. Converting to the provider's minor units is
 *   the adapter's job ({@link MinorUnits}).</li>
 *   <li>Calls must be idempotent on the key we send (the payment id / the transaction idempotency key),
 *   because we retry after timeouts.</li>
 *   <li>A network timeout / outage throws {@link ProviderUnavailableException} ("outcome unknown");
 *   a business rejection is returned as a FAILED result, never thrown.</li>
 *   <li>{@link #verifyAndParseWebhook} must verify the signature BEFORE parsing anything and throw
 *   {@code InvalidWebhookException} when it does not check out.</li>
 * </ul>
 */
public interface PaymentProvider {

    PaymentProviderCode code();

    PaymentInitiationResult createPayment(PaymentRequest request);

    PaymentStatusResult getPayment(String providerPaymentId);

    CaptureResult capture(CaptureRequest request);

    CancelResult cancel(CancelRequest request);

    ProviderRefundResult refund(ProviderRefundRequest request);

    /** @param headers header names in lower case */
    ParsedWebhookEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers);
}

package com.inshore.payment.service;

import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.dto.WebhookResponse;

import java.util.Map;

public interface PaymentWebhookService {

    /**
     * The one entry point for provider webhooks - the HTTP controller and the fake gateway's simulator both
     * come through here, so they share the same verification, idempotency and state machine.
     *
     * @param rawBody the body exactly as received (signatures are computed over it)
     * @throws com.inshore.payment.exception.InvalidWebhookException when authenticity cannot be established
     */
    WebhookResponse handle(PaymentProviderCode provider, String rawBody, Map<String, String> headers);
}

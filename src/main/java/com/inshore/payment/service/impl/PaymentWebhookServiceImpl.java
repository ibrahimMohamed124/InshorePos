package com.inshore.payment.service.impl;

import com.inshore.payment.config.PaymentProperties;
import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.WebhookResult;
import com.inshore.payment.dto.WebhookResponse;
import com.inshore.payment.exception.PaymentNotFoundException;
import com.inshore.payment.exception.PaymentUnavailableException;
import com.inshore.payment.provider.ParsedWebhookEvent;
import com.inshore.payment.provider.PaymentProvider;
import com.inshore.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Webhook pipeline: (1) the provider adapter verifies the signature and translates the payload - nothing
 * happens before that succeeds; (2) one transaction stores the event (unique per provider event id) and
 * applies it to the payment, and through events to the order. Verification failures are rejected and never
 * stored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookServiceImpl implements PaymentWebhookService {

    private final PaymentTxService tx;
    private final PaymentProperties properties;
    private final ObjectProvider<PaymentProvider> providers;

    @Override
    public WebhookResponse handle(PaymentProviderCode code, String rawBody, Map<String, String> headers) {
        if (!properties.getWebhook().isEnabled()) {
            throw new PaymentUnavailableException("webhooks are disabled");
        }
        PaymentProvider provider = providers.getIfAvailable();
        // only the configured provider has an endpoint; any other one looks like it does not exist
        if (provider == null || provider.code() != code) {
            throw new PaymentNotFoundException("unknown payment provider");
        }

        Map<String, String> lower = new HashMap<>();
        if (headers != null) {
            headers.forEach((k, v) -> lower.put(k.toLowerCase(Locale.ROOT), v));
        }

        // throws InvalidWebhookException - before any business logic and before anything is stored
        ParsedWebhookEvent event = provider.verifyAndParseWebhook(rawBody, lower);

        WebhookResult result;
        try {
            result = tx.handleWebhook(code, event, rawBody);
        } catch (DataIntegrityViolationException ex) {
            // Two deliveries of the same event raced and the other one won the unique constraint. Only that
            // case is treated as a duplicate; anything else is a real error (and the provider will retry).
            if (!tx.eventExists(code, event.providerEventId())) {
                throw ex;
            }
            result = WebhookResult.DUPLICATE;
        }
        return new WebhookResponse(true, result.name());
    }
}

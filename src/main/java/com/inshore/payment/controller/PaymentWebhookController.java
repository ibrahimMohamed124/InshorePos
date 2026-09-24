package com.inshore.payment.controller;

import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.dto.WebhookResponse;
import com.inshore.payment.exception.PaymentNotFoundException;
import com.inshore.payment.service.PaymentWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public endpoint for provider callbacks (no JWT - see SecurityConfig / JwtValidator). It is authenticated by
 * the provider's signature, which the adapter verifies before anything else happens.
 * <p>
 * The body is taken as a raw String on purpose: signatures are computed over the exact bytes received, so it
 * must not be parsed and re-serialised before verification.
 */
@RestController
@RequestMapping("/api/payments/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentWebhookService webhookService;

    @PostMapping("/{provider}")
    public ResponseEntity<WebhookResponse> receive(
            @PathVariable String provider,
            @RequestBody String rawBody,
            @RequestHeader Map<String, String> headers) {

        PaymentProviderCode code;
        try {
            code = PaymentProviderCode.parse(provider);
        } catch (IllegalArgumentException ex) {
            throw new PaymentNotFoundException("unknown payment provider");
        }
        return ResponseEntity.ok(webhookService.handle(code, rawBody, headers));
    }
}

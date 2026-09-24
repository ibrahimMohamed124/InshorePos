package com.inshore.payment.config;

import com.inshore.payment.domain.PaymentProviderCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Binds the {@code payment.*} properties (see application.properties / application-dev.properties).
 * Secrets (webhook secret, provider API key) come from environment variables only - never from a
 * committed file. Every field has a safe default.
 */
@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    /** Which provider to use ("fake"). Blank = the payment system is switched off. */
    private String provider = "";

    /** Reserved for the real provider adapter (PAYMENT_PROVIDER_API_KEY). Unused by the fake provider. */
    private String apiKey = "";

    private Enforcement enforcement = new Enforcement();
    private Webhook webhook = new Webhook();
    private Fake fake = new Fake();

    public Optional<PaymentProviderCode> providerCode() {
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(PaymentProviderCode.parse(provider));
    }

    public boolean isConfigured() {
        return provider != null && !provider.isBlank();
    }

    /**
     * True when a non-cash order may only be completed through a captured payment. It only bites when a
     * provider is configured - otherwise there would be no way at all to complete a card order.
     */
    public boolean isEnforced() {
        return enforcement.isEnabled() && isConfigured();
    }

    @Getter
    @Setter
    public static class Enforcement {
        /** Block completing non-cash orders by hand (PATCH /api/orders/{id}/status). */
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Webhook {
        private boolean enabled = true;
        /** Shared secret of a real provider (PAYMENT_WEBHOOK_SECRET). Env only. */
        private String secret = "";
        /** How far a signed timestamp may be from now. */
        private long toleranceSeconds = 300;
    }

    /** Only read when payment.provider=fake (dev/local/test profiles). */
    @Getter
    @Setter
    public static class Fake {
        /** HMAC secret the fake gateway signs its webhooks with. Dev value lives in application-dev.properties. */
        private String webhookSecret = "";
        /** sync = refunds succeed at once; async = refunds stay PENDING until simulate-refund. */
        private String refundMode = "sync";
        /** automatic = success captures; manual = success only AUTHORIZES and capture is a separate call. */
        private String captureMode = "automatic";
        /** none | success | failure | requires-action : outcome the fake gateway sends by itself after a delay. */
        private String autoOutcome = "none";
        private long autoDelayMs = 2000;
        /** Base of the (fake) hosted-checkout link returned as nextActionUrl. */
        private String checkoutBaseUrl = "http://localhost:5173/fake-checkout";
    }
}

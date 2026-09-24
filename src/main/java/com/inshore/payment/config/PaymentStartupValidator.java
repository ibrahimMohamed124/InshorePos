package com.inshore.payment.config;

import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.provider.PaymentProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fails the application at startup on a payment configuration that would be unsafe, instead of
 * discovering it at the first real payment:
 * <ul>
 *   <li>{@code payment.provider=fake} outside the dev / local / test profiles (the fake provider must
 *   never be reachable in production);</li>
 *   <li>a provider that has no adapter bean;</li>
 *   <li>a provider without its webhook secret.</li>
 * </ul>
 * A blank {@code payment.provider} is fine: the payment system is simply switched off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentStartupValidator {

    public static final Profiles FAKE_ALLOWED_PROFILES = Profiles.of("dev", "local", "test");

    private final Environment environment;
    private final PaymentProperties properties;
    private final ObjectProvider<PaymentProvider> provider;

    @PostConstruct
    void validate() {
        if (!properties.isConfigured()) {
            log.warn("payment.provider is not set - the payment system is switched off "
                    + "(payment endpoints answer 503, non-cash orders are not blocked)");
            return;
        }

        PaymentProviderCode code = PaymentProviderCode.parse(properties.getProvider());

        if (code == PaymentProviderCode.FAKE) {
            if (!environment.acceptsProfiles(FAKE_ALLOWED_PROFILES)) {
                throw new IllegalStateException(
                        "payment.provider=fake is only allowed with the dev, local or test profile");
            }
            if (isBlank(properties.getFake().getWebhookSecret())) {
                throw new IllegalStateException("payment.fake.webhook-secret must be set when payment.provider=fake");
            }
        } else if (isBlank(properties.getWebhook().getSecret())) {
            throw new IllegalStateException("PAYMENT_WEBHOOK_SECRET must be set for payment provider " + code);
        }

        if (provider.getIfAvailable() == null) {
            throw new IllegalStateException("payment.provider=" + code + " but no PaymentProvider adapter is available");
        }
        log.info("payment system enabled with provider {}", code);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

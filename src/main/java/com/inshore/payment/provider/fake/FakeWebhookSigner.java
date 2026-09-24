package com.inshore.payment.provider.fake;

import com.inshore.payment.exception.InvalidWebhookException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Signature scheme of the fake gateway - deliberately the same shape real providers use: an HMAC-SHA256
 * over "timestamp.body" with a shared secret, plus a timestamp tolerance against replays.
 * Header value: {@code t=<epoch seconds>,v1=<hex hmac>}.
 */
@Component
@Profile({"dev", "local", "test"})
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "fake")
public class FakeWebhookSigner {

    /** Header name, lower case (the webhook service lower-cases every incoming header name). */
    public static final String HEADER = "x-fake-signature";

    public String sign(String secret, long timestampSeconds, String body) {
        return "t=" + timestampSeconds + ",v1=" + hmacHex(secret, timestampSeconds + "." + body);
    }

    /** @throws InvalidWebhookException when the header is missing/malformed, too old, or the signature is wrong */
    public void verify(String secret, String headerValue, String body, long toleranceSeconds, Instant now) {
        if (headerValue == null || headerValue.isBlank()) {
            throw new InvalidWebhookException("missing signature");
        }
        Long timestamp = null;
        String signature = null;
        for (String part : headerValue.split(",")) {
            String p = part.trim();
            if (p.startsWith("t=")) {
                try {
                    timestamp = Long.parseLong(p.substring(2));
                } catch (NumberFormatException ex) {
                    throw new InvalidWebhookException("malformed signature timestamp");
                }
            } else if (p.startsWith("v1=")) {
                signature = p.substring(3);
            }
        }
        if (timestamp == null || signature == null) {
            throw new InvalidWebhookException("malformed signature header");
        }
        if (Math.abs(now.getEpochSecond() - timestamp) > toleranceSeconds) {
            throw new InvalidWebhookException("signature timestamp outside the tolerance window");
        }
        byte[] expected = hmacHex(secret, timestamp + "." + body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        // constant-time comparison
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new InvalidWebhookException("signature mismatch");
        }
    }

    private static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 is not available", ex);
        }
    }
}

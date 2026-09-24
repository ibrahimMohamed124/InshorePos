package com.inshore.payment.provider.fake;

import com.inshore.payment.config.PaymentProperties;
import com.inshore.payment.domain.Payment;
import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.exception.InvalidWebhookException;
import com.inshore.payment.exception.PaymentNotFoundException;
import com.inshore.payment.provider.MinorUnits;
import com.inshore.payment.repository.PaymentRepository;
import com.inshore.payment.service.PaymentService;
import com.inshore.payment.service.PaymentWebhookService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Plays the part of "the payment provider calling us back". Every outcome is delivered as a signed webhook
 * through {@link PaymentWebhookService} - the very same entry point the real webhook controller uses - so
 * signature verification, event idempotency, the state machine and the payment -> order hand-off are all
 * exercised exactly as they would be with a real provider. This class never reads or writes a Payment or an
 * Order itself.
 * <p>
 * Two ways in: the dev endpoints (a user triggers an outcome by hand) and {@code payment.fake.auto-outcome}
 * (the gateway answers by itself after a delay, like a real one would).
 */
@Slf4j
@Component
@Profile({"dev", "local", "test"})
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "fake")
@RequiredArgsConstructor
public class FakeProviderSimulator {

    private final FakePaymentProvider provider;
    private final FakeWebhookSigner signer;
    private final PaymentWebhookService webhookService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentProperties properties;

    private ScheduledExecutorService scheduler;

    @PostConstruct
    void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fake-payment-gateway");
            t.setDaemon(true);
            return t;
        });
        provider.setNewPaymentHook(this::scheduleAutoOutcome);
    }

    @PreDestroy
    void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    /**
     * A user-triggered outcome. The access check is the payment API's own (tenant + branch, 404 otherwise).
     *
     * @param refundAmount only for REFUND; null = everything still refundable
     * @param duplicate    deliver the very same webhook twice (the second one must be ignored)
     */
    public PaymentResponse simulate(UUID paymentId, FakeOutcome outcome, BigDecimal refundAmount, boolean duplicate) {
        FakeGatewayPayment gw = gatewayFor(paymentId);
        deliver(gw, outcome, refundAmount, duplicate);
        return paymentService.getPayment(paymentId);
    }

    /** Sends a "captured" webhook signed with the wrong secret; it must be rejected and change nothing. */
    public String simulateInvalidSignature(UUID paymentId) {
        FakeGatewayPayment gw = gatewayFor(paymentId);
        String body = paymentEvent("payment.captured", gw, null);
        try {
            send(body, "not-the-real-secret");
            return "UNEXPECTED: the webhook with a bad signature was accepted";
        } catch (InvalidWebhookException ex) {
            return "rejected as expected: " + ex.getMessage();
        }
    }

    // ---- delivery ---------------------------------------------------------------------------------

    void deliver(FakeGatewayPayment gw, FakeOutcome outcome, BigDecimal refundAmount, boolean duplicate) {
        String body = switch (outcome) {
            case SUCCESS -> {
                if ("manual".equalsIgnoreCase(properties.getFake().getCaptureMode())) {
                    moveGateway(gw, PaymentStatus.AUTHORIZED);
                    yield paymentEvent("payment.authorized", gw, null);
                }
                gw.capture();
                yield paymentEvent("payment.captured", gw, null);
            }
            case FAILURE -> {
                moveGateway(gw, PaymentStatus.FAILED);
                yield paymentEvent("payment.failed", gw, "card_declined");
            }
            case REQUIRES_ACTION -> {
                moveGateway(gw, PaymentStatus.REQUIRES_ACTION);
                yield paymentEvent("payment.requires_action", gw, null);
            }
            case CANCEL -> {
                moveGateway(gw, PaymentStatus.CANCELLED);
                yield paymentEvent("payment.cancelled", gw, null);
            }
            case EXPIRE -> {
                moveGateway(gw, PaymentStatus.EXPIRED);
                yield paymentEvent("payment.expired", gw, null);
            }
            case REFUND -> refundEvent(gw, refundAmount);
        };
        send(body);
        if (duplicate) {
            send(body);
        }
    }

    private String refundEvent(FakeGatewayPayment gw, BigDecimal amount) {
        FakeGatewayPayment.FakeRefund refund = gw.firstPendingRefund();
        if (refund != null) {
            // the refund Inshore asked for (async mode) finally goes through
            gw.completeRefund(refund);
        } else {
            // a refund nobody in Inshore asked for - e.g. made from the provider's own dashboard
            long minor;
            try {
                minor = amount != null
                        ? MinorUnits.toMinor(amount, gw.currency())
                        : gw.amountMinor() - gw.refundedMinor();
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("amount has more decimals than " + gw.currency() + " allows");
            }
            refund = gw.externalRefund(minor);
            if (refund == null) {
                throw new IllegalStateException("nothing (more) can be refunded at the fake gateway for this payment");
            }
        }
        return event("refund.succeeded", gw, refund.id(), refund.amountMinor(), null);
    }

    // The gateway keeps a captured payment captured; everything else just follows the event.
    private static void moveGateway(FakeGatewayPayment gw, PaymentStatus status) {
        if (gw.status() != PaymentStatus.CAPTURED) {
            gw.setStatus(status);
        }
    }

    private String paymentEvent(String type, FakeGatewayPayment gw, String failureCode) {
        return event(type, gw, null, gw.amountMinor(), failureCode);
    }

    private String event(String type, FakeGatewayPayment gw, String refundId, long amountMinor, String failureCode) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", "evt_" + UUID.randomUUID().toString().replace("-", ""));
        fields.put("type", type);
        fields.put("created", String.valueOf(Instant.now().getEpochSecond()));
        fields.put("payment_id", gw.providerPaymentId());
        fields.put("reference", gw.reference());
        fields.put("refund_id", refundId);
        fields.put("amount_minor", String.valueOf(amountMinor));
        fields.put("currency", gw.currency());
        fields.put("failure_code", failureCode);
        return FakeJson.write(fields);
    }

    private void send(String body) {
        send(body, properties.getFake().getWebhookSecret());
    }

    private void send(String body, String secret) {
        String signature = signer.sign(secret, Instant.now().getEpochSecond(), body);
        webhookService.handle(PaymentProviderCode.FAKE, body, Map.of(FakeWebhookSigner.HEADER, signature));
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private FakeGatewayPayment gatewayFor(UUID paymentId) {
        paymentService.getPayment(paymentId); // tenant + branch check: 404 for anything the caller may not see
        Payment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("payment not found"));
        if (p.getProviderPaymentId() == null) {
            throw new IllegalStateException("the provider has not accepted this payment yet - repeat the create request first");
        }
        return provider.rehydrate(p.getProviderPaymentId(), p.getId().toString(), p.getAmount(),
                p.getCurrency(), p.getStatus());
    }

    private void scheduleAutoOutcome(FakeGatewayPayment gw) {
        String mode = properties.getFake().getAutoOutcome();
        FakeOutcome outcome = switch (mode == null ? "none" : mode.toLowerCase(Locale.ROOT)) {
            case "success" -> FakeOutcome.SUCCESS;
            case "failure" -> FakeOutcome.FAILURE;
            case "requires-action" -> FakeOutcome.REQUIRES_ACTION;
            default -> null;
        };
        if (outcome == null) {
            return;
        }
        long delay = Math.max(0, properties.getFake().getAutoDelayMs());
        scheduler.schedule(() -> {
            try {
                deliver(gw, outcome, null, false);
            } catch (RuntimeException ex) {
                log.warn("fake gateway: auto outcome {} for {} failed: {}", outcome, gw.providerPaymentId(), ex.getMessage());
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}

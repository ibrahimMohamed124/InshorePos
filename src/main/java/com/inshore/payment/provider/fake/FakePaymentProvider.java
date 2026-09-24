package com.inshore.payment.provider.fake;

import com.inshore.payment.config.PaymentProperties;
import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.domain.PaymentTransactionStatus;
import com.inshore.payment.exception.InvalidWebhookException;
import com.inshore.payment.provider.CancelRequest;
import com.inshore.payment.provider.CancelResult;
import com.inshore.payment.provider.CaptureRequest;
import com.inshore.payment.provider.CaptureResult;
import com.inshore.payment.provider.MinorUnits;
import com.inshore.payment.provider.ParsedWebhookEvent;
import com.inshore.payment.provider.PaymentInitiationResult;
import com.inshore.payment.provider.PaymentProvider;
import com.inshore.payment.provider.PaymentRequest;
import com.inshore.payment.provider.PaymentStatusResult;
import com.inshore.payment.provider.ProviderRefundRequest;
import com.inshore.payment.provider.ProviderRefundResult;
import com.inshore.payment.provider.ProviderUnavailableException;
import com.inshore.payment.provider.WebhookEventKind;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A stand-in for a real payment gateway that behaves like one from Inshore's side of the fence:
 * <ul>
 *   <li>{@code createPayment} answers PENDING (or REQUIRES_ACTION) and returns a checkout link - the
 *   result comes later, as a signed webhook, exactly like Stripe/Adyen;</li>
 *   <li>amounts cross the boundary as integer minor units and are converted back here;</li>
 *   <li>calls are idempotent on the key they are given;</li>
 *   <li>webhooks are HMAC-signed and verified with the same scheme a real provider uses.</li>
 * </ul>
 * It never touches a Payment or an Order: the only way its outcomes reach Inshore is through the
 * webhook pipeline ({@link FakeProviderSimulator}). Only wired up for the dev / local / test profiles
 * AND {@code payment.provider=fake}.
 */
@Component
@Profile({"dev", "local", "test"})
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "fake")
@RequiredArgsConstructor
public class FakePaymentProvider implements PaymentProvider {

    private final PaymentProperties properties;
    private final FakeWebhookSigner signer;

    private final Map<String, FakeGatewayPayment> byProviderId = new ConcurrentHashMap<>();
    private final Map<String, FakeGatewayPayment> byReference = new ConcurrentHashMap<>();

    // set by FakeProviderSimulator; called once for every payment this gateway newly accepts
    private volatile Consumer<FakeGatewayPayment> newPaymentHook = p -> { };

    @Override
    public PaymentProviderCode code() {
        return PaymentProviderCode.FAKE;
    }

    void setNewPaymentHook(Consumer<FakeGatewayPayment> hook) {
        this.newPaymentHook = hook;
    }

    // ---- PaymentProvider ------------------------------------------------------------------------

    @Override
    public PaymentInitiationResult createPayment(PaymentRequest request) {
        boolean[] created = {false};
        // idempotent on our payment id: a retried request gets the SAME gateway payment back
        FakeGatewayPayment gw = byReference.computeIfAbsent(request.paymentId().toString(), ref -> {
            created[0] = true;
            long minor = MinorUnits.toMinor(request.amount(), request.currency());
            FakeGatewayPayment p = new FakeGatewayPayment("fake_pay_" + FakeGatewayPayment.shortId(),
                    ref, minor, request.currency());
            if ("requires-action".equalsIgnoreCase(properties.getFake().getAutoOutcome())) {
                p.setStatus(PaymentStatus.REQUIRES_ACTION);
            }
            byProviderId.put(p.providerPaymentId(), p);
            return p;
        });
        if (created[0]) {
            newPaymentHook.accept(gw);
        }
        String checkoutUrl = properties.getFake().getCheckoutBaseUrl() + "/" + gw.providerPaymentId();
        return new PaymentInitiationResult(gw.providerPaymentId(), gw.status(), checkoutUrl, null, null, null);
    }

    @Override
    public PaymentStatusResult getPayment(String providerPaymentId) {
        FakeGatewayPayment gw = require(providerPaymentId);
        return new PaymentStatusResult(gw.providerPaymentId(), gw.status(), null);
    }

    @Override
    public CaptureResult capture(CaptureRequest request) {
        FakeGatewayPayment gw = require(request.providerPaymentId());
        synchronized (gw) {
            if (gw.status() == PaymentStatus.CAPTURED) {
                return new CaptureResult(PaymentTransactionStatus.SUCCEEDED, gw.captureId(), null);
            }
            if (gw.status() != PaymentStatus.AUTHORIZED) {
                return new CaptureResult(PaymentTransactionStatus.FAILED, null, "NOT_AUTHORIZED");
            }
            if (MinorUnits.toMinor(request.amount(), request.currency()) != gw.amountMinor()) {
                return new CaptureResult(PaymentTransactionStatus.FAILED, null, "AMOUNT_MISMATCH");
            }
            return new CaptureResult(PaymentTransactionStatus.SUCCEEDED, gw.capture(), null);
        }
    }

    @Override
    public CancelResult cancel(CancelRequest request) {
        FakeGatewayPayment gw = require(request.providerPaymentId());
        synchronized (gw) {
            PaymentStatus s = gw.status();
            if (s == PaymentStatus.PENDING || s == PaymentStatus.REQUIRES_ACTION || s == PaymentStatus.AUTHORIZED) {
                gw.setStatus(PaymentStatus.CANCELLED);
                return new CancelResult(PaymentStatus.CANCELLED, null);
            }
            return new CancelResult(s, s == PaymentStatus.CANCELLED ? null : "NOT_CANCELLABLE");
        }
    }

    @Override
    public ProviderRefundResult refund(ProviderRefundRequest request) {
        FakeGatewayPayment gw = require(request.providerPaymentId());
        synchronized (gw) {
            FakeGatewayPayment.FakeRefund existing = gw.refundForKey(request.idempotencyKey());
            if (existing != null) {
                return new ProviderRefundResult(existing.status(), existing.id(), null);
            }
            if (gw.status() != PaymentStatus.CAPTURED) {
                return new ProviderRefundResult(PaymentTransactionStatus.FAILED, null, "NOT_CAPTURED");
            }
            boolean immediate = !"async".equalsIgnoreCase(properties.getFake().getRefundMode());
            long minor = MinorUnits.toMinor(request.amount(), request.currency());
            FakeGatewayPayment.FakeRefund refund = gw.addRefund(request.idempotencyKey(), minor, immediate);
            if (refund == null) {
                return new ProviderRefundResult(PaymentTransactionStatus.FAILED, null, "AMOUNT_EXCEEDS_CAPTURED");
            }
            return new ProviderRefundResult(refund.status(), refund.id(), null);
        }
    }

    @Override
    public ParsedWebhookEvent verifyAndParseWebhook(String rawBody, Map<String, String> headers) {
        // 1. authenticity first - nothing below runs for an unsigned/forged body
        signer.verify(properties.getFake().getWebhookSecret(), headers.get(FakeWebhookSigner.HEADER), rawBody,
                properties.getWebhook().getToleranceSeconds(), Instant.now());

        // 2. translate to Inshore terms
        Map<String, String> f;
        try {
            f = FakeJson.parse(rawBody);
        } catch (IllegalArgumentException ex) {
            throw new InvalidWebhookException("malformed webhook body");
        }
        String eventId = f.get("id");
        String type = f.get("type");
        if (eventId == null || eventId.isBlank() || type == null) {
            throw new InvalidWebhookException("webhook is missing id or type");
        }

        String currency = f.get("currency");
        BigDecimal amount = null;
        Instant occurredAt;
        try {
            if (f.get("amount_minor") != null && currency != null) {
                amount = MinorUnits.fromMinor(Long.parseLong(f.get("amount_minor")), currency);
            }
            occurredAt = f.get("created") != null ? Instant.ofEpochSecond(Long.parseLong(f.get("created"))) : Instant.now();
        } catch (RuntimeException ex) {
            throw new InvalidWebhookException("malformed amount or timestamp");
        }

        return new ParsedWebhookEvent(eventId, type, kindOf(type), f.get("payment_id"), f.get("reference"),
                f.get("refund_id"), amount, currency, occurredAt, f.get("failure_code"));
    }

    // ---- helpers used by the simulator ----------------------------------------------------------

    /** Finds the gateway's record, or rebuilds it after an application restart from what Inshore stored. */
    FakeGatewayPayment rehydrate(String providerPaymentId, String reference, BigDecimal amount,
                                 String currency, PaymentStatus status) {
        return byProviderId.computeIfAbsent(providerPaymentId, id -> {
            FakeGatewayPayment p = new FakeGatewayPayment(id, reference,
                    MinorUnits.toMinor(amount, currency), currency);
            switch (status) {
                case CAPTURED, PARTIALLY_REFUNDED, REFUNDED -> p.capture();
                case CREATED -> p.setStatus(PaymentStatus.PENDING);
                default -> p.setStatus(status);
            }
            byReference.put(reference, p);
            return p;
        });
    }

    private FakeGatewayPayment require(String providerPaymentId) {
        FakeGatewayPayment gw = providerPaymentId == null ? null : byProviderId.get(providerPaymentId);
        if (gw == null) {
            // e.g. the app was restarted: the in-memory gateway forgot it. "Unknown" is not "failed".
            throw new ProviderUnavailableException("the fake gateway has no record of " + providerPaymentId
                    + " (in-memory state is lost on restart)");
        }
        return gw;
    }

    private static WebhookEventKind kindOf(String type) {
        return switch (type) {
            case "payment.pending" -> WebhookEventKind.PAYMENT_PENDING;
            case "payment.requires_action" -> WebhookEventKind.REQUIRES_ACTION;
            case "payment.authorized" -> WebhookEventKind.AUTHORIZED;
            case "payment.captured" -> WebhookEventKind.CAPTURED;
            case "payment.failed" -> WebhookEventKind.FAILED;
            case "payment.cancelled" -> WebhookEventKind.CANCELLED;
            case "payment.expired" -> WebhookEventKind.EXPIRED;
            case "refund.succeeded" -> WebhookEventKind.REFUND_SUCCEEDED;
            case "refund.failed" -> WebhookEventKind.REFUND_FAILED;
            // an event type we do not act on: accepted and stored, but it changes nothing
            default -> null;
        };
    }
}

package com.inshore.payment.controller;

import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.provider.fake.FakeOutcome;
import com.inshore.payment.provider.fake.FakeProviderSimulator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * DEVELOPMENT ONLY. This controller is not just hidden in production - it does not exist there: the bean is
 * only created for the dev / local / test profiles AND payment.provider=fake (and the application refuses to
 * start with provider=fake in any other profile, see PaymentStartupValidator).
 * <p>
 * Each call makes the fake gateway "call back" Inshore with a signed webhook, so the payment moves through
 * the real webhook pipeline. The caller still needs to be logged in and able to see the payment.
 */
@RestController
@RequestMapping("/api/dev/payments")
@Profile({"dev", "local", "test"})
@ConditionalOnProperty(prefix = "payment", name = "provider", havingValue = "fake")
@RequiredArgsConstructor
public class DevPaymentSimulationController {

    private final FakeProviderSimulator simulator;

    /** Payment succeeded: CAPTURED (or AUTHORIZED when payment.fake.capture-mode=manual). */
    @PostMapping("/{paymentId}/simulate-success")
    public ResponseEntity<PaymentResponse> success(@PathVariable UUID paymentId,
                                                   @RequestParam(defaultValue = "false") boolean duplicate) {
        return ResponseEntity.ok(simulator.simulate(paymentId, FakeOutcome.SUCCESS, null, duplicate));
    }

    @PostMapping("/{paymentId}/simulate-failure")
    public ResponseEntity<PaymentResponse> failure(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(simulator.simulate(paymentId, FakeOutcome.FAILURE, null, false));
    }

    /** The customer must complete 3-D Secure / the hosted checkout. */
    @PostMapping("/{paymentId}/simulate-3ds")
    public ResponseEntity<PaymentResponse> requiresAction(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(simulator.simulate(paymentId, FakeOutcome.REQUIRES_ACTION, null, false));
    }

    @PostMapping("/{paymentId}/simulate-cancel")
    public ResponseEntity<PaymentResponse> cancel(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(simulator.simulate(paymentId, FakeOutcome.CANCEL, null, false));
    }

    @PostMapping("/{paymentId}/simulate-expire")
    public ResponseEntity<PaymentResponse> expire(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(simulator.simulate(paymentId, FakeOutcome.EXPIRE, null, false));
    }

    /**
     * Completes the refund Inshore asked for (payment.fake.refund-mode=async), or - when none is pending -
     * simulates a refund made at the provider's own dashboard. amount omitted = everything refundable.
     */
    @PostMapping("/{paymentId}/simulate-refund")
    public ResponseEntity<PaymentResponse> refund(@PathVariable UUID paymentId,
                                                  @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(simulator.simulate(paymentId, FakeOutcome.REFUND, amount, false));
    }

    /** A webhook with a forged signature: must be rejected and must not change the payment. */
    @PostMapping("/{paymentId}/simulate-invalid-webhook")
    public ResponseEntity<Map<String, String>> invalidWebhook(@PathVariable UUID paymentId) {
        return ResponseEntity.ok(Map.of("result", simulator.simulateInvalidSignature(paymentId)));
    }
}

package com.inshore.payment.controller;

import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.dto.CreatePaymentRequest;
import com.inshore.payment.dto.PaymentOutcome;
import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.dto.RefundOutcome;
import com.inshore.payment.dto.RefundPaymentRequest;
import com.inshore.payment.dto.RefundResponse;
import com.inshore.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Authorization is enforced in the service layer (branch access for create/view, branch management for
 * capture/cancel/refund, platform admin read-only) - the same approach as the other controllers.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final PaymentService paymentService;

    /**
     * 201 = new payment. 200 + Idempotent-Replayed: true = the same Idempotency-Key was used before.
     * 202 = the provider has not answered yet (payment is CREATED): repeat the same request to continue it.
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {

        PaymentOutcome outcome = paymentService.createPayment(request, idempotencyKey);
        if (outcome.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(outcome.payment());
        }
        HttpStatus status = outcome.payment().getStatus() == PaymentStatus.CREATED
                ? HttpStatus.ACCEPTED : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(outcome.payment());
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.getPayment(id));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getPaymentsByOrder(@RequestParam Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentsByOrder(orderId));
    }

    /** Recovery: ask the provider for the payment's status (e.g. a webhook never arrived). */
    @PostMapping("/{id}/sync")
    public ResponseEntity<PaymentResponse> syncPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.syncPayment(id));
    }

    @PostMapping("/{id}/capture")
    public ResponseEntity<PaymentResponse> capturePayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.capturePayment(id));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentResponse> cancelPayment(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentService.cancelPayment(id));
    }

    /** 201 = refund accepted, 200 + Idempotent-Replayed = repeat of an earlier request. */
    @PostMapping("/{id}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundPaymentRequest request,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey) {

        RefundOutcome outcome = paymentService.refund(id, request, idempotencyKey);
        if (outcome.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(outcome.refund());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(outcome.refund());
    }
}

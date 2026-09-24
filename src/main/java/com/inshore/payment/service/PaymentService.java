package com.inshore.payment.service;

import com.inshore.payment.dto.CreatePaymentRequest;
import com.inshore.payment.dto.PaymentOutcome;
import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.dto.RefundOutcome;
import com.inshore.payment.dto.RefundPaymentRequest;
import com.inshore.user.domain.User;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    /** Idempotent on (tenant, idempotencyKey): repeating the request returns the same payment. */
    PaymentOutcome createPayment(CreatePaymentRequest request, String idempotencyKey);

    PaymentResponse getPayment(UUID id);

    List<PaymentResponse> getPaymentsByOrder(Long orderId);

    /** Asks the provider for the payment's current status and applies it (recovery for a lost webhook). */
    PaymentResponse syncPayment(UUID id);

    PaymentResponse capturePayment(UUID id);

    PaymentResponse cancelPayment(UUID id);

    /** The public refund API: branch management only. Idempotent on the key. */
    RefundOutcome refund(UUID paymentId, RefundPaymentRequest request, String idempotencyKey);

    /**
     * Refund through the payment of an order, for the refund module (approved refund requests). The caller
     * has already checked that the approver may do this.
     *
     * @param amount null = everything still refundable
     */
    RefundOutcome refundOrderPayment(Long orderId, BigDecimal amount, String reason, String idempotencyKey,
                                     User requestedBy, User approvedBy);

    /** A payment exists that still holds the order (not failed / cancelled / expired). */
    boolean hasActivePayment(Long orderId);

    boolean hasAnyPayment(Long orderId);

    /** Money was collected through the payment system for this order (captured, or refunded since). */
    boolean hasOnlinePayment(Long orderId);

    /** The accounting refund was paid back through a provider and must stay on the record. */
    boolean isProviderRefund(Long refundId);
}

package com.inshore.payment.service.impl;

import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.domain.PaymentTransactionStatus;
import com.inshore.payment.dto.CreatePaymentRequest;
import com.inshore.payment.dto.PaymentOutcome;
import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.dto.RefundOutcome;
import com.inshore.payment.dto.RefundPaymentRequest;
import com.inshore.payment.exception.IdempotencyConflictException;
import com.inshore.payment.exception.PaymentUnavailableException;
import com.inshore.payment.provider.CancelResult;
import com.inshore.payment.provider.CaptureResult;
import com.inshore.payment.provider.PaymentInitiationResult;
import com.inshore.payment.provider.PaymentProvider;
import com.inshore.payment.provider.PaymentStatusResult;
import com.inshore.payment.provider.ProviderRefundResult;
import com.inshore.payment.provider.ProviderUnavailableException;
import com.inshore.payment.repository.PaymentRepository;
import com.inshore.payment.repository.PaymentTransactionRepository;
import com.inshore.payment.service.PaymentService;
import com.inshore.shared.security.AccessPolicy;
import com.inshore.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Orchestrates the payment workflows. This class is deliberately NOT transactional: each database step is a
 * short transaction inside {@link PaymentTxService}, and the calls to the payment provider happen between
 * them, outside any transaction. That is what makes every failure recoverable:
 * <ul>
 *   <li>provider timeout - the payment stays CREATED / the refund stays PENDING; repeating the request with
 *   the same Idempotency-Key continues it (the provider is idempotent on our ids, so no double charge);</li>
 *   <li>DB failure after the provider answered - the provider's webhook (or a retry / sync) brings the
 *   payment to the right state;</li>
 *   <li>crash at any point - every committed step is a valid state of the state machine.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,128}$");

    private final PaymentTxService tx;
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final AccessPolicy accessPolicy;
    private final ObjectProvider<PaymentProvider> providers;

    // ---- create ----------------------------------------------------------------------------------

    @Override
    public PaymentOutcome createPayment(CreatePaymentRequest request, String idempotencyKey) {
        PaymentProvider provider = provider();
        String key = requireKey(idempotencyKey);
        User user = accessPolicy.currentUser();

        PaymentTxService.PreparedCreate prepared;
        try {
            prepared = tx.prepareCreate(user, request, key, provider.code());
        } catch (DataIntegrityViolationException ex) {
            // same key from another request that committed a moment ago
            throw new IdempotencyConflictException(
                    "a request with this Idempotency-Key is already being processed - retry in a moment");
        }

        if (prepared.needsProvider()) {
            try {
                PaymentInitiationResult result = provider.createPayment(prepared.providerRequest());
                tx.applyInitiation(prepared.paymentId(), result);
            } catch (ProviderUnavailableException ex) {
                // outcome unknown: the payment stays CREATED. Repeating the request (same key) continues it;
                // the provider dedupes on the payment id, so it cannot be charged twice.
                log.warn("payment {}: provider did not answer ({}) - left as CREATED", prepared.paymentId(), ex.getMessage());
            }
        }
        return new PaymentOutcome(tx.viewInternal(prepared.paymentId()), prepared.replayed());
    }

    // ---- reads -----------------------------------------------------------------------------------

    @Override
    public PaymentResponse getPayment(UUID id) {
        return tx.view(id, accessPolicy.currentUser());
    }

    @Override
    public List<PaymentResponse> getPaymentsByOrder(Long orderId) {
        return tx.viewByOrder(orderId, accessPolicy.currentUser());
    }

    @Override
    public PaymentResponse syncPayment(UUID id) {
        PaymentProvider provider = provider();
        PaymentTxService.PreparedSync prepared = tx.prepareSync(id, accessPolicy.currentUser());
        PaymentStatusResult result;
        try {
            result = provider.getPayment(prepared.providerPaymentId());
        } catch (ProviderUnavailableException ex) {
            throw new PaymentUnavailableException("the payment provider could not be reached - try again shortly");
        }
        tx.applyObserved(prepared.paymentId(), result);
        return tx.viewInternal(prepared.paymentId());
    }

    // ---- capture / cancel ------------------------------------------------------------------------

    @Override
    public PaymentResponse capturePayment(UUID id) {
        PaymentProvider provider = provider();
        User user = accessPolicy.currentUser();
        PaymentTxService.PreparedCapture prepared = tx.prepareCapture(id, user);

        CaptureResult result;
        try {
            result = provider.capture(prepared.providerRequest());
        } catch (ProviderUnavailableException ex) {
            // the CAPTURE row stays PENDING; a retry reuses it and the provider dedupes on its key
            throw new PaymentUnavailableException("the payment provider could not be reached - try again shortly");
        }
        PaymentTransactionStatus status = tx.completeCapture(id, prepared.transactionId(), result, user);
        if (status == PaymentTransactionStatus.FAILED) {
            throw new IllegalStateException("the payment provider refused the capture (" + result.failureCode() + ")");
        }
        return tx.viewInternal(id);
    }

    @Override
    public PaymentResponse cancelPayment(UUID id) {
        User user = accessPolicy.currentUser();
        PaymentTxService.PreparedCancel prepared = tx.prepareCancel(id, user);

        if (prepared.needsProvider()) {
            CancelResult result;
            try {
                result = provider().cancel(prepared.providerRequest());
            } catch (ProviderUnavailableException ex) {
                throw new PaymentUnavailableException("the payment provider could not be reached - try again shortly");
            }
            tx.completeCancel(id, result, user);
            PaymentResponse after = tx.viewInternal(id);
            if (after.getStatus() != PaymentStatus.CANCELLED) {
                throw new IllegalStateException("the payment could not be cancelled - it is now " + after.getStatus());
            }
            return after;
        }
        return tx.viewInternal(id);
    }

    // ---- refunds ---------------------------------------------------------------------------------

    @Override
    public RefundOutcome refund(UUID paymentId, RefundPaymentRequest request, String idempotencyKey) {
        User user = accessPolicy.currentUser();
        return doRefund(paymentId, user, null, request.getAmount(), request.getReason(), idempotencyKey, true);
    }

    @Override
    public RefundOutcome refundOrderPayment(Long orderId, BigDecimal amount, String reason, String idempotencyKey,
                                            User requestedBy, User approvedBy) {
        UUID paymentId = tx.findRefundablePaymentId(orderId);
        return doRefund(paymentId, requestedBy, approvedBy, amount, reason, idempotencyKey, false);
    }

    private RefundOutcome doRefund(UUID paymentId, User requestedBy, User approvedBy, BigDecimal amount,
                                   String reason, String idempotencyKey, boolean enforceAccess) {
        PaymentProvider provider = provider();
        String key = requireKey(idempotencyKey);

        PaymentTxService.PreparedRefund prepared =
                tx.prepareRefund(paymentId, requestedBy, approvedBy, amount, reason, key, enforceAccess);

        if (prepared.needsProvider()) {
            try {
                ProviderRefundResult result = provider.refund(prepared.providerRequest());
                tx.completeRefund(paymentId, prepared.transactionId(), result);
            } catch (ProviderUnavailableException ex) {
                // the refund row stays PENDING and holds its amount; repeating the request continues it
                log.warn("refund {} on payment {}: provider did not answer ({}) - left PENDING",
                        prepared.transactionId(), paymentId, ex.getMessage());
            }
        }

        RefundOutcome outcome = tx.refundOutcome(paymentId, prepared.transactionId(), prepared.replayed());
        if (outcome.refund().getStatus() == PaymentTransactionStatus.FAILED) {
            throw new IllegalStateException("the payment provider rejected the refund");
        }
        return outcome;
    }

    // ---- questions other modules ask ---------------------------------------------------------------

    @Override
    public boolean hasActivePayment(Long orderId) {
        return orderId != null && paymentRepository.countByOrderIdAndStatusIn(orderId, PaymentStatus.BLOCKING) > 0;
    }

    @Override
    public boolean hasAnyPayment(Long orderId) {
        return orderId != null && paymentRepository.countByOrderId(orderId) > 0;
    }

    @Override
    public boolean hasOnlinePayment(Long orderId) {
        return orderId != null && paymentRepository.countByOrderIdAndStatusIn(orderId, PaymentStatus.COLLECTED) > 0;
    }

    @Override
    public boolean isProviderRefund(Long refundId) {
        return refundId != null && transactionRepository.existsByRefundId(refundId);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    private PaymentProvider provider() {
        PaymentProvider provider = providers.getIfAvailable();
        if (provider == null) {
            throw new PaymentUnavailableException("the payment system is not configured");
        }
        return provider;
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("the Idempotency-Key header is required");
        }
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be 8-128 characters: letters, digits, '-' or '_'");
        }
        return key;
    }
}

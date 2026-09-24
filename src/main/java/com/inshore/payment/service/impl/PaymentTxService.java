package com.inshore.payment.service.impl;

import com.inshore.order.domain.Order;
import com.inshore.order.domain.OrderStatus;
import com.inshore.order.domain.PaymentType;
import com.inshore.order.exception.OrderNotFoundException;
import com.inshore.order.repository.OrderRepository;
import com.inshore.payment.domain.Payment;
import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.domain.PaymentTransaction;
import com.inshore.payment.domain.PaymentTransactionStatus;
import com.inshore.payment.domain.PaymentTransactionType;
import com.inshore.payment.domain.PaymentWebhookEvent;
import com.inshore.payment.domain.TransactionSource;
import com.inshore.payment.domain.WebhookResult;
import com.inshore.payment.dto.CreatePaymentRequest;
import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.dto.RefundOutcome;
import com.inshore.payment.event.PaymentCapturedEvent;
import com.inshore.payment.event.PaymentRefundSucceededEvent;
import com.inshore.payment.exception.IdempotencyConflictException;
import com.inshore.payment.exception.InvalidPaymentStateException;
import com.inshore.payment.exception.PaymentNotFoundException;
import com.inshore.payment.exception.RefundAmountExceededException;
import com.inshore.payment.mapper.PaymentMapper;
import com.inshore.payment.provider.CancelRequest;
import com.inshore.payment.provider.CancelResult;
import com.inshore.payment.provider.CaptureRequest;
import com.inshore.payment.provider.CaptureResult;
import com.inshore.payment.provider.ParsedWebhookEvent;
import com.inshore.payment.provider.PaymentInitiationResult;
import com.inshore.payment.provider.PaymentRequest;
import com.inshore.payment.provider.PaymentStatusResult;
import com.inshore.payment.provider.ProviderRefundRequest;
import com.inshore.payment.provider.ProviderRefundResult;
import com.inshore.payment.repository.PaymentRepository;
import com.inshore.payment.repository.PaymentTransactionRepository;
import com.inshore.payment.repository.PaymentWebhookEventRepository;
import com.inshore.shared.config.PricingProperties;
import com.inshore.shared.security.AccessPolicy;
import com.inshore.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The SHORT database transactions of the payment system. {@link PaymentServiceImpl} orchestrates them and
 * makes the calls to the payment provider BETWEEN them - a provider call is never made inside one of these
 * transactions (a database transaction cannot be extended over an HTTP call, so each step is committed and
 * recoverable on its own).
 * <p>
 * It is a separate bean because Spring's {@code @Transactional} only works through the proxy. Every method
 * runs in its own transaction (REQUIRES_NEW) - also when the caller already has one open (the refund
 * approval flow) - so the payment state that was committed stays true even if the caller's own transaction
 * rolls back later. The read-only methods simply join whatever transaction is there.
 * <p>
 * Rules every write follows: lock the payment row first (PESSIMISTIC_WRITE), change it only through the
 * state machine on {@link Payment}, and publish Spring events for the order / refund modules inside the
 * same transaction so a failure there rolls the whole step back.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentTxService {

    private static final int MAX_STORED_PAYLOAD = 20_000;

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final PaymentWebhookEventRepository eventRepository;
    private final OrderRepository orderRepository;
    private final AccessPolicy accessPolicy;
    private final PricingProperties pricing;
    private final ApplicationEventPublisher events;

    // ---- what the orchestrator needs to carry between steps ----------------------------------

    public record PreparedCreate(UUID paymentId, boolean replayed, boolean needsProvider, PaymentRequest providerRequest) {
    }

    public record PreparedCancel(UUID paymentId, boolean needsProvider, CancelRequest providerRequest) {
    }

    public record PreparedCapture(UUID paymentId, UUID transactionId, CaptureRequest providerRequest) {
    }

    public record PreparedRefund(UUID paymentId, UUID transactionId, boolean replayed, boolean needsProvider,
                                 ProviderRefundRequest providerRequest) {
    }

    public record PreparedSync(UUID paymentId, String providerPaymentId) {
    }

    private record EventOutcome(WebhookResult result, String reason) {
        static EventOutcome of(WebhookResult result) {
            return new EventOutcome(result, null);
        }

        static EventOutcome invalid(String reason) {
            return new EventOutcome(WebhookResult.INVALID_TRANSITION, reason);
        }
    }

    // =========================================================================================
    // create
    // =========================================================================================

    /** TX 1 of createPayment: validates, then stores the payment as CREATED (or finds the earlier one). */
    public PreparedCreate prepareCreate(User user, CreatePaymentRequest request, String idempotencyKey,
                                        PaymentProviderCode provider) {
        if (accessPolicy.isPlatformAdmin(user)) {
            throw new AccessDeniedException("platform admins have read-only access to payments");
        }

        // The order row lock serialises every payment attempt on the same order, so the "one active
        // payment per order" rule and the idempotency lookup below cannot race.
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException("order not found"));
        // another tenant's order looks exactly like a missing one
        if (!accessPolicy.canAccessBranch(user, order.getBranch())) {
            throw new OrderNotFoundException("order not found");
        }
        if (order.getBranch() == null || order.getBranch().getStore() == null) {
            throw new IllegalStateException("the order's branch has no store");
        }
        Long storeId = order.getBranch().getStore().getId();

        PaymentType method = request.getPaymentMethod() != null ? request.getPaymentMethod() : order.getPaymentType();
        if (method == null || method == PaymentType.CASH) {
            throw new IllegalArgumentException("cash orders are not paid through the payment system");
        }
        if (order.getPaymentType() != method) {
            throw new IllegalArgumentException(
                    "paymentMethod must match the order's payment type (" + order.getPaymentType() + ")");
        }
        String returnUrl = request.getReturnUrl();
        if (returnUrl != null && !returnUrl.isBlank() && !returnUrl.matches("(?i)^https?://\\S+$")) {
            throw new IllegalArgumentException("returnUrl must be an http(s) URL");
        }

        String fingerprint = fingerprint(order.getId(), method);
        Optional<Payment> existing = paymentRepository.findByStoreIdAndIdempotencyKey(storeId, idempotencyKey);
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (!p.getRequestFingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException(
                        "this Idempotency-Key was already used for a different payment request");
            }
            return preparedCreate(p, true, returnUrl);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException("only a pending order can be paid, this order is " + order.getStatus());
        }
        if (!paymentRepository.findByOrderIdAndStatusIn(order.getId(), PaymentStatus.BLOCKING).isEmpty()) {
            throw new IllegalStateException("this order already has an active payment");
        }

        BigDecimal amount = money(order.getTotalAmount());
        if (amount.signum() <= 0) {
            throw new IllegalStateException("the order total must be greater than zero to be paid");
        }

        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .store(order.getBranch().getStore())
                .branch(order.getBranch())
                .order(order)
                .provider(provider)
                .idempotencyKey(idempotencyKey)
                .requestFingerprint(fingerprint)
                .amount(amount)
                .currency(pricing.getCurrency())
                .paymentMethod(method)
                .status(PaymentStatus.CREATED)
                .createdBy(user.getId())
                .build());
        log.info("payment {} created for order {} store {} provider {} amount {} {}",
                payment.getId(), order.getId(), storeId, provider, amount, payment.getCurrency());
        return preparedCreate(payment, false, returnUrl);
    }

    /** TX 2 of createPayment: records what the provider answered. */
    public void applyInitiation(UUID paymentId, PaymentInitiationResult result) {
        Payment p = lock(paymentId);
        if (p.getProviderPaymentId() == null && result.providerPaymentId() != null) {
            p.setProviderPaymentId(result.providerPaymentId());
        }
        if (result.nextActionUrl() != null) {
            p.setNextActionUrl(result.nextActionUrl());
        }
        if (result.cardBrand() != null) {
            p.setCardBrand(result.cardBrand());
            p.setCardLast4(result.cardLast4());
        }
        // A webhook may already have moved the payment further than this answer: moveTo never goes back.
        moveTo(p, result.status(), result.failureCode(), null, TransactionSource.API, p.getCreatedBy());
        paymentRepository.save(p);
    }

    // =========================================================================================
    // reads
    // =========================================================================================

    @Transactional(readOnly = true)
    public PaymentResponse view(UUID id, User user) {
        return toResponse(loadReadable(id, user));
    }

    @Transactional(readOnly = true)
    public PaymentResponse viewInternal(UUID id) {
        return toResponse(paymentRepository.findById(id).orElseThrow(() -> new PaymentNotFoundException("payment not found")));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> viewByOrder(Long orderId, User user) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException("order not found"));
        if (!accessPolicy.canAccessBranch(user, order.getBranch())) {
            throw new OrderNotFoundException("order not found");
        }
        return paymentRepository.findByOrderId(orderId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public UUID findRefundablePaymentId(Long orderId) {
        return paymentRepository.findByOrderIdAndStatusIn(orderId, PaymentStatus.COLLECTED).stream()
                .filter(p -> p.getStatus().isRefundable())
                .map(Payment::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("this order has no refundable payment"));
    }

    @Transactional(readOnly = true)
    public RefundOutcome refundOutcome(UUID paymentId, UUID transactionId, boolean replayed) {
        Payment p = paymentRepository.findById(paymentId).orElseThrow(() -> new PaymentNotFoundException("payment not found"));
        PaymentTransaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException("refund not found"));
        return new RefundOutcome(PaymentMapper.toRefundResponse(p, t), replayed, t.getRefundId());
    }

    @Transactional(readOnly = true)
    public boolean eventExists(PaymentProviderCode provider, String providerEventId) {
        return eventRepository.findByProviderAndProviderEventId(provider, providerEventId).isPresent();
    }

    // =========================================================================================
    // sync (manual reconcile with the provider)
    // =========================================================================================

    @Transactional(readOnly = true)
    public PreparedSync prepareSync(UUID id, User user) {
        Payment p = loadReadable(id, user);
        if (p.getProviderPaymentId() == null) {
            throw new IllegalStateException("the provider has not accepted this payment yet");
        }
        return new PreparedSync(p.getId(), p.getProviderPaymentId());
    }

    public void applyObserved(UUID id, PaymentStatusResult result) {
        Payment p = lock(id);
        PaymentStatus observed = result.status();
        if (observed != PaymentStatus.PARTIALLY_REFUNDED && observed != PaymentStatus.REFUNDED
                && observed != PaymentStatus.CREATED) {
            moveTo(p, observed, result.failureCode(), null, TransactionSource.SYSTEM, null);
            paymentRepository.save(p);
        }
    }

    // =========================================================================================
    // cancel
    // =========================================================================================

    public PreparedCancel prepareCancel(UUID id, User user) {
        Payment p = loadWritableLocked(id, user);
        if (p.getStatus() == PaymentStatus.CANCELLED) {
            return new PreparedCancel(id, false, null);
        }
        if (!p.getStatus().canTransitionTo(PaymentStatus.CANCELLED)) {
            throw new InvalidPaymentStateException("a " + p.getStatus() + " payment cannot be cancelled"
                    + (p.getStatus().isRefundable() ? " - refund it instead" : ""));
        }
        if (p.getProviderPaymentId() == null) {
            // the provider never confirmed it - nothing to cancel over there
            moveTo(p, PaymentStatus.CANCELLED, null, null, TransactionSource.API, user.getId());
            paymentRepository.save(p);
            return new PreparedCancel(id, false, null);
        }
        return new PreparedCancel(id, true, new CancelRequest(p.getProviderPaymentId(), "cancel-" + p.getId()));
    }

    public void completeCancel(UUID id, CancelResult result, User user) {
        Payment p = lock(id);
        moveTo(p, result.status(), result.failureCode(), null, TransactionSource.API, user.getId());
        paymentRepository.save(p);
    }

    // =========================================================================================
    // capture
    // =========================================================================================

    public PreparedCapture prepareCapture(UUID id, User user) {
        Payment p = loadWritableLocked(id, user);
        if (p.getStatus() != PaymentStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException("only an AUTHORIZED payment can be captured, this one is " + p.getStatus());
        }
        PaymentTransaction t = transactionRepository
                .findByPaymentIdAndTypeAndStatus(id, PaymentTransactionType.CAPTURE, PaymentTransactionStatus.PENDING)
                .stream().findFirst()
                .orElseGet(() -> recordTransaction(p, PaymentTransactionType.CAPTURE, PaymentTransactionStatus.PENDING,
                        p.getAmount(), null, "capture-" + UUID.randomUUID(), null, TransactionSource.API, user.getId(), null));
        return new PreparedCapture(id, t.getId(),
                new CaptureRequest(p.getProviderPaymentId(), p.getAmount(), p.getCurrency(), "capture-" + t.getId()));
    }

    public PaymentTransactionStatus completeCapture(UUID paymentId, UUID transactionId, CaptureResult result, User user) {
        Payment p = lock(paymentId);
        PaymentTransaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException("capture not found"));
        switch (result.status()) {
            case SUCCEEDED -> {
                moveTo(p, PaymentStatus.CAPTURED, null, result.providerTransactionId(), TransactionSource.API, user.getId());
                paymentRepository.save(p);
            }
            case FAILED -> t.markFailed(result.failureCode());
            case PENDING -> t.setProviderTransactionId(result.providerTransactionId());
        }
        return result.status();
    }

    // =========================================================================================
    // refund
    // =========================================================================================

    /**
     * TX 1 of a refund: locks the payment, checks what is left to refund (captured - refunded - refunds still
     * in flight) and stores the refund as PENDING. The same idempotency key always maps to the same row.
     *
     * @param enforceAccess true for the public API (caller must manage the branch); false when the refund
     *                      module already checked the approver's authority
     */
    public PreparedRefund prepareRefund(UUID paymentId, User actor, User approver, BigDecimal amount, String reason,
                                        String idempotencyKey, boolean enforceAccess) {
        Payment p = enforceAccess
                ? loadWritableLocked(paymentId, actor)
                : paymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() -> new PaymentNotFoundException("payment not found"));

        BigDecimal requested = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);

        Optional<PaymentTransaction> existing = transactionRepository.findByPaymentIdAndIdempotencyKey(paymentId, idempotencyKey);
        if (existing.isPresent()) {
            PaymentTransaction t = existing.get();
            if (t.getType() != PaymentTransactionType.REFUND
                    || (requested != null && t.getAmount().compareTo(requested) != 0)) {
                throw new IdempotencyConflictException(
                        "this Idempotency-Key was already used for a different refund request");
            }
            // a refund that was stored but never reached the provider (crash / timeout) is sent again,
            // with the same provider key, so it cannot be refunded twice
            boolean needsProvider = t.isPending() && t.getProviderTransactionId() == null;
            return new PreparedRefund(paymentId, t.getId(), true, needsProvider, needsProvider ? refundRequest(p, t) : null);
        }

        if (!p.getStatus().isRefundable()) {
            throw new InvalidPaymentStateException("a " + p.getStatus() + " payment cannot be refunded");
        }
        BigDecimal available = p.refundableAmount().subtract(pendingRefundTotal(paymentId));
        BigDecimal value = requested != null ? requested : available;
        if (value.signum() <= 0) {
            throw new RefundAmountExceededException("nothing is left to refund on this payment");
        }
        if (value.compareTo(available) > 0) {
            throw new RefundAmountExceededException("refund amount exceeds the refundable balance of " + available);
        }

        String cleanReason = reason == null ? null : reason.trim();
        if (cleanReason != null && cleanReason.length() > 500) {
            cleanReason = cleanReason.substring(0, 500);
        }
        PaymentTransaction t = recordTransaction(p, PaymentTransactionType.REFUND, PaymentTransactionStatus.PENDING,
                value, null, idempotencyKey, cleanReason, TransactionSource.API,
                actor != null ? actor.getId() : null, approver != null ? approver.getId() : null);
        log.info("refund {} of {} {} requested on payment {} by {}", t.getId(), value, p.getCurrency(), p.getId(),
                actor != null ? actor.getId() : "system");
        return new PreparedRefund(paymentId, t.getId(), false, true, refundRequest(p, t));
    }

    /** TX 2 of a refund: records the provider's answer. A webhook may have completed it already. */
    public void completeRefund(UUID paymentId, UUID transactionId, ProviderRefundResult result) {
        Payment p = lock(paymentId);
        PaymentTransaction t = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new PaymentNotFoundException("refund not found"));
        if (!t.isPending()) {
            return;
        }
        switch (result.status()) {
            case SUCCEEDED -> completeRefundSuccess(p, t, result.providerTransactionId());
            case FAILED -> t.markFailed(result.failureCode());
            case PENDING -> t.setProviderTransactionId(result.providerTransactionId());
        }
        paymentRepository.save(p);
    }

    // =========================================================================================
    // webhooks
    // =========================================================================================

    /**
     * Stores and applies one verified webhook, all in this single transaction: if applying it fails (or the
     * process dies half way) everything rolls back and the provider's retry starts clean.
     */
    public WebhookResult handleWebhook(PaymentProviderCode provider, ParsedWebhookEvent e, String rawBody) {
        if (eventRepository.findByProviderAndProviderEventId(provider, e.providerEventId()).isPresent()) {
            log.info("webhook {} from {} ignored: duplicate", e.providerEventId(), provider);
            return WebhookResult.DUPLICATE;
        }

        PaymentWebhookEvent event = PaymentWebhookEvent.builder()
                .provider(provider)
                .providerEventId(e.providerEventId())
                .eventType(e.eventType())
                .providerPaymentId(e.providerPaymentId())
                .payload(truncate(rawBody, MAX_STORED_PAYLOAD))
                .signatureVerified(true)
                .processed(false)
                .build();
        // unique (provider, event id): two identical events racing each other - the loser fails HERE,
        // before anything has been applied
        eventRepository.saveAndFlush(event);

        Optional<Payment> found = findPaymentForEvent(provider, e);
        EventOutcome outcome;
        if (found.isEmpty()) {
            outcome = new EventOutcome(WebhookResult.UNKNOWN_PAYMENT, "no payment matches this event");
        } else {
            Payment payment = found.get();
            event.setPayment(payment);
            outcome = applyEvent(payment, e);
            paymentRepository.save(payment);
        }

        event.setResult(outcome.result());
        event.setFailureReason(truncate(outcome.reason(), 255));
        event.setProcessed(outcome.result() == WebhookResult.APPLIED || outcome.result() == WebhookResult.NO_OP);
        event.setProcessedAt(Instant.now());

        if (outcome.result() == WebhookResult.UNKNOWN_PAYMENT || outcome.result() == WebhookResult.INVALID_TRANSITION) {
            log.warn("webhook {} ({}) from {} NOT applied: {} - {}", e.providerEventId(), e.eventType(), provider,
                    outcome.result(), outcome.reason());
        } else {
            log.info("webhook {} ({}) from {}: {}", e.providerEventId(), e.eventType(), provider, outcome.result());
        }
        return outcome.result();
    }

    private Optional<Payment> findPaymentForEvent(PaymentProviderCode provider, ParsedWebhookEvent e) {
        Optional<Payment> found = Optional.empty();
        if (e.providerPaymentId() != null) {
            found = paymentRepository.findByProviderAndProviderPaymentIdForUpdate(provider, e.providerPaymentId());
        }
        // The event can beat our own "createPayment" answer, so the provider payment id is not stored yet:
        // fall back to the reference (our payment id) the provider echoes back.
        if (found.isEmpty() && e.paymentReference() != null) {
            try {
                found = paymentRepository.findByIdForUpdate(UUID.fromString(e.paymentReference()))
                        .filter(p -> p.getProvider() == provider)
                        .filter(p -> p.getProviderPaymentId() == null
                                || e.providerPaymentId() == null
                                || p.getProviderPaymentId().equals(e.providerPaymentId()));
            } catch (IllegalArgumentException ex) {
                found = Optional.empty();
            }
        }
        found.ifPresent(p -> {
            if (p.getProviderPaymentId() == null && e.providerPaymentId() != null) {
                p.setProviderPaymentId(e.providerPaymentId());
            }
        });
        return found;
    }

    private EventOutcome applyEvent(Payment p, ParsedWebhookEvent e) {
        if (e.kind() == null) {
            return new EventOutcome(WebhookResult.NO_OP, "event type not handled: " + e.eventType());
        }
        return switch (e.kind()) {
            case PAYMENT_PENDING -> statusEvent(p, PaymentStatus.PENDING, e);
            case REQUIRES_ACTION -> statusEvent(p, PaymentStatus.REQUIRES_ACTION, e);
            case AUTHORIZED -> collectedEvent(p, PaymentStatus.AUTHORIZED, e);
            case CAPTURED -> collectedEvent(p, PaymentStatus.CAPTURED, e);
            case FAILED -> statusEvent(p, PaymentStatus.FAILED, e);
            case CANCELLED -> statusEvent(p, PaymentStatus.CANCELLED, e);
            case EXPIRED -> statusEvent(p, PaymentStatus.EXPIRED, e);
            case REFUND_SUCCEEDED -> refundSucceededEvent(p, e);
            case REFUND_FAILED -> refundFailedEvent(p, e);
        };
    }

    private EventOutcome statusEvent(Payment p, PaymentStatus target, ParsedWebhookEvent e) {
        if (p.getStatus() == target) {
            return EventOutcome.of(WebhookResult.NO_OP);
        }
        if (moveTo(p, target, e.failureCode(), e.providerTransactionId(), TransactionSource.WEBHOOK, null)) {
            return EventOutcome.of(WebhookResult.APPLIED);
        }
        // e.g. CAPTURED arriving for a payment we already expired: money moved that we no longer expect.
        return EventOutcome.invalid("cannot move a " + p.getStatus() + " payment to " + target);
    }

    // AUTHORIZED / CAPTURED carry money: the amount must be exactly what we asked for
    private EventOutcome collectedEvent(Payment p, PaymentStatus target, ParsedWebhookEvent e) {
        if (target == PaymentStatus.CAPTURED && PaymentStatus.COLLECTED.contains(p.getStatus())) {
            return EventOutcome.of(WebhookResult.NO_OP);
        }
        if (e.amount() != null && e.amount().compareTo(p.getAmount()) != 0) {
            return EventOutcome.invalid("amount mismatch: event " + e.amount() + " vs payment " + p.getAmount());
        }
        if (e.currency() != null && !e.currency().equalsIgnoreCase(p.getCurrency())) {
            return EventOutcome.invalid("currency mismatch: event " + e.currency() + " vs payment " + p.getCurrency());
        }
        return statusEvent(p, target, e);
    }

    private EventOutcome refundSucceededEvent(Payment p, ParsedWebhookEvent e) {
        String providerTxId = e.providerTransactionId();
        if (providerTxId == null) {
            return EventOutcome.invalid("refund event carries no refund id");
        }
        Optional<PaymentTransaction> known = transactionRepository.findByPaymentIdAndProviderTransactionId(p.getId(), providerTxId);
        if (known.isPresent()) {
            PaymentTransaction t = known.get();
            if (t.getType() != PaymentTransactionType.REFUND) {
                return EventOutcome.invalid("refund id belongs to a " + t.getType() + " transaction");
            }
            if (t.getStatus() == PaymentTransactionStatus.SUCCEEDED) {
                return EventOutcome.of(WebhookResult.NO_OP);
            }
            if (e.amount() != null && e.amount().compareTo(t.getAmount()) != 0) {
                return EventOutcome.invalid("refund amount mismatch: event " + e.amount() + " vs " + t.getAmount());
            }
            if (!p.getStatus().isRefundable()) {
                return EventOutcome.invalid("cannot refund a " + p.getStatus() + " payment");
            }
            completeRefundSuccess(p, t, providerTxId);
            return EventOutcome.of(WebhookResult.APPLIED);
        }

        // a refund we did not start ourselves (e.g. made from the provider's own dashboard)
        if (e.amount() == null || e.amount().signum() <= 0) {
            return EventOutcome.invalid("refund event carries no amount");
        }
        if (!p.getStatus().isRefundable()) {
            return EventOutcome.invalid("cannot refund a " + p.getStatus() + " payment");
        }
        BigDecimal value = e.amount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal available = p.refundableAmount().subtract(pendingRefundTotal(p.getId()));
        if (value.compareTo(available) > 0) {
            return EventOutcome.invalid("refund of " + value + " exceeds the refundable balance of " + available);
        }
        PaymentTransaction t = recordTransaction(p, PaymentTransactionType.REFUND, PaymentTransactionStatus.PENDING,
                value, providerTxId, null, "Refund issued at the payment provider", TransactionSource.WEBHOOK, null, null);
        completeRefundSuccess(p, t, providerTxId);
        return EventOutcome.of(WebhookResult.APPLIED);
    }

    private EventOutcome refundFailedEvent(Payment p, ParsedWebhookEvent e) {
        if (e.providerTransactionId() == null) {
            return EventOutcome.of(WebhookResult.NO_OP);
        }
        Optional<PaymentTransaction> known = transactionRepository
                .findByPaymentIdAndProviderTransactionId(p.getId(), e.providerTransactionId());
        if (known.isPresent() && known.get().getType() == PaymentTransactionType.REFUND && known.get().isPending()) {
            known.get().markFailed(e.failureCode());
            return EventOutcome.of(WebhookResult.APPLIED);
        }
        return EventOutcome.of(WebhookResult.NO_OP);
    }

    // =========================================================================================
    // state changes shared by the API path and the webhook path
    // =========================================================================================

    /**
     * Moves the payment to {@code target} if the state machine allows it, doing the money bookkeeping that
     * goes with the new status. Returns false (and changes nothing) when it does not.
     */
    private boolean moveTo(Payment p, PaymentStatus target, String failureCode, String providerTxId,
                           TransactionSource source, UUID actor) {
        if (target == null || p.getStatus() == target) {
            return false;
        }
        PaymentStatus from = p.getStatus();
        if (!from.canTransitionTo(target)) {
            log.warn("payment {} order {}: ignoring move {} -> {}", p.getId(), p.getOrder().getId(), from, target);
            return false;
        }
        switch (target) {
            case CAPTURED -> collect(p, providerTxId, source, actor);
            case AUTHORIZED -> {
                p.transitionTo(target);
                recordTransaction(p, PaymentTransactionType.AUTHORIZATION, PaymentTransactionStatus.SUCCEEDED,
                        p.getAmount(), providerTxId, null, null, source, actor, null);
            }
            case FAILED -> {
                p.transitionTo(target);
                p.setFailureCode(failureCode);
            }
            case CANCELLED -> {
                boolean voidsAuthorization = from == PaymentStatus.AUTHORIZED;
                p.transitionTo(target);
                if (voidsAuthorization) {
                    recordTransaction(p, PaymentTransactionType.VOID, PaymentTransactionStatus.SUCCEEDED,
                            p.getAmount(), providerTxId, null, null, source, actor, null);
                }
            }
            case PENDING, REQUIRES_ACTION, EXPIRED -> p.transitionTo(target);
            default -> throw new IllegalArgumentException("status " + target + " cannot be set directly");
        }
        log.info("payment {} order {} store {} provider {}: {} -> {}", p.getId(), p.getOrder().getId(),
                p.getStore().getId(), p.getProvider(), from, target);
        return true;
    }

    /** Full capture: ledger row + running total + the event that completes the order. */
    private void collect(Payment p, String providerTxId, TransactionSource source, UUID actor) {
        List<PaymentTransaction> pending = transactionRepository.findByPaymentIdAndTypeAndStatus(
                p.getId(), PaymentTransactionType.CAPTURE, PaymentTransactionStatus.PENDING);
        if (pending.isEmpty()) {
            recordTransaction(p, PaymentTransactionType.CAPTURE, PaymentTransactionStatus.SUCCEEDED,
                    p.getAmount(), providerTxId, null, null, source, actor, null);
        } else {
            pending.get(0).markSucceeded(providerTxId);
        }
        p.recordCapture(p.getAmount());
        events.publishEvent(new PaymentCapturedEvent(p.getId(), p.getOrder().getId(), p.getStore().getId(), p.getAmount()));
    }

    private void completeRefundSuccess(Payment p, PaymentTransaction t, String providerTxId) {
        t.markSucceeded(providerTxId);
        p.recordRefund(t.getAmount());
        PaymentRefundSucceededEvent event = new PaymentRefundSucceededEvent(p.getId(), t.getId(),
                p.getOrder().getId(), t.getAmount(), t.getReason(), t.getCreatedBy(), t.getApprovedBy());
        events.publishEvent(event);
        if (event.getRefundId() != null) {
            t.setRefundId(event.getRefundId());
        }
        log.info("refund {} of {} succeeded on payment {} -> {}", t.getId(), t.getAmount(), p.getId(), p.getStatus());
    }

    // =========================================================================================
    // helpers
    // =========================================================================================

    private PaymentTransaction recordTransaction(Payment p, PaymentTransactionType type, PaymentTransactionStatus status,
                                                 BigDecimal amount, String providerTxId, String idempotencyKey,
                                                 String reason, TransactionSource source, UUID createdBy, UUID approvedBy) {
        return transactionRepository.save(PaymentTransaction.builder()
                .payment(p)
                .type(type)
                .status(status)
                .amount(amount)
                .currency(p.getCurrency())
                .providerTransactionId(providerTxId)
                .idempotencyKey(idempotencyKey)
                .reason(reason)
                .source(source)
                .createdBy(createdBy)
                .approvedBy(approvedBy)
                .build());
    }

    private PreparedCreate preparedCreate(Payment p, boolean replayed, String returnUrl) {
        boolean needsProvider = p.getStatus() == PaymentStatus.CREATED && p.getProviderPaymentId() == null;
        PaymentRequest providerRequest = needsProvider
                ? new PaymentRequest(p.getId(), p.getAmount(), p.getCurrency(), p.getPaymentMethod(),
                "Order #" + p.getOrder().getId(), returnUrl,
                Map.of("orderId", String.valueOf(p.getOrder().getId()),
                        "storeId", String.valueOf(p.getStore().getId()),
                        "paymentId", p.getId().toString()))
                : null;
        return new PreparedCreate(p.getId(), replayed, needsProvider, providerRequest);
    }

    private ProviderRefundRequest refundRequest(Payment p, PaymentTransaction t) {
        return new ProviderRefundRequest(p.getProviderPaymentId(), t.getAmount(), t.getCurrency(),
                "refund-" + t.getId(), t.getReason());
    }

    private BigDecimal pendingRefundTotal(UUID paymentId) {
        return transactionRepository
                .findByPaymentIdAndTypeAndStatus(paymentId, PaymentTransactionType.REFUND, PaymentTransactionStatus.PENDING)
                .stream()
                .map(PaymentTransaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentMapper.toResponse(p, transactionRepository.findByPaymentId(p.getId()));
    }

    private Payment lock(UUID id) {
        return paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new PaymentNotFoundException("payment not found"));
    }

    /**
     * Tenant isolation lives here. A payment is loaded through its store ({@code findByIdAndStoreId}); the
     * platform admin (read-only) is the one exception. Anything the caller may not see is reported as "not
     * found", never as "forbidden", so ids cannot be probed across tenants.
     */
    private Payment loadReadable(UUID id, User user) {
        Optional<Payment> found;
        if (accessPolicy.isPlatformAdmin(user)) {
            found = paymentRepository.findById(id);
        } else {
            Long storeId = accessPolicy.storeIdOf(user);
            found = storeId == null ? Optional.empty() : paymentRepository.findByIdAndStoreId(id, storeId);
        }
        Payment p = found.orElseThrow(() -> new PaymentNotFoundException("payment not found"));
        if (!accessPolicy.canAccessBranch(user, p.getBranch())) {
            throw new PaymentNotFoundException("payment not found");
        }
        return p;
    }

    /** For capture / cancel / refund: branch management only (never the cashier, never the platform admin). */
    private Payment loadWritableLocked(UUID id, User user) {
        if (accessPolicy.isPlatformAdmin(user)) {
            throw new AccessDeniedException("platform admins have read-only access to payments");
        }
        Payment p = loadReadable(id, user);
        accessPolicy.requireManageBranch(user, p.getBranch());
        return lock(id);
    }

    private BigDecimal money(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new IllegalStateException("the order has no valid total");
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String fingerprint(Long orderId, PaymentType method) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((orderId + ":" + method).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}

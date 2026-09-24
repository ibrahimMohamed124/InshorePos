package com.inshore.payment.provider.fake;

import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.domain.PaymentTransactionStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the fake gateway "remembers" about one payment - the equivalent of the provider's own database.
 * Lives in memory only (dev), so nothing about the fake ever lands in the real database. Amounts are in
 * minor units, like a real provider's. Thread-safe: the gateway is touched by request threads and by the
 * auto-outcome scheduler.
 */
public final class FakeGatewayPayment {

    /** A refund as the gateway knows it. */
    public static final class FakeRefund {
        private final String id;
        private final long amountMinor;
        private PaymentTransactionStatus status;

        FakeRefund(String id, long amountMinor, PaymentTransactionStatus status) {
            this.id = id;
            this.amountMinor = amountMinor;
            this.status = status;
        }

        public String id() {
            return id;
        }

        public long amountMinor() {
            return amountMinor;
        }

        public PaymentTransactionStatus status() {
            return status;
        }
    }

    private final String providerPaymentId;
    private final String reference;
    private final long amountMinor;
    private final String currency;
    private PaymentStatus status = PaymentStatus.PENDING;
    private String captureId;
    private long refundedMinor;
    private final Map<String, FakeRefund> refundsByKey = new LinkedHashMap<>();
    private final List<FakeRefund> refunds = new ArrayList<>();

    FakeGatewayPayment(String providerPaymentId, String reference, long amountMinor, String currency) {
        this.providerPaymentId = providerPaymentId;
        this.reference = reference;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    public String providerPaymentId() {
        return providerPaymentId;
    }

    /** Our payment id, echoed back in webhooks. */
    public String reference() {
        return reference;
    }

    public long amountMinor() {
        return amountMinor;
    }

    public String currency() {
        return currency;
    }

    public synchronized PaymentStatus status() {
        return status;
    }

    public synchronized void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public synchronized String captureId() {
        return captureId;
    }

    /** Marks the payment collected and returns the (stable) capture id. */
    public synchronized String capture() {
        status = PaymentStatus.CAPTURED;
        if (captureId == null) {
            captureId = "fake_cap_" + shortId();
        }
        return captureId;
    }

    public synchronized long refundedMinor() {
        return refundedMinor;
    }

    synchronized long pendingRefundMinor() {
        return refunds.stream()
                .filter(r -> r.status == PaymentTransactionStatus.PENDING)
                .mapToLong(r -> r.amountMinor)
                .sum();
    }

    synchronized FakeRefund refundForKey(String key) {
        return refundsByKey.get(key);
    }

    /** @return the new refund, or null when it would exceed what is left to refund */
    synchronized FakeRefund addRefund(String key, long minor, boolean immediate) {
        if (minor <= 0 || minor + refundedMinor + pendingRefundMinor() > amountMinor) {
            return null;
        }
        FakeRefund refund = new FakeRefund("fake_re_" + shortId(),
                minor, immediate ? PaymentTransactionStatus.SUCCEEDED : PaymentTransactionStatus.PENDING);
        if (immediate) {
            refundedMinor += minor;
        }
        refunds.add(refund);
        if (key != null) {
            refundsByKey.put(key, refund);
        }
        return refund;
    }

    public synchronized FakeRefund firstPendingRefund() {
        return refunds.stream().filter(r -> r.status == PaymentTransactionStatus.PENDING).findFirst().orElse(null);
    }

    public synchronized void completeRefund(FakeRefund refund) {
        if (refund.status == PaymentTransactionStatus.PENDING) {
            refund.status = PaymentTransactionStatus.SUCCEEDED;
            refundedMinor += refund.amountMinor;
        }
    }

    /** A refund the merchant did NOT ask us for (e.g. from the provider's own dashboard). */
    public synchronized FakeRefund externalRefund(long minor) {
        return addRefund(null, minor, true);
    }

    static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }
}

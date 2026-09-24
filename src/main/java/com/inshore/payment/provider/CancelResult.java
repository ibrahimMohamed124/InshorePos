package com.inshore.payment.provider;

import com.inshore.payment.domain.PaymentStatus;

/** @param status the payment's status at the provider after the attempt (CANCELLED when it worked) */
public record CancelResult(PaymentStatus status, String failureCode) {
}

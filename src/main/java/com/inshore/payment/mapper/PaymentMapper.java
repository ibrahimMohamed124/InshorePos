package com.inshore.payment.mapper;

import com.inshore.payment.domain.Payment;
import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.domain.PaymentTransaction;
import com.inshore.payment.dto.PaymentResponse;
import com.inshore.payment.dto.PaymentTransactionResponse;
import com.inshore.payment.dto.RefundResponse;

import java.util.List;

public class PaymentMapper {

    public static PaymentResponse toResponse(Payment payment, List<PaymentTransaction> transactions) {
        if (payment == null) {
            return null;
        }
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder() != null ? payment.getOrder().getId() : null)
                .branchId(payment.getBranch() != null ? payment.getBranch().getId() : null)
                .provider(payment.getProvider())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .capturedAmount(payment.getCapturedAmount())
                .refundedAmount(payment.getRefundedAmount())
                .refundableAmount(payment.refundableAmount())
                .nextActionUrl(showNextAction(payment) ? payment.getNextActionUrl() : null)
                .cardBrand(payment.getCardBrand())
                .cardLast4(payment.getCardLast4())
                .failureCode(payment.getFailureCode())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .transactions(transactions == null ? List.of()
                        : transactions.stream().map(PaymentMapper::toResponse).toList())
                .build();
    }

    private static boolean showNextAction(Payment payment) {
        return payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.REQUIRES_ACTION;
    }

    public static PaymentTransactionResponse toResponse(PaymentTransaction t) {
        return PaymentTransactionResponse.builder()
                .id(t.getId())
                .type(t.getType())
                .status(t.getStatus())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .reason(t.getReason())
                .createdAt(t.getCreatedAt())
                .build();
    }

    public static RefundResponse toRefundResponse(Payment payment, PaymentTransaction refund) {
        return RefundResponse.builder()
                .transactionId(refund.getId())
                .paymentId(payment.getId())
                .amount(refund.getAmount())
                .status(refund.getStatus())
                .paymentStatus(payment.getStatus())
                .refundedAmount(payment.getRefundedAmount())
                .refundableAmount(payment.refundableAmount())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}

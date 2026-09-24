package com.inshore.payment.dto;

import com.inshore.payment.domain.PaymentStatus;
import com.inshore.payment.domain.PaymentTransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResponse {

    private UUID transactionId;
    private UUID paymentId;
    private BigDecimal amount;
    // PENDING = the provider accepted it and will confirm through a webhook
    private PaymentTransactionStatus status;
    private PaymentStatus paymentStatus;
    private BigDecimal refundedAmount;
    private BigDecimal refundableAmount;
    private Instant createdAt;
}

package com.inshore.payment.dto;

import com.inshore.order.domain.PaymentType;
import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What the API shows of a payment. Never includes the provider's own payment id, the idempotency key or
 * the tenant id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private Long orderId;
    private Long branchId;
    private PaymentProviderCode provider;
    private PaymentStatus status;
    private BigDecimal amount;
    private String currency;
    private PaymentType paymentMethod;
    private BigDecimal capturedAmount;
    private BigDecimal refundedAmount;
    private BigDecimal refundableAmount;
    private String nextActionUrl;
    private String cardBrand;
    private String cardLast4;
    private String failureCode;
    private Instant createdAt;
    private Instant updatedAt;
    private List<PaymentTransactionResponse> transactions;
}

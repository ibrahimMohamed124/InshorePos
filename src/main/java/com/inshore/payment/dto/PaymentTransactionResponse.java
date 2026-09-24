package com.inshore.payment.dto;

import com.inshore.payment.domain.PaymentTransactionStatus;
import com.inshore.payment.domain.PaymentTransactionType;
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
public class PaymentTransactionResponse {

    private UUID id;
    private PaymentTransactionType type;
    private PaymentTransactionStatus status;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private Instant createdAt;
}

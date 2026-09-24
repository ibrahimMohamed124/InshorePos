package com.inshore.payment.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundPaymentRequest {

    // omit to refund everything that is still refundable
    @Positive(message = "amount must be greater than zero")
    @Digits(integer = 17, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotBlank(message = "a reason is required for every refund")
    @Size(max = 500, message = "reason is too long")
    private String reason;
}

package com.inshore.payment.dto;

import com.inshore.order.domain.PaymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Deliberately has no amount, currency or store: the amount is always the order's total, computed
 * server-side, and the tenant always comes from the order. The Idempotency-Key travels as a header.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    // optional: defaults to the order's own payment type, and must match it when given. Never CASH.
    private PaymentType paymentMethod;

    // where the customer returns to after a hosted checkout / 3-D Secure (http or https)
    @Size(max = 1024, message = "returnUrl is too long")
    private String returnUrl;
}

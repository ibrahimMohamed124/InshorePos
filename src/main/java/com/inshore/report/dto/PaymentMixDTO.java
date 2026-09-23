package com.inshore.report.dto;

import com.inshore.order.domain.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Same fields as {@code PaymentSummary} (the shift-report breakdown) plus {@code amount}, an alias
 * of {@code totalAmount} - the reports screen of the web client reads {@code amount}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentMixDTO {

    private PaymentType paymentType;
    private Double totalAmount;
    private Double amount;
    private Integer transactionCount;
    private Double percentage;
}

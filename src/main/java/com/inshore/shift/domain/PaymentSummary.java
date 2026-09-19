package com.inshore.shift.domain;

import com.inshore.order.domain.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Plain in-memory value object - never persisted on its own (ShiftReport keeps
// this list @Transient, computed fresh from Order/Refund aggregates each time
// a report is built). It was previously annotated @Entity with no @Id, which
// would have failed at startup ("no identifier specified for entity") the
// first time Hibernate scanned it.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentSummary {

    private PaymentType paymentType;
    private Double totalAmount;
    private int transactionCount;
    private Double percentage;
}

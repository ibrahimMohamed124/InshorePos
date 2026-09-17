package com.inshore.payload.dto;

import com.inshore.domain.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RefundDTO {

    private Long id;

    // Client sends orderId on create; the full order is only populated on the
    // way out - same in/out split as OrderDTO#customerId/customer.
    private Long orderId;
    private OrderDTO order;

    private String reason;

    // Client may send amount to request a partial refund; omit it (null) to
    // refund the order's full remaining balance instead. Always validated
    // server-side against what's actually left to refund on the order - never
    // trusted as the final amount (see RefundServiceImpl#createRefund).
    private Double amount;

    // Shift reports aren't wired up yet - ShiftReport has no repository/service
    // to resolve "the cashier's current shift" from. Always null for now; kept
    // so refunds can be attributed to a shift once that feature exists.
    private Long shiftReportId;

    // cashier/branch are always derived server-side - from the authenticated
    // user and from the order being refunded - a client can never set who
    // processed a refund or which branch it's attributed to.
    private UUID cashierId;
    private UserDTO cashier;
    private String cashierName;

    private Long branchId;
    private BranchDTO branch;

    // Optional on create: defaults to the order's own paymentType (refunding
    // back through the same method it was paid with) when left unset.
    private PaymentType paymentType;

    private LocalDateTime createdAt;

}

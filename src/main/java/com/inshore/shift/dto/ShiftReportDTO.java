package com.inshore.shift.dto;

import com.inshore.branch.dto.BranchDTO;
import com.inshore.order.dto.OrderDTO;
import com.inshore.product.dto.ProductDTO;
import com.inshore.refund.dto.RefundDTO;
import com.inshore.shift.domain.PaymentSummary;
import com.inshore.user.dto.UserDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ShiftReportDTO {
    private Long id;

    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;

    private Double totalSale;
    private Double totalRefunds;
    private Double netSale;
    private Long totalOrders;

    // Was Long - User.id (the cashier) is a UUID everywhere else in the app
    // (see UserDTO, RefundDTO, OrderRepository). Left as Long here it could
    // never actually hold a real cashier id.
    private UUID cashierId;

    private UserDTO cashier;

    // Was Double - Branch.id is a Long everywhere else (see BranchDTO,
    // RefundDTO).
    private Long branchId;

    private BranchDTO branch;

    private List<PaymentSummary> paymentSummary;

    private List<ProductDTO> topSellingProducts;

    private List<OrderDTO> recentOrders;

    private List<RefundDTO> refunds;

}

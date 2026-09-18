package com.inshore.order.dto;

import com.inshore.branch.dto.BranchDTO;
import com.inshore.order.domain.OrderStatus;
import com.inshore.order.domain.PaymentType;
import com.inshore.customer.domain.Customer;
import com.inshore.user.dto.UserDTO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderDTO {

    private Long id;

    private Double totalAmount;

    private LocalDateTime createdAt;

    // branch/cashier are always derived server-side from the authenticated
    // cashier's own branch (see OrderServiceImpl#createOrder) - a client can
    // never set which branch or cashier an order belongs to.
    private Long branchId;
    private BranchDTO branch;

    private UserDTO cashier;

    // Optional: only present for a walk-in/registered customer. Null means a
    // guest/anonymous sale. Client sends customerId on create; the full
    // customer is only populated on the way out.
    private Long customerId;
    private Customer customer;

    private PaymentType paymentType;

    private OrderStatus status;

    private List<OrderItemDTO> items;
}

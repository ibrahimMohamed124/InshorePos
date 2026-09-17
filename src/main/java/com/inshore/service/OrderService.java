package com.inshore.service;

import com.inshore.domain.OrderStatus;
import com.inshore.domain.PaymentType;
import com.inshore.payload.dto.OrderDTO;

import java.util.List;
import java.util.UUID;

public interface OrderService {

    OrderDTO createOrder(OrderDTO orderDTO) throws Exception;

    OrderDTO getOrderById(Long id) throws Exception;

    /**
     * branchId is required - every order belongs to exactly one branch.
     * customerId, cashierId, paymentType and orderStatus are optional filters;
     * pass null for any of them to not filter on that field.
     * Cashier IDs are UUIDs (see User#id), not Longs.
     */
    List<OrderDTO> getOrderByBranch(
            Long branchId,
            Long customerId,
            UUID cashierId,
            PaymentType paymentType,
            OrderStatus orderStatus
    ) throws Exception;

    List<OrderDTO> getOrderByCashier(UUID cashierId) throws Exception;

    void deleteOrder(Long id) throws Exception;

    OrderDTO updateOrderStatus(Long id, OrderStatus status) throws Exception;

    List<OrderDTO> getTodayOrdersByBranch(Long branchId) throws Exception;

    List<OrderDTO> getOrdersByCustomerId(Long customerId) throws Exception;

    List<OrderDTO> getTop5RecentOrdersByBranchId(Long branchId) throws Exception;
}

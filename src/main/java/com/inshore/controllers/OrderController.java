package com.inshore.controllers;

import com.inshore.domain.OrderStatus;
import com.inshore.domain.PaymentType;
import com.inshore.payload.dto.OrderDTO;
import com.inshore.payload.response.ApiResponse;
import com.inshore.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(
            @RequestBody OrderDTO orderDTO
    ) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(orderDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(
            @PathVariable Long id
    ) throws Exception {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping
    public ResponseEntity<List<OrderDTO>> getOrdersByBranch(
            @RequestParam Long branchId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) PaymentType paymentType,
            @RequestParam(required = false) OrderStatus status
    ) throws Exception {
        return ResponseEntity.ok(
                orderService.getOrderByBranch(branchId, customerId, cashierId, paymentType, status));
    }

    @GetMapping("/cashier/{cashierId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByCashier(
            @PathVariable UUID cashierId
    ) throws Exception {
        return ResponseEntity.ok(orderService.getOrderByCashier(cashierId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByCustomer(
            @PathVariable Long customerId
    ) throws Exception {
        return ResponseEntity.ok(orderService.getOrdersByCustomerId(customerId));
    }

    @GetMapping("/branch/{branchId}/today")
    public ResponseEntity<List<OrderDTO>> getTodayOrdersByBranch(
            @PathVariable Long branchId
    ) throws Exception {
        return ResponseEntity.ok(orderService.getTodayOrdersByBranch(branchId));
    }

    @GetMapping("/branch/{branchId}/recent")
    public ResponseEntity<List<OrderDTO>> getRecentOrdersByBranch(
            @PathVariable Long branchId
    ) throws Exception {
        return ResponseEntity.ok(orderService.getTop5RecentOrdersByBranchId(branchId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @PathVariable Long id,
            @RequestParam OrderStatus status
    ) throws Exception {
        return ResponseEntity.ok(orderService.updateOrderStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteOrder(
            @PathVariable Long id
    ) throws Exception {
        orderService.deleteOrder(id);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setSuccess(true);
        apiResponse.setMessage("Order deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }
}

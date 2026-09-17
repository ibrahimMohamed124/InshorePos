package com.inshore.repository;

import com.inshore.models.Order;
import com.inshore.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerId(Long customerId);
    List<Order> findByBranchId(Long branchId);
    List<Order> findByCashierId(UUID cashierId);
    List<Order> findByBranchIdAndCreatedAtBetween(
            Long branchId, LocalDateTime from, LocalDateTime to
    );
    List<Order> findByCashierAndCreatedAtBetween(
            User cashier, LocalDateTime from, LocalDateTime to
    );
    List<Order> findTop5ByBranchIdOrderByCreatedAtDesc(Long branchId);
}

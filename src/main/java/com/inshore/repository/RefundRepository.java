package com.inshore.repository;

import com.inshore.models.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderId(Long orderId);

    List<Refund> findByCashierId(UUID cashierId);

    List<Refund> findByCashierIdAndCreatedAtBetween(
            UUID cashierId, LocalDateTime startDate, LocalDateTime endDate
    );

    List<Refund> findByBranchId(Long branchId);

    List<Refund> findByShiftReportId(Long shiftReportId);

}

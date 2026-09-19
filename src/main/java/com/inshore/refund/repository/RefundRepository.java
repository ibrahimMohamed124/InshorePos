package com.inshore.refund.repository;

import com.inshore.refund.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    // RefundMapper#toDTO reads cashier, branch, shiftReport, and the full order
    // (which OrderMapper then walks the same way it does when orders are listed
    // directly - see OrderRepository.ORDER_WITH_DETAILS). Fetch all of it here
    // too, or every refund in a list re-triggers that same chain of extra
    // SELECTs. DISTINCT is required because the order.items join multiplies
    // each refund's row once per item on its order.
    String REFUND_WITH_DETAILS = """
            SELECT DISTINCT r FROM Refund r
            LEFT JOIN FETCH r.cashier
            LEFT JOIN FETCH r.branch rb
            LEFT JOIN FETCH rb.store
            LEFT JOIN FETCH rb.manager
            LEFT JOIN FETCH r.shiftReport
            LEFT JOIN FETCH r.order o
            LEFT JOIN FETCH o.branch ob
            LEFT JOIN FETCH ob.store
            LEFT JOIN FETCH ob.manager
            LEFT JOIN FETCH o.cashier
            LEFT JOIN FETCH o.customer
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH p.store
            LEFT JOIN FETCH p.category
            """;

    @Query(REFUND_WITH_DETAILS + "WHERE r.order.id = :orderId")
    List<Refund> findByOrderId(@Param("orderId") Long orderId);

    @Query(REFUND_WITH_DETAILS + "WHERE r.cashier.id = :cashierId")
    List<Refund> findByCashierId(@Param("cashierId") UUID cashierId);

    @Query(REFUND_WITH_DETAILS + "WHERE r.cashier.id = :cashierId AND r.createdAt BETWEEN :startDate AND :endDate")
    List<Refund> findByCashierIdAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(REFUND_WITH_DETAILS + "WHERE r.branch.id = :branchId")
    List<Refund> findByBranchId(@Param("branchId") Long branchId);

    @Query(REFUND_WITH_DETAILS + "WHERE r.shiftReport.id = :shiftReportId")
    List<Refund> findByShiftReportId(@Param("shiftReportId") Long shiftReportId);

    // Used by getAllRefunds() (admin-only listing) instead of the inherited
    // findAll(), for the same reason as above.
    @Query(REFUND_WITH_DETAILS)
    List<Refund> findAllWithDetails();

    // ShiftReportServiceImpl needs only the total, not the full refund graph -
    // a SUM avoids fetch-joining every refund's order/items/product chain just
    // to add up amounts.
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM Refund r WHERE r.cashier.id = :cashierId AND r.createdAt BETWEEN :from AND :to")
    Double sumAmountByCashierAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

}

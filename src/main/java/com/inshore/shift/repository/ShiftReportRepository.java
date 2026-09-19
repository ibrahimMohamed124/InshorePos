package com.inshore.shift.repository;

import com.inshore.shift.domain.ShiftReport;
import com.inshore.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftReportRepository extends JpaRepository<ShiftReport, Long> {

    // ShiftReportMapper#toDTO reads cashier and branch (+ branch.store,
    // branch.manager), both @ManyToOne and EAGER by default - fetch them here
    // so listing shift reports doesn't fire one extra SELECT per row per
    // relation. Deliberately NOT fetch-joining topSellingProducts/recentOrders/
    // refunds: joining three separate collections in one query multiplies the
    // result rows by the product of their sizes. Those stay lazy and are
    // batched via hibernate.default_batch_fetch_size (see application.properties),
    // the same approach OrderRepository takes for Order.items.
    String SHIFT_REPORT_WITH_DETAILS = """
            SELECT sr FROM ShiftReport sr
            LEFT JOIN FETCH sr.cashier
            LEFT JOIN FETCH sr.branch b
            LEFT JOIN FETCH b.store
            LEFT JOIN FETCH b.manager
            """;

    @Query(SHIFT_REPORT_WITH_DETAILS + "WHERE sr.cashier.id = :cashierId ORDER BY sr.shiftStart DESC")
    List<ShiftReport> findByCashierId(@Param("cashierId") UUID cashierId);

    @Query(SHIFT_REPORT_WITH_DETAILS + "WHERE sr.branch.id = :branchId ORDER BY sr.shiftStart DESC")
    List<ShiftReport> findByBranchId(@Param("branchId") Long branchId);

    @Query(SHIFT_REPORT_WITH_DETAILS)
    List<ShiftReport> findAllWithDetails();

    // @EntityGraph (rather than hand-written JPQL) is used here specifically
    // so the "Top1 by shiftStart desc" LIMIT stays intact - combining a JOIN
    // FETCH with a row-limiting query in JPQL can make Hibernate drop the
    // LIMIT and page in memory instead (see OrderRepository.findTop5...).
    // An entity graph fetches the same to-one relations without that risk.
    @EntityGraph(attributePaths = {"cashier", "branch", "branch.store", "branch.manager"})
    Optional<ShiftReport> findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(User cashier);

    @Query(SHIFT_REPORT_WITH_DETAILS
            + "WHERE sr.cashier = :cashier AND sr.shiftStart BETWEEN :rangeStart AND :rangeEnd")
    Optional<ShiftReport> findByCashierAndShiftStartBetween(
            @Param("cashier") User cashier,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd
    );

}

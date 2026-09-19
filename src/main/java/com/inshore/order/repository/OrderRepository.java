package com.inshore.order.repository;

import com.inshore.user.domain.User;
import com.inshore.order.domain.Order;
import com.inshore.order.domain.PaymentType;
import com.inshore.product.domain.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // OrderMapper#toDTO walks: branch (+ branch.store, branch.manager), cashier,
    // customer, and every item (+ item.product, product.store, product.category).
    // All of those are @ManyToOne (EAGER by default), so without this JOIN FETCH
    // chain, listing N orders fires roughly 4-8 extra SELECTs per order.
    // DISTINCT is required because joining the items collection multiplies each
    // order's row once per item.
    String ORDER_WITH_DETAILS = """
            SELECT DISTINCT o FROM Order o
            LEFT JOIN FETCH o.branch b
            LEFT JOIN FETCH b.store
            LEFT JOIN FETCH b.manager
            LEFT JOIN FETCH o.cashier
            LEFT JOIN FETCH o.customer
            LEFT JOIN FETCH o.items i
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH p.store
            LEFT JOIN FETCH p.category
            """;

    @Query(ORDER_WITH_DETAILS + "WHERE o.customer.id = :customerId")
    List<Order> findByCustomerId(@Param("customerId") Long customerId);

    @Query(ORDER_WITH_DETAILS + "WHERE o.branch.id = :branchId")
    List<Order> findByBranchId(@Param("branchId") Long branchId);

    @Query(ORDER_WITH_DETAILS + "WHERE o.cashier.id = :cashierId")
    List<Order> findByCashierId(@Param("cashierId") UUID cashierId);

    @Query(ORDER_WITH_DETAILS + "WHERE o.branch.id = :branchId AND o.createdAt BETWEEN :from AND :to")
    List<Order> findByBranchIdAndCreatedAtBetween(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query(ORDER_WITH_DETAILS + "WHERE o.cashier = :cashier AND o.createdAt BETWEEN :from AND :to")
    List<Order> findByCashierAndCreatedAtBetween(
            @Param("cashier") User cashier,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Kept as a plain derived query (no JOIN FETCH): combining a JOIN FETCH on
    // the "items" collection with a row-limiting ("Top5") query makes Hibernate
    // drop the LIMIT and page in memory instead - worse than the problem it'd
    // fix. This is capped at 5 rows already, and hibernate.default_batch_fetch_size
    // (see application.properties) batches its handful of remaining lazy loads
    // into one or two extra queries instead of one per row.
    List<Order> findTop5ByBranchIdOrderByCreatedAtDesc(Long branchId);

    // --- Shift-report aggregates (ShiftReportServiceImpl) -------------------
    // These compute totals in the database with SUM/COUNT/GROUP BY instead of
    // loading every order (with its full fetch-joined item/product graph) into
    // memory just to add numbers up.

    // Only COMPLETED and REFUNDED orders represent a real, finished sale;
    // PENDING isn't final yet and CANCELLED never happened.
    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.cashier.id = :cashierId
              AND o.createdAt BETWEEN :from AND :to
              AND o.status IN (com.inshore.order.domain.OrderStatus.COMPLETED, com.inshore.order.domain.OrderStatus.REFUNDED)
            """)
    Double sumTotalAmountByCashierAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT COUNT(o)
            FROM Order o
            WHERE o.cashier.id = :cashierId
              AND o.createdAt BETWEEN :from AND :to
              AND o.status IN (com.inshore.order.domain.OrderStatus.COMPLETED, com.inshore.order.domain.OrderStatus.REFUNDED)
            """)
    Long countByCashierAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
            SELECT o.paymentType AS paymentType,
                   COALESCE(SUM(o.totalAmount), 0) AS totalAmount,
                   COUNT(o) AS transactionCount
            FROM Order o
            WHERE o.cashier.id = :cashierId
              AND o.createdAt BETWEEN :from AND :to
              AND o.status IN (com.inshore.order.domain.OrderStatus.COMPLETED, com.inshore.order.domain.OrderStatus.REFUNDED)
            GROUP BY o.paymentType
            """)
    List<PaymentTypeTotalsProjection> summarizePaymentTypesByCashierAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // GROUP BY on oi.product (not just its id) lets Spring Data hydrate the
    // Product straight from this one query/projection instead of a second
    // lookup per row.
    @Query("""
            SELECT oi.product AS product, SUM(oi.quantity) AS totalQuantity
            FROM Order o JOIN o.items oi
            WHERE o.cashier.id = :cashierId
              AND o.createdAt BETWEEN :from AND :to
              AND o.status IN (com.inshore.order.domain.OrderStatus.COMPLETED, com.inshore.order.domain.OrderStatus.REFUNDED)
            GROUP BY oi.product
            ORDER BY SUM(oi.quantity) DESC
            """)
    List<ProductQuantityProjection> findTopSellingProductsByCashierAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    // To-one relations only, no "items" JOIN FETCH - same reasoning as
    // findTop5ByBranchIdOrderByCreatedAtDesc above: a limited/paged query
    // combined with a collection fetch join silently loses its LIMIT. Order
    // items lazy-load in a single batched query instead when the mapper reads
    // order.getItems().
    @Query("""
            SELECT o FROM Order o
            LEFT JOIN FETCH o.branch b
            LEFT JOIN FETCH b.store
            LEFT JOIN FETCH b.manager
            LEFT JOIN FETCH o.cashier
            LEFT JOIN FETCH o.customer
            WHERE o.cashier.id = :cashierId AND o.createdAt BETWEEN :from AND :to
            ORDER BY o.createdAt DESC
            """)
    List<Order> findRecentByCashierAndCreatedAtBetween(
            @Param("cashierId") UUID cashierId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    interface PaymentTypeTotalsProjection {
        PaymentType getPaymentType();
        Double getTotalAmount();
        Long getTransactionCount();
    }

    interface ProductQuantityProjection {
        Product getProduct();
        Long getTotalQuantity();
    }
}

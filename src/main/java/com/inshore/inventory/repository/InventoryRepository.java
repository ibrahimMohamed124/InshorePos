package com.inshore.inventory.repository;

import com.inshore.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    // InventoryMapper#toDTO reads branch, and product (+ product.store,
    // product.category, since ProductMapper is not used here directly but the
    // same EAGER-by-default chain applies to product's own relations the first
    // time it's touched elsewhere in the same request) - fetch them all now.
    @Query("""
            SELECT i FROM Inventory i
            LEFT JOIN FETCH i.branch
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH p.store
            LEFT JOIN FETCH p.category
            WHERE i.product.id = :productId AND i.branch.id = :branchId
            """)
    Inventory findByProductIdAndBranchId(@Param("productId") Long productId, @Param("branchId") Long branchId);

    @Query("""
            SELECT i FROM Inventory i
            LEFT JOIN FETCH i.branch
            LEFT JOIN FETCH i.product p
            LEFT JOIN FETCH p.store
            LEFT JOIN FETCH p.category
            WHERE i.branch.id = :branchId
            """)
    List<Inventory> findByBranchId(@Param("branchId") Long branchId);

    /**
     * Row-locking variant of findByProductIdAndBranchId. Two orders for the same
     * product/branch created at the same instant both read-then-write the stock
     * count, so without a DB-level lock they can both pass the "enough stock?"
     * check off the same stale number and oversell. Only use this inside an
     * existing @Transactional method (see OrderServiceImpl) - the lock is held
     * until that transaction commits or rolls back.
     *
     * Deliberately NOT fetch-joined: locking across multiple joined tables in
     * one statement is a different (and murkier) locking guarantee than
     * locking just the Inventory row this method exists to protect.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.branch.id = :branchId")
    Inventory findByProductIdAndBranchIdForUpdate(
            @Param("productId") Long productId,
            @Param("branchId") Long branchId
    );

}

package com.inshore.repository;

import com.inshore.models.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Inventory findByProductIdAndBranchId(Long productId, Long branchId);
    List<Inventory> findByBranchId(Long branchId);

    /**
     * Row-locking variant of findByProductIdAndBranchId. Two orders for the same
     * product/branch created at the same instant both read-then-write the stock
     * count, so without a DB-level lock they can both pass the "enough stock?"
     * check off the same stale number and oversell. Only use this inside an
     * existing @Transactional method (see OrderServiceImpl) - the lock is held
     * until that transaction commits or rolls back.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.product.id = :productId AND i.branch.id = :branchId")
    Inventory findByProductIdAndBranchIdForUpdate(
            @Param("productId") Long productId,
            @Param("branchId") Long branchId
    );

}

package com.inshore.payment.repository;

import com.inshore.payment.domain.Payment;
import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // Tenant-scoped lookup: a payment is only found through the store that owns it.
    @Query("SELECT p FROM Payment p WHERE p.id = :id AND p.store.id = :storeId")
    Optional<Payment> findByIdAndStoreId(@Param("id") UUID id, @Param("storeId") Long storeId);

    @Query("SELECT p FROM Payment p WHERE p.store.id = :storeId AND p.idempotencyKey = :key")
    Optional<Payment> findByStoreIdAndIdempotencyKey(@Param("storeId") Long storeId, @Param("key") String key);

    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId ORDER BY p.createdAt DESC")
    List<Payment> findByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId AND p.status IN :statuses ORDER BY p.createdAt DESC")
    List<Payment> findByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                           @Param("statuses") Collection<PaymentStatus> statuses);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.order.id = :orderId")
    long countByOrderId(@Param("orderId") Long orderId);

    @Query("SELECT COUNT(p) FROM Payment p WHERE p.order.id = :orderId AND p.status IN :statuses")
    long countByOrderIdAndStatusIn(@Param("orderId") Long orderId,
                                   @Param("statuses") Collection<PaymentStatus> statuses);

    /**
     * Row lock for everything that changes a payment (webhook, capture, cancel, refund): concurrent
     * writers queue up here and each one sees the previous one's committed totals. Only call it inside
     * a transaction. Same pattern as InventoryRepository#findByProductIdAndBranchIdForUpdate.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.provider = :provider AND p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findByProviderAndProviderPaymentIdForUpdate(
            @Param("provider") PaymentProviderCode provider,
            @Param("providerPaymentId") String providerPaymentId);
}

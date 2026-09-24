package com.inshore.payment.repository;

import com.inshore.payment.domain.PaymentTransaction;
import com.inshore.payment.domain.PaymentTransactionStatus;
import com.inshore.payment.domain.PaymentTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    @Query("SELECT t FROM PaymentTransaction t WHERE t.payment.id = :paymentId ORDER BY t.createdAt ASC")
    List<PaymentTransaction> findByPaymentId(@Param("paymentId") UUID paymentId);

    @Query("SELECT t FROM PaymentTransaction t WHERE t.payment.id = :paymentId AND t.type = :type AND t.status = :status")
    List<PaymentTransaction> findByPaymentIdAndTypeAndStatus(@Param("paymentId") UUID paymentId,
                                                             @Param("type") PaymentTransactionType type,
                                                             @Param("status") PaymentTransactionStatus status);

    @Query("SELECT t FROM PaymentTransaction t WHERE t.payment.id = :paymentId AND t.idempotencyKey = :key")
    Optional<PaymentTransaction> findByPaymentIdAndIdempotencyKey(@Param("paymentId") UUID paymentId,
                                                                  @Param("key") String key);

    @Query("SELECT t FROM PaymentTransaction t WHERE t.payment.id = :paymentId AND t.providerTransactionId = :providerTransactionId")
    Optional<PaymentTransaction> findByPaymentIdAndProviderTransactionId(
            @Param("paymentId") UUID paymentId,
            @Param("providerTransactionId") String providerTransactionId);

    boolean existsByRefundId(Long refundId);
}

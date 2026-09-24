package com.inshore.payment.repository;

import com.inshore.payment.domain.PaymentProviderCode;
import com.inshore.payment.domain.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    Optional<PaymentWebhookEvent> findByProviderAndProviderEventId(PaymentProviderCode provider, String providerEventId);
}

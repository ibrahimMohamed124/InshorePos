package com.inshore.order.listener;

import com.inshore.order.domain.Order;
import com.inshore.order.domain.OrderStatus;
import com.inshore.order.repository.OrderRepository;
import com.inshore.payment.event.PaymentCapturedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The ONLY place an order becomes paid: when the payment module reports that money was captured. The event
 * is delivered synchronously inside the transaction that captured the payment (a webhook, a capture, a
 * sync), so the payment and the order change together or not at all.
 * <p>
 * In Inshore terms "paid" is COMPLETED: reports, shifts and cash reconciliation already treat COMPLETED as
 * a finished sale, so no separate PAID status is needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentListener {

    private final OrderRepository orderRepository;

    @EventListener
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        Order order = orderRepository.findByIdForUpdate(event.orderId()).orElse(null);
        if (order == null) {
            log.error("payment {} captured for order {} which no longer exists", event.paymentId(), event.orderId());
            return;
        }
        switch (order.getStatus()) {
            case PENDING -> {
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
                log.info("order {} paid by payment {} -> COMPLETED", order.getId(), event.paymentId());
            }
            case COMPLETED -> log.debug("order {} already COMPLETED (payment {})", order.getId(), event.paymentId());
            // Money was collected for an order that is no longer payable. Never throw here: that would roll
            // the capture back and make the provider retry forever. Make it loud instead - it needs a refund.
            default -> log.error("payment {} captured {} for order {} which is {} - MANUAL REVIEW / REFUND NEEDED",
                    event.paymentId(), event.amount(), order.getId(), order.getStatus());
        }
    }
}

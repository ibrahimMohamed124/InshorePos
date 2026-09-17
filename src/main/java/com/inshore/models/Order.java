package com.inshore.models;

import com.inshore.domain.OrderStatus;
import com.inshore.domain.PaymentType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    // Protects against two concurrent requests racing on the same order - e.g.
    // one request cancelling it while another marks it completed, or a double
    // click sending the same delete twice. Hibernate rejects the second write
    // with an optimistic-locking failure instead of silently letting it through.
    @Version
    private Long version;

    private Double totalAmount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @ManyToOne
    private Branch branch;

    @ManyToOne
    private User cashier;

    @ManyToOne
    private Customer customer;

    // mappedBy points back to OrderItem.order (the owning side of the FK).
    // Without mappedBy, Hibernate would expect a separate join table instead of
    // using the order_id column on order_item, and without cascade/orphanRemoval
    // saving/deleting an Order never persists or cleans up its items.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = OrderStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package com.inshore.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Integer quantity;

    // Line total (unit price * quantity) captured at the time of sale, so a later
    // change to Product.sellingPrice never rewrites the historical order value.
    @Column(nullable = false)
    private Double price;

    @ManyToOne
    @JoinColumn(nullable = false)
    private Product product;

    // This is the owning side of the Order <-> OrderItem relationship
    // (Order.items is mappedBy = "order"); nullable = false enforces that
    // an OrderItem can never exist without its parent order.
    @ManyToOne
    @JoinColumn(nullable = false)
    private Order order;
}

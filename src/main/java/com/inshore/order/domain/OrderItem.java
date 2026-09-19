package com.inshore.order.domain;

import com.inshore.product.domain.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(indexes = {
        @Index(name = "idx_order_item_order_id", columnList = "order_id"),
        @Index(name = "idx_order_item_product_id", columnList = "product_id")
})
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

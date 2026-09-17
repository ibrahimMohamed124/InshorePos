package com.inshore.payload.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderItemDTO {

    private Long id;

    // Client sends this on create; price is always computed server-side from
    // the product's current selling price, never trusted from the client.
    private Integer quantity;

    private Double price;

    private ProductDTO product;

    private Long productId;

    private Long orderId;
}

package com.inshore.mapper;

import com.inshore.models.OrderItem;
import com.inshore.payload.dto.OrderItemDTO;

public class OrderItemMapper {

    public static OrderItemDTO toDTO(OrderItem orderItem) {
        if (orderItem == null) {
            return null;
        }

        return OrderItemDTO.builder()
                .id(orderItem.getId())
                .productId(orderItem.getProduct() != null ? orderItem.getProduct().getId() : null)
                .quantity(orderItem.getQuantity())
                .price(orderItem.getPrice())
                .product(orderItem.getProduct() != null ? ProductMapper.toDTO(orderItem.getProduct()) : null)
                .orderId(orderItem.getOrder() != null ? orderItem.getOrder().getId() : null)
                .build();
    }
}

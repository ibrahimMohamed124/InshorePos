package com.inshore.order.mapper;

import com.inshore.branch.mapper.BranchMapper;
import com.inshore.order.domain.Order;
import com.inshore.order.dto.OrderDTO;
import com.inshore.user.mapper.UserMapper;

import java.util.Collections;
import java.util.stream.Collectors;

public class OrderMapper {

    public static OrderDTO toDTO(Order order) {
        if (order == null) {
            return null;
        }

        return OrderDTO.builder()
                .id(order.getId())
                .totalAmount(order.getTotalAmount())
                .createdAt(order.getCreatedAt())
                .branchId(order.getBranch() != null ? order.getBranch().getId() : null)
                .branch(order.getBranch() != null ? BranchMapper.toDTO(order.getBranch()) : null)
                .cashier(UserMapper.toDTO(order.getCashier()))
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customer(order.getCustomer())
                .paymentType(order.getPaymentType())
                .status(order.getStatus())
                .items(order.getItems() == null
                        ? Collections.emptyList()
                        : order.getItems().stream()
                            .map(OrderItemMapper::toDTO)
                            .collect(Collectors.toList()))
                .build();
    }
}

package com.inshore.inventory.mapper;

import com.inshore.branch.domain.Branch;
import com.inshore.inventory.domain.Inventory;
import com.inshore.product.domain.Product;
import com.inshore.inventory.dto.InventoryDTO;

public class InventoryMapper {

    public static InventoryDTO toDTO(Inventory inventory) {
        return InventoryDTO.builder()
                .id(inventory.getId())
                .branchId(inventory.getBranch().getId())
                .productId(inventory.getProduct().getId())
                .quantity(inventory.getQuantity())
                .build();
    }

    public static Inventory toEntity(
            InventoryDTO inventoryDTO,
            Branch branch,
            Product product
    ) {
        return Inventory.builder()
                .branch(branch)
                .product(product)
                .quantity(inventoryDTO.getQuantity())
                .build();
    }
}

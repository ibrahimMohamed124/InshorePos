package com.inshore.mapper;

import com.inshore.models.Branch;
import com.inshore.models.Inventory;
import com.inshore.models.Product;
import com.inshore.payload.dto.InventoryDTO;

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

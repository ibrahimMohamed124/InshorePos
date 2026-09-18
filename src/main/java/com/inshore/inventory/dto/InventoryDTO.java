package com.inshore.inventory.dto;

import com.inshore.branch.domain.Branch;
import com.inshore.product.domain.Product;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryDTO {

    private Long id;

    private Long branchId;

    private Long productId;

    private Branch branch;

    private Product product;

    private Integer quantity;

    private LocalDateTime lastUpdate;

}

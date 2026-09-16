package com.inshore.payload.dto;

import com.inshore.models.Branch;
import com.inshore.models.Product;
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

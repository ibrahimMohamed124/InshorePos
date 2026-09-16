package com.inshore.service;

import com.inshore.models.Inventory;
import com.inshore.payload.dto.InventoryDTO;

import java.util.List;

public interface InventoryService {

    InventoryDTO createInventory(InventoryDTO inventoryDTO);
    InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO);
    void deleteInventory(Long id);
    InventoryDTO getInventoryById(Long id);
    InventoryDTO getInventoryByProductIdAndBranchId(Long productId, Long branchId);
    List<InventoryDTO> getAllInventoryByBranchId(Long branchId);
}

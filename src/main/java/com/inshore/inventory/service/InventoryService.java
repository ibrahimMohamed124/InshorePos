package com.inshore.inventory.service;

import com.inshore.inventory.domain.Inventory;
import com.inshore.inventory.dto.InventoryDTO;

import java.util.List;

public interface InventoryService {

    InventoryDTO createInventory(InventoryDTO inventoryDTO);
    InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO);
    void deleteInventory(Long id);
    InventoryDTO getInventoryById(Long id);
    InventoryDTO getInventoryByProductIdAndBranchId(Long productId, Long branchId);
    List<InventoryDTO> getAllInventoryByBranchId(Long branchId);
}

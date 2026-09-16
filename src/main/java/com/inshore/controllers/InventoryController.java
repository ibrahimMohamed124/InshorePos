package com.inshore.controllers;

import java.util.List;

import com.inshore.payload.dto.InventoryDTO;
import com.inshore.payload.response.ApiResponse;
import com.inshore.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping
    public ResponseEntity<InventoryDTO> createInventory(
            @RequestBody InventoryDTO inventoryDTO
    ) {
        return ResponseEntity.ok(inventoryService.createInventory(inventoryDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InventoryDTO> updateInventory(
            @PathVariable Long id,
            @RequestBody InventoryDTO inventoryDTO
    ) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, inventoryDTO));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteInventory(
            @PathVariable Long id
    ) {
        inventoryService.deleteInventory(id);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Inventory record deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InventoryDTO> getInventoryById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(inventoryService.getInventoryById(id));
    }

    @GetMapping("/product/{productId}/branch/{branchId}")
    public ResponseEntity<InventoryDTO> getInventoryByProductIdAndBranchId(
            @PathVariable Long productId,
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(inventoryService.getInventoryByProductIdAndBranchId(productId, branchId));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<InventoryDTO>> getAllInventoryByBranchId(
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(inventoryService.getAllInventoryByBranchId(branchId));
    }
}

package com.inshore.controllers;

import java.util.List;

import com.inshore.exceptions.UserException;
import com.inshore.models.User;
import com.inshore.payload.dto.BranchDTO;
import com.inshore.payload.response.ApiResponse;
import com.inshore.service.BranchService;
import com.inshore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<BranchDTO> createBranch(
            @RequestBody BranchDTO branchDTO
    ) throws UserException {
        User user = userService.getCurrentUser();

        return ResponseEntity.ok(branchService.createBranch(branchDTO, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BranchDTO> updateBranch(
            @PathVariable Long id,
            @RequestBody BranchDTO branchDTO
    ) throws UserException {
        User user = userService.getCurrentUser();

        return ResponseEntity.ok(branchService.updateBranch(id, branchDTO, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteBranch(
            @PathVariable Long id
    ) throws UserException {
        User user = userService.getCurrentUser();

        branchService.deleteBranch(id, user);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Branch deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BranchDTO> getBranchById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(branchService.getBranchById(id));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<BranchDTO>> getAllBranchesByStoreId(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(branchService.getAllBranchesByStoreId(storeId));
    }
}

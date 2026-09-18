package com.inshore.category.controller;

import java.util.List;

import com.inshore.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inshore.shared.exception.UserException;
import com.inshore.category.dto.CategoryDTO;
import com.inshore.category.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryDTO> createCategory(
            @RequestHeader("Authorization") String jwt,
            @RequestBody CategoryDTO categoryDTO
    ) throws Exception {
        return ResponseEntity.ok(categoryService.createCategory(categoryDTO));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryDTO> updateCategory(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long id,
            @RequestBody CategoryDTO categoryDTO
    ) throws UserException {
        return ResponseEntity.ok(categoryService.updateCategory(id, categoryDTO));
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<CategoryDTO>> getCategoriesByStoreId(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(categoryService.getCategoriesByStore(storeId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCategory(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long id
    ) throws Exception {
        categoryService.delete(id);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Category deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }
}

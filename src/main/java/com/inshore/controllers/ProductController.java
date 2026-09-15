package com.inshore.controllers;

import java.util.List;

import com.inshore.payload.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inshore.exceptions.UserException;
import com.inshore.models.User;
import com.inshore.payload.dto.ProductDTO;
import com.inshore.service.ProductService;
import com.inshore.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(
            @RequestHeader("Authorization") String jwt,
            @RequestBody ProductDTO productDTO
    ) throws UserException {
        User user = userService.getUserFromJwt(jwt);

        return ResponseEntity.ok(productService.createProduct(productDTO, user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long id,
            @RequestBody ProductDTO productDTO
    ) throws UserException {
        User user = userService.getUserFromJwt(jwt);

        return ResponseEntity.ok(productService.updateProduct(id, productDTO, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteProduct(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long id
    ) throws UserException {
        User user = userService.getUserFromJwt(jwt);

        productService.deleteProduct(id, user);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Product deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<ProductDTO>> getProductsByStoreId(
            @PathVariable Long storeId
    ) {
        return ResponseEntity.ok(productService.getProductsByStoreId(storeId));
    }

    @GetMapping("/store/{storeId}/search")
    public ResponseEntity<List<ProductDTO>> searchByKeyword(
            @PathVariable Long storeId,
            @RequestParam String keyword
    ) {
        return ResponseEntity.ok(productService.searchByKeyword(storeId, keyword));
    }
}

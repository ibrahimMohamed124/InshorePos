package com.inshore.service;

import com.inshore.models.User;
import com.inshore.payload.dto.ProductDTO;

import java.util.List;

public interface ProductService {
    ProductDTO createProduct(ProductDTO productDto, User user) throws Exception;
    ProductDTO updateProduct(Long id, ProductDTO productDto, User user);
    void deleteProduct(Long id, User user);
    List<ProductDTO> getProductsByStoreId(Long storeId);
    List<ProductDTO> searchByKeyword(Long storeId, String keyword);
}

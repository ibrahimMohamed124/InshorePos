package com.inshore.product.service;

import com.inshore.user.domain.User;
import com.inshore.product.dto.ProductDTO;

import java.util.List;

public interface ProductService {
    ProductDTO createProduct(ProductDTO productDto, User user) throws Exception;
    ProductDTO updateProduct(Long id, ProductDTO productDto, User user);
    void deleteProduct(Long id, User user);
    List<ProductDTO> getAllProducts();
    List<ProductDTO> getProductsByStoreId(Long storeId);
    List<ProductDTO> searchByKeyword(Long storeId, String keyword);
}

package com.inshore.product.mapper;

import com.inshore.category.domain.Category;
import com.inshore.product.domain.Product;
import com.inshore.store.domain.Store;
import com.inshore.product.dto.ProductDTO;

public class ProductMapper {

    public static ProductDTO toDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .sku(product.getSku())
                .description(product.getDescription())
                .mrp(product.getMrp())
                .sellingPrice(product.getSellingPrice())
                .brand(product.getBrand())
                .storeId(product.getStore() != null ? product.getStore().getId() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .image(product.getImage())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    public static Product toEntity(ProductDTO dto, Store store, Category category) {
        return Product.builder()
                .name(dto.getName())
                .store(store)
                .category(category)
                .sku(dto.getSku())
                .description(dto.getDescription())
                .mrp(dto.getMrp())
                .sellingPrice(dto.getSellingPrice())
                .brand(dto.getBrand())
                .image(dto.getImage())
                .store(store)
                .build();
    }
}
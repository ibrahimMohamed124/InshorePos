package com.inshore.category.mapper;

import com.inshore.category.domain.Category;
import com.inshore.store.domain.Store;
import com.inshore.category.dto.CategoryDTO;

public class CategoryMapper {

    public static CategoryDTO toDTO(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .storeId(category.getStore() != null ? category.getStore().getId() : null)
                .build();
    }

    public static Category toEntity(CategoryDTO dto, Store store) {
        return Category.builder()
                .name(dto.getName())
                .store(store)
                .build();
    }
}

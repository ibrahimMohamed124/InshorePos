package com.inshore.category.service;

import com.inshore.category.dto.CategoryDTO;

import java.util.List;

public interface CategoryService {

    CategoryDTO createCategory(CategoryDTO categoryDTO) throws Exception;
    CategoryDTO updateCategory(Long id, CategoryDTO categoryDTO);
    List<CategoryDTO> getCategoriesByStore(Long storeId);
    void delete(Long id) throws Exception;

}

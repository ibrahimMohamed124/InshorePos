package com.inshore.category.service.impl;

import com.inshore.user.domain.UserRole;
import com.inshore.category.mapper.CategoryMapper;
import com.inshore.category.domain.Category;
import com.inshore.store.domain.Store;
import com.inshore.user.domain.User;
import com.inshore.category.dto.CategoryDTO;
import com.inshore.category.repository.CategoryRepository;
import com.inshore.store.repository.StoreRepository;
import com.inshore.user.repository.UserRepository;
import com.inshore.category.service.CategoryService;
import com.inshore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public CategoryDTO createCategory(CategoryDTO categoryDTO) throws Exception {
        User user = userService.getCurrentUser();

        if (categoryDTO.getStoreId() == null) {
            throw new RuntimeException("storeId is required to create a category");
        }

        Store store = storeRepository.findById(
                categoryDTO.getStoreId()
        ).orElseThrow(
                () -> new RuntimeException("store not found")
        );

        Category category = CategoryMapper.toEntity(categoryDTO, store);

        checkAuthority(user, category.getStore());



        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    public CategoryDTO updateCategory(Long id, CategoryDTO categoryDTO) {
        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("category not found")
        );

        category.setName(categoryDTO.getName());

        if (categoryDTO.getStoreId() != null) {
            Store store = storeRepository.findById(
                    categoryDTO.getStoreId()
            ).orElseThrow(
                    () -> new RuntimeException("store not found")
            );
            category.setStore(store);
        }

        return CategoryMapper.toDTO(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDTO> getCategoriesByStore(Long storeId) {
        List<Category> categories = categoryRepository.findByStoreId(storeId);
        return categories.stream().map(CategoryMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public void delete(Long id) throws Exception {
        Category category = categoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("category not found")
        );

        User user = userService.getCurrentUser();

        checkAuthority(user, category.getStore());

        categoryRepository.delete(category);
    }

    private void checkAuthority(User user, Store store) throws Exception {
        boolean isAdmin = user.getRole().equals(UserRole.ROLE_STORE_ADMIN);
        boolean isManager = user.getRole().equals(UserRole.ROLE_STORE_MANAGER);
        boolean isSameStore = user.equals(store.getStoreAdmin());

        if (!(isAdmin && isSameStore) && !isManager) {
            throw new Exception("you don't have permission to manage this category");
        }
    }
}

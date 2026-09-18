package com.inshore.product.service.impl;

import com.inshore.product.mapper.ProductMapper;
import com.inshore.category.domain.Category;
import com.inshore.product.domain.Product;
import com.inshore.store.domain.Store;
import com.inshore.user.domain.User;
import com.inshore.product.dto.ProductDTO;
import com.inshore.category.repository.CategoryRepository;
import com.inshore.product.repository.ProductRepository;
import com.inshore.store.repository.StoreRepository;
import com.inshore.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;

    @Override
    public ProductDTO createProduct(ProductDTO productDto, User user) throws Exception {
        Store store = storeRepository.findById(
                productDto.getStoreId()
        ).orElseThrow(
                () -> new RuntimeException("store not found")
        );

        if (store.getStoreAdmin() == null || !store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can add products to this store");
        }

        Category category = categoryRepository.findById(productDto.getCategoryId()).orElseThrow(
                () -> new Exception("Category not found")
        );

        Product product = ProductMapper.toEntity(productDto, store, category);

        return ProductMapper.toDTO(productRepository.save(product));
    }

    @Override
    public ProductDTO updateProduct(Long id, ProductDTO productDto, User user) {
        Product product = productRepository.findById(id).orElseThrow(
                () -> new RuntimeException("product not found")
        );

        Store store = product.getStore();

        if (store == null || store.getStoreAdmin() == null || !store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can update this product");
        }

        product.setName(productDto.getName());
        product.setSku(productDto.getSku());
        product.setDescription(productDto.getDescription());
        product.setMrp(productDto.getMrp());
        product.setSellingPrice(productDto.getSellingPrice());
        product.setBrand(productDto.getBrand());
        product.setImage(productDto.getImage());

        if (productDto.getCategoryId() != null) {
            Category category = categoryRepository.findById(productDto.getCategoryId()).orElseThrow(
                    () -> new RuntimeException("Category not found")
            );
            product.setCategory(category);
        }

        return ProductMapper.toDTO(productRepository.save(product));
    }

    @Override
    public void deleteProduct(Long id, User user) {
        Product product = productRepository.findById(id).orElseThrow(
                () -> new RuntimeException("product not found")
        );

        Store store = product.getStore();

        if (store == null || store.getStoreAdmin() == null || !store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can delete this product");
        }

        productRepository.delete(product);
    }

    @Override
    public List<ProductDTO> getAllProducts() {
        return productRepository.findAll()
                .stream()
                .map(ProductMapper::toDTO)
                .toList();
    }

    @Override
    public List<ProductDTO> getProductsByStoreId(Long storeId) {
        List<Product> products = productRepository.findByStoreId(storeId);
        return products.stream().map(ProductMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public List<ProductDTO> searchByKeyword(Long storeId, String keyword) {
        List<Product> products = productRepository.searchByKeyword(storeId, keyword);
        return products.stream().map(ProductMapper::toDTO).collect(Collectors.toList());
    }
}

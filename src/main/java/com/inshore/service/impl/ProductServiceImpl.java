package com.inshore.service.impl;

import com.inshore.mapper.ProductMapper;
import com.inshore.models.Product;
import com.inshore.models.Store;
import com.inshore.models.User;
import com.inshore.payload.dto.ProductDTO;
import com.inshore.repository.ProductRepository;
import com.inshore.repository.StoreRepository;
import com.inshore.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;

    @Override
    public ProductDTO createProduct(ProductDTO productDto, User user) {
        Store store = storeRepository.findById(
                productDto.getStoreId()
        ).orElseThrow(
                () -> new RuntimeException("store not found")
        );

        if (store.getStoreAdmin() == null || !store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can add products to this store");
        }

        Product product = ProductMapper.toEntity(productDto, store);

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
        product.setCreatedAt(LocalDateTime.now());

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

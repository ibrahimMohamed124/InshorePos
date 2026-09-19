package com.inshore.product.repository;

import com.inshore.product.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // ProductMapper#toDTO reads both store and category (both @ManyToOne,
    // EAGER by default) - fetch them in the same query instead of one
    // extra SELECT each per product.
    @Query("""
            SELECT p FROM Product p
            LEFT JOIN FETCH p.store
            LEFT JOIN FETCH p.category
            WHERE p.store.id = :storeId
            """)
    List<Product> findByStoreId(@Param("storeId") Long storeId);

    @Query("""
        SELECT p
        FROM Product p
        LEFT JOIN FETCH p.store
        LEFT JOIN FETCH p.category
        WHERE p.store.id = :storeId
          AND (
              LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(p.brand) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%'))
          )
        """)
    List<Product> searchByKeyword(
            @Param("storeId") Long storeId,
            @Param("query") String keyword
    );
}

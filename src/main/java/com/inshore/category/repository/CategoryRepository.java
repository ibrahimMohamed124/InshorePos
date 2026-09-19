package com.inshore.category.repository;

import com.inshore.category.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // CategoryMapper#toDTO reads category.getStore() - fetch it up front.
    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.store WHERE c.store.id = :storeId")
    List<Category> findByStoreId(@Param("storeId") Long storeId);

}

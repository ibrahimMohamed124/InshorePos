package com.inshore.store.repository;

import java.util.List;
import java.util.UUID;

import com.inshore.store.domain.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @Query("""
            SELECT s FROM Store s
            LEFT JOIN FETCH s.storeAdmin sa
            LEFT JOIN FETCH sa.branch
            WHERE s.storeAdmin.id = :adminId
            """)
    Store findByStoreAdminId(@Param("adminId") UUID adminId);

    @Query("""
            SELECT DISTINCT s FROM Store s
            LEFT JOIN FETCH s.storeAdmin sa
            LEFT JOIN FETCH sa.branch
            """)
    List<Store> findAllWithDetails();

}

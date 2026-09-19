package com.inshore.branch.repository;

import com.inshore.branch.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    // BranchMapper#toDTO reads both store and manager (both @ManyToOne, EAGER
    // by default) - fetch them in the same query instead of one SELECT each.
    @Query("""
            SELECT b FROM Branch b
            LEFT JOIN FETCH b.store
            LEFT JOIN FETCH b.manager
            WHERE b.store.id = :storeId
            """)
    List<com.inshore.branch.domain.Branch> findByStoreId(@Param("storeId") Long storeId);

}

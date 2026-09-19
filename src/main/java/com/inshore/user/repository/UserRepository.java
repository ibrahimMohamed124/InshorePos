package com.inshore.user.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.inshore.user.domain.User;
import com.inshore.user.domain.UserRole;

public interface UserRepository extends JpaRepository<User, UUID> {

    // store and branch are @ManyToOne (fetch = EAGER by default), but without an
    // explicit JOIN FETCH Hibernate loads them via a *separate* SELECT right
    // after the main query, per row. UserMapper#toDTO always reads both, so
    // every one of these methods pulls them in with the main query instead.
    String USER_WITH_DETAILS = """
            SELECT u FROM User u
            LEFT JOIN FETCH u.store
            LEFT JOIN FETCH u.branch
            """;

    @Query(USER_WITH_DETAILS + "WHERE u.email = :email")
    User findByEmail(@Param("email") String email);

    @Query(USER_WITH_DETAILS + "WHERE u.store.id = :storeId")
    List<User> findByStoreId(@Param("storeId") Long storeId);

    @Query(USER_WITH_DETAILS + "WHERE u.store.id = :storeId AND u.role = :role")
    List<User> findByStoreIdAndRole(@Param("storeId") Long storeId, @Param("role") UserRole role);

    @Query(USER_WITH_DETAILS + "WHERE u.branch.id = :branchId")
    List<User> findByBranchId(@Param("branchId") Long branchId);

    @Query(USER_WITH_DETAILS + "WHERE u.branch.id = :branchId AND u.role = :role")
    List<User> findByBranchIdAndRole(@Param("branchId") Long branchId, @Param("role") UserRole role);

}

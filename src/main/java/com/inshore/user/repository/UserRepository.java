package com.inshore.user.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inshore.user.domain.UserRole;
import com.inshore.user.domain.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByEmail(String email);

    List<User> findByStoreId(Long storeId);

    List<User> findByStoreIdAndRole(Long storeId, UserRole role);

    List<User> findByBranchId(Long branchId);

    List<User> findByBranchIdAndRole(Long branchId, UserRole role);

}
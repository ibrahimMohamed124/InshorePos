package com.inshore.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.inshore.models.User;

public interface UserRepository extends JpaRepository<User, UUID> {
    User findByEmail(String email);

}

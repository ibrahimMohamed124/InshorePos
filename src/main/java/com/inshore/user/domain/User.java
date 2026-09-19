package com.inshore.user.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.inshore.branch.domain.Branch;

import com.inshore.store.domain.Store;
import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_store_id", columnList = "store_id"),
        @Index(name = "idx_users_branch_id", columnList = "branch_id"),
        @Index(name = "idx_users_store_role", columnList = "store_id, role"),
        @Index(name = "idx_users_branch_role", columnList = "branch_id, role")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @ManyToOne
    private Store store;

    @ManyToOne
    private Branch branch;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime lastLoginAt;
}

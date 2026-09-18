package com.inshore.user.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.inshore.user.domain.UserRole;

import lombok.Data;

@Data
public class UserDTO {

    private UUID id;

    private String username;

    private String email;

    // accepted on requests, never written back in responses
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String phone;

    private Long branchId;

    private Long storeId;

    private UserRole role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}
package com.inshore.payload.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.inshore.domain.UserRole;

import lombok.Data;

@Data 
public class UserDto {

    private UUID id;

    private String username;

    private String email;

    private String password;

    private String phone;

    private UserRole role;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
}

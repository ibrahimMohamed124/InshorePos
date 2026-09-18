package com.inshore.auth.dto;

import com.inshore.user.dto.UserDTO;

import lombok.Data;

@Data 
public class AuthResponse {
    private String jwt;
    private String message;
    private UserDTO user;
}

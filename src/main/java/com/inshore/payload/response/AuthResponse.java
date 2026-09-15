package com.inshore.payload.response;

import com.inshore.payload.dto.UserDTO;

import lombok.Data;

@Data 
public class AuthResponse {
    private String jwt;
    private String message;
    private UserDTO user;
}

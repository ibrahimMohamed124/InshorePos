package com.inshore.payload.response;

import com.inshore.payload.dto.UserDto;

import lombok.Data;

@Data 
public class AuthResponse {
    private String jwt;
    private String message;
    private UserDto user;
}

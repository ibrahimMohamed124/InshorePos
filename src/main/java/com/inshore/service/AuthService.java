package com.inshore.service;

import org.springframework.stereotype.Service;

import com.inshore.payload.dto.UserDto;
import com.inshore.payload.response.AuthResponse;

@Service 
public interface AuthService {
    AuthResponse signup(UserDto userDto);
    AuthResponse login(UserDto userDto);

}

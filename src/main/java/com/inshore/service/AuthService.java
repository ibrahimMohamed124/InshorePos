package com.inshore.service;

import org.springframework.stereotype.Service;

import com.inshore.payload.dto.UserDTO;
import com.inshore.payload.response.AuthResponse;

@Service 
public interface AuthService {
    AuthResponse signup(UserDTO userDto);
    AuthResponse login(UserDTO userDto);

}

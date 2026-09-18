package com.inshore.auth.service;

import org.springframework.stereotype.Service;

import com.inshore.user.dto.UserDTO;
import com.inshore.auth.dto.AuthResponse;

@Service 
public interface AuthService {
    AuthResponse signup(UserDTO userDto);
    AuthResponse login(UserDTO userDto);

}

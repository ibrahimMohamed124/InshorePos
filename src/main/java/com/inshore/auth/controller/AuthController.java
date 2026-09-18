package com.inshore.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inshore.user.dto.UserDTO;
import com.inshore.auth.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/auth")
@RequiredArgsConstructor 
public class AuthController {
    private final AuthService authService;


//  localhost:5000/auth/signup

    @PostMapping("/signup")
    public ResponseEntity<?> signupHandler(
        @RequestBody UserDTO userDto
    ) {
        return ResponseEntity.ok(
            authService.signup(userDto)
        );
    }

        @PostMapping("/login")
    public ResponseEntity<?> loginHandler(
        @RequestBody UserDTO userDto
    ) {
        return ResponseEntity.ok(
            authService.login(userDto)
        );
    }
}

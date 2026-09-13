package com.inshore.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inshore.payload.dto.UserDto;
import com.inshore.service.AuthService;

import lombok.RequiredArgsConstructor;

@RestController 
@RequestMapping ("/auth")
@RequiredArgsConstructor 
public class AuthController {
    private final AuthService authService;


//  localhost:5000/auth/signup

    @PostMapping("/signup")
    public ResponseEntity<?> signupHandler(
        @RequestBody UserDto userDto
    ) {
        return ResponseEntity.ok(
            authService.signup(userDto)
        );
    }

        @PostMapping("/login")
    public ResponseEntity<?> loginHandler(
        @RequestBody UserDto userDto
    ) {
        return ResponseEntity.ok(
            authService.login(userDto)
        );
    }
}

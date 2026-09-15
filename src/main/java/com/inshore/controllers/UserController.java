package com.inshore.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.inshore.exceptions.UserException;
import com.inshore.mapper.UserMapper;
import com.inshore.models.User;
import com.inshore.payload.dto.UserDTO;
import com.inshore.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController 
@RequestMapping("/api/users")
@RequiredArgsConstructor 
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<UserDTO> getUserProfile(
        @RequestHeader("Authorization") String jwt
    ) throws UserException {
        User user = userService.getUserFromJwt(jwt);

        return ResponseEntity.ok(UserMapper.toDTO(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUserById(
        @RequestHeader("Authorization") String jwt,
        @PathVariable UUID id
    ) throws UserException {
        User user = userService.getUserById(id);

        return ResponseEntity.ok(UserMapper.toDTO(user));
    }
}

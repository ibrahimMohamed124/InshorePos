package com.inshore.auth.service.impl;

import java.util.Collection;

import com.inshore.user.service.impl.CustomUserImplementation;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.inshore.shared.security.JwtProvider;
import com.inshore.user.domain.UserRole;
import com.inshore.user.exception.CannotCreateAdminUserException;
import com.inshore.user.exception.UserAlreadyExistsException;
import com.inshore.user.mapper.UserMapper;
import com.inshore.user.domain.User;
import com.inshore.user.dto.UserDTO;
import com.inshore.auth.dto.AuthResponse;
import com.inshore.user.repository.UserRepository;
import com.inshore.auth.service.AuthService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final CustomUserImplementation customUserImplementation;

    @Override
    public AuthResponse signup(UserDTO userDto) {
        User user = userRepository.findByEmail(userDto.getEmail());

        if (user != null) {
            throw new UserAlreadyExistsException("User already exists");
        }

        if (userDto.getRole().equals(UserRole.ROLE_ADMIN)) {
            throw new CannotCreateAdminUserException("Cannot create admin user");
        }

        User newUser = new User();
        newUser.setUsername(userDto.getUsername());
        newUser.setEmail(userDto.getEmail());
        newUser.setPassword(passwordEncoder.encode(userDto.getPassword()));
        newUser.setPhone(userDto.getPhone());
        newUser.setRole(userDto.getRole());
        newUser.setCreatedAt(java.time.LocalDateTime.now());
        newUser.setUpdatedAt(java.time.LocalDateTime.now());
        newUser.setLastLoginAt(java.time.LocalDateTime.now());

        User savedUser = userRepository.save(newUser);

        Authentication authentication = new UsernamePasswordAuthenticationToken(savedUser.getEmail(),
                savedUser.getPassword());

        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = jwtProvider.generateToken(authentication);

        AuthResponse authResponse = new AuthResponse();

        authResponse.setJwt(jwt);
        authResponse.setMessage("User registered successfully");
        authResponse.setUser(UserMapper.toDTO(savedUser));

        return authResponse;
    }

    @Override
    public AuthResponse login(UserDTO userDto) {
        String email = userDto.getEmail();
        String password = userDto.getPassword();
        Authentication authentication = authenticate(email, password);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        String role = authorities.iterator().next().getAuthority();

        String jwt = jwtProvider.generateToken(authentication);

        User user = userRepository.findByEmail(email);

        if (user != null) {
            user.setLastLoginAt(java.time.LocalDateTime.now());
            userRepository.save(user);
        }

        AuthResponse authResponse = new AuthResponse();

        authResponse.setJwt(jwt);
        authResponse.setMessage("Logged in successfully");
        authResponse.setUser(UserMapper.toDTO(user));

        return authResponse;
    }

    private Authentication authenticate(String email, String password) {

        UserDetails userDetails = customUserImplementation.loadUserByUsername(email);

        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails.getUsername(),
                null,
                userDetails.getAuthorities());
    }

}   

package com.inshore.user.service;

import java.util.List;
import java.util.UUID;

import com.inshore.user.domain.User;

public interface UserService {
    User getUserFromJwt(String token);
    User getUserByEmail(String email);
    User getUserById(UUID id);
    User getCurrentUser();
    List<User> getAllUsers();   
}

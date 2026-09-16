package com.inshore.mapper;

import com.inshore.models.User;
import com.inshore.payload.dto.UserDTO;

public class UserMapper {

    public static UserDTO toDTO(User savedUser) {
        if (savedUser == null) {
            return null;
        }

        UserDTO userDto = new UserDTO();
        userDto.setId(savedUser.getId());
        userDto.setUsername(savedUser.getUsername());
        userDto.setEmail(savedUser.getEmail());
        userDto.setPhone(savedUser.getPhone());
        userDto.setRole(savedUser.getRole());

        // flatten the relations to ids so no entity graph leaks into the response
        userDto.setStoreId(savedUser.getStore() != null ? savedUser.getStore().getId() : null);
        userDto.setBranchId(savedUser.getBranch() != null ? savedUser.getBranch().getId() : null);

        userDto.setCreatedAt(savedUser.getCreatedAt());
        userDto.setUpdatedAt(savedUser.getUpdatedAt());
        userDto.setLastLoginAt(savedUser.getLastLoginAt());
        return userDto;
    }

    public static User toEntity(UserDTO userDto) {
        if (userDto == null) {
            return null;
        }

        User user = new User();
        user.setId(userDto.getId());
        user.setUsername(userDto.getUsername());
        user.setEmail(userDto.getEmail());
        user.setPhone(userDto.getPhone());
        user.setRole(userDto.getRole());
        user.setCreatedAt(userDto.getCreatedAt());
        user.setUpdatedAt(userDto.getUpdatedAt());
        user.setLastLoginAt(userDto.getLastLoginAt());
        return user;
    }
}
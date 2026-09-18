package com.inshore.store.dto;

import com.inshore.store.domain.StoreStatus;
import com.inshore.store.domain.StoreContact;
import com.inshore.user.dto.UserDTO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StoreDTO {

    private Long id;

    private String brand;

    private UserDTO storeAdmin;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String description;

    private String storeType;

    private StoreStatus status;

    private StoreContact contact;
}

package com.inshore.store.mapper;

import com.inshore.store.domain.Store;
import com.inshore.user.domain.User;
import com.inshore.store.dto.StoreDTO;
import com.inshore.user.mapper.UserMapper;

public class StoreMapper {

    public static StoreDTO toDTO(Store store) {
        StoreDTO storeDTO = new StoreDTO();

        storeDTO.setId(store.getId());
        storeDTO.setStoreAdmin(UserMapper.toDTO(store.getStoreAdmin()));
        storeDTO.setStoreType(store.getStoreType());
        storeDTO.setBrand(store.getBrand());
        storeDTO.setContact(store.getContact());
        storeDTO.setStatus(store.getStatus());
        storeDTO.setDescription(store.getDescription());
        storeDTO.setCreatedAt(store.getCreatedAt());
        storeDTO.setUpdatedAt(store.getUpdatedAt());

        return storeDTO;
    }

    public static Store toEntity(StoreDTO storeDTO, User storeAdmin) {
        Store store = new Store();

        store.setId(storeDTO.getId());
        store.setBrand(storeDTO.getBrand());
        store.setDescription(storeDTO.getDescription());
        store.setStoreAdmin(storeAdmin);
        store.setStoreType(storeDTO.getStoreType());
        store.setContact(storeDTO.getContact());
        store.setCreatedAt(storeDTO.getCreatedAt());
        store.setUpdatedAt(storeDTO.getUpdatedAt());

        return store;
    }
}

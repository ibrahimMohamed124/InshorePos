package com.inshore.store.service;

import com.inshore.store.domain.StoreStatus;
import com.inshore.store.domain.Store;
import com.inshore.user.domain.User;
import com.inshore.store.dto.StoreDTO;

import java.util.List;

public interface StoreService {
    StoreDTO createStore(StoreDTO storeDTO, User user) throws RuntimeException;
    StoreDTO getStoreById(Long id) throws RuntimeException;
    List<StoreDTO> getAllStores() throws RuntimeException;
    Store getStoreByAdmin() throws RuntimeException;
    StoreDTO updateStore(Long id, StoreDTO storeDTO, User user) throws RuntimeException;
    void deleteStore(Long id) throws RuntimeException;
    StoreDTO getStoreByEmployee() throws RuntimeException;
    StoreDTO moderateStore(Long id, StoreStatus status) throws  RuntimeException;
}

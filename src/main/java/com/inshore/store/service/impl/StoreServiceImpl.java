package com.inshore.store.service.impl;

import com.inshore.store.domain.StoreStatus;
import com.inshore.store.mapper.StoreMapper;
import com.inshore.store.domain.Store;
import com.inshore.store.domain.StoreContact;
import com.inshore.user.domain.User;
import com.inshore.store.dto.StoreDTO;
import com.inshore.store.repository.StoreRepository;
import com.inshore.user.repository.UserRepository;
import com.inshore.store.service.StoreService;
import com.inshore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreRepository storeRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    @Override
    public StoreDTO createStore(StoreDTO storeDTO, User user) throws RuntimeException{
        Store store = StoreMapper.toEntity(storeDTO, user);

        return StoreMapper.toDTO(storeRepository.save(store));
    }

    @Override
    public StoreDTO getStoreById(Long id) throws RuntimeException{
        Store store = storeRepository.findById(id).orElseThrow(
                () -> new RuntimeException("store not found...")
        );
        return StoreMapper.toDTO(store);
    }

    @Override
    public List<StoreDTO> getAllStores() throws RuntimeException{
        List<Store> stores = storeRepository.findAll();
        return stores.stream().map(StoreMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public Store getStoreByAdmin() throws RuntimeException{
        User admin = userService.getCurrentUser();
        return storeRepository.findByStoreAdminId(admin.getId());
    }

    @Override
    public StoreDTO updateStore(Long id, StoreDTO storeDTO, User user) throws RuntimeException{
        User currentUser = userService.getCurrentUser();
        Store store = storeRepository.findByStoreAdminId(currentUser.getId());

         if (!store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can update this store...");
        }

        store.setBrand(storeDTO.getBrand());
        store.setDescription(storeDTO.getDescription());
        store.setStoreType(storeDTO.getStoreType());
        store.setContact(storeDTO.getContact());

        if (storeDTO.getStatus() != null) {
            store.setStatus(storeDTO.getStatus());
        }

        if(storeDTO.getContact() != null) {
            StoreContact contact = StoreContact.builder()
                    .address(storeDTO.getContact().getAddress())
                    .phone(storeDTO.getContact().getPhone())
                    .email(storeDTO.getContact().getEmail())
                    .build();

            store.setContact(contact);

        }

        return StoreMapper.toDTO(storeRepository.save(store));
    }

    @Override
    public void deleteStore(Long id) throws RuntimeException{
        Store store = getStoreByAdmin();

        storeRepository.delete(store);

    }

    @Override
    public StoreDTO getStoreByEmployee() throws RuntimeException{
        User currentUser= userService.getCurrentUser();

        if (currentUser == null) {
            throw new RuntimeException("you don't have permission to acces this store");
        }

        return  StoreMapper.toDTO(currentUser.getStore());
    }

    @Override
    public StoreDTO moderateStore(Long id, StoreStatus status) throws RuntimeException {
        Store store = storeRepository.findById(id).orElseThrow(
                () -> new RuntimeException("store not found")
        );

        store.setStatus(status);
        Store updatedStore = storeRepository.save(store);

        return StoreMapper.toDTO(updatedStore);
    }
}

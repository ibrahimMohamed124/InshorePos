package com.inshore.store.controller;

import java.util.List;

import com.inshore.store.domain.StoreStatus;
import com.inshore.store.mapper.StoreMapper;
import com.inshore.store.domain.Store;
import com.inshore.shared.web.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inshore.shared.exception.UserException;
import com.inshore.user.domain.User;
import com.inshore.store.dto.StoreDTO;
import com.inshore.store.service.StoreService;
import com.inshore.user.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<StoreDTO> createStore(
            @RequestHeader("Authorization") String jwt,
            @RequestBody StoreDTO storeDTO
    ) throws UserException {
        User user = userService.getUserFromJwt(jwt);

        return ResponseEntity.ok(storeService.createStore(storeDTO, user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoreDTO> getStoreById(
            @PathVariable Long id,
            @RequestHeader("Authorization") String jwt
    ) throws UserException {
        return ResponseEntity.ok(storeService.getStoreById(id));
    }

    @GetMapping
    public ResponseEntity<List<StoreDTO>> getAllStores(
            @RequestHeader("Authorization") String jwt
    ) throws UserException {
        return ResponseEntity.ok(storeService.getAllStores());
    }

    @GetMapping("/admin")
    public ResponseEntity<StoreDTO> getStoreByAdmin(@RequestHeader("Authorization") String jwt) throws UserException {
        return ResponseEntity.ok(StoreMapper.toDTO(storeService.getStoreByAdmin()));
    }

    @GetMapping("/employee")
    public ResponseEntity<StoreDTO> getStoreByEmployee() throws UserException {
        return ResponseEntity.ok(storeService.getStoreByEmployee());
    }

    @PutMapping("/{id}")
    public ResponseEntity<StoreDTO> updateStore(
            @RequestHeader("Authorization") String jwt,
            @PathVariable Long id,
            @RequestBody StoreDTO storeDTO
    ) throws UserException {
        User user = userService.getUserFromJwt(jwt);

        return ResponseEntity.ok(storeService.updateStore(id, storeDTO, user));
    }

    @PutMapping("/{id}/moderate")
    public ResponseEntity<StoreDTO> moderateStore(
            @RequestParam StoreStatus status,
            @PathVariable Long id
            ) {
        return ResponseEntity.ok((storeService.moderateStore(id, status)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteStore(
            @PathVariable Long id,
            @RequestHeader("Authorization") String jwt
    ) throws UserException {
        storeService.deleteStore(id);
        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Store deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }
}

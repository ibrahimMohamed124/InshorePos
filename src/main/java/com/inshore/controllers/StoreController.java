package com.inshore.controllers;

import java.util.List;

import com.inshore.domain.StoreStatus;
import com.inshore.mapper.StoreMapper;
import com.inshore.models.Store;
import com.inshore.payload.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inshore.exceptions.UserException;
import com.inshore.models.User;
import com.inshore.payload.dto.StoreDTO;
import com.inshore.service.StoreService;
import com.inshore.service.UserService;

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

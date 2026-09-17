package com.inshore.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.inshore.domain.UserRole;
import com.inshore.payload.dto.UserDTO;
import com.inshore.payload.response.ApiResponse;
import com.inshore.service.EmployeeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    @PostMapping("/store/{storeId}")
    public ResponseEntity<UserDTO> createStoreEmployee(
            @PathVariable Long storeId,
            @RequestBody UserDTO employee
    ) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createStoreEmployee(employee, storeId));
    }

    @PostMapping("/branch/{branchId}")
    public ResponseEntity<UserDTO> createBranchEmployee(
            @PathVariable Long branchId,
            @RequestBody UserDTO employee
    ) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.createBranchEmployee(employee, branchId));
    }

    @PutMapping("/{employeeId}")
    public ResponseEntity<UserDTO> updateEmployee(
            @PathVariable UUID employeeId,
            @RequestBody UserDTO employeeDetails
    ) throws Exception {
        return ResponseEntity.ok(employeeService.updateEmployee(employeeId, employeeDetails));
    }

    @DeleteMapping("/{employeeId}")
    public ResponseEntity<ApiResponse> deleteEmployee(
            @PathVariable UUID employeeId
    ) throws Exception {
        employeeService.deleteEmployee(employeeId);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setMessage("Employee deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<UserDTO>> findStoreEmployees(
            @PathVariable Long storeId,
            @RequestParam(required = false) UserRole role
    ) {
        return ResponseEntity.ok(employeeService.findStoreEmployees(storeId, role));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<UserDTO>> findBranchEmployees(
            @PathVariable Long branchId,
            @RequestParam(required = false) UserRole role
    ) {
        return ResponseEntity.ok(employeeService.findBranchEmployees(branchId, role));
    }
}

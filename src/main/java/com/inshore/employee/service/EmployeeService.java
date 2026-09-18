package com.inshore.employee.service;

import com.inshore.user.domain.UserRole;
import com.inshore.user.dto.UserDTO;

import java.util.List;
import java.util.UUID;

public interface EmployeeService {

    UserDTO createStoreEmployee(UserDTO employee, Long storeId) throws Exception;

    UserDTO createBranchEmployee(UserDTO employee, Long branchId) throws Exception;

    UserDTO updateEmployee(UUID employeeId, UserDTO employeeDetails);

    void deleteEmployee(UUID employeeId);

    // role is optional: null means "all employees of this store"
    List<UserDTO> findStoreEmployees(Long storeId, UserRole role);

    // role is optional: null means "all employees of this branch"
    List<UserDTO> findBranchEmployees(Long branchId, UserRole role);
}
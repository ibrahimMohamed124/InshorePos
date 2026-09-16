package com.inshore.service.impl;

import com.inshore.domain.UserRole;
import com.inshore.exceptions.UserAlreadyExistsException;
import com.inshore.exceptions.UserException;
import com.inshore.mapper.UserMapper;
import com.inshore.models.Branch;
import com.inshore.models.Store;
import com.inshore.models.User;
import com.inshore.payload.dto.UserDTO;
import com.inshore.repository.BranchRepository;
import com.inshore.repository.StoreRepository;
import com.inshore.repository.UserRepository;
import com.inshore.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;

    /** Roles that may be created directly on a store. */
    private static final Set<UserRole> STORE_LEVEL_ROLES =
            EnumSet.of(UserRole.ROLE_STORE_MANAGER);

    /** Roles that may be created on a branch. */
    private static final Set<UserRole> BRANCH_LEVEL_ROLES =
            EnumSet.of(UserRole.ROLE_BRANCH_MANAGER, UserRole.ROLE_CASHIER);

    /**
     * Every role this API is allowed to touch. ROLE_ADMIN, ROLE_STORE_ADMIN and
     * ROLE_USER are deliberately out: they are not employees and are created
     * elsewhere (signup / store creation).
     */
    private static final Set<UserRole> EMPLOYEE_ROLES =
            EnumSet.of(UserRole.ROLE_STORE_MANAGER, UserRole.ROLE_BRANCH_MANAGER, UserRole.ROLE_CASHIER);

    @Override
    @Transactional
    public UserDTO createStoreEmployee(UserDTO employee, Long storeId) throws Exception {
        Store store = storeRepository.findById(storeId).orElseThrow(
                () -> new UserException("store not found with id " + storeId)
        );

        validateRole(employee.getRole(), STORE_LEVEL_ROLES, "store");

        User newEmployee = buildEmployee(employee);
        newEmployee.setStore(store);

        User savedEmployee = userRepository.save(newEmployee);

        return UserMapper.toDTO(savedEmployee);
    }

    @Override
    @Transactional
    public UserDTO createBranchEmployee(UserDTO employee, Long branchId) throws Exception {
        Branch branch = branchRepository.findById(branchId).orElseThrow(
                () -> new UserException("branch not found with id " + branchId)
        );

        if (branch.getStore() == null) {
            throw new UserException("this branch is not linked to any store");
        }

        validateRole(employee.getRole(), BRANCH_LEVEL_ROLES, "branch");

        // a branch can only have one manager at a time
        if (employee.getRole() == UserRole.ROLE_BRANCH_MANAGER && branch.getManager() != null) {
            throw new UserException(
                    "branch " + branchId + " already has a manager. remove or replace the current one first");
        }

        User newEmployee = buildEmployee(employee);
        newEmployee.setBranch(branch);
        // a branch employee always belongs to the branch's store as well
        newEmployee.setStore(branch.getStore());

        User savedEmployee = userRepository.save(newEmployee);

        // a branch manager is also registered as the manager of that branch
        if (savedEmployee.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            branch.setManager(savedEmployee);
            branchRepository.save(branch);
        }

        return UserMapper.toDTO(savedEmployee);
    }

    @Override
    @Transactional
    public UserDTO updateEmployee(UUID employeeId, UserDTO employeeDetails) {
        User employee = userRepository.findById(employeeId).orElseThrow(
                () -> new UserException("employee not found with id " + employeeId)
        );

        // null means "don't touch this field"; blank means someone is trying to
        // wipe a NOT NULL column
        if (employeeDetails.getUsername() != null) {
            if (employeeDetails.getUsername().isBlank()) {
                throw new UserException("username cannot be empty");
            }
            employee.setUsername(employeeDetails.getUsername());
        }

        if (employeeDetails.getPhone() != null) {
            if (employeeDetails.getPhone().isBlank()) {
                throw new UserException("phone cannot be empty");
            }
            employee.setPhone(employeeDetails.getPhone());
        }

        // email can change, but it must stay unique
        if (employeeDetails.getEmail() != null
                && !employeeDetails.getEmail().equalsIgnoreCase(employee.getEmail())) {
            User existing = userRepository.findByEmail(employeeDetails.getEmail());
            if (existing != null && !existing.getId().equals(employeeId)) {
                throw new UserAlreadyExistsException(
                        "email " + employeeDetails.getEmail() + " is already used by another user");
            }
            employee.setEmail(employeeDetails.getEmail());
        }

        if (employeeDetails.getPassword() != null && !employeeDetails.getPassword().isBlank()) {
            employee.setPassword(passwordEncoder.encode(employeeDetails.getPassword()));
        }

        // resolve the target branch first, so the role can be validated against the
        // level the employee will actually end up on
        Branch targetBranch = employee.getBranch();
        boolean branchChanged = false;

        if (employeeDetails.getBranchId() != null
                && (employee.getBranch() == null
                || !employeeDetails.getBranchId().equals(employee.getBranch().getId()))) {

            targetBranch = branchRepository.findById(employeeDetails.getBranchId()).orElseThrow(
                    () -> new UserException("branch not found with id " + employeeDetails.getBranchId())
            );

            if (targetBranch.getStore() == null) {
                throw new UserException("this branch is not linked to any store");
            }

            branchChanged = true;
        }

        UserRole targetRole = employeeDetails.getRole() != null
                ? employeeDetails.getRole()
                : employee.getRole();

        // same rule as on create: the role has to match the level
        validateRole(targetRole,
                targetBranch != null ? BRANCH_LEVEL_ROLES : STORE_LEVEL_ROLES,
                targetBranch != null ? "branch" : "store");

        // can't take over a branch that already has a different manager
        if (targetRole == UserRole.ROLE_BRANCH_MANAGER
                && targetBranch != null
                && targetBranch.getManager() != null
                && !targetBranch.getManager().getId().equals(employeeId)) {
            throw new UserException("branch " + targetBranch.getId() + " already has a manager");
        }

        employee.setRole(targetRole);

        if (branchChanged) {
            detachFromCurrentBranch(employee);
            employee.setBranch(targetBranch);
            employee.setStore(targetBranch.getStore());
        }

        employee.setUpdatedAt(LocalDateTime.now());

        User updated = userRepository.save(employee);

        // keep the branch manager link in sync with the employee's current role
        Branch currentBranch = updated.getBranch();
        if (currentBranch != null) {
            if (updated.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
                currentBranch.setManager(updated);
                branchRepository.save(currentBranch);
            } else if (currentBranch.getManager() != null
                    && currentBranch.getManager().getId().equals(updated.getId())) {
                currentBranch.setManager(null);
                branchRepository.save(currentBranch);
            }
        }

        return UserMapper.toDTO(updated);
    }

    @Override
    @Transactional
    public void deleteEmployee(UUID employeeId) {
        User employee = userRepository.findById(employeeId).orElseThrow(
                () -> new UserException("employee not found with id " + employeeId)
        );

        if (!EMPLOYEE_ROLES.contains(employee.getRole())) {
            throw new UserException(
                    "user " + employeeId + " has role " + employee.getRole()
                            + " and is not an employee, so it cannot be deleted through this API");
        }

        if (employee.getStore() == null) {
            throw new UserException("user " + employeeId + " is not attached to any store");
        }

        detachFromCurrentBranch(employee);

        userRepository.delete(employee);
    }

    @Override
    public List<UserDTO> findStoreEmployees(Long storeId, UserRole role) {
        if (!storeRepository.existsById(storeId)) {
            throw new UserException("store not found with id " + storeId);
        }

        validateRoleFilter(role);

        List<User> employees = (role == null)
                ? userRepository.findByStoreId(storeId)
                : userRepository.findByStoreIdAndRole(storeId, role);

        return employees.stream()
                // a store row can also hold non-employees; never leak them here
                .filter(user -> EMPLOYEE_ROLES.contains(user.getRole()))
                .map(UserMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserDTO> findBranchEmployees(Long branchId, UserRole role) {
        if (!branchRepository.existsById(branchId)) {
            throw new UserException("branch not found with id " + branchId);
        }

        validateRoleFilter(role);

        List<User> employees = (role == null)
                ? userRepository.findByBranchId(branchId)
                : userRepository.findByBranchIdAndRole(branchId, role);

        return employees.stream()
                .filter(user -> EMPLOYEE_ROLES.contains(user.getRole()))
                .map(UserMapper::toDTO)
                .collect(Collectors.toList());
    }

    private User buildEmployee(UserDTO employee) throws Exception {
        if (employee.getUsername() == null || employee.getUsername().isBlank()) {
            throw new UserException("username is required");
        }

        if (employee.getEmail() == null || employee.getEmail().isBlank()) {
            throw new UserException("email is required");
        }

        if (employee.getPhone() == null || employee.getPhone().isBlank()) {
            throw new UserException("phone is required");
        }

        if (employee.getPassword() == null || employee.getPassword().isBlank()) {
            throw new UserException("password is required");
        }

        if (userRepository.findByEmail(employee.getEmail()) != null) {
            throw new UserAlreadyExistsException("User already exists");
        }

        User newEmployee = new User();
        newEmployee.setUsername(employee.getUsername());
        newEmployee.setEmail(employee.getEmail());
        newEmployee.setPassword(passwordEncoder.encode(employee.getPassword()));
        newEmployee.setPhone(employee.getPhone());
        newEmployee.setRole(employee.getRole());
        newEmployee.setCreatedAt(LocalDateTime.now());
        newEmployee.setUpdatedAt(LocalDateTime.now());

        return newEmployee;
    }

    private void validateRole(UserRole role, Set<UserRole> allowed, String level) {
        if (role == null) {
            throw new UserException("role is required");
        }

        if (!allowed.contains(role)) {
            throw new UserException(
                    "invalid role for a " + level + " employee: " + role + ". allowed roles are " + allowed);
        }
    }

    private void validateRoleFilter(UserRole role) {
        if (role != null && !EMPLOYEE_ROLES.contains(role)) {
            throw new UserException(
                    role + " is not an employee role. filter by one of " + EMPLOYEE_ROLES);
        }
    }

    private void detachFromCurrentBranch(User employee) {
        Branch oldBranch = employee.getBranch();
        if (oldBranch != null
                && oldBranch.getManager() != null
                && oldBranch.getManager().getId().equals(employee.getId())) {
            oldBranch.setManager(null);
            branchRepository.save(oldBranch);
        }
    }
}
package com.inshore.service.impl;

import com.inshore.mapper.BranchMapper;
import com.inshore.models.Branch;
import com.inshore.models.Store;
import com.inshore.models.User;
import com.inshore.payload.dto.BranchDTO;
import com.inshore.repository.BranchRepository;
import com.inshore.repository.StoreRepository;
import com.inshore.repository.UserRepository;
import com.inshore.service.BranchService;
import com.inshore.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public BranchDTO createBranch(BranchDTO branchDTO, User user) {
        User currentUser = userService.getCurrentUser();
        Store store = storeRepository.findByStoreAdminId(currentUser.getId());

        Branch branch = BranchMapper.toEntity(branchDTO, store);
        Branch savedBranch = branchRepository.save(branch);
        return BranchMapper.toDTO(savedBranch);
    }

    @Override
    public BranchDTO updateBranch(Long id, BranchDTO branchDTO, User user) {
        Branch branch = branchRepository.findById(id).orElseThrow(
                () -> new RuntimeException("branch not found")
        );

        Store store = branch.getStore();

        if (store == null || store.getStoreAdmin() == null || !store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can update this branch");
        }

        branch.setName(branchDTO.getName());
        branch.setAddress(branchDTO.getAddress());
        branch.setPhone(branchDTO.getPhone());
        branch.setEmail(branchDTO.getEmail());
        branch.setWorkingDays(branchDTO.getWorkingDays());
        branch.setOpenTime(branchDTO.getOpenTime());
        branch.setCloseTime(branchDTO.getCloseTime());

        if (branchDTO.getManagerId() != null) {
            User manager = userRepository.findById(branchDTO.getManagerId()).orElseThrow(
                    () -> new RuntimeException("manager not found")
            );
            branch.setManager(manager);
        }

        Branch updatedbranch = branchRepository.save(branch);

        return BranchMapper.toDTO(updatedbranch);
    }

    @Override
    public void deleteBranch(Long id, User user) {
        Branch branch = branchRepository.findById(id).orElseThrow(
                () -> new RuntimeException("branch not found")
        );

        Store store = branch.getStore();

        if (store == null || store.getStoreAdmin() == null || !store.getStoreAdmin().getId().equals(user.getId())) {
            throw new RuntimeException("only the store admin can delete this branch");
        }

        branchRepository.delete(branch);
    }

    @Override
    public List<BranchDTO> getAllBranchesByStoreId(Long storeId) {
        List<Branch> branches = branchRepository.findByStoreId(storeId);
        return branches.stream().map(BranchMapper::toDTO).collect(Collectors.toList());
    }

    @Override
    public BranchDTO getBranchById(Long id) {
        Branch branch = branchRepository.findById(id).orElseThrow(
                () -> new RuntimeException("branch not found")
        );

        return BranchMapper.toDTO(branch);
    }
}

package com.inshore.branch.service;

import com.inshore.user.domain.User;
import com.inshore.branch.dto.BranchDTO;

import java.util.List;

public interface BranchService {
    BranchDTO createBranch(BranchDTO branchDTO, User user);
    BranchDTO updateBranch(Long id ,BranchDTO branchDTO, User user);
    void deleteBranch(Long id, User user);
    List<BranchDTO> getAllBranchesByStoreId(Long storeId);
    BranchDTO getBranchById(Long id);
}

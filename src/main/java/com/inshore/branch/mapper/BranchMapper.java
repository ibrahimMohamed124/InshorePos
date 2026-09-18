package com.inshore.branch.mapper;

import com.inshore.branch.domain.Branch;
import com.inshore.store.domain.Store;
import com.inshore.branch.dto.BranchDTO;

public class BranchMapper {

    public static BranchDTO toDTO(Branch branch) {
        return BranchDTO.builder()
                .id(branch.getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .phone(branch.getPhone())
                .email(branch.getEmail())
                .openTime(branch.getOpenTime())
                .closeTime(branch.getCloseTime())
                .workingDays(branch.getWorkingDays())
                .storeId(branch.getStore() != null ? branch.getStore().getId() : null)
                .managerId(branch.getManager() != null ? branch.getManager().getId() : null)
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    public static Branch toEntity(BranchDTO branchDTO, Store store) {
        return Branch.builder()
                .name(branchDTO.getName())
                .address(branchDTO.getAddress())
                .store(store)
                .email(branchDTO.getEmail())
                .phone(branchDTO.getPhone())
                .openTime(branchDTO.getOpenTime())
                .closeTime(branchDTO.getCloseTime())
                .workingDays(branchDTO.getWorkingDays())
                .build();
    }
}
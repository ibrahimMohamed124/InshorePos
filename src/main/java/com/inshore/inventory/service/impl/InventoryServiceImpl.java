package com.inshore.inventory.service.impl;

import com.inshore.user.domain.UserRole;
import com.inshore.inventory.mapper.InventoryMapper;
import com.inshore.branch.domain.Branch;
import com.inshore.inventory.domain.Inventory;
import com.inshore.product.domain.Product;
import com.inshore.user.domain.User;
import com.inshore.inventory.dto.InventoryDTO;
import com.inshore.branch.repository.BranchRepository;
import com.inshore.inventory.repository.InventoryRepository;
import com.inshore.product.repository.ProductRepository;
import com.inshore.inventory.service.InventoryService;
import com.inshore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final BranchRepository branchRepository;
    private final ProductRepository productRepository;
    private final UserService userService;

    @Override
    public InventoryDTO createInventory(InventoryDTO inventoryDTO) {
        Branch branch = branchRepository.findById(inventoryDTO.getBranchId()).orElseThrow(
                () -> new RuntimeException("branch not found")
        );

        Product product = productRepository.findById(inventoryDTO.getProductId()).orElseThrow(
                () -> new RuntimeException("product not found")
        );

        User user = userService.getCurrentUser();
        checkAuthority(user, branch);

        if (product.getStore() == null || branch.getStore() == null
                || !product.getStore().getId().equals(branch.getStore().getId())) {
            throw new RuntimeException("product does not belong to this branch's store");
        }

        Inventory existing = inventoryRepository.findByProductIdAndBranchId(product.getId(), branch.getId());
        if (existing != null) {
            throw new RuntimeException("inventory record already exists for this product in this branch");
        }

        Inventory inventory = InventoryMapper.toEntity(inventoryDTO, branch, product);

        return InventoryMapper.toDTO(inventoryRepository.save(inventory));
    }

    @Override
    public InventoryDTO updateInventory(Long id, InventoryDTO inventoryDTO) {
        Inventory inventory = inventoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("inventory not found")
        );

        User user = userService.getCurrentUser();
        checkAuthority(user, inventory.getBranch());

        if (inventoryDTO.getQuantity() != null) {
            inventory.setQuantity(inventoryDTO.getQuantity());
        }

        if (inventoryDTO.getProductId() != null
                && !inventoryDTO.getProductId().equals(inventory.getProduct().getId())) {
            Product product = productRepository.findById(inventoryDTO.getProductId()).orElseThrow(
                    () -> new RuntimeException("product not found")
            );
            inventory.setProduct(product);
        }

        if (inventoryDTO.getBranchId() != null
                && !inventoryDTO.getBranchId().equals(inventory.getBranch().getId())) {
            Branch newBranch = branchRepository.findById(inventoryDTO.getBranchId()).orElseThrow(
                    () -> new RuntimeException("branch not found")
            );
            checkAuthority(user, newBranch);
            inventory.setBranch(newBranch);
        }

        return InventoryMapper.toDTO(inventoryRepository.save(inventory));
    }

    @Override
    public void deleteInventory(Long id) {
        Inventory inventory = inventoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("inventory not found")
        );

        User user = userService.getCurrentUser();
        checkAuthority(user, inventory.getBranch());

        inventoryRepository.delete(inventory);
    }

    @Override
    public InventoryDTO getInventoryById(Long id) {
        Inventory inventory = inventoryRepository.findById(id).orElseThrow(
                () -> new RuntimeException("inventory not found")
        );

        return InventoryMapper.toDTO(inventory);
    }

    @Override
    public InventoryDTO getInventoryByProductIdAndBranchId(Long productId, Long branchId) {
        Inventory inventory = inventoryRepository.findByProductIdAndBranchId(productId, branchId);

        if (inventory == null) {
            throw new RuntimeException("inventory not found");
        }

        return InventoryMapper.toDTO(inventory);
    }

    @Override
    public List<InventoryDTO> getAllInventoryByBranchId(Long branchId) {
        List<Inventory> inventories = inventoryRepository.findByBranchId(branchId);
        return inventories.stream().map(InventoryMapper::toDTO).collect(Collectors.toList());
    }

    private void checkAuthority(User user, Branch branch) {
        boolean isStoreAdmin = user.getRole().equals(UserRole.ROLE_STORE_ADMIN)
                && branch.getStore() != null
                && branch.getStore().getStoreAdmin() != null
                && branch.getStore().getStoreAdmin().getId().equals(user.getId());

        boolean isStoreManager = user.getRole().equals(UserRole.ROLE_STORE_MANAGER)
                && branch.getStore() != null
                && user.getStore() != null
                && user.getStore().getId().equals(branch.getStore().getId());

        boolean isBranchManager = user.getRole().equals(UserRole.ROLE_BRANCH_MANAGER)
                && branch.getManager() != null
                && branch.getManager().getId().equals(user.getId());

        if (!isStoreAdmin && !isStoreManager && !isBranchManager) {
            throw new RuntimeException("you don't have permission to manage inventory for this branch");
        }
    }
}

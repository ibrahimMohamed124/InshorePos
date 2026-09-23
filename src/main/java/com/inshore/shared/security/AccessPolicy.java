package com.inshore.shared.security;

import com.inshore.branch.domain.Branch;
import com.inshore.store.domain.Store;
import com.inshore.store.repository.StoreRepository;
import com.inshore.user.domain.User;
import com.inshore.user.domain.UserRole;
import com.inshore.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * One place for the "who may touch this store / branch" rules used by the newer modules
 * (reports, purchasing, stock transfers, ...). It mirrors the checks that the older services
 * (orders, refunds, shifts) each carry their own private copy of, but throws
 * {@link AccessDeniedException} so callers get a JSON {@code 403} with a readable message
 * instead of a bare {@code 500}.
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    private final UserService userService;
    private final StoreRepository storeRepository;

    public User currentUser() {
        return userService.getCurrentUser();
    }

    public boolean isPlatformAdmin(User user) {
        return user.getRole() == UserRole.ROLE_ADMIN;
    }

    /**
     * The store this user administers (store admin) or works for (everyone else), or
     * {@code null} when none is assigned. A store admin's own {@code User.store} stays empty
     * (the store points at the admin, not the other way round), so it is looked up by admin id.
     */
    public Long storeIdOf(User user) {
        if (user.getRole() == UserRole.ROLE_STORE_ADMIN) {
            Store store = storeRepository.findByStoreAdminId(user.getId());
            return store != null ? store.getId() : null;
        }
        return user.getStore() != null ? user.getStore().getId() : null;
    }

    /** Store admin / store manager of that store, or the platform admin. */
    public boolean canManageStore(User user, Long storeId) {
        if (isPlatformAdmin(user)) {
            return true;
        }
        if (storeId == null) {
            return false;
        }
        boolean storeLevel = user.getRole() == UserRole.ROLE_STORE_ADMIN
                || user.getRole() == UserRole.ROLE_STORE_MANAGER;
        return storeLevel && storeId.equals(storeIdOf(user));
    }

    /** Any authenticated member assigned to the store, or the platform admin. */
    public boolean canAccessStore(User user, Long storeId) {
        if (isPlatformAdmin(user)) {
            return true;
        }
        if (storeId == null) {
            return false;
        }
        Long ownStoreId = storeIdOf(user);
        return ownStoreId != null && ownStoreId.equals(storeId);
    }

    public void requireAccessStore(User user, Long storeId) {
        if (!canAccessStore(user, storeId)) {
            throw new AccessDeniedException("you don't have permission to access this store");
        }
    }

    /** Store admin / store manager of the branch's store, that branch's manager, or the platform admin. */
    public boolean canManageBranch(User user, Branch branch) {
        if (branch == null) {
            return false;
        }
        if (isPlatformAdmin(user)) {
            return true;
        }
        Long storeId = branch.getStore() != null ? branch.getStore().getId() : null;
        if (canManageStore(user, storeId)) {
            return true;
        }
        return user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && ((branch.getManager() != null && branch.getManager().getId().equals(user.getId()))
                || (user.getBranch() != null && user.getBranch().getId().equals(branch.getId())));
    }

    /** Anyone who may manage the branch, plus the staff (cashiers...) who work in it. */
    public boolean canAccessBranch(User user, Branch branch) {
        if (canManageBranch(user, branch)) {
            return true;
        }
        return branch != null && user.getBranch() != null && user.getBranch().getId().equals(branch.getId());
    }

    /** Store admin, store manager or branch manager working for that store (or the platform admin). */
    public boolean isManagementOfStore(User user, Long storeId) {
        if (isPlatformAdmin(user)) {
            return true;
        }
        if (storeId == null) {
            return false;
        }
        boolean management = user.getRole() == UserRole.ROLE_STORE_ADMIN
                || user.getRole() == UserRole.ROLE_STORE_MANAGER
                || user.getRole() == UserRole.ROLE_BRANCH_MANAGER;
        return management && storeId.equals(storeIdOf(user));
    }

    public void requireManageStore(User user, Long storeId) {
        if (!canManageStore(user, storeId)) {
            throw new AccessDeniedException("only the store admin or a store manager can do this");
        }
    }

    public void requireManagementOfStore(User user, Long storeId) {
        if (!isManagementOfStore(user, storeId)) {
            throw new AccessDeniedException("only store admins and managers can do this for this store");
        }
    }

    public void requireManageBranch(User user, Branch branch) {
        if (!canManageBranch(user, branch)) {
            throw new AccessDeniedException("you don't have permission to manage this branch");
        }
    }

    public void requireAccessBranch(User user, Branch branch) {
        if (!canAccessBranch(user, branch)) {
            throw new AccessDeniedException("you don't have permission to access this branch");
        }
    }
}

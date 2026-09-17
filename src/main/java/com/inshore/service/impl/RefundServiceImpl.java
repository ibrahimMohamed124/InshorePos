package com.inshore.service.impl;

import com.inshore.domain.OrderStatus;
import com.inshore.domain.UserRole;
import com.inshore.exceptions.OrderNotFoundException;
import com.inshore.exceptions.RefundNotFoundException;
import com.inshore.mapper.RefundMapper;
import com.inshore.models.Branch;
import com.inshore.models.Order;
import com.inshore.models.Refund;
import com.inshore.models.User;
import com.inshore.payload.dto.RefundDTO;
import com.inshore.repository.BranchRepository;
import com.inshore.repository.OrderRepository;
import com.inshore.repository.RefundRepository;
import com.inshore.service.RefundService;
import com.inshore.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final BranchRepository branchRepository;
    private final UserService userService;

    @Override
    @Transactional
    public RefundDTO createRefund(RefundDTO refundDTO) throws Exception {
        if (refundDTO == null) {
            throw new IllegalArgumentException("refund payload is required");
        }
        if (refundDTO.getOrderId() == null) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (!StringUtils.hasText(refundDTO.getReason())) {
            throw new IllegalArgumentException("a reason is required for every refund");
        }
        if (refundDTO.getAmount() != null && refundDTO.getAmount() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        Order order = orderRepository.findById(refundDTO.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException("order not found"));

        User currentUser = userService.getCurrentUser();
        checkBranchAccess(currentUser, order.getBranch());

        // Only a completed (paid) order has money to give back - a pending order
        // was never charged, and a cancelled order already had its stock
        // returned via OrderServiceImpl#updateOrderStatus with nothing collected.
        // A previously (partially) refunded order is allowed back in here so a
        // second partial refund can be issued against what's still left.
        if (order.getStatus() != OrderStatus.COMPLETED && order.getStatus() != OrderStatus.REFUNDED) {
            throw new IllegalStateException(
                    "only a completed order can be refunded, this order is " + order.getStatus());
        }

        double alreadyRefunded = refundRepository.findByOrderId(order.getId()).stream()
                .mapToDouble(r -> r.getAmount() == null ? 0d : r.getAmount())
                .sum();
        double orderTotal = order.getTotalAmount() == null ? 0d : order.getTotalAmount();
        double remainingRefundable = orderTotal - alreadyRefunded;

        if (remainingRefundable <= 0) {
            throw new IllegalStateException("this order has already been fully refunded");
        }

        // No amount supplied means "refund what's left" - defaulting it this way
        // (rather than requiring the client to compute and send the exact
        // remaining balance) also avoids rounding drift between what the client
        // thinks is owed and what the server has actually tracked.
        double amount = refundDTO.getAmount() != null ? refundDTO.getAmount() : remainingRefundable;
        if (amount > remainingRefundable) {
            throw new IllegalArgumentException(
                    "refund amount exceeds the order's remaining refundable balance of " + remainingRefundable);
        }

        Refund refund = Refund.builder()
                .order(order)
                .reason(refundDTO.getReason())
                .amount(amount)
                // Refund through the same method the order was paid with by
                // default; a client can override (e.g. store policy is cash
                // refunds only) but the payment type is never left unset.
                .paymentType(refundDTO.getPaymentType() != null
                        ? refundDTO.getPaymentType()
                        : order.getPaymentType())
                .cashier(currentUser)
                .branch(order.getBranch())
                // Shift reports aren't wired up yet - see RefundDTO#shiftReportId.
                .shiftReport(null)
                .build();

        Refund savedRefund = refundRepository.save(refund);

        // Flip the order over to REFUNDED once nothing is left to give back, so
        // order listings/reports can tell a refunded sale apart from an
        // ordinary completed one without re-summing every refund each time.
        if (amount >= remainingRefundable) {
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
        }

        return RefundMapper.toDTO(savedRefund);
    }

    @Override
    public List<RefundDTO> getAllRefunds() throws Exception {
        // Every other lookup here is scoped to a branch/cashier/order the
        // caller already has access to; an unscoped "everything" listing spans
        // every store, so restrict it the same way SecurityConfig reserves
        // /api/super-admin/** for ROLE_ADMIN.
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() != UserRole.ROLE_ADMIN) {
            throw new AccessDeniedException("only an admin can list every refund");
        }

        return refundRepository.findAll().stream()
                .map(RefundMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefundDTO> getRefundByCashier(UUID cashierId) throws Exception {
        if (cashierId == null) {
            throw new IllegalArgumentException("cashierId is required");
        }

        User currentUser = userService.getCurrentUser();
        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new EntityNotFoundException("cashier not found");
        }

        // a cashier can always see their own refunds; viewing someone else's
        // requires branch-level authority (same rule as
        // OrderServiceImpl#getOrderByCashier).
        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, cashier.getBranch());
        }

        return refundRepository.findByCashierId(cashierId).stream()
                .map(RefundMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefundDTO> getRefundByShiftReport(Long shiftReportId) throws Exception {
        if (shiftReportId == null) {
            throw new IllegalArgumentException("shiftReportId is required");
        }

        // ShiftReport has no repository/service yet (it's an empty stub), so
        // its own branch/cashier can't be looked up to authorize against
        // up front - authorize per refund instead, using each refund's branch.
        User currentUser = userService.getCurrentUser();

        return refundRepository.findByShiftReportId(shiftReportId).stream()
                .filter(r -> hasBranchAccess(currentUser, r.getBranch()))
                .map(RefundMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefundDTO> getRefundByCashierAndDateRange(
            UUID cashierId, LocalDateTime startDate, LocalDateTime endDate) throws Exception {
        if (cashierId == null) {
            throw new IllegalArgumentException("cashierId is required");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must not be after endDate");
        }

        User currentUser = userService.getCurrentUser();
        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new EntityNotFoundException("cashier not found");
        }

        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, cashier.getBranch());
        }

        return refundRepository.findByCashierIdAndCreatedAtBetween(cashierId, startDate, endDate).stream()
                .map(RefundMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RefundDTO> getRefundByBranch(Long branchId) throws Exception {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("branch not found"));

        checkBranchAccess(userService.getCurrentUser(), branch);

        return refundRepository.findByBranchId(branchId).stream()
                .map(RefundMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public RefundDTO getRefundById(Long refundId) throws Exception {
        Refund refund = findRefundOrThrow(refundId);
        checkBranchAccess(userService.getCurrentUser(), refund.getBranch());
        return RefundMapper.toDTO(refund);
    }

    @Override
    @Transactional
    public void deleteRefund(Long refundId) throws Exception {
        Refund refund = findRefundOrThrow(refundId);
        User currentUser = userService.getCurrentUser();
        checkBranchAccess(currentUser, refund.getBranch());

        // A refund is a correction to a financial record a cashier already
        // issued - letting that same cashier delete it would let them quietly
        // erase evidence of a bad or fraudulent refund. Reversing one requires
        // branch-manager tier or above.
        boolean canDelete = currentUser.getRole() == UserRole.ROLE_ADMIN
                || currentUser.getRole() == UserRole.ROLE_STORE_ADMIN
                || currentUser.getRole() == UserRole.ROLE_STORE_MANAGER
                || currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER;
        if (!canDelete) {
            throw new AccessDeniedException("you don't have permission to delete refunds");
        }

        Order order = refund.getOrder();
        refundRepository.delete(refund);

        // If removing this refund means the order is no longer fully refunded,
        // put it back to COMPLETED rather than leaving it permanently marked
        // REFUNDED for money it's actually still owed for.
        if (order != null && order.getStatus() == OrderStatus.REFUNDED) {
            double remainingRefunded = refundRepository.findByOrderId(order.getId()).stream()
                    .mapToDouble(r -> r.getAmount() == null ? 0d : r.getAmount())
                    .sum();
            double orderTotal = order.getTotalAmount() == null ? 0d : order.getTotalAmount();
            if (remainingRefunded < orderTotal) {
                order.setStatus(OrderStatus.COMPLETED);
                orderRepository.save(order);
            }
        }
    }

    private Refund findRefundOrThrow(Long refundId) {
        if (refundId == null) {
            throw new IllegalArgumentException("id is required");
        }
        return refundRepository.findById(refundId)
                .orElseThrow(() -> new RefundNotFoundException("refund not found"));
    }

    // Mirrors OrderServiceImpl's branch-access rules - a refund is only ever
    // visible/actionable by people who could also see the order it refunds.
    private void checkBranchAccess(User user, Branch branch) {
        if (!hasBranchAccess(user, branch)) {
            throw new AccessDeniedException("you don't have permission to access refunds for this branch");
        }
    }

    private boolean hasBranchAccess(User user, Branch branch) {
        if (branch == null) {
            return true;
        }

        boolean isAdmin = user.getRole() == UserRole.ROLE_ADMIN;

        boolean isStoreAdmin = user.getRole() == UserRole.ROLE_STORE_ADMIN
                && branch.getStore() != null
                && branch.getStore().getStoreAdmin() != null
                && branch.getStore().getStoreAdmin().getId().equals(user.getId());

        boolean isStoreManager = user.getRole() == UserRole.ROLE_STORE_MANAGER
                && branch.getStore() != null
                && user.getStore() != null
                && user.getStore().getId().equals(branch.getStore().getId());

        boolean isBranchManager = user.getRole() == UserRole.ROLE_BRANCH_MANAGER
                && branch.getManager() != null
                && branch.getManager().getId().equals(user.getId());

        boolean isOwnBranchStaff = user.getBranch() != null
                && user.getBranch().getId().equals(branch.getId());

        return isAdmin || isStoreAdmin || isStoreManager || isBranchManager || isOwnBranchStaff;
    }
}

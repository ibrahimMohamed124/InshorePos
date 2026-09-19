package com.inshore.refund.service.impl;

import com.inshore.order.domain.OrderStatus;
import com.inshore.user.domain.UserRole;
import com.inshore.order.exception.OrderNotFoundException;
import com.inshore.refund.exception.RefundNotFoundException;
import com.inshore.refund.mapper.RefundMapper;
import com.inshore.branch.domain.Branch;
import com.inshore.order.domain.Order;
import com.inshore.refund.domain.Refund;
import com.inshore.user.domain.User;
import com.inshore.refund.dto.RefundDTO;
import com.inshore.branch.repository.BranchRepository;
import com.inshore.order.repository.OrderRepository;
import com.inshore.refund.repository.RefundRepository;
import com.inshore.refund.service.RefundService;
import com.inshore.shift.domain.ShiftReport;
import com.inshore.shift.repository.ShiftReportRepository;
import com.inshore.user.service.UserService;
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
    private final ShiftReportRepository shiftReportRepository;
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

        double amount = refundDTO.getAmount() != null ? refundDTO.getAmount() : remainingRefundable;
        if (amount > remainingRefundable) {
            throw new IllegalArgumentException(
                    "refund amount exceeds the order's remaining refundable balance of " + remainingRefundable);
        }

        // ShiftReport now has a repository/service (see ShiftReportServiceImpl) -
        // attribute this refund to the cashier's open shift when they have one,
        // instead of always leaving it null. Still optional: a refund processed
        // with no open shift (e.g. by an admin outside shift hours) is allowed.
        ShiftReport openShift = shiftReportRepository
                .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(currentUser)
                .orElse(null);

        Refund refund = Refund.builder()
                .order(order)
                .reason(refundDTO.getReason())
                .amount(amount)
                .paymentType(refundDTO.getPaymentType() != null
                        ? refundDTO.getPaymentType()
                        : order.getPaymentType())
                .cashier(currentUser)
                .branch(order.getBranch())
                .shiftReport(openShift)
                .build();

        Refund savedRefund = refundRepository.save(refund);

        if (amount >= remainingRefundable) {
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);
        }

        return RefundMapper.toDTO(savedRefund);
    }

    @Override
    public List<RefundDTO> getAllRefunds() throws Exception {

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

        boolean canDelete = currentUser.getRole() == UserRole.ROLE_ADMIN
                || currentUser.getRole() == UserRole.ROLE_STORE_ADMIN
                || currentUser.getRole() == UserRole.ROLE_STORE_MANAGER
                || currentUser.getRole() == UserRole.ROLE_BRANCH_MANAGER;
        if (!canDelete) {
            throw new AccessDeniedException("you don't have permission to delete refunds");
        }

        Order order = refund.getOrder();
        refundRepository.delete(refund);

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

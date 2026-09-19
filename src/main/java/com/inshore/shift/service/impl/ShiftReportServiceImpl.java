package com.inshore.shift.service.impl;

import com.inshore.branch.domain.Branch;
import com.inshore.branch.repository.BranchRepository;
import com.inshore.order.domain.Order;
import com.inshore.order.repository.OrderRepository;
import com.inshore.product.domain.Product;
import com.inshore.refund.repository.RefundRepository;
import com.inshore.shared.exception.UserException;
import com.inshore.shift.domain.PaymentSummary;
import com.inshore.shift.domain.ShiftReport;
import com.inshore.shift.dto.ShiftReportDTO;
import com.inshore.shift.exception.ShiftReportNotFoundException;
import com.inshore.shift.mapper.ShiftReportMapper;
import com.inshore.shift.repository.ShiftReportRepository;
import com.inshore.shift.service.ShiftReportService;
import com.inshore.user.domain.User;
import com.inshore.user.domain.UserRole;
import com.inshore.user.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShiftReportServiceImpl implements ShiftReportService {

    // How many entries "top selling products" / "recent orders" keep on a
    // closed shift report - unbounded lists here would mean an all-day shift
    // with thousands of orders drags its entire history along on every read.
    private static final int TOP_PRODUCTS_LIMIT = 5;
    private static final int RECENT_ORDERS_LIMIT = 10;

    private final ShiftReportRepository shiftReportRepository;
    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final BranchRepository branchRepository;
    private final UserService userService;

    @Override
    @Transactional
    public ShiftReportDTO startShift(UUID cashierId, Long branchId, LocalDateTime shiftStart) throws Exception {
        if (cashierId == null) {
            throw new IllegalArgumentException("cashierId is required");
        }
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }
        if (shiftStart == null) {
            throw new IllegalArgumentException("shiftStart is required");
        }

        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new EntityNotFoundException("cashier not found");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("branch not found"));

        User currentUser = userService.getCurrentUser();
        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, branch);
        }

        shiftReportRepository.findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier)
                .ifPresent(open -> {
                    throw new IllegalStateException(
                            "this cashier already has an open shift (id " + open.getId() + ") - end it first");
                });

        ShiftReport shiftReport = ShiftReport.builder()
                .shiftStart(shiftStart)
                .cashier(cashier)
                .branch(branch)
                .totalSale(0d)
                .totalRefunds(0d)
                .netSale(0d)
                .totalOrders(0L)
                .build();

        ShiftReport saved = shiftReportRepository.save(shiftReport);
        return toDetailedDTO(saved);
    }

    @Override
    @Transactional
    public ShiftReportDTO endShift(Long shiftReportId, LocalDateTime shiftEnd) throws Exception {
        if (shiftEnd == null) {
            throw new IllegalArgumentException("shiftEnd is required");
        }

        ShiftReport shiftReport = findShiftReportOrThrow(shiftReportId);
        checkBranchAccess(userService.getCurrentUser(), shiftReport.getBranch());

        if (shiftReport.getShiftEnd() != null) {
            throw new IllegalStateException("this shift has already been closed");
        }
        if (shiftEnd.isBefore(shiftReport.getShiftStart())) {
            throw new IllegalArgumentException("shiftEnd cannot be before shiftStart");
        }

        UUID cashierId = shiftReport.getCashier().getId();
        ShiftAggregates aggregates = computeAggregates(cashierId, shiftReport.getShiftStart(), shiftEnd);

        shiftReport.setShiftEnd(shiftEnd);
        shiftReport.setTotalSale(aggregates.totalSale);
        shiftReport.setTotalRefunds(aggregates.totalRefunds);
        shiftReport.setNetSale(aggregates.totalSale - aggregates.totalRefunds);
        shiftReport.setTotalOrders(aggregates.totalOrders);
        shiftReport.setTopSellingProducts(aggregates.topSellingProducts);
        shiftReport.setRecentOrders(aggregates.recentOrders);

        ShiftReport saved = shiftReportRepository.save(shiftReport);

        ShiftReportDTO dto = ShiftReportMapper.toDTO(saved);
        dto.setPaymentSummary(aggregates.paymentSummary);
        return dto;
    }

    @Override
    public ShiftReportDTO getShiftReportById(Long id) throws Exception {
        ShiftReport shiftReport = findShiftReportOrThrow(id);
        checkBranchAccess(userService.getCurrentUser(), shiftReport.getBranch());
        return toDetailedDTO(shiftReport);
    }

    @Override
    public List<ShiftReportDTO> getAllShiftReports() throws Exception {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() != UserRole.ROLE_ADMIN) {
            throw new AccessDeniedException("only an admin can list every shift report");
        }

        return shiftReportRepository.findAllWithDetails().stream()
                .map(this::toDetailedDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ShiftReportDTO> getShiftReportsByBranchId(Long branchId) {
        if (branchId == null) {
            throw new IllegalArgumentException("branchId is required");
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException("branch not found"));
        checkBranchAccess(userService.getCurrentUser(), branch);

        return shiftReportRepository.findByBranchId(branchId).stream()
                .map(this::toDetailedDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ShiftReportDTO> getShiftReportsByCashierId(UUID cashierId) {
        if (cashierId == null) {
            throw new IllegalArgumentException("cashierId is required");
        }

        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new EntityNotFoundException("cashier not found");
        }

        User currentUser = userService.getCurrentUser();
        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, cashier.getBranch());
        }

        return shiftReportRepository.findByCashierId(cashierId).stream()
                .map(this::toDetailedDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ShiftReportDTO getCurrentShiftProgress(UUID cashierId) throws UserException {
        if (cashierId == null) {
            throw new UserException("cashierId is required");
        }

        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new UserException("cashier not found");
        }

        User currentUser = userService.getCurrentUser();
        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, cashier.getBranch());
        }

        ShiftReport openShift = shiftReportRepository
                .findTopByCashierAndShiftEndIsNullOrderByShiftStartDesc(cashier)
                .orElseThrow(() -> new UserException("this cashier has no active shift right now"));

        return toDetailedDTO(openShift);
    }

    @Override
    public ShiftReportDTO getShiftByCashierAndDate(UUID cashierId, LocalDateTime date) throws Exception {
        if (cashierId == null) {
            throw new IllegalArgumentException("cashierId is required");
        }
        if (date == null) {
            throw new IllegalArgumentException("date is required");
        }

        User cashier = userService.getUserById(cashierId);
        if (cashier == null) {
            throw new EntityNotFoundException("cashier not found");
        }

        User currentUser = userService.getCurrentUser();
        if (!currentUser.getId().equals(cashierId)) {
            checkBranchAccess(currentUser, cashier.getBranch());
        }

        // "the shift on this date" means the shift that started sometime that
        // calendar day - not an exact timestamp match.
        LocalDateTime dayStart = date.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1).minusNanos(1);

        ShiftReport shiftReport = shiftReportRepository
                .findByCashierAndShiftStartBetween(cashier, dayStart, dayEnd)
                .orElseThrow(() -> new EntityNotFoundException("no shift found for this cashier on that date"));

        return toDetailedDTO(shiftReport);
    }

    // --- helpers -------------------------------------------------------

    private ShiftReport findShiftReportOrThrow(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        return shiftReportRepository.findById(id)
                .orElseThrow(() -> new ShiftReportNotFoundException("shift report not found"));
    }

    // paymentSummary is @Transient on ShiftReport (it's a derived breakdown,
    // not something we want to freeze at endShift-time and never revisit), so
    // every read recomputes it here - for an open shift, over
    // [shiftStart, now); for a closed one, over [shiftStart, shiftEnd].
    // Everything else (totalSale, totalOrders, topSellingProducts, ...) is
    // read straight off the persisted entity for a closed shift, and is
    // computed live for a still-open one so "current progress" is meaningful
    // however it's reached (this method, or a plain getById on an open shift).
    private ShiftReportDTO toDetailedDTO(ShiftReport shiftReport) {
        ShiftReportDTO dto = ShiftReportMapper.toDTO(shiftReport);

        if (shiftReport.getCashier() == null || shiftReport.getShiftStart() == null) {
            return dto;
        }

        UUID cashierId = shiftReport.getCashier().getId();
        boolean isOpen = shiftReport.getShiftEnd() == null;
        LocalDateTime to = isOpen ? LocalDateTime.now() : shiftReport.getShiftEnd();

        if (isOpen) {
            ShiftAggregates aggregates = computeAggregates(cashierId, shiftReport.getShiftStart(), to);
            dto.setTotalSale(aggregates.totalSale);
            dto.setTotalRefunds(aggregates.totalRefunds);
            dto.setNetSale(aggregates.totalSale - aggregates.totalRefunds);
            dto.setTotalOrders(aggregates.totalOrders);
            dto.setPaymentSummary(aggregates.paymentSummary);
            dto.setTopSellingProducts(aggregates.topSellingProducts.stream()
                    .map(com.inshore.product.mapper.ProductMapper::toDTO)
                    .collect(Collectors.toList()));
            dto.setRecentOrders(aggregates.recentOrders.stream()
                    .map(com.inshore.order.mapper.OrderMapper::toDTO)
                    .collect(Collectors.toList()));
        } else {
            dto.setPaymentSummary(buildPaymentSummary(cashierId, shiftReport.getShiftStart(), to));
        }

        return dto;
    }

    private ShiftAggregates computeAggregates(UUID cashierId, LocalDateTime from, LocalDateTime to) {
        Double totalSale = orderRepository.sumTotalAmountByCashierAndCreatedAtBetween(cashierId, from, to);
        Long totalOrders = orderRepository.countByCashierAndCreatedAtBetween(cashierId, from, to);
        Double totalRefunds = refundRepository.sumAmountByCashierAndCreatedAtBetween(cashierId, from, to);

        List<Product> topSellingProducts = orderRepository
                .findTopSellingProductsByCashierAndCreatedAtBetween(
                        cashierId, from, to, PageRequest.of(0, TOP_PRODUCTS_LIMIT))
                .stream()
                .map(OrderRepository.ProductQuantityProjection::getProduct)
                .collect(Collectors.toList());

        List<Order> recentOrders = orderRepository.findRecentByCashierAndCreatedAtBetween(
                cashierId, from, to, PageRequest.of(0, RECENT_ORDERS_LIMIT));

        List<PaymentSummary> paymentSummary = buildPaymentSummary(cashierId, from, to);

        return new ShiftAggregates(
                totalSale == null ? 0d : totalSale,
                totalOrders == null ? 0L : totalOrders,
                totalRefunds == null ? 0d : totalRefunds,
                topSellingProducts,
                recentOrders,
                paymentSummary
        );
    }

    private List<PaymentSummary> buildPaymentSummary(UUID cashierId, LocalDateTime from, LocalDateTime to) {
        List<OrderRepository.PaymentTypeTotalsProjection> rows =
                orderRepository.summarizePaymentTypesByCashierAndCreatedAtBetween(cashierId, from, to);

        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        double grandTotal = rows.stream()
                .mapToDouble(r -> r.getTotalAmount() == null ? 0d : r.getTotalAmount())
                .sum();

        return rows.stream()
                .map(r -> PaymentSummary.builder()
                        .paymentType(r.getPaymentType())
                        .totalAmount(r.getTotalAmount())
                        .transactionCount(r.getTransactionCount() == null ? 0 : r.getTransactionCount().intValue())
                        .percentage(grandTotal > 0 ? (r.getTotalAmount() / grandTotal) * 100 : 0d)
                        .build())
                .collect(Collectors.toList());
    }

    private void checkBranchAccess(User user, Branch branch) {
        if (!hasBranchAccess(user, branch)) {
            throw new AccessDeniedException("you don't have permission to access shift reports for this branch");
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

    private static final class ShiftAggregates {
        private final double totalSale;
        private final long totalOrders;
        private final double totalRefunds;
        private final List<Product> topSellingProducts;
        private final List<Order> recentOrders;
        private final List<PaymentSummary> paymentSummary;

        private ShiftAggregates(double totalSale, long totalOrders, double totalRefunds,
                                 List<Product> topSellingProducts, List<Order> recentOrders,
                                 List<PaymentSummary> paymentSummary) {
            this.totalSale = totalSale;
            this.totalOrders = totalOrders;
            this.totalRefunds = totalRefunds;
            this.topSellingProducts = topSellingProducts;
            this.recentOrders = recentOrders;
            this.paymentSummary = paymentSummary;
        }
    }
}

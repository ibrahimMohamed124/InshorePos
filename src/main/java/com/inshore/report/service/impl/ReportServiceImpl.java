package com.inshore.report.service.impl;

import com.inshore.branch.domain.Branch;
import com.inshore.branch.repository.BranchRepository;
import com.inshore.order.domain.PaymentType;
import com.inshore.order.repository.OrderRepository;
import com.inshore.refund.repository.RefundRepository;
import com.inshore.report.dto.PaymentMixDTO;
import com.inshore.report.dto.ReportSummaryDTO;
import com.inshore.report.dto.SalesBucketDTO;
import com.inshore.report.dto.TopProductDTO;
import com.inshore.report.service.ReportService;
import com.inshore.shared.security.AccessPolicy;
import com.inshore.user.domain.User;
import com.inshore.user.domain.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportServiceImpl implements ReportService {

    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 100;
    private static final long MAX_RANGE_DAYS = 400;
    // hour buckets only make sense for short windows; anything longer is grouped by day
    private static final long MAX_HOURLY_RANGE_DAYS = 3;

    private final OrderRepository orderRepository;
    private final RefundRepository refundRepository;
    private final BranchRepository branchRepository;
    private final AccessPolicy accessPolicy;

    @Override
    public ReportSummaryDTO getSummary(Long branchId, LocalDateTime from, LocalDateTime to, String groupBy) {
        LocalDateTime[] window = resolveWindow(from, to);
        List<Long> branchIds = resolveBranchIds(branchId);

        String effectiveGroupBy = resolveGroupBy(groupBy, window[0], window[1]);

        if (branchIds.isEmpty()) {
            return ReportSummaryDTO.builder()
                    .totalSales(0d).totalRefunds(0d).netSales(0d).orderCount(0L).averageOrder(0d)
                    .groupBy(effectiveGroupBy).series(new ArrayList<>())
                    .build();
        }

        List<OrderRepository.SalesRowProjection> rows =
                orderRepository.findSalesRows(branchIds, window[0], window[1]);

        double totalSales = 0d;
        Map<LocalDateTime, double[]> buckets = new TreeMap<>();
        for (OrderRepository.SalesRowProjection row : rows) {
            double amount = row.getTotalAmount() == null ? 0d : row.getTotalAmount();
            totalSales += amount;

            LocalDateTime key = "hour".equals(effectiveGroupBy)
                    ? row.getCreatedAt().truncatedTo(ChronoUnit.HOURS)
                    : row.getCreatedAt().toLocalDate().atStartOfDay();
            double[] bucket = buckets.computeIfAbsent(key, k -> new double[2]);
            bucket[0] += amount;
            bucket[1] += 1;
        }

        Double refunds = refundRepository.sumAmountByBranchIdsAndCreatedAtBetween(branchIds, window[0], window[1]);
        double totalRefunds = refunds == null ? 0d : refunds;
        long orderCount = rows.size();

        List<SalesBucketDTO> series = new ArrayList<>();
        buckets.forEach((key, values) -> series.add(SalesBucketDTO.builder()
                .bucket(key)
                .sales(round(values[0]))
                .orders((long) values[1])
                .build()));

        return ReportSummaryDTO.builder()
                .totalSales(round(totalSales))
                .totalRefunds(round(totalRefunds))
                .netSales(round(totalSales - totalRefunds))
                .orderCount(orderCount)
                .averageOrder(orderCount > 0 ? round(totalSales / orderCount) : 0d)
                .groupBy(effectiveGroupBy)
                .series(series)
                .build();
    }

    @Override
    public List<TopProductDTO> getTopProducts(Long branchId, LocalDateTime from, LocalDateTime to, Integer limit) {
        LocalDateTime[] window = resolveWindow(from, to);
        List<Long> branchIds = resolveBranchIds(branchId);
        if (branchIds.isEmpty()) {
            return new ArrayList<>();
        }

        int size = limit == null ? DEFAULT_TOP_LIMIT : Math.max(1, Math.min(limit, MAX_TOP_LIMIT));

        return orderRepository.findTopProducts(branchIds, window[0], window[1], PageRequest.of(0, size))
                .stream()
                .map(row -> TopProductDTO.builder()
                        .productId(row.getProductId())
                        .name(row.getName())
                        .quantity(row.getQuantity() == null ? 0L : row.getQuantity())
                        .revenue(round(row.getRevenue() == null ? 0d : row.getRevenue()))
                        .build())
                .toList();
    }

    @Override
    public List<PaymentMixDTO> getPaymentMix(Long branchId, LocalDateTime from, LocalDateTime to) {
        LocalDateTime[] window = resolveWindow(from, to);
        List<Long> branchIds = resolveBranchIds(branchId);
        if (branchIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<PaymentType, double[]> byType = new EnumMap<>(PaymentType.class);
        double grandTotal = 0d;
        for (OrderRepository.SalesRowProjection row : orderRepository.findSalesRows(branchIds, window[0], window[1])) {
            if (row.getPaymentType() == null) {
                continue;
            }
            double amount = row.getTotalAmount() == null ? 0d : row.getTotalAmount();
            double[] totals = byType.computeIfAbsent(row.getPaymentType(), k -> new double[2]);
            totals[0] += amount;
            totals[1] += 1;
            grandTotal += amount;
        }

        final double total = grandTotal;
        List<PaymentMixDTO> result = new ArrayList<>();
        byType.forEach((type, totals) -> result.add(PaymentMixDTO.builder()
                .paymentType(type)
                .totalAmount(round(totals[0]))
                .amount(round(totals[0]))
                .transactionCount((int) totals[1])
                .percentage(total > 0 ? round(totals[0] / total * 100) : 0d)
                .build()));
        result.sort(Comparator.comparing(PaymentMixDTO::getTotalAmount).reversed());
        return result;
    }

    // --- helpers -------------------------------------------------------

    /** [from, to]; defaults to "today so far"; validates order and a sane maximum length. */
    private LocalDateTime[] resolveWindow(LocalDateTime from, LocalDateTime to) {
        LocalDateTime end = to != null ? to : LocalDateTime.now();
        LocalDateTime start = from != null ? from : LocalDate.now().atStartOfDay();

        if (start.isAfter(end)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        if (Duration.between(start, end).toDays() > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("the report range is limited to " + MAX_RANGE_DAYS + " days");
        }
        return new LocalDateTime[]{start, end};
    }

    private String resolveGroupBy(String requested, LocalDateTime from, LocalDateTime to) {
        if (requested != null && !requested.isBlank()
                && !requested.equalsIgnoreCase("hour") && !requested.equalsIgnoreCase("day")) {
            throw new IllegalArgumentException("groupBy must be 'hour' or 'day'");
        }
        boolean wantsHour = requested == null || requested.isBlank()
                ? Duration.between(from, to).toDays() < 1
                : requested.equalsIgnoreCase("hour");
        boolean shortRange = Duration.between(from, to).toDays() <= MAX_HOURLY_RANGE_DAYS;
        return wantsHour && shortRange ? "hour" : "day";
    }

    /**
     * Which branches the report covers, after checking the caller may see them. Reports are for
     * management only: platform admin, store admin, store manager and (own branch) branch manager.
     */
    private List<Long> resolveBranchIds(Long branchId) {
        User user = accessPolicy.currentUser();

        boolean allowedRole = user.getRole() == UserRole.ROLE_ADMIN
                || user.getRole() == UserRole.ROLE_STORE_ADMIN
                || user.getRole() == UserRole.ROLE_STORE_MANAGER
                || user.getRole() == UserRole.ROLE_BRANCH_MANAGER;
        if (!allowedRole) {
            throw new AccessDeniedException("reports are only available to store admins and managers");
        }

        if (branchId != null) {
            Branch branch = branchRepository.findById(branchId).orElseThrow(
                    () -> new IllegalArgumentException("branch not found")
            );
            accessPolicy.requireManageBranch(user, branch);
            return List.of(branch.getId());
        }

        if (user.getRole() == UserRole.ROLE_BRANCH_MANAGER) {
            if (user.getBranch() == null) {
                throw new IllegalArgumentException("no branch is assigned to this account");
            }
            return List.of(user.getBranch().getId());
        }

        if (user.getRole() == UserRole.ROLE_ADMIN) {
            return branchRepository.findAll().stream().map(Branch::getId).toList();
        }

        Long storeId = accessPolicy.storeIdOf(user);
        if (storeId == null) {
            throw new IllegalArgumentException("no store is linked to this account yet");
        }
        return branchRepository.findByStoreId(storeId).stream().map(Branch::getId).toList();
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}

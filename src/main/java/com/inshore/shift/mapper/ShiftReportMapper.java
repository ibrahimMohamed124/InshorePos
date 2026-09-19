package com.inshore.shift.mapper;

import com.inshore.branch.mapper.BranchMapper;
import com.inshore.order.dto.OrderDTO;
import com.inshore.order.domain.Order;
import com.inshore.order.mapper.OrderMapper;
import com.inshore.product.domain.Product;
import com.inshore.product.dto.ProductDTO;
import com.inshore.product.mapper.ProductMapper;
import com.inshore.refund.domain.Refund;
import com.inshore.refund.dto.RefundDTO;
import com.inshore.refund.mapper.RefundMapper;
import com.inshore.shift.domain.ShiftReport;
import com.inshore.shift.dto.ShiftReportDTO;
import com.inshore.user.mapper.UserMapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ShiftReportMapper {

    // Was previously taking a ShiftReportDTO instead of the ShiftReport entity
    // (so it could never have compiled: it called UserMapper.toDTO(...) - which
    // expects a User entity - with shiftReportDTO.getCashier(), which already
    // returns a UserDTO). Rewritten to map the entity, the same way every other
    // mapper in the app does, with null-safety on every relation.
    public static ShiftReportDTO toDTO(ShiftReport shiftReport) {
        if (shiftReport == null) {
            return null;
        }

        return ShiftReportDTO.builder()
                .id(shiftReport.getId())
                .shiftStart(shiftReport.getShiftStart())
                .shiftEnd(shiftReport.getShiftEnd())
                .totalSale(shiftReport.getTotalSale())
                .totalRefunds(shiftReport.getTotalRefunds())
                .netSale(shiftReport.getNetSale())
                .totalOrders(shiftReport.getTotalOrders())
                .cashierId(shiftReport.getCashier() != null ? shiftReport.getCashier().getId() : null)
                .cashier(UserMapper.toDTO(shiftReport.getCashier()))
                .branchId(shiftReport.getBranch() != null ? shiftReport.getBranch().getId() : null)
                .branch(shiftReport.getBranch() != null ? BranchMapper.toDTO(shiftReport.getBranch()) : null)
                .paymentSummary(shiftReport.getPaymentSummary())
                .recentOrders(mapOrders(shiftReport.getRecentOrders()))
                .topSellingProducts(mapProducts(shiftReport.getTopSellingProducts()))
                .refunds(mapRefunds(shiftReport.getRefunds()))
                .build();
    }

    public static List<ShiftReportDTO> toDTOList(List<ShiftReport> shiftReports) {
        if (shiftReports == null || shiftReports.isEmpty()) {
            return Collections.emptyList();
        }

        return shiftReports.stream().map(ShiftReportMapper::toDTO).collect(Collectors.toList());
    }

    private static List<RefundDTO> mapRefunds(List<Refund> refunds) {
        if (refunds == null || refunds.isEmpty()) {
            return Collections.emptyList();
        }

        return refunds.stream().map(RefundMapper::toDTO).collect(Collectors.toList());
    }

    private static List<ProductDTO> mapProducts(List<Product> topSellingProducts) {
        if (topSellingProducts == null || topSellingProducts.isEmpty()) {
            return Collections.emptyList();
        }

        return topSellingProducts.stream().map(ProductMapper::toDTO).collect(Collectors.toList());
    }

    private static List<OrderDTO> mapOrders(List<Order> recentOrders) {
        if (recentOrders == null || recentOrders.isEmpty()) {
            return Collections.emptyList();
        }

        return recentOrders.stream().map(OrderMapper::toDTO).collect(Collectors.toList());
    }
}

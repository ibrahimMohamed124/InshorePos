package com.inshore.refund.mapper;

import com.inshore.branch.mapper.BranchMapper;
import com.inshore.order.mapper.OrderMapper;
import com.inshore.refund.domain.Refund;
import com.inshore.refund.dto.RefundDTO;
import com.inshore.user.mapper.UserMapper;

public class RefundMapper {

    public static RefundDTO toDTO(Refund refund) {
        if (refund == null) {
            return null;
        }

        return RefundDTO.builder()
                .id(refund.getId())
                .orderId(refund.getOrder() != null ? refund.getOrder().getId() : null)
                .order(OrderMapper.toDTO(refund.getOrder()))
                .reason(refund.getReason())
                .amount(refund.getAmount())
                .shiftReportId(refund.getShiftReport() != null ? refund.getShiftReport().getId() : null)
                .cashierId(refund.getCashier() != null ? refund.getCashier().getId() : null)
                .cashier(UserMapper.toDTO(refund.getCashier()))
                .cashierName(refund.getCashier() != null ? refund.getCashier().getUsername() : null)
                .branchId(refund.getBranch() != null ? refund.getBranch().getId() : null)
                .branch(refund.getBranch() != null ? BranchMapper.toDTO(refund.getBranch()) : null)
                .paymentType(refund.getPaymentType())
                .createdAt(refund.getCreatedAt())
                .build();
    }
}

package com.inshore.service;

import com.inshore.payload.dto.RefundDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RefundService {

    /**
     * orderId, reason are required. amount is optional - omit it to refund the
     * order's full remaining balance. cashier and branch are always derived
     * server-side from the authenticated user and the order, never from the
     * payload (see RefundServiceImpl).
     */
    RefundDTO createRefund(RefundDTO refundDTO) throws Exception;

    /**
     * Restricted to ROLE_ADMIN - every other lookup here is scoped to a
     * branch/cashier/order the caller already has access to; this one spans
     * every store.
     */
    List<RefundDTO> getAllRefunds() throws Exception;

    List<RefundDTO> getRefundByCashier(UUID cashierId) throws Exception;

    List<RefundDTO> getRefundByShiftReport(Long shiftReportId) throws Exception;

    List<RefundDTO> getRefundByCashierAndDateRange(
            UUID cashierId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) throws Exception;

    List<RefundDTO> getRefundByBranch(Long branchId) throws Exception;

    RefundDTO getRefundById(Long refundId) throws Exception;

    /**
     * Restricted to branch-manager tier and above - the cashier who issued a
     * refund cannot delete it themselves.
     */
    void deleteRefund(Long refundId) throws Exception;

}

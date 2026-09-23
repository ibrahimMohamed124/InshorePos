package com.inshore.report.service;

import com.inshore.report.dto.PaymentMixDTO;
import com.inshore.report.dto.ReportSummaryDTO;
import com.inshore.report.dto.TopProductDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {

    /**
     * branchId null = every branch the caller is responsible for (their store, or their own
     * branch for a branch manager). from/to default to "today" when null.
     */
    ReportSummaryDTO getSummary(Long branchId, LocalDateTime from, LocalDateTime to, String groupBy);

    List<TopProductDTO> getTopProducts(Long branchId, LocalDateTime from, LocalDateTime to, Integer limit);

    List<PaymentMixDTO> getPaymentMix(Long branchId, LocalDateTime from, LocalDateTime to);
}

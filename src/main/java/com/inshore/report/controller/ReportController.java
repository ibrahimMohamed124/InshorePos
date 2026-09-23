package com.inshore.report.controller;

import com.inshore.report.dto.PaymentMixDTO;
import com.inshore.report.dto.ReportSummaryDTO;
import com.inshore.report.dto.TopProductDTO;
import com.inshore.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Management reports. {@code branchId} is optional everywhere: omit it for the whole store (or,
 * for a branch manager, their own branch). {@code from}/{@code to} are ISO date-times
 * ({@code yyyy-MM-ddTHH:mm:ss}); both default to "today so far".
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<ReportSummaryDTO> getSummary(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String groupBy
    ) {
        return ResponseEntity.ok(reportService.getSummary(branchId, from, to, groupBy));
    }

    @GetMapping("/top-products")
    public ResponseEntity<List<TopProductDTO>> getTopProducts(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Integer limit
    ) {
        return ResponseEntity.ok(reportService.getTopProducts(branchId, from, to, limit));
    }

    @GetMapping("/payment-mix")
    public ResponseEntity<List<PaymentMixDTO>> getPaymentMix(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return ResponseEntity.ok(reportService.getPaymentMix(branchId, from, to));
    }
}

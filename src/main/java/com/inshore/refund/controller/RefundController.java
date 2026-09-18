package com.inshore.refund.controller;

import com.inshore.refund.dto.RefundDTO;
import com.inshore.shared.web.ApiResponse;
import com.inshore.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    @PostMapping
    public ResponseEntity<RefundDTO> createRefund(
            @RequestBody RefundDTO refundDTO
    ) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.createRefund(refundDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RefundDTO> getRefundById(
            @PathVariable Long id
    ) throws Exception {
        return ResponseEntity.ok(refundService.getRefundById(id));
    }

    @GetMapping
    public ResponseEntity<List<RefundDTO>> getAllRefunds() throws Exception {
        return ResponseEntity.ok(refundService.getAllRefunds());
    }

    @GetMapping("/cashier/{cashierId}")
    public ResponseEntity<List<RefundDTO>> getRefundsByCashier(
            @PathVariable UUID cashierId
    ) throws Exception {
        return ResponseEntity.ok(refundService.getRefundByCashier(cashierId));
    }

    @GetMapping("/cashier/{cashierId}/range")
    public ResponseEntity<List<RefundDTO>> getRefundsByCashierAndDateRange(
            @PathVariable UUID cashierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate
    ) throws Exception {
        return ResponseEntity.ok(refundService.getRefundByCashierAndDateRange(cashierId, startDate, endDate));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<RefundDTO>> getRefundsByBranch(
            @PathVariable Long branchId
    ) throws Exception {
        return ResponseEntity.ok(refundService.getRefundByBranch(branchId));
    }

    @GetMapping("/shift-report/{shiftReportId}")
    public ResponseEntity<List<RefundDTO>> getRefundsByShiftReport(
            @PathVariable Long shiftReportId
    ) throws Exception {
        return ResponseEntity.ok(refundService.getRefundByShiftReport(shiftReportId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteRefund(
            @PathVariable Long id
    ) throws Exception {
        refundService.deleteRefund(id);

        ApiResponse apiResponse = new ApiResponse();
        apiResponse.setSuccess(true);
        apiResponse.setMessage("Refund deleted successfully");
        return ResponseEntity.ok(apiResponse);
    }
}

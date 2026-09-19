package com.inshore.shift.controller;

import com.inshore.shift.dto.ShiftReportDTO;
import com.inshore.shift.service.ShiftReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/shift-reports")
@RequiredArgsConstructor
public class ShiftReportController {

    private final ShiftReportService shiftReportService;

    @PostMapping("/start")
    public ResponseEntity<ShiftReportDTO> startShift(
            @RequestParam UUID cashierId,
            @RequestParam Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftStart
    ) throws Exception {
        LocalDateTime start = shiftStart != null ? shiftStart : LocalDateTime.now();
        return ResponseEntity.status(HttpStatus.CREATED).body(shiftReportService.startShift(cashierId, branchId, start));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<ShiftReportDTO> endShift(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime shiftEnd
    ) throws Exception {
        LocalDateTime end = shiftEnd != null ? shiftEnd : LocalDateTime.now();
        return ResponseEntity.ok(shiftReportService.endShift(id, end));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftReportDTO> getShiftReportById(
            @PathVariable Long id
    ) throws Exception {
        return ResponseEntity.ok(shiftReportService.getShiftReportById(id));
    }

    @GetMapping
    public ResponseEntity<List<ShiftReportDTO>> getAllShiftReports() throws Exception {
        return ResponseEntity.ok(shiftReportService.getAllShiftReports());
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<List<ShiftReportDTO>> getShiftReportsByBranch(
            @PathVariable Long branchId
    ) {
        return ResponseEntity.ok(shiftReportService.getShiftReportsByBranchId(branchId));
    }

    @GetMapping("/cashier/{cashierId}")
    public ResponseEntity<List<ShiftReportDTO>> getShiftReportsByCashier(
            @PathVariable UUID cashierId
    ) {
        return ResponseEntity.ok(shiftReportService.getShiftReportsByCashierId(cashierId));
    }

    @GetMapping("/cashier/{cashierId}/current")
    public ResponseEntity<ShiftReportDTO> getCurrentShiftProgress(
            @PathVariable UUID cashierId
    ) {
        return ResponseEntity.ok(shiftReportService.getCurrentShiftProgress(cashierId));
    }

    @GetMapping("/cashier/{cashierId}/date")
    public ResponseEntity<ShiftReportDTO> getShiftByCashierAndDate(
            @PathVariable UUID cashierId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime date
    ) throws Exception {
        return ResponseEntity.ok(shiftReportService.getShiftByCashierAndDate(cashierId, date));
    }
}

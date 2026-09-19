package com.inshore.shift.service;

import com.inshore.shared.exception.UserException;
import com.inshore.shift.dto.ShiftReportDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ShiftReportService {

    // cashierId was typed Long on every method here, but User.id (the
    // cashier) is a UUID everywhere else in the app (UserService, OrderRepository,
    // RefundService) - a Long could never actually match a real cashier.
    ShiftReportDTO startShift(UUID cashierId, Long branchId, LocalDateTime shiftStart) throws Exception;
    ShiftReportDTO endShift(Long shiftReportId, LocalDateTime shiftEnd) throws Exception;
    ShiftReportDTO getShiftReportById(Long id) throws Exception;
    List<ShiftReportDTO> getAllShiftReports() throws Exception;
    List<ShiftReportDTO> getShiftReportsByBranchId(Long branchId);
    List<ShiftReportDTO> getShiftReportsByCashierId(UUID cashierId);
    ShiftReportDTO getCurrentShiftProgress(UUID cashierId) throws UserException;
    ShiftReportDTO getShiftByCashierAndDate(UUID cashierId, LocalDateTime date) throws Exception;

}

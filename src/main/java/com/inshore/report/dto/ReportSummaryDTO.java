package com.inshore.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryDTO {

    private Double totalSales;
    private Double totalRefunds;
    private Double netSales;
    private Long orderCount;
    private Double averageOrder;

    /** "hour" or "day" - the bucket size actually used (long ranges are always grouped by day). */
    private String groupBy;

    /** Only buckets that had sales, oldest first. */
    private List<SalesBucketDTO> series;
}

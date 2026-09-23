package com.inshore.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesBucketDTO {

    /** Start of the hour or day this bucket covers. */
    private LocalDateTime bucket;
    private Double sales;
    private Long orders;
}

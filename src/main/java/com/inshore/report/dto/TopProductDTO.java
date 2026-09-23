package com.inshore.report.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopProductDTO {

    private Long productId;
    private String name;
    private Long quantity;
    private Double revenue;
}

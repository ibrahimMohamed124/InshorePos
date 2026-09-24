package com.inshore.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private boolean received;
    // APPLIED / NO_OP / UNKNOWN_PAYMENT / INVALID_TRANSITION / DUPLICATE
    private String result;
}

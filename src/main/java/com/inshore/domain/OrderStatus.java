package com.inshore.domain;

public enum OrderStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    // Set by RefundServiceImpl once an order's full total has been refunded
    // (see RefundServiceImpl#createRefund). A partially refunded order stays
    // COMPLETED until nothing is left to give back.
    REFUNDED
}

package com.inshore.payment.exception;

public class RefundAmountExceededException extends RuntimeException {
    public RefundAmountExceededException(String message) {
        super(message);
    }
}

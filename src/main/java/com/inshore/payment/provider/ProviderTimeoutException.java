package com.inshore.payment.provider;

public class ProviderTimeoutException extends ProviderUnavailableException {
    public ProviderTimeoutException(String message) {
        super(message);
    }
}

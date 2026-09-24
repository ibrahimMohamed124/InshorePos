package com.inshore.payment.provider;

/** The provider could not be reached / did not answer: the outcome of the call is UNKNOWN, not failed. */
public class ProviderUnavailableException extends RuntimeException {
    public ProviderUnavailableException(String message) {
        super(message);
    }
}

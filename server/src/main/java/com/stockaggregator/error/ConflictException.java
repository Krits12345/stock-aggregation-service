package com.stockaggregator.error;

/** 409 - e.g. signing up with an email that already exists. */
public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(message, 409, "conflict");
    }
}

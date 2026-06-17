package com.stockaggregator.error;

/** 404 - symbol not found. */
public class NotFoundException extends ApiException {
    public NotFoundException(String message) {
        super(message, 404, "not_found");
    }
}

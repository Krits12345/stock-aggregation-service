package com.stockaggregator.error;

/** 401 - missing or invalid bearer token / bad credentials. */
public class UnauthorizedException extends ApiException {
    public UnauthorizedException(String message) {
        super(message, 401, "unauthorized");
    }
}

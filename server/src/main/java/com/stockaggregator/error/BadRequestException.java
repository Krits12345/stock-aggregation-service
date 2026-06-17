package com.stockaggregator.error;

/** 400 - missing/invalid parameter, bad timeframe, bad date range. */
public class BadRequestException extends ApiException {
    public BadRequestException(String message) {
        super(message, 400, "bad_request");
    }
}

package com.stockaggregator.error;

/**
 * Base for errors we turn into JSON responses with a specific status code.
 * The {@code error} string is a short machine-readable code (e.g. "bad_request").
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;

    public ApiException(String message, int status, String error) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public int getStatus() { return status; }

    public String getError() { return error; }
}

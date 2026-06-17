package com.stockaggregator.web.dto;

/** JSON body returned for any error: {"error", "message", "status"}. */
public record ErrorResponse(String error, String message, int status) {
}

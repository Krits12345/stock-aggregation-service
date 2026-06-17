package com.stockaggregator.web.dto;

/** One candle as it appears in the JSON response (datetime is ISO 8601, UTC). */
public record CandleDto(
        String datetime,
        double open,
        double high,
        double low,
        double close,
        long volume) {
}

package com.stockaggregator.web.dto;

import java.util.List;

/** Top-level response for GET /api/v1/candles. */
public record CandlesResponse(
        String symbol,
        String timeframe,
        List<CandleDto> candles,
        int count,
        Pagination pagination) {
}

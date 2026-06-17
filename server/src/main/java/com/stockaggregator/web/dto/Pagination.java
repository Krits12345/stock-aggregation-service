package com.stockaggregator.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Pagination block included with every candle response. */
public record Pagination(
        int page,
        @JsonProperty("page_size") int pageSize,
        @JsonProperty("total_candles") int totalCandles,
        @JsonProperty("total_pages") int totalPages) {
}

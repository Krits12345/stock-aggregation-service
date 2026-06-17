package com.stockaggregator.web;

import com.stockaggregator.service.CandleService;
import com.stockaggregator.web.dto.CandlesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP layer for the candle endpoint. Validation lives in the service. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "candles")
public class CandleController {

    private final CandleService service;

    public CandleController(CandleService service) {
        this.service = service;
    }

    @GetMapping("/candles")
    @Operation(summary = "Return OHLCV candles aggregated to the requested timeframe")
    public CandlesResponse getCandles(
            @Parameter(description = "Stock ticker symbol", example = "RELIANCE")
            @RequestParam(required = false) String symbol,

            @Parameter(description = "Target timeframe: 1m, 5m, 15m, 30m, 1h, 1d", example = "15m")
            @RequestParam(required = false) String timeframe,

            @Parameter(description = "yyyy-MM-dd HH:mm:ss or ISO 8601", example = "2026-01-01 09:15:00")
            @RequestParam(name = "start_date", required = false) String startDate,

            @Parameter(description = "yyyy-MM-dd HH:mm:ss or ISO 8601", example = "2026-01-01 15:30:00")
            @RequestParam(name = "end_date", required = false) String endDate,

            @Parameter(description = "Page number (1-based)")
            @RequestParam(defaultValue = "1") int page,

            @Parameter(description = "Candles per page (1-1000)")
            @RequestParam(name = "page_size", defaultValue = "" + CandleService.DEFAULT_PAGE_SIZE) int pageSize) {

        return service.getCandles(symbol, timeframe, startDate, endDate, page, pageSize);
    }
}

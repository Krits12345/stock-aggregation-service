package com.stockaggregator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stockaggregator.aggregation.Aggregator;
import com.stockaggregator.aggregation.Candle;
import com.stockaggregator.config.CacheProperties;
import com.stockaggregator.error.BadRequestException;
import com.stockaggregator.error.NotFoundException;
import com.stockaggregator.repository.CandleRepository;
import com.stockaggregator.web.dto.CandleDto;
import com.stockaggregator.web.dto.CandlesResponse;
import com.stockaggregator.web.dto.Pagination;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Business logic: validate input, fetch, aggregate, cache, paginate. */
@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);

    static final int MAX_PAGE_SIZE = 1000;
    public static final int DEFAULT_PAGE_SIZE = 500;

    // Accepted start_date/end_date formats. All are interpreted as UTC.
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

    private static final DateTimeFormatter ISO_OUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private final CandleRepository repository;
    private final Cache<String, List<Candle>> cache;

    public CandleService(CandleRepository repository, CacheProperties cacheProps) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(cacheProps.getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(cacheProps.getTtlSeconds()))
                .build();
    }

    public CandlesResponse getCandles(String symbol, String timeframe,
                                      String startDate, String endDate,
                                      int page, int pageSize) {
        Request req = validate(symbol, timeframe, startDate, endDate, page, pageSize);

        String key = String.join("|", req.symbol, req.timeframe,
                req.start.toString(), req.end.toString());
        List<Candle> candles = cache.getIfPresent(key);
        boolean hit = candles != null;

        if (!hit) {
            List<Candle> raw = repository.fetch1m(req.symbol, req.start, req.end);
            if (raw.isEmpty() && !repository.symbolExists(req.symbol)) {
                throw new NotFoundException("Symbol not found: " + req.symbol);
            }
            candles = Aggregator.aggregate(raw, req.timeframe);
            cache.put(key, candles);
        }

        log.info("candles symbol={} tf={} range=[{}..{}] total={} cache={}",
                req.symbol, req.timeframe, req.start, req.end, candles.size(), hit ? "HIT" : "MISS");

        return paginate(req.symbol, req.timeframe, candles, req.page, req.pageSize);
    }

    private Request validate(String symbol, String timeframe,
                             String startDate, String endDate, int page, int pageSize) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("Missing required parameter: symbol");
        }
        if (timeframe == null || timeframe.isBlank()) {
            throw new BadRequestException("Missing required parameter: timeframe");
        }
        if (startDate == null || startDate.isBlank()) {
            throw new BadRequestException("Missing required parameter: start_date");
        }
        if (endDate == null || endDate.isBlank()) {
            throw new BadRequestException("Missing required parameter: end_date");
        }

        symbol = symbol.trim().toUpperCase();

        if (!Aggregator.isSupported(timeframe)) {
            throw new BadRequestException("Unsupported timeframe: '" + timeframe + "'. Supported: "
                    + String.join(", ", Aggregator.SUPPORTED_TIMEFRAMES));
        }

        Instant start = parseDateTime(startDate, "start_date");
        Instant end = parseDateTime(endDate, "end_date");
        if (start.isAfter(end)) {
            throw new BadRequestException("Invalid date range: start_date must be <= end_date");
        }

        if (page < 1) {
            throw new BadRequestException("page must be >= 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BadRequestException("page_size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return new Request(symbol, timeframe, start, end, page, pageSize);
    }

    private CandlesResponse paginate(String symbol, String timeframe,
                                     List<Candle> candles, int page, int pageSize) {
        int total = candles.size();
        int totalPages = Math.max(1, (total + pageSize - 1) / pageSize);
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);

        List<CandleDto> window = new ArrayList<>();
        for (Candle c : candles.subList(from, to)) {
            window.add(new CandleDto(ISO_OUT.format(c.getDatetime()),
                    c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume()));
        }

        return new CandlesResponse(symbol, timeframe, window, window.size(),
                new Pagination(page, pageSize, total, totalPages));
    }

    private static Instant parseDateTime(String value, String field) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(value.trim(), fmt).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        throw new BadRequestException(
                "Invalid " + field + ": '" + value + "'. Expected 'yyyy-MM-dd HH:mm:ss' or ISO 8601.");
    }

    /** Validated, parsed request parameters. */
    private record Request(String symbol, String timeframe,
                           Instant start, Instant end, int page, int pageSize) {
    }
}

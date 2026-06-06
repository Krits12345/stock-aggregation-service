"""Business logic: validate input, aggregate, cache, paginate."""
from __future__ import annotations

import logging
from datetime import datetime, timezone

from cachetools import TTLCache

from .aggregation import SUPPORTED_TIMEFRAMES, aggregate, Candle
from .errors import BadRequestError, NotFoundError
from .repository import CandleRepository

log = logging.getLogger("service")

DATE_FORMATS = ("%Y-%m-%d %H:%M:%S", "%Y-%m-%dT%H:%M:%S", "%Y-%m-%dT%H:%M:%SZ")

MAX_PAGE_SIZE = 1000
DEFAULT_PAGE_SIZE = 500


def parse_datetime(value: str, field: str) -> datetime:
    for fmt in DATE_FORMATS:
        try:
            return datetime.strptime(value, fmt).replace(tzinfo=timezone.utc)
        except ValueError:
            continue
    raise BadRequestError(
        f"Invalid {field}: '{value}'. Expected 'yyyy-MM-dd HH:mm:ss' or ISO 8601."
    )


def to_iso(dt: datetime) -> str:
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


class CandleService:
    def __init__(self, repository: CandleRepository, cache_ttl=60, cache_size=256):
        self._repo = repository
        self._cache = TTLCache(maxsize=cache_size, ttl=cache_ttl)

    def get_candles(self, symbol, timeframe, start_date, end_date,
                    page=1, page_size=DEFAULT_PAGE_SIZE) -> dict:
        symbol, timeframe, start, end, page, page_size = self._validate(
            symbol, timeframe, start_date, end_date, page, page_size
        )

        key = (symbol, timeframe, start.isoformat(), end.isoformat())
        candles = self._cache.get(key)
        hit = candles is not None

        if not hit:
            raw = self._repo.fetch_1m(symbol, start, end)
            if not raw and not self._repo.symbol_exists(symbol):
                raise NotFoundError(f"Symbol not found: {symbol}")
            candles = aggregate(raw, timeframe)
            self._cache[key] = candles

        log.info("candles symbol=%s tf=%s range=[%s..%s] total=%d cache=%s",
                 symbol, timeframe, start, end, len(candles), "HIT" if hit else "MISS")

        return self._paginate(symbol, timeframe, candles, page, page_size)

    def _validate(self, symbol, timeframe, start_date, end_date, page, page_size):
        if not symbol or not symbol.strip():
            raise BadRequestError("Missing required parameter: symbol")
        if not timeframe:
            raise BadRequestError("Missing required parameter: timeframe")
        if not start_date:
            raise BadRequestError("Missing required parameter: start_date")
        if not end_date:
            raise BadRequestError("Missing required parameter: end_date")

        symbol = symbol.strip().upper()

        if timeframe not in SUPPORTED_TIMEFRAMES:
            raise BadRequestError(
                f"Unsupported timeframe: '{timeframe}'. "
                f"Supported: {', '.join(SUPPORTED_TIMEFRAMES)}"
            )

        start = parse_datetime(start_date, "start_date")
        end = parse_datetime(end_date, "end_date")
        if start > end:
            raise BadRequestError("Invalid date range: start_date must be <= end_date")

        try:
            page, page_size = int(page), int(page_size)
        except (TypeError, ValueError):
            raise BadRequestError("page and page_size must be integers")
        if page < 1:
            raise BadRequestError("page must be >= 1")
        if not 1 <= page_size <= MAX_PAGE_SIZE:
            raise BadRequestError(f"page_size must be between 1 and {MAX_PAGE_SIZE}")

        return symbol, timeframe, start, end, page, page_size

    def _paginate(self, symbol, timeframe, candles: list[Candle], page, page_size) -> dict:
        total = len(candles)
        total_pages = max(1, (total + page_size - 1) // page_size)
        start = (page - 1) * page_size
        window = candles[start:start + page_size]

        return {
            "symbol": symbol,
            "timeframe": timeframe,
            "candles": [
                {
                    "datetime": to_iso(c.datetime),
                    "open": c.open,
                    "high": c.high,
                    "low": c.low,
                    "close": c.close,
                    "volume": c.volume,
                }
                for c in window
            ],
            "count": len(window),
            "pagination": {
                "page": page,
                "page_size": page_size,
                "total_candles": total,
                "total_pages": total_pages,
            },
        }

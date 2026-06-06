"""OHLCV aggregation.

No Flask/Cassandra imports here on purpose, so the logic stays unit-testable
on its own.

Rules:
    open   = first open in the window
    high   = max high
    low    = min low
    close  = last close
    volume = sum of volume
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone


# target timeframe -> window size in seconds. "1d" is special-cased below.
TIMEFRAME_SECONDS = {
    "1m": 60,
    "5m": 5 * 60,
    "15m": 15 * 60,
    "30m": 30 * 60,
    "1h": 60 * 60,
    "1d": 24 * 60 * 60,
}

SUPPORTED_TIMEFRAMES = tuple(TIMEFRAME_SECONDS.keys())


@dataclass
class Candle:
    datetime: datetime
    open: float
    high: float
    low: float
    close: float
    volume: int


def bucket_start(ts: datetime, timeframe: str) -> datetime:
    """Start of the window that ts belongs to.

    Intraday frames snap to clock boundaries (5m -> :00/:05/...), 1d to midnight.
    """
    if ts.tzinfo is None:
        ts = ts.replace(tzinfo=timezone.utc)

    if timeframe == "1d":
        return ts.replace(hour=0, minute=0, second=0, microsecond=0)

    size = TIMEFRAME_SECONDS[timeframe]
    epoch = int(ts.timestamp())
    return datetime.fromtimestamp(epoch - (epoch % size), tz=timezone.utc)


def aggregate(candles: list[Candle], timeframe: str) -> list[Candle]:
    """Roll up 1-minute candles (sorted ascending) into the target timeframe."""
    if timeframe not in TIMEFRAME_SECONDS:
        raise ValueError(f"Unsupported timeframe: {timeframe}")

    if timeframe == "1m" or not candles:
        return list(candles)

    out: list[Candle] = []
    current: Candle | None = None
    current_key: datetime | None = None

    for c in candles:
        key = bucket_start(c.datetime, timeframe)
        if current is None or key != current_key:
            if current is not None:
                out.append(current)
            current = Candle(key, c.open, c.high, c.low, c.close, c.volume)
            current_key = key
        else:
            current.high = max(current.high, c.high)
            current.low = min(current.low, c.low)
            current.close = c.close
            current.volume += c.volume

    if current is not None:
        out.append(current)
    return out

"""Unit tests for the pure OHLCV aggregation logic."""
from __future__ import annotations

from datetime import datetime, timedelta, timezone

import pytest

from app.aggregation import Candle, aggregate, bucket_start


def _c(minute_offset: int, o, h, l, c, v) -> Candle:
    base = datetime(2026, 1, 1, 9, 15, 0, tzinfo=timezone.utc)
    return Candle(datetime=base + timedelta(minutes=minute_offset),
                  open=o, high=h, low=l, close=c, volume=v)


def _minute_series(n: int) -> list[Candle]:
    """n consecutive 1-minute candles with predictable values."""
    out = []
    for i in range(n):
        out.append(_c(i, o=100 + i, h=100 + i + 2, l=100 + i - 2, c=100 + i + 1, v=10 * (i + 1)))
    return out


def test_1m_returns_raw_unchanged():
    series = _minute_series(5)
    result = aggregate(series, "1m")
    assert result == series


def test_5m_ohlcv_rules():
    # 10 one-minute candles -> two 5-minute candles.
    series = _minute_series(10)
    result = aggregate(series, "5m")
    assert len(result) == 2

    first = result[0]
    # Open = first open in window
    assert first.open == series[0].open
    # Close = last close in window
    assert first.close == series[4].close
    # High = max high in window
    assert first.high == max(c.high for c in series[:5])
    # Low = min low in window
    assert first.low == min(c.low for c in series[:5])
    # Volume = sum of volume in window
    assert first.volume == sum(c.volume for c in series[:5])


def test_bucket_alignment_5m():
    # 09:17 should fall into the 09:15 bucket (epoch-aligned 5m).
    ts = datetime(2026, 1, 1, 9, 17, 30, tzinfo=timezone.utc)
    assert bucket_start(ts, "5m") == datetime(2026, 1, 1, 9, 15, 0, tzinfo=timezone.utc)


def test_bucket_alignment_1h():
    ts = datetime(2026, 1, 1, 9, 47, 0, tzinfo=timezone.utc)
    assert bucket_start(ts, "1h") == datetime(2026, 1, 1, 9, 0, 0, tzinfo=timezone.utc)


def test_1d_groups_by_calendar_day():
    day1 = _minute_series(3)
    day2 = [Candle(datetime=datetime(2026, 1, 2, 9, 15, tzinfo=timezone.utc) + timedelta(minutes=i),
                   open=200 + i, high=205 + i, low=195 + i, close=201 + i, volume=5)
            for i in range(3)]
    result = aggregate(day1 + day2, "1d")
    assert len(result) == 2
    assert result[0].datetime == datetime(2026, 1, 1, 0, 0, tzinfo=timezone.utc)
    assert result[1].datetime == datetime(2026, 1, 2, 0, 0, tzinfo=timezone.utc)
    assert result[0].volume == sum(c.volume for c in day1)


def test_partial_window_is_emitted():
    # 7 minutes into 5m -> one full window + one partial window.
    series = _minute_series(7)
    result = aggregate(series, "5m")
    assert len(result) == 2
    assert result[1].volume == sum(c.volume for c in series[5:])


def test_empty_input():
    assert aggregate([], "15m") == []


def test_unsupported_timeframe_raises():
    with pytest.raises(ValueError):
        aggregate(_minute_series(3), "7m")


def test_high_low_track_extremes_across_window():
    series = [
        _c(0, o=100, h=110, l=99, c=105, v=10),
        _c(1, o=105, h=108, l=95, c=100, v=20),   # lowest low
        _c(2, o=100, h=120, l=101, c=118, v=30),  # highest high
        _c(3, o=118, h=119, l=117, c=118, v=40),
        _c(4, o=118, h=118, l=116, c=117, v=50),
    ]
    [candle] = aggregate(series, "5m")
    assert candle.open == 100
    assert candle.close == 117
    assert candle.high == 120
    assert candle.low == 95
    assert candle.volume == 150

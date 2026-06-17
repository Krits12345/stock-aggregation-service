package com.stockaggregator.aggregation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the pure OHLCV aggregation logic. */
class AggregatorTest {

    private static final Instant BASE =
            Instant.parse("2026-01-01T09:15:00Z");

    private static Candle candle(int minuteOffset, double o, double h, double l, double c, long v) {
        return new Candle(BASE.plusSeconds(minuteOffset * 60L), o, h, l, c, v);
    }

    /** n consecutive 1-minute candles with predictable values. */
    private static List<Candle> minuteSeries(int n) {
        List<Candle> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(candle(i, 100 + i, 100 + i + 2, 100 + i - 2, 100 + i + 1, 10L * (i + 1)));
        }
        return out;
    }

    @Test
    void rawDataReturnedUnchangedFor1m() {
        List<Candle> series = minuteSeries(5);
        assertEquals(series, Aggregator.aggregate(series, "1m"));
    }

    @Test
    void fiveMinuteWindowFollowsOhlcvRules() {
        List<Candle> series = minuteSeries(10);   // -> two 5-minute candles
        List<Candle> result = Aggregator.aggregate(series, "5m");
        assertEquals(2, result.size());

        Candle first = result.get(0);
        assertEquals(series.get(0).getOpen(), first.getOpen());            // open = first open
        assertEquals(series.get(4).getClose(), first.getClose());          // close = last close
        assertEquals(maxHigh(series, 0, 5), first.getHigh());              // high = max high
        assertEquals(minLow(series, 0, 5), first.getLow());                // low = min low
        assertEquals(sumVolume(series, 0, 5), first.getVolume());          // volume = sum
    }

    @Test
    void fiveMinuteBucketSnapsToClockBoundary() {
        Instant ts = Instant.parse("2026-01-01T09:17:30Z");
        assertEquals(Instant.parse("2026-01-01T09:15:00Z"), Aggregator.bucketStart(ts, "5m"));
    }

    @Test
    void oneHourBucketSnapsToTheHour() {
        Instant ts = Instant.parse("2026-01-01T09:47:00Z");
        assertEquals(Instant.parse("2026-01-01T09:00:00Z"), Aggregator.bucketStart(ts, "1h"));
    }

    @Test
    void oneDayGroupsByCalendarDay() {
        List<Candle> day1 = minuteSeries(3);
        List<Candle> day2 = new ArrayList<>();
        Instant base2 = Instant.parse("2026-01-02T09:15:00Z");
        for (int i = 0; i < 3; i++) {
            day2.add(new Candle(base2.plusSeconds(i * 60L), 200 + i, 205 + i, 195 + i, 201 + i, 5));
        }
        List<Candle> all = new ArrayList<>(day1);
        all.addAll(day2);

        List<Candle> result = Aggregator.aggregate(all, "1d");
        assertEquals(2, result.size());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), result.get(0).getDatetime());
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), result.get(1).getDatetime());
        assertEquals(sumVolume(day1, 0, day1.size()), result.get(0).getVolume());
    }

    @Test
    void partialTrailingWindowIsEmitted() {
        List<Candle> series = minuteSeries(7);   // one full 5m window + one partial
        List<Candle> result = Aggregator.aggregate(series, "5m");
        assertEquals(2, result.size());
        assertEquals(sumVolume(series, 5, 7), result.get(1).getVolume());
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals(List.of(), Aggregator.aggregate(List.of(), "15m"));
    }

    @Test
    void unsupportedTimeframeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Aggregator.aggregate(minuteSeries(3), "7m"));
    }

    @Test
    void highAndLowTrackExtremesAcrossWindow() {
        List<Candle> series = List.of(
                candle(0, 100, 110, 99, 105, 10),
                candle(1, 105, 108, 95, 100, 20),   // lowest low
                candle(2, 100, 120, 101, 118, 30),  // highest high
                candle(3, 118, 119, 117, 118, 40),
                candle(4, 118, 118, 116, 117, 50));

        List<Candle> result = Aggregator.aggregate(series, "5m");
        assertEquals(1, result.size());
        Candle c = result.get(0);
        assertEquals(100, c.getOpen());
        assertEquals(117, c.getClose());
        assertEquals(120, c.getHigh());
        assertEquals(95, c.getLow());
        assertEquals(150, c.getVolume());
    }

    private static double maxHigh(List<Candle> s, int from, int to) {
        return s.subList(from, to).stream().mapToDouble(Candle::getHigh).max().orElseThrow();
    }

    private static double minLow(List<Candle> s, int from, int to) {
        return s.subList(from, to).stream().mapToDouble(Candle::getLow).min().orElseThrow();
    }

    private static long sumVolume(List<Candle> s, int from, int to) {
        return s.subList(from, to).stream().mapToLong(Candle::getVolume).sum();
    }
}

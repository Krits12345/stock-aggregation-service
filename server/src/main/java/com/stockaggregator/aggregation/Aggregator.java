package com.stockaggregator.aggregation;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OHLCV aggregation. No Spring or Cassandra here on purpose, so the logic stays
 * unit-testable on its own.
 *
 * <p>Rules for rolling 1-minute candles into a larger window:
 * <ul>
 *   <li>open   = first open in the window</li>
 *   <li>high   = max high</li>
 *   <li>low    = min low</li>
 *   <li>close  = last close</li>
 *   <li>volume = sum of volume</li>
 * </ul>
 */
public final class Aggregator {

    /** Target timeframe -> window size in seconds. "1d" is special-cased below. */
    private static final Map<String, Long> TIMEFRAME_SECONDS = new LinkedHashMap<>();
    static {
        TIMEFRAME_SECONDS.put("1m", 60L);
        TIMEFRAME_SECONDS.put("5m", 5 * 60L);
        TIMEFRAME_SECONDS.put("15m", 15 * 60L);
        TIMEFRAME_SECONDS.put("30m", 30 * 60L);
        TIMEFRAME_SECONDS.put("1h", 60 * 60L);
        TIMEFRAME_SECONDS.put("1d", 24 * 60 * 60L);
    }

    public static final List<String> SUPPORTED_TIMEFRAMES =
            List.copyOf(TIMEFRAME_SECONDS.keySet());

    private Aggregator() {
    }

    public static boolean isSupported(String timeframe) {
        return TIMEFRAME_SECONDS.containsKey(timeframe);
    }

    /**
     * Start of the window that {@code ts} belongs to. Intraday frames snap to
     * clock boundaries (5m -> :00/:05/...), 1d to midnight UTC.
     */
    public static Instant bucketStart(Instant ts, String timeframe) {
        if ("1d".equals(timeframe)) {
            return ts.truncatedTo(ChronoUnit.DAYS);
        }
        long size = TIMEFRAME_SECONDS.get(timeframe);
        long epoch = ts.getEpochSecond();
        return Instant.ofEpochSecond(epoch - Math.floorMod(epoch, size));
    }

    /** Roll up 1-minute candles (sorted ascending) into the target timeframe. */
    public static List<Candle> aggregate(List<Candle> candles, String timeframe) {
        if (!isSupported(timeframe)) {
            throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
        }
        // 1m is the raw data; nothing to roll up.
        if ("1m".equals(timeframe) || candles.isEmpty()) {
            return new ArrayList<>(candles);
        }

        List<Candle> out = new ArrayList<>();
        Candle current = null;
        Instant currentKey = null;

        for (Candle c : candles) {
            Instant key = bucketStart(c.getDatetime(), timeframe);
            if (current == null || !key.equals(currentKey)) {
                if (current != null) {
                    out.add(current);
                }
                current = new Candle(key, c.getOpen(), c.getHigh(), c.getLow(), c.getClose(), c.getVolume());
                currentKey = key;
            } else {
                current.setHigh(Math.max(current.getHigh(), c.getHigh()));
                current.setLow(Math.min(current.getLow(), c.getLow()));
                current.setClose(c.getClose());
                current.setVolume(current.getVolume() + c.getVolume());
            }
        }

        if (current != null) {
            out.add(current);
        }
        return out;
    }
}

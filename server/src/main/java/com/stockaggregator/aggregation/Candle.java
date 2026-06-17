package com.stockaggregator.aggregation;

import java.time.Instant;
import java.util.Objects;

/**
 * A single OHLCV candle. Fields are mutable because {@link Aggregator} updates
 * the running high/low/close/volume as it rolls minutes into a larger window.
 */
public class Candle {

    private Instant datetime;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;

    public Candle(Instant datetime, double open, double high, double low, double close, long volume) {
        this.datetime = datetime;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Instant getDatetime() { return datetime; }
    public void setDatetime(Instant datetime) { this.datetime = datetime; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Candle other)) return false;
        return Double.compare(open, other.open) == 0
                && Double.compare(high, other.high) == 0
                && Double.compare(low, other.low) == 0
                && Double.compare(close, other.close) == 0
                && volume == other.volume
                && Objects.equals(datetime, other.datetime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(datetime, open, high, low, close, volume);
    }
}

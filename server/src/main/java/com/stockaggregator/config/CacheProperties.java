package com.stockaggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the in-process candle cache (prefix {@code cache.*}). */
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    private long ttlSeconds = 60;
    private long maxSize = 256;

    public long getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }

    public long getMaxSize() { return maxSize; }
    public void setMaxSize(long maxSize) { this.maxSize = maxSize; }
}

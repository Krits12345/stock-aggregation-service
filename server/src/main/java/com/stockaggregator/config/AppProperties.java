package com.stockaggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Miscellaneous app toggles (prefix {@code app.*}). */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Gate /api/v1/candles behind a bearer token when true. */
    private boolean requireAuth = false;

    public boolean isRequireAuth() { return requireAuth; }
    public void setRequireAuth(boolean requireAuth) { this.requireAuth = requireAuth; }
}

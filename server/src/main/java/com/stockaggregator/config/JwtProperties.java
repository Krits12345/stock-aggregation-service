package com.stockaggregator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT signing settings (prefix {@code jwt.*}). */
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret = "dev-secret-change-me";
    private int ttlHours = 12;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public int getTtlHours() { return ttlHours; }
    public void setTtlHours(int ttlHours) { this.ttlHours = ttlHours; }
}

package com.stockaggregator.web;

import com.stockaggregator.config.AppProperties;
import com.stockaggregator.service.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS for the Vue client, plus the optional auth gate on /candles. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties appProps;
    private final AuthService auth;

    public WebConfig(AppProperties appProps, AuthService auth) {
        this.appProps = appProps;
        this.auth = auth;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Protect /candles only when REQUIRE_AUTH is on, so the default behaviour
        // (and the CLI client) keep working without a token.
        if (appProps.isRequireAuth()) {
            registry.addInterceptor(new AuthInterceptor(auth)).addPathPatterns("/api/v1/candles");
        }
    }
}

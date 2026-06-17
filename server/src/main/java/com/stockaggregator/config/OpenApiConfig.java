package com.stockaggregator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Title/description shown in the Swagger UI at /apidocs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI apiInfo() {
        return new OpenAPI().info(new Info()
                .title("Stock Market Data Aggregation Service")
                .description("Returns OHLCV candles aggregated to a requested timeframe.")
                .version("1.0.0"));
    }
}

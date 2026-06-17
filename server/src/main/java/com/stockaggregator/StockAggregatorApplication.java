package com.stockaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the aggregation service.
 *
 * <p>Run the API:   {@code mvn spring-boot:run}
 * <p>Run the loader: {@code mvn spring-boot:run -Dspring-boot.run.profiles=loader}
 * (the loader applies the schema, ingests the CSV, then exits.)
 *
 * <p>We manage the Cassandra session ourselves (see {@code CassandraSession}) so we
 * exclude Boot's auto-configured one, which would otherwise connect eagerly at startup.
 */
@SpringBootApplication(exclude = CassandraAutoConfiguration.class)
@ConfigurationPropertiesScan
public class StockAggregatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockAggregatorApplication.class, args);
    }
}

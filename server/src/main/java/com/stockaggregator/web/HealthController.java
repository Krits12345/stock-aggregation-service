package com.stockaggregator.web;

import com.stockaggregator.repository.CandleRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reports whether the app is up and whether Cassandra is reachable. */
@RestController
@Tag(name = "system")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final CandleRepository repository;

    public HealthController(CandleRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "UP");
        try {
            repository.ping();
            body.put("cassandra", "UP");
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn("health check failed: {}", e.getMessage());
            body.put("cassandra", "DOWN");
            body.put("detail", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}

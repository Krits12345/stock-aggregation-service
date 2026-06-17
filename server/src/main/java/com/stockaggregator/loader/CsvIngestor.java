package com.stockaggregator.loader;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.AsyncResultSet;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.stockaggregator.config.CassandraProperties;
import com.stockaggregator.config.CassandraSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Loads stock_data.csv into {@code candles_1m}. One-time job, safe to re-run
 * (inserts upsert on the primary key). Bad rows are logged and skipped.
 */
@Component
@Profile("loader")
public class CsvIngestor {

    private static final Logger log = LoggerFactory.getLogger(CsvIngestor.class);
    private static final DateTimeFormatter CSV_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_IN_FLIGHT = 256;

    private final CassandraSession sessionProvider;
    private final String keyspace;

    public CsvIngestor(CassandraSession sessionProvider, CassandraProperties props) {
        this.sessionProvider = sessionProvider;
        this.keyspace = props.getKeyspace();
    }

    public void ingest(Path csvFile) throws IOException {
        CqlSession session = sessionProvider.get();
        PreparedStatement insert = session.prepare(
                "INSERT INTO " + keyspace + ".candles_1m "
                        + "(symbol, date, datetime, open, high, low, close, volume) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)");

        log.info("ingesting {}", csvFile.toAbsolutePath());
        long inserted = 0;
        List<CompletionStage<AsyncResultSet>> inFlight = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvFile)) {
            reader.readLine();   // skip header
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    inFlight.add(session.executeAsync(bindRow(insert, line.split(","))));
                    inserted++;
                } catch (RuntimeException e) {
                    log.warn("skipping bad row {}: {}", lineNo, e.getMessage());
                }
                // Keep a bounded number of writes in flight so we don't overrun the driver.
                if (inFlight.size() >= MAX_IN_FLIGHT) {
                    awaitAll(inFlight);
                    inFlight.clear();
                }
            }
        }
        awaitAll(inFlight);
        log.info("done, {} rows in {}.candles_1m", inserted, keyspace);
    }

    /** Map one CSV row (symbol,datetime,open,high,low,close,volume) to a bound insert. */
    private static BoundStatement bindRow(PreparedStatement insert, String[] f) {
        LocalDateTime dt = LocalDateTime.parse(f[1].trim(), CSV_DATETIME);
        return insert.bind()
                .setString(0, f[0].trim().toUpperCase())
                .setLocalDate(1, dt.toLocalDate())
                .setInstant(2, dt.toInstant(ZoneOffset.UTC))
                .setDouble(3, Double.parseDouble(f[2]))
                .setDouble(4, Double.parseDouble(f[3]))
                .setDouble(5, Double.parseDouble(f[4]))
                .setDouble(6, Double.parseDouble(f[5]))
                .setLong(7, (long) Double.parseDouble(f[6]));
    }

    private static void awaitAll(List<CompletionStage<AsyncResultSet>> inFlight) {
        for (CompletionStage<AsyncResultSet> stage : inFlight) {
            stage.toCompletableFuture().join();
        }
    }
}

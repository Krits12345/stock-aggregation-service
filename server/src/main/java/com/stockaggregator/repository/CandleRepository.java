package com.stockaggregator.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.stockaggregator.aggregation.Candle;
import com.stockaggregator.config.CassandraProperties;
import com.stockaggregator.config.CassandraSession;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads 1-minute candles from {@code candles_1m}.
 *
 * <p>Statements are keyspace-qualified and prepared once, on first use.
 */
@Repository
public class CandleRepository {

    private final CassandraSession sessionProvider;
    private final String keyspace;

    private volatile PreparedStatement selectStmt;
    private volatile PreparedStatement existsStmt;

    public CandleRepository(CassandraSession sessionProvider, CassandraProperties props) {
        this.sessionProvider = sessionProvider;
        this.keyspace = props.getKeyspace();
    }

    /** Lightweight round-trip used by the health check. */
    public void ping() {
        sessionProvider.get().execute("SELECT release_version FROM system.local");
    }

    public boolean symbolExists(String symbol) {
        CqlSession session = sessionProvider.get();
        if (existsStmt == null) {
            existsStmt = session.prepare(
                    "SELECT symbol FROM " + keyspace + ".candles_1m "
                            + "WHERE symbol = ? LIMIT 1 ALLOW FILTERING");
        }
        return session.execute(existsStmt.bind(symbol)).one() != null;
    }

    /**
     * Raw 1-minute candles for the inclusive range [start, end], ordered by time.
     *
     * <p>Only the day-partitions in range are queried (IN on the bucket key), and
     * each is read in clustering order -- no full-table scan.
     */
    public List<Candle> fetch1m(String symbol, Instant start, Instant end) {
        CqlSession session = sessionProvider.get();
        if (selectStmt == null) {
            selectStmt = session.prepare(
                    "SELECT datetime, open, high, low, close, volume "
                            + "FROM " + keyspace + ".candles_1m "
                            + "WHERE symbol = ? AND date IN ? AND datetime >= ? AND datetime <= ?");
        }

        BoundStatement bound = selectStmt.bind()
                .setString(0, symbol)
                .setList(1, dayBuckets(start, end), LocalDate.class)
                .setInstant(2, start)
                .setInstant(3, end);

        List<Candle> candles = new ArrayList<>();
        for (Row row : session.execute(bound)) {
            candles.add(new Candle(
                    row.getInstant("datetime"),
                    row.getDouble("open"),
                    row.getDouble("high"),
                    row.getDouble("low"),
                    row.getDouble("close"),
                    row.getLong("volume")));
        }
        // Rows come grouped by partition; sort into one ascending stream.
        candles.sort(Comparator.comparing(Candle::getDatetime));
        return candles;
    }

    /** Every trading-day bucket the range touches (UTC), inclusive. */
    private static List<LocalDate> dayBuckets(Instant start, Instant end) {
        LocalDate from = start.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate to = end.atOffset(ZoneOffset.UTC).toLocalDate();
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(d);
        }
        return days;
    }
}

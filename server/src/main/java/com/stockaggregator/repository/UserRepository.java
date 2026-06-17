package com.stockaggregator.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.stockaggregator.config.CassandraProperties;
import com.stockaggregator.config.CassandraSession;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** Reads/writes the {@code users} table for login and signup. */
@Repository
public class UserRepository {

    /** Minimal user record returned to the auth layer. */
    public record User(String email, String passwordHash) {
    }

    private final CassandraSession sessionProvider;
    private final String keyspace;

    private volatile PreparedStatement getStmt;
    private volatile PreparedStatement insertStmt;

    public UserRepository(CassandraSession sessionProvider, CassandraProperties props) {
        this.sessionProvider = sessionProvider;
        this.keyspace = props.getKeyspace();
    }

    public Optional<User> find(String email) {
        CqlSession session = sessionProvider.get();
        if (getStmt == null) {
            getStmt = session.prepare(
                    "SELECT email, password_hash FROM " + keyspace + ".users WHERE email = ?");
        }
        Row row = session.execute(getStmt.bind(email)).one();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new User(row.getString("email"), row.getString("password_hash")));
    }

    /**
     * Inserts a user. Returns false if the email is already taken -- the
     * lightweight transaction (IF NOT EXISTS) makes this race-safe.
     */
    public boolean create(String email, String passwordHash) {
        CqlSession session = sessionProvider.get();
        if (insertStmt == null) {
            insertStmt = session.prepare(
                    "INSERT INTO " + keyspace + ".users (email, password_hash, created_at) "
                            + "VALUES (?, ?, ?) IF NOT EXISTS");
        }
        Row row = session.execute(
                insertStmt.bind(email, passwordHash, Instant.now())).one();
        return row != null && row.getBoolean("[applied]");
    }
}

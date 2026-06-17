package com.stockaggregator.config;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import java.net.InetSocketAddress;

/**
 * Owns the single {@link CqlSession} for the app.
 *
 * <p>The connection is opened lazily on first use rather than at startup, so
 * the app still boots when Cassandra is momentarily down (the /health endpoint
 * then reports the real status). We connect without a default keyspace and let
 * the repositories use keyspace-qualified statements; this keeps one code path
 * for both the loader (keyspace may not exist yet) and the API.
 */
@Component
public class CassandraSession {

    private static final Logger log = LoggerFactory.getLogger(CassandraSession.class);

    private final CassandraProperties props;
    private volatile CqlSession session;

    public CassandraSession(CassandraProperties props) {
        this.props = props;
    }

    /** Returns the shared session, opening it on first call. */
    public CqlSession get() {
        CqlSession local = session;
        if (local == null) {
            synchronized (this) {
                local = session;
                if (local == null) {
                    local = connect();
                    session = local;
                }
            }
        }
        return local;
    }

    private CqlSession connect() {
        CqlSessionBuilder builder = CqlSession.builder()
        .withLocalDatacenter(props.getLocalDatacenter());
        
        for (String host : props.hostList()) {
            builder.addContactPoint(new InetSocketAddress(host, props.getPort()));
        }
        if (props.getUsername() != null && !props.getUsername().isBlank()) {
            builder.withAuthCredentials(props.getUsername(),
                    props.getPassword() == null ? "" : props.getPassword());
        }

        CqlSession s = builder.build();
        log.info("connected to cassandra {}:{}", props.hostList(), props.getPort());
        return s;
    }

    @PreDestroy
    public void close() {
        if (session != null) {
            session.close();
        }
    }
}

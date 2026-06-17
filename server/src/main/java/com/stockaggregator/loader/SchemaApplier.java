package com.stockaggregator.loader;

import com.datastax.oss.driver.api.core.CqlSession;
import com.stockaggregator.config.CassandraSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs schema.cql through the driver -- the same effect as {@code cqlsh -f schema.cql},
 * handy when cqlsh isn't around (e.g. the Docker loader container).
 */
@Component
@Profile("loader")
public class SchemaApplier {

    private static final Logger log = LoggerFactory.getLogger(SchemaApplier.class);

    private final CassandraSession sessionProvider;

    public SchemaApplier(CassandraSession sessionProvider) {
        this.sessionProvider = sessionProvider;
    }

    public void apply(Path schemaFile) throws IOException {
        log.info("applying schema from {}", schemaFile.toAbsolutePath());
        CqlSession session = sessionProvider.get();
        for (String statement : statements(schemaFile)) {
            log.info("> {}", firstLine(statement));
            session.execute(statement);
        }
        log.info("schema applied");
    }

    /** Split the file into executable statements, dropping comments and USE. */
    private static List<String> statements(Path schemaFile) throws IOException {
        StringBuilder body = new StringBuilder();
        for (String line : Files.readAllLines(schemaFile)) {
            if (!line.strip().startsWith("--")) {
                body.append(line).append('\n');
            }
        }
        List<String> result = new ArrayList<>();
        for (String part : body.toString().split(";")) {
            String stmt = part.strip();
            // USE is a no-op here: we connect keyspace-less and the CREATEs are qualified.
            if (!stmt.isEmpty() && !stmt.toUpperCase().startsWith("USE ")) {
                result.add(stmt);
            }
        }
        return result;
    }

    private static String firstLine(String statement) {
        String line = Arrays.stream(statement.split("\n")).findFirst().orElse(statement);
        return line.length() > 70 ? line.substring(0, 70) : line;
    }
}

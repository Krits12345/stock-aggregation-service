package com.stockaggregator.loader;

import com.stockaggregator.config.LoaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * The one-time loader. Active under the {@code loader} profile only:
 *
 * <pre>mvn spring-boot:run -Dspring-boot.run.profiles=loader</pre>
 *
 * Applies the schema, ingests the CSV, then exits (no web server starts).
 */
@Component
@Profile("loader")
public class LoaderRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoaderRunner.class);

    private final SchemaApplier schemaApplier;
    private final CsvIngestor ingestor;
    private final LoaderProperties props;
    private final ConfigurableApplicationContext context;

    public LoaderRunner(SchemaApplier schemaApplier, CsvIngestor ingestor,
                        LoaderProperties props, ConfigurableApplicationContext context) {
        this.schemaApplier = schemaApplier;
        this.ingestor = ingestor;
        this.props = props;
        this.context = context;
    }

    @Override
public void run(ApplicationArguments args) {
    int exitCode = 0;

    try {
        schemaApplier.apply(Path.of(props.getSchemaFile()));
        ingestor.ingest(Path.of(props.getCsv()));
    } catch (Exception e) {
        log.error("load failed: {}", e.getMessage(), e);
        exitCode = 1;
    }

    int finalExitCode = exitCode;
    System.exit(SpringApplication.exit(context, () -> finalExitCode));
}
}
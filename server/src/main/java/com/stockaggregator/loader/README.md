# loader/

The one-time data load: create the schema and ingest the CSV. This only runs under
the `loader` Spring profile, so it stays out of the way of the normal API:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=loader
```

In Docker this is the `loader` container, which runs once and exits before the API
starts.

- **SchemaApplier.java** - runs `schema.cql` through the driver, statement by
  statement. A stand-in for `cqlsh -f` so you don't need cqlsh installed.
- **CsvIngestor.java** - reads `stock_data.csv` and inserts the rows into
  `candles_1m`. Writes go out asynchronously with a cap on how many are in flight at
  once, so the load is quick without overrunning the driver. Bad rows are logged and
  skipped rather than killing the whole import, and re-running is safe (rows upsert on
  the primary key).
- **LoaderRunner.java** - ties the two together: apply schema, ingest, then exit with
  a status code (non-zero if something failed).

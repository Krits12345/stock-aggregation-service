# repository/

Everything that talks to Cassandra directly. We use the DataStax driver and write
the CQL by hand, so what runs against the database is right here, not hidden behind
a mapping layer.

Statements are keyspace-qualified (e.g. `stock_keyspace.candles_1m`) and prepared
once on first use. That's also why the session connects without a default keyspace:
the same code works for the loader (before the keyspace exists) and the API.

- **CandleRepository.java** - reads 1-minute candles for a symbol and date range.
  Instead of scanning the whole table it only hits the day-partitions the range
  touches (`date IN (...)`), reads each in time order, then merges them into one
  ascending list. Also has a small `ping()` for the health check and a
  `symbolExists` check used to tell "no data" apart from "unknown symbol".
- **UserRepository.java** - reads and writes the `users` table for login/signup.
  The insert uses `IF NOT EXISTS` so two people signing up with the same email at
  once can't both win.

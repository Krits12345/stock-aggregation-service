-- Schema for the stock aggregation service.
-- Apply with: cqlsh -f schema.cql

-- RF=1 is fine for a single local node. Use NetworkTopologyStrategy in prod.
CREATE KEYSPACE IF NOT EXISTS stock_keyspace
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE stock_keyspace;

-- 1-minute OHLCV candles.
--
-- Partition key (symbol, date): keeps a ticker together and buckets it by
-- trading day so partitions stay small (~375 rows/day) instead of growing
-- forever. Clustering on datetime keeps each day time-sorted for fast range
-- reads. A date-range query hits only the day buckets it spans:
--   WHERE symbol = ? AND date IN (...) AND datetime >= ? AND datetime <= ?
CREATE TABLE IF NOT EXISTS stock_keyspace.candles_1m (
    symbol   text,
    date     date,
    datetime timestamp,
    open     double,
    high     double,
    low      double,
    close    double,
    volume   bigint,
    PRIMARY KEY ((symbol, date), datetime)
) WITH CLUSTERING ORDER BY (datetime ASC);

-- Users for the web client's login/signup. Email is the partition key, so
-- lookups by email are a single-partition read. Passwords are stored hashed.
CREATE TABLE IF NOT EXISTS stock_keyspace.users (
    email         text PRIMARY KEY,
    password_hash text,
    created_at    timestamp
);

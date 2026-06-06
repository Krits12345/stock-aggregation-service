"""Load stock_data.csv into Cassandra. One-time job, safe to re-run.

    python ingest.py --csv ../data/stock_data.csv

Connection defaults come from the same env vars the service uses
(CASSANDRA_HOSTS, CASSANDRA_PORT, CASSANDRA_KEYSPACE, ...).
"""
from __future__ import annotations

import argparse
import csv
import logging
import os
import sys
from datetime import datetime

from cassandra.auth import PlainTextAuthProvider
from cassandra.cluster import Cluster
from cassandra.concurrent import execute_concurrent_with_args

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("ingest")

INSERT = (
    "INSERT INTO candles_1m (symbol, date, datetime, open, high, low, close, volume) "
    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
)


def parse_args(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    p = argparse.ArgumentParser(description="Ingest stock_data.csv into Cassandra")
    p.add_argument("--csv", default=os.path.join(here, "..", "data", "stock_data.csv"))
    p.add_argument("--hosts", default=os.getenv("CASSANDRA_HOSTS", "127.0.0.1"))
    p.add_argument("--port", type=int, default=int(os.getenv("CASSANDRA_PORT", "9042")))
    p.add_argument("--keyspace", default=os.getenv("CASSANDRA_KEYSPACE", "stock_keyspace"))
    p.add_argument("--username", default=os.getenv("CASSANDRA_USERNAME"))
    p.add_argument("--password", default=os.getenv("CASSANDRA_PASSWORD"))
    p.add_argument("--batch", type=int, default=500)
    return p.parse_args(argv)


def read_rows(path):
    with open(path, newline="", encoding="utf-8") as fh:
        for line_no, row in enumerate(csv.DictReader(fh), start=2):
            try:
                dt = datetime.strptime(row["datetime"].strip(), "%Y-%m-%d %H:%M:%S")
                yield (
                    row["symbol"].strip().upper(),
                    dt.date(),
                    dt,
                    float(row["open"]),
                    float(row["high"]),
                    float(row["low"]),
                    float(row["close"]),
                    int(float(row["volume"])),
                )
            except (KeyError, ValueError) as exc:
                log.warning("skipping bad row %d: %s", line_no, exc)


def main(argv):
    args = parse_args(argv)
    path = os.path.abspath(args.csv)
    if not os.path.exists(path):
        log.error("CSV not found: %s", path)
        return 1

    auth = PlainTextAuthProvider(args.username, args.password or "") if args.username else None
    hosts = [h.strip() for h in args.hosts.split(",") if h.strip()]

    log.info("connecting to %s:%s/%s", hosts, args.port, args.keyspace)
    cluster = Cluster(contact_points=hosts, port=args.port, auth_provider=auth)
    session = cluster.connect(args.keyspace)
    insert = session.prepare(INSERT)

    rows = list(read_rows(path))
    log.info("parsed %d rows from %s", len(rows), path)

    inserted = 0
    for i in range(0, len(rows), args.batch):
        for ok, result in execute_concurrent_with_args(session, insert, rows[i:i + args.batch],
                                                        concurrency=64):
            if ok:
                inserted += 1
            else:
                log.error("insert failed: %s", result)
        log.info("inserted %d / %d", inserted, len(rows))

    cluster.shutdown()
    log.info("done, %d rows in %s.candles_1m", inserted, args.keyspace)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

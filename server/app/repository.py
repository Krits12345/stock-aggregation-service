"""Cassandra access for the candle data."""
from __future__ import annotations

import logging
from datetime import date, datetime, timedelta

from cassandra.auth import PlainTextAuthProvider
from cassandra.cluster import Cluster, Session
from cassandra.query import SimpleStatement

from .aggregation import Candle
from .config import Config

log = logging.getLogger("repository")


class CandleRepository:
    """Reads 1-minute candles from candles_1m.

    Connects lazily on first use so the app still boots when Cassandra is
    momentarily down; /health reports the real status.
    """

    def __init__(self, config: Config):
        self._config = config
        self._cluster: Cluster | None = None
        self._session: Session | None = None
        self._select_stmt = None

    def _connect(self) -> Session:
        if self._session is not None:
            return self._session

        cfg = self._config
        auth = None
        if cfg.cassandra_username:
            auth = PlainTextAuthProvider(cfg.cassandra_username, cfg.cassandra_password or "")

        self._cluster = Cluster(contact_points=cfg.cassandra_hosts, port=cfg.cassandra_port,
                                auth_provider=auth)
        session = self._cluster.connect(cfg.cassandra_keyspace)
        self._select_stmt = session.prepare(
            "SELECT datetime, open, high, low, close, volume "
            "FROM candles_1m "
            "WHERE symbol = ? AND date IN ? AND datetime >= ? AND datetime <= ?"
        )
        self._session = session
        log.info("connected to cassandra %s:%s/%s", cfg.cassandra_hosts,
                 cfg.cassandra_port, cfg.cassandra_keyspace)
        return session

    def ping(self) -> None:
        self._connect().execute(SimpleStatement("SELECT release_version FROM system.local"))

    def close(self) -> None:  # pragma: no cover
        if self._cluster is not None:
            self._cluster.shutdown()

    @staticmethod
    def _day_buckets(start: datetime, end: datetime) -> list[date]:
        days = []
        d = start.date()
        while d <= end.date():
            days.append(d)
            d += timedelta(days=1)
        return days

    def symbol_exists(self, symbol: str) -> bool:
        stmt = SimpleStatement(
            "SELECT symbol FROM candles_1m WHERE symbol = %s LIMIT 1 ALLOW FILTERING"
        )
        return self._connect().execute(stmt, (symbol,)).one() is not None

    def fetch_1m(self, symbol: str, start: datetime, end: datetime) -> list[Candle]:
        """Raw 1-minute candles for [start, end], ordered by time.

        Only the day-partitions in range are queried (IN on the bucket key), and
        each is read in clustering order -- no full-table scan.
        """
        session = self._connect()
        rows = session.execute(self._select_stmt,
                               (symbol, self._day_buckets(start, end), start, end))
        candles = [Candle(r.datetime, r.open, r.high, r.low, r.close, int(r.volume))
                   for r in rows]
        # rows come grouped by partition; sort to get one ascending stream.
        candles.sort(key=lambda c: c.datetime)
        return candles

"""Cassandra access for the users table."""
from __future__ import annotations

import logging
from datetime import datetime, timezone

from cassandra.auth import PlainTextAuthProvider
from cassandra.cluster import Cluster, Session

from .config import Config

log = logging.getLogger("users")


class UserRepository:
    def __init__(self, config: Config):
        self._config = config
        self._cluster: Cluster | None = None
        self._session: Session | None = None
        self._get_stmt = None
        self._insert_stmt = None

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
        self._get_stmt = session.prepare(
            "SELECT email, password_hash FROM users WHERE email = ?"
        )
        self._insert_stmt = session.prepare(
            "INSERT INTO users (email, password_hash, created_at) VALUES (?, ?, ?) IF NOT EXISTS"
        )
        self._session = session
        return session

    def find(self, email: str) -> dict | None:
        row = self._connect().execute(self._get_stmt, (email,)).one()
        if row is None:
            return None
        return {"email": row.email, "password_hash": row.password_hash}

    def create(self, email: str, password_hash: str) -> bool:
        """Insert a user. Returns False if the email is already taken (LWT applied)."""
        result = self._connect().execute(
            self._insert_stmt, (email, password_hash, datetime.now(timezone.utc))
        ).one()
        return bool(result.applied)

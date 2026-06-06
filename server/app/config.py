"""Config read from environment variables (with defaults for local dev)."""
from __future__ import annotations

import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Config:
    cassandra_hosts: list[str] = field(default_factory=lambda: ["127.0.0.1"])
    cassandra_port: int = 9042
    cassandra_keyspace: str = "stock_keyspace"
    cassandra_username: str | None = None
    cassandra_password: str | None = None

    cache_ttl: int = 60
    cache_size: int = 256

    # auth
    jwt_secret: str = "dev-secret-change-me"
    jwt_ttl_hours: int = 12
    require_auth: bool = False  # when True, /api/v1/candles needs a bearer token

    log_level: str = "INFO"

    @staticmethod
    def from_env() -> "Config":
        hosts = os.getenv("CASSANDRA_HOSTS", "127.0.0.1")
        return Config(
            cassandra_hosts=[h.strip() for h in hosts.split(",") if h.strip()],
            cassandra_port=int(os.getenv("CASSANDRA_PORT", "9042")),
            cassandra_keyspace=os.getenv("CASSANDRA_KEYSPACE", "stock_keyspace"),
            cassandra_username=os.getenv("CASSANDRA_USERNAME") or None,
            cassandra_password=os.getenv("CASSANDRA_PASSWORD") or None,
            cache_ttl=int(os.getenv("CACHE_TTL_SECONDS", "60")),
            cache_size=int(os.getenv("CACHE_MAX_SIZE", "256")),
            jwt_secret=os.getenv("JWT_SECRET", "dev-secret-change-me"),
            jwt_ttl_hours=int(os.getenv("JWT_TTL_HOURS", "12")),
            require_auth=os.getenv("REQUIRE_AUTH", "false").lower() == "true",
            log_level=os.getenv("LOG_LEVEL", "INFO"),
        )

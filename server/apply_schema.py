"""Run schema.cql through the Python driver.

Handy when cqlsh isn't around (e.g. the Docker loader container). Same effect
as `cqlsh -f schema.cql`.
"""
from __future__ import annotations

import logging
import os
import sys

from cassandra.cluster import Cluster

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(message)s")
log = logging.getLogger("schema")


def statements(path):
    with open(path, encoding="utf-8") as fh:
        body = "\n".join(ln for ln in fh if not ln.strip().startswith("--"))
    stmts = [s.strip() for s in body.split(";") if s.strip()]
    # USE is a no-op here: we connect without a keyspace and the CREATEs are scoped.
    return [s for s in stmts if not s.upper().startswith("USE ")]


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.getenv("SCHEMA_FILE", os.path.join(here, "..", "schema.cql"))
    hosts = [h.strip() for h in os.getenv("CASSANDRA_HOSTS", "127.0.0.1").split(",")]
    port = int(os.getenv("CASSANDRA_PORT", "9042"))

    log.info("applying %s to %s:%s", path, hosts, port)
    cluster = Cluster(contact_points=hosts, port=port)
    session = cluster.connect()
    for stmt in statements(path):
        log.info("> %s", stmt.splitlines()[0][:70])
        session.execute(stmt)
    cluster.shutdown()
    log.info("schema applied")
    return 0


if __name__ == "__main__":
    sys.exit(main())

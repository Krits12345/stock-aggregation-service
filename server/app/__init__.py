"""App factory. Wires the controller -> service -> repository layers.

Flask/Cassandra imports live inside create_app so that app.aggregation can be
imported and tested without those packages installed.
"""
from __future__ import annotations

import logging


def create_app(config=None):
    from flask import Flask, jsonify
    from flask_cors import CORS
    from flasgger import Swagger

    from .auth import AuthService, build_auth_blueprint
    from .config import Config
    from .errors import register_error_handlers
    from .repository import CandleRepository
    from .routes import build_blueprint
    from .service import CandleService
    from .users import UserRepository

    config = config or Config.from_env()

    logging.basicConfig(
        level=getattr(logging, config.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
    )
    log = logging.getLogger("app")

    app = Flask(__name__)
    CORS(app, resources={r"/api/*": {"origins": "*"}})  # so the Vue client can call us
    app.config["SWAGGER"] = {"uiversion": 3, "openapi": "3.0.2"}
    Swagger(app, template={
        "info": {
            "title": "Stock Market Data Aggregation Service",
            "description": "Returns OHLCV candles aggregated to a requested timeframe.",
            "version": "1.0.0",
        }
    })

    repository = CandleRepository(config)
    service = CandleService(repository, cache_ttl=config.cache_ttl, cache_size=config.cache_size)

    users = UserRepository(config)
    auth = AuthService(users, config.jwt_secret, config.jwt_ttl_hours)

    app.register_blueprint(build_auth_blueprint(auth))
    app.register_blueprint(build_blueprint(service, auth=auth, require_auth=config.require_auth))
    register_error_handlers(app)

    @app.get("/health")
    def health():
        """Health check.
        ---
        tags: [system]
        responses:
          200: {description: Service up, Cassandra reachable}
          503: {description: Cassandra unreachable}
        """
        try:
            repository.ping()
            return jsonify(status="UP", cassandra="UP")
        except Exception as exc:  # noqa: BLE001
            log.warning("health check failed: %s", exc)
            return jsonify(status="UP", cassandra="DOWN", detail=str(exc)), 503

    log.info("app ready (cache_ttl=%ss, cache_size=%s, require_auth=%s)",
             config.cache_ttl, config.cache_size, config.require_auth)
    return app

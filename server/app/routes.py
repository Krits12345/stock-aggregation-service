"""HTTP routes. The docstring blocks double as the Swagger spec."""
from __future__ import annotations

from flask import Blueprint, jsonify, request

from .auth import require_token
from .service import CandleService, DEFAULT_PAGE_SIZE


def build_blueprint(service: CandleService, auth=None, require_auth=False) -> Blueprint:
    bp = Blueprint("api", __name__, url_prefix="/api/v1")

    # Optional gate: protect /candles only when REQUIRE_AUTH is on, so the
    # default behaviour (and the CLI client) keep working without a token.
    guard = require_token(auth) if (require_auth and auth) else (lambda fn: fn)

    @bp.get("/candles")
    @guard
    def get_candles():
        """Return OHLCV candles aggregated to the requested timeframe.
        ---
        tags: [candles]
        parameters:
          - in: query
            name: symbol
            required: true
            schema: {type: string}
            example: RELIANCE
          - in: query
            name: timeframe
            required: true
            schema: {type: string, enum: [1m, 5m, 15m, 30m, 1h, 1d]}
            example: 15m
          - in: query
            name: start_date
            required: true
            schema: {type: string}
            description: "yyyy-MM-dd HH:mm:ss or ISO 8601"
            example: "2026-01-01 09:15:00"
          - in: query
            name: end_date
            required: true
            schema: {type: string}
            example: "2026-01-01 15:30:00"
          - in: query
            name: page
            required: false
            schema: {type: integer, default: 1, minimum: 1}
          - in: query
            name: page_size
            required: false
            schema: {type: integer, default: 500, minimum: 1, maximum: 1000}
        responses:
          200:
            description: Aggregated candle data
          400:
            description: Invalid input (missing param, bad timeframe, bad date range)
          404:
            description: Symbol not found
        """
        result = service.get_candles(
            symbol=request.args.get("symbol"),
            timeframe=request.args.get("timeframe"),
            start_date=request.args.get("start_date"),
            end_date=request.args.get("end_date"),
            page=request.args.get("page", 1),
            page_size=request.args.get("page_size", DEFAULT_PAGE_SIZE),
        )
        return jsonify(result)

    return bp

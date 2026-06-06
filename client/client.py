"""Command-line client for the candle aggregation service.

Calls GET /api/v1/candles and prints the candles to the console.
Params come from CLI flags and/or a JSON config file (flags win).

    python client.py --symbol RELIANCE --timeframe 15m \
        --start "2026-01-01 09:15:00" --end "2026-01-01 15:30:00"

    python client.py --config config.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys

import requests


def load_config(path):
    if not path:
        return {}
    if not os.path.exists(path):
        sys.exit(f"ERROR: config file not found: {path}")
    with open(path, encoding="utf-8") as fh:
        return json.load(fh)


def parse_args(argv):
    p = argparse.ArgumentParser(description="Fetch aggregated candles from the service")
    p.add_argument("--config", help="path to a JSON config file")
    p.add_argument("--base-url")
    p.add_argument("--symbol")
    p.add_argument("--timeframe", help="1m, 5m, 15m, 30m, 1h, 1d")
    p.add_argument("--start", dest="start_date", help="'yyyy-MM-dd HH:mm:ss'")
    p.add_argument("--end", dest="end_date", help="'yyyy-MM-dd HH:mm:ss'")
    p.add_argument("--page", type=int)
    p.add_argument("--page-size", type=int)
    p.add_argument("--timeout", type=float, default=15.0)
    return p.parse_args(argv)


def resolve_params(args, cfg):
    """CLI flag if given, else config file value, else default."""
    def pick(cli, key, default=None):
        return cli if cli is not None else cfg.get(key, default)

    params = {
        "base_url": pick(args.base_url, "base_url", "http://localhost:8080").rstrip("/"),
        "symbol": pick(args.symbol, "symbol"),
        "timeframe": pick(args.timeframe, "timeframe"),
        "start_date": pick(args.start_date, "start_date"),
        "end_date": pick(args.end_date, "end_date"),
        "page": pick(args.page, "page"),
        "page_size": pick(args.page_size, "page_size"),
    }
    missing = [k for k in ("symbol", "timeframe", "start_date", "end_date") if not params[k]]
    if missing:
        sys.exit(f"ERROR: missing parameter(s): {', '.join(missing)} (pass via --flags or --config)")
    return params


def fetch(params, timeout):
    url = f"{params['base_url']}/api/v1/candles"
    query = {k: params[k] for k in ("symbol", "timeframe", "start_date", "end_date")}
    for k in ("page", "page_size"):
        if params.get(k) is not None:
            query[k] = params[k]

    try:
        resp = requests.get(url, params=query, timeout=timeout)
    except requests.RequestException as exc:
        sys.exit(f"ERROR: could not reach {url}: {exc}")

    if resp.status_code != 200:
        try:
            msg = resp.json().get("message", resp.text)
        except ValueError:
            msg = resp.text
        sys.exit(f"ERROR: HTTP {resp.status_code}: {msg}")

    return resp.json()


def render(data):
    candles = data.get("candles", [])
    total = data.get("pagination", {}).get("total_candles", data.get("count", len(candles)))

    print("=== Fetched Candle Data ===")
    print(f"Symbol: {data.get('symbol')} | Timeframe: {data.get('timeframe')} | "
          f"Total Candles: {total}")
    print()
    for i, c in enumerate(candles, start=1):
        print(f"{i:>3} {c['datetime']} | O: {c['open']:<10} | H: {c['high']:<10} | "
              f"L: {c['low']:<10} | C: {c['close']:<10} | V: {c['volume']}")
    pg = data.get("pagination")
    if pg and pg.get("total_pages", 1) > 1:
        print(f"\n[page {pg['page']}/{pg['total_pages']} | page_size {pg['page_size']}]")
    print("===========================")


def main(argv):
    args = parse_args(argv)
    params = resolve_params(args, load_config(args.config))
    render(fetch(params, args.timeout))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

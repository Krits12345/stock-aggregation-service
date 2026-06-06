# Stock Market Data Aggregation Service

A REST service that reads 1-minute stock candles from Apache Cassandra and returns
OHLCV candlestick data aggregated to a requested timeframe (1m, 5m, 15m, 30m, 1h, 1d).

There are three pieces:

- `server/` – the API service (Python + Flask)
- `client/` – a command-line client that calls the API (Part 3.1)
- `web-client/` – a small Vue + Highcharts chart UI (the optional bonus)

The schema lives in `schema.cql` and the sample data in `data/stock_data.csv`.

> The brief asked for Java or Golang. I built this in Python/Flask but kept the same
> layering (controller → service → repository); the schema and aggregation logic carry
> over to a Spring Boot version unchanged. Flagging it so it's not a surprise.

## Layout

```
submission/
├── server/
│   ├── app/
│   │   ├── routes.py        # HTTP layer (+ Swagger docs)
│   │   ├── service.py       # validation, caching, pagination
│   │   ├── repository.py    # Cassandra queries (candles)
│   │   ├── aggregation.py   # the OHLCV roll-up logic
│   │   ├── auth.py          # signup/login, JWT, route guard
│   │   ├── users.py         # Cassandra queries (users)
│   │   ├── errors.py
│   │   └── config.py
│   ├── tests/               # unit tests for the aggregation
│   ├── ingest.py            # CSV -> Cassandra
│   ├── apply_schema.py      # applies schema.cql (cqlsh alternative)
│   ├── wsgi.py
│   └── Dockerfile
├── client/
├── web-client/
├── data/stock_data.csv
├── schema.cql
├── docker-compose.yml
└── README.md
```

## Running it with Docker (easiest)

Needs Docker. This starts Cassandra, applies the schema, loads the CSV, then starts
the API:

```bash
cd submission
docker compose up --build
```

Once it's up:

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/apidocs
- Health: http://localhost:8080/health

Cassandra takes about a minute to come up the first time. The `loader` container does
the schema + ingestion once and exits; the API waits for it. To stop and wipe data:

```bash
docker compose down -v
```

## Running it manually (local Cassandra)

You'll need Cassandra running on 9042 and Python 3.10+.

```bash
# 1. schema (one-time)
cqlsh -f schema.cql            # or: cd server && python apply_schema.py

# 2. server deps
cd server
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# 3. load the data (one-time)
python ingest.py --csv ../data/stock_data.csv

# 4. run
python wsgi.py                 # dev
# gunicorn -w 4 -b 0.0.0.0:8080 wsgi:app   # prod
```

Connection settings are read from env vars (defaults shown):

```
CASSANDRA_HOSTS=127.0.0.1  CASSANDRA_PORT=9042  CASSANDRA_KEYSPACE=stock_keyspace
CASSANDRA_USERNAME=  CASSANDRA_PASSWORD=
CACHE_TTL_SECONDS=60  CACHE_MAX_SIZE=256  LOG_LEVEL=INFO
JWT_SECRET=dev-secret-change-me  JWT_TTL_HOURS=12  REQUIRE_AUTH=false
```

Tests:

```bash
cd server && pip install pytest && python -m pytest
```

## The API

`GET /api/v1/candles`

| Param | Required | Notes |
|-------|----------|-------|
| `symbol` | yes | e.g. `RELIANCE` |
| `timeframe` | yes | `1m` `5m` `15m` `30m` `1h` `1d` |
| `start_date` | yes | `yyyy-MM-dd HH:mm:ss` or ISO 8601 |
| `end_date` | yes | same |
| `page` | no | default 1 |
| `page_size` | no | 1–1000, default 500 |

Aggregation: open = first open, high = max high, low = min low, close = last close,
volume = sum. Intraday windows snap to clock boundaries (5m → :00/:05/…), 1d to the
calendar day.

Example:

```bash
curl "http://localhost:8080/api/v1/candles?symbol=RELIANCE&timeframe=15m&start_date=2026-01-01%2009:15:00&end_date=2026-01-01%2010:00:00"
```

```json
{
  "symbol": "RELIANCE",
  "timeframe": "15m",
  "candles": [
    {"datetime": "2026-01-01T09:15:00Z", "open": 1573.7, "high": 1589.6,
     "low": 1572.3, "close": 1588.1, "volume": 661659}
  ],
  "count": 1,
  "pagination": {"page": 1, "page_size": 500, "total_candles": 1, "total_pages": 1}
}
```

Errors come back as JSON with the right status code:

```json
{"error": "bad_request", "message": "Unsupported timeframe: '7m'. Supported: 1m, 5m, 15m, 30m, 1h, 1d", "status": 400}
```

- 400 – missing param, bad timeframe, bad date format, start > end, page out of range
- 404 – symbol not found
- 503 – Cassandra unreachable (health endpoint)

```bash
# 400
curl "http://localhost:8080/api/v1/candles?symbol=RELIANCE&timeframe=7m&start_date=2026-01-01%2009:15:00&end_date=2026-01-01%2010:00:00"
# 404
curl "http://localhost:8080/api/v1/candles?symbol=NOPE&timeframe=5m&start_date=2026-01-01%2009:15:00&end_date=2026-01-01%2010:00:00"
```

## Auth (login / signup)

The web client has a login/signup screen. The backend stores users in Cassandra
(`users` table) with hashed passwords (werkzeug) and hands back a signed JWT.

`POST /api/v1/auth/signup` and `POST /api/v1/auth/login` both take `{email, password}`
and return `{token, email}`:

```bash
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"secret123"}' \
  http://localhost:8080/api/v1/auth/signup
```

```json
{"email": "you@example.com", "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6..."}
```

By default `/api/v1/candles` stays public so the CLI client and the assignment's API
contract keep working. Set `REQUIRE_AUTH=true` to also gate it — then requests must
send `Authorization: Bearer <token>` or get a 401.

## Command-line client

```bash
cd client
pip install -r requirements.txt

python client.py --symbol RELIANCE --timeframe 15m \
  --start "2026-01-01 09:15:00" --end "2026-01-01 15:30:00"

# or from a file (flags override individual values)
cp config.example.json config.json
python client.py --config config.json
```

Output:

```
=== Fetched Candle Data ===
Symbol: RELIANCE | Timeframe: 15m | Total Candles: 2

  1 2026-01-01T09:15:00Z | O: 2450.5     | H: 2472.3     | L: 2448.1     | C: 2465.8     | V: 540000
  2 2026-01-01T09:30:00Z | O: 2465.8     | H: 2480.0     | L: 2463.5     | C: 2475.6     | V: 430000
===========================
```

## Web client (bonus)

```bash
cd web-client
npm install
npm run dev    # http://localhost:5173
```

The dev server proxies `/api` to the Flask service, so there's no CORS setup needed
locally. Override the target with `VITE_API_TARGET` if the API runs elsewhere.

It opens on the login/signup screen; after you're in you get the chart. There's a
day/night toggle in the top-right (the choice is remembered in localStorage, and the
chart re-themes with it).

## Notes / assumptions

- The CSV timestamps are naive; I treat them as UTC and return ISO 8601 with a `Z`,
  matching the example in the brief.
- Date ranges are inclusive on both ends. A partial trailing window still gets emitted.
- Symbols are matched case-insensitively (upper-cased on ingest and on query).
- `1m` returns the raw data.
- The cache is a simple in-process TTL cache keyed by (symbol, timeframe, start, end).
  Under gunicorn it's per-worker, so the first request on each worker is a miss — a
  shared store like Redis is the production answer.
- Schema choice (day-bucketed partitions) is explained in `schema.cql`.
- The provided data is RELIANCE and TCS, 2026-01-01 to 2026-01-05, market hours only.

Bonus items covered: aggregation unit tests, pagination, caching, Swagger docs,
logging + a health endpoint, the Vue chart client, and Docker setup.

Extra (asked for separately): login/signup with JWT-backed auth, and a day/night
theme toggle in the web client.

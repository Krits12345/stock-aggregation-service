# Stock Market Data Aggregation Service

A REST service that reads 1-minute stock candles from Apache Cassandra and returns
OHLCV candlestick data aggregated to a requested timeframe (1m, 5m, 15m, 30m, 1h, 1d).

There are three pieces:

- `server/` - the API service (Java 17 + Spring Boot)
- `client/` - a command-line client that calls the API (Part 3.1)
- `web-client/` - a small Vue + Highcharts chart UI (the optional bonus)

The schema lives in `schema.cql` and the sample data in `data/stock_data.csv`.

## Tech stack

- **Java 17**, **Spring Boot 3.3** (web layer, DI, config)
- **DataStax Java driver 4** for Cassandra (used directly, so the repository
  layer stays close to the CQL it runs)
- **Caffeine** for the in-process TTL cache
- **jjwt** + Spring Security's BCrypt for the login/signup feature
- **springdoc-openapi** for the Swagger UI
- **JUnit 5** for the aggregation unit tests

## Layout

```
submission/
├── server/                         # Main API service (Spring Boot)
│   ├── src/main/java/com/stockaggregator/
│   │   ├── StockAggregatorApplication.java   # main() / Spring Boot entry point
│   │   ├── config/                 # configuration + Cassandra session
│   │   ├── aggregation/            # the pure OHLCV roll-up logic
│   │   ├── repository/             # Cassandra queries
│   │   ├── service/                # validation, caching, pagination, auth
│   │   ├── web/                    # HTTP controllers + error handling
│   │   └── loader/                 # one-time schema apply + CSV ingest
│   ├── src/main/resources/application.yml    # default config (env-overridable)
│   ├── src/test/java/...           # aggregation unit tests
│   ├── pom.xml
│   └── Dockerfile
├── client/                         # Command-line client (Java)
├── web-client/                     # Vue + Highcharts chart UI (bonus)
├── data/stock_data.csv
├── schema.cql                      # Cassandra schema (CREATE KEYSPACE + tables)
├── docker-compose.yml
└── README.md
```

### What each server file does

**Entry point**
- `StockAggregatorApplication.java` - boots Spring. Run normally it starts the
  API; run with the `loader` profile it applies the schema, ingests the CSV, and exits.

**config/** - settings and the Cassandra connection
- `CassandraProperties.java`, `CacheProperties.java`, `JwtProperties.java`,
  `AppProperties.java`, `LoaderProperties.java` - typed config bound from
  `application.yml` (and overridable by environment variables).
- `CassandraSession.java` - owns the single `CqlSession`. Connects **lazily** on
  first use, so the app still boots if Cassandra is briefly down (and `/health`
  reports the real status). Connects keyspace-less; repositories use
  keyspace-qualified statements.
- `OpenApiConfig.java` - title/description for the Swagger UI.

**aggregation/** - the heart of the assignment (no Spring/Cassandra here, so it's
easy to unit test)
- `Candle.java` - a single OHLCV candle.
- `Aggregator.java` - rolls 1-minute candles into a target timeframe
  (open = first, high = max, low = min, close = last, volume = sum). Intraday
  windows snap to clock boundaries; `1d` snaps to the calendar day.

**repository/** - Cassandra access
- `CandleRepository.java` - reads 1-minute candles. A date-range query hits only
  the day-partitions it spans (`date IN (...)`), each read in clustering order -
  no full-table scan.
- `UserRepository.java` - reads/writes the `users` table (for login/signup).

**service/** - business logic
- `CandleService.java` - validates input, fetches, aggregates, caches the result
  (TTL cache keyed by symbol+timeframe+range), and paginates the response.
- `AuthService.java` - signup/login: BCrypt password hashing and JWT issuing/verification.

**web/** - the HTTP layer
- `CandleController.java` - `GET /api/v1/candles`.
- `AuthController.java` - `POST /api/v1/auth/signup` and `/login`.
- `HealthController.java` - `GET /health` (also pings Cassandra).
- `GlobalExceptionHandler.java` - turns exceptions into `{error, message, status}`
  JSON with the right HTTP status code.
- `AuthInterceptor.java` + `WebConfig.java` - CORS for the web client, plus the
  optional bearer-token gate on `/candles` (only active when `APP_REQUIRE_AUTH=true`).
- `dto/` - the response shapes (`CandlesResponse`, `CandleDto`, `Pagination`, `ErrorResponse`).

**loader/** - the one-time data load
- `SchemaApplier.java` - runs `schema.cql` through the driver (a `cqlsh -f` alternative).
- `CsvIngestor.java` - parses `stock_data.csv` and inserts the rows.
- `LoaderRunner.java` - orchestrates the two and exits; only active under the `loader` profile.

## Prerequisites

- **JDK 17+** and **Maven 3.9+** (for running locally), or
- **Docker** (for the one-command stack)

## Running it with Docker (easiest)

This starts Cassandra, applies the schema, loads the CSV, then starts the API:

```bash
docker compose up --build
```

Once it's up:

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/apidocs
- Health: http://localhost:8080/health

Cassandra takes about a minute to come up the first time. The `loader` container
does the schema + ingestion once and exits; the API waits for it. To stop and
wipe data:

```bash
docker compose down -v
```

## Running it manually (local Cassandra)

You'll need Cassandra running on 9042, plus JDK 17 and Maven.

```bash
# 1. schema (one-time)
cqlsh -f schema.cql
#   ...or, if you don't have cqlsh, let the loader profile do it (next step also ingests).

# 2. load the data (one-time) - applies the schema too, then exits
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=loader

# 3. run the API
mvn spring-boot:run
#   or build a jar: mvn clean package && java -jar target/stock-aggregation-server.jar
```

Connection settings come from `application.yml` and can be overridden with
environment variables (defaults shown):

```
CASSANDRA_HOSTS=127.0.0.1   CASSANDRA_PORT=9042   CASSANDRA_KEYSPACE=stock_keyspace
CASSANDRA_LOCAL_DATACENTER=datacenter1
CASSANDRA_USERNAME=   CASSANDRA_PASSWORD=
CACHE_TTL_SECONDS=60   CACHE_MAX_SIZE=256
JWT_SECRET=dev-secret-change-me   JWT_TTL_HOURS=12   APP_REQUIRE_AUTH=false
```

Tests (the aggregation logic):

```bash
cd server && mvn test
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
| `page_size` | no | 1-1000, default 500 |

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

- 400 - missing param, bad timeframe, bad date format, start > end, page out of range
- 404 - symbol not found
- 503 - Cassandra unreachable (health endpoint)

```bash
# 400 (bad timeframe)
curl "http://localhost:8080/api/v1/candles?symbol=RELIANCE&timeframe=7m&start_date=2026-01-01%2009:15:00&end_date=2026-01-01%2010:00:00"
# 404 (unknown symbol)
curl "http://localhost:8080/api/v1/candles?symbol=NOPE&timeframe=5m&start_date=2026-01-01%2009:15:00&end_date=2026-01-01%2010:00:00"
```

## Auth (login / signup)

The web client has a login/signup screen. The backend stores users in Cassandra
(`users` table) with BCrypt-hashed passwords and hands back a signed JWT.

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
contract keep working. Set `APP_REQUIRE_AUTH=true` to also gate it - then requests
must send `Authorization: Bearer <token>` or get a 401.

## Command-line client

Needs JDK 17 + Maven. From the `client/` folder:

```bash
cd client

# pass parameters as flags
mvn -q compile exec:java -Dexec.args="--symbol RELIANCE --timeframe 15m \
  --start '2026-01-01 09:15:00' --end '2026-01-01 15:30:00'"

# or from a file (flags override individual values)
cp config.example.json config.json
mvn -q compile exec:java -Dexec.args="--config config.json"

# you can also build a runnable jar (easiest on Windows / PowerShell):
mvn -q clean package
java -jar target/stock-aggregation-client.jar --symbol RELIANCE --timeframe 15m \
  --start "2026-01-01 09:15:00" --end "2026-01-01 15:30:00"
```

Output:

```
=== Fetched Candle Data ===
Symbol: RELIANCE | Timeframe: 15m | Total Candles: 2

  1 2026-01-01T09:15:00Z | O: 2450.5     | H: 2472.3     | L: 2448.1     | C: 2465.8     | V: 540000
  2 2026-01-01T09:30:00Z | O: 2465.8     | H: 2480.0     | L: 2463.5     | C: 2475.6     | V: 430000
===========================
```

The client takes `--base-url` (default `http://localhost:8080`), `--symbol`,
`--timeframe`, `--start`, `--end`, `--page`, `--page-size`, and `--timeout`.

## Web client (bonus)

```bash
cd web-client
npm install
npm run dev    # http://localhost:5173
```

The dev server proxies `/api` to the backend, so there's no CORS setup needed
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
- The Cassandra session connects without a default keyspace and uses
  keyspace-qualified statements, so the same code path works for the loader (before
  the keyspace exists) and the API.
- The cache is a simple in-process TTL cache keyed by (symbol, timeframe, start, end).
  Across multiple instances it'd be per-instance, so a shared store like Redis is the
  production answer.
- The JWT secret is SHA-256'd into a 256-bit key, so any configured secret length works.
- Schema choice (day-bucketed partitions) is explained in `schema.cql`.
- The provided data is RELIANCE and TCS, 2026-01-01 to 2026-01-05, market hours only.

Bonus items covered: aggregation unit tests, pagination, caching, Swagger docs,
logging + a health endpoint, the Vue chart client, and Docker setup.

Extra (beyond the brief): login/signup with JWT-backed auth, and a day/night
theme toggle in the web client.

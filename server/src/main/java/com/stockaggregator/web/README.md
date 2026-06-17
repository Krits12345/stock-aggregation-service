# web/

The HTTP layer: controllers, error handling, CORS, and the optional auth gate.
Controllers are kept thin and just hand off to the services.

- **CandleController.java** - `GET /api/v1/candles`. Declares the query params (with
  Swagger annotations for the docs) and calls `CandleService`.
- **AuthController.java** - `POST /api/v1/auth/signup` and `/login`. Both take
  `{email, password}` and return `{token, email}`.
- **HealthController.java** - `GET /health`. Also pings Cassandra so a green health
  check actually means the database is reachable.
- **GlobalExceptionHandler.java** - turns our exceptions into a consistent
  `{error, message, status}` JSON body with the matching HTTP status code, so errors
  look the same everywhere.
- **AuthInterceptor.java** + **WebConfig.java** - WebConfig wires up CORS for the web
  client and, only when `APP_REQUIRE_AUTH=true`, installs the interceptor that
  requires a valid bearer token on `/candles`. Left off by default so the CLI client
  and the assignment's API contract keep working without a login.
- **dto/** - the plain response shapes: `CandlesResponse`, `CandleDto`, `Pagination`,
  and `ErrorResponse`.

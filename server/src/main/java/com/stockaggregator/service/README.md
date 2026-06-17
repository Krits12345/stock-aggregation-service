# service/

The business logic that sits between the controllers and the repositories. The
controllers stay thin; the real work happens here.

- **CandleService.java** - the candle flow end to end: validate the input (required
  params, supported timeframe, parseable dates, sensible range and paging), fetch the
  raw minutes, run the aggregation, cache the result, then slice out the requested
  page. The cache is a small in-process Caffeine TTL cache keyed by
  symbol + timeframe + start + end, so repeat queries skip Cassandra until the entry
  expires.
- **AuthService.java** - signup and login. Passwords are hashed with BCrypt, and a
  successful call hands back a signed JWT. `verify` checks a token and returns the
  email inside it (or throws if it's missing/expired/tampered). The configured JWT
  secret is run through SHA-256 first, so any secret length produces a valid 256-bit
  signing key.

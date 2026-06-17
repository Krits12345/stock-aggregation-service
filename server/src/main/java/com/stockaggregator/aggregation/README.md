# aggregation/

The core of the assignment: turning 1-minute candles into bigger timeframes.

There's no Spring or Cassandra in here on purpose, so it's plain Java and easy to
unit test on its own (see `AggregatorTest` under `src/test`).

- **Candle.java** - one OHLCV candle (datetime, open, high, low, close, volume).
  The fields are mutable because the aggregator updates the running high/low/close/
  volume as it folds minutes into a window.
- **Aggregator.java** - the roll-up. For each target window: open is the first open,
  high is the max, low is the min, close is the last close, volume is the sum.
  `bucketStart` figures out which window a timestamp belongs to: intraday frames snap
  to clock boundaries (5m goes to :00, :05, :10 ...), and 1d snaps to midnight UTC.
  `1m` just returns the raw candles since there's nothing to combine.

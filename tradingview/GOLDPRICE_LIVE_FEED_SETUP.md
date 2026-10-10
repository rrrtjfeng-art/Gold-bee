# Gold Bee: Live Gold Price Feed

Gold Bee now includes an adapter for the documented GoldPrice.dev WebSocket tick format.

## Important provider limits

- Endpoint: `wss://api.goldprice.dev/v1/stream`
- Symbol: `XAU-USD-SPOT`
- Authentication: send an `auth` frame with your API key, then subscribe after a `welcome` frame.
- The provider documents WebSocket streaming under its Realtime Pro plan. Check the provider's current pricing and terms before purchasing; this integration does not require you to buy a plan just to build the app.
- Its unauthenticated REST sample is delayed by 15 minutes according to the provider's docs. Do not treat that REST quote as live execution data.

## What the adapter does

- Parses the provider's `tick` messages and ISO-8601 `computed_at` timestamp.
- Accepts only XAU spot ticks matching the configured symbol.
- Rejects missing Bid/Ask values instead of fabricating a spread.
- Reports connected only after the provider confirms the requested subscription.
- Reports provider errors and connection loss to the listener.

## What is not yet automatic

The adapter is a connector component; it is not, by itself, a completed end-to-end live trading feed. The app still needs to securely collect the provider API key, instantiate this client, route its ticks into `MarketFeedController`, and display the feed state. No API key is embedded in source code.

A provider spot feed may differ from the user's MT5 broker's XAUUSD quote and spread. Before any signal can be considered actionable, Gold Bee must verify freshness, spread, symbol mapping, and broker-specific execution assumptions. If data is missing, delayed, stale, or inconsistent, the safe result is NO TRADE.

Gold Bee does not place orders automatically. The user remains responsible for all order decisions in MT5.

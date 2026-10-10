# Gold Bee TradingView companion indicator

File: `GoldBee_Swing_High_Low_SR.pine`

## Add it to TradingView on a phone or computer

1. Open TradingView and open an XAUUSD chart.
2. Open Pine Editor. If the mobile app does not expose Pine Editor, open TradingView in a browser/desktop view or use a desktop browser.
3. Create a new indicator, replace the editor contents with `GoldBee_Swing_High_Low_SR.pine`, and save it.
4. Add the indicator to the chart.
5. Start with 3 left bars / 3 confirmation bars. Larger values show fewer, generally broader swings; smaller values show more noise.
6. To receive alerts, create a TradingView alert and select one of this indicator's `Gold Bee` conditions. Alerts depend on TradingView's chart data and your alert configuration.

## What it shows

- Red downward markers: confirmed Swing Highs / potential resistance pivots.
- Green upward markers: confirmed Swing Lows / potential support pivots.
- Horizontal levels: nearby pivots are merged using ATR-based tolerance.
- Repeated pivot touches make the level line thicker.
- HH / HL / LH / LL labels show confirmed market structure.
- TradingView alert conditions are available for confirmed swing points, structure shifts, and support/resistance breaks.
- A level stops extending when a candle closes through it.

## Important limits

- A pivot is only confirmed after the configured right-side candles form. The marker is drawn back on the pivot candle, but it was not knowable at that earlier time.
- This is a custom companion indicator, not a TradingView feed integration and not a copy of a third-party paid indicator.
- The Android Gold Bee app separately computes similar swing levels from its own candle provider. The data sources, candle close times, symbol suffixes, spread and timezone can differ, so levels may not match exactly.
- Support and resistance are zones of interest, not guaranteed turning points or trade instructions. Confirm price freshness and risk before any manual MT5 order.

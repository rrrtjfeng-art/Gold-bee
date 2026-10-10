# Gold Bee TradingView tools

Gold Bee is being built for a user who wants **clear, low-effort trading decisions**, not a chart full of indicators.

## User requirements

- Focus on XAUUSD gold first; expand to other FX symbols only after gold is tested.
- Short-term signals with explicit BUY / SELL / WAIT / NO TRADE decisions.
- Show a reference entry, stop loss, take profit, reward/risk, and a plain-language reason.
- Small, clearly stated targets matter (for example, a $2.00 XAUUSD price move). "Price move" is used rather than universal "pips" because brokers define gold points/pips differently.
- Do not chase a missed signal; only use confirmed candles and do not invent an entry when conditions are missing.
- Prefer fewer, better-filtered signals over constant trading.
- Simulation and backtesting first. No automatic real orders.
- Zero-budget approach; do not assume a paid server or paid data subscription.

## Files

- `GoldBee_Confirmed_Signals.pine`: strategy/backtest prototype for XAUUSD on M5.
- `GoldBee_Swing_High_Low_SR.pine`: confirmed swing high/low and support/resistance companion indicator.
- `GOLDPRICE_LIVE_FEED_SETUP.md`: notes on the separate gold-price provider adapter and its limitations.

## Confirmed signal strategy prototype

The current prototype uses:
- M5 chart only; the panel warns if another timeframe is selected.
- M5 EMA trend alignment plus the last fully closed M15 trend filter.
- RSI, directional movement / ADX, ATR range, and EMA crossover as entry filters.
- Default target price move $2.00 and stop price move $1.50 for XAUUSD; default planned reward/risk is about 1.33R.
- Three-bar cooldown and a maximum of six new trades per chart day.
- Candle-close signals and dynamic alert messages containing symbol, reference entry, SL, TP, and reward/risk.
- Strategy Tester simulation with a default commission/slippage assumption.

These are **starting assumptions**, not optimized settings. The fixed target/stop are quote-price distances, not guaranteed cash profit. Actual cash P/L depends on broker contract size, lot size, spread, commission, swaps, and fills.

## How to test

1. Use a standard-candlestick XAUUSD chart on **5 minutes (M5)**, not Heikin Ashi, Renko, or other synthetic bars.
2. Add `GoldBee_Confirmed_Signals.pine` as a strategy in TradingView's Pine Editor.
3. Open Strategy Tester and record net profit, profit factor, maximum drawdown, total trades, win rate, and average trade after costs.
4. Test more than one market period, including trend and range conditions. Do not tune settings on one period and assume they will work in the future.
5. For alerts, choose **Any alert() function call**. Alerts depend on TradingView's plan, chart feed, and alert configuration.
6. Compare the TradingView symbol and prices with the exact XAUUSD symbol in the user's MT5 broker before considering any signal actionable.

## Important limitations

- The script has been committed to GitHub but **has not yet been compiled in TradingView's Pine Editor or independently backtested**. No win rate or profitability claim is currently justified.
- TradingView's strategy tester simulates trades; it is not the user's MT5 demo account and cannot place orders in the user's broker account.
- TradingView's historical fills cannot perfectly reproduce real-time Bid/Ask spread, fast-market slippage, commissions, swaps, or broker-specific contract specifications.
- A signal is a probability-based decision aid, not a guarantee. If the chart feed is stale, spread is too wide, or the price has moved away from the reference entry, the correct response is WAIT / NO TRADE rather than chasing.
- No script can guarantee that the user never needs to understand risk. Gold Bee should hide indicator complexity, but it must show risk and never promise profits.

## Existing swing/support-resistance indicator

`GoldBee_Swing_High_Low_SR.pine` plots confirmed pivots and nearby support/resistance zones. Pivots only become known after the configured right-side confirmation bars; markers are drawn back on the pivot candle for visualization and must not be mistaken for signals available at that earlier time.

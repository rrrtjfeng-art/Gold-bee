# Gold-bee Telegram paper-trading service

This is an initial, conservative **simulation-only** service. It reads quotes and completed M5 candles from a running MetaTrader 5 desktop terminal and sends Telegram status messages. It never calls `mt5.order_send` and does not place real orders.

## Important limits

- This is a baseline EMA(9/21) crossover aligned with EMA(50), with ATR-based SL/TP. It is **not yet proven profitable** and should not be treated as a validated strategy.
- It is not truly high-frequency: signals are evaluated on completed five-minute candles, once per new candle per symbol.
- MT5's `order_calc_profit` estimates PnL in the account currency. The service does not yet deduct broker commissions, swaps, or adverse slippage; reports must not be interpreted as live-equivalent results.
- The service must run on an always-on Windows computer/VPS with the MT5 desktop terminal logged into a **demo** account. Android and GitHub Actions are not persistent 24/7 servers.
- Never put Telegram tokens, MT5 passwords, or account credentials in GitHub source. Set them as environment variables on the host.
- Symbol names and spread thresholds vary by broker. Configure exact broker symbols and verify thresholds before resuming.

## Environment

- `TELEGRAM_BOT_TOKEN`: token from Telegram BotFather
- `TELEGRAM_CHAT_ID`: only chat ID permitted to control the service
- `MT5_SYMBOLS`: optional comma-separated exact broker symbols; default XAUUSD,EURUSD,GBPUSD,USDJPY,AUDUSD,USDCAD,USDCHF
- `MT5_SPREAD_LIMITS`: optional comma-separated overrides such as `XAUUSD=0.8,EURUSD=0.0003`
- `PAPER_START_BALANCE`: default 1000 account-currency units
- `PAPER_LOT_SIZE`: default 0.01
- `PAPER_MAX_OPEN_TRADES`: default 3
- `PAPER_MAX_DAILY_LOSS_PERCENT`: default 1.5% of starting balance
- `PAPER_MIN_RR`: default 1.5
- `POLL_SECONDS`: default 5
- `PAPER_STATE_FILE`: persistent local JSON state file

## Run on Windows

1. Install Python 3.11+ and MetaTrader 5 desktop terminal on an always-on Windows host/VPS.
2. Log into a broker **demo** account and confirm all configured symbols are visible in Market Watch.
3. Install dependencies: `py -m pip install -r requirements.txt`.
4. Set the environment variables above in the host's secret/environment settings.
5. Run: `py bot.py`.
6. Confirm the Telegram startup message says paused. Use `/status`; inspect symbols/spreads and the demo terminal first. Use `/resume` only to start paper simulation. Use `/pause` to stop opening new paper positions.

## Telegram commands

- `/start` or `/help`: help
- `/status`: balance, closed PnL, win/loss count, open positions
- `/positions`: current paper positions
- `/report`: summary and last ten closed positions
- `/resume`: start opening paper positions
- `/pause`: stop opening new positions; existing simulated positions still monitor SL/TP

## Known limitations before any real-money consideration

1. No walk-forward optimization or independent out-of-sample validation is included here.
2. No commission/swap/slippage model is included in this service's realized PnL.
3. The daily loss check uses a conservative starting-balance-based threshold and is not a substitute for broker-level risk controls.
4. A stop gap can produce a worse fill than the stop price; current quote polling can miss intratick paths.
5. Persistent service hosting, uptime monitoring, backups, broker-specific symbol validation, and a full paper-vs-MT5 fill reconciliation are still required.
6. Real order execution is intentionally absent.

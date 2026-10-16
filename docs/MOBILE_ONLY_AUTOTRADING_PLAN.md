# Mobile-Only Autonomous XAUUSD Trading: Feasible Deployment Plan

## Goal

Operate and monitor Gold-bee from an Android phone without owning or keeping a physical computer. The phone is the control device; the trading engine must run somewhere continuously available.

## Key platform constraint

The MetaTrader 5 Android app can place manual trades and manage protective orders, but it does not run desktop Expert Advisors (EAs). Therefore, a phone-only workflow cannot rely on the Android MT5 app itself to execute an EA in the background.

Official references:
- MT5 Android EA limitation (broker help): https://dfsasupport.axi.com/en-US/axidfsa--axicorp-prod/article/2h1Bj94Q-can-i-use-indicators-and-expert-advisors-on-the-android-mt5-app
- MetaTrader virtual hosting overview: https://www.metatrader5.com/en/terminal/help/virtual_hosting
- TradingView webhook behavior and limitations: https://www.tradingview.com/support/solutions/43000529348-how-to-configure-webhooks/

## Recommended path: Android phone + remote Windows VPS + MT5 desktop + Gold-bee EA

1. User performs setup and monitoring from Android using a Remote Desktop client.
2. A remote Windows VPS runs the broker's MT5 desktop terminal continuously.
3. Gold-bee EA runs inside MT5 and submits orders through the terminal's normal trade API.
4. The EA places server-side SL/TP where supported, verifies broker responses, and manages risk locally on the VPS.
5. The Android Gold-bee app or Telegram is used for status, pause/resume, alerts, and monitoring—not as the always-on execution engine.

This does not require the user to own a PC, but it does require a remote Windows machine (usually paid). Any provider, account region, broker terms, and price must be verified before purchase. Do not assume a free tier will keep MT5 running continuously.

## Alternative paths and why they are not the default

- **MT5 Android only:** not suitable for running a custom EA automatically.
- **MetaTrader hosted VPS:** designed for 24/7 EA operation, but its standard setup requires an EA and chart environment to be prepared and migrated from a desktop terminal. It may be less convenient for a phone-only first setup.
- **TradingView alerts + webhook:** alerts can send HTTP POST requests to an external service, but alerts alone do not execute broker orders. A secure, continuously available execution service and broker integration are still required; webhook delivery can fail.
- **Android Termux running a loop:** not the default for unattended live trading because Android background limits, network loss, battery management, and the lack of a supported MT5 desktop Python API on Android make execution reliability poor. Never depend on a sleeping phone for risk-critical SL/TP management.
- **Broker REST API/cloud function:** possible only if the specific broker officially provides a suitable trading API and permits this use. Do not reverse-engineer private endpoints or store account passwords in TradingView webhook payloads.

## Mandatory safety defaults for implementation

- Demo account by default; live trading disabled until explicitly configured by the account owner.
- No martingale, no loss-adding, and no recovery sizing.
- Reject stale/missing quotes, excessive spread, invalid symbol specifications, and invalid SL/TP.
- Enforce maximum position count, per-trade risk, daily loss limit, and a circuit breaker.
- Idempotent order requests to reduce duplicate entries after reconnects.
- Log every decision, order request, broker response, and risk block.
- On service disconnect or uncertain state, stop opening new positions and alert the user.
- Do not claim profitability. Require historical testing and forward demo testing before live use.

## Current repository state

The existing MT5 bridge is quote-only and the Python Telegram service is paper simulation only. Neither should be described as live auto-trading. The next engineering step is to implement and validate the MT5 EA, then document a phone-accessible remote deployment procedure. Compilation and live broker integration must be verified in an actual MT5 desktop environment; they cannot be certified from GitHub source inspection alone.

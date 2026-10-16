# Gold-bee Phone-Only Cloud Trading: Compatibility Gate

**Status: research only; no live trading is implemented by this document.**

## User requirements

- Android phone is the only setup/control device.
- No paid or user-managed VPS.
- Cloud execution must continue when the phone is closed or offline.
- Starting capital target: USD 1.
- Preferred account currency: USC cent account.
- Gold target leverage: 1:2000.
- Instruments: XAUUSD plus liquid major FX pairs (EURUSD, GBPUSD, USDJPY).
- Do not activate live trading until broker/account eligibility, symbols, volume, margin, spreads, and execution are verified.

## Current compatibility result

No verified single broker/platform combination has been found that satisfies every requirement at once.

| Route | USC cent account | Gold / major FX | 1:2000 gold leverage | Phone-started cloud bot | Result |
|---|---|---|---|---|---|
| HFM Cent on MT4/MT5 | Official account page says USC | Official page says Gold and 50+ FX pairs | Official account page lists up to 1:2000 | No official evidence found for a native mobile-started cloud EA on this account | Closest match to account requirements; cloud execution remains unresolved |
| FBS Cent on MT4/MT5 | Official account page says USC | Official page says Gold and 50+ FX pairs | Official account page lists up to 1:2000 | No official evidence found for a native mobile-started cloud EA on this account | Similar limitation; Malaysia entity/account eligibility must be confirmed |
| cTrader cloud cBot | USC cent account not verified; do not assume supported | Multi-symbol trading is technically possible, broker-dependent | No qualifying 1:2000 USC account verified | Official docs confirm mobile can start a cloud instance running 24/7 | Best no-VPS execution architecture, but fails the verified-account requirement so far |

## Official references checked

- HFM Cent account: https://www.hfm.com/int/en/trading-accounts/cent-account
- HFM XAUUSD contract specifications: https://www.hfm.com/int/en/trading-instruments/single-product?symbol=XAUUSD
- FBS Cent account page: https://www.fbsjp.com/global/int/en/trading-accounts/cent-account.html
- cTrader start a cBot / cloud execution: https://help.ctrader.com/cbots/how-tos/start-a-cbot/
- cTrader cBot FAQ and warning that profits are not guaranteed: https://help.ctrader.com/cbots/faq/

Broker comparison pages are not proof of Malaysian eligibility. Before depositing, the broker must confirm the exact legal entity serving a Malaysian resident, account currency, XAUUSD symbol, contract size, minimum volume, leverage, margin, spreads, and deposit/withdrawal minimums in the account-opening flow or in writing.

## Important $1 feasibility check

A $0 minimum-deposit claim does not mean every payment method accepts a $1 deposit, nor does it guarantee that every minimum order is affordable. On HFM's published Cent XAUUSD table, 1 lot is listed as 1 ounce, minimum contract size is 0.01 lot, and margin requirement is 0.05%; the broker's live symbol specification and account leverage still govern actual margin. Spread and stop-loss risk can exceed the available equity even when nominal margin is small.

At USD 1 equity, a safety-first bot may correctly refuse to trade if the minimum volume's stop-loss risk exceeds the configured risk budget. Do not bypass that check by removing the stop loss, increasing size, or using martingale/grid recovery.

## Engineering decision

Do not build a live execution adapter until one of these paths is explicitly selected and verified:

1. **Keep USC + 1:2000:** continue searching for an officially supported cloud execution environment for that exact MT4/MT5 account. Until then, Gold-bee stays in simulation/paper mode.
2. **Keep phone-only cloud execution:** accept a compatible cTrader broker/account even if it is not a USC account or does not offer 1:2000; then develop a cTrader cBot for demo testing.
3. **Keep USC + MT5:** use a Windows MT5 runtime (normally VPS or another always-on computer), which conflicts with the no-VPS requirement.

## Gold-bee implementation status

- Existing MT5 bridge: quote-only; not a live order execution service.
- Existing Telegram bot: paper simulation; it does not submit broker orders.
- Existing Android app: mobile UI/market components; not proof of 24/7 cloud execution.
- This compatibility gate adds no live trading code and does not enable real orders.
- Next milestone after a compatible route is selected: build a demo-only execution adapter, add explicit symbol/volume/margin validation, hard stop-loss and take-profit placement, duplicate-order protection, spread filters, daily loss cutoff, and audit logs; then compile/test on the target platform before any live use.

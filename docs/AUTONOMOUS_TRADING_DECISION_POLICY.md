# Gold-bee Decision Authority and Execution Contract

## Product goal
Gold-bee is the decision engine; the user retains the final physical action in MT5. The app must reduce avoidable hesitation by presenting one current verdict and one next step, not by manufacturing certainty or placing orders.

## Decision vocabulary
- BUY / SELL: a candidate passed all available signal and preflight checks. Show the exact plan and require the user to confirm independently in MT5.
- WAIT: a setup may develop, but its trigger is not yet present. State the exact trigger to wait for when it can be calculated.
- NO TRADE: data is stale/invalid, market costs are unacceptable, the setup is invalid, risk checks fail, or evidence is insufficient. State the blocking reason.
- COPY mode: never approve a signal without explicit Entry, SL, and TP; re-evaluate against the current executable quote and reject stale, resolved, directionally conflicted, or chased signals.

## Required decision card
1. Verdict and mode (REAL or COPY).
2. Quote source, bid/ask, spread, source timestamp, and age.
3. Actionable entry price/range or trigger; distinguish market entry from pending-order idea.
4. Stop loss and at least one take-profit; show price distance and net risk/reward after known costs.
5. Risk amount and lot estimate only when account currency, balance/equity, contract size, lot step, minimum lot, commission, and stop distance are known. Never invent broker specifications.
6. One short reason, the invalidation condition, and the next action.
7. Signal expiry/freshness. Any meaningful quote or spread change must cause a fresh evaluation before the user acts.

## Execution state machine
- INVALID DATA -> NO TRADE.
- STALE QUOTE / missing Bid-Ask / unsupported source for executable cost evaluation -> NO TRADE.
- Spread or slippage risk above configured threshold -> NO TRADE.
- Higher-timeframe conflict or unconfirmed trigger -> WAIT.
- Setup passes strategy checks but fails risk preflight -> NO TRADE.
- Strategy and risk preflight pass -> BUY/SELL candidate, awaiting user action.
- Before showing a confirmation handoff, revalidate quote freshness, entry distance, stop/target orientation, minimum stop distance where known, risk limit, and whether the setup has already resolved.
- User remains responsible for the final MT5 action. No hidden or automatic real-order submission.

## Anti-hesitation rules
- Do not list competing BUY and SELL plans as if both are actionable.
- Do not repeatedly change a verdict without a material change in market data or explicit rule change.
- If the entry condition is met and all checks pass, show the plan directly.
- If it is not met, state WAIT and the specific trigger; if the opportunity has run away, state NO TRADE / do not chase.
- Never encourage revenge trading, martingale sizing, loss-adding, or raising risk to recover losses.
- “BUY/SELL” is a rule-based candidate, not a guarantee or calibrated probability of profit.

## Validation requirements
- Unit tests for stale quote, invalid Bid/Ask, spread gate, higher-timeframe conflict, missing candles, chase rejection, invalid SL/TP, net reward/risk after costs, daily risk limits, and COPY mode missing Entry.
- Backtests must include spread, commission, and slippage assumptions; report sample size, expectancy, max drawdown, consecutive losses, and out-of-sample results.
- Paper-trading results must be labeled separately from live execution.
- Never claim build/test/backtest success unless the relevant run has actually completed and its result is inspected.

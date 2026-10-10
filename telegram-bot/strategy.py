"""Small, deterministic strategy core for Gold-bee's paper-only MT5 service.

This is a testable baseline, not a claim of profitability. Bars must be chronological
and contain completed candles only.
"""
from dataclasses import dataclass
from math import isfinite
from typing import Sequence


@dataclass(frozen=True)
class Bar:
    time: int
    open: float
    high: float
    low: float
    close: float


@dataclass(frozen=True)
class Signal:
    direction: str
    entry: float
    stop_loss: float
    take_profit: float
    atr: float
    reason: str


def ema(values: Sequence[float], period: int) -> list[float]:
    if period < 1:
        raise ValueError("period must be positive")
    if len(values) < period:
        return []
    alpha = 2.0 / (period + 1.0)
    result = [sum(values[:period]) / period]
    for value in values[period:]:
        result.append(alpha * value + (1.0 - alpha) * result[-1])
    return [float("nan")] * (period - 1) + result


def atr(bars: Sequence[Bar], period: int = 14) -> float | None:
    if period < 1 or len(bars) < period + 1:
        return None
    true_ranges = []
    for previous, current in zip(bars[:-1], bars[1:]):
        true_ranges.append(max(
            current.high - current.low,
            abs(current.high - previous.close),
            abs(current.low - previous.close),
        ))
    if len(true_ranges) < period:
        return None
    return sum(true_ranges[-period:]) / period


def generate_signal(bars: Sequence[Bar], bid: float, ask: float,
                    max_spread: float, min_rr: float = 1.5) -> Signal | None:
    """EMA(9/21) crossover in the direction of EMA(50), with ATR-based exits.

    Uses the last two completed candles only. Returns no signal on invalid quotes,
    excessive spread, insufficient history, non-finite values, or no fresh cross.
    """
    if not (isfinite(bid) and isfinite(ask) and bid > 0 and ask >= bid):
        return None
    if not isfinite(max_spread) or max_spread <= 0 or ask - bid > max_spread:
        return None
    if len(bars) < 60 or not isfinite(min_rr) or min_rr < 1.0:
        return None
    closes = [bar.close for bar in bars]
    fast = ema(closes, 9)
    slow = ema(closes, 21)
    trend = ema(closes, 50)
    volatility = atr(bars, 14)
    if volatility is None or not isfinite(volatility) or volatility <= 0:
        return None
    if not all(isfinite(x) for x in (fast[-1], fast[-2], slow[-1], slow[-2], trend[-1])):
        return None

    direction = None
    if fast[-2] <= slow[-2] and fast[-1] > slow[-1] and closes[-1] > trend[-1]:
        direction = "BUY"
    elif fast[-2] >= slow[-2] and fast[-1] < slow[-1] and closes[-1] < trend[-1]:
        direction = "SELL"
    if direction is None:
        return None

    entry = ask if direction == "BUY" else bid
    distance = 1.5 * volatility
    reward = distance * min_rr
    if direction == "BUY":
        sl, tp = entry - distance, entry + reward
    else:
        sl, tp = entry + distance, entry - reward
    if min(sl, tp) <= 0 or not all(isfinite(x) for x in (entry, sl, tp)):
        return None
    return Signal(
        direction=direction,
        entry=entry,
        stop_loss=sl,
        take_profit=tp,
        atr=volatility,
        reason=f"Closed-candle EMA(9/21) crossover aligned with EMA(50); ATR(14)={volatility:.6g}; planned R:R={min_rr:.2f}. Baseline strategy; not validated as profitable.",
    )

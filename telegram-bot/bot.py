"""Telegram controller + MT5 quote monitor. SIMULATION ONLY: never sends orders.

Run on a Windows host with the MT5 desktop terminal logged into a DEMO account.
The Android app/GitHub Actions runner is not a 24/7 trading server.
"""
import json
import os
import time
import traceback
from datetime import datetime, timezone
from pathlib import Path

import MetaTrader5 as mt5
import requests

from strategy import generate_signal

BOT_TOKEN = os.environ["TELEGRAM_BOT_TOKEN"]
ALLOWED_CHAT_ID = str(os.environ["TELEGRAM_CHAT_ID"])
SYMBOLS = [s.strip() for s in os.getenv(
    "MT5_SYMBOLS", "XAUUSD,EURUSD,GBPUSD,USDJPY,AUDUSD,USDCAD,USDCHF"
).split(",") if s.strip()]
STATE_FILE = Path(os.getenv("PAPER_STATE_FILE", "goldbee-paper-state.json"))
START_BALANCE = float(os.getenv("PAPER_START_BALANCE", "1000"))
LOT_SIZE = float(os.getenv("PAPER_LOT_SIZE", "0.01"))  # hard cap, not a fixed risk size
RISK_PER_TRADE_PERCENT = float(os.getenv("PAPER_RISK_PER_TRADE_PERCENT", "0.25"))
MAX_OPEN_TRADES = int(os.getenv("PAPER_MAX_OPEN_TRADES", "3"))
MAX_DAILY_LOSS_PERCENT = float(os.getenv("PAPER_MAX_DAILY_LOSS_PERCENT", "1.5"))
MIN_RR = float(os.getenv("PAPER_MIN_RR", "1.5"))
POLL_SECONDS = max(2, int(os.getenv("POLL_SECONDS", "5")))
# Maximum allowed quoted spread in symbol price units. Override for broker suffixes
# and different symbol specifications; a missing symbol limit blocks that symbol.
DEFAULT_SPREAD_LIMITS = {
    "XAUUSD": 0.50, "EURUSD": 0.00025, "GBPUSD": 0.00035,
    "USDJPY": 0.035, "AUDUSD": 0.00030, "USDCAD": 0.00035,
    "USDCHF": 0.00030,
}
SPREAD_LIMITS = dict(DEFAULT_SPREAD_LIMITS)
for pair in os.getenv("MT5_SPREAD_LIMITS", "").split(","):
    if "=" in pair:
        name, value = pair.split("=", 1)
        SPREAD_LIMITS[name.strip()] = float(value.strip())

session = requests.Session()
offset = 0
paused = True  # Safe default: must explicitly /resume to start paper trading.
state = {"balance": START_BALANCE, "positions": [], "closed": [], "day": "", "day_start_balance": START_BALANCE, "last_bar": {}, "paused": True}


def load_state():
    global state, paused
    if STATE_FILE.exists():
        try:
            loaded = json.loads(STATE_FILE.read_text(encoding="utf-8"))
            if isinstance(loaded, dict) and isinstance(loaded.get("positions"), list):
                state.update(loaded)
        except (OSError, ValueError):
            print("State file unreadable; starting a fresh paper book. Existing file preserved.")
    paused = bool(state.get("paused", True))


def save_state():
    state["paused"] = paused
    temporary = STATE_FILE.with_suffix(".tmp")
    temporary.write_text(json.dumps(state, indent=2), encoding="utf-8")
    temporary.replace(STATE_FILE)


def telegram(method, payload=None):
    url = f"https://api.telegram.org/bot{BOT_TOKEN}/{method}"
    response = session.post(url, json=payload or {}, timeout=15)
    response.raise_for_status()
    data = response.json()
    if not data.get("ok"):
        raise RuntimeError(f"Telegram API error: {data}")
    return data["result"]


def send(text):
    telegram("sendMessage", {"chat_id": ALLOWED_CHAT_ID, "text": text})


def usd_profit(position, close_price):
    order_type = mt5.ORDER_TYPE_BUY if position["direction"] == "BUY" else mt5.ORDER_TYPE_SELL
    value = mt5.order_calc_profit(order_type, position["symbol"], position["lots"],
                                  position["entry"], close_price)
    return float(value) if value is not None else None


def current_daily_pnl():
    today = datetime.now(timezone.utc).date().isoformat()
    closed_today = sum(float(t["pnl"]) for t in state["closed"] if t["closed_at"][:10] == today)
    floating = 0.0
    for p in state["positions"]:
        tick = mt5.symbol_info_tick(p["symbol"])
        if tick:
            price = tick.bid if p["direction"] == "BUY" else tick.ask
            pnl = usd_profit(p, price)
            if pnl is not None:
                floating += pnl
    return closed_today + floating


def close_position(position, price, reason):
    pnl = usd_profit(position, price)
    if pnl is None:
        return False
    trade = dict(position)
    trade.update({"exit": price, "pnl": round(pnl, 2), "reason": reason,
                  "closed_at": datetime.now(timezone.utc).isoformat()})
    state["balance"] += pnl
    state["closed"].append(trade)
    state["positions"].remove(position)
    save_state()
    send(f"模拟平仓 {trade['symbol']} {trade['direction']}\n原因: {reason}\n平仓价: {price}\n净模拟盈亏: {pnl:+.2f}（账户货币；未含额外佣金/滑点）\n模拟余额: {state['balance']:.2f}")
    return True


def check_positions():
    for position in list(state["positions"]):
        tick = mt5.symbol_info_tick(position["symbol"])
        if tick is None or tick.time <= 0:
            continue
        # BUY closes on Bid; SELL closes on Ask, preserving spread-side logic.
        price = tick.bid if position["direction"] == "BUY" else tick.ask
        if position["direction"] == "BUY":
            if price <= position["sl"]:
                close_position(position, price, "止损触发（按可用 Bid 报价）")
            elif price >= position["tp"]:
                close_position(position, position["tp"], "止盈触发（按目标价）")
        else:
            if price >= position["sl"]:
                close_position(position, price, "止损触发（按可用 Ask 报价）")
            elif price <= position["tp"]:
                close_position(position, position["tp"], "止盈触发（按目标价）")


def risk_sized_lots(symbol, direction, entry, stop_loss):
    """Risk-size a simulated position, capped by PAPER_LOT_SIZE; return 0 if unsafe."""
    info = mt5.symbol_info(symbol)
    if info is None or info.volume_step <= 0 or info.volume_min <= 0:
        return 0.0
    order_type = mt5.ORDER_TYPE_BUY if direction == "BUY" else mt5.ORDER_TYPE_SELL
    one_lot_loss = mt5.order_calc_profit(order_type, symbol, 1.0, entry, stop_loss)
    if one_lot_loss is None or not (one_lot_loss < 0):
        return 0.0
    risk_budget = max(0.0, float(state["balance"])) * RISK_PER_TRADE_PERCENT / 100.0
    if risk_budget <= 0:
        return 0.0
    raw_lots = min(LOT_SIZE, risk_budget / abs(float(one_lot_loss)), info.volume_max)
    # Round down to the broker's volume step; never round risk upward.
    steps = int(raw_lots / info.volume_step + 1e-10)
    lots = steps * info.volume_step
    if lots + 1e-10 < info.volume_min:
        return 0.0
    return round(lots, 8)


def scan_symbol(symbol):
    global paused
    if paused or len(state["positions"]) >= MAX_OPEN_TRADES:
        return
    limit = SPREAD_LIMITS.get(symbol)
    if limit is None:
        return
    if any(p["symbol"] == symbol for p in state["positions"]):
        return
    tick = mt5.symbol_info_tick(symbol)
    if tick is None or tick.bid <= 0 or tick.ask < tick.bid or time.time() - tick.time > 10:
        return
    if tick.ask - tick.bid > limit:
        return
    rates = mt5.copy_rates_from_pos(symbol, mt5.TIMEFRAME_M5, 1, 120)  # completed bars only
    if rates is None or len(rates) < 60:
        return
    bars = [{
        "time": int(r["time"]), "open": float(r["open"]), "high": float(r["high"]),
        "low": float(r["low"]), "close": float(r["close"])
    } for r in rates]
    bars.sort(key=lambda b: b["time"])
    last_bar = str(bars[-1]["time"])
    if state["last_bar"].get(symbol) == last_bar:
        return
    state["last_bar"][symbol] = last_bar
    # Daily loss circuit-breaker includes open floating PnL.
    daily_limit = START_BALANCE * MAX_DAILY_LOSS_PERCENT / 100.0
    if current_daily_pnl() <= -daily_limit:
        paused = True
        state["paused"] = True
        save_state()
        send(f"风险熔断：当日模拟净盈亏已达到亏损限制 {-daily_limit:.2f}，机器人已暂停。")
        return
    from strategy import Bar
    signal = generate_signal([Bar(**b) for b in bars], tick.bid, tick.ask, limit, MIN_RR)
    if signal is None:
        save_state()
        return
    entry = signal.entry
    if not (signal.stop_loss < entry < signal.take_profit if signal.direction == "BUY"
            else signal.take_profit < entry < signal.stop_loss):
        return
    lots = risk_sized_lots(symbol, signal.direction, entry, signal.stop_loss)
    if lots <= 0:
        send(f"跳过 {symbol} {signal.direction}：最小模拟手数也会超过每笔风险上限，或 MT5 无法计算止损亏损。")
        return
    position = {
        "symbol": symbol, "direction": signal.direction, "entry": entry,
        "sl": signal.stop_loss, "tp": signal.take_profit, "lots": lots,
        "opened_at": datetime.now(timezone.utc).isoformat(), "signal_bar": last_bar,
        "reason": signal.reason,
    }
    state["positions"].append(position)
    save_state()
    send(f"自动模拟开仓：{symbol} {signal.direction}\n手数: {LOT_SIZE}\n进场 Bid/Ask: {entry}\nSL: {signal.stop_loss}\nTP: {signal.take_profit}\n计划 R:R: {MIN_RR:.2f}\n依据: {signal.reason}\n注意：模拟单，不会向 MT5 发送真实订单。")


def report():
    wins = sum(1 for t in state["closed"] if float(t["pnl"]) > 0)
    losses = sum(1 for t in state["closed"] if float(t["pnl"]) < 0)
    net = sum(float(t["pnl"]) for t in state["closed"])
    return (f"Gold-bee 纸面账户\n状态: {'暂停' if paused else '模拟运行中'}\n"
            f"模拟余额: {state['balance']:.2f}\n已平仓: {len(state['closed'])} | 盈利: {wins} | 亏损: {losses}\n"
            f"已平仓净盈亏: {net:+.2f}\n当前持仓: {len(state['positions'])}/{MAX_OPEN_TRADES}\n"
            f"品种: {', '.join(SYMBOLS)}\n执行模式: 仅模拟，不会下真实订单。")


def handle_command(message):
    if str(message.get("chat", {}).get("id", "")) != ALLOWED_CHAT_ID:
        return
    command = (message.get("text") or "").strip().split()[0].split("@")[0].lower() if (message.get("text") or "").strip() else ""
    if command in ("/start", "/help"):
        send("Gold-bee MT5 模拟机器人\n/status 查看盈亏\n/resume 开始自动模拟\n/pause 暂停新开仓\n/positions 查看持仓\n/report 查看已平仓记录\n\n默认暂停；仅使用 MT5 报价与模拟账本，不发送真实订单。")
    elif command == "/status":
        send(report())
    elif command == "/resume":
        global paused
        paused = False
        state["paused"] = False
        save_state()
        send("自动模拟已启动。只生成模拟单，不会执行真实订单。")
    elif command == "/pause":
        paused = True
        state["paused"] = True
        save_state()
        send("已暂停新开模拟单；现有模拟仓位仍会监控 SL/TP。")
    elif command == "/positions":
        if not state["positions"]:
            send("当前没有开放的模拟仓位。")
        else:
            send("\n\n".join(f"{p['symbol']} {p['direction']} {p['lots']} lot\nEntry {p['entry']} | SL {p['sl']} | TP {p['tp']}" for p in state["positions"]))
    elif command == "/report":
        recent = state["closed"][-10:]
        send(report() + ("\n\n最近平仓:\n" + "\n".join(
            f"{t['symbol']} {t['direction']} {float(t['pnl']):+.2f} ({t['reason']})"
            for t in recent) if recent else "\n\n尚无平仓记录。"))


def poll_telegram():
    global offset
    updates = telegram("getUpdates", {"offset": offset, "timeout": 1, "allowed_updates": ["message"]})
    for update in updates:
        offset = max(offset, int(update["update_id"]) + 1)
        if "message" in update:
            try:
                handle_command(update["message"])
            except Exception:
                traceback.print_exc()


def main():
    load_state()
    if not mt5.initialize():
        raise RuntimeError(f"MT5 initialize failed: {mt5.last_error()}")
    missing = [symbol for symbol in SYMBOLS if not mt5.symbol_select(symbol, True)]
    if missing:
        mt5.shutdown()
        raise RuntimeError(f"These configured MT5 symbols are unavailable; check broker suffixes: {missing}")
    send("Gold-bee 服务已连接 MT5。安全状态：暂停中；使用 /resume 才开始自动模拟。绝不发送真实订单。")
    try:
        while True:
            try:
                poll_telegram()
                check_positions()
                for symbol in SYMBOLS:
                    scan_symbol(symbol)
            except Exception as exc:
                print(f"Loop error: {exc}")
                traceback.print_exc()
            time.sleep(POLL_SECONDS)
    finally:
        mt5.shutdown()


if __name__ == "__main__":
    main()

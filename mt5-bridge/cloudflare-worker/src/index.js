/**
 * Gold Bee MT5 Tick Relay — Cloudflare Worker + KV.
 *
 * POST /tick : MT5 EA writes a tick (Authorization: Bearer BRIDGE_WRITE_TOKEN)
 * GET  /latest : Gold Bee Android reads latest tick (Authorization: Bearer BRIDGE_READ_TOKEN)
 * No orders, account credentials, or raw screen data are handled.
 */
const json = (data, status = 200) => new Response(JSON.stringify(data), {
  status,
  headers: {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store, no-cache, must-revalidate",
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "Authorization, Content-Type"
  }
});

function bearer(request) {
  const header = request.headers.get("Authorization") || "";
  return header.startsWith("Bearer ") ? header.slice(7).trim() : "";
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, { status: 204 });
    if (url.pathname === "/health" && request.method === "GET") {
      return json({ ok: true, service: "gold-bee-mt5-relay", mode: "read-only" });
    }
    if (url.pathname === "/tick" && request.method === "POST") {
      if (!env.TICKS || !env.BRIDGE_WRITE_TOKEN) return json({ ok: false, error: "relay_not_configured" }, 503);
      if (bearer(request) !== env.BRIDGE_WRITE_TOKEN) return json({ ok: false, error: "unauthorized" }, 401);
      let body;
      try { body = await request.json(); } catch { return json({ ok: false, error: "invalid_json" }, 400); }
      const symbol = typeof body.symbol === "string" ? body.symbol.trim().slice(0, 32) : "";
      const bid = Number(body.bid);
      const ask = Number(body.ask);
      const timestamp = Number(body.timestamp_ms);
      const now = Date.now();
      if (!symbol || !Number.isFinite(bid) || !Number.isFinite(ask) || bid <= 0 || ask < bid ||
          !Number.isFinite(timestamp) || timestamp <= 0 || timestamp > now + 5000 || now - timestamp > 15000) {
        return json({ ok: false, error: "invalid_or_stale_tick" }, 422);
      }
      const tick = { ok: true, symbol, bid, ask, timestamp_ms: Math.trunc(timestamp), received_at_ms: now, source: "MT5_EA" };
      await env.TICKS.put("latest", JSON.stringify(tick), { expirationTtl: 120 });
      return json({ ok: true, received_at_ms: now });
    }
    if (url.pathname === "/latest" && request.method === "GET") {
      if (!env.TICKS || !env.BRIDGE_READ_TOKEN) return json({ ok: false, error: "relay_not_configured" }, 503);
      if (bearer(request) !== env.BRIDGE_READ_TOKEN) return json({ ok: false, error: "unauthorized" }, 401);
      const raw = await env.TICKS.get("latest");
      if (!raw) return json({ ok: false, error: "waiting_for_mt5_tick" }, 404);
      let tick;
      try { tick = JSON.parse(raw); } catch { return json({ ok: false, error: "invalid_stored_tick" }, 500); }
      if (Date.now() - Number(tick.timestamp_ms) > 10000) return json({ ok: false, error: "stale_tick" }, 409);
      return json(tick);
    }
    return json({ ok: false, error: "not_found" }, 404);
  }
};

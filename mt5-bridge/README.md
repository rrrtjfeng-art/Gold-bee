# Gold Bee MT5 Tick Bridge (read-only)

This bridge transfers actual Bid/Ask ticks from desktop MT5 to Gold Bee Android. It does not place orders and does not expose MT5 account passwords.

## Files
- `mql5/GoldBeeBridge.mq5`: MT5 Expert Advisor publisher.
- `cloudflare-worker/src/index.js`: HTTPS relay.
- `cloudflare-worker/wrangler.toml`: deployment config template.

## One-time relay setup
1. Create a Cloudflare account and install Wrangler on a computer.
2. Create a KV namespace: `npx wrangler kv namespace create TICKS`.
3. Put the returned namespace ID into `cloudflare-worker/wrangler.toml`.
4. From `cloudflare-worker`, run `npx wrangler secret put BRIDGE_WRITE_TOKEN` and enter a long random secret. Then run `npx wrangler secret put BRIDGE_READ_TOKEN` and enter a different long random secret.
5. Deploy with `npx wrangler deploy`. Copy the HTTPS Worker URL.

## MT5 setup
1. Copy `mql5/GoldBeeBridge.mq5` into MT5 desktop's `MQL5/Experts` directory, open it in MetaEditor and compile.
2. In MT5: Tools > Options > Expert Advisors, enable **Allow WebRequest for listed URL** and add only your Worker HTTPS base URL.
3. Attach the EA to the broker's gold chart. Set `InpRelayBaseUrl` to the Worker URL and `InpWriteToken` to the write token. If the broker uses a suffix (e.g. XAUUSD.a), set `InpSymbol` to that exact symbol.
4. Confirm the Experts log says requests are accepted. This EA reads SymbolInfoTick and uses WebRequest only; it has no order-submission calls.

## Gold Bee Android
Configure the same Worker URL and the **read token** in Gold Bee's MT5 Bridge settings. Do not put the write token into Android. A tick is accepted only if the symbol, Bid/Ask and source timestamp are valid and fresh. Relay storage expires automatically.

## Important limitations
- This bridge needs desktop MT5 or MT5 running on a VPS; Android MT5 cannot run MQL5 Expert Advisors.
- Cloudflare Worker/KV setup requires a one-time account deployment. No relay URL can be prefilled before it exists.
- Demo-test it first and compare Bid/Ask and timestamps against the same MT5 terminal. Broker symbol names and market sessions differ.
- It is a quote-only bridge. It never submits, modifies, or closes orders.

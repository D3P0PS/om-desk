// Every crypto market the alerts can watch, from one light public request per venue
// (symbols + last price together), and the ticker search: type "btc", get every
// source that lists it. Parsers and search are pure, for the tests. A missing price
// is null, never 0.

import { httpJSON } from './http.js';

export const VENUES = ['BINANCE', 'BINANCE_FUTURES', 'BYBIT', 'HYPERLIQUID'] as const;
export type Venue = (typeof VENUES)[number];

export const VENUE_LABEL: Record<Venue, string> = {
  BINANCE: 'Binance',
  BINANCE_FUTURES: 'Binance Perp',
  BYBIT: 'Bybit Perp',
  HYPERLIQUID: 'Hyperliquid',
};

export interface Market {
  venue: Venue;
  symbol: string; // exchange symbol, as the alert service sends it back to the exchange
  base: string;
  quote: string;
  price: number | null;
}

// Longest first, so FDUSD wins over USD and USDT over USD.
const QUOTES = ['FDUSD', 'USDT', 'USDC', 'TUSD', 'BUSD', 'DAI', 'USD', 'BTC', 'ETH', 'BNB', 'EUR', 'TRY', 'BRL', 'JPY']
  .sort((a, b) => b.length - a.length);

function num(v: unknown): number | null {
  if (v === null || v === undefined || v === '') return null;
  const n = typeof v === 'number' ? v : Number(v);
  return Number.isFinite(n) && n > 0 ? n : null;
}

export function splitPair(symbol: string): { base: string; quote: string } | null {
  for (const q of QUOTES) {
    if (symbol.length > q.length && symbol.endsWith(q)) return { base: symbol.slice(0, -q.length), quote: q };
  }
  return null;
}

/** Binance spot `/api/v3/ticker/price` or futures `/fapi/v1/ticker/price`. Dated contracts (X_250926) are skipped. */
export function parseBinanceTickers(json: unknown, venue: 'BINANCE' | 'BINANCE_FUTURES'): Market[] {
  if (!Array.isArray(json)) throw new Error(`${venue}: expected an array of tickers`);
  const out: Market[] = [];
  for (const t of json as { symbol?: string; price?: string }[]) {
    if (typeof t.symbol !== 'string' || t.symbol.includes('_')) continue;
    const pair = splitPair(t.symbol);
    if (!pair) continue;
    out.push({ venue, symbol: t.symbol, ...pair, price: num(t.price) });
  }
  return out;
}

/** Bybit `/v5/market/tickers?category=linear`. */
export function parseBybitTickers(json: unknown): Market[] {
  const list = (json as { result?: { list?: { symbol?: string; lastPrice?: string }[] } }).result?.list;
  if (!Array.isArray(list)) throw new Error('bybit: missing result.list');
  const out: Market[] = [];
  for (const t of list) {
    if (typeof t.symbol !== 'string' || t.symbol.includes('-')) continue; // dated futures
    const pair = splitPair(t.symbol);
    if (!pair) continue;
    out.push({ venue: 'BYBIT', symbol: t.symbol, ...pair, price: num(t.lastPrice) });
  }
  return out;
}

/** Hyperliquid `allMids`: coin → mid. Spot ("@107") and outcome ("#123") keys are skipped. */
export function parseHlMids(json: unknown): Market[] {
  if (!json || typeof json !== 'object' || Array.isArray(json)) throw new Error('hyperliquid: expected an object of mids');
  const out: Market[] = [];
  for (const [coin, v] of Object.entries(json as Record<string, string>)) {
    if (coin.startsWith('@') || coin.startsWith('#') || coin.includes(':')) continue;
    // 1000x contracts are named kPEPE; the coin people type is PEPE.
    const base = /^k[A-Z]/.test(coin) ? coin.slice(1) : coin;
    out.push({ venue: 'HYPERLIQUID', symbol: coin, base, quote: 'USD', price: num(v) });
  }
  return out;
}

const QUOTE_RANK: Record<string, number> = { USDT: 0, USD: 1, USDC: 2, FDUSD: 3 };

/**
 * Ticker search. Exact coin first ("btc" → every BTC market on every venue), then
 * coins starting with the query, then symbols containing it. Within a coin: dollar
 * quotes first, then venue order.
 */
export function searchMarkets(all: Market[], query: string, limit = 40): Market[] {
  const q = query.trim().toUpperCase().replace(/[\s/:-]/g, '');
  if (!q) return [];
  const score = (m: Market): number => {
    if (m.base.toUpperCase() === q) return 0;
    if (m.symbol.toUpperCase() === q) return 1;
    if (m.base.toUpperCase().startsWith(q)) return 2;
    if (m.symbol.toUpperCase().includes(q)) return 3;
    return -1;
  };
  return all
    .map((m) => ({ m, s: score(m) }))
    .filter((x) => x.s >= 0)
    .sort((a, b) =>
      a.s - b.s
      || a.m.base.localeCompare(b.m.base)
      || (QUOTE_RANK[a.m.quote] ?? 9) - (QUOTE_RANK[b.m.quote] ?? 9)
      || VENUES.indexOf(a.m.venue) - VENUES.indexOf(b.m.venue))
    .slice(0, limit)
    .map((x) => x.m);
}

// --- loading -------------------------------------------------------------------

let cache: { at: number; markets: Market[]; failed: Venue[] } | null = null;

/** All venues in parallel; a venue that fails is reported, the others still load. */
export async function loadMarkets(maxAgeMs = 60_000): Promise<{ markets: Market[]; failed: Venue[] }> {
  if (cache && Date.now() - cache.at < maxAgeMs) return cache;
  const jobs: [Venue, Promise<Market[]>][] = [
    ['BINANCE', httpJSON('https://api.binance.com/api/v3/ticker/price').then((r) => parseBinanceTickers(r.data, 'BINANCE'))],
    ['BINANCE_FUTURES', httpJSON('https://fapi.binance.com/fapi/v1/ticker/price').then((r) => parseBinanceTickers(r.data, 'BINANCE_FUTURES'))],
    ['BYBIT', httpJSON('https://api.bybit.com/v5/market/tickers?category=linear').then((r) => parseBybitTickers(r.data))],
    ['HYPERLIQUID', httpJSON('https://api.hyperliquid.xyz/info', { method: 'POST', body: { type: 'allMids' } }).then((r) => parseHlMids(r.data))],
  ];
  const settled = await Promise.allSettled(jobs.map(([, p]) => p));
  const markets: Market[] = [];
  const failed: Venue[] = [];
  settled.forEach((r, i) => {
    if (r.status === 'fulfilled') markets.push(...r.value);
    else {
      failed.push(jobs[i][0]);
      console.warn(`markets: ${jobs[i][0]} failed:`, (r.reason as Error).message);
    }
  });
  if (markets.length === 0) throw new Error('no exchange answered');
  cache = { at: Date.now(), markets, failed };
  return cache;
}

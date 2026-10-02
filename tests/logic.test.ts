// Pure logic of the panel: market parsers, ticker search, i18n.
// Fixtures are trimmed copies of real responses (2026-10-01). No network.

import assert from 'node:assert/strict';
import { test } from 'node:test';
import { t, DICT_KEYS } from '../web/src/i18n.js';
import {
  parseBinanceTickers, parseBybitTickers, parseHlMids, searchMarkets, splitPair, type Market,
} from '../web/src/markets.js';

test('splitPair: longest quote wins, unknown quotes are skipped', () => {
  assert.deepEqual(splitPair('BTCUSDT'), { base: 'BTC', quote: 'USDT' });
  assert.deepEqual(splitPair('BTCFDUSD'), { base: 'BTC', quote: 'FDUSD' });
  assert.deepEqual(splitPair('ETHBTC'), { base: 'ETH', quote: 'BTC' });
  assert.equal(splitPair('USDT'), null);
  assert.equal(splitPair('XYZABC'), null);
});

test('parseBinanceTickers: skips dated contracts and zero prices become null', () => {
  const m = parseBinanceTickers([
    { symbol: 'BTCUSDT', price: '83952.00' },
    { symbol: 'BTCUSDT_251226', price: '85000' },
    { symbol: 'DEADUSDT', price: '0.00000000' },
    { symbol: 'WEIRD', price: '1' },
  ], 'BINANCE_FUTURES');
  assert.deepEqual(m, [
    { venue: 'BINANCE_FUTURES', symbol: 'BTCUSDT', base: 'BTC', quote: 'USDT', price: 83952 },
    { venue: 'BINANCE_FUTURES', symbol: 'DEADUSDT', base: 'DEAD', quote: 'USDT', price: null },
  ]);
  assert.throws(() => parseBinanceTickers({ code: -1 }, 'BINANCE'), /expected an array/);
});

test('parseBybitTickers: linear perps only', () => {
  const m = parseBybitTickers({ result: { list: [
    { symbol: 'ETHUSDT', lastPrice: '2680.03' },
    { symbol: 'BTC-26DEC25', lastPrice: '90000' },
  ] } });
  assert.deepEqual(m, [{ venue: 'BYBIT', symbol: 'ETHUSDT', base: 'ETH', quote: 'USDT', price: 2680.03 }]);
  assert.throws(() => parseBybitTickers({ retCode: 10001, result: {} }), /missing result.list/);
});

test('parseHlMids: perps only, kPEPE searchable as PEPE but sent back as kPEPE', () => {
  const m = parseHlMids({ BTC: '83952.5', kPEPE: '0.0123', '@107': '1.2', '#12090': '0.003', 'xyz:TSLA': '400' });
  assert.deepEqual(m, [
    { venue: 'HYPERLIQUID', symbol: 'BTC', base: 'BTC', quote: 'USD', price: 83952.5 },
    { venue: 'HYPERLIQUID', symbol: 'kPEPE', base: 'PEPE', quote: 'USD', price: 0.0123 },
  ]);
});

const ALL: Market[] = [
  ...parseBinanceTickers([{ symbol: 'BTCUSDT', price: '1' }, { symbol: 'BTCFDUSD', price: '1' }, { symbol: 'BTCDOMUSDT', price: '1' }, { symbol: 'WBTCUSDT', price: '1' }], 'BINANCE'),
  ...parseBinanceTickers([{ symbol: 'BTCUSDT', price: '1' }], 'BINANCE_FUTURES'),
  ...parseBybitTickers({ result: { list: [{ symbol: 'BTCUSDT', lastPrice: '1' }] } }),
  ...parseHlMids({ BTC: '1', kPEPE: '1' }),
  ...parseBinanceTickers([{ symbol: 'PEPEUSDT', price: '1' }], 'BINANCE'),
];

test('searchMarkets: "btc" lists every BTC source first, dollar quotes before others', () => {
  const r = searchMarkets(ALL, 'btc').map((m) => `${m.venue}:${m.symbol}`);
  assert.deepEqual(r.slice(0, 5), ['BINANCE:BTCUSDT', 'BINANCE_FUTURES:BTCUSDT', 'BYBIT:BTCUSDT', 'HYPERLIQUID:BTC', 'BINANCE:BTCFDUSD']);
  assert.ok(r.indexOf('BINANCE:BTCDOMUSDT') > 4, 'prefix matches come after exact coin matches');
  assert.ok(r.includes('BINANCE:WBTCUSDT'), 'substring matches are still found');
});

test('searchMarkets: tolerant input ("btc/usdt", "pepe" finds kPEPE), empty query → nothing', () => {
  assert.equal(searchMarkets(ALL, ' btc/usdt ')[0].symbol, 'BTCUSDT');
  assert.deepEqual(searchMarkets(ALL, 'pepe').map((m) => m.symbol).sort(), ['PEPEUSDT', 'kPEPE']);
  assert.deepEqual(searchMarkets(ALL, '   '), []);
});

test('i18n: placeholders filled, unknown vars left visible, every key non-empty', () => {
  assert.equal(t('search.none', { q: 'FOO' }), 'No crypto market matches "FOO".');
  assert.equal(t('alerts.describe.above'), 'above {level}');
  for (const k of DICT_KEYS) assert.ok(t(k).trim().length > 0, k);
});

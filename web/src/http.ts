// JSON over HTTP. Inside the Android app requests go through CapacitorHttp
// (native, so CORS does not apply); in a plain browser (dev, headless checks)
// through fetch. Errors are raised with the host and status, never swallowed.

import { Capacitor, CapacitorHttp } from '@capacitor/core';

export interface HttpResult<T> {
  data: T;
  headers: Record<string, string>;
}

export async function httpJSON<T>(
  url: string,
  opts: { method?: 'GET' | 'POST'; body?: unknown; headers?: Record<string, string> } = {},
): Promise<HttpResult<T>> {
  const method = opts.method ?? 'GET';
  const headers: Record<string, string> = { ...(opts.headers ?? {}) };
  if (opts.body !== undefined) headers['content-type'] = 'application/json';
  const where = new URL(url).host;

  if (Capacitor.isNativePlatform()) {
    const res = await CapacitorHttp.request({ url, method, headers, data: opts.body, responseType: 'json' });
    if (res.status < 200 || res.status >= 300) {
      throw new Error(`${where} HTTP ${res.status}`);
    }
    const data = typeof res.data === 'string' ? (JSON.parse(res.data) as T) : (res.data as T);
    return { data, headers: lowerKeys(res.headers ?? {}) };
  }

  const res = await fetch(url, {
    method,
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  if (!res.ok) throw new Error(`${where} HTTP ${res.status}`);
  const out: Record<string, string> = {};
  res.headers.forEach((v, k) => { out[k.toLowerCase()] = v; });
  return { data: (await res.json()) as T, headers: out };
}

function lowerKeys(h: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(h)) out[k.toLowerCase()] = String(v);
  return out;
}

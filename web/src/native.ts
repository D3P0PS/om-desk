// Bridge to the app's own Java plugins (android/app/src/main/java/xyz/aries/omapp/).
// In a plain browser they do not exist: `isNative` is false and the UI says so
// instead of faking the behaviour.

import { Capacitor, registerPlugin } from '@capacitor/core';
import { Preferences } from '@capacitor/preferences';

export const isNative = Capacitor.isNativePlatform();

export interface OmWebViewPlugin {
  /** Show openmarket.xyz full screen (between the system bars) with the app's floating button. */
  show(): Promise<void>;
  hide(): Promise<void>;
  back(): Promise<{ handled: boolean }>;
  reload(): Promise<void>;
  navigate(o: { url: string }): Promise<void>;
  toast(o: { text: string }): Promise<void>;
  /** Opens a link in the phone's browser (never inside the app). */
  openExternal(o: { url: string }): Promise<void>;
  /** 'menu': the floating button was tapped. */
  addListener(event: 'menu', fn: () => void): Promise<{ remove: () => Promise<void> }>;
}

export type AlertKind = 'above' | 'below' | 'cross' | 'pct';

export interface Alert {
  id: string;
  venue: string;
  symbol: string;
  kind: AlertKind;
  level: number; // price, or percent for 'pct'
  basePrice: number | null; // reference for 'pct'; filled by the service if null
  lastPrice: number | null;
  triggered: boolean;
  triggeredAt: number | null; // ms
  note: string;
}

export interface AlertsPlugin {
  list(): Promise<{ alerts: Alert[]; running: boolean; intervalSec: number; lastCheck: number | null; lastError: string | null }>;
  add(o: { alert: Omit<Alert, 'id' | 'lastPrice' | 'triggered' | 'triggeredAt'> }): Promise<{ id: string }>;
  remove(o: { id: string }): Promise<void>;
  rearm(o: { id: string }): Promise<void>;
  setInterval(o: { seconds: number }): Promise<void>;
  requestNotifications(): Promise<{ granted: boolean }>;
  requestBatteryExemption(): Promise<void>;
}

export const OmWebView = registerPlugin<OmWebViewPlugin>('OmWebView');
export const Alerts = registerPlugin<AlertsPlugin>('Alerts');

export async function loadJSON<T>(key: string, fallback: T): Promise<T> {
  const { value } = await Preferences.get({ key });
  if (value === null) return fallback;
  try {
    return JSON.parse(value) as T;
  } catch {
    console.warn(`omapp: stored '${key}' unreadable, using default`);
    return fallback;
  }
}

export async function saveJSON(key: string, v: unknown): Promise<void> {
  await Preferences.set({ key, value: JSON.stringify(v) });
}

// UI strings. English only for now; to add a language, add a dictionary with the
// same keys (TypeScript enforces it) and register it in DICTS. The device language
// picks the dictionary, English is the fallback. Native strings (notifications,
// toasts) live in android/app/src/main/res/values*/strings.xml.

const en = {
  'panel.back': 'OpenMarket',
  'om.browserOnly': 'In the app, OpenMarket fills the screen and this panel opens from its floating bell button. Alerts run only in the Android app.',

  'alerts.title': 'Alerts',
  'alerts.new': 'New alert',
  'alerts.running': 'Monitoring on',
  'alerts.stopped': 'Monitoring off',
  'alerts.status': '{state} · every {sec}s · last check {last}',
  'alerts.never': 'never',
  'alerts.statusError': ' · error: {error}',
  'alerts.kind.above': 'Above',
  'alerts.kind.below': 'Below',
  'alerts.kind.cross': 'Crosses',
  'alerts.kind.pct': 'Move ±%',
  'alerts.describe.above': 'above {level}',
  'alerts.describe.below': 'below {level}',
  'alerts.describe.cross': 'crosses {level}',
  'alerts.describe.pct': 'moves ±{level}% from {base}',
  'alerts.triggered': 'triggered',
  'alerts.armed': 'armed',
  'alerts.rearm': 'Re-arm',
  'alerts.delete': 'Delete',
  'alerts.empty': 'No alerts yet. Price alerts ring even with the app closed.',
  'alerts.note': 'Note (optional)',
  'alerts.levelPct': 'Percent, e.g. 5',
  'alerts.levelPrice': 'Price · now {price}',
  'alerts.invalid': 'Enter a positive number',
  'alerts.denied': 'Notifications are off for OM Desk: the alert is saved but cannot ring. Turn them on in Android settings.',
  'alerts.create': 'Create alert',
  'alerts.onceHint': 'Fires once, then you can re-arm it.',

  'search.placeholder': 'Search a ticker: BTC, ETH, SOL…',
  'search.loading': 'Loading markets…',
  'search.none': 'No crypto market matches "{q}".',
  'search.scope': 'Crypto markets on Binance, Bybit and Hyperliquid. Stocks and commodities are not available for alerts yet.',
  'search.failed': 'Not reachable right now: {venues}.',
  'search.error': 'Could not load markets: {error}',
  'search.change': 'Change',

  'more.title': 'Settings',
  'more.every': 'Check alerts every',
  'more.battery': 'Exclude from battery optimization',
  'more.batteryHint': 'Without the exclusion Android may delay alerts while the screen is off.',
  'more.reload': 'Reload OpenMarket',
  'more.loginHint': 'OpenMarket login: use email or password. Google sign-in does not work inside apps (Google blocks it).',
  'more.fabHint': 'The round bell button over OpenMarket opens this panel. Drag it up or down to move it out of the way.',
  'more.version': 'OM Desk {version}',

  'support.title': 'Support OM Desk',
  'support.text': 'OM Desk is free and has no ads. If it is useful to you, you can buy me a coffee.',
  'support.kofi': 'Buy me a coffee on Ko-fi',
  'support.copy': 'Copy',
  'support.copied': 'Address copied',
  'support.check': 'Always check the address after pasting it.',
  'about.title': 'About',
  'about.disclaimer': 'OM Desk is an independent, unofficial app. It is not affiliated with, endorsed by or sponsored by OpenMarket (openmarket.xyz). It shows their public website; your OpenMarket account, plan and payments stay with OpenMarket. OpenMarket and its logo belong to their owners.',
  'about.data': 'Alert prices come from the public APIs of Binance, Bybit and Hyperliquid and can be delayed or wrong. Nothing in this app is investment advice.',
  'notice.title': 'Before you start',
  'notice.ok': 'Got it',
};

export type Key = keyof typeof en;
type Dict = Record<Key, string>;

const DICTS: Record<string, Dict> = { en };

function pick(): Dict {
  const langs = typeof navigator === 'undefined' ? [] : [...(navigator.languages ?? []), navigator.language];
  for (const l of langs) {
    const d = l && DICTS[l.toLowerCase().split('-')[0]];
    if (d) return d;
  }
  return en;
}

const dict = pick();

export function t(key: Key, vars: Record<string, string | number> = {}): string {
  return dict[key].replace(/\{(\w+)\}/g, (_, k: string) => (k in vars ? String(vars[k]) : `{${k}}`));
}

/** Fills static markup: data-i18n (text), data-i18n-placeholder, data-i18n-aria. */
export function applyStatic(root: ParentNode = document): void {
  for (const el of root.querySelectorAll<HTMLElement>('[data-i18n]')) el.textContent = t(el.dataset.i18n as Key);
  for (const el of root.querySelectorAll<HTMLElement>('[data-i18n-placeholder]')) {
    el.setAttribute('placeholder', t(el.dataset.i18nPlaceholder as Key));
  }
  for (const el of root.querySelectorAll<HTMLElement>('[data-i18n-aria]')) {
    el.setAttribute('aria-label', t(el.dataset.i18nAria as Key));
  }
  document.documentElement.lang = dict === en ? 'en' : document.documentElement.lang;
}

export const DICT_KEYS = Object.keys(en) as Key[];

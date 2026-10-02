// OM Desk: openmarket.xyz full screen (native view, its own navigation), plus this
// panel behind a floating bell button: price alerts that ring with the app closed,
// settings, about. One UI at a time, never two navigation bars.

import { App } from '@capacitor/app';
import { applyStatic, t, type Key } from './i18n.js';
import { loadMarkets, searchMarkets, VENUE_LABEL, type Market, type Venue } from './markets.js';
import { Alerts, isNative, loadJSON, OmWebView, saveJSON, type Alert, type AlertKind } from './native.js';
import { $, closeSheet, fmtPrice, h, keepAboveKeyboard, openSheet, sheetOpen, toast } from './ui.js';

declare const __VERSION__: string;
declare const __DONATE__: { kofi: string | null; crypto: { label: string; address: string }[] };

/** 'om' = OpenMarket on screen; 'panel' = this page. */
let mode: 'om' | 'panel' = 'om';
/** True while the first-run notice is up: the native OpenMarket view must stay hidden. */
let noticeOpen = false;

// --- switching between OpenMarket and the panel -------------------------------------

async function showOpenMarket(): Promise<void> {
  closeSheet();
  mode = 'om';
  if (isNative) await OmWebView.show();
}

async function showPanel(page: 'alerts' | 'settings' = 'alerts'): Promise<void> {
  mode = 'panel';
  if (isNative) await OmWebView.hide();
  selectPage(page);
}

function selectPage(page: 'alerts' | 'settings'): void {
  for (const b of document.querySelectorAll<HTMLButtonElement>('#panelTabs button')) b.classList.toggle('on', b.dataset.p === page);
  $('p-alerts').hidden = page !== 'alerts';
  $('p-settings').hidden = page !== 'settings';
  if (page === 'alerts') void renderAlerts();
}

// --- alerts ------------------------------------------------------------------------

const KINDS: AlertKind[] = ['above', 'below', 'cross', 'pct'];

function describe(a: Pick<Alert, 'kind' | 'level' | 'basePrice'>): string {
  if (a.kind === 'pct') return t('alerts.describe.pct', { level: a.level, base: fmtPrice(a.basePrice) });
  return t(`alerts.describe.${a.kind}` as Key, { level: fmtPrice(a.level) });
}

function venueLabel(v: string): string {
  return VENUE_LABEL[v as Venue] ?? v;
}

async function renderAlerts(): Promise<void> {
  const ul = $('alertList');
  const status = $('alertStatus');
  if (!isNative) {
    status.textContent = '';
    ul.replaceChildren(h('li', { class: 'empty' }, t('alerts.empty')));
    return;
  }
  const r = await Alerts.list();
  const last = r.lastCheck ? new Date(r.lastCheck).toLocaleTimeString() : t('alerts.never');
  status.textContent = r.alerts.length === 0 ? '' : t('alerts.status', { state: t(r.running ? 'alerts.running' : 'alerts.stopped'), sec: r.intervalSec, last })
    + (r.lastError ? t('alerts.statusError', { error: r.lastError }) : '');
  status.className = `status-line ${r.lastError ? 'err' : ''}`;
  if (r.alerts.length === 0) {
    ul.replaceChildren(h('li', { class: 'empty' }, t('alerts.empty')));
    return;
  }
  ul.replaceChildren(...r.alerts.map((a) => {
    const li = h('li', { class: a.triggered ? 'done' : '' },
      h('div', { class: 'main' },
        h('div', {}, h('span', { class: 'sym' }, a.symbol), ' ', h('span', { class: 'venue' }, venueLabel(a.venue))),
        h('div', { class: 'what' }, describe(a) + (a.note ? ` · ${a.note}` : '')),
      ),
      h('div', { class: 'side' },
        h('div', { class: a.triggered ? 'up' : 'muted' }, t(a.triggered ? 'alerts.triggered' : 'alerts.armed')),
        h('div', { class: 'muted' }, a.triggeredAt ? new Date(a.triggeredAt).toLocaleString() : fmtPrice(a.lastPrice)),
      ),
    );
    li.addEventListener('click', () => {
      let close = () => {};
      close = openSheet([
        h('h2', {}, `${a.symbol} · ${venueLabel(a.venue)}`),
        h('p', { class: 'muted' }, describe(a)),
        h('div', { class: 'menu' },
          a.triggered ? h('button', { onclick: async () => { close(); await Alerts.rearm({ id: a.id }); await renderAlerts(); } }, t('alerts.rearm')) : null,
          h('button', { class: 'danger', onclick: async () => { close(); await Alerts.remove({ id: a.id }); await renderAlerts(); } }, t('alerts.delete')),
        ),
      ]);
    });
    return li;
  }));
}

/** Step 1: type a ticker, pick one of the sources that list it. */
function newAlertSheet(): void {
  const input = h('input', { type: 'search', placeholder: t('search.placeholder'), autocomplete: 'off', spellcheck: 'false', autocapitalize: 'characters' });
  const results = h('div', { class: 'results' });
  const info = h('p', { class: 'hint' }, t('search.loading'));
  openSheet([h('h2', {}, t('alerts.new')), input, info, results]);
  input.focus();

  let markets: Market[] = [];
  const paint = () => {
    const q = input.value.trim();
    if (!q) { results.replaceChildren(); return; }
    const hits = searchMarkets(markets, q);
    if (hits.length === 0) {
      results.replaceChildren(h('p', { class: 'muted' }, t('search.none', { q })));
      return;
    }
    results.replaceChildren(...hits.map((m) => h('button', { class: 'result', onclick: () => alertForm(m) },
      h('span', { class: 'coin' }, m.base.slice(0, 4)),
      h('span', { class: 'main' },
        h('div', { class: 'sym' }, m.base, h('span', { class: 'venue' }, ` / ${m.quote}`)),
        h('div', { class: 'venue' }, `${VENUE_LABEL[m.venue]} · ${m.symbol}`),
      ),
      h('span', { class: 'px' }, fmtPrice(m.price)),
    )));
  };
  input.addEventListener('input', paint);

  loadMarkets().then(({ markets: all, failed }) => {
    markets = all;
    info.textContent = t('search.scope') + (failed.length ? ` ${t('search.failed', { venues: failed.map((v) => VENUE_LABEL[v]).join(', ') })}` : '');
    paint();
  }).catch((e: Error) => { info.textContent = t('search.error', { error: e.message }); });
}

/** Step 2: condition and level for the chosen market. */
function alertForm(m: Market): void {
  let kind: AlertKind = 'above';
  const level = h('input', { type: 'number', inputmode: 'decimal', step: 'any' });
  const note = h('input', { type: 'text', placeholder: t('alerts.note') });
  const msg = h('p', { class: 'msg' });
  const seg = h('div', { class: 'seg' });
  const paintSeg = () => {
    seg.replaceChildren(...KINDS.map((k) => h('button', {
      class: k === kind ? 'on' : '',
      onclick: () => { kind = k; paintSeg(); },
    }, t(`alerts.kind.${k}` as Key))));
    level.placeholder = kind === 'pct' ? t('alerts.levelPct') : t('alerts.levelPrice', { price: fmtPrice(m.price) });
  };
  paintSeg();
  const create = h('button', {
    class: 'primary',
    onclick: async () => {
      const v = Number(level.value);
      if (!level.value || !Number.isFinite(v) || v <= 0) { msg.textContent = t('alerts.invalid'); return; }
      if (!isNative) { toast(t('om.browserOnly')); return; }
      const { granted } = await Alerts.requestNotifications();
      await Alerts.add({ alert: { venue: m.venue, symbol: m.symbol, kind, level: v, basePrice: kind === 'pct' ? m.price : null, note: note.value.trim() } });
      if (!granted) { msg.textContent = t('alerts.denied'); await renderAlerts(); return; }
      closeSheet();
      await renderAlerts();
    },
  }, t('alerts.create'));
  openSheet([
    h('h2', {}, t('alerts.new')),
    h('div', { class: 'picked' },
      h('div', { class: 'main' },
        h('div', { class: 'sym' }, `${m.base} / ${m.quote}`),
        h('div', { class: 'venue' }, `${VENUE_LABEL[m.venue]} · ${m.symbol} · ${fmtPrice(m.price)}`),
      ),
      h('button', { class: 'link-btn', onclick: () => newAlertSheet() }, t('search.change')),
    ),
    seg, level, note, create, msg,
    h('p', { class: 'hint' }, t('alerts.onceHint')),
  ]);
  level.focus();
}

// --- settings ------------------------------------------------------------------------

async function wireSettings(): Promise<void> {
  const every = $('alertEvery') as HTMLSelectElement;
  if (isNative) every.value = String((await Alerts.list()).intervalSec);
  every.addEventListener('change', () => { if (isNative) void Alerts.setInterval({ seconds: Number(every.value) }); });
  $('batteryBtn').addEventListener('click', () => { if (isNative) void Alerts.requestBatteryExemption(); });
  $('omReload').addEventListener('click', async () => { if (isNative) { await OmWebView.reload(); await showOpenMarket(); } });
  $('version').textContent = t('more.version', { version: __VERSION__ });
  wireSupport();
}

/** "Buy me a coffee": Ko-fi link and/or crypto addresses from donate.json; hidden when empty. */
function wireSupport(): void {
  const items: Node[] = [];
  if (__DONATE__.kofi) {
    const url = __DONATE__.kofi;
    items.push(h('button', {
      class: 'primary',
      onclick: () => { if (isNative) void OmWebView.openExternal({ url }); else window.open(url, '_blank', 'noopener'); },
    }, `☕ ${t('support.kofi')}`));
  }
  for (const c of __DONATE__.crypto) {
    items.push(h('div', { class: 'addr' },
      h('div', { class: 'main' }, h('div', { class: 'venue' }, c.label), h('code', {}, c.address)),
      h('button', {
        class: 'link-btn',
        onclick: async () => {
          try {
            await navigator.clipboard.writeText(c.address);
            toast(t('support.copied'));
          } catch {
            toast(c.address); // clipboard refused: at least show it in full
          }
        },
      }, t('support.copy')),
    ));
  }
  if (__DONATE__.crypto.length) items.push(h('p', { class: 'hint' }, t('support.check')));
  $('supportItems').replaceChildren(...items);
  $('support').hidden = items.length === 0;
}

// --- first-run notice ------------------------------------------------------------------

/** Non-affiliation notice, once per install, before OpenMarket appears (it would cover it). */
async function firstRunNotice(): Promise<void> {
  if (await loadJSON<boolean>('disclaimerSeen', false)) return;
  noticeOpen = true;
  await new Promise<void>((done) => {
    const ok = h('button', { class: 'primary' }, t('notice.ok'));
    const close = openSheet([
      h('h2', {}, t('notice.title')),
      h('p', {}, t('about.disclaimer')),
      h('p', { class: 'hint' }, t('about.data')),
      ok,
    ], { modal: true });
    ok.addEventListener('click', () => { close(); done(); });
  });
  noticeOpen = false;
  await saveJSON('disclaimerSeen', true);
}

// --- boot -------------------------------------------------------------------------

async function boot(): Promise<void> {
  applyStatic();
  keepAboveKeyboard();
  for (const b of document.querySelectorAll<HTMLButtonElement>('#panelTabs button')) {
    b.addEventListener('click', () => selectPage(b.dataset.p as 'alerts' | 'settings'));
  }
  $('closePanel').addEventListener('click', () => void showOpenMarket());
  $('newAlertBtn').addEventListener('click', newAlertSheet);
  await wireSettings();

  void App.addListener('backButton', async () => {
    if (noticeOpen) { await App.minimizeApp(); return; }
    if (sheetOpen()) { closeSheet(); return; }
    if (mode === 'panel') { await showOpenMarket(); return; }
    const { handled } = await OmWebView.back();
    if (!handled) await App.minimizeApp();
  });
  void App.addListener('resume', () => { if (mode === 'panel') void renderAlerts(); });

  if (!isNative) {
    const note = $('browserNote');
    note.textContent = t('om.browserOnly');
    note.hidden = false;
    $('closePanel').hidden = true;
  }

  await firstRunNotice();
  if (isNative) {
    void OmWebView.addListener('menu', () => void showPanel('alerts'));
    await showOpenMarket();
  } else {
    await showPanel('alerts');
  }
}

window.addEventListener('error', (e) => console.error('omapp:', e.message));
void boot();

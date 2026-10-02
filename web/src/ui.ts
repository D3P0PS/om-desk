// Small DOM helpers: element builder, bottom sheet, price formatting, web toast.

export function $(id: string): HTMLElement {
  const el = document.getElementById(id);
  if (!el) throw new Error(`missing #${id}`);
  return el;
}

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Record<string, unknown> = {},
  ...children: (Node | string | null | undefined)[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') el.className = String(v);
    else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v as EventListener);
    else if (k in el && typeof v !== 'string') (el as unknown as Record<string, unknown>)[k] = v;
    else el.setAttribute(k, String(v));
  }
  for (const c of children) if (c !== null && c !== undefined) el.append(c);
  return el;
}

/** Bottom sheet. `modal` sheets ignore taps on the backdrop (only their own buttons close them). */
export function openSheet(content: Node[], opts: { modal?: boolean } = {}): () => void {
  const root = $('sheet');
  const body = root.querySelector('.sheet-body') as HTMLElement;
  body.replaceChildren(...content);
  root.hidden = false;
  const close = () => { root.hidden = true; body.replaceChildren(); };
  (root.querySelector('.sheet-backdrop') as HTMLElement).onclick = opts.modal ? null : close;
  return close;
}

export function sheetOpen(): boolean {
  return !$('sheet').hidden;
}

export function closeSheet(): boolean {
  const root = $('sheet');
  if (root.hidden) return false;
  root.hidden = true;
  (root.querySelector('.sheet-body') as HTMLElement).replaceChildren();
  return true;
}

/** Decimals by magnitude, like an exchange ticker: 83,952 · 2,680.03 · 0.4312 · 0.00001234. */
export function fmtPrice(v: number | null): string {
  if (v === null) return 'n/a';
  const a = Math.abs(v);
  const d = a >= 10000 ? 0 : a >= 100 ? 2 : a >= 1 ? 3 : a >= 0.01 ? 4 : 8;
  return v.toLocaleString('en-US', { minimumFractionDigits: d, maximumFractionDigits: d });
}

/** Short message at the bottom of this page (browser builds; the app uses a native toast). */
export function toast(msg: string, ms = 3500): void {
  let el = document.getElementById('toast');
  if (!el) {
    el = document.createElement('div');
    el.id = 'toast';
    document.body.append(el);
  }
  el.textContent = msg;
  el.classList.add('on');
  const box = el as HTMLElement & { _t?: ReturnType<typeof setTimeout> };
  clearTimeout(box._t);
  box._t = setTimeout(() => el?.classList.remove('on'), ms);
}

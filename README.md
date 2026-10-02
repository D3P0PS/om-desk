# OM Desk

**An unofficial Android app for [OpenMarket](https://openmarket.xyz), with price alerts that ring even when the app is closed.**

While we wait for OpenMarket's official app: OM Desk opens openmarket.xyz full screen, exactly
as the site is (its chart, official and kScript Hub indicators, search, account), and adds what a
website cannot do on a phone, background price alerts.

Free, no ads, no tracking, no account. If it is useful to you, [buy me a coffee](#support).

> OM Desk is an independent project. It is **not affiliated with, endorsed by or sponsored by
> OpenMarket**. It shows their public website; your OpenMarket account, plan and payments stay
> with OpenMarket. OpenMarket and its logo belong to their owners.

## Features

- **OpenMarket full screen**: the real site with its own navigation. Login persists, the
  Android back button works, and external links open in your browser.
- **Price alerts**: tap the bell button on the right edge (drag it up or down to move it),
  type a ticker (`btc`, `eth`, `sol`…) and pick the source: Binance, Binance Perp, Bybit Perp
  or Hyperliquid. Choose above / below / crosses / move ±%. Alerts fire once, can be re-armed,
  and are checked every 30 s – 5 min by a background service, even with the app closed.
- **Private by design**: no analytics, no server of ours. The app talks only to openmarket.xyz
  and to the public price APIs of Binance, Bybit and Hyperliquid. No script is injected into
  OpenMarket's site. Backups of app data are disabled, so your session never leaves the phone.

## Install

1. Download `OM-Desk-<version>.apk` from [Releases](../../releases).
2. Optional but recommended: check the file. Its SHA-256 must match the `.sha256` file of
   the release, and the signing certificate must match the one below.
3. Open the APK on your phone and allow "install unknown apps" for your browser or file manager.
4. On the first alert, allow notifications. For reliable alerts with the screen off, open
   the bell button → Settings → *Exclude from battery optimization*.

Signing certificate SHA-256 (every official release is signed with this key):
`97:43:6C:7A:CF:E8:F7:00:7E:C0:24:71:90:19:82:CC:69:35:0F:49:DF:24:19:62:01:87:9D:69:2E:88:17:BA`

## Known limits

- **Google sign-in does not work inside apps** (Google blocks it in embedded browsers).
  Log in to OpenMarket with email or password.
- **Alerts cover crypto only** for now: stocks and commodities have no free live price source.
- Alert prices come from public exchange APIs and can be delayed or wrong. Nothing in this app
  is investment advice.

## Support

OM Desk is free. If you want to say thanks, the links are in the app (bell button → Settings)
and here:

- ☕ Ko-fi: **[ko-fi.com/d3p0ps](https://ko-fi.com/d3p0ps)**
- USDC / ETH (Ethereum, Base, Arbitrum): `0x5733EA6beE299Fa536627B9597bB566EA36dD8E7`

Always double-check an address after pasting it.

## Build it yourself

Requirements: Node.js ≥ 22, JDK 21, Android SDK (platform 36).

```bash
npm install
npm test              # unit tests (TypeScript)
npm run apk           # debug APK → android/app/build/outputs/apk/debug/
cd android && ./gradlew testDebugUnitTest   # unit tests (Java)
```

Signed release: `scripts/make-release-key.sh` once (the key stays in `~/.omdesk/`, keep a
backup), then `npm run release` → `dist/OM-Desk-<version>.apk` + `.sha256`.

Donation links live in `donate.json`; the build refuses malformed links or addresses.

## How it works

- `android/…/OmWebViewPlugin.java`: openmarket.xyz in a plain native WebView (the site
  forbids iframes) with no JavaScript bridge, plus the draggable bell button.
- `android/…/AlertService.java`: foreground service that checks the alerts; logic in
  `AlertLogic.java` (unit tested), storage in `AlertStore.java`.
- `web/`: the panel behind the bell button (alerts, settings), TypeScript + esbuild.
  Ticker search in `web/src/markets.ts`.

## Languages

- **OpenMarket itself** follows the language you pick on the site (Menu → Language):
  English, 中文 (简体), 한국어, 日本語, Русский, हिन्दी, Español, Türkçe.
- **The OM Desk panel** (alerts, settings) is in English for now. Translations are welcome:
  add a dictionary with the same keys to `web/src/i18n.ts` (the phone's language picks it,
  English is the fallback) and `android/app/src/main/res/values-xx/strings.xml` for
  notifications.

## License

[MIT](LICENSE)

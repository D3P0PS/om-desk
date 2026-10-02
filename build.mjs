// Build: the panel page (esbuild). OpenMarket itself is loaded live by the native view.
// `--tests` bundles tests/*.test.ts for node --test instead.

import { build } from 'esbuild';
import { cpSync, mkdirSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const pkg = JSON.parse(readFileSync('package.json', 'utf8'));
// "Buy me a coffee" links. Empty values hide the entry. Validated here so a typo in a
// payment address can never ship.
const donate = JSON.parse(readFileSync('donate.json', 'utf8'));
if (donate.kofi && !/^https:\/\/ko-fi\.com\/[A-Za-z0-9_]+$/.test(donate.kofi)) {
  throw new Error(`donate.json: kofi must look like https://ko-fi.com/<name>, got '${donate.kofi}'`);
}
for (const c of donate.crypto ?? []) {
  if (c.address && !/^0x[0-9a-fA-F]{40}$/.test(c.address)) {
    throw new Error(`donate.json: '${c.label}' is not an EVM address: '${c.address}'`);
  }
}
const donateOut = {
  kofi: donate.kofi || null,
  crypto: (donate.crypto ?? []).filter((c) => c.address),
};

const common = { bundle: true, format: 'esm', target: 'es2022', logLevel: 'warning' };

if (process.argv.includes('--tests')) {
  rmSync('.tmp-tests', { recursive: true, force: true });
  mkdirSync('.tmp-tests', { recursive: true });
  await build({
    ...common,
    platform: 'node',
    entryPoints: readdirSync('tests').filter((f) => f.endsWith('.test.ts')).map((f) => join('tests', f)),
    outdir: '.tmp-tests',
    outExtension: { '.js': '.mjs' },
  });
} else {
  rmSync('www', { recursive: true, force: true });
  mkdirSync('www', { recursive: true });
  await build({
    ...common,
    platform: 'browser',
    entryPoints: ['web/src/main.ts'],
    outfile: 'www/app.js',
    minify: true,
    sourcemap: false,
    define: { __VERSION__: JSON.stringify(pkg.version), __DONATE__: JSON.stringify(donateOut) },
  });
  cpSync('web/index.html', 'www/index.html');
  cpSync('web/styles.css', 'www/styles.css');

  console.log('www/ built');
}

#!/usr/bin/env node
// web-order-probe.mjs — lane L11: the brief's unverified web premise (§9:
// "that the web style prop is applied in object key order"), measured in the
// pinned headless Chromium on the two write paths a style object takes:
//   (1) React SSR — renderToString serialises the `style` object in key order;
//   (2) the client writer — React DOM sets `style[name] = value` per key in
//       iteration order; replayed here as the same per-key CSSOM writes.
// For each path it compares the OLD object order (`color` then `all`, what
// StyleBuilder emitted before wave 52) with the NEW one (`all` first) and
// reads the computed colour back. Writes web-order-probe.json; read-only
// otherwise.
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const reqWeb = createRequire(join(ROOT, 'runtimes', 'web', 'package.json'));
const reqTitan = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs'));
const React = reqWeb('react');
const { renderToString } = reqWeb('react-dom/server');
const puppeteer = reqTitan('puppeteer');

// The two orders under test (the span of css-cascade/all-prop-initial-color).
const OLD = { color: 'rgb(0, 128, 0)', all: 'initial' };
const NEW = { all: 'initial', color: 'rgb(0, 128, 0)' };
// SSR strings — the key order is visible in the emitted style attribute.
const ssr = (s) => renderToString(React.createElement('span', { id: 't', style: s }, 'x'));
const out = { ssrOld: ssr(OLD), ssrNew: ssr(NEW) };

const browser = await puppeteer.launch({ headless: 'new' });
try {
  const page = await browser.newPage();
  // (1) the SSR markup, parsed by Chromium; red parent so "inherit" would show.
  for (const [k, html] of [['ssrOldComputed', out.ssrOld], ['ssrNewComputed', out.ssrNew]]) {
    await page.setContent(`<p style="color:red">${html}</p>`);
    out[k] = await page.$eval('#t', (el) => getComputedStyle(el).color);
  }
  // (2) per-key CSSOM writes in object order — React DOM's client writer.
  for (const [k, obj] of [['clientOldComputed', OLD], ['clientNewComputed', NEW]]) {
    await page.setContent('<p style="color:red"><span id="t">x</span></p>');
    out[k] = await page.$eval('#t', (el, o) => {
      for (const [name, v] of Object.entries(o)) el.style[name] = v;  // key order
      return getComputedStyle(el).color;
    }, obj);
  }
  out.userAgent = await browser.userAgent();
} finally {
  await browser.close();
}
// The premise holds iff OLD order resets the green and NEW order keeps it.
out.premiseHolds = out.ssrNewComputed === 'rgb(0, 128, 0)' && out.clientNewComputed === 'rgb(0, 128, 0)' &&
  out.ssrOldComputed !== 'rgb(0, 128, 0)' && out.clientOldComputed !== 'rgb(0, 128, 0)';
writeFileSync(join(HERE, 'web-order-probe.json'), JSON.stringify(out, null, 1) + '\n');
console.log(out);
process.exit(out.premiseHolds ? 0 : 1);

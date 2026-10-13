#!/usr/bin/env node
//
// tools/titan/results/wave55-instruments/wl7a-marker-paint-probe.mjs — [W-L7a] step 1 (PLAN §2 L7, instruments.md §4 U5):
// the CDP read of the two keys L7 U5 adds to MARKER_PAINT_INITIAL, on counter-suffix's four RTL list items and their
// chains, under the bake's own page contract. STARTS CHROMIUM: an orchestrator window only (never a builder lane).
//
// Why: markerPaintLoss compares each key by its exact computed spelling (bidi-marker-paint.mjs). If Chromium does not
// expose `maskImage` / `mixBlendMode` on `getComputedStyle(…, '::marker')`, or spells their initials other than
// 'none' / 'normal', every marker chain reads "lost" and counter-suffix's markers DECLINE (29 → 23 components). This
// prints every value the bake would read, and exits 0 only when each is the initial spelling.
//
// The page load is wave54-rtl-marker-bake/marker-probe.mjs half B verbatim (same launch args, viewport, file:// URL,
// canvas-frame style, font + double-rAF settle); the chain walk is bidi-marker-paint.mjs inPageMarkerPaintChain's
// (the ::marker pseudo first, then the item and every ancestor below <body>). NOT EXECUTED by the lane (no Chromium on
// a builder lane): its first run is the window. Pair it with marker-probe.mjs run from the POST tree (the real code
// path with U5: `PASS baked — 2 roots, 10 runs`).
// Usage: node <tree>/tools/titan/results/wave55-instruments/wl7a-marker-paint-probe.mjs [css/css-counter-styles/counter-suffix.html]
//   (copy it into the tree under test first, or run it from the shared tree: it imports <its tree>/tools/titan/*.)
import puppeteer from 'puppeteer';                                        // the bake's own browser driver
import { join, resolve, dirname } from 'node:path';                      // repo-relative module paths
import { fileURLToPath } from 'node:url';                                 // this file's directory

const HERE = dirname(fileURLToPath(import.meta.url));                     // the lane dir
const T = resolve(HERE, '..', '..');                                      // tools/titan
const REPO = resolve(T, '..', '..');                                      // the checkout
const { BROWSER_LAUNCH_ARGS, canvasFrameCss, REF_RENDER_WIDTH, REF_RENDER_MIN_HEIGHT } = await import(join(T, 'capture-browser-ref.mjs'));
const { CANVAS_FRAME_STYLE_ID } = await import(join(T, 'post-load-extract.mjs'));
const rel = process.argv[2] ?? 'css/css-counter-styles/counter-suffix.html';
const WANT = { maskImage: 'none', mixBlendMode: 'normal' };               // L7 U5's two initial spellings

const browser = await puppeteer.launch({ headless: 'new', args: BROWSER_LAUNCH_ARGS });
let bad = 0;                                                              // values that are not the initial spelling
try {
  const page = await browser.newPage();
  await page.setViewport({ width: REF_RENDER_WIDTH, height: REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1 });
  await page.goto('file://' + encodeURI(join(REPO, 'tools', 'wpt', rel)), { waitUntil: 'load', timeout: 30_000 });
  await page.evaluate(({ id, css }) => { const s = document.createElement('style'); s.id = id; s.textContent = css; document.head.appendChild(s); },
    { id: CANVAS_FRAME_STYLE_ID, css: await canvasFrameCss() });
  await page.evaluate(() => document.fonts.ready.then(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
  // Every <li>: its ::marker, then the item and each ancestor below <body> — each key's computed value (null = absent).
  const chains = await page.evaluate((keys) => [...document.getElementsByTagName('li')].map((li) => {
    const read = (cs, tag) => ({ tag, ...Object.fromEntries(keys.map((k) => [k, cs[k] ?? null])) });
    const chain = [read(getComputedStyle(li, '::marker'), '::marker')];
    for (let n = li; n && n !== document.body; n = n.parentElement) chain.push(read(getComputedStyle(n), n.tagName.toLowerCase()));
    return { text: li.textContent.trim(), dir: getComputedStyle(li).direction, chain };
  }), Object.keys(WANT));
  for (const c of chains) {
    for (const e of c.chain) {
      const off = Object.entries(WANT).filter(([k, v]) => e[k] !== v);   // exact spelling, as markerPaintLoss compares
      bad += off.length;
      console.log(`${off.length ? 'FAIL' : 'ok  '} li "${c.text}" (${c.dir}) <${e.tag}> ${Object.keys(WANT).map((k) => `${k}=${JSON.stringify(e[k])}`).join(' ')}`);
    }
  }
  console.log(`items ${chains.length} (rtl ${chains.filter((c) => c.dir === 'rtl').length}) · chain entries ${chains.reduce((n, c) => n + c.chain.length, 0)}`);
} finally { await browser.close(); }
console.log(bad ? `WL7A MARKER PAINT: ${bad} value(s) not the initial spelling — U5 does NOT land` : 'WL7A MARKER PAINT: every value none / normal');
process.exit(bad ? 1 : 0);

#!/usr/bin/env node
// tools/titan/results/wave52-instrument-and-calibration/refreeze-detail.mjs
//
// wave-52 L12-B follow-up to refreeze-ab.mjs. The full A/B showed that a
// byte-exact re-render of the frozen corpus is impossible on this host: 994 of
// 1435 refs re-render with 1–45 glyph-antialias pixels changed under the OLD
// sheet itself (environmental drift since the freeze — same Chrome build). So
// the only honest "did the contract move this ref" test is SAME-SESSION: render
// the OLD sheet and the NEW contract back to back and compare pixels. This
// script does that for every ref whose new render differs from its old-arm
// render in refreeze-ab.json, saves BOTH arms (runs/wave52-calib/refs-ab/
// {old,new}/…) and reports the changed-pixel count, bbox and the colours that
// appeared / disappeared — the PNG replay evidence for each moved ref.
// Usage: node …/refreeze-detail.mjs   (reads refreeze-ab.json, writes refreeze-detail.json)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';
import { PNG } from 'pngjs';

// Paths, the shipped module, and the A/B record this follows up.
const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const cbr = await import(path.join(TITAN, 'capture-browser-ref.mjs'));
const { fixtureStem } = await import(path.join(TITAN, 'safe-name.mjs'));
const ab = JSON.parse(fs.readFileSync(path.join(HERE, 'refreeze-ab.json'), 'utf8'));
const moved = ab.rows.filter((r) => r.oldArmSha && r.newSha !== r.oldArmSha).map((r) => r.test);
const OUT = path.join(TITAN, 'runs', 'wave52-calib', 'refs-ab');

// The OLD sheet, exactly as refreeze-ab.mjs rebuilds it (B10 hunk reversed).
const NEW_SHEET = await cbr.canvasFrameCss();
const OLD_SHEET = NEW_SHEET.replace(':where(html) { background: #FFFFFF; }\n      :where(html, body) { margin: 0; padding: 0; }',
  ':where(html, body) { margin: 0; padding: 0; background: #FFFFFF; }');
if (OLD_SHEET === NEW_SHEET) { console.error('refreeze-detail: B10 literal not found'); process.exit(2); }

// Old-sheet render — the pre-wave-52 path (see refreeze-ab.mjs renderOld).
async function renderOld(page, t) {
  await page.goto('file://' + encodeURI(await cbr.resolveRefPath(t)), { waitUntil: 'load', timeout: 30_000 });
  await page.addStyleTag({ content: OLD_SHEET });
  await page.setViewport({ width: cbr.REF_RENDER_WIDTH, height: cbr.REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  const h = await page.evaluate((f) => Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight ?? 0, f), cbr.REF_RENDER_MIN_HEIGHT);
  await page.setViewport({ width: cbr.REF_RENDER_WIDTH, height: h, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  await page.evaluate(() => document.fonts.ready.then(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
  return cbr.padPngBuffer(await page.screenshot({ type: 'png' }), cbr.CANVAS_PAD_PX, cbr.CANVAS_BG);
}

// Pixel comparison: count, bbox, and the top colours gained / lost.
function compare(a, b) {
  if (a.width !== b.width || a.height !== b.height) return { dims: [`${a.width}x${a.height}`, `${b.width}x${b.height}`] };
  let n = 0, x0 = 1e9, y0 = 1e9, x1 = -1, y1 = -1;
  const gained = new Map(), lost = new Map();
  for (let y = 0; y < a.height; y++) for (let x = 0; x < a.width; x++) {
    const i = (y * a.width + x) * 4;
    if (a.data.readUInt32BE(i) === b.data.readUInt32BE(i)) continue;
    n++; x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
    const ka = [...a.data.slice(i, i + 3)].join(','), kb = [...b.data.slice(i, i + 3)].join(',');
    lost.set(ka, (lost.get(ka) ?? 0) + 1); gained.set(kb, (gained.get(kb) ?? 0) + 1);
  }
  const top = (m) => [...m.entries()].sort((p, q) => q[1] - p[1]).slice(0, 3).map(([c, k]) => `rgb(${c})×${k}`);
  return { changedPx: n, bbox: n ? [x0, y0, x1, y1] : null, oldColours: top(lost), newColours: top(gained), size: `${a.width}x${a.height}` };
}

const browser = await puppeteer.launch({ headless: 'new', args: cbr.BROWSER_LAUNCH_ARGS, protocolTimeout: 300_000 });
const page = await browser.newPage();
page.on('pageerror', () => {});
const rows = [];
for (const t of moved) {
  // Same session, back to back: old sheet, then the shipped new path.
  const oldPng = await renderOld(page, t);
  const { png: newPng, uaBodyMargin } = await cbr.renderRefPng(page, t);
  const rel = path.join(t.split('/')[1], `${fixtureStem(t)}.png`);
  for (const [arm, buf] of [['old', oldPng], ['new', newPng]]) {
    fs.mkdirSync(path.dirname(path.join(OUT, arm, rel)), { recursive: true });
    fs.writeFileSync(path.join(OUT, arm, rel), buf);
  }
  const row = { test: t, uaBodyMargin, ...compare(PNG.sync.read(oldPng), PNG.sync.read(newPng)) };
  rows.push(row);
  console.log(`${t.padEnd(78)} ${uaBodyMargin ? 'UA ' : '   '} ${row.changedPx ?? JSON.stringify(row.dims)} px  bbox ${JSON.stringify(row.bbox)}  old ${row.oldColours?.join(' ')}  →  new ${row.newColours?.join(' ')}`);
}
await browser.close();
fs.writeFileSync(path.join(HERE, 'refreeze-detail.json'), JSON.stringify({ _lane: 'wave52 L12-B same-session sheet A/B', moved: rows.length, rows }, null, 1) + '\n');

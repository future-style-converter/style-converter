#!/usr/bin/env node
// tools/titan/results/wave52-counters-and-lists/png-replay.mjs — lane L6's
// PNG replay (plan §0 rule iii: "the predicted post-fix picture … laid over
// the frozen ref, with the residual described in pixels") for the WEB half
// of the lane: T3 (the baked marker on the range-limited additive styles)
// and T7 (the cssom `-invalid` markers the author-rule bake now stamps).
//
// The capture browser itself (puppeteer's Chrome for Testing, the SAME
// launch flags and the SAME canvas contract as capture-browser-ref.mjs's
// renderRefPng) renders four pages per test, each framed exactly like a ref:
//   ref     — the WPT reference HTML (calibration: how far today's canvas
//             contract sits from the FROZEN ref PNG the gate scores against);
//   native  — the TEST HTML as authored (Chrome's own ::marker — the
//             picture the web runtime painted before T3 where the shape is
//             a predefined style);
//   string  — every applicable <li> takes the `<string>` list-style-type
//             (the plan's literal T3 wording, kept as the counterfactual);
//   plan    — every applicable <li> takes what the runtime NOW emits:
//             bakedMarkerPlan (imported from the runtime source itself) —
//             the inline box for inside + childless items, else `<string>`.
// "Applicable" and the marker strings come from the VERBATIM wave51-fix
// per-test IR (li components in document order: meta.markerText, the
// effective ListStyleType / ListStylePosition up the slot chain), except
// the cssom trio, whose markers are the T7 bake's NEW stamps (census.json).
// Scripts and @counter-style rules are stripped from string/plan pages (the
// wire carries neither). Ink bands = runs of rows with a pixel darker than
// 128 on all channels; ssim.js is the library default — NOT the scorer's
// number (no frame fit, no vetoes): read it as a trend.
//
// Usage: node tools/titan/results/wave52-counters-and-lists/png-replay.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import puppeteer from 'puppeteer';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';
import {
  BROWSER_LAUNCH_ARGS, canvasFrameCss, UA_BODY_CSS, uaBodyMarginFor, padPngBuffer,
  CANVAS_PAD_PX, CANVAS_BG, REF_RENDER_WIDTH, REF_RENDER_MIN_HEIGHT,
} from '../../capture-browser-ref.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
// The runtime's own rule (Node ≥ 22.18 strips the erasable TS types).
const { bakedMarkerPlan } = await import(path.join(ROOT, 'runtimes/web/src/engine/lists/ListStyleTypeApplier.ts'));
const WPT = path.join(ROOT, 'tools/wpt/css');
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const FROZEN = path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins');
const OUT = path.join(HERE, 'replay');
const TMP = process.env.L6_REPLAY_TMP ?? path.join(OUT, '.html');
fs.mkdirSync(TMP, { recursive: true });

/** Shape-1 carriers (census T3) + the cssom trio (T7; `bake` = new stamps). */
const CASES = [
  { rel: 'css-counter-styles/armenian/css3-counter-styles-008' },
  { rel: 'css-counter-styles/armenian/css3-counter-styles-006' },
  { rel: 'css-counter-styles/armenian/css3-counter-styles-007' },
  { rel: 'css-counter-styles/armenian/css3-counter-styles-009' },
  { rel: 'css-counter-styles/counter-suffix' },
  { rel: 'css-lists/content-property/marker-text-matches-armenian' },
  { rel: 'css-lists/content-property/marker-text-matches-georgian' },
  { rel: 'css-counter-styles/cssom/cssom-pad-setter-invalid', bake: ['001.', '002.', '003.'] },
  { rel: 'css-counter-styles/cssom/cssom-prefix-suffix-setter-invalid', bake: ['(A)', '(B)', '(C)'] },
  { rel: 'css-counter-styles/cssom/cssom-negative-setter-invalid', bake: ['(3).', '(2).', '(1).'] },
];

/** The li components of a per-test IR doc, in document order, with what
 *  the runtime sees: baked marker, inherited type / position, children. */
function irItems(rel, bake) {
  const [sec, ...rest] = rel.split('/');
  const doc = JSON.parse(fs.readFileSync(path.join(RUN, sec, 'per-test-ir', `wpt__${sec}__${rest.join('__')}.json`), 'utf8'));
  const byId = new Map(doc.components.map((c) => [c.id, c]));
  const up = (c, type) => { for (let n = c; n; n = n.slot?.parent ? byId.get(n.slot.parent) : null) { const p = n.properties?.find((x) => x.type === type); if (p) return String(p.data); } return undefined; };
  const kids = new Set(doc.components.map((c) => c.slot?.parent).filter(Boolean));
  return doc.components.filter((c) => (c.meta?.sourceTag ?? '').toLowerCase() === 'li').map((c, i) => ({
    marker: bake ? bake[i] : c.meta?.markerText, type: up(c, 'ListStyleType'), position: up(c, 'ListStylePosition'), hasChildren: kids.has(c.id),
  }));
}

/** Rewrite the n-th source <li> per `form(item)` → null | {listStyleType, inlineMarkerText?}. */
function variantHtml(src, items, form) {
  let i = 0;
  return src.replace(/<script\b[\s\S]*?<\/script>/gi, '').replace(/@counter-style\s+[^{]+\{[^}]*\}/g, '')
    .replace(/<li\b([^>]*)>/gi, (m, attrs) => {
      const it = items[i++]; const f = it && form(it);
      if (!f) return m;
      const css = `list-style-type: ${f.listStyleType.replace(/'/g, "\\'")}`;
      const open = /style\s*=\s*"/i.test(attrs) ? `<li${attrs.replace(/style\s*=\s*"/i, `style="${css}; `)}>` : `<li${attrs} style='${css}'>`;
      return f.inlineMarkerText === undefined ? open : `${open}<span style="unicode-bidi:isolate">${f.inlineMarkerText}</span>`;
    });
}

/** renderRefPng's executable steps, verbatim in order, for an arbitrary file. */
async function render(page, absHtml, testAbs, refAbs) {
  await page.goto('file://' + encodeURI(absHtml), { waitUntil: 'load', timeout: 30_000 });
  await page.addStyleTag({ content: await canvasFrameCss() });
  if (uaBodyMarginFor(testAbs, refAbs)) await page.addStyleTag({ content: UA_BODY_CSS });
  await page.setViewport({ width: REF_RENDER_WIDTH, height: REF_RENDER_MIN_HEIGHT, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  const h = await page.evaluate((floor) => Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight ?? 0, floor), REF_RENDER_MIN_HEIGHT);
  await page.setViewport({ width: REF_RENDER_WIDTH, height: h, deviceScaleFactor: 1 });
  await page.evaluate(() => new Promise((r) => setTimeout(r, 50)));
  await page.evaluate(() => document.fonts.ready.then(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)))));
  return padPngBuffer(await page.screenshot({ type: 'png' }), CANVAS_PAD_PX, CANVAS_BG);
}

/** Ink bands: [top, bottom, xMin, xMax] per run of inked rows. */
function bands(png) {
  const out = []; let cur = null;
  for (let y = 0; y < png.height; y++) {
    let x0 = -1, x1 = -1;
    for (let x = 0; x < png.width; x++) {
      const i = (y * png.width + x) * 4;
      if (png.data[i] < 128 && png.data[i + 1] < 128 && png.data[i + 2] < 128) { if (x0 < 0) x0 = x; x1 = x; }
    }
    if (x0 >= 0) { if (!cur) cur = [y, y, x0, x1]; else { cur[1] = y; cur[2] = Math.min(cur[2], x0); cur[3] = Math.max(cur[3], x1); } }
    else if (cur) { out.push(cur); cur = null; }
  }
  if (cur) out.push(cur);
  return out;
}

/** ssim.js on the common top-left crop (the frozen refs are 390 × ≥600). */
function ssimVs(a, b) {
  const w = Math.min(a.width, b.width), h = Math.min(a.height, b.height);
  const crop = (p) => { const d = Buffer.alloc(w * h * 4); for (let y = 0; y < h; y++) p.data.copy(d, y * w * 4, y * p.width * 4, y * p.width * 4 + w * 4); return { data: new Uint8ClampedArray(d), width: w, height: h }; };
  return ssim(crop(a), crop(b)).mssim;
}

const browser = await puppeteer.launch({ headless: 'new', args: BROWSER_LAUNCH_ARGS, protocolTimeout: 120_000 });
const report = { browser: await browser.version(), frozenRefDir: path.relative(ROOT, FROZEN), cases: [] };
try {
  const page = await browser.newPage();
  for (const c of CASES) {
    const testAbs = path.join(WPT, c.rel + '.html');
    const src = fs.readFileSync(testAbs, 'utf8');
    const refHref = (src.match(/rel=['"]?match['"]?\s+href=['"]?([^'"\s>]+)/i) || [])[1];
    const refAbs = path.join(path.dirname(testAbs), refHref);
    const [sec, ...rest] = c.rel.split('/');
    const stem = rest.join('__');
    const frozen = PNG.sync.read(fs.readFileSync(path.join(FROZEN, sec, stem + '.png')));
    const items = irItems(c.rel, c.bake);
    const planOf = (it) => bakedMarkerPlan(it.type, it.position, it.marker, it.hasChildren);
    const stringOf = (it) => { const p = planOf(it); return p && { listStyleType: p.inlineMarkerText === undefined ? p.listStyleType : `"${p.inlineMarkerText.replace(/"/g, '\\"')}"` }; };
    const row = { test: c.rel, items: items.map((it) => ({ ...it, plan: planOf(it) ?? null })), frozen: { bands: bands(frozen) }, renders: {} };
    const pages = [['ref', refAbs], ['native', testAbs]];
    for (const [kind, form] of [['string', stringOf], ['plan', planOf]]) {
      const f = path.join(TMP, `${stem}.${kind}.html`);
      fs.writeFileSync(f, variantHtml(src, items, form));
      pages.push([kind, f]);
    }
    for (const [kind, file] of pages) {
      const png = PNG.sync.read(await render(page, file, testAbs, refAbs));
      fs.writeFileSync(path.join(OUT, `${stem}.${kind}.png`), PNG.sync.write(png));
      row.renders[kind] = { size: [png.width, png.height], bands: bands(png), ssimVsFrozen: Number(ssimVs(png, frozen).toFixed(4)) };
    }
    report.cases.push(row);
  }
} finally { await browser.close(); }
fs.writeFileSync(path.join(OUT, 'replay.json'), JSON.stringify(report, null, 1) + '\n');
for (const r of report.cases) {
  console.log(`\n${r.test}  forms ${r.items.map((it) => (it.plan ? (it.plan.inlineMarkerText !== undefined ? 'inline' : 'string') : '-')).join(' ')}`);
  console.log(`  frozen ref bands ${r.frozen.bands.length}: ${JSON.stringify(r.frozen.bands)}`);
  for (const [k, v] of Object.entries(r.renders)) console.log(`  ${k.padEnd(6)} ssim ${v.ssimVsFrozen}  bands ${v.bands.length}: ${JSON.stringify(v.bands)}`);
}

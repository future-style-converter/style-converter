#!/usr/bin/env node
// wave-52 lane L10 — PNG replay: the predicted post-fix picture of each L10
// target, built from FROZEN wave51-fix captures that already show the right
// geometry, scored against the frozen ref with the pipeline's own SSIM
// (ssim.js `fast` on RGBA, same-size pairs — the call inject-wpt-block.mjs
// makes; same helper shape as wave52-composed-canvas/png-replay.mjs).
// Read-only; writes png-replay.json beside itself.
//   7(b)  background-clip-content-box-002 ios ← the ANDROID capture (both 50%
//         items resolved, green x 16–115 exactly as the ref — measured).
//   M3    flex-gap-decorations-008 android ← the iOS capture (six items at
//         60-px pitch overflowing, five rules — iOS P 0.9996).
//   M2    027 ios/android ← the WEB capture shifted +200 (L2's M1; web's
//         relative geometry is pixel-exact), frame columns from the ref (L2's
//         Fix A ICB clip), and "One" — off-canvas at x −132 in the web PNG —
//         either left blank (LOWER bound) or taken from the ref (UPPER bound).
//   7(a′) 045/046 natives ← the native capture with every pixel where the
//         native and web captures disagree AND either is rule-coloured
//         (pure, or a rule/background blend) replaced by web's — i.e. the
//         snapped bands (web P 1.0000 on both).
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const req = createRequire(path.join(ROOT, 'package.json'));
const { PNG } = req('pngjs');
const { ssim } = req('ssim.js');
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const manifests = new Map();

/** Frozen ref, the three captures and the recorded cells for one test. */
function load(section, test) {
  if (!manifests.has(section)) manifests.set(section, JSON.parse(fs.readFileSync(path.join(RUN, section, 'manifest.json'), 'utf8')));
  const res = manifests.get(section).wpt.results[test];
  const stem = test.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__');
  const cap = Object.fromEntries(Object.entries(DIRS).map(([p, d]) =>
    [p, PNG.sync.read(fs.readFileSync(path.join(RUN, section, d, `wpt__${stem}.png`)))]));
  const ref = PNG.sync.read(fs.readFileSync(path.join(ROOT, res.browserRef.path)));
  const cell = (p) => { const d = res.browserRef.diffs[`${p}-ref`]; return `${d.wptPass ? 'P' : 'f'} ${d.ssim}`; };
  return { cap, ref, cell };
}
/** The pipeline's score on a same-size pair (4 decimals, like the manifest). */
function score(a, b) {
  if (a.width !== b.width || a.height !== b.height) return null;
  const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
  return +ssim(img(a), img(b), { ssim: 'fast' }).mssim.toFixed(4);
}
const clone = (p) => { const o = new PNG({ width: p.width, height: p.height }); p.data.copy(o.data); return o; };
const at = (p, x, y) => (y * p.width + x) * 4;
/** Count pixels of `p` within ±tol of rgb. */
function count(p, [r, g, b], tol = 24) {
  let n = 0;
  for (let i = 0; i < p.data.length; i += 4)
    if (Math.abs(p.data[i] - r) <= tol && Math.abs(p.data[i + 1] - g) <= tol && Math.abs(p.data[i + 2] - b) <= tol) n++;
  return n;
}
/** Shift a whole image by dx (vacated pixels white), then frame columns from the ref (Fix A). */
function shiftAndClip(cap, ref, dx) {
  const o = new PNG({ width: cap.width, height: cap.height }); o.data.fill(255);
  for (let y = 0; y < cap.height; y++) for (let x = 0; x < cap.width; x++) {
    const sx = x - dx; if (sx < 0 || sx >= cap.width) continue;
    cap.data.copy(o.data, at(o, x, y), at(cap, sx, y), at(cap, sx, y) + 4);
  }
  for (let y = 0; y < o.height; y++) for (let x = 0; x < o.width; x++)
    if (x < 16 || x >= o.width - 16) ref.data.copy(o.data, at(o, x, y), at(ref, x, y), at(ref, x, y) + 4);
  return o;
}
/** Copy a rectangle [x0,x1]×[y0,y1] from `src` into a clone of `dst`. */
function paste(dst, src, x0, x1, y0, y1) {
  const o = clone(dst);
  for (let y = y0; y <= y1; y++) for (let x = x0; x <= x1; x++) src.data.copy(o.data, at(o, x, y), at(src, x, y), at(src, x, y) + 4);
  return o;
}
/** Rule-ish: saturated warm colour (red / gold rules and their blends with white). */
const ruleish = (d, i) => d[i] >= 200 && d[i + 2] <= 200 && (d[i] - d[i + 2]) >= 55;
/** Snap emulation: where native and web disagree and either is rule-ish, take web's pixel. */
function snapFromWeb(nat, web) {
  const o = clone(nat); let n = 0;
  for (let i = 0; i < o.data.length; i += 4) {
    const diff = Math.abs(nat.data[i] - web.data[i]) + Math.abs(nat.data[i + 1] - web.data[i + 1]) + Math.abs(nat.data[i + 2] - web.data[i + 2]);
    if (diff > 6 && (ruleish(nat.data, i) || ruleish(web.data, i))) { web.data.copy(o.data, i, i, i + 4); n++; }
  }
  return { img: o, replaced: n };
}

const rows = [];
const add = (test, platform, recorded, oracle, predicted, extra = {}) => rows.push({ test, platform, recorded, oracle, predicted, ...extra });

{ // 7(b)
  const t = 'css/css-backgrounds/background-clip-content-box-002.html';
  const { cap, ref, cell } = load('css-backgrounds', t);
  add(t, 'ios', cell('ios'), 'android capture', score(cap.android, ref),
      { redPxRecorded: count(cap.ios, [255, 0, 0]), redPxPredicted: count(cap.android, [255, 0, 0]) });
}
{ // M3
  const t = 'css/css-gaps/flex/flex-gap-decorations-008.html';
  const { cap, ref, cell } = load('css-gaps', t);
  add(t, 'android', cell('android'), 'ios capture', score(cap.ios, ref));
}
{ // M2 (+ L2's M1 and Fix A)
  const t = 'css/css-gaps/flex/flex-gap-decorations-027.html';
  const { cap, ref, cell } = load('css-gaps', t);
  const shifted = shiftAndClip(cap.web, ref, 200);
  // Everything the WEB capture painted off-canvas (page x < 0 before the
  // +200 shift → x 68…199 after it: "One", rule 1, "Two", rule 2, part of
  // "Three") is absent from the oracle, not wrong in it — the UPPER bound
  // takes that band from the ref; the LOWER bound leaves it blank.
  const upper = paste(shifted, ref, 68, 199, 0, ref.height - 1);
  for (const p of ['ios', 'android'])
    add(t, p, cell(p), 'web +200, Fix A frame; off-canvas band x68-199 blank | from ref', score(shifted, ref), { upper: score(upper, ref) });
}
for (const n of ['045', '046']) { // 7(a′)
  const t = `css/css-gaps/flex/flex-gap-decorations-${n}.html`;
  const { cap, ref, cell } = load('css-gaps', t);
  for (const p of ['ios', 'android']) {
    const { img, replaced } = snapFromWeb(cap[p], cap.web);
    add(t, p, cell(p), 'native with rule pixels from web (snapped bands)', score(img, ref), { replacedPx: replaced, asIs: score(cap[p], ref) });
  }
}
// 7(a′) mover census: every decorated container (census class D), both
// natives — how many rule-ish pixels the snap oracle would change, and the
// score it predicts. A large count means a STRUCTURAL difference (006's
// transpose, 027, the 033–035 instrument cells), not a snap: flagged, not predicted.
const census = JSON.parse(fs.readFileSync(path.join(HERE, 'flex-nowrap-gaps.census.json'), 'utf8'));
const movers = [];
for (const d of census.classes.D) {
  const t = d.verdict.key; if (t.includes('-045.') || t.includes('-046.')) continue;
  const { cap, ref, cell } = load('css-gaps', t);
  for (const p of ['ios', 'android']) {
    const { img, replaced } = snapFromWeb(cap[p], cap.web);
    if (!replaced) continue;
    const asIs = score(cap[p], ref), pred = score(img, ref);
    movers.push({ test: t, platform: p, recorded: cell(p), replacedPx: replaced, asIs, predicted: pred,
                  kind: replaced > 600 ? 'structural (not a snap)' : 'snap candidate' });
  }
}
fs.writeFileSync(path.join(HERE, 'png-replay.json'), JSON.stringify({ targets: rows, snapMovers: movers }, null, 1) + '\n');
for (const m of movers) console.log('mover', JSON.stringify(m));
for (const r of rows) console.log(JSON.stringify(r));

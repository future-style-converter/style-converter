#!/usr/bin/env node
// wave-52 lane L2 — PNG replay: the predicted post-fix picture of every L2
// mechanism, simulated on the FROZEN wave51-fix captures and scored against
// the frozen refs with the pipeline's own SSIM (ssim.js `fast` on RGBA at the
// ref frame — inject-wpt-block.mjs; same-size pairs, so the honest-frame fold
// is the identity). Read-only; writes png-replay.json beside itself.
//   A  Fix A: frame columns x<16 / x>=W-16 replaced by the REF's (the pixel
//      effect of the inline-axis ICB clip) — the 274 same-size overrun rows of
//      wave52-plan/overrun-ssim-prediction.json, re-derived, not re-quoted.
//   M1 027 web: the whole capture shifted +200 px (body margin-left), then
//      cropped at the ICB edge like Fix A. 006 ×3: the black square +8 px.
//   T1 ellipse-006/7/8 natives: the green ellipse rows moved +16 px.
//   T2 propagation-shadow natives: the text line moved −16 px.
//   T3 align-items-007 natives: red ink repainted green (the RC1 box on top).
//   T6 006 natives: the inset abspos <p>'s prose moved +16 px (UA margin), with M1's +8 square.
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
const manifests = new Map();                                          // section -> parsed manifest

/** The frozen ref + capture PNGs and the recorded cell for one test/platform. */
function load(section, test, platform) {
  if (!manifests.has(section)) manifests.set(section, JSON.parse(fs.readFileSync(path.join(RUN, section, 'manifest.json'), 'utf8')));
  const res = manifests.get(section).wpt.results[test];
  const stem = test.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__');
  const cap = PNG.sync.read(fs.readFileSync(path.join(RUN, section, DIRS[platform], `wpt__${stem}.png`)));
  const ref = PNG.sync.read(fs.readFileSync(path.join(ROOT, res.browserRef.path)));
  return { cap, ref, cell: res.browserRef.diffs[`${platform}-ref`] };
}
/** The pipeline's score on a same-size pair (4 decimals, like the manifest). */
function score(a, b) {
  if (a.width !== b.width || a.height !== b.height) return null;     // the fold is not modelled here
  const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
  return +ssim(img(a), img(b), { ssim: 'fast' }).mssim.toFixed(4);
}
const clone = (p) => { const o = new PNG({ width: p.width, height: p.height }); p.data.copy(o.data); return o; };
const px = (p, x, y) => (y * p.width + x) * 4;
/** Fix A: frame columns take the ref's pixels (the clip's pixel effect). */
function clipFrame(cap, ref) {
  const o = clone(cap);
  for (let y = 0; y < o.height; y++) for (let x = 0; x < o.width; x++)
    if (x < 16 || x >= o.width - 16) ref.data.copy(o.data, px(o, x, y), px(ref, x, y), px(ref, x, y) + 4);
  return o;
}
/** Move every pixel matching `sel` inside rows [y0,y1] by (dx,dy); vacated pixels take `fill`. */
function moveInk(cap, sel, dx, dy, y0 = 0, y1 = cap.height - 1, fill = [255, 255, 255, 255]) {
  const o = clone(cap); const moved = [];
  for (let y = y0; y <= y1; y++) for (let x = 0; x < cap.width; x++) {
    const i = px(cap, x, y); const c = [...cap.data.subarray(i, i + 4)];
    if (sel(c)) { moved.push([x + dx, y + dy, c]); o.data.set(fill, i); }
  }
  for (const [x, y, c] of moved) if (x >= 0 && y >= 0 && x < o.width && y < o.height) o.data.set(c, px(o, x, y));
  return o;
}
const green = ([r, g, b]) => g > 100 && r < 60 && b < 60;            // nnm-ink.mjs colour classes
const red = ([r, g, b]) => r > 200 && g < 80 && b < 80;
const ink = ([r, g, b]) => r < 248 || g < 248 || b < 248;           // anything but the white canvas
const dark = ([r, g, b]) => r < 60 && g < 60 && b < 60;
const out = { method: 'ssim.js fast on RGBA, same-size pairs; see header', fixA: {}, cells: [] };

// ── A: the 274 same-size Fix A rows ──
const pred = JSON.parse(fs.readFileSync(path.join(ROOT, 'tools/titan/results/wave52-plan/overrun-ssim-prediction.json'), 'utf8')).rows;
let asIsMismatch = 0; let predMismatch = 0; let rise = 0; let pDrop = 0; let below = []; const flips = []; const drops = [];
for (const r of pred) {
  const { cap, ref, cell } = load(r.section, r.test, r.platform);
  const now = score(cap, ref); const after = score(clipFrame(cap, ref), ref);
  if (now !== cell.ssim) asIsMismatch++;                              // calibration: must be 0
  if (after !== r.predicted) predMismatch++;                          // re-derivation of the brief
  if (cell.wptPass && after > now) rise++;
  if (cell.wptPass && after < now) { pDrop++; drops.push(`${r.test} ${r.platform} ${now}→${after}`); }
  if (cell.wptPass && after < 0.95) below.push(`${r.test} ${r.platform}`);
  if (!cell.wptPass && now < 0.95 && after >= 0.95) flips.push(`${r.test} ${r.platform} ${now}→${after}`);
}
out.fixA = { rows: pred.length, asIsMismatch, predMismatchVsBrief: predMismatch, passingRise: rise, passingDrop: pDrop, passingDropCells: drops, passingBelow095: below, scorerFlipCandidates: flips };

// ── the per-mechanism replays (one row each) ──
const row = (mech, section, test, platform, f) => {
  const { cap, ref, cell } = load(section, test, platform);
  out.cells.push({ mech, test, platform, recorded: cell.ssim, pass: cell.wptPass, replay: score(f(cap, ref), ref) });
};
for (const p of ['web', 'ios', 'android'])
  row('M1', 'css-gaps', 'css/css-gaps/flex/flex-gap-decorations-027.html', p, (c, r) => clipFrame(moveInk(c, ink, 200, 0, 16, c.height - 17), r));
for (const p of ['web', 'ios', 'android'])
  row('M1', 'CSS2', 'css/CSS2/css21-errata/s-11-1-1b-006.html', p, (c) => moveInk(c, dark, 8, 0, p === 'web' ? 53 : 37, 200));
// T6 + M1 on 006 natives: the inset abspos <p>'s prose +16 (its UA margin), then the square (+8, −8): the body
// margin's +8 x, and −8 y because T2 now collapses through root 1 (skeptic §4 — the fix pass adds the y half).
for (const p of ['ios', 'android'])
  row('T6+M1', 'CSS2', 'css/CSS2/css21-errata/s-11-1-1b-006.html', p, (c) => moveInk(moveInk(c, ink, 0, 16, 15, 36), ink, 8, -8, 55, 90));
for (const t of ['006', '007', '008']) for (const p of ['ios', 'android'])
  row('T1', 'css-masking', `css/css-masking/clip-path/clip-path-ellipse-${t}.html`, p, (c) => moveInk(c, green, 0, 16));
for (const p of ['ios', 'android'])
  row('T2', 'css-text-decor', 'css/css-text-decor/text-decoration-propagation-shadow.html', p, (c) => moveInk(c, ink, 0, -16, 40, 80));
for (const p of ['ios', 'android'])
  row('T3', 'css-flexbox', 'css/css-flexbox/align-items-007.html', p, (c) => {
    const o = clone(c); for (let i = 0; i < o.data.length; i += 4) if (red([o.data[i], o.data[i + 1], o.data[i + 2]])) o.data.set([0, 128, 0, 255], i);
    return o;
  });
// T3 fix pass: every scored T3 carrier the lift rule LEAVES in wave-51 order (declared z, later step-8 root,
// content-bearing) and abspos-011 (lifted green over a transparent root + a non-overlapping green: no pixel
// moves) predict the recorded capture itself — the replay is the identity, calibrated against the recorded ssim.
const t3 = JSON.parse(fs.readFileSync(path.join(HERE, 'composed-canvas.census.json'), 'utf8')).t3;
for (const r of t3) for (const p of ['ios', 'android']) if (r[p] !== '-' && r.why !== 'lifted' || /abspos-011/.test(r.test))
  row(`T3-${r.why.replace(/ -?\d+$/, '')}`, r.test.split('/')[1], r.test, p, (c) => c);
fs.writeFileSync(path.join(HERE, 'png-replay.json'), JSON.stringify(out, null, 1) + '\n');
console.log(JSON.stringify(out.fixA, null, 1));
for (const c of out.cells) console.log(`${c.mech} ${c.test.replace(/^css\//, '')} ${c.platform} ${c.pass ? 'P' : 'f'} ${c.recorded} → replay ${c.replay}`);

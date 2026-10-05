#!/usr/bin/env node
// Wave 52 · lane L8 (vertical-wedges) — the PNG REPLAY owed by PLAN.md §0
// rule (iii): the predicted post-fix native picture of each ch-units-vrl
// cell, laid over the frozen ref and scored with the scorer's own SSIM
// recipe (inject-wpt-block.mjs diffWebVsRef: fitToRefFrame + ssim.js 'fast'
// + the overflow-ink normalisation), residual described in pixels.
//
// Model (no device was run): the post-fix natives keep their own capture
// everywhere EXCEPT the three coloured boxes, which are erased and repainted
// at the geometry the lane's mechanisms predict, stacked from the native's
// own first-box top exactly as the boxes stack in the ref (table, then the
// two divs, no gaps):
//   • M-A: a face-less 5ch = 63 (Inter '0' 12.6 px at 20 px).
//   • M-B: under vertical-* + OWN text-orientation: upright, 5ch = 120.
//   • M-C: the upright `00000` plans one column of five line boxes; the
//     native one-glyph line box is LB: 24 (Chromium), 24.2 (iOS, MEASURED
//     by the lane's Catalyst raster pin: 120×121), 25 (if the calibrated
//     WPT line-height reaches Compose's glyph Text — not measured).
//   • NOT M-E (col widths are not harvested this wave): a td with no width
//     stays 6 px wide on both natives, as wave51-fix captured it.
// Sanity row: the CURRENT native capture re-scored with this recipe must
// reproduce the wave51-fix manifest number, or the replay is not evidence.
// Output: png-replay.json beside this file.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { ssim as computeSsim } from 'ssim.js';
import { fitToRefFrame, countOverflowInk, computeColorFailed, computeCoverageRatioFailed } from '../../inject-wpt-block.mjs';
import { computeHistogramKL, computeLabDeltaE, computeSemanticPresence } from '../../../visual/compare-screenshots-metrics.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../../..');
const sec = path.join(root, 'tools/titan/runs/wave51-fix/sections/css-writing-modes');
// WPT_REF carries comment lines; the SHA is the one 40-hex line.
const sha = fs.readFileSync(path.join(root, 'tools/titan/WPT_REF'), 'utf8').split('\n').map((l) => l.trim()).find((l) => /^[0-9a-f]{40}$/.test(l));
const refDir = path.join(root, 'tools/wpt/refs', sha, 'white-black-ink-font-lh-imgpad-htmlpins/css-writing-modes');
const manifest = JSON.parse(fs.readFileSync(path.join(sec, 'manifest.json'), 'utf8'));

const load = (p) => PNG.sync.read(fs.readFileSync(p));
/** The scorer's SSIM (diffWebVsRef's ssim half, verbatim arithmetic). */
function score(cap, ref) {
  const fit = fitToRefFrame(cap, ref.width, ref.height);
  const r = computeSsim({ data: new Uint8ClampedArray(fit.data), width: ref.width, height: ref.height },
    { data: new Uint8ClampedArray(ref.data), width: ref.width, height: ref.height }, { ssim: 'fast' });
  const area = ref.width * ref.height;
  return +((r.mssim * area) / (area + countOverflowInk(cap, ref.width, ref.height))).toFixed(4);
}
/** White-pad to (W, H) — diffWebVsRef's padToCanvas, for the colour metrics. */
function pad(img, W, H) {
  const out = new PNG({ width: W, height: H });
  out.data.fill(255);
  for (let y = 0; y < img.height; y++) img.data.copy(out.data, y * W * 4, y * img.width * 4, (y + 1) * img.width * 4);
  return out;
}
/** The two vetoes that fire on these cells today (manifest: iOS cF, cov):
 *  the colour-mass veto (any channel histogram KL > 0.1 AND mean Lab ΔE ≥
 *  2.3 — inject-wpt-block's isColorDivergent + computeColorFailed) and the
 *  coverage-ratio veto. novelInk / degenerate are not replicated (novelPx is
 *  0 on every native ch-units cell today). */
function vetoes(cap, ref) {
  const W = Math.max(cap.width, ref.width), H = Math.max(cap.height, ref.height);
  const A = pad(cap, W, H), B = pad(ref, W, H);
  const kl = computeHistogramKL(A, B);
  const divergent = [kl.r, kl.g, kl.b].some((v) => typeof v === 'number' && v > 0.1);
  const colorFailed = computeColorFailed(divergent, computeLabDeltaE(A, B, 4));
  const coverageRatioFailed = computeCoverageRatioFailed(computeSemanticPresence(A, B, { r: 255, g: 255, b: 255 }));
  return { colorFailed, coverageRatioFailed };
}
/** Score + vetoes + the predicted verdict (ssim ≥ 0.95 and no veto). */
function judge(cap, ref) {
  const ssim = score(cap, ref);
  const v = vetoes(cap, ref);
  return { ssim, ...v, pass: ssim >= 0.95 && !v.colorFailed && !v.coverageRatioFailed };
}
// The three box colours (±40 per channel).
const COLOURS = { green: [0, 128, 0], blue: [0, 0, 255], orange: [255, 165, 0] };
const isColour = (img, i, [r, g, b]) => Math.abs(img.data[i] - r) < 40 && Math.abs(img.data[i + 1] - g) < 40 && Math.abs(img.data[i + 2] - b) < 40;
/** Bounding box of one colour, or null. */
function bbox(img, rgb) {
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = 0; y < img.height; y++) for (let x = 0; x < img.width; x++) {
    if (!isColour(img, (y * img.width + x) * 4, rgb)) continue;
    x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y);
  }
  return x1 < 0 ? null : { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}
/** Paint a solid rect. */
function fill(img, { x, y, w, h }, [r, g, b]) {
  for (let yy = y; yy < Math.min(img.height, y + h); yy++) for (let xx = x; xx < Math.min(img.width, x + w); xx++) {
    const i = (yy * img.width + xx) * 4; img.data[i] = r; img.data[i + 1] = g; img.data[i + 2] = b; img.data[i + 3] = 255;
  }
}

// Predicted post-fix box sizes per test, in document order (the IR's
// table, then div 1, then div 2), as [colour, w, h]; LB = upright line box.
const predicted = (t, LB) => ({
  // tr/tbody carry the modes; td text-orientation is NOT inherited on the
  // natives → td 5ch = 63; div 1 upright blue / div 2 horizontal orange.
  '001': [['green', 6, 63], ['blue', 120, Math.round(5 * LB)], ['orange', 63, 63]],
  '002': [['green', 6, 63], ['blue', 120, Math.round(5 * LB)], ['orange', 63, 63]],
  // td own upright → 120 tall, col width not harvested → 6 wide.
  '003': [['green', 6, 120], ['blue', 120, Math.round(5 * LB)], ['orange', 63, 63]],
  '004': [['green', 6, 120], ['blue', 120, Math.round(5 * LB)], ['orange', 63, 63]],
  // sideways tr/tbody → td 63; blue horizontal; orange upright.
  '005': [['green', 6, 63], ['blue', 63, 63], ['orange', 120, Math.round(5 * LB)]],
  '006': [['green', 6, 63], ['blue', 63, 63], ['orange', 120, Math.round(5 * LB)]],
  '007': [['green', 6, 120], ['blue', 63, 63], ['orange', 120, Math.round(5 * LB)]],
  '008': [['green', 6, 120], ['blue', 63, 63], ['orange', 120, Math.round(5 * LB)]],
})[t];

// The SAME tests with M-E (col/colgroup Width harvested as the column's
// width — css-tables-3 §2.1/§3.2): the green td takes the col's 5ch, which
// the col resolves with ITS OWN orientation (upright 120 in 003/004,
// sideways 63 in 007/008). BUILT this wave (TableBoxTree column harvest +
// seam-3 / seam-4); its picture is written as replay-<stem>-<plat>-withME-LB24.2.png
// (24.2 = the iOS-measured line box) beside the without-M-E LB-24 one.
const withME = (t, LB) => ({
  '003': [['green', 120, 120], ['blue', 120, Math.round(5 * LB)], ['orange', 63, 63]],
  '004': [['green', 120, 120], ['blue', 120, Math.round(5 * LB)], ['orange', 63, 63]],
  '007': [['green', 63, 120], ['blue', 63, 63], ['orange', 120, Math.round(5 * LB)]],
  '008': [['green', 63, 120], ['blue', 63, 63], ['orange', 120, Math.round(5 * LB)]],
})[t];

const out = { run: 'wave51-fix', refs: path.relative(root, refDir), model: 'see header', cells: [] };
for (const t of ['001', '002', '003', '004', '005', '006', '007', '008']) {
  const stem = `ch-units-vrl-${t}`;
  const ref = load(path.join(refDir, `${stem}.png`));
  const diffs = manifest.wpt.results[`css/css-writing-modes/${stem}.html`].browserRef.diffs;
  const row = { test: stem, ref: Object.fromEntries(Object.entries(COLOURS).map(([k, c]) => [k, bbox(ref, c)])) };
  // The web capture: the picture the natives are predicted to approach.
  const web = load(path.join(sec, 'screenshots', `wpt__css-writing-modes__${stem}.png`));
  row.web = { manifest: diffs['web-ref']?.ssim, rescored: score(web, ref), boxes: Object.fromEntries(Object.entries(COLOURS).map(([k, c]) => [k, bbox(web, c)])) };
  for (const [plat, dir] of [['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    const cap = load(path.join(sec, dir, `wpt__css-writing-modes__${stem}.png`));
    const boxes = Object.fromEntries(Object.entries(COLOURS).map(([k, c]) => [k, bbox(cap, c)]));
    const md = diffs[`${plat}-ref`] || {};
    const cell = { manifest: { ssim: md.ssim, wptPass: md.wptPass, colorFailed: md.colorFailed, coverageRatioFailed: md.coverageRatioFailed },
      rescored: judge(cap, ref), now: boxes, predicted: {} };
    // Stack from the native's own first-box top (the green table).
    const top = Math.min(...Object.values(boxes).filter(Boolean).map((b) => b.y));
    /** Erase today's boxes and paint a predicted stack; returns the image. */
    const replay = (stack) => {
      const img = new PNG({ width: cap.width, height: cap.height });
      cap.data.copy(img.data);
      // Erase today's boxes to the white canvas…
      for (const b of Object.values(boxes)) if (b) fill(img, b, [255, 255, 255]);
      // …and paint the predicted ones, stacked with no gap at x = 16.
      let y = top;
      for (const [k, w, h] of stack) { fill(img, { x: 16, y, w, h }, COLOURS[k]); y += h; }
      return img;
    };
    // 24 = Chromium's rounded line box; 24.2 = the iOS Catalyst MEASURED
    // one-glyph Inter-20 line box (UprightChOrangeRasterTests: 120×121);
    // 25 = the calibrated 1.25 × 20 WPT line-height, if Compose's glyph
    // Text takes it (not measured — no device run this wave).
    for (const LB of [24, 24.2, 25]) {
      const img = replay(predicted(t, LB));
      cell.predicted[`LB${LB}`] = judge(img, ref);
      if (LB === 24) fs.writeFileSync(path.join(here, `replay-${stem}-${plat}.png`), PNG.sync.write(img));
      // The M-E pricing row (only the four col/colgroup tests).
      if (withME(t, LB)) {
        const meImg = replay(withME(t, LB));
        cell.predicted[`withME_LB${LB}`] = judge(meImg, ref);
        // Skeptic nit 4: commit the WITH-M-E picture too (the shipped geometry).
        if (LB === 24.2) fs.writeFileSync(path.join(here, `replay-${stem}-${plat}-withME-LB24.2.png`), PNG.sync.write(meImg));
      }
    }
    row[plat] = cell;
  }
  out.cells.push(row);
  const f = (j) => `${j.ssim}${j.pass ? 'P' : 'f'}${j.colorFailed ? 'cF' : ''}${j.coverageRatioFailed ? 'cov' : ''}`;
  const me = (c) => (c.predicted.withME_LB24 ? ` [+M-E ${f(c.predicted.withME_LB24)}/${f(c.predicted['withME_LB24.2'])}/${f(c.predicted.withME_LB25)}]` : '');
  const p = (c) => `${f(c.predicted.LB24)}/${f(c.predicted['LB24.2'])}/${f(c.predicted.LB25)}`;
  const m = (c) => `${c.manifest.ssim}${c.manifest.wptPass ? 'P' : 'f'}${c.manifest.colorFailed ? 'cF' : ''}${c.manifest.coverageRatioFailed ? 'cov' : ''}`;
  console.log(`${stem}: web ${row.web.rescored} | ios now ${f(row.ios.rescored)} (manifest ${m(row.ios)}) → ${p(row.ios)}${me(row.ios)} | android now ${f(row.android.rescored)} (manifest ${m(row.android)}) → ${p(row.android)}${me(row.android)}`);
}
// ── M-G: forms/input-range-zero-inline-size ────────────────────────────
// Post-fix model: the UA slider atom is gone (UAWidgetZeroBoxRasterTests:
// 0 accent pixels on Catalyst) and nothing else moves — the flex parents'
// 1 px green inline-start borders stay. Replay: every pixel of the native
// capture BELOW the text (rows 60+) that is neither white-ish nor green (the
// accent fill, grey track, thumb and their anti-aliasing) is painted white,
// then scored with the same SSIM + veto replay.
{
  const stem = 'forms/input-range-zero-inline-size';
  const file = 'wpt__css-writing-modes__forms__input-range-zero-inline-size.png';
  const ref = load(path.join(refDir, 'forms__input-range-zero-inline-size.png'));
  const diffs = manifest.wpt.results[`css/css-writing-modes/${stem}.html`].browserRef.diffs;
  const row = { test: stem, ref: { green: bbox(ref, COLOURS.green) } };
  for (const [plat, dir] of [['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    const cap = load(path.join(sec, dir, file));
    const img = new PNG({ width: cap.width, height: cap.height });
    cap.data.copy(img.data);
    let erased = 0;
    // Rows 60+ only: the `<p>` text (ref and captures: rows 35–51) is ink
    // the ref carries too; the sliders sit at rows 70–105 on both natives.
    for (let i = 60 * img.width * 4; i < img.data.length; i += 4) {
      const [r, g, b] = [img.data[i], img.data[i + 1], img.data[i + 2]];
      const white = r > 247 && g > 247 && b > 247;
      const green = r < 60 && g > 90 && g < 170 && b < 60;
      if (!white && !green) { img.data[i] = img.data[i + 1] = img.data[i + 2] = 255; erased++; }
    }
    const md = diffs[`${plat}-ref`] || {};
    row[plat] = { manifest: { ssim: md.ssim, wptPass: md.wptPass, colorFailed: md.colorFailed }, rescored: judge(cap, ref),
      erasedPx: erased, predicted: judge(img, ref), green: bbox(cap, COLOURS.green) };
    fs.writeFileSync(path.join(here, `replay-input-range-zero-inline-size-${plat}.png`), PNG.sync.write(img));
  }
  out.cells.push(row);
  const f2 = (j) => `${j.ssim}${j.pass ? 'P' : 'f'}${j.colorFailed ? 'cF' : ''}${j.coverageRatioFailed ? 'cov' : ''}`;
  console.log(`${stem}: ios now ${f2(row.ios.rescored)} (manifest ${row.ios.manifest.ssim}) → ${f2(row.ios.predicted)} [erased ${row.ios.erasedPx}] | android now ${f2(row.android.rescored)} (manifest ${row.android.manifest.ssim}) → ${f2(row.android.predicted)} [erased ${row.android.erasedPx}]`);
}
fs.writeFileSync(path.join(here, 'png-replay.json'), JSON.stringify(out, null, 2) + '\n');

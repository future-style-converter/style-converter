// S7 replay of lane B7 patch 1 (ua-hr-separator-box) on
// css-images/gradient/gradient-hue-direction.
//
// The capture is 390x894 against a 390x600 ref: three rule-less <hr> boxes
// ship as 100x100 white squares instead of 2px rules (+98px each). The row-ink
// profiles locate the three displacement bands exactly (see _note.md), so the
// repair is simulated by a piecewise row shift of 0/98/196/294 onto the ref
// frame, scored with the campaign's own scorer.
//
// TWO variants are scored, because they answer different questions:
//   A "shift-only"  — what B7's measurement actually did: displacement removed,
//                     but the hr's own 2px rule still absent (the capture never
//                     painted one).
//   B "shift+rule"  — the shift PLUS the ref's own hr rule rows stamped in,
//                     i.e. what the UA bake is supposed to produce.
import { validate, cell, scorePng, readPng, writePng, SCRATCH } from './replay-lib.mjs';
import { PNG } from 'pngjs';
import { join } from 'node:path';

const SEC = 'css-images', T = 'css/css-images/gradient/gradient-hue-direction.html';
// ref-row → shift into the capture. Derived from the run-length row-ink
// profiles of both PNGs (documented in _note.md).
const BANDS = [[0, 178, 0], [178, 310, 98], [310, 442, 196], [442, 600, 294]];
const HR_ROWS = [[188, 190], [320, 322], [452, 454]];   // the ref's own 2px rules

function shiftFrame(cap, W, H) {
  const out = new PNG({ width: W, height: H });
  out.data.fill(0xFF);
  for (const [y0, y1, dy] of BANDS)
    for (let y = y0; y < y1 && y < H; y++) {
      const sy = y + dy;
      if (sy >= cap.height) continue;
      const s = sy * cap.width * 4;
      cap.data.copy(out.data, y * W * 4, s, s + Math.min(cap.width, W) * 4);
    }
  return out;
}
function stampHr(img, ref) {
  for (const [y0, y1] of HR_ROWS)
    for (let y = y0; y < y1; y++) {
      const s = y * ref.width * 4;
      ref.data.copy(img.data, y * img.width * 4, s, s + ref.width * 4);
    }
  return img;
}
function deltaStats(a, b, thr) {
  let over = 0, max = 0, any = 0;
  for (let y = 0; y < b.height; y++) for (let x = 0; x < b.width; x++) {
    const i = (y*a.width+x)*4, j = (y*b.width+x)*4;
    const m = Math.max(Math.abs(a.data[i]-b.data[j]), Math.abs(a.data[i+1]-b.data[j+1]), Math.abs(a.data[i+2]-b.data[j+2]));
    if (m > 0) any++; if (m > max) max = m; if (m > thr) over++;
  }
  return { pixelsOverThr: over, anyDiff: any, maxDelta: max };
}
/** Same stats restricted to rows that are NOT in an hr band — B7's "over every band". */
function deltaStatsBandsOnly(a, b, thr) {
  const skip = new Set(); for (const [y0,y1] of HR_ROWS) for (let y=y0;y<y1;y++) skip.add(y);
  let over = 0, max = 0, any = 0;
  for (let y = 0; y < b.height; y++) { if (skip.has(y)) continue;
    for (let x = 0; x < b.width; x++) {
      const i = (y*a.width+x)*4, j = (y*b.width+x)*4;
      const m = Math.max(Math.abs(a.data[i]-b.data[j]), Math.abs(a.data[i+1]-b.data[j+1]), Math.abs(a.data[i+2]-b.data[j+2]));
      if (m > 0) any++; if (m > max) max = m; if (m > thr) over++;
    } }
  return { pixelsOverThr: over, anyDiff: any, maxDelta: max };
}
const pick = d => d && ({ ssim: d.ssim, wptPass: d.wptPass, colorFailed: d.colorFailed,
  coverageRatioFailed: d.coverageRatioFailed, presenceFailed: d.presenceFailed,
  novelInkFailed: d.novelInkFailed, pixelMismatchedPct: d.pixelMismatchedPct,
  frame: d.frame, wptFuzzyMatch: d.wptFuzzyMatch });

const out = {};
for (const plat of ['web', 'ios', 'android']) {
  const v = await validate(SEC, T, plat);
  const c = cell(SEC, T, plat);
  const cap = await readPng(c.capPath), ref = await readPng(c.refPath);
  const A = shiftFrame(cap, ref.width, ref.height);
  const B = stampHr(shiftFrame(cap, ref.width, ref.height), ref);
  const pa = await writePng(A, join(SCRATCH, `B7a-${plat}-shift.png`));
  const pb = await writePng(B, join(SCRATCH, `B7a-${plat}-shift-rule.png`));
  out[plat] = {
    harnessExactMatch: v.ok, harnessMismatches: v.diffs ?? v.reason,
    before: pick(c.recorded),
    shiftOnly: { delta8_fullFrame: deltaStats(A, ref, 8), delta8_bandsOnly: deltaStatsBandsOnly(A, ref, 8),
                 score: pick(await scorePng(pa, c.testKey, c.refPath, c.fuzzy)) },
    shiftPlusRule: { delta8_fullFrame: deltaStats(B, ref, 8),
                 score: pick(await scorePng(pb, c.testKey, c.refPath, c.fuzzy)) },
  };
}
console.log(JSON.stringify(out, null, 1));

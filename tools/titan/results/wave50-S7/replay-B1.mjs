// S7 replay of lane B1's predicted flips on css-values/angle-units-001.
// Method: validate the harness on each untouched capture (must equal the
// wave49-final manifest field-for-field), then repaint every pure-red pixel to
// the ref's own rgb(0,128,0) — the paint a two-stop `linear-gradient(green,
// green)` produces — and re-score with the SAME scorer entry point.
// Also scores the sibling tests 002-005 as they stand (B1's capability argument).
import { validate, cell, scorePng, readPng, writePng, repaint, clonePng, SCRATCH, inkBBox } from './replay-lib.mjs';
import { join } from 'node:path';

const SEC = 'css-values';
const out = { validate: {}, before: {}, after: {}, siblings: {} };
const T = 'css/css-values/angle-units-001.html';

for (const plat of ['web', 'ios', 'android']) {
  const v = await validate(SEC, T, plat);
  out.validate[plat] = { exactMatch: v.ok, mismatches: v.diffs ?? v.reason };
  const c = cell(SEC, T, plat);
  const rec = c.recorded;
  out.before[plat] = pick(rec);
  // Repaint pure red → the ref's green.
  const png = await readPng(c.capPath);
  const copy = clonePng(png);
  const n = repaint(copy, [255, 0, 0], [0, 128, 0]);
  const p = await writePng(copy, join(SCRATCH, `B1-${plat}-green.png`));
  const after = await scorePng(p, c.testKey, c.refPath, c.fuzzy);
  out.after[plat] = { repaintedPx: n, ...pick(after) };
}

for (const n of ['002', '003', '004', '005']) {
  const t = `css/css-values/angle-units-${n}.html`;
  out.siblings[n] = {};
  for (const plat of ['web', 'ios', 'android']) {
    const v = await validate(SEC, t, plat);
    out.siblings[n][plat] = { exactMatch: v.ok, mismatches: v.diffs ?? v.reason, ...pick(v.rec) };
  }
}

function pick(d) {
  if (!d) return null;
  return {
    ssim: d.ssim, wptPass: d.wptPass, colorFailed: d.colorFailed,
    presenceFailed: d.presenceFailed, coverageRatioFailed: d.coverageRatioFailed,
    novelInkFailed: d.novelInkFailed, novelPx: d.novelInk?.novelPx,
    colorComposite: d.colorComposite, labDeltaEMean: d.labDeltaE?.mean,
    pixelMismatchedPct: d.pixelMismatchedPct, wptFuzzyMatch: d.wptFuzzyMatch,
  };
}
console.log(JSON.stringify(out, null, 1));

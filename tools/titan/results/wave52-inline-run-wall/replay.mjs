#!/usr/bin/env node
// Wave 52 · lane L9 — PNG replay, step 2: score captures against the frozen
// refs with the campaign's OWN SSIM recipe (inject-wpt-block.mjs
// diffWebVsRef's SSIM half: ssim.js `fast` on the REF's 390×600 frame, the
// capture fitted by fitToRefFrame, out-of-frame ink folded in by
// countOverflowInk — both IMPORTED from the scorer, never re-implemented).
// First it re-scores the wave51-fix captures and asserts the manifest's
// numbers are reproduced EXACTLY (the recipe check the composed-ref scoring
// memory demands); only then does it score replay_sim.py's predictions.
//
// Usage: node tools/titan/results/wave52-inline-run-wall/replay.mjs
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import { ssim as computeSsim } from 'ssim.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
// The scorer's own frame helpers (exported pure functions, IS_CLI-gated).
const { fitToRefFrame, countOverflowInk } = await import(path.join(ROOT, 'tools/titan/inject-wpt-block.mjs'));
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const REFS = path.join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins');
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };

// diffWebVsRef's SSIM half, verbatim in arithmetic (inject-wpt-block.mjs).
function score(capPath, refPath) {
  const a = PNG.sync.read(fs.readFileSync(capPath));
  const b = PNG.sync.read(fs.readFileSync(refPath));
  const refArea = b.width * b.height;
  const aFit = fitToRefFrame(a, b.width, b.height);
  const r = computeSsim(
    { data: new Uint8ClampedArray(aFit.data), width: b.width, height: b.height },
    { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height },
    { ssim: 'fast' });
  const overflowInkPx = countOverflowInk(a, b.width, b.height);
  return +((r.mssim * refArea) / (refArea + overflowInkPx)).toFixed(4);
}

// [section, per-test stem, manifest key, ref file] for every replayed test.
const TESTS = [
  ['css-text', 'wpt__css-text__hanging-punctuation__hanging-punctuation-inline-001',
    'css/css-text/hanging-punctuation/hanging-punctuation-inline-001.html',
    'css-text/hanging-punctuation__hanging-punctuation-inline-001.png', 'hanging-punctuation-inline-001'],
  ['css-overflow', 'wpt__css-overflow__line-clamp__block-ellipsis-025',
    'css/css-overflow/line-clamp/block-ellipsis-025.html',
    'css-overflow/line-clamp__block-ellipsis-025.png', 'block-ellipsis-025'],
  ['css-overflow', 'wpt__css-overflow__line-clamp__block-ellipsis-032.tentative',
    'css/css-overflow/line-clamp/block-ellipsis-032.tentative.html',
    'css-overflow/line-clamp__block-ellipsis-032.tentative.png', 'block-ellipsis-032'],
  // Fix pass (skeptic M1): the 030 prediction and the 028 pre-fix counterfactual.
  ['css-overflow', 'wpt__css-overflow__line-clamp__block-ellipsis-030',
    'css/css-overflow/line-clamp/block-ellipsis-030.html',
    'css-overflow/line-clamp__block-ellipsis-030.png', 'block-ellipsis-030'],
  ['css-overflow', 'wpt__css-overflow__line-clamp__block-ellipsis-028',
    'css/css-overflow/line-clamp/block-ellipsis-028.html',
    'css-overflow/line-clamp__block-ellipsis-028.png', 'block-ellipsis-028'],
];

const out = { recipeCheck: [], predictions: [], counterfactuals: [] };
let mismatch = 0;
for (const [sec, stem, key, ref, short] of TESTS) {
  const m = JSON.parse(fs.readFileSync(path.join(RUN, sec, 'manifest.json'), 'utf8'));
  const diffs = m.wpt.results[key].browserRef.diffs;
  for (const [plat, dir] of Object.entries(DIRS)) {
    const got = score(path.join(RUN, sec, dir, `${stem}.png`), path.join(REFS, ref));
    const want = diffs[`${plat}-ref`].ssim;
    if (got !== want) mismatch++;
    out.recipeCheck.push({ test: key, platform: plat, manifest: want, rescored: got, exact: got === want });
    // A prediction exists for this cell → score it against the same ref.
    const pred = path.join(HERE, 'replay', `${short}.${plat}.predicted.png`);
    if (fs.existsSync(pred)) {
      out.predictions.push({ test: key, platform: plat, today: want, predicted: score(pred, path.join(REFS, ref)),
        file: path.relative(ROOT, pred) });
    }
    // Counterfactuals (`<short>.<plat>.counterfactual-*.png`): what a REJECTED
    // variant would have painted — scored the same way, reported apart.
    for (const f of fs.readdirSync(path.join(HERE, 'replay'))) {
      if (!f.startsWith(`${short}.${plat}.counterfactual-`)) continue;
      const p = path.join(HERE, 'replay', f);
      out.counterfactuals.push({ test: key, platform: plat, today: want, scored: score(p, path.join(REFS, ref)),
        file: path.relative(ROOT, p) });
    }
  }
}
fs.writeFileSync(path.join(HERE, 'replay.json'), JSON.stringify(out, null, 1));
console.log(JSON.stringify(out, null, 1));
// A recipe that does not reproduce the manifest cannot be trusted to predict.
process.exit(mismatch ? 1 : 0);

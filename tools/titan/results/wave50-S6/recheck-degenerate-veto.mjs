// S6 INDEPENDENT recomputation of the wave-50 degenerate veto's confusion
// matrix over lane B12's own 100-cell hand-verdict sample.
//
// Written from scratch for the skeptic pass: it reads ONLY the cell list in
// handverdicts.json (test, platform, capture/ref paths and the hand verdict)
// and re-derives the veto decision from the PNG pair itself. It deliberately
// does NOT reuse anything under tools/titan/results/wave50-B12/ beyond those
// labels, so agreement is evidence rather than an echo.
//
// Run from the repo root:  node tools/titan/results/wave50-S6/recheck-degenerate-veto.mjs
// Result at tree 747b28e4 + the wave-50 tree:
//   100 cells · 35 hand-wrong · 14 fires · 13 TP / 1 FP / 22 FN / 64 TN
//   precision 0.9286 · recall 0.3714   (reproduces lane B12's 92.9 % / 37.1 %)
import fs from 'node:fs';
// pngjs comes from the workspace install at the repo root (npm workspaces).
import { PNG } from 'pngjs';
const probe = await import('../../degenerate-veto-probe.mjs');
const hv = JSON.parse(fs.readFileSync('tools/titan/results/wave50-B12/handverdicts.json', 'utf8'));
const load = p => PNG.sync.read(fs.readFileSync(p));
let tp = 0, fp = 0, fn = 0, tn = 0; const fired = [];
for (const c of hv.cells) {
  const d = probe.computeDegenerate(load(c.capture), load(c.ref));
  const f = probe.degenerateVetoFailed(d) === true;
  const wrong = c.verdict !== 'correct';
  if (f && wrong) { tp++; fired.push(c.n); }
  else if (f && !wrong) { fp++; fired.push(c.n + '(FP)'); }
  else if (!f && wrong) fn++;
  else tn++;
}
console.log(JSON.stringify({
  sample: hv.cells.length,
  handWrong: hv.cells.filter(c => c.verdict !== 'correct').length,
  vetoFired: tp + fp, truePositives: tp, falsePositives: fp,
  falseNegatives: fn, trueNegatives: tn,
  precision: +(tp / Math.max(tp + fp, 1)).toFixed(4),
  recall: +(tp / Math.max(tp + fn, 1)).toFixed(4),
  firedCells: fired,
}, null, 1));

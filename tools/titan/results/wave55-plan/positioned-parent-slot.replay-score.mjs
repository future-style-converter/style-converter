#!/usr/bin/env node
// tools/titan/results/wave55-plan/positioned-parent-slot.replay-score.mjs
// Scores the positioned-parent-slot.replay.py PNGs with the gate's own composed metric (inject-wpt-block.mjs
// diffWebVsRef). The CALIBRATION rows score the real wave54-final captures and must reproduce cells.mjs
// (dir-style-02a android 0.9425, web 1; bidi-tab-001 android 0.9805; anchor-position-multicol-nested-001 android
// 0.9573, web 0.9742; dynamic-offset-rtl-001 android 0.9952) or the replay numbers mean nothing. Read-only.
// Each value is `ssim[/veto]`: the two exported CAL-RC6 vetoes are recomputed on the same diff (coverage = the ink-mass
// ratio > 2, colour = the colour-mass divergence) because wptPass is ssim ≥ 0.95 AND no veto — an SSIM above 0.95 with
// `/coverage` is still an f (anchor-position-multicol-nested-001 android at wave54-final). The presence veto is not
// exported and is not recomputed here.
// Usage: node tools/titan/results/wave55-plan/positioned-parent-slot.replay-score.mjs
import path from 'node:path';
import { diffWebVsRef, computeCoverageRatioFailed, computeColorFailed } from '../../inject-wpt-block.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const RUN = `${ROOT}/tools/titan/runs/wave54-final/sections`;
const REP = `${HERE}/positioned-parent-slot-replay`;
// [label, section, ref file, capture stem, replay arms]
// One scored value: ssim plus any veto that would make wptPass false.
const score = async (cap, ref) => {
  const d = await diffWebVsRef(cap, ref);
  const v = [computeCoverageRatioFailed(d.semanticPresence) && 'coverage', computeColorFailed(d.colorDivergent, d.labDeltaE) && 'colour'].filter(Boolean);
  const cov = d.semanticPresence ? ` ink ${d.semanticPresence.aCoveragePct}%/${d.semanticPresence.bCoveragePct}%` : '';
  return `${d.ssim}${v.length ? '/' + v.join('+') : ''}${cov}`;
};
const docs = [
  ['dir-style-02a', 'selectors', 'dir-style-02a.png', 'dir-style-02a', ['U1', 'U1U2', 'PLAIN']],
  ['bidi-tab-001', 'css-text', 'bidi__bidi-tab-001.png', 'bidi__bidi-tab-001', ['U1U2', 'PLAIN']],
  ['anchor-position-multicol-nested-001', 'css-anchor-position', 'anchor-position-multicol-nested-001.png',
    'anchor-position-multicol-nested-001', ['U1']],
  ['dynamic-offset-rtl-001', 'css-writing-modes', 'dynamic-offset-rtl-001.png', 'dynamic-offset-rtl-001', ['U2']],
  ['dynamic-offset-vrl-rtl-001', 'css-writing-modes', 'dynamic-offset-vrl-rtl-001.png', 'dynamic-offset-vrl-rtl-001', ['U2']],
  ['dynamic-offset-rtl-002', 'css-writing-modes', 'dynamic-offset-rtl-002.png', 'dynamic-offset-rtl-002', ['U2']],
  ['dynamic-offset-vrl-rtl-002', 'css-writing-modes', 'dynamic-offset-vrl-rtl-002.png', 'dynamic-offset-vrl-rtl-002', ['U2']],
];
for (const [label, sec, ref, stem, arms] of docs) {
  const R = `${REFS}/${sec}/${ref}`;
  const row = [label.padEnd(38)];
  for (const [plat, dir] of [['web', 'screenshots'], ['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    row.push(`${plat} ${await score(`${RUN}/${sec}/${dir}/wpt__${sec}__${stem}.png`, R)}`);
  }
  for (const arm of arms) row.push(`android-${arm} ${await score(`${REP}/${label}-android-${arm}.png`, R)}`);
  console.log(row.join(' | '));
}

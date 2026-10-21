#!/usr/bin/env node
// tools/titan/results/wave55-plan/cbb-android.replay-score.mjs — scores backdrop-filter-nested-border-radius-clip-3
// android with the gate's own composed metric (tools/titan/inject-wpt-block.mjs diffWebVsRef; the
// composed-ref-scoring recipe): the wave54-final capture (base, must print the gate's f 0.9574), the wave54-probe
// capture (U1 only, must print the probe's P 0.9668) and the U2 replay (cbb-android.replay.py). Read-only on the runs.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '../../../..');
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/filter-effects/backdrop-filter-nested-border-radius-clip-3.png`;
const cap = (run) => `${ROOT}/tools/titan/runs/${run}/sections/filter-effects/android-screenshots/wpt__filter-effects__backdrop-filter-nested-border-radius-clip-3.png`;
for (const [label, png, gate] of [
  ['base (wave54-final, neither unit)', cap('wave54-final'), 'f 0.9574'],
  ['U1 only (wave54-probe, 5b333c8a)', cap('wave54-probe'), 'P 0.9668'],
  ['U1+U2 replay (Android pixels re-laid)', `${HERE}/cbb-android-replay/android-clip-3.png`, '(prediction)'],
  // The planning fakes (cbb-android.fakes.py): wrong pictures the score alone would pass — the geometry gates them.
  ['F3 fake: requiredSize centring', `${HERE}/cbb-android-replay/fake-centered.png`, '(fake)'],
  ['F4 fake: #outer clip missing', `${HERE}/cbb-android-replay/fake-unclipped.png`, '(fake)'],
  ['F6 fake: band added twice', `${HERE}/cbb-android-replay/fake-double.png`, '(fake)'],
]) {
  const d = await diffWebVsRef(png, REF);
  console.log(`${label.padEnd(40)} gate ${gate.padEnd(12)} | ssim ${d.ssim} wptPass ${d.wptPass} pixelMismatchedPct ${d.pixelMismatchedPct}`);
}

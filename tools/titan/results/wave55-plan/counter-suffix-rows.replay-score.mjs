#!/usr/bin/env node
// tools/titan/results/wave55-plan/counter-suffix-rows.replay-score.mjs
// Scores the per-unit replay ARMS written by counter-suffix-rows.replay.py (counter-suffix-rows-replay/arms/) with the
// gate's own composed metric (inject-wpt-block.mjs diffWebVsRef — the scorer the manifests use for composed captures).
// CALIBRATION first: the real wave54-final captures of counter-suffix and cjk-decimal/css3-counter-styles-005 must
// reproduce cells.mjs (counter-suffix web 1 / ios 0.9873 / android 0.989; -005 web 1 / ios 0.993 / android 0.9759), or
// the replay numbers mean nothing. A replay is the unit's predicted pixels composed from pixels a platform ALREADY
// paints (Android's letter-then-period and tabular '1', the CJK zone moved by the 4-px gap) — never a device read.
// Usage: node tools/titan/results/wave55-plan/counter-suffix-rows.replay-score.mjs
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';

const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-counter-styles`;
const SEC = `${ROOT}/tools/titan/runs/wave54-final/sections/css-counter-styles`;
const ARMS = `${HERE}/counter-suffix-rows-replay/arms`;
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
const rows = [];
for (const [test, ref] of [['counter-suffix', 'counter-suffix'], ['cjk-decimal__css3-counter-styles-005', 'cjk-decimal__css3-counter-styles-005']]) {
  for (const [p, d] of Object.entries(DIRS)) rows.push([`wave54-final ${test} ${p} (capture)`, `${SEC}/${d}/wpt__css-counter-styles__${test}.png`, `${REFS}/${ref}.png`]);
}
const CS = `${REFS}/counter-suffix.png`;
const C5 = `${REFS}/cjk-decimal__css3-counter-styles-005.png`;
for (const [k, f, r] of [
  ['arm counter-suffix ios U1 (rows 3-4 order)', 'ios-U1', CS],
  ['arm counter-suffix ios U2o (rows 5-6 +4)', 'ios-U2o', CS],
  ['arm counter-suffix ios U3 (row 9 tabular 1)', 'ios-U3', CS],
  ['arm counter-suffix ios U1+U2o', 'ios-U1U2o', CS],
  ['arm counter-suffix ios U1+U2o+U3', 'ios-all', CS],
  ['arm counter-suffix android U2o (rows 5-6 +4)', 'android-U2o', CS],
  ['arm css3-counter-styles-005 ios U2i (item -4)', '005-ios-U2i', C5],
  ['arm css3-counter-styles-005 android U2i (item -4)', '005-android-U2i', C5],
]) rows.push([k, `${ARMS}/${f}.png`, r]);
for (const [k, p, r] of rows) {
  const d = await diffWebVsRef(p, r);
  console.log(k.padEnd(62), 'ssim', d.ssim);
}

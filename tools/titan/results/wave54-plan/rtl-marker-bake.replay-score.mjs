#!/usr/bin/env node
// tools/titan/results/wave54-plan/rtl-marker-bake.replay-score.mjs
// Scores the rtl-marker-bake.replay.py PNGs and, as the CALIBRATION rows, the six real counter-suffix captures of
// wave53-final and wave53-probe with the gate's own composed metric (inject-wpt-block.mjs diffWebVsRef — the scorer
// the manifests use for composed captures). The calibration rows must reproduce cells.mjs (wave53-final 0.9818 /
// 0.9802 / 0.9547; wave53-probe 1 / 0.9873 / 0.9793), or the replay numbers mean nothing.
// Usage: node tools/titan/results/wave54-plan/rtl-marker-bake.replay-score.mjs <dir written by the replay>
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const dir = process.argv[2];
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-counter-styles/counter-suffix.png`;
// One composed capture of counter-suffix in a run, by platform folder.
const cap = (run, d) => `${ROOT}/tools/titan/runs/${run}/sections/css-counter-styles/${d}/wpt__css-counter-styles__counter-suffix.png`;
const rows = [];
for (const run of ['wave53-final', 'wave53-probe']) {
  for (const [p, d] of [['web', 'screenshots'], ['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
    rows.push([`${run} ${p} (capture)`, cap(run, d)]);
  }
}
rows.push(['replay android P (from wave53-final)', `${dir}/android-P.png`]);
rows.push(["replay android M'+P device glyphs", `${dir}/android-Mp-dev.png`]);
rows.push(["replay android M'+P ref marker", `${dir}/android-Mp-ref.png`]);
for (const [k, p] of rows) {
  const d = await diffWebVsRef(p, REF);
  console.log(k.padEnd(40), 'ssim', d.ssim);
}

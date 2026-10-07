#!/usr/bin/env node
// Scores the replay PNGs (rtl-marker-bake.replay.py) and the three wave52-ship
// captures with the gate's own composed metric (inject-wpt-block.mjs
// diffWebVsRef — the scorer the manifests use for composed captures).
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const dir = process.argv[2];
const REF = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-counter-styles/counter-suffix.png`;
const cap = (d) => `${ROOT}/tools/titan/runs/wave52-ship/sections/css-counter-styles/${d}/wpt__css-counter-styles__counter-suffix.png`;
const rows = [['web (ship)', cap('screenshots')], ['ios (ship)', cap('ios-screenshots')], ['android (ship)', cap('android-screenshots')],
  ['web M', `${dir}/web-M.png`], ['ios M', `${dir}/ios-M.png`], ['android M', `${dir}/android-M.png`],
  ['android P', `${dir}/android-P.png`], ['android M+P', `${dir}/android-MP.png`]];
for (const [k, p] of rows) {
  const d = await diffWebVsRef(p, REF);
  console.log(k.padEnd(16), 'ssim', d.ssim, 'pixelPct', d.pixelMismatchedPct ?? d.pixelPct ?? '');
}
for (const stem of ['bidi__bidi-lines-002', 'bidi__bidi-lines-001']) {
  const ref = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text/${stem}.png`;
  const ship = `${ROOT}/tools/titan/runs/wave52-ship/sections/css-text/android-screenshots/wpt__css-text__${stem}.png`;
  for (const [k, p] of [[`android ship ${stem}`, ship], [`android P ${stem}`, `${dir}/android-P-${stem}.png`]]) {
    const d = await diffWebVsRef(p, ref);
    console.log(k.padEnd(40), 'ssim', d.ssim);
  }
}

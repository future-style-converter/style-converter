#!/usr/bin/env node
// tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.replay-score.mjs — scores the wave53-final -002 / -001
// captures (sanity: must reproduce the gate's numbers) and the W1 replay picture written by
// web-out-of-flow-hyphen-box.replay.py, with the gate's own composed metric (inject-wpt-block.mjs diffWebVsRef).
// Read-only on the run dirs.
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text`;
const cap = (d, t) => `${ROOT}/tools/titan/runs/wave53-final/sections/css-text/${d}/wpt__css-text__hyphens__${t}.png`;
const rows = [
  ['-002 web capture (gate: f 0.9411)', cap('screenshots', 'hyphens-out-of-flow-002'), 'hyphens__hyphens-out-of-flow-002'],
  ['-002 ios capture (gate: P 0.9971)', cap('ios-screenshots', 'hyphens-out-of-flow-002'), 'hyphens__hyphens-out-of-flow-002'],
  ['-002 android capture (gate: P 0.9943)', cap('android-screenshots', 'hyphens-out-of-flow-002'), 'hyphens__hyphens-out-of-flow-002'],
  ['-001 web capture (gate: P 1)', cap('screenshots', 'hyphens-out-of-flow-001'), 'hyphens__hyphens-out-of-flow-001'],
  ['-002 web W1 replay (boxes 4,5 := 7)', `${HERE}/web-out-of-flow-hyphen-box-replay/web-W1.png`, 'hyphens__hyphens-out-of-flow-002'],
  ['-002 web W1-exact (boxes 3-6 := 7)', `${HERE}/web-out-of-flow-hyphen-box-replay/web-W1-exact.png`, 'hyphens__hyphens-out-of-flow-002'],
  ['-001 web W1-exact, ungated (3,6 := 7)', `${HERE}/web-out-of-flow-hyphen-box-replay/web-001-W1-exact.png`, 'hyphens__hyphens-out-of-flow-001'],
];
for (const [k, p, ref] of rows) {
  const d = await diffWebVsRef(p, `${REFS}/${ref}.png`);
  console.log(k.padEnd(40), 'ssim', d.ssim, 'pass', d.wptPass ?? d.pass ?? '');
}

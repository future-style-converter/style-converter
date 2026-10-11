#!/usr/bin/env node
// tools/titan/results/wave55-plan/hyphen-ios-linebox.radius-score.mjs — scores hyphen-ios-linebox.radius-replay.py's
// pictures and the real iOS captures (sanity: = the gate's cells) with the gate metric (diffWebVsRef). Read-only.
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const TESTS = [...Array.from({ length: 8 }, (_, i) => ['css-text', `boundary-shaping__boundary-shaping-00${i + 1}`]),
  ['css-text-decor', 'text-decoration-dotted-001'], ['css-text-decor', 'text-decoration-dotted-002']];
const r4 = (x) => Math.round(x * 1e4) / 1e4;
for (const [sec, stem] of TESTS) {
  const ref = `${REFS}/${sec}/${stem}.png`;
  const cap = (d) => `${ROOT}/tools/titan/runs/wave54-final/sections/${sec}/${d}/wpt__${sec}__${stem}.png`;
  const row = [`${sec}/${stem}`];
  for (const [lab, d] of [['web', 'screenshots'], ['android', 'android-screenshots'], ['ios', 'ios-screenshots']]) row.push(`${lab} ${r4((await diffWebVsRef(cap(d), ref)).ssim)}`);
  row.push(`ios-U ${r4((await diffWebVsRef(`${HERE}/hyphen-ios-linebox-replay/radius-${stem}-ios-U.png`, ref)).ssim)}`);
  console.log(row.join(' · '));
}

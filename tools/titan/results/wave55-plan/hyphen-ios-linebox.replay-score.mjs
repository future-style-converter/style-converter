#!/usr/bin/env node
// tools/titan/results/wave55-plan/hyphen-ios-linebox.replay-score.mjs — wave-55 L1 (BACKLOG 0(f)) planning scorer.
// Scores, with the gate's own composed metric (inject-wpt-block.mjs diffWebVsRef — the number manifest.json carries),
// (a) the real iOS captures of hyphenate-character-001/-003/-004 at wave54-ab-drop-U3b and wave54-final (sanity: must
// reproduce the gate's cells exactly), and (b) the replay pictures hyphen-ios-linebox.replay.py wrote (arms X, Rw, R0,
// Rd; see that file's banner). Read-only on the run dirs and the frozen refs.
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REF = (t) => `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text/hyphens__hyphenate-character-${t}.png`;
const CAP = (run, t) => `${ROOT}/tools/titan/runs/${run}/sections/css-text/ios-screenshots/wpt__css-text__hyphens__hyphenate-character-${t}.png`;
const r4 = (x) => Math.round(x * 1e4) / 1e4;
for (const t of ['001', '002', '003', '004']) {
  const row = [`${t} ios`];
  for (const run of ['wave54-ab-drop-U3b', 'wave54-final']) row.push(`${run} ${r4((await diffWebVsRef(CAP(run, t), REF(t))).ssim)}`);
  // -002's emergency-broken groups have no line-by-line ref twin: only the model arms exist for it.
  const arms = t === '002' ? ['Rm', 'Rm1'] : ['X', 'Rw', 'Rm', 'R0', 'Rd', 'R1', 'Rm1'];
  for (const arm of arms) row.push(`${arm} ${r4((await diffWebVsRef(`${HERE}/hyphen-ios-linebox-replay/${t}-ios-${arm}.png`, REF(t))).ssim)}`);
  console.log(row.join(' · '));
}

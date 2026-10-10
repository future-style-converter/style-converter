#!/usr/bin/env node
// Scores hyphenate-character.replay-b-u3f.py's pictures and the wave53-final captures they were cut from with the gate's
// composed metric (inject-wpt-block.mjs diffWebVsRef; the capture column must reproduce the manifest value).
import { existsSync } from 'node:fs';
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
for (const [sec, stem] of [['css-masking', 'clip-path__clip-path-filter-order'], ['css-multicol', 'balance-grid-container']]) {
  for (const [plat, d] of Object.entries(DIRS)) {
    const B = `${HERE}/hyphenate-character-replay/${sec}__${stem}-${plat}-B.png`;
    if (!existsSync(B)) continue;
    const cap = `${ROOT}/tools/titan/runs/wave53-final/sections/${sec}/${d}/wpt__${sec}__${stem}.png`;
    const c = (await diffWebVsRef(cap, `${REFS}/${sec}/${stem}.png`)).ssim, b = (await diffWebVsRef(B, `${REFS}/${sec}/${stem}.png`)).ssim;
    console.log(`${sec}/${stem} ${plat.padEnd(7)} capture ${c} B ${b}  Δ${b - c >= 0 ? '+' : ''}${(b - c).toFixed(4)}`);
  }
}

#!/usr/bin/env node
// Scores hyphenate-character.replay-b-radius.py's pictures and the wave53-final
// captures they were cut from, with the gate's composed metric (diffWebVsRef).
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
for (const t of ['002', '004', '005', '006']) {
  const stem = `line-clamp__block-ellipsis-${t}`;
  const ref = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-overflow/${stem}.png`;
  for (const [plat, d] of Object.entries(DIRS)) {
    const cap = `${ROOT}/tools/titan/runs/wave53-final/sections/css-overflow/${d}/wpt__css-overflow__${stem}.png`;
    console.log(`${stem} ${plat.padEnd(7)} capture ${(await diffWebVsRef(cap, ref)).ssim} B ${(await diffWebVsRef(`${HERE}/hyphenate-character-replay/${stem}-${plat}-B.png`, ref)).ssim}`);
  }
}

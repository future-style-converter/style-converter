#!/usr/bin/env node
// hyphenate-character.replay-score.mjs — scores the wave53-final captures (sanity:
// must reproduce the gate's numbers) and the replay pictures written by
// hyphenate-character.replay.py, with the gate's own composed metric
// (inject-wpt-block.mjs diffWebVsRef). Read-only on the run dirs.
//   G  = hyphenate string fixed only (br blank lines + lost <b> left)
//   GB = string + br heights fixed (lost <b> left)
//   GW = string + <b> fixed (br blank lines left)
//   GBd = GB with the platform's surviving per-group drift (the realistic native estimate)
//   GBn = (iOS only) GB with a -0/-1/-2/-3 px drift: the U3b risk arm
//   B  = br heights fixed only, on the platform's OWN capture (hyphenate-character.replay-b.py)
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REF = (t) => `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text/hyphens__hyphenate-character-${t}.png`;
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
for (const t of ['001', '002', '003', '004']) {
  for (const [plat, d] of Object.entries(DIRS)) {
    const cap = `${ROOT}/tools/titan/runs/wave53-final/sections/css-text/${d}/wpt__css-text__hyphens__hyphenate-character-${t}.png`;
    const row = [`${t} ${plat.padEnd(7)}`, 'capture', (await diffWebVsRef(cap, REF(t))).ssim];
    row.push('B', (await diffWebVsRef(`${HERE}/hyphenate-character-replay/${t}-${plat}-B.png`, REF(t))).ssim);
    if (t !== '002') for (const tag of ['G', 'GB', 'GBd', 'GW']) {
      row.push(tag, (await diffWebVsRef(`${HERE}/hyphenate-character-replay/${t}-${plat}-${tag}.png`, REF(t))).ssim);
    }
    if (t !== '002' && plat === 'ios') row.push('GBn', (await diffWebVsRef(`${HERE}/hyphenate-character-replay/${t}-ios-GBn.png`, REF(t))).ssim);
    console.log(row.join(' '));
  }
}

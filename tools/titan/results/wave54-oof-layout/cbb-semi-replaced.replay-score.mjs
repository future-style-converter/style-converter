#!/usr/bin/env node
// L4 (CBB-android) — scores the three semi-replaced-stretch ANDROID captures
// of wave53-final (sanity: must reproduce the gate's numbers) and their CBB
// replays (cbb-semi-replaced.replay.py) with the gate's own composed metric
// (tools/titan/inject-wpt-block.mjs diffWebVsRef). Read-only on the run dirs.
import path from 'node:path';
import { diffWebVsRef } from '../../inject-wpt-block.mjs';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const REFS = `${ROOT}/tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-position`;
const CAP = `${ROOT}/tools/titan/runs/wave53-final/sections/css-position/android-screenshots`;
for (const [t, gate] of [['button', 'f 0.9739'], ['input', 'f 0.4117'], ['other', 'f 0.4396']]) {
  const ref = `${REFS}/position-absolute-semi-replaced-stretch-${t}.png`;
  // Today's capture first (it must print the gate's value), then the replay.
  const a = await diffWebVsRef(`${CAP}/wpt__css-position__position-absolute-semi-replaced-stretch-${t}.png`, ref);
  const b = await diffWebVsRef(`${HERE}/cbb-semi-replaced-replay/android-${t}.png`, ref);
  console.log(`${t.padEnd(7)} gate ${gate} | capture ssim ${a.ssim} pass ${a.wptPass ?? a.pass} | CBB replay ssim ${b.ssim} pass ${b.wptPass ?? b.pass}`);
}

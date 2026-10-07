#!/usr/bin/env node
// tools/titan/results/wave53-plan/contents-root-background.replay.mjs
//
// PNG replay for the wave-53 family brief `contents-root-background.md`.
// Reads FOUR PNGs (the frozen ref + the three wave52-ship captures of
// css-display/display-contents-root-background), simulates the post-fix
// capture by re-compositing every capture pixel's black-text coverage over the
// root background's tile colour (0,128,0) instead of the white canvas, and
// scores both the shipped and the simulated capture with the scorer's own SSIM
// call (ssim.js, { ssim: 'fast' } — inject-wpt-block.mjs diffWebVsRef :478;
// same-size frames, so fitToRefFrame is the identity and overflow is 0).
// Light by construction: 4 decodes, 6 SSIMs on 390x600.
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';

const ROOT = new URL('../../../../', import.meta.url).pathname;
const S = 'css-display', ST = 'display-contents-root-background';
const REF = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/${S}/${ST}.png`;
const CAP = (dir) => `${ROOT}tools/titan/runs/wave52-ship/sections/${S}/${dir}/wpt__${S}__${ST}.png`;
const TILE = [0, 128, 0];   // tools/wpt/css/support/1x1-green.png's one pixel (PLTE 00 80 00)

const load = (p) => PNG.sync.read(readFileSync(p));
const img = (png) => ({ data: new Uint8ClampedArray(png.data), width: png.width, height: png.height });

// Black ink over white: a capture grey v means coverage a = 1 - v/255, so the
// same ink over the tile colour C is C * (1 - a) = C * v/255 (per channel).
function overTile(png) {
  const out = new PNG({ width: png.width, height: png.height });
  for (let i = 0; i < png.data.length; i += 4) {
    const v = (png.data[i] + png.data[i + 1] + png.data[i + 2]) / 3;   // captures are grey
    for (let c = 0; c < 3; c++) out.data[i + c] = Math.round(TILE[c] * v / 255);
    out.data[i + 3] = 255;
  }
  return out;
}

const ref = load(REF);
for (const [plat, dir] of [['web', 'screenshots'], ['ios', 'ios-screenshots'], ['android', 'android-screenshots']]) {
  const cap = load(CAP(dir));
  const now = ssim(img(cap), img(ref), { ssim: 'fast' }).mssim;
  const sim = ssim(img(overTile(cap)), img(ref), { ssim: 'fast' }).mssim;
  console.log(`${plat.padEnd(8)} shipped ${now.toFixed(4)}  simulated-post-fix ${sim.toFixed(4)}`);
}

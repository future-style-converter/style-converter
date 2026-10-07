#!/usr/bin/env node
// tools/titan/results/wave53-canvas-root/s006.replay.mjs — L3 item-B score REPLAY for
// CSS2/css21-errata/s-11-1-1b-006 (the brief predicted the natives' number only "by analogy").
// Reads the frozen ref + the three wave52-ship captures, moves each capture's 20×20 black square
// from where the platform drew it (web y66, natives y51) to the reference's rows 56-75 (x 24-43,
// unchanged), and scores shipped vs simulated with the scorer's own SSIM call (ssim.js { ssim:
// 'fast' }, as inject-wpt-block.mjs diffWebVsRef). Pixels vacated by the square are refilled
// from the reference (white there on every platform's ink map: the text band ends at row 51).
// Also scores the "square vanished" capture — the predicted Android outcome (see _note.md §B).
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';

const ROOT = new URL('../../../../', import.meta.url).pathname;
const S = 'CSS2', ST = 'css21-errata__s-11-1-1b-006';
const REF = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/${S}/${ST}.png`;
const CAP = (dir) => `${ROOT}tools/titan/runs/wave52-ship/sections/${S}/${dir}/wpt__${S}__${ST}.png`;
const load = (p) => PNG.sync.read(readFileSync(p));
const img = (png) => ({ data: new Uint8ClampedArray(png.data), width: png.width, height: png.height });

/** A copy of `cap` with the square's rows [from, from+20) blanked to white and redrawn at rows [to, to+20). */
function moved(cap, from, to) {
  const out = new PNG({ width: cap.width, height: cap.height }); cap.data.copy(out.data);
  const px = (x, y, v) => { const i = (y * cap.width + x) * 4; out.data[i] = out.data[i + 1] = out.data[i + 2] = v; out.data[i + 3] = 255; };
  for (let y = from; y < from + 20; y++) for (let x = 24; x < 44; x++) px(x, y, 255);
  if (to !== null) for (let y = to; y < to + 20; y++) for (let x = 24; x < 44; x++) px(x, y, 0);
  return out;
}

const ref = load(REF);
for (const [plat, dir, from] of [['web', 'screenshots', 66], ['ios', 'ios-screenshots', 51], ['android', 'android-screenshots', 51]]) {
  const cap = load(CAP(dir));
  const score = (p) => ssim(img(p), img(ref), { ssim: 'fast' }).mssim.toFixed(4);
  console.log(`${plat.padEnd(8)} shipped ${score(cap)}  square→y56 ${score(moved(cap, from, 56))}  square-vanished ${score(moved(cap, from, null))}`);
}

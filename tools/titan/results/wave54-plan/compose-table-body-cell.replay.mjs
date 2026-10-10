#!/usr/bin/env node
// tools/titan/results/wave54-plan/compose-table-body-cell.replay.mjs — score REPLAY for the re-done L3 B-android
// (CSS2/css21-errata/s-11-1-1b-006 android), with the method that predicted wave-53's iOS cell exactly
// (wave53-canvas-root/s006.replay.mjs: replay 0.9992, device 0.9992). Scorer's own SSIM call (ssim.js
// {ssim:'fast'}, as inject-wpt-block.mjs diffWebVsRef) against the frozen ref.
//   0. calibration: the two shipped Android pictures scored as they are (must print the gate's 0.9944 / 0.9906);
//   1. FIX (D1 + D2): from the probe picture — clear the outlined anonymous cell (x24-43) and the displaced square
//      (x44-63), draw the 20x20 square at x24-43 rows 56-75; and the same from the final picture (square 51 → 56);
//   2. D1-only (stroke gone, the anonymous cell still fills): the square stays at x44-63, no outline — the falsifier
//      picture the geometry probe must catch and SSIM would not;
//   3. D2-only (the anonymous cell hugs to 0, the demo stroke stays): the td keeps a 1-px black stroke on its own
//      black fill → the FIX picture byte-for-byte (black on black), recorded so a D2-only landing is understood.
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';

const ROOT = new URL('../../../../', import.meta.url).pathname;
const S = 'CSS2', ST = 'css21-errata__s-11-1-1b-006';
const REF = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/${S}/${ST}.png`;
const CAP = (run) => `${ROOT}tools/titan/runs/${run}/sections/${S}/android-screenshots/wpt__${S}__${ST}.png`;
const load = (p) => PNG.sync.read(readFileSync(p));
const img = (png) => ({ data: new Uint8ClampedArray(png.data), width: png.width, height: png.height });
const ref = load(REF);
const score = (p) => ssim(img(p), img(ref), { ssim: 'fast' }).mssim.toFixed(4);

/** A copy of `cap` with rect [x0,x1)×[y0,y1) painted grey level v. */
function paint(cap, rects) {
  const out = new PNG({ width: cap.width, height: cap.height }); cap.data.copy(out.data);
  for (const [x0, x1, y0, y1, v] of rects) for (let y = y0; y < y1; y++) for (let x = x0; x < x1; x++) {
    const i = (y * cap.width + x) * 4; out.data[i] = out.data[i + 1] = out.data[i + 2] = v; out.data[i + 3] = 255;
  }
  return out;
}

const probe = load(CAP('wave53-probe')), fin = load(CAP('wave53-final'));
console.log(`calibration  wave53-final android as shipped ${score(fin)}   wave53-probe android as shipped ${score(probe)}`);
// 1. FIX: clear x24-63 rows 56-75 (outline + displaced square), draw the square at x24-43 rows 56-75.
const fixFromProbe = paint(probe, [[24, 64, 56, 76, 255], [24, 44, 56, 76, 0]]);
const fixFromFinal = paint(fin, [[24, 44, 51, 71, 255], [24, 44, 56, 76, 0]]);
console.log(`FIX          from probe ${score(fixFromProbe)}   from final ${score(fixFromFinal)}`);
// 2. D1-only: the stroke gone, the td still displaced to x44-63.
const d1only = paint(probe, [[24, 44, 56, 76, 255]]);
console.log(`D1-only      square at x44-63, no outline ${score(d1only)}   (geometry probe must print WRONG)`);
// 3. D2-only: the td at x24-43 with a 1-px black stroke on its black fill = the FIX picture.
const d2only = paint(fixFromProbe, [[24, 44, 56, 57, 0], [24, 44, 75, 76, 0], [24, 25, 56, 76, 0], [43, 44, 56, 76, 0]]);
console.log(`D2-only      td stroked black-on-black ${score(d2only)}   (identical bytes to FIX: ${Buffer.compare(d2only.data, fixFromProbe.data) === 0})`);

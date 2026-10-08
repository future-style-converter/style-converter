#!/usr/bin/env node
// tools/titan/results/wave54-plan/web-root-separator.replay.mjs
//
// PNG replay for queue-scout-text-web.md §B (web-root-separator). Reads the
// frozen ref + the wave53-final WEB capture of each target, simulates the
// post-fix capture by moving every pixel right of the measured split column
// (rows yFrom..yTo only, so the prose above is untouched) RIGHT by the
// measured missing advance (5 px — ref second-column left edge minus web's,
// measured by the census's run scan: 151 vs 146, 91 vs 86, 151 vs 146,
// 192 vs 187), filling the vacated band with the canvas white, and scores
// shipped and simulated with the scorer's own SSIM call (ssim.js,
// { ssim: 'fast' } — inject-wpt-block.mjs diffWebVsRef; same-size frames,
// so fitToRefFrame is the identity). The simulation is an UPPER BOUND on the
// geometry half only: it cannot add the missing <strong> weight (every
// box-sizing paragraph is `inline-run-merged`), which every column lacks.
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';

const ROOT = new URL('../../../../', import.meta.url).pathname;
const REFS = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const CAP = (sec, t) => `${ROOT}tools/titan/runs/wave53-final/sections/${sec}/screenshots/wpt__${sec}__${t.split('/').join('__')}.png`;
// [section, test, splitX (first moved column), dx, yFrom, yTo(exclusive; -1 = bottom)]
const CASES = [
  ['css-ui', 'box-sizing-007', 136, 5, 100, -1],
  ['css-ui', 'box-sizing-008', 136, 5, 100, -1],
  ['css-ui', 'box-sizing-022', 146, 5, 100, -1],
  ['css-ui', 'box-sizing-010', 86, 5, 100, -1],   // = 011/014-019 (web capture sha1 857e5d3751 ×8)
  ['css-ui', 'box-sizing-013', 86, 5, 100, -1],
  ['css-ui', 'box-sizing-020', 146, 5, 100, -1],   // = 021/024/025 (web capture sha1 e1705010ed ×4)
  ['css-position', 'position-absolute-semi-replaced-stretch-other', 180, 5, 0, -1],
  ['css-position', 'position-absolute-semi-replaced-stretch-input', 180, 5, 0, -1],
];
const load = (p) => PNG.sync.read(readFileSync(p));
const img = (png) => ({ data: new Uint8ClampedArray(png.data), width: png.width, height: png.height });
function shift(png, splitX, dx, y0, y1) {
  const out = new PNG({ width: png.width, height: png.height });
  png.data.copy(out.data);
  const W = png.width, yEnd = y1 < 0 ? png.height : y1;
  for (let y = y0; y < yEnd; y++) {
    for (let x = W - 1; x >= splitX; x--) {
      const dst = (y * W + x) * 4;
      const sx = x - dx;
      if (sx >= splitX) { for (let c = 0; c < 4; c++) out.data[dst + c] = png.data[(y * W + sx) * 4 + c]; }
      else { out.data[dst] = out.data[dst + 1] = out.data[dst + 2] = 255; out.data[dst + 3] = 255; }
    }
  }
  return out;
}
for (const [sec, t, splitX, dx, y0, y1] of CASES) {
  const ref = load(`${REFS}/${sec}/${t.split('/').join('__')}.png`);
  const cap = load(CAP(sec, t));
  if (ref.width !== cap.width || ref.height !== cap.height) { console.log(`${t}: frame ${cap.width}x${cap.height} vs ref ${ref.width}x${ref.height} — skipped`); continue; }
  const now = ssim(img(cap), img(ref), { ssim: 'fast' }).mssim;
  const sim = ssim(img(shift(cap, splitX, dx, y0, y1)), img(ref), { ssim: 'fast' }).mssim;
  console.log(`${(sec + '/' + t).padEnd(62)} web shipped ${now.toFixed(4)}  simulated-separator ${sim.toFixed(4)}`);
}

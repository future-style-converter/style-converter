#!/usr/bin/env node
// tools/titan/results/wave54-web-tail/scope-pseudo.replay.mjs — wave 54 lane L6 (RS): the sign of the LOW
// up-or-stay row css-cascade/scope-pseudo-element web (f 0.9353), which revert rule 2 binds (a Δ ≤ −0.002
// would revert RS). Simulates the separators' effect on the wave53-final web capture — box 2 (web x118..219)
// right by `d1`, box 3 (x220..) right by `d2` (ref lefts 123 / 229: the advances 5 and 9 of two collapsed
// spaces, ±1 px for the sub-pixel 4.5-px advance) — and scores it with the scorer's own SSIM call
// (ssim.js { ssim: 'fast' }, as web-root-separator.replay.mjs). An upper-bound geometry replay only.
import { readFileSync } from 'node:fs';
import { PNG } from 'pngjs';
import { ssim } from 'ssim.js';
const ROOT = new URL('../../../../', import.meta.url).pathname;
const ref = PNG.sync.read(readFileSync(`${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-cascade/scope-pseudo-element.png`));
const cap = PNG.sync.read(readFileSync(`${ROOT}tools/titan/runs/wave53-final/sections/css-cascade/screenshots/wpt__css-cascade__scope-pseudo-element.png`));
const img = (png) => ({ data: new Uint8ClampedArray(png.data), width: png.width, height: png.height });
/** Move columns [x0, W) right by dx on every row (vacated band → white). */
function shift(png, x0, dx) {
  const out = new PNG({ width: png.width, height: png.height }); png.data.copy(out.data);
  const W = png.width;
  for (let y = 0; y < png.height; y++) for (let x = W - 1; x >= x0; x--) {
    const dst = (y * W + x) * 4, sx = x - dx;
    for (let c = 0; c < 4; c++) out.data[dst + c] = sx >= x0 ? png.data[(y * W + sx) * 4 + c] : 255;
  }
  return out;
}
console.log(`frames web ${cap.width}x${cap.height} ref ${ref.width}x${ref.height}`);
console.log(`shipped ${ssim(img(cap), img(ref), { ssim: 'fast' }).mssim.toFixed(4)}`);
for (const [d1, d2] of [[4, 9], [5, 9], [5, 10], [4, 8]]) {
  // Box 3 first (its band starts right of box 2's), then box 2 — each shift carries what lies right of it.
  const sim = shift(shift(cap, 220, d2 - d1), 118, d1);
  console.log(`box2 +${d1} box3 +${d2}: ${ssim(img(sim), img(ref), { ssim: 'fast' }).mssim.toFixed(4)}`);
}

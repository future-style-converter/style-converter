// Plan skeptic r1, check (4c): for the gating keys whose geometry probe PASSES a vertically displaced ref
// (adversarial-geometry.out.txt), does the SSIM floor catch the same picture? Scores ref-shifted-down-by-dy vs ref with
// the scorer's own call (ssim.js { ssim: 'fast' }, same-size frame, 4 dp — tools/titan/inject-wpt-block.mjs diffWebVsRef).
import { readFileSync } from 'node:fs'; import { PNG } from 'pngjs'; import { ssim } from 'ssim.js';
const ROOT = new URL('../../../../../', import.meta.url).pathname;
const REFS = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const CASES = [['css-text', 'bidi/bidi-lines-002', 0.975, '[P] android'], ['css-break', 'block-in-inline-015-print', 0.97, 'U1-android'],
  ['css-ui', 'box-sizing-007', 0.975, 'RS'], ['css-ui', 'box-sizing-008', 0.965, 'RS'], ['css-ui', 'box-sizing-022', 0.96, 'RS'],
  ['css-text', 'hyphens/hyphens-out-of-flow-002', 0.995, 'W1']];
const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
const shift = (png, dy) => { const o = new PNG({ width: png.width, height: png.height }); o.data.fill(255);
  for (let y = 0; y < png.height - dy; y++) png.data.copy(o.data, ((y + dy) * png.width) * 4, (y * png.width) * 4, ((y + 1) * png.width) * 4); return o; };
for (const [sec, t, floor, unit] of CASES) {
  const ref = PNG.sync.read(readFileSync(`${REFS}/${sec}/${t.split('/').join('__')}.png`));
  const row = [1, 2, 3, 4, 6].map((dy) => { const s = +ssim(img(shift(ref, dy)), img(ref), { ssim: 'fast' }).mssim.toFixed(4); return `down${dy} ${s}${s >= floor ? ' (>= floor)' : ''}`; });
  console.log(`${(sec + '/' + t).padEnd(44)} ${unit.padEnd(11)} floor ${floor}: ${row.join(' · ')}`);
}

// Plan skeptic r1, check (4d): block-in-inline-015-print — a picture with the RIGHT 2em bold face but the WRONG line
// pitch (each of the 4 lines moved down k·d px: a line-height error) — does the gating pair (floor 0.97 AND
// ua-heading-face.geometry.py's band heights + right edges) catch it? Writes the pictures as a synthetic run so the
// probe itself can be run on them; scores with the scorer's ssim.js { ssim: 'fast' } call.
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'; import { PNG } from 'pngjs'; import { ssim } from 'ssim.js';
const ROOT = new URL('../../../../../', import.meta.url).pathname; const HERE = new URL('./', import.meta.url).pathname;
const REF = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-break/block-in-inline-015-print.png`;
const ref = PNG.sync.read(readFileSync(REF)); const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
// ref bands (ua-heading-face.geometry.out.txt): (29,46) (65,86) (103,126) (142,166); cut lines halfway between them.
const cuts = [[0, 55], [56, 94], [95, 133], [134, 199]];
for (const d of [1, 2, 3, 4]) {
  const o = new PNG({ width: ref.width, height: ref.height }); o.data.fill(255);
  cuts.forEach(([y0, y1], k) => { for (let y = y0; y <= y1; y++) { const ty = y + k * d; if (ty < ref.height) ref.data.copy(o.data, ty * ref.width * 4, y * ref.width * 4, (y + 1) * ref.width * 4); } });
  for (let y = 200; y < ref.height; y++) ref.data.copy(o.data, y * ref.width * 4, y * ref.width * 4, (y + 1) * ref.width * 4);
  const s = +ssim(img(o), img(ref), { ssim: 'fast' }).mssim.toFixed(4);
  for (const dir of ['screenshots', 'ios-screenshots', 'android-screenshots']) { const dd = `${HERE}fake-pitch${d}/sections/css-break/${dir}`; mkdirSync(dd, { recursive: true }); writeFileSync(`${dd}/wpt__css-break__block-in-inline-015-print.png`, PNG.sync.write(o)); }
  console.log(`pitch +${d} px/line (line 4 displaced ${3 * d} px): ssim ${s} ${s >= 0.97 ? '>= floor 0.97 (floor PASSES it)' : '< floor 0.97'}`);
}

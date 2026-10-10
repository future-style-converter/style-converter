// skeptic-r2 adversarial picture for the GAP gating keys (flex-gap-decorations-033 ios/android, floor 0.99): the ref
// with line 2's single column-rule segment painted at the OTHER gap (x111-120 -> x61-70, rows 71-120). Same red pixel
// count, same red bbox, same blue, same row-20 runs. Scored with the scorer's call (ssim.js fast, 4 dp).
import { readFileSync, writeFileSync } from 'node:fs'; import { PNG } from 'pngjs'; import { ssim } from 'ssim.js';
const ROOT = new URL('../../../../../', import.meta.url).pathname;
const REF = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-gaps/flex__flex-gap-decorations-033.png`;
const ref = PNG.sync.read(readFileSync(REF));
const fake = new PNG({ width: ref.width, height: ref.height }); ref.data.copy(fake.data);
const at = (x, y) => (y * ref.width + x) * 4;
for (let y = 71; y <= 120; y++) for (let dx = 0; dx < 10; dx++) {
  const a = at(111 + dx, y), b = at(61 + dx, y);
  for (let k = 0; k < 4; k++) { const t = fake.data[a + k]; fake.data[a + k] = fake.data[b + k]; fake.data[b + k] = t; }
}
const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
const s = +ssim(img(fake), img(ref), { ssim: 'fast' }).mssim.toFixed(4);
for (const d of ['screenshots', 'ios-screenshots', 'android-screenshots']) writeFileSync(`skeptic-r2/fake-gap/sections/css-gaps/${d}/wpt__css-gaps__flex__flex-gap-decorations-033.png`, PNG.sync.write(fake));
console.log(`fake (line-2 segment at the wrong gap) vs ref: ssim ${s} ${s >= 0.99 ? '>= floor 0.99 (the floor does NOT catch it)' : '< floor 0.99'}`);

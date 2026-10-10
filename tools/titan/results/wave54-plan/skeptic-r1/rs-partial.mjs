// Plan skeptic r1, check (4e): web-root-separator.geometry.py reads ONE pixel row per test. Simulate a PARTIAL RS fix on
// box-sizing-007 / -008 web (only the atom row band that holds the probed row gets the 5-px separator; every other row
// stays flush) and score it with the scorer's ssim.js { ssim: 'fast' } call against the ref; write it as a synthetic
// run so the probe itself reads it. A partial fix that clears the floor AND the probe is a wrong picture certified.
import { readFileSync, mkdirSync, writeFileSync } from 'node:fs'; import { PNG } from 'pngjs'; import { ssim } from 'ssim.js';
const ROOT = new URL('../../../../../', import.meta.url).pathname; const HERE = new URL('./', import.meta.url).pathname;
const REFS = `${ROOT}tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`;
const img = (p) => ({ data: new Uint8ClampedArray(p.data), width: p.width, height: p.height });
const inkRow = (p, y, x0) => { for (let x = x0; x < p.width; x++) { const i = (y * p.width + x) * 4; if (p.data[i] < 240 || p.data[i + 1] < 240 || p.data[i + 2] < 240) return true; } return false; };
for (const [t, probeY, split, floor] of [['box-sizing-007', 150, 136, 0.975], ['box-sizing-008', 150, 136, 0.965]]) {
  const ref = PNG.sync.read(readFileSync(`${REFS}/css-ui/${t}.png`));
  const cap = PNG.sync.read(readFileSync(`${ROOT}tools/titan/runs/wave53-final/sections/css-ui/screenshots/wpt__css-ui__${t}.png`));
  let y0 = probeY, y1 = probeY; while (y0 > 0 && inkRow(cap, y0 - 1, split)) y0--; while (y1 < cap.height - 1 && inkRow(cap, y1 + 1, split)) y1++;
  const out = new PNG({ width: cap.width, height: cap.height }); cap.data.copy(out.data);
  for (let y = y0; y <= y1; y++) for (let x = cap.width - 1; x >= split; x--) { const d = (y * cap.width + x) * 4, sx = x - 5;
    if (sx >= split) for (let c = 0; c < 4; c++) out.data[d + c] = cap.data[(y * cap.width + sx) * 4 + c]; else out.data.fill(255, d, d + 4); }
  const s0 = +ssim(img(cap), img(ref), { ssim: 'fast' }).mssim.toFixed(4), s1 = +ssim(img(out), img(ref), { ssim: 'fast' }).mssim.toFixed(4);
  const dd = `${HERE}fake-rs-partial/sections/css-ui/screenshots`; mkdirSync(dd, { recursive: true }); writeFileSync(`${dd}/wpt__css-ui__${t}.png`, PNG.sync.write(out));
  console.log(`${t} web: shipped ${s0}; partial fix (only rows y${y0}-${y1} separated) ${s1} ${s1 >= floor ? `>= floor ${floor}` : `< floor ${floor}`}`);
}

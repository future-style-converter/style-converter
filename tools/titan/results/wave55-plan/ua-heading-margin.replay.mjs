// tools/titan/results/wave55-plan/ua-heading-margin.replay.mjs
//
// Wave-55 lane L4 (ua-heading-margin) — replay ESTIMATES of the score SIGN for each unit, built from the
// wave54-final native captures and the frozen ref, scored with the campaign scorer itself
// (tools/titan/inject-wpt-block.mjs diffWebVsRef — the gate's own composed metric). Read-only over the run and the
// refs; the synthetic PNGs go to ./ua-heading-margin-replay/ (this directory only).
//
// Arms (the heading region = every row at or below CUT, i.e. below the explanation prose):
//   T      today's capture (calibration: must reproduce the wave54-final cell exactly)
//   M<m>   (and M<m±1>, the whole-px sensitivity) U1 alone — the UA margin on the abspos heading, face still withheld: the native heading region moved
//          DOWN by m px. m = round(.67 × 32) = 21 (005/006/011) and round(.67 × 26) = 17 (014): the UA-CASCADE basis
//          (the em of the heading's UA face, HTML §15.3.7) this brief recommends.
//   R<m>   the rejected basis — the margin at the face the native PAINTS today (16 px → 11; monospace 13 → 9).
//   C      U1 + U2 with the wrap — the ref's own heading rows at lift 0 (skeptic arm C; the ceiling, Chromium glyphs).
//   D      U1 + U2 with NO wrap (Compose: RenderAbsoluteChild → absposOverflowMeasure measures Constraints(),
//          unbounded, so "fox" stays on line 1 and is clipped at x390) — 005/006 only (014 has a definite 16ch width).
//   B<m>   U2 alone (face, no margin) — the ref's rows lifted m px (skeptic arm B / synth-sensitivity lift m).
import { dirname, join } from 'node:path'; import { fileURLToPath } from 'node:url';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'; import { createRequire } from 'node:module';
const HERE = dirname(fileURLToPath(import.meta.url)); const ROOT = join(HERE, '..', '..', '..', '..');
const require = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs')); const { PNG } = require('pngjs');
const { diffWebVsRef } = await import(join(ROOT, 'tools', 'titan', 'inject-wpt-block.mjs'));
const REF = join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor');
const RUN = join(ROOT, 'tools/titan/runs/wave54-final/sections/css-text-decor');
const OUT = join(HERE, 'ua-heading-margin-replay'); mkdirSync(OUT, { recursive: true });
const DIRS = { android: 'android-screenshots', ios: 'ios-screenshots' };
// CUT: first row below the explanation's last ink band (natives: 005/006/014 end y90; 011 ends y106/107).
const CASES = [
  { t: 'text-decoration-inset-005', cut: 96, m: 21, r: 11, faces: true, nowrap: true },
  { t: 'text-decoration-inset-006', cut: 96, m: 21, r: 11, faces: true, nowrap: true },
  { t: 'text-decoration-inset-014', cut: 96, m: 17, r: 9, faces: true, nowrap: false },
  { t: 'text-decoration-inset-011', cut: 118, m: 21, r: 11, faces: false, nowrap: false },
];
const white = (im, x, y) => { const i = (y * im.width + x) * 4; im.data[i] = im.data[i + 1] = im.data[i + 2] = 255; im.data[i + 3] = 255; };
const copyPx = (dst, dx, dy, src, sx, sy) => {
  if (dx < 0 || dy < 0 || dx >= dst.width || dy >= dst.height || sx < 0 || sy < 0 || sx >= src.width || sy >= src.height) return;
  const d = (dy * dst.width + dx) * 4, s = (sy * src.width + sx) * 4; for (let c = 0; c < 4; c++) dst.data[d + c] = src.data[s + c];
};
async function score(im, name, refPath) { const p = join(OUT, name); writeFileSync(p, PNG.sync.write(im)); return (await diffWebVsRef(p, refPath)).ssim; }
const lines = [];
for (const { t, cut, m, r, faces, nowrap } of CASES) {
  const refPath = join(REF, `${t}.png`); const ref = PNG.sync.read(readFileSync(refPath));
  for (const plat of ['ios', 'android']) {
    const capPath = join(RUN, DIRS[plat], `wpt__css-text-decor__${t}.png`);
    const today = (await diffWebVsRef(capPath, refPath)).ssim;
    const row = [`T=${today}`];
    const shifted = async (by, tag) => {
      const src = PNG.sync.read(readFileSync(capPath)); const im = PNG.sync.read(readFileSync(capPath));
      for (let y = cut; y < im.height; y++) for (let x = 0; x < im.width; x++) {
        const sy = y - by; if (sy >= cut) copyPx(im, x, y, src, x, sy); else white(im, x, y);
      }
      const s = await score(im, `${t}-${plat}-${tag}.png`, refPath); row.push(`${tag}=${s} (Δ${(s - today).toFixed(4)})`);
    };
    await shifted(m, `M${m}`);
    // ±1 px sensitivity of the U1 arm (Compose places whole px; iOS may place 21.44 / 17.42 pt with AA rows).
    await shifted(m - 1, `M${m - 1}`);
    await shifted(m + 1, `M${m + 1}`);
    await shifted(r, `R${r}`);
    if (faces) {
      for (const [tag, lift] of [['C', 0], [`B${m}`, m]]) {
        const im = PNG.sync.read(readFileSync(capPath));
        for (let y = 96; y < im.height; y++) for (let x = 0; x < im.width; x++) { const sy = y + lift; if (sy >= 104 && sy < ref.height) copyPx(im, x, y, ref, x, sy); else white(im, x, y); }
        const s = await score(im, `${t}-${plat}-${tag}.png`, refPath); row.push(`${tag}=${s} (Δ${(s - today).toFixed(4)})`);
      }
      if (nowrap && plat === 'android') {
        // D: ref line 1 (rows 128–178) in place + the ref's "fox" (x10–72, rows 182–216) moved up one 40-px line box
        // to sit after "n" (skeptic synth-after arm A geometry at lift 0), clipped at the 390-px frame.
        const im = PNG.sync.read(readFileSync(capPath));
        for (let y = 96; y < im.height; y++) for (let x = 0; x < im.width; x++) white(im, x, y);
        for (let y = 128; y < 178; y++) for (let x = 0; x < im.width; x++) copyPx(im, x, y, ref, x, y);
        for (let y = 182; y < 216; y++) for (let x = 10; x < 72; x++) copyPx(im, 339 + (x - 10), y - 40, ref, x, y);
        const s = await score(im, `${t}-${plat}-D.png`, refPath); row.push(`D=${s} (Δ${(s - today).toFixed(4)})`);
      }
    }
    lines.push(`${t} ${plat} ${row.join('  ')}`);
  }
}
console.log(lines.join('\n'));

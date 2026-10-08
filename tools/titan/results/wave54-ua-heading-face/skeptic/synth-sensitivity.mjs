// Skeptic (wave 54 L5) — sensitivity of the synth estimate: the ref's own heading rows (face + wrap)
// pasted into TODAY's native capture at lifts 0…24 px (0 = the UA margin present; 21 = .67em × 32
// absent), for inset-005 / -006 / -014 on both natives. Lift needs the pixels, so it is done in JS.
import { dirname, join } from 'node:path'; import { fileURLToPath } from 'node:url';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'; import { createRequire } from 'node:module';
const HERE = dirname(fileURLToPath(import.meta.url)); const ROOT = join(HERE, '..', '..', '..', '..', '..');
const require = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs')); const { PNG } = require('pngjs');
const { diffWebVsRef } = await import(join(ROOT, 'tools', 'titan', 'inject-wpt-block.mjs'));
const REF = join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor');
const RUN = join(ROOT, 'tools/titan/runs/wave53-final/sections/css-text-decor');
const OUT = join(HERE, 'png', 'sens'); mkdirSync(OUT, { recursive: true });
for (const t of ['text-decoration-inset-005', 'text-decoration-inset-006', 'text-decoration-inset-014']) {
  const refPath = join(REF, `${t}.png`); const ref = PNG.sync.read(readFileSync(refPath));
  for (const [plat, d] of [['android', 'android-screenshots'], ['ios', 'ios-screenshots']]) {
    const capPath = join(RUN, d, `wpt__css-text-decor__${t}.png`); const today = (await diffWebVsRef(capPath, refPath)).ssim;
    const row = [];
    for (const lift of [0, 6, 12, 18, 21, 24]) {
      const cap = PNG.sync.read(readFileSync(capPath));
      for (let y = 104; y < cap.height; y++) for (let x = 0; x < cap.width; x++) {
        const i = (y * cap.width + x) * 4; const sy = y + lift; // the ref row that lands here
        const src = sy >= 110 && sy < ref.height ? (sy * ref.width + x) * 4 : -1;
        for (let c = 0; c < 4; c++) cap.data[i + c] = src >= 0 ? ref.data[src + c] : 255;
      }
      const p = join(OUT, `${t}-${plat}-lift${lift}.png`); writeFileSync(p, PNG.sync.write(cap));
      const s = (await diffWebVsRef(p, refPath)).ssim; row.push(`lift${lift}=${s} (Δ${(s - today).toFixed(4)})`);
    }
    console.log(`${t} ${plat} today=${today} | ${row.join('  ')}`);
  }
}

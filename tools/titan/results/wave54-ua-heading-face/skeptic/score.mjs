// Skeptic (wave 54 L5) — score PNGs against the frozen ref with the campaign scorer (diffWebVsRef);
// first re-scores TODAY's captures, which must reproduce the recorded cell scores (calibration).
import { dirname, join } from 'node:path'; import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url)); const ROOT = join(HERE, '..', '..', '..', '..', '..');
const { diffWebVsRef } = await import(join(ROOT, 'tools', 'titan', 'inject-wpt-block.mjs'));
const REF = join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor');
const RUN = join(ROOT, 'tools/titan/runs/wave53-final/sections/css-text-decor');
for (const t of ['text-decoration-inset-005', 'text-decoration-inset-006']) {
  const ref = join(REF, `${t}.png`);
  for (const [plat, d] of [['android', 'android-screenshots'], ['ios', 'ios-screenshots']]) {
    const today = await diffWebVsRef(join(RUN, d, `wpt__css-text-decor__${t}.png`), ref);
    console.log(`${t} ${plat} TODAY (calibration) ssim=${today.ssim}`);
    for (const k of ['A-nowrap-nomargin', 'B-wrap-nomargin', 'C-wrap-margin']) {
      const r = await diffWebVsRef(join(HERE, 'png', `synth-${t}-${plat}-${k}.png`), ref);
      console.log(`${t} ${plat} ${k} ssim=${r.ssim} Δ=${(r.ssim - today.ssim).toFixed(4)}`);
    }
  }
}

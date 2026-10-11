// tools/titan/results/wave55-plan/ua-heading-margin.calibrate.mjs
//
// Wave-55 lane L4 — the RASTER ALLOWANCE of the replay's ceiling arm C. The anchors text-decoration-inset-001…004
// already carry the 2em bold face AND the .67em margin on both natives (extractor-baked UA_H1_PROPS, the same abspos
// <h1> in a relative <div>), so "arm C on the anchor" (the ref's own heading rows pasted into the native capture)
// minus "the anchor's real cell" is how much native glyphs cost a CORRECT heading. That gap, per platform, is
// subtracted from the target's arm-C / arm-D replay to predict a device score. Read-only; synthetic PNGs go to
// ./ua-heading-margin-replay/.
import { dirname, join } from 'node:path'; import { fileURLToPath } from 'node:url';
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'; import { createRequire } from 'node:module';
const HERE = dirname(fileURLToPath(import.meta.url)); const ROOT = join(HERE, '..', '..', '..', '..');
const require = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs')); const { PNG } = require('pngjs');
const { diffWebVsRef } = await import(join(ROOT, 'tools', 'titan', 'inject-wpt-block.mjs'));
const REF = join(ROOT, 'tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-text-decor');
const RUN = join(ROOT, 'tools/titan/runs/wave54-final/sections/css-text-decor');
const OUT = join(HERE, 'ua-heading-margin-replay'); mkdirSync(OUT, { recursive: true });
const DIRS = { android: 'android-screenshots', ios: 'ios-screenshots' };
const CUT = 96; // below every anchor's explanation prose (its last band ends ≤ y90; the heading starts ≥ y115)
const gaps = { ios: [], android: [] };
for (const n of ['001', '002', '003', '004']) {
  const t = `text-decoration-inset-${n}`; const refPath = join(REF, `${t}.png`); const ref = PNG.sync.read(readFileSync(refPath));
  for (const plat of ['ios', 'android']) {
    const capPath = join(RUN, DIRS[plat], `wpt__css-text-decor__${t}.png`);
    const real = (await diffWebVsRef(capPath, refPath)).ssim;
    const im = PNG.sync.read(readFileSync(capPath));
    for (let y = CUT; y < im.height; y++) for (let x = 0; x < im.width; x++) {
      const i = (y * im.width + x) * 4; for (let c = 0; c < 4; c++) im.data[i + c] = y < ref.height ? ref.data[i + c] : 255;
    }
    const p = join(OUT, `${t}-${plat}-C.png`); writeFileSync(p, PNG.sync.write(im));
    const c = (await diffWebVsRef(p, refPath)).ssim; gaps[plat].push(c - real);
    console.log(`${t} ${plat} real=${real} armC=${c} allowance=${(c - real).toFixed(4)}`);
  }
}
for (const plat of ['ios', 'android']) {
  const g = gaps[plat]; console.log(`${plat} allowance min=${Math.min(...g).toFixed(4)} max=${Math.max(...g).toFixed(4)} mean=${(g.reduce((a, b) => a + b, 0) / g.length).toFixed(4)}`);
}

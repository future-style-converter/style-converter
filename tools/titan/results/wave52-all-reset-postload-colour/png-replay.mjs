#!/usr/bin/env node
// png-replay.mjs — lane L11 PNG replay (PLAN §0 iii): the PREDICTED post-fix
// picture for the colour cells, laid over the frozen ref and scored with the
// gate's own diffWebVsRef (tools/titan/inject-wpt-block.mjs). The fixes change
// colour only (F1: the span's own green survives `all`; F2: lines 5/7 gain
// their live green) — glyph shapes and positions are the runtime's own — so
// the prediction is the wave51-fix capture with the named lines' ink recoloured
// from black to the ref's green rgb(0,128,0), alpha-preserving (an
// anti-aliased pixel of grey level v becomes white→green at α = (255−v)/255).
// The script first re-scores the UNMODIFIED capture to prove it reproduces the
// manifest number (otherwise its predictions are not comparable).
//
// Usage: node png-replay.mjs [out.json]   (writes PNGs + JSON into ./png-replay/)
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const require = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs'));
const { PNG } = require('pngjs');
const { diffWebVsRef } = await import(join(ROOT, 'tools', 'titan', 'inject-wpt-block.mjs'));
const RUN = join(ROOT, 'tools', 'titan', 'runs', 'wave51-fix', 'sections');
const OUT = join(HERE, 'png-replay');
mkdirSync(OUT, { recursive: true });

// Capture dir per platform inside a section of the run.
const CAP = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
// The cells: which ink lines (1-based, top to bottom) the fix recolours, and
// to what (default the ref's green). has-visited is the F2 gainer the 277-test
// replay found beyond the brief (ios f 0.9469): parent1's line turns green,
// parent3's yellowgreen rgb(154,205,50); its links keep their own (chromatic,
// untouched) ink. Ink bands (measured, all three captures): 1 = parent1's
// line [19|20, 35], 5 = parent3's line [79|80, 95].
const GREEN = [0, 128, 0], YELLOWGREEN = [154, 205, 50];
const CASES = [
  { section: 'css-cascade', stem: 'all-prop-initial-color', lines: [[1, GREEN]] },
  { section: 'selectors', stem: 'invalidation__nth-child-of-class', key: 'invalidation/nth-child-of-class', lines: [[5, GREEN], [7, GREEN]] },
  { section: 'selectors', stem: 'has-visited', lines: [[1, GREEN], [5, YELLOWGREEN]] },
  // nth-child-of-has (ios f 0.9371 — the brief predicts NO flip: its deficit is
  // the iOS line overlap) and first-letter-block-to-inline (the script's
  // `inner.style.color = "green"` — a gainer the 277-test replay found).
  { section: 'selectors', stem: 'invalidation__nth-child-of-has', key: 'invalidation/nth-child-of-has', lines: [[5, GREEN], [7, GREEN]] },
  { section: 'css-pseudo', stem: 'first-letter-block-to-inline', lines: [[1, GREEN]] },
];

/** Ink rows → line bands (consecutive rows with any pixel darker than 200). */
function lineBands(png) {
  const bands = [];
  let start = -1;
  for (let y = 0; y < png.height; y++) {
    let ink = false;
    for (let x = 0; x < png.width && !ink; x++) {
      const i = (y * png.width + x) * 4;
      if (png.data[i] < 200 && png.data[i + 1] < 200 && png.data[i + 2] < 200) ink = true;
    }
    if (ink && start < 0) start = y;
    if (!ink && start >= 0) { bands.push([start, y - 1]); start = -1; }
  }
  if (start >= 0) bands.push([start, png.height - 1]);
  return bands;
}

/** Recolour the grey ink of rows [y0, y1] to `rgb`, alpha-preserving. */
function recolour(png, [y0, y1], rgb) {
  let n = 0;
  for (let y = y0; y <= y1; y++) for (let x = 0; x < png.width; x++) {
    const i = (y * png.width + x) * 4;
    const [r, g, b] = [png.data[i], png.data[i + 1], png.data[i + 2]];
    // Only neutral (black→grey AA) ink; anything chromatic is not text ink.
    if (Math.max(r, g, b) - Math.min(r, g, b) > 24 || r > 250) continue;
    const a = (255 - (r + g + b) / 3) / 255;
    png.data[i] = Math.round(255 - a * (255 - rgb[0]));
    png.data[i + 1] = Math.round(255 - a * (255 - rgb[1]));
    png.data[i + 2] = Math.round(255 - a * (255 - rgb[2]));
    n++;
  }
  return n;
}

const rows = [];
for (const c of CASES) {
  const manifest = JSON.parse(readFileSync(join(RUN, c.section, 'manifest.json'), 'utf8'));
  const key = `css/${c.section}/${(c.key ?? c.stem)}.html`;
  // The exact ref the wave51-fix manifest scored against (browserRef.path).
  const refPng = join(ROOT, manifest.wpt.results[key].browserRef.path);   // repo-relative
  for (const [plat, dir] of Object.entries(CAP)) {
    const cap = join(RUN, c.section, dir, `wpt__${c.section}__${c.stem}.png`);
    if (!existsSync(cap) || !existsSync(refPng)) { rows.push({ cell: `${key} ${plat}`, missing: true }); continue; }
    const manifestSsim = manifest.wpt.results[key]?.browserRef?.diffs?.[`${plat}-ref`]?.ssim;
    const before = await diffWebVsRef(cap, refPng);
    const png = PNG.sync.read(readFileSync(cap));
    const bands = lineBands(png);
    const lines = c.lines;
    const changed = lines.map(([k, rgb]) => recolour(png, bands[k - 1], rgb));
    const predicted = join(OUT, `${c.stem}__${plat}__predicted.png`);
    writeFileSync(predicted, PNG.sync.write(png));
    const after = await diffWebVsRef(predicted, refPng);
    rows.push({ cell: `${key} ${plat}`, manifestSsim, rescoredSsim: before.ssim, reproduces: before.ssim === manifestSsim,
      allBands: bands.length, bands: lines.map(([k]) => bands[k - 1]), recolouredPx: changed,
      predictedSsim: after.ssim, predictedWptPass: after.wptPass ?? null,
      labDeltaEMeanBefore: before.labDeltaE?.mean, labDeltaEMeanAfter: after.labDeltaE?.mean,
      pixelMismatchedBefore: before.pixelMismatchedCount, pixelMismatchedAfter: after.pixelMismatchedCount });
    console.log(rows.at(-1));
  }
}
writeFileSync(process.argv[2] ?? join(OUT, 'png-replay.json'), JSON.stringify({ generated: new Date().toISOString(), rows }, null, 1) + '\n');

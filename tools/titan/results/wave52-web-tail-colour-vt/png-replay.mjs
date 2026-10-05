#!/usr/bin/env node
// tools/titan/results/wave52-web-tail-colour-vt/png-replay.mjs — wave-52 lane L1
// PNG REPLAY: the predicted post-fix picture, simulated in pixels from the
// wave51-fix captures, scored against the frozen ref with the SAME diff and
// the SAME verdict composition the gate uses (inject-wpt-block.mjs
// diffWebVsRef → fuzzy / presence / colour / coverage / novel-ink / degenerate
// stamps → computeWptPass; mirrored from its :1746-1767 block).
//
//   node tools/titan/results/wave52-web-tail-colour-vt/png-replay.mjs
//
// F-A: the `.test` box the converter left unpainted is painted with the sRGB
//      the new arm computes (same matrix, in JS). The box is LOCATED once, on
//      the WEB capture, as the bounding box of capture≠ref (web text is the
//      ref's own Chromium text, so the box is the only differing region), and
//      that rectangle is reused for the natives (same 390×600 composed canvas,
//      same layout); each capture is checked to be uniform canvas inside it.
// F-B: every pure-white canvas pixel of the capture becomes the author
//      backdrop — the stamp changes ONLY the canvas colour, box pixels stay.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import {
  diffWebVsRef, checkFuzzyMatch, computePresenceFailed, computeColorFailed,
  computeCoverageRatioFailed, computeWptPass, novelInkVetoActive, degenerateVetoActive,
} from '../../inject-wpt-block.mjs';
import { computeNovelInkFailed } from '../../novel-ink.mjs';
import { degenerateVetoFailed } from '../../degenerate-veto-probe.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const RUN = path.join(REPO, 'tools', 'titan', 'runs', 'wave51-fix', 'sections');
const REFS = path.join(REPO, 'tools', 'wpt', 'refs', '9b5435e55e0b54a6cd09c1c563861eb3c999cef1', 'white-black-ink-font-lh-imgpad-htmlpins');
const OUT = path.join(HERE, 'replay');
fs.mkdirSync(OUT, { recursive: true });
const CAP_DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };
// The `<meta name=fuzzy>` budget the gate honours, read from the run's manifest per test.
const fuzzyOf = (sec, test) => JSON.parse(fs.readFileSync(path.join(RUN, sec, 'manifest.json'), 'utf8')).wpt.results[`css/${sec}/${test}.html`]?.fuzzy ?? null;

// ── The gate's verdict, composed exactly as inject-wpt-block.mjs does ────────
async function verdict(capPath, refPath, fuzzy) {
  const diff = await diffWebVsRef(capPath, refPath);
  diff.wptFuzzyMatch = checkFuzzyMatch(diff, fuzzy);
  diff.presenceFailed = computePresenceFailed(diff.semanticPresence);
  diff.colorFailed = computeColorFailed(diff.colorDivergent, diff.labDeltaE);
  diff.coverageRatioFailed = computeCoverageRatioFailed(diff.semanticPresence);
  diff.novelInkFailed = computeNovelInkFailed(diff.novelInk);
  diff.degenerateFailed = degenerateVetoFailed(diff.degenerate);
  diff.wptPass = computeWptPass(diff.ssim, diff.wptFuzzyMatch, diff.presenceFailed,
    diff.colorFailed, diff.coverageRatioFailed, novelInkVetoActive(diff.novelInkFailed),
    degenerateVetoActive(diff.degenerateFailed));
  // Keep only the verdict fields (the full block is the gate's business).
  return { ssim: diff.ssim, wptPass: diff.wptPass, presenceFailed: diff.presenceFailed, colorFailed: diff.colorFailed,
    coverageRatioFailed: diff.coverageRatioFailed, labDeltaEMean: diff.labDeltaE?.mean, fuzzyDifferingPixels: diff.fuzzyDifferingPixels };
}

// ── The converter's math, mirrored (ColorConversion.kt P3_TO_XYZ / xyzToSrgb) ─
const P3_TO_XYZ = [[0.4865709486482162, 0.26566769316909306, 0.19821728523436247],
  [0.2289745640697488, 0.6917385218365064, 0.079286914093745],
  [0.0, 0.04511338185890264, 1.043944368900976]];
const gamma = (c) => (c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1 / 2.4) - 0.055);
const clamp01 = (v) => Math.min(1, Math.max(0, v));
// color(display-p3-linear r g b) → sRGB 8-bit, exactly the new arm: matrix, NO decode, clip.
function p3LinearToSrgb8([r, g, b]) {
  const [x, y, z] = P3_TO_XYZ.map((row) => row[0] * r + row[1] * g + row[2] * b);
  const lin = [3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
    -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
    0.0556434 * x - 0.2040259 * y + 1.0572252 * z];
  return lin.map((c) => Math.round(clamp01(gamma(c)) * 255));
}
// The six VERBATIM `.test` values (tools/wpt/css/css-color/display-p3-linear-00N.html).
const P3 = { '001': [0.0383, 0.2087, 0.0156], '002': [0, 0, 0], '003': [1, 1, 1],
  '004': [0, 1, 0], '005': [1, 1, 0.0895], '006': [0.183382, 0.245634, 0.082317] };

const readPng = (p) => PNG.sync.read(fs.readFileSync(p));
const px = (img, x, y) => { const o = (y * img.width + x) * 4; return [img.data[o], img.data[o + 1], img.data[o + 2]]; };
// Bounding box of pixels where two same-size images disagree by > 40 in any channel.
function diffBox(a, b) {
  let x0 = Infinity, y0 = Infinity, x1 = -1, y1 = -1;
  for (let y = 0; y < Math.min(a.height, b.height); y++) for (let x = 0; x < Math.min(a.width, b.width); x++) {
    const p = px(a, x, y), q = px(b, x, y);
    if (Math.max(Math.abs(p[0] - q[0]), Math.abs(p[1] - q[1]), Math.abs(p[2] - q[2])) > 40) { x0 = Math.min(x0, x); y0 = Math.min(y0, y); x1 = Math.max(x1, x); y1 = Math.max(y1, y); }
  }
  return x1 < 0 ? null : { x: x0, y: y0, w: x1 - x0 + 1, h: y1 - y0 + 1 };
}
const results = { fa: [], fb: [] };

// ── F-A ──────────────────────────────────────────────────────────────────────
for (const n of Object.keys(P3)) {
  const test = `display-p3-linear-${n}`;
  const refPath = path.join(REFS, 'css-color', `${test}.png`);
  const ref = readPng(refPath);
  const predicted = p3LinearToSrgb8(P3[n]);
  const fuzzy = fuzzyOf('css-color', test);
  // Locate the box ONCE on the web capture (ref-identical text), reuse below.
  const webCap = readPng(path.join(RUN, 'css-color', CAP_DIRS.web, `wpt__css-color__${test}.png`));
  const box = diffBox(webCap, ref);
  const refCentre = box ? px(ref, Math.floor(box.x + box.w / 2), Math.floor(box.y + box.h / 2)) : null;
  for (const [plat, dir] of Object.entries(CAP_DIRS)) {
    const capPath = path.join(RUN, 'css-color', dir, `wpt__css-color__${test}.png`);
    if (!fs.existsSync(capPath)) { results.fa.push({ test, plat, missing: true }); continue; }
    const cap = readPng(capPath);
    // Is THIS capture uniform (unpainted canvas) inside the web-located box?
    const distinct = new Set();
    if (box) for (let y = box.y; y < box.y + box.h; y++) for (let x = box.x; x < box.x + box.w; x++) distinct.add(px(cap, x, y).join(','));
    // Paint the predicted colour over the box in a copy of the capture.
    const sim = new PNG({ width: cap.width, height: cap.height }); cap.data.copy(sim.data);
    if (box) for (let y = box.y; y < box.y + box.h; y++) for (let x = box.x; x < box.x + box.w; x++) { const o = (y * sim.width + x) * 4; sim.data[o] = predicted[0]; sim.data[o + 1] = predicted[1]; sim.data[o + 2] = predicted[2]; }
    const simPath = path.join(OUT, `fa__${test}__${plat}.png`);
    fs.writeFileSync(simPath, PNG.sync.write(sim));
    results.fa.push({ test, plat, predictedSrgb8: predicted, box, capDistinctColoursInBox: distinct.size,
      capColourInBox: box ? [...distinct][0] : null, refCentre,
      before: await verdict(capPath, refPath, fuzzy), after: await verdict(simPath, refPath, fuzzy) });
  }
}

// ── F-B ──────────────────────────────────────────────────────────────────────
// The four targets (lightpink backdrop, ref ring lightpink) and the at-risk
// column-span (pink backdrop, frozen ref ring WHITE — the erased-ref class).
const FB = { 'fractional-box-with-shadow-new': [255, 182, 193], 'fractional-box-with-shadow-old': [255, 182, 193],
  'fractional-box-with-overflow-children-new': [255, 182, 193], 'fractional-box-with-overflow-children-old': [255, 182, 193],
  'column-span-during-transition-doesnt-skip': [255, 192, 203] };
for (const [test, bg] of Object.entries(FB)) {
  const refPath = path.join(REFS, 'css-view-transitions', `${test}.png`);
  const fuzzy = fuzzyOf('css-view-transitions', test);
  for (const [plat, dir] of Object.entries(CAP_DIRS)) {
    const capPath = path.join(RUN, 'css-view-transitions', dir, `wpt__css-view-transitions__${test}.png`);
    if (!fs.existsSync(capPath)) { results.fb.push({ test, plat, missing: true }); continue; }
    const cap = readPng(capPath);
    const sim = new PNG({ width: cap.width, height: cap.height }); cap.data.copy(sim.data);
    // Every pure-white pixel is canvas (the boxes are green / darkgreen); recolour it.
    let recoloured = 0;
    for (let o = 0; o < sim.data.length; o += 4) {
      if (sim.data[o] === 255 && sim.data[o + 1] === 255 && sim.data[o + 2] === 255) { sim.data[o] = bg[0]; sim.data[o + 1] = bg[1]; sim.data[o + 2] = bg[2]; recoloured++; }
    }
    const simPath = path.join(OUT, `fb__${test}__${plat}.png`);
    fs.writeFileSync(simPath, PNG.sync.write(sim));
    results.fb.push({ test, plat, backdrop: bg, recolouredPx: recoloured, totalPx: cap.width * cap.height,
      before: await verdict(capPath, refPath, fuzzy), after: await verdict(simPath, refPath, fuzzy) });
  }
}

fs.writeFileSync(path.join(HERE, 'png-replay.json'), JSON.stringify(results, null, 1) + '\n');
const fmt = (d) => d ? `${d.wptPass ? 'P' : 'f'} ${d.ssim}${d.colorFailed ? ' C' : ''}${d.coverageRatioFailed ? ' R' : ''}${d.presenceFailed ? ' Pr' : ''}` : '-';
console.log('F-A replay (before → after; predicted sRGB vs ref centre; the web-located box):');
for (const r of results.fa) console.log(`  ${r.test} ${r.plat.padEnd(7)} ${fmt(r.before)} → ${fmt(r.after)}  predicted ${JSON.stringify(r.predictedSrgb8)} ref ${JSON.stringify(r.refCentre)} box ${JSON.stringify(r.box)} capInBox ${r.capColourInBox} (${r.capDistinctColoursInBox} colours)`);
console.log('F-B replay (before → after; white canvas → backdrop):');
for (const r of results.fb) console.log(`  ${r.test} ${r.plat.padEnd(7)} ${fmt(r.before)} → ${fmt(r.after)}  recoloured ${r.recolouredPx}/${r.totalPx} ΔE ${r.before.labDeltaEMean}→${r.after.labDeltaEMean}`);

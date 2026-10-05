#!/usr/bin/env node
// wave-52 lane L5 (extractor-cascade), FIX PASS — the F-E PNG replay the
// skeptic found missing (skeptic.md D1): what `seam-6-FE-li.patch` ALONE does
// to the cssom pictures, versus F-E landed WITH lane L6's seam-3/seam-4.
//
// F-E removes the 100×100 placeholder from the empty cssom `<li>`. The
// runtimes then render an EMPTY inside item; three layouts are simulated on
// the wave51-fix capture by MOVING its marker ink bands (glyphs untouched):
//   feAlone   natives, HEAD renderer: the inside marker is a zero-size overlay
//             (ComponentRenderer.kt RenderListItemMarker / ComponentRenderer
//             .swift insideOverlay — ".overlay never participates in its
//             host's sizing"), so each 0-tall item paints its marker at the
//             SAME y: a list's three bands are composited (darkest pixel)
//             onto its first band. Measured by lane L6 on Catalyst
//             (wave52-counters-and-lists/seam-4.verify.log): ONE band
//             [(16, 28)] where the three rows were. A second list follows a
//             0-tall first list after the same inter-list gap as today.
//   feSeam34  natives WITH L6 seam-3/seam-4 (ListMarkerEmptyItem → Row /
//             HStack): one marker line per item, pitch 16 px (the Catalyst
//             raster: [(16,27),(32,43),(48,59)]). Android pitch is NOT
//             measured (no JVM raster); the iOS pitch is assumed.
//   feWeb     web: an empty `display: list-item` with an inside marker is one
//             line box (Chrome also drew the ref), so rows at the ref's pitch.
// Each prediction is scored with the gate's own composed scorer
// (`diffComposedVsRef`: SSIM + presence/colour/coverage vetoes → wptPass);
// `today` re-scores the untouched capture as the apparatus sanity check.
//
// Usage (repo root): node fe-native-replay.mjs <HEAD copy of inject-wpt-block.mjs> > fe-native-replay.json
import { readFileSync, writeFileSync, mkdtempSync, mkdirSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

// Repo root from this file's location (results/<lane>/ is four levels down).
const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '..', '..', '..', '..');
// pngjs from the workspace root, the same decoder the scorer uses.
const { PNG } = createRequire(join(REPO, 'package.json'))('pngjs');
// The scorer: a HEAD copy (the shared tree's carries another lane's edit).
const SCORER = resolve(process.argv[2] ?? join(REPO, 'tools/titan/inject-wpt-block.mjs'));
const { diffComposedVsRef, safe } = await import(SCORER);
// The run of record and the one section every F-E carrier lives in.
const SEC = 'css-counter-styles';
const SDIR = join(REPO, 'tools/titan/runs/wave51-fix/sections', SEC);
const RESULTS = JSON.parse(readFileSync(join(SDIR, 'manifest.json'), 'utf8')).wpt.results;
// Scratch dir for predicted PNGs (each scenario/platform gets its own dir,
// because diffComposedVsRef looks the capture up by `<safe(testKey)>.png`).
const TMP = mkdtempSync(join(tmpdir(), 'l5-fe-replay-'));
// Capture directory per platform, as the gate lays them out.
const DIRS = { web: 'screenshots', ios: 'ios-screenshots', android: 'android-screenshots' };

// Ink row: any pixel dark on all three channels (black glyphs on white).
const inkRow = (img, y) => { for (let x = 0; x < img.width; x++) { const i = (y * img.width + x) * 4; if (img.data[i] < 160 && img.data[i + 1] < 160 && img.data[i + 2] < 160) return true; } return false; };
// Maximal runs of ink rows = the marker bands, top to bottom.
function bands(img) {
  const out = []; let top = null;
  for (let y = 0; y < img.height; y++) { if (inkRow(img, y)) { if (top === null) top = y; } else if (top !== null) { out.push({ top, bottom: y - 1 }); top = null; } }
  if (top !== null) out.push({ top, bottom: img.height - 1 });
  return out;
}
// Crop margin around a band: anti-aliased glyph edges are lighter than the
// ink threshold, so 2 rows either side travel with the band.
const PAD = 2;
// Copy the rows of each band out of the capture BEFORE anything is erased.
const cropRows = (img, b) => { const y0 = Math.max(0, b.top - PAD), y1 = Math.min(img.height - 1, b.bottom + PAD); return { y0, data: Buffer.from(img.data.subarray(y0 * img.width * 4, (y1 + 1) * img.width * 4)) }; };
// Paint a band's rows white (the ink leaves its old position).
const clearRows = (img, b) => { for (let y = Math.max(0, b.top - PAD); y <= Math.min(img.height - 1, b.bottom + PAD); y++) img.data.fill(255, y * img.width * 4, (y + 1) * img.width * 4); };
// Composite a crop at a new top (darkest pixel wins: opaque black text drawn
// over other black text on white — the overlay's overlapping glyphs).
function stamp(img, crop, newTop) {
  const rows = crop.data.length / (img.width * 4);
  for (let r = 0; r < rows; r++) {
    const y = newTop - PAD + r; if (y < 0 || y >= img.height) continue;
    for (let x = 0; x < img.width * 4; x++) { const i = y * img.width * 4 + x; img.data[i] = Math.min(img.data[i], crop.data[r * img.width * 4 + x]); }
  }
}
// Move every band to the y `place(listIndex, itemIndex, firstTop)` returns.
// `perList` = items per list (3 in every cssom test); bands are grouped in
// document order. Returns the new band tops for the record.
function relayout(img, place, perList = 3) {
  const bs = bands(img); const crops = bs.map((b) => cropRows(img, b));
  for (const b of bs) clearRows(img, b);
  const tops = [];
  bs.forEach((b, k) => { const li = Math.floor(k / perList), it = k % perList; const t = place(li, it, bs[0].top); stamp(img, crops[k], t); tops.push(t); });
  return { before: bs.map((b) => b.top), after: tops };
}
// The inter-list gap today = list 2's first band − (list 1's first band +
// 3 × 100 px placeholders); measured 16 px on all three platforms.
const GAP = 16;
// Scenario geometry (list 2 begins after list 1's new height + the gap).
const SCENARIOS = {
  // Zero-size overlay: every item of a list paints at the list's first y;
  // the 0-tall list 1 puts list 2 one gap below it.
  feAlone: (li, it, first) => first + li * GAP,
  // Sensitivity: the same collapse with the gap doubled (no margin collapse).
  feAloneGap32: (li, it, first) => first + li * 2 * GAP,
  // L6 seam-3/4: one 16-px marker line per item (Catalyst raster pitch).
  feSeam34: (li, it, first) => first + li * (3 * 16 + GAP) + it * 16,
  // Web: the ref's own pitch — 20 px rows, list 2 at +76 (ref bands 36/112).
  feWeb: (li, it, first) => first + li * (3 * 20 + GAP) + it * 20,
};
// Which scenarios each platform is predicted under.
const PLAN = { web: ['feWeb'], ios: ['feAlone', 'feAloneGap32', 'feSeam34'], android: ['feAlone', 'feAloneGap32', 'feSeam34'] };

// The F-E carriers (the static differential's 16 docs, census.json).
// override-in-shadow-dom is post-load extracted in the gate (key-order-only
// change there), so its capture already has 20-px rows: replayed as `today` only.
const tests = Object.keys(RESULTS).filter((k) => /\/cssom\//.test(k)).sort();
// Score one PNG through the gate's composed path, keeping the verdict fields.
async function score(dir, testKey, refPng, fuzzy) {
  const d = await diffComposedVsRef({ platformDir: dir, testKey, refPng, fuzzy });
  // capInk/refInk = the semanticPresence ink % pair the coverage-ratio veto
  // compares (fails when max/min > WPT_COVERAGE_RATIO_MAX = 2 at HEAD).
  const sp = d.semanticPresence ?? {};
  return { ssim: d.ssim, pass: d.wptPass === true, colorFailed: d.colorFailed, coverageRatioFailed: d.coverageRatioFailed, presenceFailed: d.presenceFailed, capInkPct: sp.aCoveragePct, refInkPct: sp.bCoveragePct };
}
const out = [];
for (const key of tests) {
  // `css/css-counter-styles/cssom/x.html` → stem `cssom__x`, test key `wpt__css-counter-styles__cssom__x`.
  const stem = key.replace(`css/${SEC}/`, '').replace(/\.html$/, '').replaceAll('/', '__');
  const testKey = `wpt__${SEC}__${stem}`;
  const r = RESULTS[key]; const refPng = join(REPO, r.browserRef.path);
  for (const [platform, dir] of Object.entries(DIRS)) {
    const diffKey = `${platform}-ref`; const was = r.browserRef.diffs[diffKey];
    const capDir = join(SDIR, dir);
    // Apparatus check: the untouched capture must reproduce the manifest cell.
    const today = await score(capDir, testKey, refPng, r.fuzzy);
    const row = { cell: `${SEC}/${stem.replace('__', '/')} ${platform}`, wave51fix: `${was.wptPass ? 'P' : 'f'} ${was.ssim}`, today, predicted: {} };
    for (const sc of PLAN[platform]) {
      const img = PNG.sync.read(readFileSync(join(capDir, `${safe(testKey)}.png`)));
      const geometry = relayout(img, SCENARIOS[sc]);
      const outDir = join(TMP, `${sc}-${platform}`); mkdirSync(outDir, { recursive: true });
      writeFileSync(join(outDir, `${safe(testKey)}.png`), PNG.sync.write(img));
      row.predicted[sc] = { ...(await score(outDir, testKey, refPng, r.fuzzy)), bandTops: geometry.after };
    }
    out.push(row);
  }
}
// The 18 native cells the skeptic names: native cells that PASS in wave51-fix.
const nativeP = out.filter((x) => x.cell.match(/ (ios|android)$/) && x.wave51fix.startsWith('P'));
const summary = {
  apparatusReproduces: out.every((x) => x.today.pass === x.wave51fix.startsWith('P') && Math.abs(x.today.ssim - Number(x.wave51fix.split(' ')[1])) < 0.0006),
  nativePassingCells: nativeP.length,
  feAloneLost: nativeP.filter((x) => !x.predicted.feAlone.pass).map((x) => x.cell),
  feAloneGap32Lost: nativeP.filter((x) => !x.predicted.feAloneGap32.pass).map((x) => x.cell),
  feSeam34Lost: nativeP.filter((x) => !x.predicted.feSeam34.pass).map((x) => x.cell),
  webLost: out.filter((x) => x.cell.endsWith(' web') && x.wave51fix.startsWith('P') && !x.predicted.feWeb.pass).map((x) => x.cell),
};
console.log(JSON.stringify({ scorer: SCORER, scratch: TMP, summary, replays: out }, null, 1));

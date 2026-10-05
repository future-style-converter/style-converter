#!/usr/bin/env node
// tools/titan/results/wave52-web-tail-colour-vt/census.mjs — wave-52 lane L1
// (web-tail-colour-vt) CORPUS CENSUS for F-A (linear colour spaces) and F-B
// (the bail-path `::view-transition` backdrop stamp). Read-only over the
// evidence run of record; writes census.json beside this file.
//
//   node tools/titan/results/wave52-web-tail-colour-vt/census.mjs
//
// Sources: the 1435 per-test IR docs and the 30 manifests of
// tools/titan/runs/wave51-fix, the 48 css-view-transitions test sources under
// tools/wpt, that section's extract.log (the `[vt-bake: …]` note per test) and
// the frozen ref PNGs (the ring colour each ref actually carries).
//
// FIX PASS (2026-10-05, skeptic must-fix 1). The F-B "stamp" column used to
// be PREDICTED from authorship (`backdrop && solve-class bail`) and claimed 9
// stamps; the drive stamps on the MEASURED settled ring, so authorship is only
// a CANDIDATE filter. The stamp verdict now comes from bake-probe.json (the
// real drive over every solve-class bail — run bake-probe.mjs first); a test
// the probe did not drive reads `unmeasured`, never a guess.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
// The module under change supplies its own classifier and ring sampler, so the
// census predicts with the SAME predicates the drive will run — not a copy.
import { isSolveClassBail, frameRingColor } from '../../view-transition-bake.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const RUN = path.join(REPO, 'tools', 'titan', 'runs', 'wave51-fix', 'sections');
const WPT = process.env.WPT_DIR ?? path.join(REPO, 'tools', 'wpt');
const REFS = path.join(WPT, 'refs', '9b5435e55e0b54a6cd09c1c563861eb3c999cef1', 'white-black-ink-font-lh-imgpad-htmlpins');

// ── Cells: the scorer's own reading (score-gate.mjs isScored/isPass) ─────────
const PLATFORMS = { 'web-ref': 'web', 'ios-ref': 'ios', 'android-ref': 'android' };
const cells = new Map();                          // "css/<sec>/<test>.html" → { web: 'P 0.9' … }
for (const sec of fs.readdirSync(RUN)) {
  const mp = path.join(RUN, sec, 'manifest.json');
  if (!fs.existsSync(mp)) continue;
  const results = JSON.parse(fs.readFileSync(mp, 'utf8')).wpt?.results ?? {};
  for (const [test, r] of Object.entries(results)) {
    const row = {};
    for (const [k, p] of Object.entries(PLATFORMS)) {
      const x = r.browserRef?.diffs?.[k];
      // Unscored (no ssim or scoreExcluded) prints as '-' so it is never mistaken for a verdict.
      row[p] = (x && typeof x.ssim === 'number' && !x.scoreExcluded)
        ? `${x.wptPass === true ? 'P' : 'f'} ${x.ssim}` : '-';
    }
    cells.set(test, row);
  }
}

// ── F-A: every color() declaration in the IR, by colour space ────────────────
// The parser table AFTER this lane (ColorParser.kt `when (colorSpace)`); any
// space outside it still resolves `srgb: null` and is listed as unresolved.
const PARSER_TABLE = new Set(['srgb', 'srgb-linear', 'display-p3', 'a98-rgb', 'rec2020',
  'xyz', 'xyz-d65', 'xyz-d50', 'display-p3-linear', 'a98-rgb-linear', 'rec2020-linear']);
const LINEAR_ARMS = new Set(['display-p3-linear', 'a98-rgb-linear', 'rec2020-linear']);
const bySpace = {};                               // colorSpace → { decls, withSrgb, tests:Set }
let irDocs = 0;
// Depth-first over every object; a hit is `{type:'color', colorSpace}`, and its
// parent (`data`) is where the converter puts the `srgb` sibling.
function walk(node, parent, test) {
  if (!node || typeof node !== 'object') return;
  if (node.type === 'color' && typeof node.colorSpace === 'string') {
    const s = (bySpace[node.colorSpace] ??= { decls: 0, withSrgb: 0, tests: new Set() });
    s.decls++; s.tests.add(test);
    if (parent && parent.srgb) s.withSrgb++;
  }
  for (const v of Array.isArray(node) ? node : Object.values(node)) walk(v, node, test);
}
for (const sec of fs.readdirSync(RUN)) {
  const dir = path.join(RUN, sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir)) {
    if (!f.endsWith('.json')) continue;
    irDocs++;
    // `wpt__<sec>__<path with __ for />.json` → the manifest key `css/<sec>/<path>.html`.
    const rel = 'css/' + f.replace(/^wpt__/, '').replace(/\.json$/, '').split('__').join('/') + '.html';
    walk(JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')), null, rel);
  }
}
const colorSpaces = Object.fromEntries(Object.entries(bySpace).sort().map(([k, v]) => [k, {
  decls: v.decls, withSrgb: v.withSrgb, tests: [...v.tests].sort(),
  // The lane's own claim, per space: newly resolved by F-A, already resolved, or still null.
  status: LINEAR_ARMS.has(k) ? 'RESOLVED BY F-A' : PARSER_TABLE.has(k) ? 'already resolved' : 'still null (runtime-dependent)',
}]));
const faTests = [...new Set(Object.entries(bySpace).filter(([k]) => LINEAR_ARMS.has(k)).flatMap(([, v]) => [...v.tests]))]
  .sort().map((t) => ({ test: t, cells: cells.get(t) ?? null }));

// ── F-B: the 48 css-view-transitions sources × the bake note × the ref ring ──
// The author-backdrop idiom `::view-transition { background: <colour> }`; the
// colour is kept as written, and the few named colours the corpus uses are
// mapped to the rgb() spelling frameRingColor emits so ring == backdrop is a
// string comparison (unknown names stay unmapped and are reported as such).
const NAMED = { lightpink: 'rgb(255, 182, 193)', pink: 'rgb(255, 192, 203)', rebeccapurple: 'rgb(102, 51, 153)',
  green: 'rgb(0, 128, 0)', red: 'rgb(255, 0, 0)', blue: 'rgb(0, 0, 255)', white: 'rgb(255, 255, 255)',
  black: 'rgb(0, 0, 0)', yellow: 'rgb(255, 255, 0)', lightblue: 'rgb(173, 216, 230)', grey: 'rgb(128, 128, 128)',
  gray: 'rgb(128, 128, 128)', orange: 'rgb(255, 165, 0)', purple: 'rgb(128, 0, 128)' };
const toRgb = (c) => NAMED[c.trim().toLowerCase()] ?? (/^rgb\(/.test(c.trim()) ? c.trim() : null);
const vtSec = path.join(RUN, 'css-view-transitions');
const vtTests = fs.readFileSync(path.join(vtSec, 'tests.list'), 'utf8').split('\n').map((l) => l.trim()).filter(Boolean);
const log = fs.readFileSync(path.join(vtSec, 'extract.log'), 'utf8').split('\n');
// The MEASURED drive (bake-probe.json, `{ summary, drives }`): rel → the ring
// the live settled page read and whether the stamp was written.
const probePath = path.join(HERE, 'bake-probe.json');
const probe = fs.existsSync(probePath) ? JSON.parse(fs.readFileSync(probePath, 'utf8')) : null;
const measured = new Map((probe?.drives ?? []).map((d) => [d.rel, d]));
const vt = [];
for (const test of vtTests) {
  const rel = test.startsWith('css/') ? test : `css/${test}`;
  const src = fs.readFileSync(path.join(WPT, rel), 'utf8');
  // Every `::view-transition { … }` block (the bare pseudo, NOT -group/-old/-new).
  let backdrop = null;
  for (const m of src.matchAll(/::view-transition\s*\{([^}]*)\}/g)) {
    const bg = /(?:^|;|\s)background(?:-color)?\s*:\s*([^;!]+)/.exec(m[1]);
    if (bg) backdrop = bg[1].trim();
  }
  // The bake note the section run logged for this test, if any.
  const line = log.find((l) => l.includes(rel) && l.includes('[vt-bake:'));
  const note = line ? /\[vt-bake: (\w+)(?: — ([^\]]*))?\]/.exec(line) : null;
  const status = note?.[1] ?? null, reason = note?.[2] ?? null;
  // How many body-root components the static extraction minted (0 → the stamp MINTS one).
  const irPath = path.join(vtSec, 'per-test-ir', 'wpt__css-view-transitions__' + path.basename(rel, '.html') + '.json');
  const bodyRoots = fs.existsSync(irPath)
    ? JSON.parse(fs.readFileSync(irPath, 'utf8')).components.filter((c) => c.meta?.role === 'body-root').length : null;
  // The ring the FROZEN ref actually carries (null = white or non-uniform), plus
  // its raw top-left pixel so a null ring reads as WHITE vs non-uniform here.
  const refPng = path.join(REFS, 'css-view-transitions', path.basename(rel, '.html') + '.png');
  const refRing = fs.existsSync(refPng) ? frameRingColor(fs.readFileSync(refPng)) : 'no-ref';
  const refCorner = fs.existsSync(refPng)
    ? (() => { const p = PNG.sync.read(fs.readFileSync(refPng)); return `rgb(${p.data[0]}, ${p.data[1]}, ${p.data[2]})`; })() : null;
  // The solve class, from the same classifier the drive gates the sample on.
  const solveBail = status === 'bailed' && isSolveClassBail(reason);
  // AUTHORSHIP is only a candidate filter: a group snapshot over the ring, or
  // a backdrop that never reaches the settled page, reads non-uniform/white.
  const stampCandidate = !!backdrop && solveBail;
  // The verdict that counts: what the real drive measured (bake-probe.json).
  const d = measured.get(rel);
  const stampMeasured = solveBail ? (d ? d.stamped === true : 'unmeasured') : false;
  const ringMeasured = d ? d.ringMeasured : null;
  const backdropRgb = backdrop ? toRgb(backdrop) : null;
  // Direction against TODAY's frozen ref, for MEASURED stamps only.
  let direction = 'none';
  if (stampMeasured === 'unmeasured') direction = 'unmeasured — run bake-probe.mjs';
  else if (stampMeasured === true) {
    if (refRing === ringMeasured) direction = 'TOWARD ref (ref ring == measured ring)';
    else if (refRing === null) direction = 'AWAY from ref (ref ring white or non-uniform)';
    else direction = `differs (ref ring ${refRing} vs measured ${ringMeasured})`;
  } else if (stampCandidate) direction = 'none — candidate, ring measured null (negative control)';
  vt.push({ test: rel, backdrop, backdropRgb, bake: status ? `${status}${reason ? ' — ' + reason : ''}` : null,
    solveClassBail: solveBail, bodyRoots, refRing, refCorner, stampCandidate, stampMeasured,
    ringSampled: d ? d.ringSampled : null, ringMeasured, direction, cells: cells.get(rel) ?? null });
}
const authors = vt.filter((t) => t.backdrop);
const candidates = vt.filter((t) => t.stampCandidate);
const stamps = vt.filter((t) => t.stampMeasured === true);
const unmeasured = vt.filter((t) => t.stampMeasured === 'unmeasured');

// ── Write + print ────────────────────────────────────────────────────────────
const out = {
  generatedAt: new Date().toISOString(), run: 'wave51-fix', irDocs,
  fa: { colorSpaces, linearSpaceTests: faTests },
  fb: { sources: vtTests.length, authors: authors.length, solveClassBails: vt.filter((t) => t.solveClassBail).length,
    stampCandidates: candidates.length, stampsMeasured: stamps.length, unmeasured: unmeasured.length,
    // Cells a measured stamp can move: 3 platforms per stamped test, scored ones only.
    stampCells: stamps.reduce((n, t) => n + Object.values(t.cells ?? {}).filter((c) => c !== '-').length, 0),
    probe: probe?.summary ?? null, tests: vt },
};
fs.writeFileSync(path.join(HERE, 'census.json'), JSON.stringify(out, null, 1) + '\n');
console.log(`IR docs walked: ${irDocs}`);
console.log('F-A colour spaces in color():');
for (const [k, v] of Object.entries(colorSpaces)) console.log(`  ${k.padEnd(20)} decls ${String(v.decls).padStart(3)} withSrgb ${String(v.withSrgb).padStart(3)} tests ${v.tests.length}  ${v.status}`);
console.log(`F-A tests carrying a linear arm: ${faTests.length}`);
for (const t of faTests) console.log(`  ${t.test}  ${JSON.stringify(t.cells)}`);
console.log(`F-B sources ${vtTests.length} · backdrop authors ${authors.length} · solve-class bails ${out.fb.solveClassBails}` +
  ` · stamp candidates (authorship) ${candidates.length} · STAMPS MEASURED ${stamps.length} (${out.fb.stampCells} cells)` +
  ` · unmeasured ${unmeasured.length}`);
// Every solve-class bail, measured verdict first — the negative controls are as load-bearing as the stamps.
for (const t of vt.filter((x) => x.solveClassBail)) {
  console.log(`  ${t.stampMeasured === true ? 'STAMP' : t.stampCandidate ? 'ctrl ' : '  -  '} ${t.test.replace('css/css-view-transitions/', '')}` +
    `\n     backdrop ${t.backdrop ?? '-'} · sampled ${t.ringSampled} · ring ${t.ringMeasured} · ref ring ${t.refRing} · body-roots ${t.bodyRoots}` +
    ` · ${t.direction} · ${JSON.stringify(t.cells)}`);
}

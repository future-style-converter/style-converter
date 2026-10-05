#!/usr/bin/env node
// pair-gate-proxy.mjs — lane L11 fix pass 2 (skeptic RV-M1): the device-free
// PROXY of the closing gate's cross-platform pair gate + spec oracle for
// fixtures/combinations/all-then-color.json, re-built from the skeptic's recipe
// (skeptic.md "RV-M1": CSS-truth captures; web keeps the UA initial face that
// `all: initial` gives it — Chromium: Times; "iOS" re-declares -apple-system and
// "Android" re-declares Roboto/Arial AFTER `all`, on the TEXT nodes only) and
// judged by the REAL tools/visual/compare-screenshots.mjs, exactly as
// test-all.sh's gate-only child runs it (cwd tools/visual, `--input <fixture>`,
// no --baseline: the fixture has no committed baseline).
//
// Why a proxy: the build lanes are device-free. The face split is the ONLY
// cross-platform difference this proxy models (box/fill are CSS truth on all
// three), so it answers one question: does a text row's UA-initial face, alone,
// redden the pair gate? Real native rasterisation differs from Chromium's at
// least as much, so a FAIL here is a real fail and a PASS is optimistic by the
// glyph-raster delta (stated in _note.md, never hidden).
//
// Fidelity beyond the skeptic's first proxy (both make it CLOSER to the gate):
//   - the legacy flatten() walk is modelled — one capture per component in
//     PRE-ORDER (roots AND their children standalone: `reset`, `span`), the
//     same list CaptureGallery.flatten / iOS flatten() / Android
//     flattenComponents() emit (no context-creating parent here — asserted);
//   - the harness label chrome is stamped where the real harness draws it
//     (leaf with no `_text`): tools/visual/block-font.json 5x7 cells at
//     (8 + 6i, 6), composited byte (174,174,180) — identical ×3 by contract
//     (docs/DYNAMIC_CAPTURE.md §5), so it can never create a divergence.
//
// EXECUTED MUTATIONS (mutate-fixture-proxy.sh, 2026-10-05, in place on the
// fixture, restore byte-exact; mutations.log "FIX PASS 2" is gitignored, so the
// result is recorded HERE and in pair-gate-proxy.json):
//   P-M1  the HEAD b35e203a fixture (span `_text` "Hamburg 123" restored) →
//         required exit 4 → GOT 4 (4 unexpected: 004 + 005, iOS-web 0.9404,
//         Android-web 0.9420 — the skeptic's RV-M1 red: the proxy CAN fail);
//   P-M2  span `display: block` moved BEFORE `all` → required 6 → GOT 6 (6
//         violations: ATC_InitialUnderRedParent fill rgb(52,152,219), box 0x0 on
//         all three — the child collapses to an empty inline box, BLUE dominates);
//   P-M3  span emptied (the pre-wave-52 order-blind drop) → required 6 → GOT 6;
//   P-M4  PropsThenAll child `all: revert` → `all: initial`, `--model native` →
//         required 4 → GOT 4 (003_reset iOS-web / Android-web SSIM 0.6627, Δpx
//         48.39 %: frame 390x32 web vs 390x62 natives — the S2 split below).
//   Fixture sha256 before ae4867a8947f63072779808290a5b9b40f79cdeffec7d796427c512a27a12e6e
//   = after: IDENTICAL (final run of the script; earlier runs on the intermediate
//   bytes 0ee2eb5c… / 8deecdf6… restored IDENTICAL too). Un-mutated runs: HEAD
//   fixture (sha 87e19d8f…) exit 4; textless-only fixture (0ee2eb5c…) css exit 0
//   but native model exit 4; the landed fixture (ae4867a8…) exit 0 under BOTH
//   models — 18 pairs / 0 unexpected, 12 oracle checks / 0 violations.
// Usage: node pair-gate-proxy.mjs --tag <t> [--fixture <repo-relative>] [--model css|native] [--expect-exit <n>]
//        PROXY_SCRATCH=<dir> sets where captures / the report go (default: os tmpdir).
// Writes ONE record per tag into pair-gate-proxy.json beside itself (read-modify-write).
import { spawnSync } from 'node:child_process';                  // the REAL comparator runs as a child, like test-all.sh
import { existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';   // capture dirs + the record
import { createHash } from 'node:crypto';                          // fixture sha256 into every record (which bytes were judged)
import { tmpdir } from 'node:os';                                  // default scratch root when PROXY_SCRATCH is unset
import { dirname, join } from 'node:path';                         // path assembly only
import { fileURLToPath } from 'node:url';                          // ESM __dirname
import { createRequire } from 'node:module';                       // reach the workspace-root puppeteer / pngjs

const HERE = dirname(fileURLToPath(import.meta.url));              // this results dir
const ROOT = join(HERE, '..', '..', '..', '..');                   // repo root (results/<lane>/ is 4 deep)
const req = createRequire(join(ROOT, 'tools', 'titan', 'score-gate.mjs'));   // same resolution base as fixture-oracle-probe.mjs
const puppeteer = req('puppeteer');                                // the pinned headless Chromium (the skeptic's renderer)
const { PNG } = req('pngjs');                                      // PNG encode/decode for the label stamp
const arg = (k, d) => (process.argv.includes(k) ? process.argv[process.argv.indexOf(k) + 1] : d);   // `--k v` flags
const TAG = arg('--tag', 'current');                               // record key (one per run)
const FIXTURE_REL = arg('--fixture', 'fixtures/combinations/all-then-color.json');   // the path the gate passes as --input
const EXPECT = arg('--expect-exit', null);                         // optional pass condition for a mutation run
const SCRATCH = join(process.env.PROXY_SCRATCH ?? join(tmpdir(), 'l11-pair-proxy'), TAG);   // per-tag capture root
const fixtureBytes = readFileSync(join(ROOT, FIXTURE_REL));        // exact bytes judged (hashed into the record)
const fixture = JSON.parse(fixtureBytes.toString('utf8'));        // the fixture document
const font = JSON.parse(readFileSync(join(ROOT, 'tools', 'visual', 'block-font.json'), 'utf8'));   // label atlas

// Per-platform text face. web = null: no re-declaration, so `all: initial` leaves
// the UA initial font-family (Chromium's standard face, Times) — what the real
// harness measured (skeptic R4: span-005 / atc_directionsurvives-006 compute
// Times). The natives drop the inherited channel under INITIAL and draw their
// system sans; the proxy writes that face AFTER `all` on text nodes only.
// "iOS" is `system-ui` (SF), NOT a bare `-apple-system`: MEASURED in this pinned
// Chrome/151 headless, a bare `-apple-system` lays "Hamburg 123" @20px out at
// 110.19 px = Times (the fallback — so iOS-web read 1.0000, a proxy that could
// not see the face split), while `system-ui` / `-apple-system, BlinkMacSystemFont`
// give 118.48 px = SF. Roboto is not installed here: "Android" lands on Arial
// (121.19 px) — the skeptic's "Roboto/Arial".
const FACES = { iOS: 'system-ui', Android: 'Roboto, Arial', web: null };
// The harness canvas (CaptureGallery canvasStyle + index.html body font): 390 px,
// border-box, 16 px pad, #1A1A2E ground, clipped, its own layer.
const CANVAS_CSS = "width:390px;box-sizing:border-box;padding:16px;background:#1A1A2E;overflow:hidden;" +
  "transform:translateZ(0);position:relative;font-family:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Oxygen,Ubuntu,sans-serif;";
// Properties that make CaptureGallery.parentCreatesContext / dependsOnBackdrop
// suppress standalone children — the proxy does NOT model that branch, so it
// refuses a fixture that would need it instead of emitting a wrong capture list.
const CONTEXT_KEYS = ['clip-path', 'mask', 'mask-image', 'filter', 'backdrop-filter', 'overflow', 'opacity', 'transform', 'mix-blend-mode', 'isolation'];

/** Pre-order flatten — the legacy capture list (parent first, then children). */
function flatten(components) {
  const out = [];                                                  // [{name, node}] in capture-index order
  const walk = (name, node) => {                                   // depth-first, parent before descendants
    out.push({ name, node });                                      // every component is its own capture
    const kids = Object.entries(node.children ?? {});              // fixture children keep source order
    if (kids.length && CONTEXT_KEYS.some((k) => k in (node.properties ?? {}))) {   // a parent the real flatten would suppress
      throw new Error(`${name}: context-creating parent — the proxy does not model child suppression`);   // fail loud
    }
    for (const [n, c] of kids) walk(n, c);                         // recurse into each child
  };
  for (const [n, c] of Object.entries(components)) walk(n, c);    // roots in fixture order
  return out;                                                      // the flattened capture list
}

// `--model native` (fix pass 2, second finding): the CSS-truth columns above
// cannot see the natives' known gap S2 (skeptic.md — Compose / SwiftUI never
// apply CSS initial values: an `all: initial` box stays a BLOCK and gets the
// harness placeholder floor instead of collapsing inline). This model renders:
//   web     — CSS truth + the harness floors calibrateStyles appends AFTER
//             `...styles` (apps/web-harness/src/sdui/ComponentRenderer.tsx,
//             uncapped branch: min-width = width || 50px, min-height = height
//             || 30px) on every component (decorateStyles runs per component).
//             The floors READ the post-F1 `styles` (StyleBuilder.ts filters
//             the before-`all` declarations out — skeptic RV-N1), so an
//             `all`-LAST box floors at 50x30, never at its dropped 150x50
//             (first native-model run read the raw fixture: web 390x82 — wrong);
//   natives — the F1 reset applied to the OWN list (AllReset.kt /
//             GlobalExtractor.applyingAllReset: the last `all` governs, before
//             it only direction / unicode-bidi survive, `all` itself leaves the
//             list — so no CSS initial value is ever applied), then the 50x30
//             placeholder floor on an axis with no width/min-width (height/
//             min-height) — StyleApplier.placeholderFloorMinSize (Compose) /
//             StyleBuilder.minFloor + MinBoxFloor (SwiftUI), read, not run.
// Default `--model css` is the skeptic's recipe unchanged (the recorded runs).
const MODEL = arg('--model', 'css');                               // css | native
const EXEMPT = new Set(['direction', 'unicode-bidi']);             // css-cascade-4 §3.1: `all` never resets these

/** One component's ordered declarations for one column under the chosen model. */
function decls(props, column) {
  const list = Object.entries(props ?? {});                        // fixture key order = source order (§6.4)
  if (MODEL === 'css') return list;                                // skeptic recipe: CSS truth everywhere
  const last = list.map(([k]) => k).lastIndexOf('all');            // the governing `all` (the LAST one)
  const own = last < 0 ? list                                      // no `all`: the list is untouched
    : [...list.slice(0, last).filter(([k]) => EXEMPT.has(k)), ...list.slice(last + 1).filter(([k]) => k !== 'all')];   // F1 own-list rule
  const val = (k) => own.find(([n]) => n === k)?.[1];              // a post-F1 declared value (web's `styles.x`)
  if (column === 'web') {                                          // CSS truth + calibrateStyles' trailing floors
    const minW = val('min-width') ?? val('width') ?? '50px';       // `styles.minWidth || … || styles.width || '50px'`
    const minH = val('min-height') ?? val('height') ?? '30px';     // the block-axis twin
    return [...list, ['min-width', minW], ['min-height', minH]];   // CSS-equivalent list, floors appended AFTER `all`
  }
  const has = (k) => val(k) !== undefined;                         // declared on the post-reset list?
  const floor = [];                                                // the native placeholder floor, per axis
  if (!has('width') && !has('min-width')) floor.push(['min-width', '50px']);     // SizeConfig.width == nil && minWidth == nil
  if (!has('height') && !has('min-height')) floor.push(['min-height', '30px']);  // the block-axis twin
  return [...own, ...floor];                                       // natives never become inline: a div stays a block
}

/** The fixture subtree re-shaped for one column: properties as ordered pairs. */
function shape(node, column) {
  return {
    properties: decls(node.properties, column),                    // model-specific declaration list
    _text: node._text,                                             // glyphs are model-independent
    children: Object.fromEntries(Object.entries(node.children ?? {}).map(([n, c]) => [n, shape(c, column)])),   // recurse
  };
}

/** Stamp the harness label chrome (DYNAMIC_CAPTURE.md §5) onto a decoded PNG. */
function stampLabel(png, name) {
  const chars = Array.from(name.replace(/_/g, ' ').toUpperCase()).map((c) => (font.glyphs[c] ? c : '-'));   // shared normalize
  const n = Math.min(chars.length, Math.floor((png.width - 16) / font.advance));   // largest n with 8 + 6n ≤ W − 8
  for (let i = 0; i < n; i++) {                                    // one cell per kept char
    font.glyphs[chars[i]].forEach((row, y) => [...row].forEach((bit, x) => {   // 7 rows × 5 cols of '0'/'1'
      if (bit !== '1') return;                                     // unset bit: ground stays
      const px = 8 + i * font.advance + x, py = 6 + y;             // block origin (8, 6), integer coords
      const o = (py * png.width + px) * 4;                         // RGBA byte offset
      png.data[o] = 174; png.data[o + 1] = 174; png.data[o + 2] = 180; png.data[o + 3] = 255;   // the composited contract byte
    }));
  }
}

/** Render one capture (a `shape`d subtree as a root) for one platform → PNG buffer. */
async function render(page, name, node, face) {
  await page.setContent('<!doctype html><html><body style="margin:0;background:#1A1A2E"></body></html>');   // clean page per capture
  await page.evaluate((n, f, css) => {                             // build the subtree in-page
    const canvas = Object.assign(document.createElement('div'), { id: 'canvas' });   // the capture frame
    canvas.style.cssText = css;                                    // harness canvas contract
    const build = (c) => {                                         // one <div> per component
      const el = document.createElement('div');                    // generic box (the IR carries no tag here)
      for (const [k, v] of c.properties) el.style.setProperty(k, v);   // per-key, in the model's order (§6.4)
      if (c._text) {                                               // a text node: the only place faces differ
        if (f) el.style.setProperty('font-family', f);             // native face written AFTER `all` (wins, §6.4)
        el.append(c._text);                                        // the glyphs
      }
      for (const ch of Object.values(c.children ?? {})) el.append(build(ch));   // nested children in source order
      return el;                                                   // the built subtree
    };
    canvas.append(build(n));                                       // the capture root
    document.body.append(canvas);                                  // attach for layout
  }, node, face, CANVAS_CSS);
  await page.evaluate(() => document.fonts.ready);                 // faces settled before raster
  const png = PNG.sync.read(await (await page.$('#canvas')).screenshot({ type: 'png' }));   // element crop, DPR 1
  const leaf = Object.keys(node.children ?? {}).length === 0;      // label predicate half 1: no children
  if (leaf && !(typeof node._text === 'string' && node._text.length > 0)) stampLabel(png, name);   // half 2: no text
  return PNG.sync.write(png);                                      // re-encode (untagged sRGB, like the harness)
}

const list = flatten(fixture.components);                          // the capture list for this fixture
rmSync(SCRATCH, { recursive: true, force: true });                 // a stale capture must never be re-judged
const dirs = {};                                                   // platform → capture dir
const browser = await puppeteer.launch({ headless: 'new', args: ['--force-color-profile=srgb'] });   // sRGB raster, as the lane probe
try {
  const page = await browser.newPage();                            // one page, re-filled per capture
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 1 });   // phone-width, 1:1 px
  for (const [platform, face] of Object.entries(FACES)) {          // three proxy columns
    dirs[platform] = join(SCRATCH, platform);                      // per-platform dir (compare's *_SCREENSHOTS_DIR)
    mkdirSync(dirs[platform], { recursive: true });                // create it
    for (const [i, { name, node }] of list.entries()) {            // pre-order index = capture index
      const file = `${String(i + 1).padStart(3, '0')}_${name}.png`;   // the legacy `{NNN}_{Name}.png` contract
      writeFileSync(join(dirs[platform], file), await render(page, name, shape(node, platform), face));   // write the capture
    }
  }
} finally {
  await browser.close();                                           // never leak Chromium
}

// The REAL comparator, gate-only (no --baseline), cwd tools/visual — test-all.sh's call.
const report = join(SCRATCH, 'report');                            // HTML + diffs (scratch, never tools/visual/report)
const manifestOut = join(SCRATCH, 'manifest.json');                // machine-readable result
const run = spawnSync('node', ['compare-screenshots.mjs', '--input', FIXTURE_REL], {
  cwd: join(ROOT, 'tools', 'visual'),                              // same cwd as test-all.sh
  env: { ...process.env, IOS_SCREENSHOTS_DIR: dirs.iOS, ANDROID_SCREENSHOTS_DIR: dirs.Android,
    WEB_SCREENSHOTS_DIR: dirs.web, REPORT_DIR: report, MANIFEST_OUT: manifestOut },   // every write redirected to scratch
  encoding: 'utf8',                                                // text stdout/stderr
});
const m = existsSync(manifestOut) ? JSON.parse(readFileSync(manifestOut, 'utf8')) : null;   // absent on exit 2
const p95 = (pr) => pr?.labDeltaE?.p95 ?? null;                    // ΔE95 (the gate's third metric)
const record = {
  generated: new Date().toISOString(),                             // when
  fixture: FIXTURE_REL,                                            // the --input path
  model: MODEL,                                                    // css (skeptic recipe) | native (S2 model)
  fixtureSha256: createHash('sha256').update(fixtureBytes).digest('hex'),   // which bytes
  captures: list.map(({ name }, i) => `${String(i + 1).padStart(3, '0')}_${name}`),   // the flattened list
  exit: run.status,                                                // the comparator's exit code
  gate: m && { checked: m.crossPlatformGate.checked, unexpected: m.crossPlatformGate.unexpected.map((r) => `${r.component} · ${r.pair} — SSIM ${r.observed?.ssim?.toFixed(4)} · Δpx ${r.observed?.pixelPct?.toFixed(2)}% · ΔE95 ${r.observed?.labDeltaEP95?.toFixed(2)}`) },
  oracle: m?.specOracle && { checked: m.specOracle.checked, violations: m.specOracle.violations.length },   // count only
  pairs: m && Object.fromEntries(m.rows.map((r) => [r.name, Object.fromEntries(Object.entries(r.pairs ?? {}).map(([k, pr]) => [k,
    pr && { ssim: +pr.ssim.toFixed(4), pxPct: pr.pixelMismatchedPct, dE95: p95(pr) === null ? null : +p95(pr).toFixed(2) }]))])),
  summaryLines: (run.stdout + run.stderr).split('\n').filter((l) => /cross-platform gate|spec oracle|✗|^\s+· /.test(l)),   // the gate's own words
};
const OUT = join(HERE, 'pair-gate-proxy.json');                    // the committed record
const all = existsSync(OUT) ? JSON.parse(readFileSync(OUT, 'utf8')) : { runs: {} };   // read-modify-write
all.runs[TAG] = record;                                            // one record per tag
writeFileSync(OUT, JSON.stringify(all, null, 1) + '\n');           // persist
console.log(`[${TAG}] exit ${record.exit} · gate ${JSON.stringify(record.gate)} · oracle ${JSON.stringify(record.oracle)}`);   // headline
for (const [row, ps] of Object.entries(record.pairs ?? {})) console.log(`  ${row.padEnd(38)} ${JSON.stringify(ps)}`);   // per-row pairs
for (const l of record.summaryLines) console.log(`  | ${l}`);      // the comparator's verdict lines
process.exit(EXPECT === null ? (record.exit ?? 2) : (String(record.exit) === EXPECT ? 0 : 1));   // mutation runs pass on the EXPECTED exit

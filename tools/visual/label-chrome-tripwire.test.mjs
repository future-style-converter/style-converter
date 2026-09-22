#!/usr/bin/env node
// label-chrome-tripwire.test.mjs — the POSITIONAL cross-platform tripwire for the
// harness debug-label chrome. Normative home: docs/DYNAMIC_CAPTURE.md, section
// "Harness label chrome" (drawers: HarnessLabelChrome.swift, ScreenshotCaptureScreen.kt
// `harnessLabelRects`, LabelChrome.tsx). Per stem tools/visual/baseline/
// {Android,iOS,web}__NNN_<name>.png:
//   P     = the set-bit pixels the chrome MUST draw for <name>: glyphs from
//           tools/visual/block-font.json (cell 5×7, advance 6) at frame origin (8,6);
//           text = <name> with '_'→' ', normalized (upper-case, atlas-unknown → '-'),
//           truncated to the largest n with 8 + 6n ≤ pngWidth − 8.
//   (i)   every p ∈ P is non-ground (not #1A1A2E ±1/channel) on all three PNGs.
//   (ii)  on stems whose rows 0..15 OUTSIDE P are ground on all three, rows 0..15 are
//         byte-identical across the three PNGs — the label is the only paint in the
//         band and rgba(237,237,237,179/255) over #1A1A2E is (174,174,180) everywhere
//         (web moved from alpha 0.7 = (173,173,179) to 179/255 in this PR).
//   (iii) on the other stems (shadows, outlines, transforms, negative offsets reaching
//         the band) the P-mask bytes agree across platforms within ±1/channel.
// Colour is never counted (review C39): the checks are positional, so ink left at the
// old in-box footprint, an origin off by one, or a platform truncating differently is
// red, while platform-different penumbra OUTSIDE the glyphs is not. Nothing is skipped
// silently: every stem prints its verdict; a missing platform PNG, an unparseable file
// name or a width mismatch fails the stem, never skips it.
//
// NEGATIVE CONTROL (proof of teeth), executed 2026-09-16 on campaign/wave51-labels HEAD
// 8c4ad393 — the PRE-refresh baselines, label still INSIDE the box at its content-box
// origin: 130/130 stem tests red, every one on (i) (111 stems in band-identical mode,
// 19 in glyph-mask mode = the design's 19-stem band-paint list; 6 of those 19 — 024,
// 027, 032, 037, 082, 098 — also red on (iii), the under-glyph paint differing ×3).
// Run: 136 tests, 6 pass (geometry + synthetic controls), 130 fail. This file goes
// green ONLY after the PR's UPDATE_BASELINE refresh relocates the label into the band
// on all 390 PNGs — until then it is the PR's own red gate, by design. The synthetic
// tests prove the SAME checker passes a correct triplet and fails each named mutation.
import { test } from 'node:test';                        // node's built-in runner (CI: node --test tools/visual/*.test.mjs)
import assert from 'node:assert/strict';                 // strict equality — a PNG byte is a byte
import { readdirSync, readFileSync } from 'node:fs';     // baseline listing + atlas/PNG bytes
import path from 'node:path';                            // path joins relative to this file
import { fileURLToPath } from 'node:url';                // ESM has no __dirname
import { PNG } from 'pngjs';                             // decoder — `tools` workspace dependency (tools/package.json)

const HERE = path.dirname(fileURLToPath(import.meta.url));                       // tools/visual/
const BASELINE_DIR = path.join(HERE, 'baseline');                                // the 390 committed captures
const FONT = JSON.parse(readFileSync(path.join(HERE, 'block-font.json'), 'utf8')); // THE atlas (single source of truth)
const PLATFORMS = ['Android', 'iOS', 'web'];                                     // file-name prefixes, fixed order
const ORIGIN = { x: 8, y: 6 };                                                   // shared-spec label origin, capture frame
const EDGE_MARGIN = 8;                                                           // right inset of the truncation rule
const GROUND = [0x1a, 0x1a, 0x2e];                                               // #1A1A2E — every capture canvas ground
const GROUND_TOL = 1;                                                            // ±1/channel: PNG encoders never move more
const BAND_ROWS = 16;                                                            // rows 0..15 = the top pad above the border box
const MASK_TOL = 1;                                                              // (iii): one blend-rounding step, per channel
const INK = [237, 237, 237];                                                     // label ink RGB, before alpha
const INK_ALPHA = 179;                                                           // 179/255 — natives pin it; web must match

/** Shared-spec normalize: upper-case, then map every atlas-unknown char to '-'. */
export function normalize(label) {
  return Array.from(label.toUpperCase()).map((ch) => (FONT.glyphs[ch] ? ch : '-')).join(''); // mirrors BlockLabel.normalize ×3
}
/** Shared-spec truncation: largest n with 8 + 6n ≤ frameWidth − 8, clamped to the label. */
export function truncatedCount(len, frameWidth) {
  const budget = frameWidth - ORIGIN.x - EDGE_MARGIN;                            // px left for cells after both insets
  return budget <= 0 ? 0 : Math.min(len, Math.floor(budget / FONT.advance));     // 62 at 390, 39 at 250, 0 below 22
}
/** P for one component name at one frame width: [x, y] per set atlas bit, frame coords. */
export function glyphPixels(componentName, frameWidth) {
  const chars = normalize(componentName.replace(/_/g, ' '));                     // the PLAIN name, '_'→' ' (design C51)
  const n = truncatedCount(chars.length, frameWidth);                            // glyphs that fit the frame
  const out = [];                                                                // accumulated set-bit pixels
  for (let i = 0; i < n; i++) {                                                  // cell i sits at x = 8 + 6i
    const rows = FONT.glyphs[chars[i]];                                          // 7 row strings of 5 bits, index 0 = leftmost
    for (let r = 0; r < FONT.cell.height; r++) {                                 // row 0 = top of the cell
      for (let c = 0; c < FONT.cell.width; c++) {                                // col 0 = leftmost
        if (rows[r][c] === '1') out.push([ORIGIN.x + i * FONT.advance + c, ORIGIN.y + r]); // one 1×1 rect per set bit
      }
    }
  }
  return out;                                                                    // empty when nothing fits
}

/** True when pixel (x,y) is the canvas ground within ±GROUND_TOL on every RGB channel. */
function isGround(png, x, y) {
  const i = (y * png.width + x) * 4;                                             // RGBA stride
  return GROUND.every((g, c) => Math.abs(png.data[i + c] - g) <= GROUND_TOL);    // alpha ignored: ground is opaque anyway
}

/** The whole verdict for one platform triplet: mode + problem list (empty = green). */
export function checkTriplet(pngs, componentName) {
  const problems = [];                                                           // human-readable failures, tagged (i)/(ii)/(iii)
  const width = pngs[0].width;                                                   // P is derived from the PNG width…
  if (!pngs.every((p) => p.width === width)) problems.push(`width mismatch ${pngs.map((p) => p.width).join('/')}`); // …so it must agree
  const P = glyphPixels(componentName, width);                                   // the expected glyph pixel set
  const inP = new Set(P.map(([x, y]) => y * width + x));                         // O(1) membership for the band scan
  pngs.forEach((png, k) => {                                                     // (i) per platform
    const dark = P.filter(([x, y]) => isGround(png, x, y)).length;               // glyph pixels that are still ground
    if (dark) problems.push(`(i) ${PLATFORMS[k]}: ${dark}/${P.length} glyph px are ground`); // ink missing or elsewhere
  });
  const clean = pngs.map((png) => {                                              // per platform: is the band ground outside P?
    for (let y = 0; y < Math.min(BAND_ROWS, png.height); y++) {                  // rows 0..15 (guard very short PNGs)
      for (let x = 0; x < width; x++) if (!inP.has(y * width + x) && !isGround(png, x, y)) return false; // paint in the band
    }
    return true;                                                                 // nothing but ground (and P) up there
  });
  const mode = clean.every(Boolean) ? 'band-identical' : 'glyph-mask';          // which cross-platform clause applies
  let diff = 0;                                                                  // differing pixels under the applied clause
  if (mode === 'band-identical') {                                               // (ii) whole band, all 4 bytes, ×3
    for (let y = 0; y < BAND_ROWS; y++) {                                        // every band row
      for (let x = 0; x < width; x++) {                                          // every column
        const i = (y * width + x) * 4;                                           // RGBA offset shared by the three (same width)
        if ([0, 1, 2, 3].some((c) => pngs.some((p) => p.data[i + c] !== pngs[0].data[i + c]))) diff += 1; // any byte differs
      }
    }
    if (diff) problems.push(`(ii) ${diff} band px (rows 0..15) differ across platforms`); // byte identity is the contract
  } else {                                                                       // (iii) glyph mask only, ±MASK_TOL per channel
    for (const [x, y] of P) {                                                    // every expected glyph pixel
      const i = (y * width + x) * 4;                                             // its RGBA offset
      const off = [0, 1, 2].some((c) => {                                        // any RGB channel spread beyond tolerance?
        const v = pngs.map((p) => p.data[i + c]);                                // the three platforms' bytes
        return Math.max(...v) - Math.min(...v) > MASK_TOL;                       // spread, not distance to a reference
      });
      if (off) diff += 1;                                                        // count the glyph pixels that disagree
    }
    if (diff) problems.push(`(iii) ${diff}/${P.length} glyph px differ > ±${MASK_TOL}/channel across platforms`); // penumbra leak
  }
  return { mode, glyphs: P.length, problems };                                   // the caller prints and asserts
}

/** Group baseline PNGs by stem; an unparseable name is a failure row, not a skip. */
function baselineStems() {
  const stems = new Map();                                                       // stem → { name, files: platform → file }
  for (const f of readdirSync(BASELINE_DIR).filter((n) => n.endsWith('.png')).sort()) { // deterministic order
    const m = /^(Android|iOS|web)__(\d{3}_(.+))\.png$/.exec(f);                  // <platform>__<NNN>_<name>.png
    const stem = m ? m[2] : f;                                                   // unparseable → its own (failing) stem
    const entry = stems.get(stem) ?? { name: m ? m[3] : null, files: {} };       // component name = after the NNN_ prefix
    if (m) entry.files[m[1]] = f;                                                // remember which platform this file is
    stems.set(stem, entry);                                                      // upsert
  }
  return stems;                                                                  // 130 stems × 3 platforms today
}

// ── Geometry self-check: the checker's own maths, independent of any PNG ──────
test('label geometry: origin (8,6), advance 6, truncation 62/39/1/0, normalize', () => {
  assert.deepEqual(glyphPixels('Layout_C01', 390)[0], [8, 6]);                  // 'L' row 0 = "10000" → first ink at (8,6)
  assert.deepEqual(glyphPixels('Ii', 390).slice(0, 3), [[9, 6], [10, 6], [11, 6]]); // 'I' row 0 "01110" → cols 1..3
  assert.equal(glyphPixels('Ii', 390).length, 2 * 11);                           // 'I' = 3+1+1+1+1+1+3 bits; 'i' case-folds to it
  assert.deepEqual(glyphPixels('Ii', 390)[11], [15, 6]);                         // second cell starts at x = 8 + 6 → col 1 = 15
  assert.equal(truncatedCount(100, 390), 62);                                    // default canvas → 62 glyphs
  assert.equal(truncatedCount(100, 250), 39);                                    // CAPTURE_WIDTH=250 → 39 glyphs
  assert.equal(truncatedCount(5, 22), 1);                                        // smallest frame with one glyph
  assert.equal(truncatedCount(5, 21), 0);                                        // below 22 → nothing (design C5)
  assert.equal(normalize('ratio_16 9?é'), 'RATIO_16 9--');                       // upper-case; unknown → '-'
});

// ── Synthetic controls: the checker passes a correct triplet, fails each mutation ──
/** A ground canvas, optional under-paint, then the label composited at `alpha` from `origin`. */
function synthetic(name, { w = 390, h = 64, paint = null, alpha = INK_ALPHA, origin = ORIGIN } = {}) {
  const png = new PNG({ width: w, height: h });                                  // opaque canvas (alpha 255 below)
  for (let i = 0; i < png.data.length; i += 4) png.data.set([...GROUND, 255], i); // fill with the ground
  if (paint) paint(png);                                                         // e.g. a shadow reaching the band
  for (const [x, y] of glyphPixels(name, w)) {                                   // ink at the SPEC origin…
    const i = ((y - ORIGIN.y + origin.y) * w + (x - ORIGIN.x + origin.x)) * 4;   // …shifted by the mutation's origin delta
    for (let c = 0; c < 3; c++) png.data[i + c] = Math.round((INK[c] * alpha + png.data[i + c] * (255 - alpha)) / 255); // src-over
  }
  return png;                                                                    // one platform's capture
}
const band = (png, dr = 0) => { for (let y = 0; y < 14; y++) for (let x = 0; x < 120; x++) png.data.set([90 + dr, 90, 110, 255], (y * png.width + x) * 4); }; // shadow-like band paint
const tags = (v) => v.problems.map((p) => /^\((i{1,3})\)/.exec(p)?.[0] ?? p);    // '(i)' / '(ii)' / '(iii)' per problem
const trio = (...opts) => PLATFORMS.map((_, k) => synthetic('Test_Comp', opts[k] ?? opts[0])); // Android, iOS, web canvases

test('synthetic: a correct triplet is green in both modes', () => {
  assert.deepEqual(checkTriplet(trio({}), 'Test_Comp'), { mode: 'band-identical', glyphs: 117, problems: [] }); // T11+E18+S15+T11+C13+O16+M18+P15
  assert.deepEqual(checkTriplet(trio({ paint: band }), 'Test_Comp'), { mode: 'glyph-mask', glyphs: 117, problems: [] }); // identical paint under P
});
test('mutation: one platform at alpha 0.7 (178/255 → 173,173,179) is red on (ii)', () => {
  assert.deepEqual(tags(checkTriplet(trio({}, {}, { alpha: 178 }), 'Test_Comp')), ['(ii)']); // the review-measured web defect
});
test('mutation: origin off by one — on one platform (i)+(iii), on all three (i) ×3', () => {
  const shifted = { origin: { x: 9, y: 6 } };                                    // glyphs one column right of spec
  assert.deepEqual(tags(checkTriplet(trio({}, {}, shifted), 'Test_Comp')), ['(i)', '(iii)']); // web ink outside P → glyph-mask, P differs
  assert.deepEqual(tags(checkTriplet(trio(shifted), 'Test_Comp')), ['(i)', '(i)', '(i)']); // consistent ×3 is still red: leftmost bits ground
});
test('mutation: label left at the old in-box footprint (24,22) is red on (i) ×3', () => {
  assert.deepEqual(tags(checkTriplet(trio({ origin: { x: 24, y: 22 } }), 'Test_Comp')), ['(i)', '(i)', '(i)']); // pre-refresh geometry
});
test('mutation: platform-different paint UNDER the glyphs is red on (iii)', () => {
  const lighter = (png) => band(png, 10);                                        // web penumbra +10 red → +3 after compositing
  assert.deepEqual(tags(checkTriplet(trio({ paint: band }, { paint: band }, { paint: lighter }), 'Test_Comp')), ['(iii)']); // > MASK_TOL
});

// ── The tripwire proper: one test per committed baseline stem ─────────────────
for (const [stem, { name, files }] of baselineStems()) {                         // 130 stems; each prints its own verdict
  test(`label chrome ${stem}`, () => {
    const missing = PLATFORMS.filter((p) => !files[p]);                          // a triplet needs all three
    assert.equal(missing.length, 0, `${stem}: missing ${missing.join(', ')} baseline (or unparseable file name)`); // no silent skip
    const pngs = PLATFORMS.map((p) => PNG.sync.read(readFileSync(path.join(BASELINE_DIR, files[p])))); // Android, iOS, web
    const v = checkTriplet(pngs, name);                                          // the shared checker
    console.log(`  ${stem} [${v.mode}, ${v.glyphs} glyph px] ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'ok'}`); // per-stem verdict, always
    assert.deepEqual(v.problems, [], `${stem}: ${v.problems.join('; ')}`);       // green only when every clause holds
  });
}

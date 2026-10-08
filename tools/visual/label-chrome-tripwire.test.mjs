#!/usr/bin/env node
// label-chrome-tripwire.test.mjs — the POSITIONAL cross-platform tripwire for the
// harness debug-label chrome. Normative home: docs/DYNAMIC_CAPTURE.md, section
// "Harness label chrome" (drawers: HarnessLabelChrome.swift, ScreenshotCaptureScreen.kt
// `harnessLabelRects`, LabelChrome.tsx). The pure checker is tools/visual/label-chrome-check.mjs
// (re-exported below for replays). Per stem tools/visual/baseline/{Android,iOS,web}__NNN_<name>.png:
//   P     = the set-bit pixels the chrome MUST draw for <name>: glyphs from
//           tools/visual/block-font.json (cell 5×7, advance 6) at frame origin (8,6);
//           text = <name> with '_'→' ', normalized (upper-case, atlas-unknown → '-'),
//           truncated to the largest n with 8 + 6n ≤ pngWidth − 8.
//   (i)   every p ∈ P is non-ground (not #1A1A2E ±1/channel) on all three PNGs.
//   (ii)  on stems whose rows 0..15 OUTSIDE P are ground on all three, rows 0..15 are
//         byte-identical across the three PNGs — the label is the only paint in the
//         band and the label's COMPOSITED byte over #1A1A2E is (174,174,180) everywhere
//         (natives: alpha 179/255; web: CSS alpha 0.706 = alpha byte 180, because
//         Chromium rounds CSS alpha to 8 bits and byte 179 lands at (173,173,179) — the
//         0.7 and 0.70196 spellings both did; measured in skeptic-web.md, this PR).
//   (iii) on the other stems (shadows, outlines, transforms, negative offsets reaching
//         the band) the P-mask byte spread across platforms is COUNTED for the verdict
//         line, never asserted (checkTriplet's own comment: the under-glyph paint
//         legitimately differs per platform; (i) is the whole check there).
//   (iv)  wave 54 — the contract's "iff" (§5 "When": no label on a composed root WITH
//         composed children or non-empty text). A stem listed in
//         tools/visual/label-chrome-exempt.json whose entry VERIFIES against its fixture's
//         structure runs with P = ∅ (so (ii) binds when the band is ground ×3) and is red
//         when the name's would-be label is fully drawn on any platform. An entry that does
//         not verify is red and exempts nothing; an UNLISTED stem whose label is absent ×3
//         stays red and prints a HINT naming candidate fixture nodes — never a pass.
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
// WAVE-54 CONTROL (lane L7, U1): on the 18 all-then-color PNGs of 77fe41e8 (withdrawn
// at the wave-53 ship) the pre-(iv) rule is red (i) on 001/003/005 ×3 (437/369/293 glyph
// px ground) — the oracle's missing "iff", not a chrome defect; under the verified
// manifest they are green and its mutations m1–m5 are red
// (tools/titan/results/wave54-label-chrome/_note.md).
import { test } from 'node:test';                        // node's built-in runner (CI: node --test tools/visual/*.test.mjs)
import assert from 'node:assert/strict';                 // strict equality — a PNG byte is a byte
import { readdirSync, readFileSync } from 'node:fs';     // baseline listing + manifest/PNG bytes
import path from 'node:path';                            // path joins relative to this file
import { fileURLToPath } from 'node:url';                // ESM has no __dirname
import { PNG } from 'pngjs';                             // decoder — `tools` workspace dependency (tools/package.json)
import {
  PLATFORMS, ORIGIN, GROUND, INK, INK_ALPHA, ROOT,      // the shared-spec constants (one home: the checker)
  normalize, truncatedCount, glyphPixels, checkTriplet,  // P-derivation and clauses (i)-(iii)
  checkExempt, parseExemptManifest, verifyExemptEntry,   // wave 54: clause (iv) and the manifest verifier
  readFixture, labelAbsentEverywhere, listFixtures, exemptHint, // fixture reads + the (d) hint
} from './label-chrome-check.mjs';                       // the pure checker (no node:test cases)
export { normalize, truncatedCount, glyphPixels, checkTriplet, checkExempt } from './label-chrome-check.mjs'; // replays import from here

const HERE = path.dirname(fileURLToPath(import.meta.url));                       // tools/visual/
const BASELINE_DIR = path.join(HERE, 'baseline');                                // the committed captures
const BASELINE_FILES = readdirSync(BASELINE_DIR).filter((n) => n.endsWith('.png')).sort(); // deterministic order
const MANIFEST = parseExemptManifest(JSON.parse(readFileSync(path.join(HERE, 'label-chrome-exempt.json'), 'utf8'))); // the exempt list (shape-checked)
const VERIFIED = new Map(Object.entries(MANIFEST.entries).map(([stem, e]) => [stem, verifyExemptEntry(stem, e, { fixtureDoc: readFixture(ROOT, e?.fixture), baselineFiles: BASELINE_FILES })])); // (a) per entry

/** Group baseline PNGs by stem; an unparseable name is a failure row, not a skip. */
function baselineStems() {
  const stems = new Map();                                                       // stem → { name, files: platform → file }
  for (const f of BASELINE_FILES) {                                              // sorted listing
    const m = /^(Android|iOS|web)__(\d{3}_(.+))\.png$/.exec(f);                  // <platform>__<NNN>_<name>.png
    const stem = m ? m[2] : f;                                                   // unparseable → its own (failing) stem
    const entry = stems.get(stem) ?? { name: m ? m[3] : null, files: {} };       // component name = after the NNN_ prefix
    if (m) entry.files[m[1]] = f;                                                // remember which platform this file is
    stems.set(stem, entry);                                                      // upsert
  }
  return stems;                                                                  // one entry per committed stem
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
  for (const [x, y] of glyphPixels(name, w)) {                                   // ink at the SPEC origin (none for name '')…
    const i = ((y - ORIGIN.y + origin.y) * w + (x - ORIGIN.x + origin.x)) * 4;   // …shifted by the mutation's origin delta
    for (let c = 0; c < 3; c++) png.data[i + c] = Math.round((INK[c] * alpha + png.data[i + c] * (255 - alpha)) / 255); // src-over
  }
  return png;                                                                    // one platform's capture
}
const band = (png, dr = 0) => { for (let y = 0; y < 14; y++) for (let x = 0; x < 120; x++) png.data.set([90 + dr, 90, 110, 255], (y * png.width + x) * 4); }; // shadow-like band paint
const tags = (v) => v.problems.map((p) => /^\((i{1,3}|iv)\)/.exec(p)?.[0] ?? p); // '(i)' … '(iv)' per problem
const trio = (...opts) => PLATFORMS.map((_, k) => synthetic('Test_Comp', opts[k] ?? opts[0])); // Android, iOS, web canvases
const bare = (...opts) => PLATFORMS.map((_, k) => synthetic('', opts[k] ?? opts[0] ?? {})); // label-FREE canvases (P('') = ∅)

test('synthetic: a correct triplet is green in both modes', () => {
  assert.deepEqual(checkTriplet(trio({}), 'Test_Comp'), { mode: 'band-identical', glyphs: 117, problems: [], underPaintSpread: 0 }); // T11+E18+S15+T11+C13+O16+M18+P15
  assert.deepEqual(checkTriplet(trio({ paint: band }), 'Test_Comp'), { mode: 'glyph-mask', glyphs: 117, problems: [], underPaintSpread: 0 }); // identical paint under P
});
test('mutation: one platform at alpha 0.7 (178/255 → 173,173,179) is red on (ii)', () => {
  assert.deepEqual(tags(checkTriplet(trio({}, {}, { alpha: 178 }), 'Test_Comp')), ['(ii)']); // the review-measured web defect
});
test('mutation: origin off by one — on one platform (i), on all three (i) ×3', () => {
  const shifted = { origin: { x: 9, y: 6 } };                                    // glyphs one column right of spec
  assert.deepEqual(tags(checkTriplet(trio({}, {}, shifted), 'Test_Comp')), ['(i)']); // web ink outside P → glyph-mask mode, and (i) catches the missing column
  assert.deepEqual(tags(checkTriplet(trio(shifted), 'Test_Comp')), ['(i)', '(i)', '(i)']); // consistent ×3 is still red: leftmost bits ground
});
test('mutation: label left at the old in-box footprint (24,22) is red on (i) ×3', () => {
  assert.deepEqual(tags(checkTriplet(trio({ origin: { x: 24, y: 22 } }), 'Test_Comp')), ['(i)', '(i)', '(i)']); // pre-refresh geometry
});
test('glyph-mask stems: platform-different paint UNDER the glyphs is counted, never red', () => {
  const lighter = (png) => band(png, 10);                                        // web penumbra +10 red → +3 after compositing
  const v = checkTriplet(trio({ paint: band }, { paint: band }, { paint: lighter }), 'Test_Comp'); // legitimate per-platform under-paint
  assert.deepEqual(v.problems, []);                                              // positional only — (i) holds, nothing else is asserted
  assert.ok(v.underPaintSpread > 0);                                             // …but the spread is still reported for the verdict line
});

// ── Synthetic controls for the wave-54 exempt path (U1 (c)) ───────────────────
test('exempt: a label-free triplet is green (P = ∅, band byte-identical, (iv) holds)', () => {
  assert.deepEqual(checkExempt(bare(), 'Test_Comp'), { mode: 'band-identical', glyphs: 0, problems: [], underPaintSpread: 0, exempt: true }); // the 001/003/005 shape
});
test('exempt mutation: the label drawn ×3 is red on (iv) ×3; drawn on one platform only, (iv) on that one', () => {
  assert.deepEqual(tags(checkExempt(trio({}), 'Test_Comp')), ['(iv)', '(iv)', '(iv)']); // the contract broken identically ×3 (brief m1)
  assert.deepEqual(checkExempt([...bare().slice(0, 2), synthetic('Test_Comp')], 'Test_Comp').problems, ['(iv) web: 117/117 label px drawn on a label-exempt capture']); // one platform's predicate drifted (brief m2)
});
test('exempt mutation: a band one LSB off on one platform is still red on (ii)', () => {
  const lsb = (png) => { for (let x = 0; x < png.width; x++) png.data[(3 * png.width + x) * 4] += 1; }; // row 3 red +1: still "ground" (±1), not byte-identical
  assert.deepEqual(tags(checkExempt(bare({}, {}, { paint: lsb }), 'Test_Comp')), ['(ii)']); // P = ∅ keeps (ii) binding on exempt stems
});
const DOC = { components: { Box: { children: { Kid: {} } }, Txt: { _text: '123' }, Leaf: { properties: {} }, Empty: { _text: '' } } }; // one node per shape
const files = (...stems) => stems.flatMap((s) => PLATFORMS.map((p) => `${p}__${s}.png`)); // a committed triplet per stem
const verify = (stem, why, baselineFiles, fixtureDoc = DOC) => verifyExemptEntry(stem, { fixture: 'synthetic.json', why }, { fixtureDoc, baselineFiles }); // (a) on a synthetic fixture
test('manifest: a container / a text root with a committed triplet verifies', () => {
  assert.deepEqual(verify('001_Box', 'container', files('001_Box')), { status: 'verified', problems: [] }); // children → container
  assert.deepEqual(verify('002_Txt', 'text', files('002_Txt')), { status: 'verified', problems: [] });     // non-empty _text → text
});
test('manifest mutations: a leaf, an empty _text, a kind mismatch, a missing node, a stale / partial entry are red', () => {
  const red = (v) => { assert.equal(v.status, 'red'); return v.problems.join(' / '); };                  // red, and its reasons
  assert.match(red(verify('003_Leaf', 'container', files('003_Leaf'))), /is a leaf, entry says container — a childless textless root IS labelled/); // brief m4
  assert.match(red(verify('004_Empty', 'text', files('004_Empty'))), /is a leaf, entry says text/);     // `_text: ""` is no text (§5 "non-empty")
  assert.match(red(verify('001_Box', 'text', files('001_Box'))), /is a container, entry says text/);    // `why` must match the node
  assert.match(red(verify('005_Gone', 'container', files('001_Box'))), /declares no component "Gone"/); // renamed / removed node (brief m5)
  assert.match(red(verify('001_Box', 'container', files('002_Txt'))), /stale entry/);                   // fixture seeded, this stem absent
  assert.match(red(verify('001_Box', 'container', files('001_Box').slice(0, 2))), /partial triplet/);   // web PNG missing
  assert.match(red(verify('001_Box', 'sometimes', files('001_Box'))), /neither container nor text/);    // closed vocabulary
  assert.match(red(verify('001_Box', 'container', files('001_Box'), null)), /missing or not JSON/);     // moved fixture
  assert.match(red(verify('Box', 'container', [])), /not a <NNN>_<name> stem/);                         // cannot name a PNG
});
test('manifest: an entry whose fixture has NO committed baseline yet is pending — it exempts nothing', () => {
  assert.deepEqual(verify('001_Box', 'container', files('000_Other')), { status: 'pending', problems: [] }); // test-all runs that fixture gate-only
});
test('manifest shape: unknown keys, a missing fixture path or a missing exempt map are red', () => {
  assert.deepEqual(parseExemptManifest({ _comment: [], exempt: {} }).problems, []);                     // the empty manifest is well-formed
  assert.equal(parseExemptManifest({ exempt: { '001_Box': { fixture: 'f.json', why: 'container', typo: 1 } }, extra: 1 }).problems.length, 2); // both typos named
  assert.match(parseExemptManifest({ exempt: { '001_Box': { why: 'container' } } }).problems.join(), /no "fixture" path/); // provenance is mandatory
  assert.match(parseExemptManifest({}).problems.join(), /"exempt" must be an object/);                  // no silent empty list
});
test('hint (d): a label absent ×3 names the container / text-root candidates — and the stem stays red', () => {
  assert.ok(labelAbsentEverywhere(bare(), 'Box') && !labelAbsentEverywhere(trio({}), 'Test_Comp'));      // absent ×3 vs drawn
  assert.ok(checkTriplet(bare(), 'Box').problems.length > 0);                                           // the hint never turns a red into a pass
  assert.match(exemptHint('001_Box', [{ rel: 'synthetic.json', doc: DOC }]), /"001_Box": \{ "fixture": "synthetic.json", "why": "container" \}/); // the line to add
  assert.match(exemptHint('003_Leaf', [{ rel: 'synthetic.json', doc: DOC }]), /a chrome predicate regression, not an exemption/); // a leaf justifies nothing
});

// ── The exempt manifest: shape, then one verification per entry (U1 (a)) ─────
test('label chrome exempt manifest: shape', () => {
  assert.deepEqual(MANIFEST.problems, [], MANIFEST.problems.join('; '));         // malformed manifest → red, named
});
for (const [stem, v] of VERIFIED) {                                              // one test per entry, each prints its verdict
  test(`label chrome exempt-entry ${stem}`, () => {
    const e = MANIFEST.entries[stem];                                            // the entry as written
    const said = v.status === 'pending' ? 'pending — no committed baseline names any component of the fixture yet (test-all.sh runs it gate-only); exempts nothing until seeded' : v.status; // loud, never silent
    console.log(`  exempt-entry ${stem} [${e?.why}, ${e?.fixture}] ${v.problems.length ? 'RED ' + v.problems.join('; ') : said}`); // per-entry verdict, always
    assert.deepEqual(v.problems, [], `${stem}: ${v.problems.join('; ')}`);       // green only when the structure justifies the exemption
  });
}

// ── The tripwire proper: one test per committed baseline stem ─────────────────
let fixtures = null;                                                             // tracked fixtures, read lazily for the (d) hint only
for (const [stem, { name, files: have }] of baselineStems()) {                  // every committed stem prints its own verdict
  test(`label chrome ${stem}`, () => {
    const missing = PLATFORMS.filter((p) => !have[p]);                           // a triplet needs all three
    assert.equal(missing.length, 0, `${stem}: missing ${missing.join(', ')} baseline (or unparseable file name)`); // no silent skip
    const pngs = PLATFORMS.map((p) => PNG.sync.read(readFileSync(path.join(BASELINE_DIR, have[p])))); // Android, iOS, web
    const exempt = VERIFIED.get(stem)?.status === 'verified';                    // only a VERIFIED entry exempts (b)
    const v = exempt ? checkExempt(pngs, name) : checkTriplet(pngs, name);       // P = ∅ + (iv), or today's path unchanged
    console.log(`  ${stem} [${exempt ? 'exempt ' : ''}${v.mode}, ${v.glyphs} glyph px] ${v.problems.length ? 'RED ' + v.problems.join('; ') : 'ok'}`); // per-stem verdict, always
    if (!exempt && v.problems.length && labelAbsentEverywhere(pngs, name)) console.log(`  ${exemptHint(stem, (fixtures ??= listFixtures(ROOT)))}`); // (d) a message, never a pass
    assert.deepEqual(v.problems, [], `${stem}: ${v.problems.join('; ')}`);       // green only when every clause holds
  });
}

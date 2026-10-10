// label-chrome-check.mjs — the PURE checker behind tools/visual/label-chrome-tripwire.test.mjs (no node:test cases
// here, so replay scripts import it without registering the tripwire's per-stem tests). Normative home:
// docs/DYNAMIC_CAPTURE.md §5 "Harness label chrome". The P-derivation and clauses (i)-(iii) moved here (code verbatim)
// from the test file (wave 54, lane L7 U1); NEW in wave 54 is the contract's "iff" (§5 "When": a label exactly
// when the composed capture root has zero composed children AND no non-empty text): a stem listed in
// tools/visual/label-chrome-exempt.json and VERIFIED against its fixture's structure runs with P = ∅ plus clause
// (iv) instead of demanding a label the contract says must not be drawn
// (tools/titan/results/wave54-plan/label-chrome-all-reset.md §0, §6).
import { readFileSync, readdirSync } from 'node:fs';      // atlas, fixtures, manifest
import path from 'node:path';                             // joins relative to this file / the repo root
import { fileURLToPath } from 'node:url';                 // ESM has no __dirname

const HERE = path.dirname(fileURLToPath(import.meta.url));                       // tools/visual/
export const ROOT = path.resolve(HERE, '..', '..');                              // repo root: manifest fixture paths are root-relative
export const FONT = JSON.parse(readFileSync(path.join(HERE, 'block-font.json'), 'utf8')); // THE atlas (single source of truth)
export const PLATFORMS = ['Android', 'iOS', 'web'];                              // file-name prefixes, fixed order
export const ORIGIN = { x: 8, y: 6 };                                            // shared-spec label origin, capture frame
const EDGE_MARGIN = 8;                                                           // right inset of the truncation rule
export const GROUND = [0x1a, 0x1a, 0x2e];                                        // #1A1A2E — every capture canvas ground
const GROUND_TOL = 1;                                                            // ±1/channel: PNG encoders never move more
const BAND_ROWS = 16;                                                            // rows 0..15 = the top pad above the border box
const MASK_TOL = 1;                                                              // (iii): one blend-rounding step, per channel
export const INK = [237, 237, 237];                                              // label ink RGB, before alpha
export const INK_ALPHA = 179;                                                    // 179/255 — natives pin it; web must match
export const EXEMPT_KINDS = ['container', 'text'];                               // the two shapes §5 "When" draws no label on

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
  return out;                                                                    // empty when nothing fits (or name '')
}

/** True when pixel (x,y) is the canvas ground within ±GROUND_TOL on every RGB channel. */
export function isGround(png, x, y) {
  const i = (y * png.width + x) * 4;                                             // RGBA stride
  return GROUND.every((g, c) => Math.abs(png.data[i + c] - g) <= GROUND_TOL);    // alpha ignored: ground is opaque anyway
}

/** The whole verdict for one platform triplet: mode + problem list (empty = green). Unchanged since wave 51. */
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
  } else {                                                                       // (iii) glyph-mask stems: POSITIONAL only
    // The paint UNDER the glyphs is the component's own penumbra / blur / transformed edge, which legitimately
    // differs per platform, so it is counted for the verdict line and never asserted (review C39: colour is never
    // counted; wave-51 refresh-check.mjs S4b measured 8 of 19 such stems > 1 LSB apart under the glyphs).
    for (const [x, y] of P) {                                                    // every expected glyph pixel
      const i = (y * width + x) * 4;                                             // its RGBA offset
      const off = [0, 1, 2].some((c) => {                                        // any RGB channel spread beyond MASK_TOL?
        const v = pngs.map((p) => p.data[i + c]);                                // the three platforms' bytes
        return Math.max(...v) - Math.min(...v) > MASK_TOL;                       // spread, not distance to a reference
      });
      if (off) diff += 1;                                                        // informational: platform-different under-paint
    }
  }
  return { mode, glyphs: P.length, problems, underPaintSpread: mode === 'glyph-mask' ? diff : 0 }; // the caller prints and asserts
}

/** (b) A VERIFIED exempt stem: P = ∅ (so (ii) band byte-identity binds when the band is ground ×3) plus NEW clause
 *  (iv): per platform, NOT every pixel of the name's would-be label may be non-ground. Positional, no colour (C39);
 *  "every pixel" (not "any") so the component's own paint reaching a few glyph cells never false-reds (brief §9 R2). */
export function checkExempt(pngs, componentName) {
  const base = checkTriplet(pngs, '');                                           // P = ∅: (i) vacuous, (ii)/(iii) by band state
  const wouldBe = glyphPixels(componentName, pngs[0].width);                     // where the label WOULD sit if it were drawn
  const problems = [...base.problems];                                           // keep (ii) / width-mismatch findings
  pngs.forEach((png, k) => {                                                     // (iv) per platform
    const lit = wouldBe.filter(([x, y]) => !isGround(png, x, y)).length;        // would-be glyph pixels carrying paint
    if (wouldBe.length && lit === wouldBe.length) problems.push(`(iv) ${PLATFORMS[k]}: ${lit}/${wouldBe.length} label px drawn on a label-exempt capture`); // a label the contract forbids
  });
  return { ...base, exempt: true, problems };                                    // same shape as checkTriplet + the exempt flag
}

/** The §5 "When" predicate read off a FIXTURE node: composed children → container, non-empty `_text` → text. */
export function nodeKind(node) {
  if (node?.children && typeof node.children === 'object' && Object.keys(node.children).length) return 'container'; // fixture `children` map → composed children
  if (typeof node?._text === 'string' && node._text.length > 0) return 'text';  // the converter's text source (CssParsing.kt `_text`)
  return 'leaf';                                                                 // childless and textless: §5 says it IS labelled
}
/** Every node named `name` at any depth of a fixture's `components` tree (a name may recur under two parents). */
export function nodesNamed(doc, name) {
  const hits = [];                                                               // matching nodes, document order
  const walk = (map) => { for (const [k, v] of Object.entries(map ?? {})) { if (k === name) hits.push(v); if (v?.children) walk(v.children); } }; // pre-order
  walk(doc?.components);                                                         // fixtures keep their roots under `components`
  return hits;                                                                   // [] when the fixture declares no such node
}
/** test-all.sh `_gate_fixture_has_baselines`, re-typed: does ANY committed PNG name a component of this fixture? */
export function fixtureSeeded(doc, baselineFiles) {
  const names = new Set();                                                       // every component name, any depth
  const walk = (map) => { for (const [k, v] of Object.entries(map ?? {})) { names.add(k); if (v?.children) walk(v.children); } }; // same walk as the shell rule
  walk(doc?.components);                                                         // the fixture's roots
  return baselineFiles.some((f) => names.has(/^(?:iOS|Android|web)__\d+_(.+)\.png$/.exec(f)?.[1])); // the shell rule's regex, \d+ included
}
/** Parsed fixture JSON, or null when the path is missing / not JSON (the verifier turns null into a red). */
export function readFixture(root, rel) {
  try { return JSON.parse(readFileSync(path.join(root, rel), 'utf8')); } catch { return null; } // unreadable → null, never a throw at import
}

/** Shape check of the manifest file: { _comment, exempt: { "<NNN>_<name>": { fixture, why, _comment? } } }. */
export function parseExemptManifest(json) {
  const problems = [];                                                           // shape findings; any → red
  for (const k of Object.keys(json ?? {})) if (k !== '_comment' && k !== 'exempt') problems.push(`manifest: unknown top-level key "${k}"`); // typo guard
  const entries = json?.exempt && typeof json.exempt === 'object' && !Array.isArray(json.exempt) ? json.exempt : {}; // the stem map
  if (entries !== json?.exempt) problems.push('manifest: "exempt" must be an object of stem → entry');               // absent / wrong type
  for (const [stem, e] of Object.entries(entries)) {                             // per entry
    for (const k of Object.keys(e ?? {})) if (!['fixture', 'why', '_comment'].includes(k)) problems.push(`manifest: ${stem}: unknown key "${k}"`); // no silent extras
    if (typeof e?.fixture !== 'string' || !e.fixture) problems.push(`manifest: ${stem}: no "fixture" path`);           // provenance is mandatory
  }
  return { entries, problems };                                                  // entries are verified one by one below
}

/** (a) Verify ONE entry structurally — independent of every pixel and every runtime. status: 'verified' (exempts its
 *  stem), 'pending' (the fixture has no committed baseline at all yet — exempts nothing), or 'red'. */
export function verifyExemptEntry(stem, entry, { fixtureDoc, baselineFiles }) {
  const name = /^\d{3}_(.+)$/.exec(stem)?.[1];                                   // the component name after NNN_
  if (!name) return { status: 'red', problems: [`manifest: "${stem}" is not a <NNN>_<name> stem`] }; // cannot match a PNG
  const problems = [];                                                           // findings for this entry
  if (!EXEMPT_KINDS.includes(entry?.why)) problems.push(`manifest: ${stem}: why "${entry?.why}" is neither container nor text`); // closed vocabulary
  if (!fixtureDoc) problems.push(`manifest: ${stem}: fixture ${entry?.fixture} is missing or not JSON`); // moved / deleted fixture → loud
  const hits = fixtureDoc ? nodesNamed(fixtureDoc, name) : [];                   // the node(s) the stem was captured from
  if (fixtureDoc && !hits.length) problems.push(`manifest: ${entry.fixture} declares no component "${name}"`); // renamed component → loud
  for (const node of hits) {                                                     // EVERY same-named node must justify the exemption
    const kind = nodeKind(node);                                                 // container / text / leaf
    if (kind !== entry?.why) problems.push(`manifest: "${name}" in ${entry.fixture} is a ${kind}, entry says ${entry?.why}${kind === 'leaf' ? ' — a childless textless root IS labelled' : ''}`); // mismatch → red, named
  }
  const have = PLATFORMS.filter((p) => baselineFiles.includes(`${p}__${stem}.png`)); // committed platforms of this stem
  let status = 'verified';                                                       // optimistic; downgraded below
  if (!have.length && fixtureDoc && !fixtureSeeded(fixtureDoc, baselineFiles)) status = 'pending'; // fixture still gate-only (test-all's own rule): nothing to exempt yet
  else if (!have.length) problems.push(`manifest: ${stem} has no committed PNG while ${entry?.fixture} has committed baselines (stale entry)`); // renumbered / withdrawn stem
  else if (have.length < PLATFORMS.length) problems.push(`manifest: ${stem} is committed on ${have.join(', ')} only (partial triplet)`); // the stem test reds too
  return { status: problems.length ? 'red' : status, problems };                 // red wins over pending / verified
}

/** True when every glyph pixel of the name's label is ground on ALL three PNGs — the shape an unlisted exempt stem takes. */
export function labelAbsentEverywhere(pngs, componentName) {
  const P = glyphPixels(componentName, pngs[0].width);                           // the label the tripwire expected
  return P.length > 0 && pngs.every((png) => P.every(([x, y]) => isGround(png, x, y))); // fully dark ×3, not a partial loss
}
/** Tracked fixtures (fixtures/**.json minus the gitignored wpt/ mirror) as [{ rel, doc }]; read lazily for (d) only. */
export function listFixtures(root) {
  const out = [];                                                                // parsed fixtures with a components tree
  const walk = (dir) => { for (const d of readdirSync(path.join(root, dir), { withFileTypes: true })) {   // depth-first
    const rel = path.posix.join(dir, d.name);                                    // root-relative, '/'-separated (manifest form)
    if (d.isDirectory() && d.name !== 'wpt') walk(rel);                          // skip only the gitignored WPT mirror
    else if (d.isFile() && d.name.endsWith('.json')) { const doc = readFixture(root, rel); if (doc?.components) out.push({ rel, doc }); } // fixtures only
  } };
  walk('fixtures');                                                              // every tracked fixture family
  return out;                                                                    // [] if the tree has none
}
/** (d) A HINT for a red-(i) stem whose label is absent ×3: the container / text-root nodes of that name and the
 *  manifest line that would exempt it. A message for the reader — the stem stays red. */
export function exemptHint(stem, fixtures) {
  const name = /^\d{3}_(.+)$/.exec(stem)?.[1] ?? stem;                           // component name of the stem
  const lines = [];                                                              // candidate manifest lines
  for (const { rel, doc } of fixtures) for (const node of nodesNamed(doc, name)) { const kind = nodeKind(node); if (kind !== 'leaf') lines.push(`"${stem}": { "fixture": "${rel}", "why": "${kind}" }`); } // exempt-shaped nodes only
  if (!lines.length) return `HINT ${stem}: label absent on all three, and no tracked fixture has a container / text root "${name}" — a chrome predicate regression, not an exemption`; // nothing justifies it
  return `HINT ${stem}: label absent on all three; §5 "When" draws none on a container / text root. If this capture's root is one of these, add to tools/visual/label-chrome-exempt.json "exempt": ${[...new Set(lines)].join(' | ')}`; // the line(s) to add, deduplicated
}

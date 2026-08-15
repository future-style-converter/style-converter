#!/usr/bin/env node
//
// tools/titan/split-combined-ir.mjs
//
// Splits ONE combined TITAN IR document (the thing test-all.sh converts
// fixtures/wpt/_smoke-combined.json into — a flat list of components named
// `wpt__<section>__<stem>__<idx>`) back into PER-TEST IR documents, one file
// per WPT test, so the native inbox feeders (feed-android.mjs / feed-ios.mjs)
// can STREAM them: each feeder fixture must be a small, self-contained IR
// document (1–10 components) that fits within the per-fixture timeout — the
// whole 700+-component combined doc as a single fixture would blow that
// budget. This is the reverse of build-combined-fixture.mjs.
//
// Component names/ids are preserved VERBATIM, so the per-component PNGs the
// devices produce keep the exact `<idx>_<safeName>.png` names that
// inject-wpt-block.mjs globs for — the split is transparent to the compare
// pipeline.
//
// Usage:
//   node tools/titan/split-combined-ir.mjs --in <combined-ir.json> --out <dir>
//
// Writes <out>/<safe(testPrefix)>.json for every test group and prints a
// one-line JSON summary. Exit 0 on success, 1 on bad input.

import { promises as fs } from 'node:fs';
import { resolve, join, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
// The ONE canonical compare-pipeline sanitiser (dot KEPT). Previously this
// module had its own copy that DROPPED the dot — a divergence that could name
// a split per-test doc `<key-with-underscore>.json` while inject-wpt-block
// globbed `<key-with-dot>.png`, silently dropping that platform's column for a
// test key containing ".". Sharing the one helper closes that gap.
import { safe } from './safe-name.mjs';

// Re-export so existing `import { safe } from './split-combined-ir.mjs'`
// call sites (and tests) resolve to the single shared implementation.
export { safe };

// The test key of a ROOT component's name. build-combined-fixture prefixes
// only ROOTS as `wpt__<section>__<stem>__<idx>`, so the test key is the
// root name MINUS the trailing `__<idx>` segment. wave-21 collision fix:
// this used to take the FIRST THREE `__`-delimited segments, which was only
// correct while stems never contained `__` — the subdir-encoded stems the
// fixture-collision fix introduced (`flexbox__monolithic-overflow-001
// .tentative`) contain `__` by design, and slice(0, 3) would truncate them
// to the bare subdir name, merging every test in that subdir into one bogus
// group. Stripping the one trailing child-index segment is exact for BOTH
// shapes (the index is always the appended `__<idx>`, per componentKey in
// build-combined-fixture.mjs) and byte-identical to the old rule for every
// `__`-free stem. Non-`wpt__` root names (defensive) remain their own group.
export function rootTestKey(rootName) {
  const parts = String(rootName ?? '').split('__');
  if (parts[0] === 'wpt' && parts.length >= 4) return parts.slice(0, -1).join('__');
  return String(rootName ?? '');
}

// Resolve the test key for ANY component by walking its `slot.parent` chain
// up to the root, then keying on the root's name. CRUCIAL: flattened CHILD
// components keep their raw extracted names (e.g. `clip-...__0__0`), NOT the
// `wpt__` prefix — only the root carries it — so a child can be grouped with
// its test ONLY via the slot chain, never by its own name. `byId` maps
// component id -> component; a chain that leaves the document (dangling
// parent) or cycles stops at the last in-document component (treated as the
// root), so no component is ever dropped. Returns the test key string.
export function testKeyOf(component, byId) {
  let cur = component;
  const seen = new Set();
  // Climb while the current node has an in-document slot parent.
  while (cur?.slot?.parent && byId.has(cur.slot.parent) && !seen.has(cur.id)) {
    seen.add(cur.id);
    cur = byId.get(cur.slot.parent);
  }
  return rootTestKey(cur?.name ?? cur?.id ?? '');
}

// ── Wave 43 lane V5: per-test @font-face scoping ─────────────────────────
//
// Wave 35 carried the combined document's ENTIRE `fontFaces` database onto
// every split doc — correct under css-fonts-4 §4.1 (the rule is document-
// scoped and each split doc IS a document), but dishonest at the per-test
// grain: in wave-42's css-text run 40 of 48 per-test docs carried a
// LinLibertine face that only the 8 boundary-shaping tests declare or use.
// The native feeders read `fontFaces[].src` to decide which font FILES to
// push into the app sandbox (feed-lib.mjs documentFontSrcs) and the native
// runtimes register every listed face, so every unrelated test paid the
// whole section's font delivery — and a face-parse crash in ONE test's font
// would have poisoned all 48 captures. A split doc is a COMPLETE slot tree
// (every component of exactly one test), so any family its text can resolve
// is named by one of its own components' FontFamily properties — a face no
// component names is unreachable in that document and is dropped here.

// Family-name identity for matching a face against a FontFamily reference.
// css-fonts-4 §4.2: family matching is ASCII case-insensitive, and the
// <string> spelling ("test") names the SAME family as the <custom-ident>
// spelling (test) — so quotes are stripped defensively even though both
// wire producers (extract-fixture's unquoteCssString and the converter's
// FontFamilySerializer, which emits bare strings) already emit unquoted.
export function familyId(name) {
  let t = String(name ?? '').trim();
  // Strip ONE layer of matching surrounding quotes (a CSS <string> token).
  if (t.length >= 2 && (t[0] === '"' || t[0] === "'") && t[t.length - 1] === t[0]) {
    t = t.slice(1, -1).trim();
  }
  return t.toLowerCase();
}

// The set of familyId()s a group of components references via FontFamily
// properties (wire shape per the converter's FontFamilySerializer:
// {type:"FontFamily", data:["Inter","sans-serif"]} — each family a bare
// string, generics as lowercase keywords). Returns NULL when any FontFamily
// entry (or a component's properties container) has a shape this reader
// does not recognise — the caller must then keep EVERY face: fail OPEN,
// never silently un-deliver a face an unreadable reference might name.
export function referencedFamilies(components) {
  const fams = new Set();
  for (const comp of Array.isArray(components) ? components : []) {
    const props = comp?.properties;
    if (props == null) continue;              // genuinely style-less: no refs
    if (!Array.isArray(props)) return null;   // unknown container → fail open
    for (const p of props) {
      if (p?.type !== 'FontFamily') continue; // only FontFamily names faces
      if (!Array.isArray(p.data)) return null;      // e.g. null (var()) → open
      for (const f of p.data) {
        if (typeof f !== 'string') return null;     // unknown entry → open
        fams.add(familyId(f));
      }
    }
  }
  return fams;
}

// Filter the document's face list down to the faces ONE test's components
// reference. Document ORDER is preserved (a §4.1 slot conflict resolves by
// order, so reordering could flip which face wins). Non-array inputs and
// unscannable groups carry VERBATIM — the pre-scoping behavior.
export function scopedFontFaces(fontFaces, components) {
  if (!Array.isArray(fontFaces)) return fontFaces;  // unknown shape → verbatim
  const fams = referencedFamilies(components);
  if (fams === null) return fontFaces;              // unscannable → keep all
  return fontFaces.filter((face) => fams.has(familyId(face?.family)));
}

// Pure core: combined IR document -> array of { key, doc } per-test docs.
// Each emitted doc carries the v2 version pair + that test's components,
// plus the document-level `keyframes` block when the source had one (it is
// document-scoped, so every split doc that might reference an animation
// keeps access to the full set — cheap, and correctness beats size here).
export function splitCombinedIr(combined) {
  if (!combined || !Array.isArray(combined.components)) {
    throw new Error('split-combined-ir: input is not an IR document with a components array');
  }
  // Index by id so testKeyOf can walk slot.parent chains up to each root.
  const byId = new Map();
  for (const comp of combined.components) if (comp?.id) byId.set(comp.id, comp);
  // Preserve first-seen group order so output is deterministic + matches
  // the source document order (important for byte-reproducible runs).
  const groups = new Map();
  for (const comp of combined.components) {
    const key = testKeyOf(comp, byId);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(comp);
  }
  const docs = [];
  for (const [key, components] of groups) {
    const doc = {
      irVersion: combined.irVersion ?? 2,
      minReaderVersion: combined.minReaderVersion ?? 2,
      components,
    };
    // Only carry keyframes when the source had them (additive v2 key;
    // omitting keeps pre-motion docs byte-identical to a hand-authored one).
    if (combined.keyframes) doc.keyframes = combined.keyframes;
    // Wave 35 lane B2 carried `fontFaces` (spec 01 §5) WHOLE; wave 43 lane V5
    // narrows the carry to the faces THIS test references (see the scoping
    // banner above scopedFontFaces): a face no component in this complete
    // slot tree names cannot be resolved by this document, so carrying it
    // only made every native capture pay the full section's font delivery.
    // Omit-when-empty matches build-combined-fixture's convention (the
    // converter never emits an empty list, and feed-lib's documentFontSrcs
    // treats absence as []); a non-array value carries verbatim (fail open).
    if (combined.fontFaces) {
      const scoped = scopedFontFaces(combined.fontFaces, components);
      if (!Array.isArray(scoped) || scoped.length > 0) doc.fontFaces = scoped;
    }
    docs.push({ key, doc });
  }
  return docs;
}

// ── CLI ──────────────────────────────────────────────────────────────────
function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

const IS_CLI = process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1]);
if (IS_CLI) {
  const inPath = arg('--in');
  const outDir = arg('--out');
  if (!inPath || !outDir) {
    console.error('usage: split-combined-ir.mjs --in <combined-ir.json> --out <dir>');
    process.exit(1);
  }
  const combined = JSON.parse(await fs.readFile(resolve(inPath), 'utf8'));
  const docs = splitCombinedIr(combined);
  await fs.mkdir(resolve(outDir), { recursive: true });
  for (const { key, doc } of docs) {
    await fs.writeFile(join(resolve(outDir), `${safe(key)}.json`), JSON.stringify(doc) + '\n');
  }
  console.log(JSON.stringify({
    ok: true,
    input: basename(inPath),
    tests: docs.length,
    components: combined.components.length,
    // Wave 43 lane V5 — the face-scoping record for the section log: how many
    // faces the combined DOCUMENT registers vs how many split docs still
    // carry any after per-test scoping. Present only when the section has
    // faces at all, so face-free sections' summary lines stay byte-identical.
    ...(Array.isArray(combined.fontFaces) && combined.fontFaces.length ? {
      fontFaces: combined.fontFaces.length,
      docsWithFaces: docs.filter(({ doc }) => Array.isArray(doc.fontFaces) && doc.fontFaces.length).length,
    } : {}),
    out: resolve(outDir),
  }));
}

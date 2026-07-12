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

// Filename/segment sanitiser — identical rule to inject-wpt-block.mjs's
// safe() so a split file name matches what downstream tooling expects.
export function safe(s) {
  return String(s).replace(/[^a-zA-Z0-9_-]/g, '_');
}

// The test key of a ROOT component's name. build-combined-fixture prefixes
// only ROOTS as `wpt__<section>__<stem>__<idx>`, so the test is the first
// three `__`-delimited segments (`wpt`, `<section>`, `<stem>`); WPT section
// names and stems use hyphens, never `__`, so those three identify the test
// unambiguously. Non-`wpt__` root names (defensive) become their own group.
export function rootTestKey(rootName) {
  const parts = String(rootName ?? '').split('__');
  if (parts[0] === 'wpt' && parts.length >= 4) return parts.slice(0, 3).join('__');
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
    out: resolve(outDir),
  }));
}

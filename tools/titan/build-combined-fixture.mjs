#!/usr/bin/env node
//
// tools/titan/build-combined-fixture.mjs
//
// Concat-fixture helper for the Phase 1 orchestrator. Given a SMOKE_TESTS
// list, walk the per-test extracted fixtures (fixtures/wpt/<section>/<stem>.json)
// and emit one combined fixture that the existing test-all.sh pipeline can
// chew on (Style-Converter's gradle convert + iOS/Android/web capture
// loop already handles a {"components": {...}} dict — they don't care
// whether it's a hand-authored 109-row visual-test or a 100-row WPT smoke).
//
// Component-name stability rule: each WPT component is renamed to a
// deterministic key so the post-capture wpt: block injector can look up
// `components.<key>` ← `manifest.rows[name=<key>]`.
//
// Naming convention:
//   wpt__<spec-section>__<test-stem>__<child-index>
//
// Example: a98rgb-001.html with one body child becomes
//   wpt__css-color__a98rgb-001__0
//
// <test-stem> is safe-name.mjs's fixtureStem(): bare basename for top-level
// tests; `<subdir>__…__<basename>` for nested tests (wave-21 collision fix),
// e.g. css/css-break/flexbox/monolithic-overflow-001.tentative.html →
//   wpt__css-break__flexbox__monolithic-overflow-001.tentative__0
// Consumers that recover the test key from a root name must therefore strip
// the TRAILING `__<idx>` segment (split-combined-ir.mjs rootTestKey), never
// take the "first three __-segments" — the stem itself may contain `__`.
//
// We do NOT include refs in the combined fixture for Phase 1 — the
// browser-ref pipeline already produced a Chromium render under
// tools/wpt/refs/<sha>/, and the iOS/Android/web "agreement" check is
// against the test, not the ref. (Phase 4 will wire ref-side captures.)
//
// Usage:
//   node build-combined-fixture.mjs --tests <SMOKE_FILE> --out <PATH>
//
// Exit codes:
//   0 — wrote combined fixture; non-zero count printed to stderr
//   1 — usage error / missing input
//   2 — IO error

import { promises as fs } from 'node:fs';
// basename/sep dropped from this import at the wave-21 collision fix — the
// two stem-derivation sites now go through safe-name.mjs's fixtureStem().
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
// The ONE canonical fixture-stem derivation (wave-21 collision fix): must
// match what extract-fixture.mjs used when it WROTE the per-test fixture —
// nested tests encode their subdirectory chain into the stem
// (`flexbox__monolithic-overflow-001.tentative`), so both the fixture-file
// lookup and the `wpt__<section>__<stem>__<idx>` component keys below stay
// collision-free when two sampled tests share a basename. See safe-name.mjs.
import { fixtureStem } from './safe-name.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname  = dirname(__filename);
const REPO_ROOT  = resolve(__dirname, '..', '..');

function arg(name) {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : null;
}

// wave-35 lane B2: run the CLI only when this module IS the entry point.
// Before this guard, `import`ing the file to unit-test its exported helpers
// tripped the usage check and killed the test process — the same idiom
// split-combined-ir.mjs already uses (its IS_CLI constant).
const IS_CLI = process.argv[1] && __filename === resolve(process.argv[1]);

const TESTS_FILE = arg('--tests');
const OUT_FILE   = arg('--out');
if (IS_CLI && (!TESTS_FILE || !OUT_FILE)) {
  console.error('usage: build-combined-fixture.mjs --tests <FILE> --out <FILE>');
  process.exit(1);
}

/** Component key matching the spec contract above. Exported in tests via
 *  the keys present in the resulting fixture.
 *
 *  wave-21 collision fix: the stem is fixtureStem(testRel) — subdir-encoded
 *  for nested tests — NOT the bare basename. Two tests with equal basenames
 *  in different subdirs (e.g. css-break/flexbox vs css-break/grid) used to
 *  produce IDENTICAL component keys here, so the second test's components
 *  silently overwrote the first's in the combined fixture AND in wptKeyMap.
 *  Top-level tests keep the exact historical key (zero churn). */
function componentKey(testRel, childIndex) {
  const parts = testRel.split('/');
  const section = parts.length >= 3 ? parts[1] : 'css';
  const stem = fixtureStem(testRel);
  return `wpt__${section}__${stem}__${childIndex}`;
}

// ── wave-35 lane B2: the @font-face DOCUMENT hop ────────────────────────────
//
// Wave 34 taught extract-fixture.mjs to emit a per-test, DOCUMENT-level
// `fontFaces` list (schema/spec/01-envelope.md §5) and taught the web harness
// to mount it. This file was the missing link in the SECTION pipeline: it
// merges N per-test fixtures into ONE combined document, and it merged only
// `components` — so every face a section's tests declared was dropped on the
// floor before gradle ever saw it, and the /wpt-font/ route had nothing to
// serve. Nothing downstream was broken; the delivery simply stopped here.
//
// WHY A MERGE AND NOT A PER-TEST CARRY. The combined fixture IS one CSS
// document by construction (gradle converts it as a single input, vite serves
// one IR to one page, and the composed capture paints every test's components
// onto that page). css-fonts-4 §4.1 scopes @font-face to the document's font
// database, so N tests' faces become ONE database — exactly what the merge
// below builds. The per-test IR docs the natives consume are re-split from
// this same document by split-combined-ir.mjs, which carries the list back
// down unchanged.
//
// THE COLLISION THIS CREATES, AND WHY IT IS STAMPED RATHER THAN RENAMED.
// WPT authors overwhelmingly name their test face `test` (all ten
// css-text/boundary-shaping docs do). Two tests declaring family `test` with
// the SAME file are one face after dedupe — the common case, and harmless.
// Two tests declaring family `test` with DIFFERENT files are a genuine
// conflict: §4.1 makes the LAST face with a given (family, weight, style) win,
// so the loser's text would silently shape with the winner's outlines and the
// capture would look plausible while measuring the wrong file. We do NOT
// rename the family to disambiguate — the component properties reference the
// author's name (`font: 36px test`) and rewriting both sides would fork the
// IR away from what the ref renders. Instead the FIRST face wins (document
// order, matching a browser reading the sheets in list order) and every test
// whose face lost is stamped `fontFacesDelivered: false` + recorded in
// `_wpt.fontFaceConflicts`. That stamp is what keeps the Rule 15 re-admission
// in inject-wpt-block.mjs honest: a shadowed test is NOT delivered, so it must
// not be scored as though it were.

/** Case-insensitive family identity. css-fonts-4 §4.2: `font-family` inside
 *  @font-face is an ASCII case-insensitive match against the used
 *  `font-family` name, so `Test` and `test` are ONE family and must collapse
 *  to one database entry (otherwise the second would shadow the first at
 *  render time while looking distinct here). */
function familyKey(family) {
  return String(family).trim().toLowerCase();
}

/** The (family, weight, style) triple css-fonts-4 §4.1 uses to decide which
 *  declared face WINS. Two entries sharing this slot cannot both live in one
 *  document — that is precisely the conflict the merge must surface. */
function faceSlotKey(face) {
  return `${familyKey(face.family)}|${face.weight ?? ''}|${face.style ?? ''}`;
}

/** Full identity including the file: two entries equal under this key are the
 *  SAME face declared twice (the boundary-shaping shape) and dedupe silently. */
function faceIdentityKey(face) {
  return `${faceSlotKey(face)}|${face.src}`;
}

/** Accept only entries the wire admits (spec 01 §5: `family` and `src` are
 *  REQUIRED, non-empty strings). A malformed entry is dropped here rather
 *  than passed to the converter, which would drop it anyway — dropping at the
 *  first reader keeps the delivery stamp below truthful. */
function usableFace(f) {
  return !!f && typeof f.family === 'string' && f.family.trim() !== ''
    && typeof f.src === 'string' && f.src.trim() !== '';
}

async function main() {
  const raw = await fs.readFile(TESTS_FILE, 'utf8');
  const tests = raw.split('\n').map((s) => s.trim())
    .filter((s) => s && !s.startsWith('#'));

  // Extract artifacts live at fixtures/wpt/<section>/<stem>.json. We DON'T
  // re-extract here — the orchestrator step 1 has already done that. We
  // tolerate missing files (the corresponding test failed extraction)
  // and surface the count.
  const components = {};
  const wptKeyMap = {};   // component key -> { test, childIndex, ... }
  let missingFixtures = 0;
  // wave-35 lane B2 — the merged document font database (see the banner above
  // familyKey). `fontFaces` is emitted in FIRST-SEEN order because that is the
  // order a browser would have read the concatenated sheets in, and §4.1
  // resolution depends on it.
  const fontFaces = [];
  const seenFaceIdentity = new Set();      // faceIdentityKey → already emitted
  const faceSlotOwner = new Map();         // faceSlotKey → { src, test }
  const fontFaceConflicts = [];            // loud record of every shadowed face
  const deliveredByTest = new Map();       // testRel → boolean delivery stamp

  for (const testRel of tests) {
    const parts = testRel.split('/');
    const section = parts.length >= 3 ? parts[1] : 'css';
    // wave-21 collision fix: read back the SAME subdir-encoded filename
    // extract-fixture.mjs wrote (safe-name.mjs fixtureStem) — a bare
    // basename here would miss every nested test's fixture (and, before
    // the fix, read whichever colliding sibling was extracted LAST).
    const stem = fixtureStem(testRel);
    const fixturePath = join(REPO_ROOT, 'fixtures', 'wpt', section, `${stem}.json`);

    let fixture;
    try {
      const j = JSON.parse(await fs.readFile(fixturePath, 'utf8'));
      fixture = j;
    } catch {
      missingFixtures++;
      // Emit a stub component anyway so the wpt: block can record the
      // miss as `bucket=missing`. Empty properties → renderer will
      // produce a blank canvas, classifier yields `no-data`.
      const key = componentKey(testRel, 0);
      components[key] = { properties: { width: '100px', height: '100px' } };
      wptKeyMap[key] = { test: testRel, section, stem, childIndex: 0,
                         bucket: 'missing', lossy: false,
                         // wave-16: a missing fixture certainly delivered no
                         // post-script state — explicit false keeps the
                         // score gate's conservative wall exclusion.
                         postLoadExtracted: false,
                         // wave-20: nor any post-script structure.
                         structureExtracted: false,
                         bidiBaked: false,
                         // Wave 27 — a missing fixture certainly baked no
                         // list markers either (explicit, never inferred).
                         counterStyleBaked: false,
                         // Wave 35 — nor any font face: there is no fixture to
                         // read one from. Explicit false, same conservatism.
                         fontFacesDelivered: false };
      continue;
    }

    // ── wave-35 lane B2: fold this test's faces into the document database.
    // Runs BEFORE the component loop so a conflict is recorded against the
    // test that lost, in test-list order (deterministic across runs).
    const declared = Array.isArray(fixture.fontFaces)
      ? fixture.fontFaces.filter(usableFace) : [];
    let allDelivered = declared.length > 0;
    for (const face of declared) {
      const identity = faceIdentityKey(face);
      if (seenFaceIdentity.has(identity)) continue;   // same face, declared twice
      const slot = faceSlotKey(face);
      const owner = faceSlotOwner.get(slot);
      if (owner) {
        // Same (family, weight, style), DIFFERENT file — §4.1 lets only one
        // win in a single document. First wins; this one is shadowed and its
        // test loses its delivery stamp (see the banner).
        fontFaceConflicts.push({
          family: face.family, weight: face.weight ?? null, style: face.style ?? null,
          kept: owner.src, keptBy: owner.test, shadowed: face.src, shadowedFor: testRel,
        });
        allDelivered = false;
        continue;
      }
      seenFaceIdentity.add(identity);
      faceSlotOwner.set(slot, { src: face.src, test: testRel });
      // Emit the four wire keys only — spec 01 §5 pins the entry shape and the
      // converter's CssParsing reader refuses to invent the optional two.
      fontFaces.push({
        family: face.family,
        src: face.src,
        ...(face.weight ? { weight: face.weight } : {}),
        ...(face.style ? { style: face.style } : {}),
      });
    }
    deliveredByTest.set(testRel, allDelivered);

    // Each child component is renamed to the WPT-stable key. The original
    // local name (e.g. "a98rgb-001__0") is preserved in wptKeyMap for
    // diagnostics.
    const fixtureComponents = fixture.components ?? {};
    let i = 0;
    for (const [localName, body] of Object.entries(fixtureComponents)) {
      const key = componentKey(testRel, i);
      components[key] = body;
      wptKeyMap[key] = {
        test: testRel,
        ref:  fixture._wpt?.ref ?? null,
        section,
        stem,
        childIndex: i,
        localName,
        bucket: fixture._wpt?.bucket ?? 'A',
        lossy:  !!fixture._wpt?.lossy,
        lossyReasons: fixture._wpt?.lossyReasons ?? [],
        fuzzy:  fixture._wpt?.fuzzy ?? null,
        // wave-16 POST-LOAD: thread the post-load delivery stamp to
        // inject-wpt-block's score gate (same channel as lossyReasons —
        // the keyMap is how per-test fixture truth reaches scoring).
        // true ⇔ tools/titan/post-load-extract.mjs captured the test's
        // POST-script computed state and baked it into this fixture, so
        // EXTRACTION_WALL_TAGS no longer score-exclude it.
        postLoadExtracted: fixture._wpt?.postLoadExtracted === true,
        // wave-20 STRUCTURE: the companion stamp — true ⇔ the component
        // tree was re-extracted from the serialized post-script DOM (the
        // appendChild family). Rides the same channel; applyNaScoreGate
        // treats either stamp as the wall's delivery record, and the
        // manifest surfaces both.
        structureExtracted: fixture._wpt?.structureExtracted === true,
        // Wave 23 — the bidi bake's provenance stamp (informational: bidi
        // tests are NOT wall-tagged, so this never feeds applyNaScoreGate;
        // it surfaces on the manifest row for investigators only).
        bidiBaked: fixture._wpt?.bidiBaked === true,
        // Wave 27 — the counter-style bake's provenance stamp (same
        // informational channel as bidiBaked: list tests are not
        // wall-tagged, so it never feeds applyNaScoreGate; it tells an
        // investigator whether a row's markers were resolved upstream or
        // synthesised by each runtime's own table).
        counterStyleBaked: fixture._wpt?.counterStyleBaked === true,
        // Wave 35 — the @font-face DELIVERY record, riding the same keyMap
        // channel as postLoadExtracted/structureExtracted (see those stamps
        // above). true ⇔ this test declared at least one usable face AND
        // every one of them reached the combined document unshadowed, so the
        // faces the ref shaped with are the faces our surfaces register.
        // inject-wpt-block.mjs's Rule 15 gate re-admits on exactly this.
        fontFacesDelivered: deliveredByTest.get(testRel) === true,
      };
      i++;
    }
  }

  const out = {
    // The combined fixture rides the existing pipeline; its `components`
    // dict is what gradle/test-all sees. The `_wpt` block is informational
    // — gradle ignores unknown top-level keys (kotlinx-serialization is
    // configured with `ignoreUnknownKeys = true`).
    _wpt: {
      smoke: true,
      keyMap: wptKeyMap,
      stats: {
        totalTests: tests.length,
        totalComponents: Object.keys(components).length,
        missingFixtures,
        // Wave 35 — how many DISTINCT faces the section's document registers.
        fontFaces: fontFaces.length,
      },
      // Wave 35 — every face a later test declared into an already-owned
      // (family, weight, style) slot. Empty in the overwhelming majority of
      // sections; when non-empty it names both sides so an investigator can
      // see WHICH file the capture actually shaped with. Omitted when empty so
      // the committed combined fixtures stay byte-identical for face-free
      // sections.
      ...(fontFaceConflicts.length ? { fontFaceConflicts } : {}),
    },
    // Wave 35 — the DOCUMENT-level face list (schema/spec/01-envelope.md §5).
    // Sits beside `components` because that is where the converter's
    // CssParsing reader looks (doc["fontFaces"]), and omit-when-empty keeps
    // every face-free section's combined fixture byte-identical to wave 34.
    ...(fontFaces.length ? { fontFaces } : {}),
    components,
  };

  await fs.mkdir(dirname(OUT_FILE), { recursive: true });
  await fs.writeFile(OUT_FILE, JSON.stringify(out, null, 2) + '\n', 'utf8');

  // A conflict is a SILENT wrong-typeface risk if it only lives in the JSON —
  // print it where the section log will carry it.
  for (const c of fontFaceConflicts) {
    process.stderr.write(
      `build-combined-fixture: WARNING @font-face family "${c.family}" already owned by ` +
      `${c.keptBy} (${c.kept}) — ${c.shadowedFor}'s ${c.shadowed} is SHADOWED and that test ` +
      `is stamped fontFacesDelivered:false\n`
    );
  }

  process.stderr.write(
    `build-combined-fixture: ${tests.length} tests → ${Object.keys(components).length} components ` +
    `(missing fixtures: ${missingFixtures}, font faces: ${fontFaces.length}` +
    `${fontFaceConflicts.length ? `, shadowed: ${fontFaceConflicts.length}` : ''}) → ${OUT_FILE}\n`
  );
}

// CLI only — see IS_CLI. An importer gets the helpers and nothing else runs.
if (IS_CLI) {
  main().catch((err) => {
    console.error('build-combined-fixture: fatal:', err);
    process.exit(2);
  });
}

// Exported for tests.
export { componentKey };
// Wave 35 lane B2 — the @font-face merge primitives, exported so the unit
// suite can pin the §4.1 slot rule and the case-insensitive family match
// without driving the whole file-reading main().
export { familyKey, faceSlotKey, faceIdentityKey, usableFace };

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

const TESTS_FILE = arg('--tests');
const OUT_FILE   = arg('--out');
if (!TESTS_FILE || !OUT_FILE) {
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
                         counterStyleBaked: false };
      continue;
    }

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
      },
    },
    components,
  };

  await fs.mkdir(dirname(OUT_FILE), { recursive: true });
  await fs.writeFile(OUT_FILE, JSON.stringify(out, null, 2) + '\n', 'utf8');

  process.stderr.write(
    `build-combined-fixture: ${tests.length} tests → ${Object.keys(components).length} components ` +
    `(missing fixtures: ${missingFixtures}) → ${OUT_FILE}\n`
  );
}

main().catch((err) => {
  console.error('build-combined-fixture: fatal:', err);
  process.exit(2);
});

// Exported for tests.
export { componentKey };

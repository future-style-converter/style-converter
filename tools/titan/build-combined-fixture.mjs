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
import { resolve, dirname, join, basename, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

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
 *  the keys present in the resulting fixture. */
function componentKey(testRel, childIndex) {
  const parts = testRel.split('/');
  const section = parts.length >= 3 ? parts[1] : 'css';
  const stem = basename(parts[parts.length - 1], '.html');
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
    const stem = basename(parts[parts.length - 1], '.html');
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
                         bucket: 'missing', lossy: false };
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

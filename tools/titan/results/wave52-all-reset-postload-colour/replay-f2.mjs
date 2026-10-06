#!/usr/bin/env node
// replay-f2.mjs — lane L11 (F2) executed census: run the REAL static
// extraction + post-load overlay (headless Chromium, the same
// postLoadAugmentFixture the gate's extract step calls) and report, per
// component, how its `color` key moved: `introduced` (the wave-52
// parent-differs route wrote a key the static bake lacked) or `repaired`
// (the wave-44 route, unchanged). Nothing is written outside this directory —
// fixtures stay in memory (writeFixturePair is never called).
//
// Exactness: on the plain overlay path a component can only GAIN a colour
// through the wave-52 route, so `introduced` IS the F2 delta. On the
// STRUCTURE path the static tree is re-extracted, so a test with structure
// + introduced rows is re-run with the route switched OFF
// (WRITE_RULES.color.introduceWhenParentDiffers = false) and only the ids
// whose colour differs between the two runs are kept.
//
// Usage: node replay-f2.mjs [--all-postload] [out.json] [css/…/test.html …]
//   --all-postload  every test the wave51-fix manifests stamp postLoadExtracted
//   (default list: the brief's F2 gainers + UA-tag carriers)
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const TITAN = join(HERE, '..', '..');
const RUN = join(TITAN, 'runs', 'wave51-fix', 'sections');
const { extractFixture } = await import(join(TITAN, 'extract-fixture.mjs'));
const { postLoadAugmentFixture, closePostLoadBrowser, WRITE_RULES } =
  await import(join(TITAN, 'post-load-extract.mjs'));

// The brief's F2 gainers + the UA-tag at-risk carriers.
const DEFAULT_TESTS = [
  'css/selectors/invalidation/nth-child-of-class.html',
  'css/selectors/invalidation/nth-child-of-class-prefix.html',
  'css/selectors/invalidation/nth-child-of-attr.html',
  'css/selectors/invalidation/nth-child-of-id-prefix.html',
  'css/selectors/invalidation/nth-child-of-in-is.html',
  'css/selectors/invalidation/nth-child-of-has.html',
  'css/selectors/invalidation/nth-child-of-ids.html',
  'css/selectors/invalidation/nth-child-containing-ancestor.html',
  'css/selectors/invalidation/negated-nth-child-when-ancestor-changes.html',
  'css/selectors/invalidation/negated-nth-last-child-when-ancestor-changes.html',
  'css/selectors/invalidation/class-id-attr.html',
  'css/css-color/currentcolor-004.html',
  'css/selectors/has-visited.html',
  'css/selectors/invalidation/any-link-attribute-removal.html',
];

// wave51-fix gate cells per manifest key (P/f + ssim per platform).
const CELLS = new Map();
for (const sec of readdirSync(RUN)) {
  const m = join(RUN, sec, 'manifest.json');
  if (!existsSync(m)) continue;
  const res = JSON.parse(readFileSync(m, 'utf8')).wpt?.results ?? {};
  for (const [key, v] of Object.entries(res)) {
    // One row per platform from the browser-ref diffs the scorer reads.
    const diffs = v.browserRef?.diffs ?? {};
    const cells = {};
    for (const p of ['web', 'ios', 'android']) {
      const d = diffs[`${p}-ref`];
      if (d) cells[p] = `${d.wptPass ? 'P' : 'f'} ${d.ssim}`;
    }
    CELLS.set(key, { section: sec, postLoad: !!v.postLoadExtracted, cells });
  }
}

const argv = process.argv.slice(2);
const all = argv.includes('--all-postload');
const rest = argv.filter((a) => a !== '--all-postload');
const out = rest[0]?.endsWith('.json') ? rest.shift() : join(HERE, all ? 'replay-f2-all.json' : 'replay-f2.json');
const tests = rest.length ? rest
  : all ? [...CELLS].filter(([, v]) => v.postLoad).map(([k]) => k).sort() : DEFAULT_TESTS;

/** id → component over the fixture's nested components/children maps. */
function flat(fixture) {
  const acc = new Map();
  const visit = (map) => { for (const [id, c] of Object.entries(map ?? {})) { acc.set(id, c); visit(c.children); } };
  visit(fixture.components);
  return acc;
}

/** One static + post-load pass with the route ON or OFF; returns colours by id. */
async function pass(rel, routeOn) {
  WRITE_RULES.color.introduceWhenParentDiffers = routeOn;     // the switch under test
  const result = await extractFixture(rel);                    // static bake
  const before = new Map([...flat(result.fixture)].map(([id, c]) => [id, c.properties?.color]));
  const outcome = await postLoadAugmentFixture(result.fixture, rel);
  const after = new Map([...flat(result.fixture)].map(([id, c]) => [id, { c, color: c.properties?.color }]));
  return { outcome, before, after };
}

const report = [];
try {
  for (const rel of tests) {
    let on;
    try { on = await pass(rel, true); } catch (e) { report.push({ rel, error: String(e.message ?? e) }); continue; }
    // Classify every component's colour movement against the static bake.
    let rows = [];
    for (const [id, { c, color }] of on.after) {
      const was = on.before.get(id);
      const kind = was === undefined && color !== undefined ? 'introduced'
        : was !== undefined && color !== was ? 'repaired' : null;
      if (kind) rows.push({ id, tag: c._tag ?? null, kind, was: was ?? null, now: color, text: (c._text ?? '').slice(0, 40) });
    }
    // Structure path: subtract what the re-extracted static tree already had.
    if (on.outcome.structure && rows.some((r) => r.kind === 'introduced')) {
      const off = await pass(rel, false);
      rows = rows.filter((r) => r.kind !== 'introduced' || off.after.get(r.id)?.color !== r.now);
    }
    const cell = CELLS.get(rel) ?? {};
    const introduced = rows.filter((r) => r.kind === 'introduced');
    report.push({ rel, section: cell.section ?? null, status: on.outcome.status,
      reason: on.outcome.reason ?? null, structure: !!on.outcome.structure,
      cells: cell.cells ?? {}, introduced: introduced.length, colour: rows });
    console.log(`${on.outcome.status.padEnd(9)} ${rel}  introduced=${introduced.length}` +
      `  ${JSON.stringify(cell.cells ?? {})}`);
  }
} finally {
  WRITE_RULES.color.introduceWhenParentDiffers = true;          // restore the module default
  await closePostLoadBrowser();
}
const gainers = report.filter((t) => t.introduced > 0);
writeFileSync(out, JSON.stringify({
  generated: new Date().toISOString(), run: 'wave51-fix', tests: report.length,
  extracted: report.filter((t) => t.status === 'extracted').length,
  errors: report.filter((t) => t.error).length,
  testsWithIntroducedColour: gainers.length,
  componentsIntroduced: gainers.reduce((n, t) => n + t.introduced, 0),
  byTest: report,
}, null, 1) + '\n');
console.log(`tests=${report.length} withIntroduced=${gainers.length} → ${out}`);

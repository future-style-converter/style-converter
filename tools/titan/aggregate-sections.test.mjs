#!/usr/bin/env node
//
// Unit tests for tools/titan/aggregate-sections.mjs.
//
// Mirrors the tools/titan/extract-fixture.test.mjs pattern: pure-function
// tests via node:test, no filesystem. The aggregator is the merge step
// that turns N per-section manifests into a unified TITAN-run manifest;
// the regressions we care about (key collisions, bucket sums, wall-clock
// math) are all exercised at the mergeManifests() level.
//
// Three required tests per the TITAN-IMPL-2A brief: empty input,
// single section, multi-section. We add a couple of regression cases
// (duplicate-key throws, summarize includes per-section table) so the
// aggregator's invariants stay pinned.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { mergeManifests, summarize, aggregate } from './aggregate-sections.mjs';

// Helper: build a minimal v4 manifest stub for a single section.
// Each test passes only the fields it cares about; the helper fills in
// the rest with defensible defaults so we don't have to repeat the v4
// boilerplate in every test.
function stub(section, {
  totalTests = 1, A = 1, B = 0, C = 0,
  results = null, skipped = null, rows = [],
  captureMs = 1000, ref = 'sha-aaa',
} = {}) {
  return {
    section,
    manifest: {
      manifestVersion: 4,
      generatedAt: '2026-05-11T00:00:00Z',
      inputLabel: `fixtures/wpt/_section-${section}.json`,
      thresholds: { ssim: 0.95, pixel: 2 },
      perPlatformProbes: null,
      wpt: {
        ref,
        runId: 'sec-run-id',
        totalTests,
        buckets: { A, B, C },
        duration: { captureMs, compareMs: null },
        // Default results map: one entry per A test, keyed under the
        // section so different sections produce disjoint keys.
        results: results ?? Object.fromEntries(
          Array.from({ length: A }, (_, i) => [
            `css/${section}/test-${i}.html`,
            { bucket: 'A', specSection: section, divergence: 'identical', pairs: null },
          ]),
        ),
        skipped: skipped ?? {},
      },
      rows,
    },
  };
}

// ── empty input ─────────────────────────────────────────────────────────────

test('mergeManifests on [] produces a syntactically valid v4 manifest', () => {
  // Phase-2 invariant: dashboards should not have to special-case "no
  // sections completed". An empty merge still yields a manifest object
  // that satisfies v4 shape.
  const m = mergeManifests([]);
  assert.equal(m.manifestVersion, 4);
  assert.equal(m.wpt.totalTests, 0);
  assert.deepEqual(m.wpt.buckets, { A: 0, B: 0, C: 0 });
  assert.deepEqual(m.wpt.results, {});
  assert.deepEqual(m.wpt.skipped, {});
  assert.deepEqual(m.wpt.sections, {});
  assert.deepEqual(m.rows, []);
});

test('mergeManifests on [] respects opts.runId', () => {
  const m = mergeManifests([], { runId: 'TITAN-2A-smoke' });
  assert.equal(m.wpt.runId, 'TITAN-2A-smoke');
});

// ── single section ─────────────────────────────────────────────────────────

test('mergeManifests on a single section preserves its counts', () => {
  // Section runner finishes 4 tests; aggregator should reflect that 1:1.
  const e = stub('css-color', { totalTests: 4, A: 4 });
  const m = mergeManifests([e], { runId: 'r1' });
  assert.equal(m.wpt.totalTests, 4);
  assert.deepEqual(m.wpt.buckets, { A: 4, B: 0, C: 0 });
  assert.equal(Object.keys(m.wpt.results).length, 4);
  assert.equal(m.wpt.runId, 'r1');
  assert.equal(m.wpt.ref, 'sha-aaa');
  assert.equal(m.thresholds.ssim, 0.95);
  // Per-section block must record the input section so the dashboard
  // can colour rows by source.
  assert.ok(m.wpt.sections['css-color']);
  assert.equal(m.wpt.sections['css-color'].totalTests, 4);
});

// ── multi-section ──────────────────────────────────────────────────────────

test('mergeManifests sums totals across multiple sections', () => {
  const entries = [
    stub('css-flexbox', { totalTests: 10, A: 9, B: 1, captureMs: 5000 }),
    stub('css-grid',    { totalTests: 12, A: 12,        captureMs: 8000 }),
    stub('css-color',   { totalTests:  3, A: 3,         captureMs: 1500 }),
  ];
  const m = mergeManifests(entries);
  assert.equal(m.wpt.totalTests, 25);
  assert.deepEqual(m.wpt.buckets, { A: 24, B: 1, C: 0 });
  // Each section's results must be present; total is the sum since the
  // namespaced keys don't collide.
  assert.equal(Object.keys(m.wpt.results).length, 24);
  // Wall-clock is the SLOWEST section (parallel run is bounded by it).
  assert.equal(m.wpt.duration.captureMs, 8000);
  assert.deepEqual(Object.keys(m.wpt.sections).sort(),
                   ['css-color', 'css-flexbox', 'css-grid']);
});

test('mergeManifests appends rows[] from each section', () => {
  // rows[] is the per-component pair table (Phase 1 leaves them as
  // section-namespaced; collision is structurally impossible). Aggregator
  // must concat all of them.
  const entries = [
    stub('css-flexbox', { rows: [{ name: 'wpt__css-flexbox__a__0.png' }] }),
    stub('css-grid',    { rows: [{ name: 'wpt__css-grid__b__0.png' },
                                  { name: 'wpt__css-grid__c__0.png' }] }),
  ];
  const m = mergeManifests(entries);
  assert.equal(m.rows.length, 3);
});

test('mergeManifests throws on duplicate result keys', () => {
  // Same test path appearing in two sections is an invariant violation —
  // the section namespace is supposed to be disjoint. Throw rather than
  // silently overwrite so the caller learns of the bug.
  const dup = { 'css/css-color/dup.html': { bucket: 'A', specSection: 'css-color', divergence: 'identical' } };
  const entries = [
    stub('css-color', { results: dup }),
    stub('css-color-2', { results: dup }),
  ];
  assert.throws(() => mergeManifests(entries), /duplicate result key/);
});

// ── summarize() ────────────────────────────────────────────────────────────

test('summarize produces a one-line summary plus per-section table', () => {
  const entries = [
    stub('css-color', { totalTests: 2, A: 2, captureMs: 500 }),
    stub('css-grid',  { totalTests: 3, A: 3, captureMs: 1500 }),
  ];
  const m = mergeManifests(entries);
  const { line, table } = summarize(m);
  // One line of headline stats.
  assert.match(line, /aggregated 2 sections/);
  assert.match(line, /5 tests/);
  assert.match(line, /A=5 B=0 C=0/);
  // Per-section table includes every section with its duration.
  assert.match(table, /css-color/);
  assert.match(table, /css-grid/);
  assert.match(table, /0\.5s/);
  assert.match(table, /1\.5s/);
});

test('summarize tolerates missing classifier data', () => {
  // Empty results -> no labels block; line should still be parseable.
  const m = mergeManifests([]);
  const { line } = summarize(m);
  assert.match(line, /no classifier data/);
});

// ── aggregate() race-bug filtering ──────────────────────────────────────────
//
// The 2026-05-11 race bug: section-runner.sh wrote v3 manifests (no wpt
// block) when killed between Step 6 (compare) and Step 7 (inject). The fix
// has two halves: section-runner stamps a skeleton beacon with
// `wpt.incomplete: true`, AND aggregate() must skip incomplete-or-missing
// wpt manifests with a loud warning rather than zero-filling them. These
// tests pin both halves so a future regression fails loudly.

async function writeSectionManifest(runDir, section, manifest) {
  const dir = join(runDir, 'sections', section);
  await fs.mkdir(dir, { recursive: true });
  await fs.writeFile(join(dir, 'manifest.json'), JSON.stringify(manifest, null, 2));
}

test('aggregate() skips manifests with wpt.incomplete:true (skeleton beacon)', async () => {
  // Setup: 2 healthy + 1 skeleton — the skeleton must be skipped, not summed.
  const runDir = await fs.mkdtemp(join(tmpdir(), 'titan-agg-skel-'));
  try {
    await writeSectionManifest(runDir, 'css-good-1', stub('css-good-1').manifest);
    await writeSectionManifest(runDir, 'css-good-2', stub('css-good-2').manifest);
    await writeSectionManifest(runDir, 'css-skeleton', {
      manifestVersion: 4,
      rows: [],
      // Skeleton shape from section-runner.sh Step 6.5 — no real results.
      wpt: {
        ref: 'sha-aaa', runId: 'partial', totalTests: 99,
        buckets: { A: 0, B: 0, C: 0 },
        results: {}, skipped: {},
        incomplete: true, incompleteReason: 'killed mid-run',
      },
    });

    // Capture stderr to assert the warning was emitted.
    const errs = [];
    const origWrite = process.stderr.write.bind(process.stderr);
    process.stderr.write = (chunk) => { errs.push(String(chunk)); return true; };
    let unified;
    try { unified = await aggregate(runDir); }
    finally { process.stderr.write = origWrite; }

    assert.equal(unified.wpt.totalTests, 2,
      'incomplete section must NOT contribute its 99 tests to the total');
    assert.equal(Object.keys(unified.wpt.sections).length, 2,
      'incomplete section must NOT appear in wpt.sections');
    assert.ok(!unified.wpt.sections['css-skeleton'],
      'incomplete section must be filtered out');
    assert.ok(errs.some(e => /css-skeleton.*incomplete:true/.test(e)),
      `expected stderr warning for css-skeleton, got: ${errs.join('')}`);
  } finally {
    await fs.rm(runDir, { recursive: true, force: true });
  }
});

test('aggregate() skips manifests missing the wpt block entirely (race-bug symptom)', async () => {
  // Pre-fix race-bug symptom: manifest exists but has no wpt block at all.
  // Aggregator must skip rather than treat as zero — that was the original
  // silent-invisible bug for css-backgrounds/css-overflow/css-sizing/css-break.
  const runDir = await fs.mkdtemp(join(tmpdir(), 'titan-agg-noblock-'));
  try {
    await writeSectionManifest(runDir, 'css-good', stub('css-good').manifest);
    // Pre-fix manifest — v3, no wpt field. This is exactly the shape
    // compare-screenshots.mjs writes; before the patch, inject-wpt-block
    // didn't run, so this is what landed.
    await writeSectionManifest(runDir, 'css-racey', {
      manifestVersion: 3,
      rows: [{ name: '000_test.png' }],
      // no `wpt` field at all
    });

    const errs = [];
    const origWrite = process.stderr.write.bind(process.stderr);
    process.stderr.write = (chunk) => { errs.push(String(chunk)); return true; };
    let unified;
    try { unified = await aggregate(runDir); }
    finally { process.stderr.write = origWrite; }

    assert.equal(unified.wpt.totalTests, 1);
    assert.ok(!unified.wpt.sections['css-racey']);
    assert.ok(errs.some(e => /css-racey.*no wpt block/.test(e)),
      `expected stderr warning for css-racey, got: ${errs.join('')}`);
  } finally {
    await fs.rm(runDir, { recursive: true, force: true });
  }
});

// ── wave-8: NA-excluded headline split ─────────────────────────────────────
//
// Corpus honesty: the headline line must split score-eligible tests from
// NA-tagged ones so "N tests" is never read as "N scored tests". A test is
// NA when inject-wpt-block stamped scoreEligible:false (or, for pre-wave-8
// manifests, when its divergence label is test-not-applicable).

test('wave8: summarize splits scored vs NA-excluded tests in the headline', () => {
  const results = {
    'css/css-break/ok.html':
      { bucket: 'A', specSection: 'css-break', divergence: 'identical', scoreEligible: true, pairs: null },
    'css/css-break/na-tagged.html':
      { bucket: 'A', specSection: 'css-break', divergence: 'test-not-applicable', scoreEligible: false, pairs: null },
    // Legacy manifest shape: no scoreEligible field, NA label only — must
    // still count as excluded (backfill path).
    'css/css-break/na-legacy.html':
      { bucket: 'A', specSection: 'css-break', divergence: 'test-not-applicable', pairs: null },
  };
  const m = mergeManifests([stub('css-break', { totalTests: 3, A: 3, results })]);
  const { line } = summarize(m);
  assert.match(line, /scored=1 NA-excluded=2/);
  // The distribution still reports the NA label separately.
  assert.match(line, /test-not-applicable: 2/);
});

test('wave8: summarize reports scored=total when nothing is NA-tagged', () => {
  const m = mergeManifests([stub('css-color', { totalTests: 2, A: 2 })]);
  const { line } = summarize(m);
  assert.match(line, /scored=2 NA-excluded=0/);
});

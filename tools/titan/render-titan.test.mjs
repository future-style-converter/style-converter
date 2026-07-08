#!/usr/bin/env node
//
// Unit tests for tools/titan/render-titan.mjs.
//
// Pure-helper tests via node:test. The renderer itself is exercised by
// CLI-running it against the real swarm-2b-w4 manifest (smoke check via
// `node tools/titan/render-titan.mjs --run swarm-2b-w4`); these tests
// pin the contract for the helpers a future refactor is most likely to
// break: the URL builder, the label-color picker, and the manifest
// aggregator's invariants on totals + per-section histograms.
//
// At least 3 tests required by the TITAN-IMPL-4 brief; we ship more so
// the regression-pin surface is wider.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  buildWptSourceUrl,
  labelOrder,
  titanBadgeColor,
  escapeHtml,
  aggregateManifest,
} from './render-titan.mjs';

// ── buildWptSourceUrl ─────────────────────────────────────────────────────

test('buildWptSourceUrl: returns null for null input', () => {
  assert.equal(buildWptSourceUrl(null, 'abc1234'), null);
  assert.equal(buildWptSourceUrl('', 'abc1234'), null);
});

test('buildWptSourceUrl: pins to ref SHA when ref looks like a git SHA', () => {
  const sha = '9b5435e55e0b54a6cd09c1c563861eb3c999cef1';
  const url = buildWptSourceUrl('css/css-grid/grid-area-005.html', sha);
  assert.equal(
    url,
    `https://github.com/web-platform-tests/wpt/blob/${sha}/css/css-grid/grid-area-005.html`
  );
});

test('buildWptSourceUrl: falls back to "master" when ref is missing or invalid', () => {
  // No ref supplied.
  assert.equal(
    buildWptSourceUrl('css/css-flexbox/flex-001.html', null),
    'https://github.com/web-platform-tests/wpt/blob/master/css/css-flexbox/flex-001.html'
  );
  // Non-SHA-shaped ref (e.g. accidentally pinned to a branch name with
  // an invalid character) — also falls back.
  assert.equal(
    buildWptSourceUrl('css/css-flexbox/flex-001.html', 'not a SHA!!'),
    'https://github.com/web-platform-tests/wpt/blob/master/css/css-flexbox/flex-001.html'
  );
});

test('buildWptSourceUrl: encodes path components but preserves slashes', () => {
  const url = buildWptSourceUrl('css/CSS2/path with spaces.html', 'abc1234');
  // Slashes are kept; spaces become %20.
  assert.equal(
    url,
    'https://github.com/web-platform-tests/wpt/blob/abc1234/css/CSS2/path%20with%20spaces.html'
  );
});

// ── labelOrder + titanBadgeColor ──────────────────────────────────────────

test('labelOrder: includes all 10 dashboard labels in canonical order', () => {
  const order = labelOrder();
  // Must include `no-data` (manifest-level, distinct from no-content).
  assert.ok(order.includes('no-data'), 'expected no-data in label order');
  // Sanity: "best" labels appear before "worst".
  assert.ok(order.indexOf('identical') < order.indexOf('structural-divergence'));
  // Pipeline-failure cluster sits at the end.
  assert.ok(order.indexOf('test-not-applicable') > order.indexOf('mixed'));
  assert.equal(order.length, 10);
});

test('titanBadgeColor: returns distinct color for no-data (not in classify-divergence)', () => {
  // `no-data` is dashboard-only — picked distinct from every classifier
  // label so the heatmap cluster reads correctly.
  assert.notEqual(titanBadgeColor('no-data'), titanBadgeColor('unknown'));
  assert.notEqual(titanBadgeColor('no-data'), titanBadgeColor('test-not-applicable'));
  // Defers to classify-divergence.badgeColor for known labels.
  assert.equal(titanBadgeColor('identical'), '#2d7a3a');
  assert.equal(titanBadgeColor('mixed'), '#c46a1f');
});

// ── escapeHtml ────────────────────────────────────────────────────────────

test('escapeHtml: escapes the standard 5 entities', () => {
  assert.equal(escapeHtml('a<b>&"\\\''), 'a&lt;b&gt;&amp;&quot;\\&#39;');
  assert.equal(escapeHtml(null), '');
  assert.equal(escapeHtml(undefined), '');
});

// ── aggregateManifest ─────────────────────────────────────────────────────

function stubManifest({
  ref = 'sha-aaa',
  runId = 'test-run',
  results = {},
} = {}) {
  return {
    manifestVersion: 4,
    generatedAt: '2026-05-15T00:00:00Z',
    inputLabel: 'titan-test',
    thresholds: { ssim: 0.95 },
    perPlatformProbes: null,
    wpt: {
      ref,
      runId,
      totalTests: Object.keys(results).length,
      buckets: { A: Object.keys(results).length, B: 0, C: 0 },
      duration: { captureMs: 60_000, compareMs: null },
      results,
    },
    rows: [],
  };
}

test('aggregateManifest: empty manifest returns zero totals', () => {
  const agg = aggregateManifest(stubManifest());
  assert.equal(agg.totalTests, 0);
  assert.equal(agg.sectionList.length, 0);
  assert.deepEqual(agg.bucketBreakdown, {});
});

test('aggregateManifest: handles null/missing input gracefully', () => {
  const agg = aggregateManifest(null);
  assert.equal(agg.totalTests, 0);
  assert.deepEqual(agg.labelHistogram, {});
});

test('aggregateManifest: sums labels and per-section histograms correctly', () => {
  const m = stubManifest({
    results: {
      'css/css-grid/grid-001.html': {
        bucket: 'A', specSection: 'css-grid',
        divergence: 'mixed', browserRefDivergence: { web: 'mixed' },
        notApplicableTags: [],
      },
      'css/css-grid/grid-002.html': {
        bucket: 'A', specSection: 'css-grid',
        divergence: 'structural-divergence', browserRefDivergence: { web: 'structural-divergence' },
        notApplicableTags: [],
      },
      'css/css-color/c-001.html': {
        bucket: 'A', specSection: 'css-color',
        divergence: 'identical',
        notApplicableTags: [],
      },
    },
  });
  const agg = aggregateManifest(m);
  assert.equal(agg.totalTests, 3);
  assert.equal(agg.labelHistogram['mixed'], 1);
  assert.equal(agg.labelHistogram['structural-divergence'], 1);
  assert.equal(agg.labelHistogram['identical'], 1);
  assert.equal(agg.sectionList.length, 2);
  const grid = agg.sectionList.find((s) => s.section === 'css-grid');
  assert.equal(grid.total, 2);
  assert.equal(grid.labels['mixed'], 1);
  assert.equal(grid.labels['structural-divergence'], 1);
});

test('aggregateManifest: backfills notApplicableTags from bucketsIdx', () => {
  const m = stubManifest({
    results: {
      'css/css-fonts/f-001.html': {
        bucket: 'A', specSection: 'css-fonts',
        divergence: 'mixed',
        notApplicableTags: [], // Empty in manifest…
      },
    },
  });
  const bucketsIdx = {
    'css/css-fonts/f-001.html': ['requires-font-face', 'requires-script-mutation'],
  };
  const agg = aggregateManifest(m, bucketsIdx);
  // Tag histogram picks up the two backfilled tags.
  assert.equal(agg.tagHistogram['requires-font-face'], 1);
  assert.equal(agg.tagHistogram['requires-script-mutation'], 1);
  // The test entry inside the section also carries the backfilled tags.
  const sec = agg.sectionList[0];
  assert.deepEqual(sec.tests[0].naTags, ['requires-font-face', 'requires-script-mutation']);
});

test('aggregateManifest: inline notApplicableTags wins over bucketsIdx', () => {
  // When the manifest has its own tag list, the backfill is a no-op —
  // the manifest is the truth source.
  const m = stubManifest({
    results: {
      'css/css-grid/g.html': {
        bucket: 'A', specSection: 'css-grid',
        divergence: 'test-not-applicable',
        notApplicableTags: ['requires-shadow-dom'],
      },
    },
  });
  const bucketsIdx = {
    'css/css-grid/g.html': ['requires-table-layout'], // would be wrong
  };
  const agg = aggregateManifest(m, bucketsIdx);
  // Inline value preserved; bucketsIdx not consulted.
  assert.deepEqual(agg.sectionList[0].tests[0].naTags, ['requires-shadow-dom']);
  assert.equal(agg.tagHistogram['requires-shadow-dom'], 1);
  assert.equal(agg.tagHistogram['requires-table-layout'], undefined);
});

test('aggregateManifest: tests within a section sort by severity (worst first)', () => {
  const m = stubManifest({
    results: {
      'css/css-grid/a.html': { bucket: 'A', specSection: 'css-grid', divergence: 'identical' },
      'css/css-grid/b.html': { bucket: 'A', specSection: 'css-grid', divergence: 'structural-divergence' },
      'css/css-grid/c.html': { bucket: 'A', specSection: 'css-grid', divergence: 'mixed' },
    },
  });
  const agg = aggregateManifest(m);
  const sec = agg.sectionList[0];
  // Severity: structural-divergence > mixed > identical (in labelOrder).
  // Note that `unknown` is special-cased to low severity in the
  // aggregator so we sanity-check on the concrete labels.
  assert.equal(sec.tests[0].path, 'css/css-grid/b.html'); // structural
  assert.equal(sec.tests[1].path, 'css/css-grid/c.html'); // mixed
  assert.equal(sec.tests[2].path, 'css/css-grid/a.html'); // identical
});

test('aggregateManifest: extracts ssim/labP95/pixelPct from browserRef.diffs.web-ref', () => {
  const m = stubManifest({
    results: {
      'css/css-color/x.html': {
        bucket: 'A', specSection: 'css-color', divergence: 'identical',
        browserRef: {
          available: true,
          path: '...',
          diffs: { 'web-ref': {
            ssim: 0.999, pixelMismatchedPct: 0.05,
            labDeltaE: { p95: 0.5 },
          }},
        },
      },
    },
  });
  const agg = aggregateManifest(m);
  const t = agg.sectionList[0].tests[0];
  assert.equal(t.ssim, 0.999);
  assert.equal(t.pixelPct, 0.05);
  assert.equal(t.labP95, 0.5);
});

test('aggregateManifest: counts buckets independently', () => {
  const m = stubManifest({
    results: {
      'a.html': { bucket: 'A', specSection: 's', divergence: 'mixed' },
      'b.html': { bucket: 'A', specSection: 's', divergence: 'mixed' },
      'c.html': { bucket: 'missing', specSection: 's', divergence: 'no-data' },
    },
  });
  const agg = aggregateManifest(m);
  assert.equal(agg.bucketBreakdown['A'], 2);
  assert.equal(agg.bucketBreakdown['missing'], 1);
});

test('aggregateManifest: preserves wptRef + runId + duration in output', () => {
  const m = stubManifest({ ref: 'deadbeef', runId: 'swarm-test' });
  const agg = aggregateManifest(m);
  assert.equal(agg.wptRef, 'deadbeef');
  assert.equal(agg.runId, 'swarm-test');
  assert.equal(agg.duration.captureMs, 60_000);
});

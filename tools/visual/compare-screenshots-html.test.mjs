#!/usr/bin/env node
// Report-honesty tests for tools/visual/compare-screenshots-html.mjs
// (LANE E, 2026-08-29).
//
// The lie these tests pin: renderHTML's headline used to count a row as
// "identical" via `.every()` over the row's NON-NULL pairs — and a
// one-platform run has ZERO non-null pairs (compare-screenshots.mjs sets
// `pairs[key] = null` when either capture is missing), so `.every()` on an
// empty array was vacuously true and a web-only run's report read
// "N components · N identical". A report that can say "identical" without
// comparing anything is the exact pass-while-wrong failure documented in
// docs/STATUS.md (2026-08-28). These tests feed renderHTML hand-built rows
// with KNOWN correct verdicts and assert on the rendered HTML string, so
// they fail if the counting logic regresses to vacuous truth.
//
// Run via `node --test tools/visual/compare-screenshots-html.test.mjs`
// (picked up by the smoke.sh glob `node --test tools/visual/*.test.mjs`).

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { renderHTML } from './compare-screenshots-html.mjs';

// ── Row builders ────────────────────────────────────────────────────────────
// Minimal-but-real row shapes matching what analyzeComponent() emits: a
// platforms block (present/width/height/image), a pairs block keyed
// 'iOS-Android' / 'iOS-web' / 'Android-web' (null when either side missing),
// and no baseline block (tests below pass useBaseline: false).

// One platform present → every pair is null. This is exactly what a
// SKIP_ANDROID=1 SKIP_IOS=1 (web-only) run produces per row.
function webOnlyRow(name) {
  return {
    name,
    platforms: {
      // web captured fine — the run itself is healthy, just alone.
      web: { present: true, width: 390, height: 120, image: `images/web/${name}` },
      // The two natives never ran, so their captures are absent.
      iOS: { present: false },
      Android: { present: false },
    },
    // All three pair slots null — nothing was comparable.
    pairs: { 'iOS-Android': null, 'iOS-web': null, 'Android-web': null },
  };
}

// Fully-compared row with every pair at the given SSIM. pixelMismatchedPct
// is always a number in real rows (diffPair computes it from W*H ≥ 1), so
// the builder mirrors that; divergence is whatever the classifier said.
function comparedRow(name, ssim, divergence = 'identical') {
  const pair = () => ({
    ssim,
    pixelMismatchedPct: 0.0,
    diffImage: `diffs/${name}__x.png`,
    divergence,
  });
  return {
    name,
    platforms: {
      web: { present: true, width: 390, height: 120, image: `images/web/${name}` },
      iOS: { present: true, width: 390, height: 120, image: `images/iOS/${name}` },
      Android: { present: true, width: 390, height: 120, image: `images/Android/${name}` },
    },
    pairs: { 'iOS-Android': pair(), 'iOS-web': pair(), 'Android-web': pair() },
  };
}

// Baseline opts bundle: everything renderHTML dereferences, nothing more.
// crossPlatformGate null exercises the "gate not evaluated" early return.
const OPTS = {
  useBaseline: false,
  ssimThreshold: 0.95,
  pixelThreshold: 2,
  regressionCount: 0,
  inputLabel: 'unit-test',
  perPlatformProbes: null,
  crossPlatformGate: null,
};

// Extract the whole headline <p class="meta"> block so assertions read the
// string a human reads, not internal state. The template wraps the counts
// across source lines inside one <p>, so match from "N components ·" up to
// the paragraph's closing tag rather than splitting on newlines.
function headline(html) {
  const m = html.match(/\d+ components ·[\s\S]*?<\/p>/);
  assert.ok(m, 'report is missing its headline meta block');
  return m[0];
}

// ── 1. The pinned lie: one-platform manifest must NOT read identical ───────
test('one-platform run: headline says not-compared, never identical', () => {
  // Two web-only rows — under the old vacuous-.every() logic this rendered
  // "2 components · 2 identical". The correct verdict is 0 identical.
  const html = renderHTML([webOnlyRow('000_A.png'), webOnlyRow('001_B.png')], OPTS);
  const line = headline(html);
  // The identical bucket must be exactly zero…
  assert.match(line, /\b0 identical/, `headline claims identical rows: ${line}`);
  // …and the not-compared bucket must own both rows, by name in the headline.
  assert.match(line, /\b2 not compared\b/, `headline hides the not-compared bucket: ${line}`);
});

// ── 2. Control: a genuinely-identical run still counts as identical ────────
// Without this control, test 1 could be satisfied by never counting anything
// as identical — the test suite must be able to fail in BOTH directions.
test('three-platform run at SSIM 0.99: counted identical, zero not-compared', () => {
  const html = renderHTML([comparedRow('000_A.png', 0.99)], OPTS);
  const line = headline(html);
  // The real comparison at 0.99 ≥ 0.97 lands in the identical bucket…
  assert.match(line, /\b1 identical/, `real identical row not counted: ${line}`);
  // …and the not-compared bucket is explicitly zero (printed, not omitted,
  // so absence-of-bucket can never masquerade as empty-bucket).
  assert.match(line, /\b0 not compared\b/, `not-compared bucket missing from headline: ${line}`);
});

// ── 3. A null-SSIM pair blocks the identical verdict ───────────────────────
test('row with one unscoreable pair is not identical', () => {
  // Two pairs score 0.99 but the third pair exists with ssim null (safeSsim
  // compute failure). Skipping it would repeat the vacuous-truth bug one
  // level down, so the verdict must be "not identical" (but still compared).
  const row = comparedRow('000_A.png', 0.99);
  row.pairs['Android-web'] = { ssim: null, pixelMismatchedPct: 0.0, diffImage: 'diffs/x.png', divergence: 'unknown' };
  const line = headline(renderHTML([row], OPTS));
  // Not identical — the failed metric poisons the verdict…
  assert.match(line, /\b0 identical/, `null-SSIM pair did not block identical: ${line}`);
  // …but the row WAS compared (two scoreable pairs), so not-compared stays 0.
  assert.match(line, /\b0 not compared\b/, `partially-scored row miscounted as not compared: ${line}`);
});

// ── 4. Row header must not fabricate "SSIM 1.000" for unscored rows ────────
test('not-compared row shows a badge, not a perfect SSIM', () => {
  const html = renderHTML([webOnlyRow('000_A.png')], OPTS);
  // The old fallback minSsim='1' rendered "SSIM 1.000" on a row that was
  // never scored — a fabricated perfect score. It must be gone…
  assert.ok(!html.includes('SSIM 1.000'), 'unscored row renders a fabricated SSIM 1.000');
  // …replaced by an explicit not-compared marker in the row header.
  assert.ok(html.includes('>not compared</span>'), 'unscored row missing its not-compared badge');
  // And the row must be visible under the "diffs only" filter — it carries
  // the not-compared class the client-side apply() treats as a problem row.
  assert.match(html, /class="row[^"]*\bnot-compared\b/, 'unscored row missing the not-compared row class');
});

// ── 5. Cross-platform gate: zero checked pairs is vacuous, not green ───────
test('gate summary with checked=0 is rendered as vacuous', () => {
  // A gate that evaluated nothing must not present "0 unexpected" as a pass.
  const g = { skipped: false, checked: 0, unexpected: [], expected: [], stale: [], expired: [] };
  const html = renderHTML([webOnlyRow('000_A.png')], { ...OPTS, crossPlatformGate: g });
  // The explicit vacuity note must appear where the counts are read…
  assert.ok(html.includes('gate is vacuous'), 'zero-pair gate not flagged as vacuous');
  // …and the unexpected-count chip must be amber (warn), not green (ok).
  assert.match(html, /<span class="warn">0 unexpected<\/span>/, 'zero-pair gate renders a green pass chip');
  // Regression guard for the `${g.checked}` interpolation: an absent field
  // used to print the literal string "undefined pair(s)".
  const g2 = { skipped: false, unexpected: [], expected: [], stale: [], expired: [] };
  const html2 = renderHTML([], { ...OPTS, crossPlatformGate: g2 });
  assert.ok(!html2.includes('undefined pair'), 'gate summary prints "undefined pair(s)"');
});

// ── 6. Baseline cell with null SSIM must not tint green ────────────────────
test('baseline cell with null SSIM renders warn, not ok', () => {
  const row = comparedRow('000_A.png', 0.99);
  // A baseline comparison whose SSIM computation failed: regressed=false
  // (pairRegressed had nothing to judge) — the old severity chain fell
  // through pixelMismatchedPct 0.0 to a green 'ok' cell.
  row.baseline = {
    regressed: false,
    platforms: {
      iOS: { present: true, ssim: null, pixelMismatchedPct: 0.0, diffImage: 'diffs/b.png', regressed: false },
      Android: { present: false },
      web: { present: false },
    },
  };
  const html = renderHTML([row], { ...OPTS, useBaseline: true });
  // The failed-metric cell must carry the warn tier…
  assert.match(html, /<div class="pair warn">\s*<div class="ptitle">iOS vs baseline/, 'null-SSIM baseline cell is not warn');
  // …and no baseline cell in this report may claim the ok tier.
  assert.ok(!/<div class="pair ok">\s*<div class="ptitle">iOS vs baseline/.test(html), 'null-SSIM baseline cell tinted green');
});

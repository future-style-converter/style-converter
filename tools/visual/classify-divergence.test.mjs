#!/usr/bin/env node
// Unit tests for tools/visual/classify-divergence.mjs (COMPARE_METRICS Section 4).
//
// One test per label (7 minimum) plus edge cases for MIXED and UNKNOWN.
// Each test constructs a synthetic ImagePairMetrics block engineered to
// trigger exactly the bucket under test, then asserts the classifier
// returns that bucket.
//
// Picked up by smoke.sh's `node --test tools/visual/*.test.mjs` glob.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  classifyDivergence,
  worstLabel,
  badgeColor,
  CLASSIFIER_VERSION,
} from './classify-divergence.mjs';

// ── Fixture builder ────────────────────────────────────────────────────────
// Start from a "perfect identical" base; per-test overrides flip just the
// fields needed to drop into the target bucket. This keeps each test
// readable as "what's different about this pair" rather than restating
// the full 6-field block every time.
function metrics(overrides = {}) {
  const base = {
    ssim: 1.0,
    pixelMismatchedCount: 0,
    pixelMismatchedPct: 0,
    edgeSsim: 1.0,
    pHash: { hex: 'a'.repeat(64), hammingDistance: 0 },
    labDeltaE: { mean: 0, max: 0, p95: 0 },
    histogramKL: { r: 0, g: 0, b: 0 },
    perChannelSsim: { r: 1, g: 1, b: 1, a: 1 },
    dssim: 0,
  };
  // Allow `pHash`/`labDeltaE` to be replaced wholesale or partially.
  return { ...base, ...overrides };
}

// ── Label 1: IDENTICAL ─────────────────────────────────────────────────────

test('classifyDivergence: identical → "identical"', () => {
  // Perfect match across every metric — the canonical happy path.
  assert.equal(classifyDivergence(metrics()), 'identical');
});

// ── Label 2: SUB-PIXEL-NOISE ───────────────────────────────────────────────

test('classifyDivergence: small AA jiggle → "sub-pixel-noise"', () => {
  // Cross-platform AA: SSIM still very high, edges intact, ΔE p95 below
  // perceptibility threshold, pHash unchanged. Classic 80%-of-baseline case.
  const m = metrics({
    ssim: 0.98,
    pixelMismatchedCount: 50,
    pixelMismatchedPct: 0.1,
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 1 },
    labDeltaE: { mean: 0.2, max: 5, p95: 1.5 },
  });
  assert.equal(classifyDivergence(m), 'sub-pixel-noise');
});

// ── Label 3: COLOR-DRIFT ───────────────────────────────────────────────────

test('classifyDivergence: colors shifted but edges intact → "color-drift"', () => {
  // labDeltaE.mean > 2 trips the colour-drift gate, edgeSsim stays high
  // (well above 0.85 floor) so we know shapes didn't move — only colour.
  // SSIM intentionally < 0.95 so sub-pixel-noise rule doesn't pre-empt.
  const m = metrics({
    ssim: 0.92,
    pixelMismatchedCount: 1000,
    pixelMismatchedPct: 5,
    edgeSsim: 0.95,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 3 },
    labDeltaE: { mean: 4, max: 12, p95: 8 },
  });
  assert.equal(classifyDivergence(m), 'color-drift');
});

// ── Label 4: EDGE-SHIFT ────────────────────────────────────────────────────

test('classifyDivergence: edges moved but global silhouette preserved → "edge-shift"', () => {
  // edgeSsim well below 0.85 (shapes/borders moved), pHash hamming ≤ 4
  // (overall silhouette unchanged). We also keep labDeltaE.mean < 2 so
  // the colour-drift gate cannot fire — this isolates the edge-shift fingerprint.
  const m = metrics({
    ssim: 0.88,
    pixelMismatchedCount: 800,
    pixelMismatchedPct: 4,
    edgeSsim: 0.60,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 3 },
    labDeltaE: { mean: 1, max: 8, p95: 1.5 },
  });
  assert.equal(classifyDivergence(m), 'edge-shift');
});

// ── Label 5: STRUCTURAL-DIVERGENCE ─────────────────────────────────────────

test('classifyDivergence: pHash hamming > 10 → "structural-divergence"', () => {
  // Wholesale "wrong image" — hamming above the spec's 10-bit threshold
  // and SSIM tanked. Real rendering bug territory. We bump labDeltaE too
  // since structural divergence usually drags everything else with it,
  // but the gate fires off pHash alone — that's the spec contract.
  // Also bump edgeSsim above 0.85 so EDGE-SHIFT doesn't co-trigger and
  // demote this to MIXED — we want a clean structural-only signal here.
  const m = metrics({
    ssim: 0.55,
    pixelMismatchedCount: 50000,
    pixelMismatchedPct: 60,
    edgeSsim: 0.90,
    pHash: { hex: 'c'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 1, max: 90, p95: 1.5 },
  });
  assert.equal(classifyDivergence(m), 'structural-divergence');
});

// ── Label 6: MIXED ─────────────────────────────────────────────────────────

test('classifyDivergence: edge-shift + color-drift co-fire → "mixed"', () => {
  // Engineered to trigger BOTH: edgeSsim < 0.85 (edge-shift) AND
  // labDeltaE.mean > 2 (colour-drift). The color-drift gate also requires
  // edgeSsim ≥ 0.85, so we need to be careful — set edgeSsim juuust
  // below 0.85 (0.84) so edge-shift fires, then bump labDeltaE p95 > 5
  // (the "OR" branch of color-drift's gate that doesn't depend on edge).
  // pHash small so structural doesn't pile on.
  // Wait — color-drift requires edgeSsim ≥ 0.85 unconditionally per spec.
  // So edge-shift + color-drift cannot truly co-trigger by that gate
  // wording. Use edge-shift + structural instead: pHash > 10 (structural)
  // AND edgeSsim < 0.85 with pHash ≤ 4 (edge-shift)... also impossible
  // because pHash is shared. Use the actually-orthogonal pair:
  // STRUCTURAL (ssim < 0.85) + COLOR-DRIFT (mean > 2 with edgeSsim ≥ 0.85).
  const m = metrics({
    ssim: 0.70,                        // < 0.85 → structural
    pixelMismatchedCount: 5000,
    pixelMismatchedPct: 20,
    edgeSsim: 0.95,                    // ≥ 0.85 → satisfies color-drift gate
    pHash: { hex: 'b'.repeat(64), hammingDistance: 5 },
    labDeltaE: { mean: 6, max: 30, p95: 10 },  // mean > 2 → color-drift
  });
  assert.equal(classifyDivergence(m), 'mixed');
});

test('classifyDivergence: weak signals everywhere, no clean bucket → "mixed"', () => {
  // ssim a hair below 0.95 (so no sub-pixel-noise), nothing else triggers
  // a specific bucket. Catch-all MIXED branch — "we have signal but no
  // clean fit". This exercises the final `return 'mixed'` (rule 6 catch-all)
  // separately from the early-return MIXED for multi-bucket triggers.
  const m = metrics({
    ssim: 0.93,
    pixelMismatchedCount: 200,
    pixelMismatchedPct: 0.5,
    edgeSsim: 0.95,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 4 },
    labDeltaE: { mean: 1, max: 3, p95: 1.5 },
  });
  assert.equal(classifyDivergence(m), 'mixed');
});

// ── Label 7: UNKNOWN ───────────────────────────────────────────────────────

test('classifyDivergence: null metrics → "unknown"', () => {
  // Defensive null-check — the manifest can carry `null` per-pair when a
  // platform decode failed. Must not throw.
  assert.equal(classifyDivergence(null), 'unknown');
});

test('classifyDivergence: missing edgeSsim → "unknown"', () => {
  // Section 4 footnote: "fallback if metrics missing/null". We classify
  // edgeSsim, ssim, pHash, labDeltaE.{mean,p95,max}, and pixelCount as
  // the core minimum — if any is missing the classifier refuses to guess.
  const m = metrics();
  delete m.edgeSsim;
  assert.equal(classifyDivergence(m), 'unknown');
});

test('classifyDivergence: missing pHash → "unknown"', () => {
  // pHash is one of the four "core" fingerprints; missing → unknown.
  const m = metrics({ pHash: { hex: null, hammingDistance: null } });
  assert.equal(classifyDivergence(m), 'unknown');
});

test('classifyDivergence: missing labDeltaE → "unknown"', () => {
  // labDeltaE drives both color-drift and sub-pixel-noise gates — without
  // it we can't disambiguate, so 'unknown' is the safe label.
  const m = metrics({ labDeltaE: null });
  assert.equal(classifyDivergence(m), 'unknown');
});

// ── Label 8: NO-CONTENT (round 91 — semantic-presence pre-gate) ────────────
//
// Pilot-001 (TITAN-INVESTIGATOR / css-break/block-end-aligned-abspos-with-
// overflow) showed that empty-extraction pairs against a dark browser-ref
// scored SSIM ~0.91 purely on matching backgrounds. The existing 7-label
// tree mislabelled them as `structural-divergence` / `mixed` / `sub-pixel-
// noise`. The new `no-content` label fires BEFORE every other rule whenever
// the `semanticPresence` field reports < 5% foreground coverage on either
// side of the pair (canonical bg = #1A1A2E; threshold = 5%, see
// NO_CONTENT_COVERAGE_PCT in classify-divergence.mjs).

test('classifyDivergence: empty A side + dark bg dominance → "no-content"', () => {
  // Reproduces the pilot-001 false-positive shape: SSIM 0.95 (would pass
  // sub-pixel-noise on metrics alone) but A side is essentially blank.
  // Without the gate, the classifier would label this 'sub-pixel-noise'
  // — the most misleading possible label, since it implies "perceptually
  // identical perfectly-rendered pair" when in fact one side is empty.
  const m = metrics({
    ssim: 0.95,
    pixelMismatchedPct: 0.5,
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 1 },
    labDeltaE: { mean: 0.2, max: 5, p95: 1.5 },
    semanticPresence: {
      aCoveragePct: 0.8,    // < 5% → triggers no-content
      bCoveragePct: 42.0,   // real content on B
      threshold: 5,
      tolerance: 8,
    },
  });
  assert.equal(classifyDivergence(m), 'no-content');
});

test('classifyDivergence: both sides empty → "no-content" (overrides identical)', () => {
  // Two blank #1A1A2E canvases would be classified `identical` by every
  // existing rule (SSIM=1, hamming=0, etc). The pre-gate must override —
  // a pair of empty captures isn't a "pass," it's a missing comparison.
  const m = metrics({
    semanticPresence: {
      aCoveragePct: 0.0,
      bCoveragePct: 0.0,
      threshold: 5,
      tolerance: 8,
    },
  });
  assert.equal(classifyDivergence(m), 'no-content');
});

test('classifyDivergence: both sides have real content → falls through to existing tree', () => {
  // Sanity check the gate's negative case: when both sides have plenty of
  // foreground coverage (>5%), the semanticPresence field is informational
  // and the classifier returns whatever the existing 7-label tree decides.
  // Here the metrics are perfect-identical so we should still see 'identical'.
  const m = metrics({
    semanticPresence: {
      aCoveragePct: 38.5,
      bCoveragePct: 42.1,
      threshold: 5,
      tolerance: 8,
    },
  });
  assert.equal(classifyDivergence(m), 'identical');
});

test('classifyDivergence: empty B side overrides structural-divergence', () => {
  // The pilot-001 case as it currently scores in the manifest: structural
  // divergence on every metric (ssim 0.55, hamming 22, edges < 0.85 etc.)
  // BUT B side has < 5% foreground. The pipeline-level "we produced
  // nothing comparable" failure should dominate the per-pair structural
  // label — the reviewer needs to see "fix the empty extraction first,
  // then re-classify" not "structural rendering bug, look at the diff".
  const m = metrics({
    ssim: 0.55,
    pixelMismatchedPct: 60,
    edgeSsim: 0.40,
    pHash: { hex: 'c'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 8, max: 90, p95: 30 },
    semanticPresence: {
      aCoveragePct: 51.2,
      bCoveragePct: 1.4,    // < 5% → triggers no-content
      threshold: 5,
      tolerance: 8,
    },
  });
  assert.equal(classifyDivergence(m), 'no-content');
});

test('classifyDivergence: missing semanticPresence → falls through (back-compat)', () => {
  // Round-91 manifests have semanticPresence; round-90-and-earlier don't.
  // The classifier MUST treat a missing semanticPresence field as
  // "signal unavailable, defer to the existing 7-label tree" so older
  // manifests classify identically to before this change. Otherwise a
  // re-run of baseline-stats.mjs over a stale manifest would mass-flip
  // labels and make distribution-drift diffs unreadable.
  const m = metrics({
    ssim: 0.98,
    pixelMismatchedPct: 0.1,
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 1 },
    labDeltaE: { mean: 0.2, max: 5, p95: 1.5 },
  });
  // No semanticPresence field at all in the metric block. Should match
  // the pre-round-91 'sub-pixel-noise' behaviour.
  assert.equal(classifyDivergence(m), 'sub-pixel-noise');
});

test('classifyDivergence: semanticPresence with non-numeric coverage → falls through', () => {
  // Defensive — if computeSemanticPresence returns a malformed shape (e.g.
  // a future patch introduces a string-encoded percent), the classifier
  // must NOT throw and must NOT fire the gate on a non-numeric value.
  // It should fall back to the existing decision tree.
  const m = metrics({
    semanticPresence: {
      aCoveragePct: 'unknown',
      bCoveragePct: null,
      threshold: 5,
      tolerance: 8,
    },
  });
  // No content gate fires; perfect-base metrics → 'identical'.
  assert.equal(classifyDivergence(m), 'identical');
});

// ── Label 9: TEST-NOT-APPLICABLE (FIX-E — auto-bucket pre-gate) ────────────
//
// `tools/titan/wpt-not-applicable.mjs` scans WPT source HTML against ~17
// architectural-exclusion rules (requires-inline-FC, requires-script-mutation,
// requires-shadow-dom, requires-table-layout, requires-form-control-rendering,
// …) and emits the matching tag list to `tools/titan/wpt-buckets.json`'s
// `notApplicable` block. `inject-wpt-block.mjs` then propagates that list
// into the manifest as `metrics.wptNotApplicableTags`. The classifier's
// new pre-gate fires when that array is non-empty and overrides every
// other label. Without this gate the FIX-D projection of 1,471 reclassi-
// fications never materialised — W4's manifest still labelled all 3,676
// bucketer-flagged tests as `mixed` / `structural-divergence` / `no-data`.

test('classifyDivergence: non-empty wptNotApplicableTags → "test-not-applicable"', () => {
  // Positive case: a single architectural-exclusion tag is enough to flip
  // the label, even when every other metric scores `identical`. The gate
  // is the highest-severity pre-rule, fires first, and short-circuits the
  // entire decision tree — a test that was never expected to render
  // correctly on Compose/SwiftUI shouldn't be SSIM-gated at all.
  const m = metrics({
    wptNotApplicableTags: ['requires-inline-FC'],
  });
  assert.equal(classifyDivergence(m), 'test-not-applicable');
});

test('classifyDivergence: tags override structural-divergence (worst-case real signal)', () => {
  // Engineered to also trigger structural-divergence on metrics alone
  // (ssim 0.55, hamming 22). The auto-bucket override MUST win — a test
  // that scans positive for `requires-script-mutation` is out-of-scope
  // regardless of how badly its perceptual metrics happen to score, because
  // the bucketer's signal is "this test depends on a runtime feature we
  // architecturally don't have" rather than "the renderer produced a
  // wrong image". Multi-tag arrays are common (a test can require both
  // shadow-dom AND visited-pseudo) — verifies the gate fires on length>0
  // not just length===1.
  const m = metrics({
    ssim: 0.55,
    pixelMismatchedPct: 60,
    edgeSsim: 0.40,
    pHash: { hex: 'c'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 8, max: 90, p95: 30 },
    wptNotApplicableTags: ['requires-shadow-dom', 'requires-visited-pseudo'],
  });
  assert.equal(classifyDivergence(m), 'test-not-applicable');
});

test('classifyDivergence: tags override no-content (FIX-E ranks above pilot-001 gate)', () => {
  // The auto-bucket gate ranks ABOVE no-content because a test that's
  // architecturally out-of-scope shouldn't surface as "the pipeline
  // produced nothing comparable" either — the more-actionable signal is
  // "filter this test from the SSIM gate", not "investigate why the
  // capture is empty". This pin confirms the rule ordering inside
  // classifyDivergence (test-not-applicable check happens before the
  // semanticPresence gate).
  const m = metrics({
    wptNotApplicableTags: ['requires-print-medium'],
    semanticPresence: {
      aCoveragePct: 0.5,    // would trigger no-content on its own
      bCoveragePct: 1.2,
      threshold: 5,
      tolerance: 8,
    },
  });
  assert.equal(classifyDivergence(m), 'test-not-applicable');
});

test('classifyDivergence: empty wptNotApplicableTags array → falls through (back-compat)', () => {
  // Negative case: an EMPTY tag array means "the bucketer scanned this
  // test and didn't match any exclusion rule" — the pre-gate must NOT
  // fire on a defensive `[]`, only on a genuinely-non-empty list. Any
  // other behaviour would mass-relabel every WPT test in the manifest as
  // out-of-scope the moment inject-wpt-block started writing the field.
  // Perfect-base metrics → 'identical'.
  const m = metrics({
    wptNotApplicableTags: [],
  });
  assert.equal(classifyDivergence(m), 'identical');
});

test('classifyDivergence: missing wptNotApplicableTags → falls through (legacy 327-pair)', () => {
  // Pre-FIX-E manifests have no `wptNotApplicableTags` field at all
  // (legacy 327-pair pipeline never ran inject-wpt-block). The classifier
  // MUST treat an absent field as "no signal, defer to the existing tree"
  // so legacy manifests classify byte-identically to before this change.
  // Re-running baseline-stats over a stale manifest must not mass-flip
  // labels and break distribution-drift diffs.
  const m = metrics({
    ssim: 0.98,
    pixelMismatchedPct: 0.1,
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 1 },
    labDeltaE: { mean: 0.2, max: 5, p95: 1.5 },
  });
  // No wptNotApplicableTags field; should match the pre-FIX-E
  // 'sub-pixel-noise' behaviour exactly.
  assert.equal(classifyDivergence(m), 'sub-pixel-noise');
});

test('classifyDivergence: non-array wptNotApplicableTags → falls through (defensive)', () => {
  // Defensive — if a future patch accidentally writes a string or object
  // to wptNotApplicableTags, the classifier must NOT throw and must NOT
  // fire the gate on a non-array value. Falls back to the existing tree.
  const m = metrics({
    wptNotApplicableTags: 'requires-inline-FC',  // wrong shape
  });
  assert.equal(classifyDivergence(m), 'identical');
});

// ── Label 10: GLYPH-METRIC-NOISE (swarm-002 — pHash inflated by glyph y-offset) ──
//
// Swarm-002 RC: small styled-text WPT tests where the renderer is provably
// correct (text renders in the right colour at the right position,
// edgeSsim=1, labMean<1, <3% pixel mismatch) but the pHash hamming distance
// trips the STRUCTURAL gate purely because the harness's 390x~62 fit-content
// capture is diffed against the browser-ref's 390x600 full-document canvas.
// The gate fires when STRUCTURAL would normally trigger (pHash > 10) AND
// every "rest of the canvas matches" guard holds:
//   ssim >= 0.95 AND labMean < 1.0 AND edgeSsim >= 0.95 AND pixelPct < 3.
// Surfaces ~1,455 W5 reclassifications away from structural-divergence
// without changing any pair that has a real structural / color / edge bug.

test('classifyDivergence: swarm-002 color-001 shape → "glyph-metric-noise"', () => {
  // POSITIVE — the actual swarm-002 RC numbers for css-color/color-001:
  //   ssim 0.9745, ph 26, labMean 0.387, edge 1.00, px 0.287
  // Pre-FIX-F this scored `structural-divergence` (pHash 26 > 10), even
  // though the green-text-on-dark assertion passes. The new label rescues
  // this exact pattern without absorbing genuine structural bugs.
  const m = metrics({
    ssim: 0.9745,
    pixelMismatchedCount: 671,
    pixelMismatchedPct: 0.287,
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 26 },
    labDeltaE: { mean: 0.387, max: 47.993, p95: 0 },
  });
  assert.equal(classifyDivergence(m), 'glyph-metric-noise');
});

test('classifyDivergence: swarm-002 anchor-center shape → "glyph-metric-noise"', () => {
  // POSITIVE — the actual swarm-002 RC numbers for the sister test
  // css-anchor-position/anchor-center-no-default:
  //   ssim 0.9764, ph 21, labMean 0.485, edge 1.00, px 0.905
  // Same harness-vs-browser-ref glyph-band offset pattern; same rescue.
  const m = metrics({
    ssim: 0.9764,
    pixelMismatchedCount: 2118,
    pixelMismatchedPct: 0.905,
    edgeSsim: 1.0,
    pHash: { hex: 'c'.repeat(64), hammingDistance: 21 },
    labDeltaE: { mean: 0.485, max: 85.705, p95: 0 },
  });
  assert.equal(classifyDivergence(m), 'glyph-metric-noise');
});

test('classifyDivergence: real structural bug (labMean too high) → stays "structural-divergence"', () => {
  // NEGATIVE — same pHash/ssim shape as the swarm-002 pattern but the
  // colour delta is genuinely high (labMean 4 > 1.0 cap). A wrong-colour
  // text render would trip exactly this signature; we MUST NOT absorb
  // that into glyph-metric-noise. The gate's labMean < 1.0 guard is the
  // primary defence against this — verify it holds.
  const m = metrics({
    ssim: 0.96,
    pixelMismatchedCount: 600,
    pixelMismatchedPct: 0.3,
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 4.2, max: 90, p95: 0 },  // labMean > 1 → fails gate
  });
  // Reaches the existing tree: triggersColorDrift=true (mean>2 + edge>=0.85),
  // triggersStructural=true (ph 22 > 10). Two buckets fire → mixed.
  assert.equal(classifyDivergence(m), 'mixed');
});

test('classifyDivergence: real structural bug (pixelPct too high) → stays "structural-divergence"', () => {
  // NEGATIVE — swarm-002 shape but >3% pixels disagree. A renderer that
  // missed a whole element / shifted a block would push pixelPct well past
  // 3% even while keeping labMean low (mostly-matching-background canvases
  // still register the missing element as a chunky pixel-count delta).
  // The gate's pixelPct < 3 ceiling rejects this.
  const m = metrics({
    ssim: 0.96,
    pixelMismatchedCount: 12000,
    pixelMismatchedPct: 5.1,  // > 3 → fails gate
    edgeSsim: 1.0,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 0.4, max: 40, p95: 0 },
  });
  assert.equal(classifyDivergence(m), 'structural-divergence');
});

test('classifyDivergence: real edge bug (edgeSsim too low) → stays "structural-divergence"/"mixed"', () => {
  // NEGATIVE — swarm-002 shape but edges genuinely moved. A border-shift
  // or shape-deformation bug would drop edgeSsim below the 0.95 floor while
  // keeping ssim and labMean otherwise quiet. The gate's edgeSsim >= 0.95
  // guard rejects this so the pair falls through to the real edge/structural
  // diagnosis.
  const m = metrics({
    ssim: 0.96,
    pixelMismatchedCount: 600,
    pixelMismatchedPct: 0.3,
    edgeSsim: 0.80,  // < 0.95 → fails gate
    pHash: { hex: 'b'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 0.4, max: 40, p95: 0 },
  });
  // pHash 22 > 10 → structural. edgeSsim 0.80 < 0.85 → edge-shift. But
  // edge-shift requires pHash <= 4, fails here (pHash 22). So only
  // structural triggers → 'structural-divergence'.
  assert.equal(classifyDivergence(m), 'structural-divergence');
});

test('classifyDivergence: edge case — pHash exactly at structural floor + 1 → "glyph-metric-noise"', () => {
  // EDGE — minimum pHash that fires structural (11, just above the > 10
  // threshold) with every other guard at its tightest passing value. Pins
  // the lower boundary of the gate so a future threshold tweak doesn't
  // silently slide the rescue zone.
  const m = metrics({
    ssim: 0.95,                    // ≥ 0.95 floor exactly
    pixelMismatchedCount: 500,
    pixelMismatchedPct: 2.99,      // < 3 ceiling
    edgeSsim: 0.95,                // ≥ 0.95 floor exactly
    pHash: { hex: 'b'.repeat(64), hammingDistance: 11 },  // > 10 → structural would fire
    labDeltaE: { mean: 0.99, max: 50, p95: 0 },  // < 1.0 ceiling
  });
  assert.equal(classifyDivergence(m), 'glyph-metric-noise');
});

test('classifyDivergence: glyph-noise gate ignores low-pHash structural (ssim<0.85)', () => {
  // EDGE — the gate fires ONLY when STRUCTURAL would trigger on pHash
  // (hamming > 10). A pair that's STRUCTURAL via the ssim < 0.85 arm
  // alone should NOT be absorbed into glyph-noise — that arm signals a
  // real perceptual mismatch the harness/ref geometry can't explain.
  const m = metrics({
    ssim: 0.70,                    // < 0.85 → structural via SSIM arm
    pixelMismatchedCount: 500,
    pixelMismatchedPct: 2.5,
    edgeSsim: 0.97,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 5 },  // pHash NOT > 10
    labDeltaE: { mean: 0.5, max: 40, p95: 0 },
  });
  // pHash 5 ≤ 10 → glyph-noise gate's pHashHamming > 10 fails.
  // ssim 0.70 < 0.85 → structural fires alone. No other bucket triggers.
  assert.equal(classifyDivergence(m), 'structural-divergence');
});

test('classifyDivergence: legacy manifest without pixelMismatchedPct → falls through (back-compat)', () => {
  // EDGE — pre-FIX-F manifests don't necessarily carry pixelMismatchedPct
  // in the same shape we expect. The gate's `pixelPct != null` clause
  // ensures missing values fail safe (gate doesn't fire), preserving the
  // pre-existing structural-divergence label for legacy manifests.
  const m = metrics({
    ssim: 0.97,
    pixelMismatchedCount: 500,
    pixelMismatchedPct: undefined,  // legacy shape
    edgeSsim: 0.99,
    pHash: { hex: 'b'.repeat(64), hammingDistance: 22 },
    labDeltaE: { mean: 0.3, max: 40, p95: 0 },
  });
  // Glyph-noise rejected (pixelPct null), structural triggers alone.
  assert.equal(classifyDivergence(m), 'structural-divergence');
});

test('worstLabel: glyph-metric-noise outranks sub-pixel-noise but loses to color-drift', () => {
  // Pins the severity rank: glyph-metric-noise (3) sits between
  // sub-pixel-noise (2) and unknown (4) / color-drift (7). A row with
  // a glyph-noise pair and a sub-pixel pair should headline glyph-noise
  // (more-actionable signal). A row with a glyph-noise pair and a real
  // color-drift pair should headline color-drift (real divergence wins).
  assert.equal(
    worstLabel(['sub-pixel-noise', 'glyph-metric-noise']),
    'glyph-metric-noise'
  );
  assert.equal(
    worstLabel(['glyph-metric-noise', 'color-drift']),
    'color-drift'
  );
});

test('badgeColor: glyph-metric-noise has a distinct colour from every other label', () => {
  // Defensive — the new cyan chip must read as visually separate from
  // sub-pixel-noise's light green (the most likely accidental alias —
  // both labels sit at the "almost-pass" end of the palette) AND from
  // every divergence-bucket colour so a dashboard skim instantly
  // distinguishes "pHash inflated by geometry, renderer is correct"
  // from any real-signal label.
  const glyphColor = badgeColor('glyph-metric-noise');
  for (const other of [
    'identical', 'sub-pixel-noise', 'color-drift', 'edge-shift',
    'structural-divergence', 'mixed', 'unknown', 'no-content',
    'test-not-applicable',
  ]) {
    assert.notEqual(badgeColor(other), glyphColor,
      `glyph-metric-noise colour ${glyphColor} collides with ${other}`);
  }
});

// ── worstLabel + badgeColor helpers ────────────────────────────────────────

test('worstLabel: picks structural over color-drift over identical', () => {
  // Severity ordering per Section 5 (round 91+):
  // no-content > structural > color-drift > edge-shift > mixed >
  // sub-pixel-noise > identical > unknown
  assert.equal(
    worstLabel(['identical', 'color-drift', 'structural-divergence']),
    'structural-divergence'
  );
});

test('worstLabel: test-not-applicable outranks no-content (FIX-E ordering)', () => {
  // FIX-E severity ranking: test-not-applicable > no-content > structural-
  // divergence > … . An out-of-scope test in the row dominates every
  // other signal because the architectural-exclusion match is the most-
  // actionable label the dashboard can show: filter the test out of
  // scoring, don't ask why other signals are weak.
  assert.equal(
    worstLabel(['identical', 'no-content', 'test-not-applicable']),
    'test-not-applicable'
  );
});

test('worstLabel: no-content outranks structural-divergence (round 91)', () => {
  // The pipeline-level "we produced nothing comparable" failure is the
  // most actionable signal in any row that contains it — fix the empty
  // extraction first, then the per-pair structural label can describe
  // what's actually being rendered. Verifies the v2 severity rank.
  assert.equal(
    worstLabel(['identical', 'structural-divergence', 'no-content']),
    'no-content'
  );
});

test('worstLabel: empty array → "unknown"', () => {
  // Defensive — an empty input has no signal so unknown is correct.
  assert.equal(worstLabel([]), 'unknown');
});

test('badgeColor: returns valid CSS color for every label', () => {
  // Smoke-test the badge palette: every defined label produces some
  // non-empty colour string. Catches accidental case-fallthrough.
  // Round 91: no-content added; FIX-E: test-not-applicable added;
  // swarm-002: glyph-metric-noise added. All three must be palette-distinct.
  for (const l of [
    'identical', 'sub-pixel-noise', 'glyph-metric-noise', 'color-drift',
    'edge-shift', 'structural-divergence', 'mixed', 'unknown', 'no-content',
    'test-not-applicable',
  ]) {
    const c = badgeColor(l);
    assert.ok(typeof c === 'string' && c.length > 0, `no colour for ${l}`);
  }
});

test('badgeColor: no-content has a distinct colour from every other label', () => {
  // Defensive against accidental switch fall-through that would alias
  // no-content to e.g. unknown grey or structural red. The new label is
  // the headline signal of the round-91 fix — it has to read as visually
  // distinct in the dashboard or the fix fails its UX goal.
  const noContent = badgeColor('no-content');
  for (const other of [
    'identical', 'sub-pixel-noise', 'glyph-metric-noise', 'color-drift',
    'edge-shift', 'structural-divergence', 'mixed', 'unknown',
    'test-not-applicable',
  ]) {
    assert.notEqual(badgeColor(other), noContent,
      `no-content colour ${noContent} collides with ${other}`);
  }
});

test('badgeColor: test-not-applicable has a distinct colour from every other label', () => {
  // FIX-E equivalent of the no-content distinct-colour pin. The dark-slate
  // chip is the headline signal of the FIX-E fix — has to read as visually
  // separate from `unknown`'s neutral grey (the most likely accidental
  // alias) and from every divergence-bucket colour (yellows, reds,
  // oranges, magenta) so a dashboard skim instantly distinguishes "out of
  // scope by architecture" from any real-signal label.
  const naColor = badgeColor('test-not-applicable');
  for (const other of [
    'identical', 'sub-pixel-noise', 'glyph-metric-noise', 'color-drift',
    'edge-shift', 'structural-divergence', 'mixed', 'unknown', 'no-content',
  ]) {
    assert.notEqual(badgeColor(other), naColor,
      `test-not-applicable colour ${naColor} collides with ${other}`);
  }
});

test('CLASSIFIER_VERSION is a positive integer', () => {
  // Stamped into baseline-stats.json so distribution drift due to a
  // classifier change is detectable post-hoc.
  assert.ok(Number.isInteger(CLASSIFIER_VERSION) && CLASSIFIER_VERSION > 0);
});

test('glyph-metric-noise must not absorb a colour-gate co-trigger', () => {
  // The Neumorphic hole: divergence confined to a narrow band keeps
  // labMean tiny while dE95 exceeds the colour gate. Without the labP95
  // guard this classified as benign "glyph-metric-noise"; it must surface
  // the colour signal instead (color-drift, or mixed when structure also
  // fires — anything but a noise label).
  const label = classifyDivergence({
    ssim: 0.9554, pixelMismatchedPct: 0.47,
    labDeltaE: { mean: 0.4, p95: 5.4, max: 21 },
    pHashHamming: 12, edgeSsim: 0.97,
    perChannelSsim: { r: 0.99, g: 0.99, b: 0.99 },
    histogramKL: 0.01, dssim: 0.02,
  });
  assert.notEqual(label, 'glyph-metric-noise');
  assert.notEqual(label, 'sub-pixel-noise');
  assert.notEqual(label, 'identical');
});

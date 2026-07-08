// classify-divergence.mjs
//
// COMPARE_METRICS.md Section 4 — divergence classifier.
//
// Pure function `classifyDivergence(metrics) → label`. Takes one
// ImagePairMetrics block (Section 2) and returns exactly one of ten
// labels, evaluated top-to-bottom in a decision tree. First match wins;
// order matters (rationale documented per-rule below).
//
// Why a separate file (Section 8 decision 7):
//   - unit-testable in isolation without booting compare-screenshots.mjs
//   - imported by both compare-screenshots.mjs (per-pair labelling at
//     diff time) and baseline-stats.mjs (post-hoc bulk classification)
//
// Hard rule: thresholds in this file are LITERAL constants, not pulled
// from manifest.thresholds — manifest thresholds are SSIM-only regression
// gates (Phase A), whereas these constants are perceptual-fingerprint
// boundaries used to categorise *what kind* of divergence happened.
// Mixing the two would conflate "did this pair regress?" with "what
// shape of divergence is this pair?".
//
// Every constant cites the spec subsection it comes from so future
// drift between code and spec is auditable.

// Public version stamp — written into baseline-stats.json so a future
// classifier change is detectable in the empirical distribution.
// Bump this when the decision tree or thresholds change.
//
// v1 → v2 (round 91): added `no-content` label as a pre-identical gate to
// fix pilot-001's false positive (TITAN-INVESTIGATOR found that empty-
// extraction pairs against a dark browser-ref scored SSIM 0.91+ purely on
// matching backgrounds, and the existing 7-label tree mislabelled them as
// `structural-divergence` / `mixed` / `sub-pixel-noise` rather than
// surfacing "the capture/extract pipeline produced nothing comparable").
// The new label is gated on the `semanticPresence` metric block produced
// by compare-screenshots-metrics.mjs:computeSemanticPresence — see header
// comment there for the foreground-coverage definition.
//
// v2 → v3 (FIX-E): added `test-not-applicable` label as the highest-severity
// pre-gate. Fires when `metrics.wptNotApplicableTags` is a non-empty array,
// signalling that the auto-bucketer in `testing/titan/wpt-not-applicable.mjs`
// matched this WPT test against one or more architectural-exclusion rules
// (e.g. `requires-inline-FC`, `requires-script-mutation`, `requires-shadow-
// dom`). Without this gate, the ~3,676 W4 tests that the bucketer flagged
// were still being scored as `mixed` / `structural-divergence` / `no-data`
// in the manifest because the tag block in `testing/wpt-buckets.json` was
// never wired into the runtime classifier — defeating the FIX-D projection
// of 1,471 reclassifications. The gate runs FIRST so a `test-not-applicable`
// label dominates every other signal: a test that was never expected to
// render correctly on Compose/SwiftUI shouldn't be scored on perceptual
// metrics at all.
//
// v3 → v4 (swarm-002 / glyph-metric-noise): added `glyph-metric-noise`
// label between `sub-pixel-noise` and `color-drift` in severity. Surfaces
// the swarm-002 false-positive pattern documented in
// `testing/titan/investigations/swarm-002/css-color__color-001.json` and
// `…/css-anchor-position__anchor-center-no-default.json`: small styled-text
// WPT tests where the renderer is provably correct (text renders in the
// right colour at the right position, edgeSsim=1, labMean<1, <3% pixel
// mismatch) but the pHash hamming distance trips the structural-divergence
// gate purely because the harness's 390x~62 fit-content capture is being
// diffed against the browser-ref's 390x600 full-document canvas — two
// visually-similar text strips at different glyph metrics / y-offsets hash
// dramatically apart, inflating pHash hamming into the 20-40 range while
// every other metric agrees the images match. Without this label, ~1,455
// W5 tests sit at `structural-divergence` despite their stated assertion
// (text-is-green / text-is-bold / text-is-aligned) provably passing. The
// new label fires AFTER `sub-pixel-noise` (so genuine perfect matches keep
// the stronger label) and BEFORE the structural / color-drift / edge-shift
// branch, gated on the conjunction of every "rest of the canvas agrees"
// signal so a real structural bug cannot accidentally fall into it.
export const CLASSIFIER_VERSION = 4;

// Coverage-percent threshold under which an image counts as "empty"
// (semantically content-free vs the canonical #1A1A2E capture background).
// Matches `SEMANTIC_PRESENCE_EMPTY_PCT` in compare-screenshots-metrics.mjs;
// duplicated here so the classifier stays import-free of the metric module
// (keeps it a pure function for unit testing without booting sharp etc).
// If you change one, change the other.
const NO_CONTENT_COVERAGE_PCT = 5;

// ── Threshold constants (every value sourced from COMPARE_METRICS.md) ──────

// Section 4 rule 1 — IDENTICAL gate: "all metrics at perfect". Spec calls
// out `ssim=1`, `pHash hamming=0`, `labDeltaE.max=0` as the canonical
// fingerprint. We use ssim≥0.9999 (rounded to 4dp by safeSsim) so a
// last-bit float drift doesn't demote a bitwise-identical pair.
const IDENTICAL_SSIM = 0.9999;
const IDENTICAL_PHASH_HAMMING = 0;
const IDENTICAL_LAB_MAX = 0;
const IDENTICAL_PIXEL_COUNT = 0;

// Section 4 rule 2 — SUB-PIXEL-NOISE: "perceptually identical but a few
// pixels jiggled". Spec values: ssim≥0.95, edgeSsim=1, labDeltaE.p95<2,
// pHash hamming≤2. p95<2 = "barely perceptible" per CIE (Section 1 / B7).
// hamming≤2 leaves room for 1–2 bits of pHash bucket-boundary noise.
const SUBPIXEL_SSIM_MIN = 0.95;
const SUBPIXEL_EDGE_MIN = 1;
const SUBPIXEL_LAB_P95_MAX = 2;
const SUBPIXEL_PHASH_MAX = 2;

// Section 4 rule 3 — COLOR-DRIFT: "labDeltaE.mean>2 OR p95>5, but
// edgeSsim still high (≥0.85)". The edge floor protects against false
// classification of an edge-shift (which always disturbs colour at the
// moved-pixel boundaries) as a colour-drift.
const COLOR_LAB_MEAN_MIN = 2;
const COLOR_LAB_P95_MIN = 5;
const COLOR_EDGE_FLOOR = 0.85;

// Section 4 rule 4 — EDGE-SHIFT: "edgeSsim<0.85 but pHash hamming small
// (≤4)". pHash being small means the global structure / silhouette is
// preserved; only the local edges (1px AA, baseline shifts, etc.) moved.
const EDGE_SSIM_MAX = 0.85;
const EDGE_PHASH_MAX = 4;

// Section 4 rule 5 — STRUCTURAL-DIVERGENCE: "pHash hamming>10 OR
// ssim<0.85". Either fingerprint says "globally different image" — a
// missing element, swapped layout, or wholesale rendering bug.
const STRUCTURAL_PHASH_MIN = 10;
const STRUCTURAL_SSIM_MAX = 0.85;

// Section 4 rule 4.5 — GLYPH-METRIC-NOISE (swarm-002): fires when pHash
// would normally trigger STRUCTURAL but every "rest of the canvas" signal
// agrees the images match. Empirically derived from the swarm-002 RCs:
//   css-color/color-001:                   ssim 0.9745, ph 26, labMean 0.387, edge 1.00, px 0.287
//   css-anchor-position/anchor-center-...: ssim 0.9764, ph 21, labMean 0.485, edge 1.00, px 0.905
// Both render correctly per their stated assertion; both trip STRUCTURAL
// only because the 390x~62 harness fit-content capture is padded against
// the 390x600 browser-ref full-page canvas and the two text strips land
// at different y-bands, hashing apart even though they show identical
// characters in identical colours. The thresholds are deliberately
// stricter than COLOR_EDGE_FLOOR (0.85) and SUBPIXEL_LAB_P95_MAX (2)
// because we're claiming "the renderer is correct" — every guard has to
// hold for the gate to fire, otherwise we'd start absorbing real
// rendering bugs (wrong colour, wrong silhouette, missing glyphs) into
// what's supposed to be a font-metrics-noise bucket.
//
//   edge SSIM ≥ 0.95 — sub-pixel-noise demands edge=1, we relax to 0.95
//     because text-glyph AA between Chromium-raw and Chromium-via-React
//     can shift one row of edge pixels even when the renderer is correct.
//     Below 0.95 means edges actually moved, which would be a real bug.
//   labDeltaE.mean < 1.0 — < 1 ΔE is "imperceptible" per CIE; if the
//     average colour delta is below this, the canvas as a whole is
//     colour-identical. Catastrophic colour bugs always push mean well
//     above 1.
//   pixelMismatchedPct < 3 — caps the gate at "at most 3% of pixels
//     disagree". The swarm-002 RCs both came in well under 1%; the 3%
//     ceiling adds margin for slightly busier text fixtures while still
//     excluding the >10% mismatches that real rendering bugs produce.
const GLYPH_NOISE_SSIM_MIN = 0.95;
const GLYPH_NOISE_LAB_MEAN_MAX = 1.0;
const GLYPH_NOISE_EDGE_MIN = 0.95;
const GLYPH_NOISE_PIXEL_PCT_MAX = 3;

// ── Decision tree ────────────────────────────────────────────────────────

/**
 * Classify a single pair-metric block per Section 4's decision tree.
 *
 * @param {object|null|undefined} metrics — an ImagePairMetrics block
 *   (Section 2). Any field can be null/missing; this function never
 *   throws on shape mismatch — missing fields fall through to 'unknown'.
 * @returns {'test-not-applicable'|'no-content'|'identical'|'sub-pixel-noise'
 *           |'glyph-metric-noise'|'color-drift'|'edge-shift'
 *           |'structural-divergence'|'mixed'|'unknown'}
 */
export function classifyDivergence(metrics) {
  // Defensive null-check: a pair can be `null` in the manifest when one
  // platform failed to decode. Treat as unknown rather than throwing.
  if (!metrics) return 'unknown';

  // ── Rule -1: TEST-NOT-APPLICABLE (auto-bucket pre-gate, FIX-E) ─────────
  // MUST run before every other rule including no-content. Rationale:
  // `testing/titan/wpt-not-applicable.mjs` scans WPT source HTML against
  // ~17 architectural-exclusion rules (requires-inline-FC, requires-script-
  // mutation, requires-shadow-dom, requires-table-layout, …) and emits the
  // matching tag list to `testing/wpt-buckets.json`'s `notApplicable` block.
  // `inject-wpt-block.mjs` propagates that list into the manifest as
  // `metrics.wptNotApplicableTags`. When the array is non-empty, the test
  // was never expected to render correctly on Compose/SwiftUI — scoring it
  // on SSIM / pHash / labDeltaE produces noise that drowns the real signals.
  // The FIX-D projection of 1,471 reclassifications never materialised
  // because this gate was missing; W4's manifest still labelled all 3,676
  // bucketer-flagged tests as `mixed` / `structural-divergence` / `no-data`
  // (counted via inject-wpt-block's bucketsIdx lookup at runtime).
  //
  // The field is OPTIONAL (back-compat): legacy 327-pair manifests have no
  // `wptNotApplicableTags` so this branch is a no-op for them — they
  // continue to be classified by the existing 8-label tree exactly as
  // before. We require an explicit non-empty array (not just "field
  // present") so a defensive empty list doesn't accidentally suppress real
  // signal.
  const tags = metrics.wptNotApplicableTags;
  if (Array.isArray(tags) && tags.length > 0) return 'test-not-applicable';

  // ── Rule 0: NO-CONTENT (semantic-presence pre-gate, round 91) ──────────
  // MUST run before identical and every other rule. Rationale:
  // pilot-001 (TITAN-INVESTIGATOR / css-break/block-end-aligned-abspos-
  // with-overflow) showed that when the IR extracts to empty placeholders,
  // both the capture and the browser-ref are dominated by the canonical
  // dark #1A1A2E background. SSIM scores ~0.91+ on matching backgrounds
  // alone, the existing tree labels the pair as structural-divergence
  // (or mixed / sub-pixel-noise depending on the metrics), and the
  // reviewer can't tell from the label that "both sides are essentially
  // blank — the comparison itself is meaningless."
  //
  // Trigger: EITHER side of the pair has < NO_CONTENT_COVERAGE_PCT (5%)
  // foreground coverage vs CANONICAL_BG. We use OR (not AND) because a
  // single empty side is enough to invalidate the comparison — comparing
  // a real rendered component against a blank capture is meaningless even
  // if the metrics happen to score "structural-divergence" on the diff.
  //
  // The `semanticPresence` field is OPTIONAL (Section 2 backward-compat):
  // older manifests written before round 91 don't have it, and an empty
  // capture buffer can produce `null` from computeSemanticPresence. Both
  // cases fall through to the existing 7-label tree — the gate only fires
  // when we have explicit foreground-coverage signal.
  const sp = metrics.semanticPresence;
  if (sp && typeof sp.aCoveragePct === 'number' && typeof sp.bCoveragePct === 'number') {
    if (sp.aCoveragePct < NO_CONTENT_COVERAGE_PCT ||
        sp.bCoveragePct < NO_CONTENT_COVERAGE_PCT) {
      return 'no-content';
    }
  }

  // Pull the raw values once. Use `??` (not `||`) so a legitimate `0` —
  // e.g. labDeltaE.max === 0 on identical images — doesn't get coerced.
  const ssim = metrics.ssim;
  const pixelCount = metrics.pixelMismatchedCount;
  const pixelPct = metrics.pixelMismatchedPct;
  const edgeSsim = metrics.edgeSsim;
  const pHashHamming = metrics.pHash?.hammingDistance;
  const labMean = metrics.labDeltaE?.mean;
  const labMax = metrics.labDeltaE?.max;
  const labP95 = metrics.labDeltaE?.p95;

  // Section 4 footnote: "unknown — fallback if metrics missing/null".
  // We treat the *core* fingerprints (ssim, edgeSsim, pHash, labDeltaE)
  // as the minimum viable set; if any is missing we cannot confidently
  // pick a label and surface 'unknown' so the report can show "we don't
  // know yet". This is intentional vs. defaulting to a more specific
  // bucket — false classification is worse than 'unknown'.
  const coreMissing =
    ssim == null ||
    edgeSsim == null ||
    pHashHamming == null ||
    labMean == null ||
    labP95 == null ||
    labMax == null ||
    pixelCount == null;
  if (coreMissing) return 'unknown';

  // Track which buckets the pair triggers — used by rule 6 (MIXED) to
  // detect "fits more than one category", per spec: "no clear primary".
  // We compute these as independent predicates rather than reading the
  // chosen label out of the early-return chain so MIXED can fire even
  // when an earlier rule already matched a single category.
  const triggersIdentical =
    ssim >= IDENTICAL_SSIM &&
    pixelCount === IDENTICAL_PIXEL_COUNT &&
    pHashHamming === IDENTICAL_PHASH_HAMMING &&
    labMax === IDENTICAL_LAB_MAX;

  const triggersSubPixel =
    ssim >= SUBPIXEL_SSIM_MIN &&
    edgeSsim >= SUBPIXEL_EDGE_MIN &&
    labP95 < SUBPIXEL_LAB_P95_MAX &&
    pHashHamming <= SUBPIXEL_PHASH_MAX;

  const triggersColorDrift =
    (labMean > COLOR_LAB_MEAN_MIN || labP95 > COLOR_LAB_P95_MIN) &&
    edgeSsim >= COLOR_EDGE_FLOOR;

  const triggersEdgeShift =
    edgeSsim < EDGE_SSIM_MAX &&
    pHashHamming <= EDGE_PHASH_MAX;

  const triggersStructural =
    pHashHamming > STRUCTURAL_PHASH_MIN || ssim < STRUCTURAL_SSIM_MAX;

  // swarm-002 gate (rule 4.5): the only way to reach this branch with a
  // sensible label is when STRUCTURAL would otherwise fire on pHash alone
  // (hamming > 10) AND every other "the canvas matches" guard holds. We
  // intentionally require pHashHamming > STRUCTURAL_PHASH_MIN (not just
  // triggersStructural) so a low-SSIM-but-low-pHash pair routes to the
  // proper structural / edge-shift branch instead — `glyph-metric-noise`
  // is specifically the "pHash exaggerated by glyph-band offset" pattern,
  // not a catch-all rescue for any structural false-positive. `pixelPct`
  // is the only field we read that wasn't in the original "core" set;
  // older manifests without it fail the gate cleanly via the null-check
  // (pixelPct < 3 is false when pixelPct is null) and fall through to the
  // existing tree — fully back-compat with pre-FIX-F manifests.
  const triggersGlyphNoise =
    pHashHamming > STRUCTURAL_PHASH_MIN &&
    ssim >= GLYPH_NOISE_SSIM_MIN &&
    labMean < GLYPH_NOISE_LAB_MEAN_MAX &&
    edgeSsim >= GLYPH_NOISE_EDGE_MIN &&
    pixelPct != null &&
    pixelPct < GLYPH_NOISE_PIXEL_PCT_MAX;

  // ── Rule 1: IDENTICAL ──────────────────────────────────────────────────
  // Cheapest and most-common test (Section 4 rationale paragraph). Has
  // to win first — it logically subsumes sub-pixel-noise.
  if (triggersIdentical) return 'identical';

  // ── Rule 2: SUB-PIXEL-NOISE ────────────────────────────────────────────
  // "Covers ~80% of remaining pairs in current 327-baseline" per spec.
  // Tested before any of the divergence buckets so cross-platform AA
  // doesn't get mislabelled as "color-drift" or "edge-shift".
  if (triggersSubPixel) return 'sub-pixel-noise';

  // ── Rule 2.5: GLYPH-METRIC-NOISE (swarm-002 false-positive rescue) ─────
  // MUST run before the MIXED early-return AND before STRUCTURAL: the gate
  // is specifically the "STRUCTURAL would mislabel this pair purely on
  // pHash inflation from text-band y-offset" pattern (see swarm-002
  // investigations). Position rationale:
  //   - After SUB-PIXEL-NOISE: a pair perfect enough to score sub-pixel
  //     deserves the stronger label; this branch is for "imperfect but
  //     the imperfection is glyph metrics, not the renderer being wrong".
  //   - Before MIXED early-return: a glyph-noise pair that would also
  //     satisfy COLOR-DRIFT (mean drift) or EDGE-SHIFT (edge slide) is
  //     by construction a real divergence — we DON'T want to absorb
  //     genuine signal into this rescue bucket. The conjunctive guards
  //     (labMean < 1, edgeSsim ≥ 0.95) make co-trigger with those two
  //     buckets impossible, so reaching this point with glyph-noise true
  //     means STRUCTURAL is the sole co-trigger and we want to win it.
  //   - Before STRUCTURAL: glyph-noise IS the more-specific diagnosis
  //     for the swarm-002 pattern — surfacing structural-divergence on
  //     these pairs is the bug we're fixing.
  if (triggersGlyphNoise) return 'glyph-metric-noise';

  // ── Rule 6 (early): MIXED before single-bucket divergences ─────────────
  // Spec orders MIXED last in its example, but its definition is "multiple
  // categories triggered, no clear primary". When two or more divergence
  // buckets fire we surface MIXED instead of arbitrarily picking the
  // first one — that gives the reviewer a "needs human" signal rather
  // than a confidently-wrong label.
  const divergenceBucketCount =
    (triggersColorDrift ? 1 : 0) +
    (triggersEdgeShift ? 1 : 0) +
    (triggersStructural ? 1 : 0);
  if (divergenceBucketCount >= 2) return 'mixed';

  // ── Rule 3: COLOR-DRIFT ────────────────────────────────────────────────
  // Note the order vs. the spec's reference snippet: spec puts EDGE-SHIFT
  // before COLOR-DRIFT in code so colour-shift on moved-pixel borders
  // doesn't mask an edge-shift. Our `triggersColorDrift` already gates
  // on `edgeSsim >= COLOR_EDGE_FLOOR (0.85)`, so a true edge-shift
  // (edgeSsim < 0.85) cannot trigger this branch — order is therefore
  // safe and we keep it inline here. Either order produces identical
  // results given mutually-exclusive edgeSsim predicates.
  if (triggersColorDrift) return 'color-drift';

  // ── Rule 4: EDGE-SHIFT ─────────────────────────────────────────────────
  // Borders / shapes moved but global silhouette preserved.
  if (triggersEdgeShift) return 'edge-shift';

  // ── Rule 5: STRUCTURAL-DIVERGENCE ──────────────────────────────────────
  // The most catastrophic real-divergence label — anything that gets here
  // is likely a rendering bug needing a human. Lower priority than
  // EDGE-SHIFT/COLOR-DRIFT only because those are diagnostically more
  // specific when their gates fit; structural is the "we know it's
  // broken but can't be more specific" bucket.
  if (triggersStructural) return 'structural-divergence';

  // ── Rule 6: MIXED (catch-all) ──────────────────────────────────────────
  // Reaches this point when no individual bucket triggered cleanly but
  // the pair is also not identical / sub-pixel-noise. Spec definition
  // matches: "multiple categories triggered weakly" — i.e. partial fits.
  // Rather than declare 'unknown' (which means "we have no signal"),
  // 'mixed' tells the reviewer "we have signal but it's ambiguous".
  return 'mixed';
}

// ── Helpers for the HTML report (Section 5) ────────────────────────────

// Severity ordering used by row-level "headline divergence" badge:
// `test-not-applicable > no-content > structural > color-drift >
//  edge-shift > mixed > glyph-metric-noise > unknown > sub-pixel-noise >
//  identical`. Index = severity rank; higher = worse. Used by
// `worstLabel(labels)` below — picks the most-severe across the 3 pairs
// in a row so the row badge surfaces the worst-case pair.
//
// Why `no-content` is ranked HIGHER than structural-divergence: the
// pipeline-level "we produced nothing comparable" failure dominates any
// per-pair structural divergence. A row where one pair is no-content and
// another is structural-divergence is a row where the no-content pair is
// the more actionable signal — fixing the empty extraction surfaces real
// comparison data the structural label can then describe correctly.
//
// Why `test-not-applicable` is ranked HIGHEST (above no-content, FIX-E):
// a test that was never expected to render correctly on the target
// platform should never be scored as a regression even if the pipeline
// also happens to produce no content for it. The exclusion-rule match
// is the most-actionable signal: filter the test out of the SSIM gate
// entirely, don't ask "why is the comparison empty" or "why is the
// structure different" because the answer is always "this test is out
// of scope for our IR architecture".
// `glyph-metric-noise` ranks BETWEEN `unknown` and `mixed` per spec:
// severity is between sub-pixel-noise (rank 2) and color-drift (rank 7
// after the demotion). We slot it at rank 4 — above `unknown` (so a row
// with a glyph-metric pair correctly headlines glyph-metric rather than
// defaulting to the unknown initialiser in `worstLabel`) and below
// `mixed` (a real multi-bucket pair still wins). This preserves the
// pre-existing "unknown is the worstLabel initial" semantics — any
// label with rank > 3 wins over the default unknown. Slotting at 4
// demotes `mixed`/`edge-shift`/`color-drift`/`structural-divergence`/
// `no-content`/`test-not-applicable` by +1 so the canonical "worse =
// higher rank" ordering is preserved. Severity order is now (rank
// ascending):
//   identical (1) < sub-pixel-noise (2) < unknown (3) <
//   glyph-metric-noise (4) < mixed (5) < edge-shift (6) < color-drift (7) <
//   structural-divergence (8) < no-content (9) < test-not-applicable (10).
const SEVERITY_RANK = {
  identical: 1,
  'sub-pixel-noise': 2,
  unknown: 3,
  'glyph-metric-noise': 4,
  mixed: 5,
  'edge-shift': 6,
  'color-drift': 7,
  'structural-divergence': 8,
  'no-content': 9,
  'test-not-applicable': 10,
};

/**
 * Pick the worst-severity label from a list (per Section 5: "headline
 * divergence" for the row-level h2 badge). Empty / all-undefined input
 * returns 'unknown'.
 */
export function worstLabel(labels) {
  let worst = 'unknown';
  let worstRank = SEVERITY_RANK.unknown;
  for (const l of labels) {
    if (!l) continue;
    const r = SEVERITY_RANK[l] ?? SEVERITY_RANK.unknown;
    if (r > worstRank) {
      worst = l;
      worstRank = r;
    }
  }
  return worst;
}

// Badge colours per Section 5 + spec: green=identical/sub-pixel-noise,
// cyan=glyph-metric-noise (swarm-002), yellow=color-drift/edge-shift,
// red=structural-divergence/mixed, gray=unknown, magenta=no-content,
// dark-slate=test-not-applicable. Returned as a plain CSS background-
// color so the HTML can inline the style without a class lookup (per
// upfront decision).
export function badgeColor(label) {
  switch (label) {
    case 'identical':         return '#2d7a3a'; // dark green — strongest pass signal
    case 'sub-pixel-noise':   return '#4a9b5e'; // light green — perceptually identical
    // Light cyan (swarm-002) — sits between the sub-pixel-noise green and
    // the color-drift yellow in both severity rank and palette hue, so a
    // dashboard skim reads "almost-pass, capture-geometry artefact"
    // rather than "real divergence". Picked from a safe-for-colour-
    // blindness range (cyan vs the surrounding green/yellow stays
    // distinguishable under deuteranopia + protanopia simulation) and
    // deliberately distinct from no-content's magenta and test-not-
    // applicable's dark-slate so the new label gets its own chip identity.
    case 'glyph-metric-noise': return '#3a9b9b'; // light cyan — pHash inflated by glyph y-offset
    case 'color-drift':       return '#a8830a'; // yellow — perceptual, not structural
    case 'edge-shift':        return '#a8830a'; // yellow — same severity as color-drift
    case 'structural-divergence': return '#a33'; // red — likely real bug
    case 'mixed':             return '#c46a1f'; // orange/red — needs human review
    case 'unknown':           return '#666';    // grey — metric data missing
    // Magenta — distinct from every other palette entry so an audit-skim
    // of the report instantly separates "pipeline produced nothing" from
    // both pass-bucket greens and divergence-bucket yellows/reds. Picked
    // from a safe-for-colour-blindness range (deuteranopia + protanopia
    // both perceive magenta vs the existing palette).
    case 'no-content':        return '#9b3a8e'; // magenta — capture/extract pipeline empty
    // Dark slate-gray (FIX-E) — visually distinct from every other entry:
    // the pass-bucket greens, the divergence-bucket yellows/reds/oranges,
    // the unknown medium-grey, AND the no-content magenta. Reads as
    // "deliberately set aside" rather than "broken" — appropriate for a
    // test the auto-bucketer marked as out-of-scope-by-architecture.
    // Picked at #2c3340 (very dark blue-grey) so it stays legible against
    // the dashboard's light background and reads as a clearly-different
    // hue family from `unknown`'s neutral #666.
    case 'test-not-applicable': return '#2c3340'; // dark slate — out-of-scope for SSIM gate
    default:                  return '#666';    // defensive: unknown future label → grey
  }
}

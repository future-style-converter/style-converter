// classify-text-probe.mjs
//
// COMPARE_METRICS_B8-B10.md Section 5 — text-probe classifier.
//
// Pure function `classifyTextProbe(b8, b9, b10) → label`. Mirrors
// `classifyDivergence` in classify-divergence.mjs structure (separate
// file, separate version stamp, named threshold constants) so the test
// suite + future tuners can introspect it the same way.
//
// CRITICAL: this is a *separate* classifier from the 7-label
// classify-divergence.mjs. Probe metrics measure a different population
// (the ~20-component probe set, not the 327-pair regression baseline),
// so they get their own labels and the existing CLASSIFIER_VERSION = 1
// stays untouched. See B-EXT spec Section 5 option (a) for the rationale.
//
// Inputs:
//   b8  — { maxBaselineDeltaPx, divergentFixtureCount } | null
//   b9  — { agreement: 'agree'|'partial'|'differ' } | null
//   b10 — { maxMeanDeltaPx, maxStddevDeltaPx } | null
//
// Output: one of 'agree' | 'sub-px-drift' | 'kerning-drift' | 'broken'.
//
// Decision-tree thresholds reference the spec's Section 4 tables.

// Public version stamp — written into testing/text-metrics.json so a
// future classifier change is detectable. Bump when the tree changes.
export const TEXT_PROBE_CLASSIFIER_VERSION = 1;

// ── Threshold constants (every value sourced from spec Section 4) ──────────

// Section 4 / B8 table: 0.25 px = "agree"; 0.5 px = "sub-px drift";
// 1.0 px = "drift" (orange); >1.0 = "broken" (red). The classifier
// compresses these into agree / sub-px-drift / broken at the headline
// label level.
const B8_AGREE_MAX_PX = 0.5;       // ≤0.5 px = sub-px or better
const B8_DRIFT_MAX_PX = 1.0;       // 0.5–1.0 px = drift; >1.0 = broken

// Section 4 / B10 table: mean<0.5 + stddev<0.5 = agree; mean 0.5–1.5 =
// kerning drift; stddev>1.5 = hinting drift; both>1.5 = broken.
const B10_AGREE_MEAN_MAX = 0.5;
const B10_AGREE_STDDEV_MAX = 0.5;
const B10_DRIFT_MEAN_MAX = 1.5;
const B10_DRIFT_STDDEV_MAX = 1.5;

// ── Decision tree ──────────────────────────────────────────────────────────

/**
 * Classify a triplet of B-EXT probe results into a single headline label.
 *
 * @param {object|null} b8  — result of `compute*` for B8
 * @param {object|null} b9  — result of `compute*` for B9
 * @param {object|null} b10 — result of `compute*` for B10
 * @returns {'agree'|'sub-px-drift'|'kerning-drift'|'broken'}
 */
export function classifyTextProbe(b8, b9, b10) {
  // Defensive null-check: if every probe failed, surface 'broken' rather
  // than a misleading 'agree'. We'd rather flag missing data than hide
  // it under an "everything's fine" verdict.
  if (b8 == null && b9 == null && b10 == null) return 'broken';

  // Pull the headline numbers, defaulting to 0 when absent (the absent
  // probe contributes nothing to the worst-case calculation). null b9 is
  // treated as 'agree' (no signal = no failure to report on this axis).
  const baselineDelta = b8?.maxBaselineDeltaPx ?? 0;
  const aaAgreement = b9?.agreement ?? 'agree';
  const meanDelta = b10?.maxMeanDeltaPx ?? 0;
  const stddevDelta = b10?.maxStddevDeltaPx ?? 0;

  // ── BROKEN: any axis crossed its red boundary ────────────────────────
  // Single broken axis is enough — we don't average across axes because
  // a single "broken" signal usually means a real rendering bug worth
  // human review (per spec Section 4 — "broken" = "visible non-side-by-side").
  if (baselineDelta > B8_DRIFT_MAX_PX) return 'broken';
  if (meanDelta > B10_DRIFT_MEAN_MAX && stddevDelta > B10_DRIFT_STDDEV_MAX) return 'broken';

  // ── KERNING-DRIFT: B10 mean drifted but stddev still bounded ─────────
  // Spec Section 4 / B10 row 2: mean 0.5–1.5 = kerning drift. Surfaced
  // before sub-px-drift because a kerning shift is a more specific finding
  // than "general sub-pixel jitter" — easier to act on.
  if (meanDelta > B10_AGREE_MEAN_MAX) return 'kerning-drift';

  // ── KERNING-DRIFT: B10 stddev jumped (hinting variance) ──────────────
  // Same label as mean-shift because the spec collapses both into
  // "kerning-drift" at the headline level (the hinting/kerning split is
  // visible inside b10's own meanDelta vs stddevDelta breakdown — the
  // headline isn't required to call out which sub-mode).
  if (stddevDelta > B10_AGREE_STDDEV_MAX) return 'kerning-drift';

  // ── SUB-PX-DRIFT: B8 baseline detected drift, but below 1-px JND ─────
  // 0.25 px = noise floor (B8_AGREE_MAX_PX is 0.5 = upper bound of
  // sub-pixel band). B9 'differ' (different AA strategies) also lands
  // here because that's a perceptual not structural issue.
  if (baselineDelta > 0.25 || aaAgreement === 'differ') return 'sub-px-drift';

  // ── AGREE: every axis below its sub-pixel boundary ───────────────────
  return 'agree';
}

// ── Helpers for the HTML report ────────────────────────────────────────────

// Severity ordering used by the report's headline badge. Higher = worse.
// Mirrors the SEVERITY_RANK shape in classify-divergence.mjs so the same
// tooling can sort across both classifiers.
const TEXT_PROBE_SEVERITY = {
  agree: 1,
  'sub-px-drift': 2,
  'kerning-drift': 3,
  broken: 4,
};

/**
 * Pick the worst-severity text-probe label across a list. Empty / all-null
 * input returns 'agree' (no probe ran → no failure to report). Mirrors
 * the worstLabel helper in classify-divergence.mjs.
 */
export function worstTextProbeLabel(labels) {
  let worst = 'agree';
  let worstRank = TEXT_PROBE_SEVERITY.agree;
  for (const l of labels) {
    if (!l) continue;
    const r = TEXT_PROBE_SEVERITY[l] ?? TEXT_PROBE_SEVERITY.agree;
    if (r > worstRank) { worst = l; worstRank = r; }
  }
  return worst;
}

/**
 * Badge colour matching the existing report convention (per
 * classify-divergence.badgeColor). Agree = green, sub-px-drift = light
 * green, kerning-drift = yellow, broken = red.
 */
export function textProbeBadgeColor(label) {
  switch (label) {
    case 'agree':         return '#2d7a3a'; // dark green — strongest pass
    case 'sub-px-drift':  return '#4a9b5e'; // light green — perceptually identical
    case 'kerning-drift': return '#a8830a'; // yellow — needs investigation
    case 'broken':        return '#a33';    // red — visible regression
    default:              return '#666';    // grey — unknown future label
  }
}

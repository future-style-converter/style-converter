#!/usr/bin/env node
//
// compare-screenshots.mjs
//
// Cross-platform component screenshot comparison.
//
// Reads per-component PNGs from:
//   apps/ios-harness/screenshots/
//   apps/android-harness/screenshots/
//   apps/web-harness/screenshots/
//
// For each component (matched by filename like `012_Color_HSL.png`) it:
//
//   1. Normalizes all three images to the same dimensions (padding rather
//      than stretching, so borders / shadows / layouts stay aligned).
//   2. Computes a pairwise **pixelmatch** diff — percentage of pixels
//      whose RGB differs by more than an anti-aliasing-aware threshold.
//   3. Computes a pairwise **SSIM** (structural similarity) score — a
//      perceptual metric that tolerates font rendering / subpixel AA but
//      still catches "the shadow is missing" / "the shape is wrong".
//   4. Optionally diffs against a committed baseline in tools/visual/baseline/
//      and fails hard if any component regresses beyond the threshold.
//
// Outputs:
//   - tools/visual/report/                  (HTML report + diff PNGs + manifest.json)
//   - tools/visual/report/index.html        (open in any browser)
//   - tools/visual/report/manifest.json     (structured data for CI / tooling)
//
// Exit codes:
//   0 — success or no baseline
//   1 — at least one component regressed beyond thresholds
//   2 — script / IO error (including "baseline mode requested but 0 comparisons ran")
//   3 — a capture is not untagged-sRGB. Distinct from 1 because it means the
//       NUMBERS are untrustworthy, not that the render changed: pngjs
//       discards colour profiles without applying them, so a tagged capture
//       is compared as if it were sRGB. See png-color-space.mjs.
//   4 — unexpected cross-platform divergence (see cross-platform-gate.mjs).
//   5 — stale cross-platform expectation (see cross-platform-gate.mjs).
//   6 — spec-oracle violation: a platform's render disagrees with a
//       spec-derived `_expect` declared in the input fixture. Distinct from
//       4 because no cross-platform comparison is involved — each platform
//       is judged ALONE, so all three agreeing on the wrong value still
//       fails. See spec-oracle.mjs.
//
// ⚠ SSIM caveat — `ssim.js` runs with `downsample: 'original'`, which
// box-filters and decimates by `f = round(min(W, H) / 256)` whenever f > 1.
// So in principle SSIM is not comparable across component heights, and the
// single 0.95 gate would be a different sensitivity per row.
//
// MEASURED 2026-08-28, and the scope is much narrower than that reads.
// Captures are 390 wide, so min(W,H) is the HEIGHT and f > 1 needs a
// component ≥ ~384px tall. Across all 399 committed baselines exactly
// THREE are downsampled — the three platforms of one component,
// `003_AR_Half` at 390×432. Every other capture (heights 32–132 on
// visual-test) is already scored at 1×.
//
// And on that one component it changes nothing: SSIM moves 0.9978 → 0.9969
// (iOS-Android) when scored at 1×, with ZERO verdict flips on any of its
// three pairs. Control: two non-downsampled rows score identically both
// ways (0.9406 → 0.9406), confirming the flag does what it claims.
//
// Do not "fix" this by flipping `downsample` — it would move a committed
// figure for no verdict change.
//
// The old text ended "the fix is to stop gating on SSIM." That advice is
// CONTRADICTED by measuring what each metric actually contributes. Over
// the 327 visual-test pairs, 26 fail at least one threshold, and the
// UNIQUE catches — failures no other metric sees — are:
//
//     SSIM alone : 14      (border radii, large radii, multi-transform,
//                           inset round shadow — antialiased CURVE
//                           divergence, where ΔE95 reads 0.00 and Δpx
//                           reads under 1.4%)
//     ΔE alone   :  4      (the iOS sepia bug; Neumorphic shadow)
//     Δpx alone  :  0
//
// SSIM is the LARGEST unique contributor and the only metric that sees
// antialiased-curve structure. `pixelmatch` is the one contributing no
// unique signal here — and it is separately blind to any uniform lightness
// shift below 66/255 (see the threshold note at its call site). Removing
// SSIM on the strength of a caveat that fires on one component and flips
// no verdict would blind the gate to its largest catch class.
//
// Re-derive with: score both corpora, then per pair compare
// (ssim < 0.95), (pixelPct > 2), (ΔE95 > 5) and count the singletons.
//
// Usage:
//     node compare-screenshots.mjs [options]
//
// Options:
//     --baseline                Compare against tools/visual/baseline/ and fail
//                               on regressions. Without this flag only the
//                               cross-platform report is generated.
//     --update-baseline         Copy the current captures into tools/visual/baseline/.
//                               Use after intentional visual changes.
//     --ssim-threshold <n>      Minimum SSIM for a component to pass against
//                               the baseline (default 0.95).
//     --pixel-threshold <n>     Max % of pixels allowed to differ (default 2).
//     --full-lab                Disable LAB ΔE stride sampling. Default
//                               samples 1-in-4 pixels (~30 ms/pair). Full
//                               sampling adds ~90 ms/pair (~40s on baseline).
//     --no-spec-oracle          Skip the spec oracle even when the input
//                               fixture declares `_expect` blocks. Mirrors
//                               --no-cross-platform-gate: a deliberate
//                               report-only run, visible in the manifest.
//

import { readdirSync, existsSync, mkdirSync, rmSync, copyFileSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname, basename, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
// (sharp is no longer imported here — the only use was canvas padding,
// which moved to pad-canvas.mjs so its invariants can be unit-tested.)
import pixelmatchDefault from 'pixelmatch';
import { ssim as computeSsim } from 'ssim.js';
// COMPARE_METRICS Section 7 items 1–6 — new per-pair metrics, extracted
// into a sibling module so they can be unit-tested in isolation.
import {
  computeDssim,
  computeHistogramKL,
  computePerChannelSsim,
  computePHash,
  computeEdgeSsim,
  computeLabDeltaE,
  computeSemanticPresence,
} from './compare-screenshots-metrics.mjs';
// COMPARE_METRICS Section 7 item 7 — divergence classifier (B-3). Pure
// function over a metric block; lives in a separate module so it can be
// unit-tested without booting the whole comparison pipeline.
import { classifyDivergence, SEVERITY_RANK } from './classify-divergence.mjs';
// Colour-space tripwire. Nothing in this pipeline is colour-managed and
// pngjs discards profile chunks without applying them, so a non-sRGB
// capture would be compared as if it were sRGB — wrong numbers, no error.
// See png-color-space.mjs for the full failure analysis.
import { assertSrgbOrUntagged } from './png-color-space.mjs';
import { padToCanvas } from './pad-canvas.mjs';
// Wave 1 — the cross-platform pairs finally gate something. Evaluation logic
// lives in a sibling module so the ledger semantics (expected / unexpected /
// stale / orphaned) are unit-testable without booting the pipeline.
import {
  evaluateCrossPlatformGate,
  pairRegressed,
  formatRecord,
  EXIT_UNEXPECTED_DIVERGENCE,
  EXIT_STALE_EXPECTATION,
  DEFAULT_DELTA_E_THRESHOLD,
} from './cross-platform-gate.mjs';
// COMPARE_METRICS Section 5 / item 9 — HTML report extracted into a
// sibling module to keep this file under the per-file size budget.
import { renderHTML } from './compare-screenshots-html.mjs';
// Lane A — the spec oracle. The cross-platform gate above can only catch the
// runtimes DISAGREEING; 2026-08-28 proved they can all be wrong together
// (filter blur/invert wrong on both natives while every pairwise gate
// passed). A fixture component may carry `_expect` with SPEC-DERIVED values,
// and each platform is judged ALONE against them — see spec-oracle.mjs.
import {
  parseExpectations,
  evaluateOracle,
  formatViolation,
  formatMissing,
  EXIT_SPEC_ORACLE_VIOLATION,
} from './spec-oracle.mjs';

const pixelmatch = pixelmatchDefault.default ?? pixelmatchDefault;

const __dirname = dirname(fileURLToPath(import.meta.url));

// ── Paths ────────────────────────────────────────────────────────────────────
// Each path can be overridden via env. Defaults preserve the legacy 327-pair
// pipeline contract (test-all.sh + the visual-test fixture). The TITAN
// section-runner (tools/titan/section-runner.sh) sets the per-platform
// screenshot dirs + REPORT_DIR + MANIFEST_OUT to per-section paths so 30+
// agents can run compare in parallel without racing on tools/visual/report/ or
// the screenshot trees.
const PLATFORMS = ['iOS', 'Android', 'web'];

const paths = {
  iOS:     process.env.IOS_SCREENSHOTS_DIR     || resolve(__dirname, '../../apps/ios-harness/screenshots'),
  Android: process.env.ANDROID_SCREENSHOTS_DIR || resolve(__dirname, '../../apps/android-harness/screenshots'),
  web:     process.env.WEB_SCREENSHOTS_DIR     || resolve(__dirname, '../../apps/web-harness/screenshots'),
  baseline: resolve(__dirname, 'baseline'),
  // REPORT_DIR controls where index.html + diffs/ + images/ are written.
  // MANIFEST_OUT (read at write time below) overrides only the manifest.json
  // path — useful when several runners share a report dir but each needs
  // its own machine-readable result file.
  report:   process.env.REPORT_DIR             || resolve(__dirname, 'report'),
};

// ── Args ─────────────────────────────────────────────────────────────────────
const args = process.argv.slice(2);
const useBaseline    = args.includes('--baseline');
const updateBaseline = args.includes('--update-baseline');
const ssimThreshold  = Number(getArg('--ssim-threshold') ?? 0.95);
const pixelThreshold = Number(getArg('--pixel-threshold') ?? 2);
// ΔE95 ceiling. Defaults to the classifier's own "clearly different"
// boundary rather than a fresh guess — see DEFAULT_DELTA_E_THRESHOLD.
const deltaEThreshold = Number(getArg('--delta-e-threshold') ?? DEFAULT_DELTA_E_THRESHOLD);
// Optional label (e.g. the input IR filename) so the report headline says
// which test case it was generated from. Populated by test-all.sh.
const inputLabel     = getArg('--input') ?? process.env.TEST_INPUT ?? '';
// COMPARE_METRICS Section 8 (decision 2) / B7 — stride-sample LAB ΔE 1-in-4
// by default to keep cost ~30ms/pair instead of ~120ms. `--full-lab` opts
// into pixel-accurate scoring for one-off accuracy runs (~40s extra on the
// 327-pair baseline).
const fullLab        = args.includes('--full-lab');
// Cross-platform gate (Wave 1). ON by default — the three-way comparison is
// the product thesis, and leaving it opt-in is how it never gets turned on.
// It self-skips when fewer than two platforms were captured, which is what
// keeps CI's one-platform-per-job visual workflow untouched without anyone
// having to remember a flag. `--no-cross-platform-gate` is the escape hatch
// for a deliberate report-only run.
const crossPlatformGate = !args.includes('--no-cross-platform-gate');
// Spec oracle (Lane A). ON by default for the same reason as the gate above:
// leaving a correctness check opt-in is how it never gets turned on. It
// self-skips when the input fixture declares no `_expect` (which is every
// pre-existing fixture, so the 327-pair corpus is untouched by construction).
// `--no-spec-oracle` is the escape hatch, mirroring --no-cross-platform-gate.
const specOracle = !args.includes('--no-spec-oracle');

function getArg(name) {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
}

// ── Baseline update shortcut ─────────────────────────────────────────────────
if (updateBaseline) {
  await syncBaseline();
  process.exit(0);
}

// ── Main ─────────────────────────────────────────────────────────────────────
// `await main()` is placed at the bottom of the file so all module-level
// `const` declarations have been initialized before the async work starts
// reaching into them. ESM hoists bindings but not initializers; accessing
// a const before its declaration throws "Cannot access X before
// initialization" (TDZ).

async function main() {
  console.log('→ collecting captures…');
  const captures = collectCaptures();
  const names = sortedUnion(captures);
  console.log(`  ${names.length} unique component(s) across platforms`);

  rmSync(paths.report, { recursive: true, force: true });
  mkdirSync(paths.report, { recursive: true });
  mkdirSync(join(paths.report, 'diffs'), { recursive: true });
  mkdirSync(join(paths.report, 'images'), { recursive: true });

  // Copy originals into report/images/ so the HTML can reference them without
  // relying on relative paths that break when the report is moved.
  for (const p of PLATFORMS) {
    const dst = join(paths.report, 'images', p);
    mkdirSync(dst, { recursive: true });
    for (const name of Object.keys(captures[p])) {
      copyFileSync(captures[p][name], join(dst, name));
    }
  }
  if (useBaseline && existsSync(paths.baseline)) {
    const dst = join(paths.report, 'images', 'baseline');
    mkdirSync(dst, { recursive: true });
    for (const f of readdirSync(paths.baseline)) {
      if (f.endsWith('.png')) copyFileSync(join(paths.baseline, f), join(dst, f));
    }
  }

  // Per-component analysis.
  const rows = [];
  let regressionCount = 0;

  for (const name of names) {
    process.stdout.write(`  ${name}… `);
    const row = await analyzeComponent(name, captures);
    rows.push(row);

    if (useBaseline && row.baseline && row.baseline.regressed) {
      regressionCount += 1;
      process.stdout.write('REGRESSION\n');
    } else {
      process.stdout.write(summarize(row) + '\n');
    }
  }

  // Write HTML + manifest.
  // COMPARE_METRICS Section 6 — bump manifestVersion to 2 to signal that
  // the per-pair blocks carry the B2/B3/B4/B5/B6/B7 metrics. External
  // tools can detect "this report has the new metrics" with one field
  // check rather than probing each row.
  //
  // COMPARE_METRICS_B8-B10.md Section 6 — bumped again 2 → 3 for the
  // top-level `perPlatformProbes` field. Graceful rollout (Section 8 q8):
  // the writer always emits v3, but `perPlatformProbes` is null until
  // tools/visual/compute-text-metrics.mjs inlines actual probe data. v2
  // consumers (smoke.sh exit-code check, baseline-stats walking pairs.*)
  // ignore unknown top-level keys, so the bump is informational.
  //
  // TITAN_ARCHITECTURE.md §7.1 — bumped again 3 → 4 for the top-level
  // `wpt` block. Same graceful-rollout pattern as perPlatformProbes:
  // we always emit v4, but `wpt` is null on non-WPT runs (the only writer
  // that fills it is tools/titan/inject-wpt-block.mjs, which runs as a
  // post-processor in run-titan.sh). v3-aware consumers (smoke.sh exit
  // check, baseline-stats walking rows[].pairs.*) treat unknown top-level
  // keys as ignorable; the bump is informational. Tests that probe for
  // `manifest.wpt !== undefined` need the explicit-null sentinel here so
  // they can branch on "field absent" vs "field present but no data" cleanly.
  // ── Cross-platform gate (Wave 1) ─────────────────────────────────────────
  // Evaluated HERE, before the report is written, so the result can be
  // rendered into the headline and the manifest. The console output and the
  // non-zero exit happen after the report is on disk — a gate that fails
  // without leaving you the artifact to diagnose it is a worse gate.
  const xGate = crossPlatformGate
    ? evaluateCrossPlatformGate(rows, loadCrossPlatformLedger(), {
        ssimThreshold, pixelThreshold, deltaEThreshold, inputLabel,
      })
    : { skipped: true, reason: 'disabled via --no-cross-platform-gate', checked: 0,
        unexpected: [], expected: [], stale: [], expired: [] };

  // ── Spec oracle (Lane A) ─────────────────────────────────────────────────
  // Evaluated here (like the gate above: before the report/manifest are
  // written, enforced after they are on disk). `null` when the fixture
  // declares no `_expect` — the common case, and the inert one.
  const oracleExpectations = loadSpecOracleExpectations();
  const xOracle = oracleExpectations === null
    ? null
    : !specOracle
      // The escape hatch still records that it was used: a manifest that
      // said nothing would make a deliberately-skipped oracle look like a
      // fixture with no expectations at all.
      ? { skipped: true, reason: 'disabled via --no-spec-oracle', checked: 0, violations: [], missing: [] }
      : evaluateOracle(rows, oracleExpectations, {
          // Re-read the raw (unpadded) capture from disk: the oracle judges
          // what the platform actually wrote, not the padded comparison
          // canvas (whose PAD_SENTINEL magenta would enter the histogram).
          // Plain PNG.sync.read, NOT loadPng: the colour-space tripwire
          // already ran over every capture in analyzeComponent, and a
          // second assert here would push duplicate violations into the
          // exit-3 listing. A capture that failed that assert has
          // present:false and is reported as missing, never re-read.
          getPng: (platform, name) => {
            const path = captures[platform]?.[name];
            return path ? PNG.sync.read(readFileSync(path)) : null;
          },
        });

  const manifest = {
    // NOT bumped to 5 for the `crossPlatformGate` key below, deliberately.
    // The three previous bumps assumed this writer is the last word on the
    // version, but two post-processors already overwrite it —
    // compute-text-metrics.mjs sets 3 and inject-wpt-block.mjs sets 4 — so a
    // 5 emitted here would be clobbered by either and would signal nothing.
    // The graceful-rollout contract documented above (unknown top-level keys
    // are ignorable, the field is explicitly null when absent) is what
    // actually carries the compatibility guarantee, and it holds unchanged.
    manifestVersion: 4,
    generatedAt: new Date().toISOString(),
    inputLabel,
    thresholds: {
      ssim: ssimThreshold,
      pixel: pixelThreshold,
      edgeSsim: 0.80,        // Section 3 placeholder (data-derived later)
      labDeltaEP95: 5.0,     // Section 3 — "clearly different" boundary
      pHashHamming: 10,      // Section 3 — Wang et al. 2008 standard
      // B-EXT spec Section 6 — info-only, no CI gating. compute-text-metrics
      // overwrites these with the same values when it runs (it doesn't
      // know we already wrote them, but the values are stable).
      b8BaselineDeltaPx: 1.0,
      b10MeanDeltaPx: 1.5,
      b10StddevDeltaPx: 1.5,
    },
    // Null until compute-text-metrics.mjs inlines real probe data. Kept
    // explicit (vs absent) so v3 readers can branch on `=== null` instead
    // of `'perPlatformProbes' in manifest` for the "no data yet" state.
    perPlatformProbes: null,
    // Null on non-WPT runs; tools/titan/inject-wpt-block.mjs replaces
    // this with the §7.1 `{ ref, runId, totalTests, buckets, results,
    // skipped, ... }` block when run-titan.sh post-processes the manifest.
    // Kept explicit so v4 readers can detect "WPT pipeline didn't run"
    // via `manifest.wpt === null` rather than `'wpt' in manifest`.
    wpt: null,
    // Wave 1 — cross-platform gate outcome. Always present (never null):
    // when the gate is skipped the object says so and why, which is more
    // useful to a reader than an absent key that could mean either "old
    // manifest" or "single-platform run".
    crossPlatformGate: xGate,
    // Lane A — spec-oracle outcome. Present ONLY when the input fixture
    // declares `_expect` (evaluated or deliberately disabled). This is the
    // opposite of crossPlatformGate's always-present rule, on purpose:
    // inertness for the no-expectation corpus is non-negotiable (the
    // 327-pair manifest must stay byte-identical), and an absent key
    // already has an unambiguous meaning here — "this fixture declares no
    // spec expectations" — unlike the gate, where absence could mean
    // "single-platform run". Unknown-top-level-key tolerance is the same
    // documented contract the previous manifest additions rode.
    ...(xOracle !== null ? { specOracle: xOracle } : {}),
    rows,
  };
  // MANIFEST_OUT lets the TITAN section-runner write the per-section manifest
  // to a stable per-section path while still letting `report/` host the HTML
  // for visual debugging. Defaults to the legacy `<report>/manifest.json` so
  // the 327-pair pipeline + run-titan.sh Phase 1 flow are unchanged.
  const manifestOut = process.env.MANIFEST_OUT || join(paths.report, 'manifest.json');
  writeFileSync(manifestOut, JSON.stringify(manifest, null, 2));
  writeFileSync(
    join(paths.report, 'index.html'),
    renderHTML(rows, {
      useBaseline,
      ssimThreshold,
      pixelThreshold,
      regressionCount,
      inputLabel,
      // B-EXT spec Section 7 step 12 — pass through the (possibly null)
      // perPlatformProbes block so the HTML renderer can append the
      // typography section. Null here means "compute-text-metrics hasn't
      // run yet"; the renderer returns an empty string in that case so
      // the existing report layout is unaffected.
      perPlatformProbes: manifest.perPlatformProbes,
      // Wave 1 — so the open-expectation count lands in the headline. An
      // expectation ledger only stays honest while its size is visible.
      crossPlatformGate: xGate,
    })
  );

  console.log(`\n✓ report: ${join(paths.report, 'index.html')}`);

  // Phase B (LOG-ONLY) — cross-platform divergence-drift check against
  // tools/visual/baseline-stats.json. Spec Section 6: "divergence label
  // downgraded between baseline and current". Compares the recorded
  // classifier label per cross-platform pair against this run's label
  // and warns when a row's label severity increased. Doesn't gate CI.
  // Only runs if baseline-stats.json exists; otherwise silently skips
  // (first run has no recorded baseline yet — generate one with
  // `node tools/visual/baseline-stats.mjs`).
  await runPhaseBDriftCheck(rows);

  // Colour-space tripwire — checked BEFORE the baseline gate so a capture in
  // the wrong colour space fails on its own terms instead of surfacing as a
  // mysterious similarity regression. Exit 3 is distinct from 1 (regression)
  // and 2 (empty run) so CI can tell the three apart at a glance.
  if (colorSpaceViolations.length > 0) {
    console.error(`✗ ${colorSpaceViolations.length} capture(s) are not untagged-sRGB:`);
    for (const m of colorSpaceViolations) console.error(`  · ${m}`);
    process.exit(3);
  }

  // ── Cross-platform gate verdict ──────────────────────────────────────────
  // Evaluated above (before the report was written); reported and enforced
  // here, BEFORE the baseline gate, so "the three runtimes disagree" is
  // surfaced on its own terms rather than being masked by, or confused with,
  // "this platform changed since its last capture".
  if (xGate.skipped) {
    console.log(`· cross-platform gate skipped — ${xGate.reason}`);
  } else {
    console.log(
      `· cross-platform gate: ${xGate.checked} pair(s) · ` +
      `${xGate.expected.length} known divergence(s) · ${xGate.unexpected.length} unexpected`,
    );
    // Expiry stays a WARNING: it fires on a calendar rollover with no code
    // change, so failing on it would redden a build nobody touched.
    for (const r of xGate.expired) {
      console.warn(`  ⚠ expectation past its expiry (${r.entry.expires}) — re-review: ${formatRecord(r)}`);
    }
    // A ledger entry whose component rendered but whose PAIR did not form
    // this run (one platform's capture of that component failed): warned,
    // never fatal — deleting the line on that evidence would un-excuse a
    // real divergence the next healthy run.
    for (const u of xGate.unexercised ?? []) {
      console.warn(`  ⚠ ledger pair not exercised this run (capture missing on one side): ${u.component} · ${u.pair}`);
    }

    // Unexpected divergence is checked FIRST. When both conditions are
    // present, "the runtimes disagree" is the one worth surfacing — a stale
    // line is bookkeeping, a new divergence is a product regression.
    if (xGate.unexpected.length > 0) {
      console.error(`✗ ${xGate.unexpected.length} unexpected cross-platform divergence(s):`);
      for (const r of xGate.unexpected) console.error(`  · ${formatRecord(r)}`);
      console.error('  Either fix the divergence, or add it to');
      console.error('  tools/visual/cross-platform-expectations.json with a reason and an owner.');
      process.exit(EXIT_UNEXPECTED_DIVERGENCE);
    }
  }

  // ── Spec-oracle verdict (Lane A) ─────────────────────────────────────────
  // ORDERING, decided rather than accidental: exit 4 > exit 6 > exit 5.
  //   · Unexpected divergence (4) stays first — when the runtimes disagree
  //     AND one is spec-wrong, the disagreement is the richer signal (it
  //     names which platform diverged from the others) and the established
  //     one; the oracle violation will still be there on the next run.
  //   · The oracle (6) outranks stale expectations (5) — a platform
  //     rendering the wrong colour is a product defect; a ledger line that
  //     now passes is bookkeeping. Spec wrongness must not queue behind a
  //     tidy-up chore, or the tidy-up commit "fixes" the build while the
  //     render is still wrong.
  // The oracle runs even when the gate self-skipped (single-platform run):
  // each platform is judged ALONE, so one platform is exactly enough.
  if (xOracle !== null) {
    if (xOracle.skipped) {
      console.log(`· spec oracle skipped — ${xOracle.reason}`);
    } else {
      console.log(
        `· spec oracle: ${xOracle.checked} platform-component check(s) · ` +
        `${xOracle.violations.length} violation(s)` +
        ((xOracle.waived?.length ?? 0) > 0 ? ` · ${xOracle.waived.length} waived` : ''),
      );
      // Waived violations: excused per-platform divergences (_expect.waive),
      // the oracle's analogue of the cross-platform ledger. Loud, with the
      // reason inline, never fatal — the excuse travels with the warning.
      for (const w of xOracle.waived ?? []) {
        console.warn(`  ⚠ waived: ${formatViolation(w)}`);
        console.warn(`      waiver: ${w.waiveReason}`);
      }
      // Missing measurements are warnings, not violations: a SKIP_* run
      // legitimately captures fewer platforms, and the decode/capture-count
      // guards own those failure classes. But they must be VISIBLE, or the
      // oracle silently narrows its own coverage.
      for (const m of xOracle.missing) console.warn(`  ⚠ oracle skipped: ${formatMissing(m)}`);

      if (xOracle.violations.length > 0) {
        console.error(`✗ ${xOracle.violations.length} spec-oracle violation(s) — a render disagrees with the CSS spec:`);
        for (const v of xOracle.violations) console.error(`  · ${formatViolation(v)}`);
        console.error('  The expected values are spec-derived (_expect in the fixture), so the platforms');
        console.error('  agreeing with EACH OTHER does not excuse this. Fix the platform(s) — or, if the');
        console.error('  derivation itself is wrong, correct the fixture and cite the spec in _expect.note.');
        process.exit(EXIT_SPEC_ORACLE_VIOLATION);
      }
      // A waiver whose platform now PASSES has outlived the divergence it
      // excused. Same two-sided rule (and same exit code) as the ledger's
      // stale entries: the fix is a one-line fixture edit, and leaving the
      // waiver in place would silently re-excuse the next real regression.
      if ((xOracle.stale?.length ?? 0) > 0) {
        console.error(`✗ ${xOracle.stale.length} stale oracle waiver(s) — the platform now passes; delete the waiver:`);
        for (const st of xOracle.stale) {
          console.error(`  · ${st.platform} · ${st.component} — waived as: ${st.reason}`);
        }
        process.exit(EXIT_STALE_EXPECTATION);
      }
      // An oracle that measured NOTHING must not read as green — the same
      // "check that cannot fail" doctrine behind the zero-comparison guard
      // in baseline mode. Every expectation unbound (renamed components,
      // empty run) lands here rather than in a quiet pass.
      if (xOracle.checked === 0) {
        console.error('✗ spec oracle: the fixture declares _expect but ZERO checks ran —');
        console.error('  no expectation matched any captured component (see the ⚠ lines above).');
        console.error('  A declared oracle that measures nothing must not pass.');
        process.exit(EXIT_SPEC_ORACLE_VIOLATION);
      }
    }
  }

  if (!xGate.skipped) {
    // Stale entries now FAIL. This was a warning while the harness's A/A
    // noise floor was unmeasured — the fear being that a pair at 0.9499
    // would flap and the failure would be indistinguishable from a real fix.
    // tools/visual/noise-floor.sh measured it: captures are BIT-FOR-BIT
    // identical across independent full runs (423 captures over two
    // fixtures), so a metric cannot flap run-to-run and the fear does not
    // apply. See cross-platform-gate.mjs for the full result and its scope.
    if (xGate.stale.length > 0) {
      const orphans = xGate.stale.filter((r) => r.orphaned);
      const fixed   = xGate.stale.filter((r) => !r.orphaned);
      console.error(`✗ ${xGate.stale.length} stale expectation(s) — the ledger no longer matches reality:`);
      for (const r of fixed) console.error(`  · now passing, delete the line: ${formatRecord(r)}`);
      for (const r of orphans) console.error(`  · no such component: ${formatRecord(r)}`);
      console.error('  Delete them from tools/visual/cross-platform-expectations.json.');
      console.error('  An expectation that outlives the divergence it excused is how the ledger rots.');
      process.exit(EXIT_STALE_EXPECTATION);
    }
  }

  if (useBaseline) {
    // Count how many baseline comparisons actually ran. A row only counts
    // if its .baseline.platforms contained at least one pair of {current,
    // baseline} images. Without this check, running `--baseline` with
    // zero captures would print "✓ no regressions" (because the regressed
    // counter stayed at 0) and falsely green-light CI.
    let checked = 0;
    for (const r of rows) {
      if (!r.baseline) continue;
      for (const info of Object.values(r.baseline.platforms)) {
        if (info && info.ssim !== undefined) checked += 1;
      }
    }

    if (checked === 0) {
      console.error('✗ baseline mode requested but 0 comparisons ran');
      console.error('  (no current captures match any baseline image — is the baseline empty,');
      console.error('   or did every capture step fail?)');
      process.exit(2);
    }

    if (regressionCount > 0) {
      console.error(`✗ ${regressionCount} component(s) regressed beyond thresholds (${checked} platform-comparisons ran)`);
      process.exit(1);
    } else {
      console.log(`✓ no regressions vs baseline (${checked} platform-comparisons ran)`);
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────

function collectCaptures() {
  const captures = { iOS: {}, Android: {}, web: {} };
  for (const p of PLATFORMS) {
    if (!existsSync(paths[p])) {
      console.warn(`  ⚠ ${p}: ${paths[p]} not found`);
      continue;
    }
    for (const f of readdirSync(paths[p])) {
      if (!f.endsWith('.png')) continue;
      // Skip ancillary images (e.g. iOS simulator.png)
      if (/^simulator|^logcat/.test(f)) continue;
      captures[p][f] = join(paths[p], f);
    }
  }
  return captures;
}

function sortedUnion(captures) {
  const all = new Set();
  for (const p of PLATFORMS) Object.keys(captures[p]).forEach((k) => all.add(k));
  return [...all].sort();
}

async function analyzeComponent(name, captures) {
  // Load + align every platform's PNG for this component. A single
  // corrupt / non-PNG file in any screenshots directory used to crash
  // the whole comparison run — now we isolate the failure per-platform
  // and surface it as `platforms[p].error` so the report can show which
  // file is broken without losing the rest of the comparison.
  const loaded = {};
  const loadErrors = {};
  for (const p of PLATFORMS) {
    const path = captures[p][name];
    if (!path) { loaded[p] = null; continue; }
    try {
      loaded[p] = await loadPng(path);
    } catch (e) {
      loaded[p] = null;
      loadErrors[p] = e.message ?? String(e);
      console.warn(`  ⚠ ${p}/${name}: ${loadErrors[p]}`);
    }
  }

  // Compute shared canvas size: the max width (platforms should all be 390
  // by contract — if they're not, we flag it) and max height so everyone
  // gets padded identically.
  const nonNull = PLATFORMS.map((p) => loaded[p]).filter(Boolean);
  if (nonNull.length === 0) {
    // Every platform failed to decode (or files missing). Return a
    // well-shaped row so `renderRow` can still produce a sensible entry
    // per-platform error cell, rather than crashing on undefined
    // lookups in `r.platforms[p]`.
    const platforms = {};
    for (const p of PLATFORMS) {
      if (loadErrors[p]) platforms[p] = { present: false, error: loadErrors[p] };
      else platforms[p] = { present: false };
    }
    const pairs = {
      'iOS-Android': null, 'iOS-web': null, 'Android-web': null,
    };
    return { name, platforms, pairs, baseline: null, errors: loadErrors };
  }
  const canvasW = Math.max(...nonNull.map((i) => i.width));
  const canvasH = Math.max(...nonNull.map((i) => i.height));

  const normalized = {};
  for (const p of PLATFORMS) {
    normalized[p] = loaded[p]
      ? await padToCanvas(loaded[p], canvasW, canvasH)
      : null;
  }

  // Pairwise comparisons for the HTML report.
  const pairs = {};
  const pairSpecs = [
    ['iOS', 'Android'],
    ['iOS', 'web'],
    ['Android', 'web'],
  ];

  for (const [a, b] of pairSpecs) {
    const key = `${a}-${b}`;
    if (!normalized[a] || !normalized[b]) {
      pairs[key] = null;
      continue;
    }
    // PAIRWISE canvas, not the 3-way union. Padding a pair to the union
    // meant that when the THIRD platform was the largest, both members of
    // this pair carried an identical magenta-sentinel region — and
    // identical regions AGREE, so every mean-based metric (SSIM, pixel %,
    // ΔE) was diluted toward similarity by area belonging to neither
    // image. The pipeline hunt measured a full verdict flip from this on
    // a synthetic trio. On the pairwise canvas a sentinel pixel can only
    // ever face REAL pixels from the other side, which is the sentinel's
    // entire design (pad-canvas.mjs: under-size must read as DIFFERENT).
    // When the pair's max equals the union (the common case — most rows
    // have all three platforms the same size), the pre-padded images are
    // reused and the bytes are identical to the old path.
    const pw = Math.max(loaded[a].width, loaded[b].width);
    const ph = Math.max(loaded[a].height, loaded[b].height);
    const A = (pw === canvasW && ph === canvasH) ? normalized[a] : await padToCanvas(loaded[a], pw, ph);
    const B = (pw === canvasW && ph === canvasH) ? normalized[b] : await padToCanvas(loaded[b], pw, ph);
    pairs[key] = await diffPair(
      A,
      B,
      pw,
      ph,
      `${name.replace(/\.png$/, '')}__${key}.png`
    );
  }

  // Per-platform dimension metadata. `error` populated when the PNG failed
  // to decode; the HTML report renders it as a red cell instead of "missing".
  const platforms = {};
  for (const p of PLATFORMS) {
    if (loaded[p]) {
      platforms[p] = {
        present: true,
        width: loaded[p].width,
        height: loaded[p].height,
        image: `images/${p}/${name}`,
      };
    } else if (loadErrors[p]) {
      platforms[p] = { present: false, error: loadErrors[p] };
    } else {
      platforms[p] = { present: false };
    }
  }

  // Baseline comparison — we compare each platform's current capture to its
  // baseline counterpart (same platform, same component name).
  let baseline = null;
  if (useBaseline && existsSync(paths.baseline)) {
    baseline = await compareBaseline(name, normalized, canvasW, canvasH);
  }

  return { name, canvasW, canvasH, platforms, pairs, baseline };
}

/**
 * Every colour-space violation seen this run. Recorded as well as thrown,
 * because the per-platform loader catches loader errors into a `decode
 * error` report cell — visible, but NOT build-failing. A capture in the
 * wrong colour space must be loud, so `main()` turns a non-empty list into
 * a distinct non-zero exit (3) that can't be confused with either a
 * baseline regression (1) or an empty run (2).
 */
const colorSpaceViolations = [];

/**
 * Load the cross-platform expectation ledger. A missing file is NOT an
 * error — a fixture with no known divergences legitimately has no ledger,
 * and in that case every failure is unexpected, which is the correct strict
 * default. A malformed file IS an error: silently treating unparseable JSON
 * as "no expectations" would flip the gate to maximally strict at the exact
 * moment someone fat-fingered a comma, and the resulting wall of failures
 * would look like a regression rather than a typo.
 */
function loadCrossPlatformLedger() {
  // CROSS_PLATFORM_EXPECTATIONS repoints the ledger. This exists so the gate
  // can be tested END TO END — asserting it really exits 4/5 rather than
  // trusting that the exported constants are wired up — which matters more
  // than usual for a gate whose entire job is to not be theatre.
  //
  // It is not a new way to weaken the gate: `--no-cross-platform-gate`
  // already disables it outright, so anyone wanting to dodge it has a
  // shorter path. Runs that set this are visible in the report headline
  // because the ledger path is echoed with the verdict.
  const path = process.env.CROSS_PLATFORM_EXPECTATIONS
    ? resolve(process.env.CROSS_PLATFORM_EXPECTATIONS)
    : resolve(__dirname, 'cross-platform-expectations.json');
  if (!existsSync(path)) return { expectations: [] };
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (e) {
    console.error(`✗ cross-platform-expectations.json is unreadable: ${e.message}`);
    process.exit(2);
  }
}

/**
 * Load the input fixture's `_expect` declarations (Lane A spec oracle).
 *
 * The comparator only knows the input as a LABEL (--input, threaded through
 * by test-all.sh), so the original fixture JSON is resolved relative to the
 * repo root — the same base every fixture path in this repo is written
 * against. `resolve` passes an absolute label through unchanged, which is
 * what the e2e tests use.
 *
 * Absence is tolerated in both senses: no label (bare comparator run) and a
 * label that is not a file on disk (TITAN section labels) both mean "no
 * oracle", returning null. A file that EXISTS but does not parse is an
 * error (exit 2), for the same reason as the ledger above: silently reading
 * unparseable JSON as "no expectations" would switch the oracle off at the
 * exact moment someone fat-fingered the fixture. Authoring errors inside
 * `_expect` (parseExpectations throws) are also exit 2 — the check's own
 * setup is broken, which is a different failure from a render being wrong.
 *
 * Returns the parsed Map, or null when the fixture declares no `_expect`
 * anywhere — the common, inert case.
 */
function loadSpecOracleExpectations() {
  if (!inputLabel) return null;
  const path = resolve(__dirname, '../..', inputLabel);
  if (!existsSync(path)) return null;
  let doc;
  try {
    doc = JSON.parse(readFileSync(path, 'utf8'));
  } catch (e) {
    console.error(`✗ spec oracle: input fixture ${path} is unreadable: ${e.message}`);
    process.exit(2);
  }
  let expectations;
  try {
    expectations = parseExpectations(doc);
  } catch (e) {
    console.error(`✗ spec oracle: ${e.message}`);
    console.error('  Fix the _expect block in the fixture — an oracle with a broken declaration');
    console.error('  must not run at all, or its silence would read as a pass.');
    process.exit(2);
  }
  return expectations.size > 0 ? expectations : null;
}

async function loadPng(path) {
  const buf = readFileSync(path);
  // Assert BEFORE decoding: pngjs would silently drop the offending chunk,
  // after which there is no way to tell the pixels are in the wrong space.
  try {
    assertSrgbOrUntagged(buf, path);
  } catch (e) {
    colorSpaceViolations.push(e.message ?? String(e));
    throw e;
  }
  return PNG.sync.read(buf);
}

// Canvas normalization (PAD_SENTINEL + padToCanvas) lives in
// pad-canvas.mjs so the "under-sized output must not be invisible"
// invariant is unit-testable — this file is a script and cannot be
// imported without running main().

/**
 * Pixel-diff via pixelmatch + SSIM via ssim.js, plus the six COMPARE_METRICS
 * Section 1 metrics (B2 perChannelSsim · B3 edgeSsim · B4 histogramKL ·
 * B5 pHash · B6 dssim · B7 labDeltaE). Returns diff metrics and writes a
 * visual diff PNG to the report folder.
 *
 * Backward-compat contract (Section 2 / Section 6): every new field is
 * OPTIONAL and independently nullable. Missing or null fields render as "—"
 * in the report and never gate regression checks during Phase A.
 */
async function diffPair(a, b, W, H, diffFilename) {
  const diff = new PNG({ width: W, height: H });
  // `threshold` controls per-pixel color-delta tolerance in pixelmatch's
  // YIQ space. 0.25 is generous — see below for why it HAS to be.
  //
  // ⚠ `includeAA: true` does the OPPOSITE of what this comment used to
  // claim. pixelmatch's own JSDoc reads "includeAA: Whether to SKIP
  // anti-aliasing detection", and the source gate is:
  //
  //     isExcludedAA = !includeAA && (antialiased(img1,…) || antialiased(img2,…))
  //
  // With `includeAA: true` that is `!true` → false, so AA detection NEVER
  // RUNS and every anti-aliased pixel is counted as an ordinary difference.
  // (Verified in node_modules/pixelmatch/index.js:12,74.)
  //
  // Consequence: this comparison has NO anti-aliasing suppression at all,
  // and `threshold: 0.25` is not a colour tolerance — it is the only thing
  // absorbing cross-rasterizer AA (Skia vs Core Graphics vs Blink). The 2 %
  // pixel budget absorbs the residue. Both numbers are compensating for
  // this, not expressing a deliberate tolerance.
  //
  // The behaviour is deliberately LEFT AS-IS here. Flipping the flag would
  // make the comparison strictly more lenient (AA pixels stop counting),
  // which moves every recorded number and could mask a real regression, so
  // it belongs behind the edge-masking + threshold-derivation work, not in
  // a comment fix. Correcting the comment is the safe half.
  const mismatched = pixelmatch(a.data, b.data, diff.data, W, H, {
    threshold: 0.25,
    includeAA: true,
    diffColor: [255, 80, 80],
    alpha: 0.15,
  });

  const diffPath = join(paths.report, 'diffs', diffFilename);
  writeFileSync(diffPath, PNG.sync.write(diff));

  const pixelPct = (mismatched / (W * H)) * 100;

  // SSIM — rescale to a small grid if the image is huge to keep this fast.
  const ssimScore = await safeSsim(a, b);

  // ── New metrics (Section 7 items 1–6, in spec order) ───────────────────────
  // Each helper from compare-screenshots-metrics.mjs follows the safeSsim
  // try/catch pattern: any failure (corrupt buffer, sharp-phash on tiny
  // image, etc) returns null and the rest of the row continues unaffected.

  // B6 — DSSIM derived from the existing SSIM result.
  const dssim = computeDssim(ssimScore);
  // B4 — color-histogram KL divergence (inline; no new dep). Position-
  // independent so this catches "right colors, wrong layout" vs vice versa.
  const histogramKL = computeHistogramKL(a, b);
  // B2 — per-channel SSIM (R/G/B/A). Informational per Section 8
  // decision 5 — emitted but does not gate regression.
  const perChannelSsim = await computePerChannelSsim(a, b);
  // B5 — perceptual hash + Hamming distance (64-bit DCT default,
  // Section 8 decision 6).
  const pHash = await computePHash(a, b);
  // B3 — edge-map SSIM. Sobel-3 kernel (Section 8 decision 4). Catches
  // AA-only differences without conflating fill diffs.
  const edgeSsim = await computeEdgeSsim(a, b);
  // B7 — CIE LAB ΔE (CIEDE2000). Stride-sampled 1-in-4 by default
  // (Section 8 decision 2). `--full-lab` CLI flag disables stride.
  const labDeltaE = computeLabDeltaE(a, b, fullLab ? 1 : 4);
  // Semantic-presence gate (round 91, fixes pilot-001 false positive). Per-
  // image foreground coverage vs the canonical #1A1A2E capture background;
  // consumed by classifyDivergence() as an early-exit "no-content" label
  // when both sides extracted to empty placeholders. Independently nullable
  // (Section 2 contract) so an out-of-range capture buffer doesn't break
  // the rest of the row — the classifier just falls through to its existing
  // 7-label decision tree when the signal is missing.
  const semanticPresence = computeSemanticPresence(a, b);

  // Section 4 — assemble the metric block first, then classify in one pass
  // so the same shape we serialise to manifest.json is the shape the
  // classifier sees. Avoids drift between "classified value" and "stored value".
  const metrics = {
    pixelMismatchedCount: mismatched,
    pixelMismatchedPct: +pixelPct.toFixed(3),
    ssim: ssimScore,
    diffImage: `diffs/${diffFilename}`,
    // ── New metrics (Section 2: every field optional, may be null) ───────────
    dssim,
    perChannelSsim,
    edgeSsim,
    histogramKL,
    pHash,
    labDeltaE,
    semanticPresence,
  };
  // Section 7 item 7 — classifier wired in (B-3). Returns one of:
  // identical · sub-pixel-noise · color-drift · edge-shift ·
  // structural-divergence · mixed · unknown. Always present so the
  // report renderer can rely on the field existing.
  metrics.divergence = classifyDivergence(metrics);
  return metrics;
}

/**
 * SSIM via ssim.js. Two properties of this number are easy to misread and
 * both are load-bearing when interpreting a report:
 *
 * 1. **It is GRAYSCALE.** ssim.js converts RGB→gray with Matlab's integer
 *    Rec.601 weights `(77R + 150G + 29B + 128) >> 8` before any SSIM math,
 *    and discards alpha. Two colours with matched luminance but different
 *    hue score ~1.0. For a CSS colour engine that is a structural blind
 *    spot, not a tuning problem — the ΔE metric exists to cover it.
 *
 * 2. **It is computed at a per-component resolution.** Defaults include
 *    `downsample: 'original'` + `maxSize: 256`, which box-filters and
 *    decimates by `f = round(min(W, H) / 256)` when f > 1. Only `ssim:
 *    'fast'` is overridden here. So a short capture is scored at 1× and a
 *    tall one at 1/2×, while every other metric stays at 1×. SSIM is not
 *    comparable across component heights.
 */
async function safeSsim(a, b) {
  // ssim.js expects ImageData-like objects (data: Uint8ClampedArray, width, height)
  try {
    const aImg = { data: new Uint8ClampedArray(a.data), width: a.width, height: a.height };
    const bImg = { data: new Uint8ClampedArray(b.data), width: b.width, height: b.height };
    const r = computeSsim(aImg, bImg, { ssim: 'fast' });
    return +r.mssim.toFixed(4);
  } catch (e) {
    return null;
  }
}

// (B2–B7 metric implementations live in compare-screenshots-metrics.mjs so
// they can be unit-tested in isolation. See the imports at the top of this
// file.)

async function compareBaseline(name, normalized, canvasW, canvasH) {
  const result = { platforms: {}, regressed: false };

  for (const p of PLATFORMS) {
    const baselinePath = join(paths.baseline, `${p}__${name}`);
    if (!existsSync(baselinePath) || !normalized[p]) {
      result.platforms[p] = { present: false };
      continue;
    }

    // The canvas dimensions passed in are the max across the CURRENT
    // captures only. When a baseline image has different dimensions
    // (e.g. a property now renders at the correct size where it
    // previously rendered wrong), the baseline may overflow canvasW/H.
    // `padToCanvas` uses sharp's `extend`, which only grows — passing
    // an already-larger image fails with "Image to extend contains an
    // area that is outside requested dimensions".
    //
    // Fix: compute a per-pair canvas that's max of baseline and current
    // dims on BOTH axes, then pad both images to that.
    const baselineRaw = await loadPng(baselinePath);
    const cur = normalized[p];
    const pairW = Math.max(canvasW, baselineRaw.width, cur.width);
    const pairH = Math.max(canvasH, baselineRaw.height, cur.height);

    const baseImg = await padToCanvas(baselineRaw, pairW, pairH);
    // Re-pad current if the pair canvas grew beyond the earlier pad.
    const curImg = (cur.width === pairW && cur.height === pairH)
      ? cur
      : await padToCanvas(cur, pairW, pairH);

    const pair = await diffPair(
      baseImg,
      curImg,
      pairW,
      pairH,
      `${name.replace(/\.png$/, '')}__baseline-${p}.png`
    );
    // Phase A gate (CI-blocking). This is what gates `--baseline` exit 1.
    //
    // Delegates to pairRegressed so the baseline gate and the
    // cross-platform gate cannot drift apart in meaning — same metrics,
    // same comparisons, different subject. cross-platform-gate.test.mjs
    // asserts they agree; this call is what makes that true by
    // construction rather than by two expressions being kept in sync by
    // hand.
    //
    // ΔE joined the expression with the cross-platform promotion: it was
    // computed on every pair and gated nothing, while pixelmatch cannot
    // fire on a uniform lightness shift below 66/255 and SSIM barely
    // moves when a shape is repainted in the wrong colour. See
    // cross-platform-gate.mjs for the measured rows that motivated it.
    const regressed = pairRegressed(pair, { ssimThreshold, pixelThreshold, deltaEThreshold });
    if (regressed) result.regressed = true;

    // Phase B (LOG-ONLY, not gating). Spec Section 6:
    // "Print warnings prefixed `[phase-b]` so they're easy to grep, but
    // DON'T fail the build (Phase A behavior — SSIM-only — remains the
    // gate). Phase C will gate on classifier in a future PR."
    //
    // We surface three signals on each baseline-vs-current pair:
    //   1. Classifier label is anything other than `identical` —
    //      a baseline diff that the classifier doesn't call `identical`
    //      indicates drift; spec calls out "label downgraded between
    //      baseline and current". For baseline-vs-current the baseline
    //      label is by construction `identical` (image vs itself
    //      should classify as identical), so any other label is a
    //      downgrade.
    //   2. labDeltaE.p95 > 2 — Section 3 threshold for "barely
    //      perceptible" boundary; spec lists "labDeltaE.p95 increase
    //      >2" as a Phase B trigger.
    //   3. pHash hamming > 3 — spec lists "pHash hamming increase
    //      >3". Baseline pHash is by construction 0 (image vs itself
    //      via the `--update-baseline` workflow), so >3 == drift.
    //   4. edgeSsim < 0.95 — spec lists "edgeSsim drop >0.05". Baseline
    //      edgeSsim is by construction 1.0, so <0.95 == >0.05 drop.
    if (!regressed) {
      // Strong-signal-only triggers: spec says "edgeSsim drop >0.05,
      // labDeltaE.p95 increase >2, pHash hamming increase >3" plus
      // "label downgraded". For baseline-vs-current the recorded
      // baseline label is implicitly `identical` (same image vs itself
      // when the baseline was captured), so anything STRONGER than
      // sub-pixel-noise is a real downgrade. We deliberately let
      // `sub-pixel-noise` slide — for cross-platform AA noise, every
      // pair would warn and the signal would be drowned out.
      const phaseBWarnings = [];
      const strongLabels = new Set([
        'color-drift', 'edge-shift', 'structural-divergence', 'mixed',
      ]);
      if (pair.divergence && strongLabels.has(pair.divergence)) {
        phaseBWarnings.push(`divergence=${pair.divergence}`);
      }
      if (pair.labDeltaE?.p95 != null && pair.labDeltaE.p95 > 2) {
        phaseBWarnings.push(`ΔE.p95=${pair.labDeltaE.p95}`);
      }
      if (pair.pHash?.hammingDistance != null && pair.pHash.hammingDistance > 3) {
        phaseBWarnings.push(`pHash=${pair.pHash.hammingDistance}`);
      }
      if (pair.edgeSsim != null && pair.edgeSsim < 0.95) {
        phaseBWarnings.push(`edgeSsim=${pair.edgeSsim}`);
      }
      if (phaseBWarnings.length > 0) {
        // Prefixed `[phase-b]` for easy `grep` in CI logs. stderr so
        // the line shows up alongside the existing red error stream
        // even though it doesn't fail the build.
        console.warn(`  [phase-b] ${p}/${name}: ${phaseBWarnings.join(' · ')}`);
      }
    }
    result.platforms[p] = { ...pair, regressed };
  }

  return result;
}

// COMPARE_METRICS Section 7 item 10 — Phase B regression-check, log-only.
// Compares this run's per-pair classifier labels against the recorded
// labels in tools/visual/baseline-stats.json (produced by baseline-stats.mjs).
// Warns on label-severity downgrades but does NOT exit non-zero.
// Spec Section 6: "Phase B — keep edgeSsim / histogramKL informational
// until Section 3's data-derived thresholds are committed". Phase C
// (future PR) will gate on these.
async function runPhaseBDriftCheck(rows) {
  const statsPath = resolve(__dirname, 'baseline-stats.json');
  if (!existsSync(statsPath)) {
    // Quiet skip: first run before any baseline is recorded. The phase-B
    // user can run baseline-stats.mjs and re-run to opt into checks.
    return;
  }
  let prior;
  try {
    prior = JSON.parse(readFileSync(statsPath, 'utf-8'));
  } catch (e) {
    // Corrupt baseline-stats — print a warning and bail so a malformed
    // file doesn't silently disable the drift check forever.
    console.warn(`  [phase-b] could not parse ${statsPath}: ${e.message ?? e}`);
    return;
  }

  // Fixture-scope check. The lookup below is keyed on `${component}__${pair}`
  // and silently `continue`s on a key it cannot find, so a stats file
  // recorded from a DIFFERENT fixture disables the entire check while
  // leaving it looking healthy — no warning, no output, nothing to notice.
  //
  // That is not hypothetical: the committed baseline-stats.json was a 6-pair
  // `examples/wpt/css-color/color-003.json` snapshot from 2026-07-08, and
  // ZERO of the 327 visual-test pairs matched any of its keys. The drift
  // check had been a complete no-op for every visual-test run since.
  //
  // Log-only, matching the rest of Phase B — but loud, because a check that
  // measures nothing is worse than one that is absent.
  if (prior.inputLabel && inputLabel && prior.inputLabel !== inputLabel) {
    console.warn(
      `  [phase-b] SKIPPED — baseline-stats.json was recorded from ` +
      `"${prior.inputLabel}" but this run is "${inputLabel}". Regenerate it ` +
      `with \`node tools/visual/baseline-stats.mjs\` after a run of this fixture.`,
    );
    return;
  }
  // Severity ordering used for "downgrade" detection — the CANONICAL
  // SEVERITY_RANK, imported. This used to be an inline copy that claimed
  // to "match classify-divergence.mjs's SEVERITY_RANK" and had silently
  // drifted: it lacked glyph-metric-noise, no-content and
  // test-not-applicable entirely (all three fell through to unknown = 3),
  // so a pair degrading from structural-divergence to NO-CONTENT — the
  // pipeline producing nothing comparable at all — ranked as an
  // IMPROVEMENT and the drift check stayed quiet. A comment promising two
  // tables agree is not a mechanism; an import is.
  const rank = SEVERITY_RANK;
  let downgrades = 0;
  let comparable = 0;    // pairs present in BOTH this run and the stats file
  let seen = 0;          // pairs present in this run at all
  for (const r of rows) {
    for (const [pairKey, pair] of Object.entries(r.pairs ?? {})) {
      if (!pair) continue;
      seen += 1;
      const key = `${r.name}__${pairKey}`;
      const priorLabel = prior.perPair?.[key];
      const currentLabel = pair.divergence;
      if (!priorLabel || !currentLabel) continue;
      comparable += 1;
      // Downgrade = current rank > prior rank (i.e. moved toward
      // structural). Equal-rank or improvement is fine.
      const priorRank = rank[priorLabel] ?? rank.unknown;
      const currentRank = rank[currentLabel] ?? rank.unknown;
      if (currentRank > priorRank) {
        downgrades += 1;
        console.warn(`  [phase-b] ${key}: ${priorLabel} → ${currentLabel}`);
      }
    }
  }
  // Coverage check — the second half of the same defence. Even with a
  // matching inputLabel, a renamed component or a partial stats file can
  // leave the check comparing almost nothing, and the `continue` above makes
  // that indistinguishable from "everything is fine". Always state the
  // denominator so the reader can tell a clean run from an empty one.
  if (comparable === 0) {
    console.warn(
      `  [phase-b] covered 0 of ${seen} pair(s) — no key in baseline-stats.json ` +
      `matched this run, so nothing was actually checked. Regenerate it with ` +
      `\`node tools/visual/baseline-stats.mjs\`.`,
    );
  } else if (downgrades > 0) {
    console.warn(`  [phase-b] ${downgrades} label downgrade(s) across ${comparable}/${seen} comparable pair(s) vs ${statsPath} (log-only — Phase C will gate)`);
  } else {
    console.log(`· phase-b drift: ${comparable}/${seen} pair(s) comparable · 0 downgrades`);
  }
}

async function syncBaseline() {
  console.log('→ updating baseline from current captures…');

  // Guard against clobbering a good baseline with nothing. If the current
  // capture run produced zero PNGs (e.g. test-all.sh was run with all
  // platforms skipped, or every platform failed), bail out rather than
  // silently wiping tools/visual/baseline/ and pretending we updated it.
  let totalAvailable = 0;
  for (const p of PLATFORMS) {
    if (!existsSync(paths[p])) continue;
    totalAvailable += readdirSync(paths[p])
      .filter((f) => f.endsWith('.png') && !/^simulator|^logcat/.test(f))
      .length;
  }
  if (totalAvailable === 0) {
    console.error('✗ refusing to update baseline: no current captures found.');
    console.error('  expected PNGs under apps/{ios,android,web}-harness/screenshots/');
    console.error('  (did a capture step fail, or was everything skipped?)');
    process.exit(2);
  }

  // UPSERT, never wipe. The old implementation rmSync'd the whole baseline
  // dir and rewrote only the current run's captures — which silently deleted
  // every baseline belonging to OTHER fixture suites and to platforms that
  // were skipped this run (bit us twice: legacy commit c36d030 and the
  // 2026-07 hard-prune campaign, where UPDATE_BASELINE with SKIP_IOS=1
  // deleted 12 iOS rows and a whole per-property suite). Now: a platform
  // with zero captures is left untouched, and existing baselines for
  // components not in this run survive. Deleting a retired component's
  // baseline is a deliberate manual `git rm`, not a side effect.
  mkdirSync(paths.baseline, { recursive: true });
  let count = 0;
  for (const p of PLATFORMS) {
    if (!existsSync(paths[p])) continue;
    for (const f of readdirSync(paths[p])) {
      if (!f.endsWith('.png')) continue;
      if (/^simulator|^logcat/.test(f)) continue;
      copyFileSync(join(paths[p], f), join(paths.baseline, `${p}__${f}`));
      count += 1;
    }
  }
  console.log(`✓ upserted ${count} baseline image(s) into ${paths.baseline} (absent platforms/components untouched)`);
}

function summarize(row) {
  const bits = [];
  for (const [k, v] of Object.entries(row.pairs)) {
    if (!v) continue;
    bits.push(`${k} ${v.ssim?.toFixed(2) ?? '—'}`);
  }
  return bits.join(' · ');
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML report — rendering extracted to compare-screenshots-html.mjs
// (item 9; CLAUDE.md ≤300-line file budget). The exported `renderHTML`
// imported at the top of this file is called directly from main().
// ─────────────────────────────────────────────────────────────────────────────


// Entry point — placed at EOF so every module-level const above has already
// been initialized before main() runs.
await main();

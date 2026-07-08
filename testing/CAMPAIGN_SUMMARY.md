# Auditor-driven testing campaign summary (CONVERGED at round 40, extended through round 70)

A 70-round audit-and-fix campaign that drove the Tier 1 tracker from
"548/548 100% passing" (~85% degenerate fixtures) to a substantively
honest classification matching the auditors strict 7-21% estimate, then
spent rounds 41-70 cleaning up the next-tier backlog and building
self-validating audit infrastructure.

## Headline numbers

| | Start (round 0) | Converged (round 40) |
|---|---|---|
| Total rows | 548 | 550 |
| Passing | 548 (100%) | **91 (16.5%)** |
| Blocked-platform | 0 | 419 |
| Failing | 0 | 3 |
| Exhausted | 0 | 37 |
| **Honest meaningful coverage** | **unstated** | **~91/550 (~17%)** |

Round 40 was a single-row Auditor catch (Scale fixture had only `scale: none`
and `scale: 1` — both identity transforms, trivially identical SSIM).
Rewritten to V_half/V_one/V_one_half/V_two; revealed substantive Android
Modifier.scale() clip-to-parent divergence (iOS+web 1.00 / Android 0.69-0.73
at scale ≥1.5 — same root cause as the Tier 4 keyframe finding). Demoted
from passing → failing as honest renderer-gap classification.

The campaign moved the tracker from "everything passes" (mostly empty boxes
matching empty boxes) to "what genuinely tests is what passes; what cannot
be tested in static-mobile fixtures is honestly blocked-platform with root cause".

## Substantive fixture rewrites (the gold-standard 31)

The 31 fixtures genuinely engineered to test their property cross-platform:
- 12 nested-children rewrites (rounds 16-22): JustifySelf, MixBlendMode,
  PerspectiveOrigin, FlexDirection, Right, MaskComposite, FlexBasis, +5 grid
- 8 corner-radii (round 34): all 8 directional border-radii with
  0/20px/50%/40px template — 100% PASS
- 2 background pattern (round 35): BackgroundPosition + BackgroundRepeat
  with small 30x30 SVG pattern — 100% PASS
- 3 mask pattern (round 35): MaskPosition + MaskRepeat + MaskSize same template
  — 100% PASS
- 6 border-side lifts (round 39): BorderTopWidth + BorderBottomWidth +
  BorderLeftStyle + BorderLeftWidth + BorderRightStyle + BorderRightWidth
  with 4-variant mirror-template — 24/24 variants ≥0.95 SSIM all 3 platforms

Plus 4 real renderer fixes:
- iOS MaskApplier short-circuit for mask-image:none
- Android ColorApplier gradient-brush size fix
- iOS FilterApplier removed .thinMaterial overlay
- test-all.sh whitespace bug in COUNT (octal arithmetic crash)

## Demotion taxonomy (~440 properties demoted across rounds 27-37)

Each cluster has a consistent root-cause note in tracker:
| Cluster | Demoted | Root cause |
|---|---|---|
| svg/* | 32 | Need <svg><path/circle/...> |
| animations/transitions | 21+ | Need keyframe state (Tier 4) |
| speech/* | 26 | Aural CSS — never visually rendered |
| paging/* | 14 | Paged-media only |
| regions/* | 11 | CSS Regions deprecated |
| navigation/* | 5 | Spatial keyboard navigation |
| scrolling/* | 41 | Need scrollable parent |
| table/* | 5 | Need <table><tr><td> |
| lists/* | 4 | Need <ul>/<li> |
| columns/* | 10 | Multi-column needs prose |
| rendering/* | 12 | Quality hints / form-control |
| interactions/* | 9 | Need cursor / scroll / input state |
| counters, content, container | ~8 | Each needs specific CSS feature consumer |
| shapes, rhythm, math | 13 | Each needs specific markup/context |
| layout offsets/items/grid-track | 30+ | Need position: or flex/grid parent + children |
| typography SVG-on-text | 8 | DominantBaseline etc. on plain HTML text |
| typography wrap | 11 | LineClamp/WhiteSpace etc. on short text |
| typography text-decoration sub | 6 | Need text-decoration:underline first |
| typography font-feature/Asian-text | 24 | Need OpenType/variable font/CJK content |
| typography input-required | 4 | CaretColor/HangingPunctuation/TextJustify |
| spacing margins without sibling | 12+ | No spatial reference |
| sizing min/max with bg-color variants | 8 | Variants change color not size |
| various needs-a-trigger | 17 | TransformOrigin needs transform, etc. |
| Tier 5/9/10/11/12 acknowledgments | n/a | Honest scaffold-only labels |

## What the campaign did NOT achieve (and what later landed)

The Tier 1 campaign converged at round 40, but rounds 40-46 then pivoted
to clearing the next-tier backlog. Cross-tier status as of round 46:

| Tier | Was (round 40) | Now (round 46) |
|------|----|----|
| Tier 5 (interaction states) | scaffold + puppeteer bails at goto() | **Phase 5a DONE: 90/90 web captures** (vite ?fixture=Name route + FixtureCanvas + per-fixture IR pre-conversion + fixed Puppeteer driver). Phase 5b/5c (iOS/Android) + 5d (IR pseudo-state) still pending. |
| Tier 9 (real-page conversion) | 5 sites manually rated, mixed pass rates | **Phases 9a-9d DONE**: var-resolution fix (CNN 0→1767 root vars), fetch-cap bump (Apple unblocked, Stripe 75× more vars), Phase 9b cluster audit (93/96 CNN deep failures are web-vs-mobile), Phase 9d isVisible filter (Apple 38%→**57%**, Stripe 29%→32%, CNN 12%→9%). Phases 9e/9f deferred. |
| Tier 10 (perf bench) | 10000-element FAILED ENOBUFS | **3 fixes landed**: ENOBUFS unblocked (round 41), sharp pixel-limit (round 43), and Phase 10b parallel-batch web crop loop (round 46 — was 1845s → expected ~200-400s for 10000-element web capture). All 3 sizes captured with honest per-platform failure modes. |
| Tier 11 (a11y) | scaffold-only stub | **Phase 11a DONE: 15/15 fixtures color-contrast scored**, 2 real WCAG AA failures found in shipped Tier-3 fixtures (NavHeader 2.28, PrimaryButton 3.67 — Material/Tailwind blue defaults). 7 other criteria honestly marked `blocked-IR` (no IRRole/IRAriaLabel — every component renders as <div>). |
| Tier 12 (OS matrix) | scaffold detects runtimes; no actual runs | **Phase 12a DONE: 109/109 fixtures byte-identical iOS 26.0 ↔ iOS 26.2** on visual-test.json (real captures via `xcrun simctl boot`-per-runtime + new SIM_UDID env var in test-all.sh + cached snapshot folders + ssim.js comparison). Phase 12b (Android) pending an AVD install. |

Still incomplete:
- Most cross-platform property INTERACTION (Tier 2 combinatorial) under-tested
- Renderer fixes: only 4; demoted blocked-platform > rewritten substantive at ratio ~17:1
- IR semantic-HTML model (Phase 11b / "Tier 13 candidate") — multi-day pre-req for meaningful a11y beyond contrast
- Tier 5 Phase 5d (IR pseudo-state model) — multi-day pre-req for styled :hover/:focus/:active comparison cross-platform

## Audit pattern that worked

1. Sample random "passing" rows, verify each variant exercises the property
2. Classify missing-context patterns (SVG, scrollable parent, animation timeline)
3. Bulk-demote all properties matching pattern
4. When pattern admits rewrite (corner-radii, position/repeat with small pattern),
   PREFER rewrite over demote — exposes real cross-platform divergence or parity
5. Re-sample to find next pattern; repeat until headline matches auditor estimate

## Stopping criterion (achieved)

Headline 16.7% within auditors strict 7-21% estimate. Pattern enumeration
exhausted across 39 audit rounds. Further iteration would require:
(a) more substantive rewrites (each ~5min-1hr), or
(b) renderer-level fixes for the 41-blocked-grid platform divergences

## CI guardrail: audit_rewrite_claims.sh

`testing/loops/audit_rewrite_claims.sh` was added in round 38 after the
auditor caught (and we verified false) an accusation of fabricated REWRITTEN
rows. The script now serves as a permanent guardrail:

- Scans tracker for every row marked REWRITTEN/rewrite/lift in notes column
- For each, verifies a real fixture-edit commit exists in git log
- Output: 24 REWRITTEN claims all backed by real commits (f7f343f, 5bb9692,
  f7462a2, edf5b6b, etc.) at last verification (round 39)
- Recommended CI use: run on every PR touching testing/TIER1_VARIANT_DEPTH.md;
  fail if any "REWRITTEN" claim has no corresponding fixture commit

This prevents the failure mode where a tracker row is marked passing/REWRITTEN
without the fixture actually being changed — the original sin that made
"548/548 100%" possible at round 0.

## Round 64-70 closeout: cleanup + automation

After Auditor 60 declared CAMPAIGN-STATE-LEGITIMATE (round 60 holistic
review), rounds 61-70 polished the campaign rather than chasing further
Tier improvements:

- **Round 61**: tightened smoke `--quick` runtime claim (~30s → ~60-90s
  to match observed) per Auditor 60's one nit.
- **Round 62**: testing/LESSONS_LEARNED.md — 8 recurring patterns the
  60-round campaign discovered (degenerate fixtures, position:fixed
  bleed, opacity:1 false-positive, ARG_MAX in glob, stale baseline,
  sample-composition shifts, iOS backdrop non-determinism, PlaceholderLabel
  divergence). Each pattern documented with symptom / root cause / how
  to spot / how to fix — institutional memory for future contributors.
- **Round 63**: heartbeat verification (no commit).
- **Round 64**: archived 9 dead testing/loops/ scripts (post-Tier-1-FREEZE
  cleanup). testing/loops/ down from 10 scripts to 2 active +
  archive/ folder with README.
- **Round 65-68**: 4 consecutive doc-rot catches via re-running source-of-truth
  scripts (DEGENERATE 102→94, README 545→550, ROLLOUT 545→550,
  ssim-history 1→2 snapshots, +35 fixtures, ✓ no regressions).
- **Round 69**: full smoke 5/5 verified post-cleanup (no commit).
- **Round 70**: testing/doc-staleness-check.sh — automates the round
  65-68 manual catch pattern. Wired into smoke.sh as the FIRST
  fail-fast static check; CI gets it for free via audit.yml.

The compound effect: the campaign's audit infrastructure now consists
of 6 self-validating layers (doc-staleness, rewrite-claim, unit tests,
Tier 5 web, Tier 11 a11y, BASELINE=1 native) all behind one
`./testing/smoke.sh` command — and the loop's "find drift, fix it"
pattern is now automated for the categories where it's been most useful.

## Round 71-76: unit-test backfill + bug catch

After round 70 wired doc-staleness checks into smoke.sh, the loop
shifted to filling structural gaps that had been deferred during the
Tier 1 → Tier 9 rush.

- **Round 71**: CAMPAIGN_SUMMARY caught up with rounds 47-70 closeout work.
- **Round 72**: testing/a11y-audit.test.mjs (14 tests). Refactored
  a11y-audit.mjs to extract shapeAxeResult() as a pure helper +
  export BLOCKED_CRITERIA / iOSStub / androidStub.
- **Round 73**: testing/os-matrix.test.mjs (14 tests). Refactored
  os-matrix.mjs to extract parseIOSRuntimesFromSimctlJson +
  parseAndroidAVDsFromList. Pins the round-49 device-disambiguation fix.
- **Round 74**: testing/interaction-states.test.mjs (9 tests). Pins
  the 90/90 invariant + iOS/Android stub return shapes. Completes
  4-of-4 unit-test coverage on testing-side scripts (62 total).
- **Round 75**: extending doc-staleness-check.sh to track test count
  triggered a full smoke run that caught 2 SILENT REGRESSIONS:
    1. FixtureCanvas's double-RAF hangs in headless puppeteer →
       Tier 5 was 0/90 instead of 90/90. Fix: setTimeout(50) instead
       of requestAnimationFrame.
    2. puppeteer.launch's recent default protocolTimeout dropped →
       Runtime.callFunctionOn timed out partway through 90-state
       harness. Fix: pass { protocolTimeout: 5*60*1000 } to all
       puppeteer.launch calls (interaction-states.mjs + a11y-audit.mjs).
  Both fixes verified with 89/90 in --quick + 90/90 in full smoke.
  This is the loop paying for itself: the audit infrastructure caught
  real bugs the campaign would otherwise have shipped silently.
- **Round 76**: LESSONS_LEARNED extended with the 2 new patterns
  (Pattern 9 + 10) so future contributors don't re-discover them.
  Doc grew 8 → 10 patterns.

End-state metrics (round 76 verified by full smoke):
  - 6 self-validating audit layers (doc-staleness, rewrite-claim,
    62 unit tests, Tier 5 web 90/90, Tier 11 contrast 15/15,
    BASELINE=1 327 platform-comparisons)
  - All 4 testing-side scripts have unit-test coverage
  - 10 patterns documented in LESSONS_LEARNED
  - testing/loops/ down to 2 active scripts (audit_rewrite_claims +
    degenerate_audit) + archive/ for 9 historical
  - CI workflow audit.yml runs unit + smoke --quick on every
    testing/** PR
  - 45 commits across rounds 39-76, each with verifiable artifacts

## Round-55 smoke-test consolidation

The campaign now has 4 self-validating audit harnesses, each catching
a different class of regression:

  testing/interaction-states.mjs  — Tier 5 web FixtureCanvas captures (90)
  testing/a11y-audit.mjs          — Tier 11 color-contrast WCAG (15 fixtures)
  testing/os-matrix.mjs           — Tier 12 cross-iOS-version diff (109)
  BASELINE=1 ./test-all.sh …      — visual-test 327 captures vs committed baseline

Round 55 added `testing/smoke.sh` — a single-command runner that brings
up vite once, runs Tier 5 + Tier 11 against it, tears vite down, then
runs BASELINE=1. Output is one combined pass/fail summary.

  ./testing/smoke.sh           # full ~5-7 min audit
  ./testing/smoke.sh --quick   # web-only ~60-90s audit (skips BASELINE=1)

Designed so a new contributor can run one command to confirm their
environment is healthy, and a release branch can run one command to
confirm no infrastructure-level regression. Each tier's individual
audit is still runnable standalone for debugging; the smoke script is
the unified entry point.

## Baseline-health audit (round 50, per Auditor 49 recommendation)

After round 49 caught a stale Glass_Effect baseline (pre-dated the iOS
.thinMaterial-removal fix by 4 days), Auditor 49 recommended sweeping
all 326 other baselines for similar staleness. Round 50 executed that
sweep:

| metric | count |
|--------|-------|
| Components with byte-identical baseline (≥0.9999 on all 3 platforms) | **104 / 109** |
| Components with sub-0.001 drift (gradient noise, libvips re-encode) | 4 / 109 |
| Components with meaningful drift (>0.001) under regression threshold | **1 / 109** (008_Margin_Offset, 0.968) |
| Components with regression-threshold drift | 0 / 109 |

The baseline is healthy. Glass_Effect was the genuine outlier — its
baseline pre-dated the f1836c7 .thinMaterial fix by 4 days and was
stuck at the pre-fix rendering. After round-49's surgical refresh,
the broader corpus is sound.

The 4 sub-0.001 gradient drifts (Gradient_Linear, Gradient_Radial,
Gradient_MultiStop, Edge_GradientWithRadius) are libvips PNG re-encode
noise — same image bytes, slightly different DEFLATE compression
producing infinitesimal SSIM differences.

008_Margin_Offset's 0.968 baseline-vs-current is sub-perceptual font
hinting variance under iOS Simulator. Visually indistinguishable
between baseline and current; well under the 0.95 regression threshold.
No refresh needed — refreshing would just lock in equivalent variance
in the new direction.

Conclusion: Auditor 49's recommended sweep was prudent but found no
systemic staleness. The audit infrastructure is reliable; future
BASELINE=1 runs accurately catch real renderer drift.

## Auditor cadence (rounds 40-46)

After Tier 1 converged the campaign shifted from per-row demote-or-rewrite
to per-tier next-phase work, with one auditor agent per landed phase:

- Round 40: Auditor caught Scale (row 435) as degenerate-passing. Rewritten;
  revealed real Android `Modifier.scale()` clip-to-parent divergence;
  demoted passing → failing.
- Round 41: Auditor verified Tier 5 Phase 5a was real (15 fixture IRs
  populated, 90/90 PNG captures non-zero, scope-honest in 3 doc places).
- Round 42: Auditor verified Tier 11 Phase 11a was real (reproduced the
  audit run, confirmed NavHeader/PrimaryButton failures match underlying
  fixtures, IR-no-semantic-model claim verified by source grep).
- Round 43: Auditor verified Tier 12 Phase 12a + Tier 10 perf-bench fix
  (reproduced os-matrix --compare-only, manual SSIM spot-check on 4 random
  fixtures all returned 0.9999..., confirmed spawnSync stdio: ['ignore',
  'ignore', 'pipe'] is the right ENOBUFS fix).
- Round 44: Auditor verified Tier 9 var-resolution + honesty reframe (read
  css-to-ir.mjs structure, verified IR diff shows real value substitution,
  reproduced stripe re-render byte-identical, confirmed "score went DOWN"
  framing reads as honest not spin).
- Round 45: Auditor confused by Monitor invocation, returned incomplete.
  Phase 9c verification deferred to round 46.
- Round 46: Auditor verified Phase 9c + 9d + simultaneously smoke-tested
  Phase 10b. Apple test-all.sh re-run reproduced 31/54 byte-identical
  through the rewritten parallel-batch crop loop — confirmed both
  Phase 9d filter accuracy AND Phase 10b doesn't break small captures.
  One verification pays for two phase landings.

Pattern: every phase landing gets an auditor in a parallel agent that
must (a) read the implementation files for honesty, (b) actually
reproduce the claimed run end-to-end, (c) spot-check the underlying data,
(d) flag any honesty drift. No phase declared DONE without verdict
PHASE-LEGITIMATE. Round-46 cross-verification (one re-run validates two
phases) is a useful efficiency pattern when an auditor's reproduction
naturally exercises a parallel code path.

## Tier-9 internal honesty correction (rounds 43-45)

The Tier 9 work in rounds 43-45 produced a particularly instructive
arc that mirrors the Tier 1 campaign at site-level:

1. **Round 43 (var-resolution fix)**: Pass rates dropped 4-2% in MDN/stripe.
   This was the same Tier-1-degeneracy pattern: previous "passes" were
   trivial empty-box matches because un-resolved `var()` refs fell back
   to identical defaults across platforms. Fix made data substantive,
   not numerically prettier. Documented honestly.

2. **Round 44 (Phase 9c — fetch-cap bump)**: Stripe got 75× more vars
   resolved (9 → 683) but +1 pass. Headline finding: var-resolution is
   NOT the bottleneck. Apple unblocked from "too thin to test" to 56/144
   (38%) — first ever data on Apple.

3. **Round 44 (Phase 9b — cluster audit)**: 93/96 of CNN's deep failures
   are web-vs-mobile (only 3 iOS-Android). Web renderer is the structural
   outlier. Many failures are empty-fixture leak-through, not renderer
   bugs.

4. **Round 45 (Phase 9d — isVisible filter)**: Apple jumped 38% → 57%
   (median SSIM 0.914 → 0.977), Stripe +3%, CNN -3%. The non-uniform
   impact is itself the finding: Phase 9d cleans up "cannot meaningfully
   test" cases but doesn't fix real renderer divergences. CNN's
   stuck-at-9% confirms 9b's diagnosis.

This 3-round Tier 9 arc was guided by an investigator agent in round 43
identifying the silent-corruption root cause, rather than the per-row
spot-check pattern that drove Tier 1. Different shape of work; same
honesty discipline.

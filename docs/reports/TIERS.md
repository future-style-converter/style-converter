# Testing tiers (outer tracker)

The 550-property baseline (`testing/PERFECT.md`) verifies that every IR
property roundtrips through the converter and that all three platforms
agree on a default rendering. This file tracks the next 12 tiers of
testing depth — each one expands coverage along a different axis.

Iterated by `/loop`. Each iteration:

1. Reads this file, picks the FIRST tier with status `in-progress`
   (or first `pending` if none in-progress; mark it `in-progress`).
2. Reads that tier's sub-tracker (path in column 5), picks first
   `pending` sub-task.
3. Does the sub-task in DEPTH (see anti-laziness contract below).
4. Updates the sub-tracker, commits.
5. When the sub-tracker is fully `complete`, marks this tier
   `complete` and moves on.

## Anti-laziness contract (read every iteration)

The previous loop cut corners. These rules forbid the shortcuts:

1. **Enumerate every parser branch.** Read the full parser file. List
   every accepted value form (keyword, length, percent, function,
   calc, var, expression, multi-value, etc.). The fixture must cover
   ALL of them. No "I'll skip the rare ones."
2. **If a variant fails, fix the renderer.** The default action is to
   edit the matching `style/<category>/<Name>{Config,Extractor,Applier}`
   triplet on the diverging platform. Dropping a variant is allowed
   ONLY after a written `blocked-platform: <root cause>` note in the
   sub-tracker AND only when the divergence is genuinely fundamental
   (rasterizer-level cap, OS feature gap). Cosmetic differences are
   not enough.
3. **One sub-task per iteration.** No batching multiple sub-tasks to
   inflate the commit count. Going wide instead of deep is a violation.
4. **No marking complete without verification.** Each sub-task ends
   with a re-run of `./test-all.sh` on the new fixture and the
   scoreboard pasted in the commit message. No "I trust the prior run."
5. **Visual-test regression check after any code change.**
   `BASELINE=1 ./test-all.sh examples/visual-test.json` must report
   0 regressions before commit. If it regresses, fix or revert.
6. **Document why, not just what.** Commit messages explain root cause
   + fix, not just "X variants pass."

## The 12 tiers

| # | tier | success criteria | sub-tracker | status |
|---|---|---|---|---|
| 1 | Variant depth — every parser branch per property | Each of 550 properties has a fixture covering ≥ N variants where N is the parser's accepted-value-form count. Every variant passes ≥ 0.95 SSIM. | testing/TIER1_VARIANT_DEPTH.md | **partial — 91/550 rows show passing (16.5%) per Auditor rounds 16-40 systematic demotion. 419 blocked-platform. 3 failing. 37 exhausted. Headline within auditor strict 7-21% range. Campaign converged at round 39 + round 40 micro-fix (Scale degenerate caught by Auditor 40, rewritten to scale 0.5/1/1.5/2 → revealed real Android Modifier.scale clip-to-parent divergence, demoted passing→failing). 32 substantive fixture rewrites + 4 real renderer fixes + ~446 honest demotions. |
| 2 | Combinatorial (property × property) | A 33×33 category-pair matrix where each cell has at least one fixture exercising both categories together. All cells pass. | testing/TIER2_COMBOS.md | **partial — coverage shallow** (526/561 cells "pass" but Auditor round 15 found ~99 cells use no-op default values like transform:none / animation-name:none / appearance:auto for one or both reps; passing SSIM mostly reflects identical empty-box comparisons rather than property-combination testing. 35 blocked-platform with shared boilerplate. Tier 2 needs combo-fixture redesign to use varied non-default values per category rep.) |
| 3 | Real component fixtures | 30–50 realistic UI components (Material card, iOS settings row, navigation header, modal, form input, toast, badge, pagination, …) each touching ≥ 15 properties. All pass. | testing/TIER3_COMPONENTS.md | **partial: 8/15 passing, 7 failing — NavHeader promoted after test-all.sh whitespace fix; PrimaryButton + 5 components share Android-Compose flex+text+border-radius root cause; IOSSettingsRow has different (iOS font metrics) root cause** |
| 4 | Animation/transition keyframe testing | For every animatable property, snapshot at t=0%, t=50%, t=100% of a 1s transition. All three platforms must produce visually-aligned intermediate frames (≥ 0.90 SSIM at midpoint, ≥ 0.95 at endpoints). | testing/TIER4_KEYFRAMES.md | **partial — 14/15 passing + 1 blocked-platform** (scale keyframe at 0.74: Android Modifier.scale() clips to parent bounds while iOS+web extend beyond — substantive Android impl gap) |
| 5 | Interaction state testing (:hover, :focus, :active, :checked) | For each interactive selector, drive the state programmatically (Puppeteer/Espresso/XCUITest), capture, compare. Every state matches cross-platform. | testing/TIER5_INTERACTIONS.md | **Phase 5a DONE (round 40): web slice 90/90 captures**. vite ?fixture=Name route + FixtureCanvas + per-fixture IR pre-conversion (`npm run build-fixtures`) + fixed Puppeteer driver (waitForSelector, mouse.down for :active, browser cleanup). 15 components × 6 states = 90 PNGs under testing/interaction-snapshots/. **Captures browser-default state changes only** — Style-Converter IR has no pseudo-class support yet (Phase 5d / Tier 13 follow-up). iOS XCUITest (5b) + Android Espresso (5c) harnesses still stubs. See testing/TIER5_PLAN.md. |
| 6 | Layout under constraint changes | Resize viewport, change parent constraints, rotate device. Verify flex/grid/percent/overflow respond correctly cross-platform at 3+ viewport sizes. | testing/TIER6_CONSTRAINTS.md | **complete (5/5 passing)** |
| 7 | CSS parser fuzz testing | Random + malformed CSS input → parser fails gracefully (returns null, never panics). Valid edge cases (empty calc(), zero-stop gradient, no-arg transform fns) don't crash any renderer. | testing/TIER7_FUZZ.md | **complete (15/15 — converter does not crash on any malformed/edge-case input)** |
| 8 | SSIM regression history | Per-fixture min SSIM tracked in JSON over time. CI alerts on regression > 0.05 in any commit. | testing/TIER8_HISTORY.md | **complete** |
| 9 | Real-world page conversion | Take 10 real websites' CSS, run through the converter, render via all three engines, manually rate fidelity. Average score ≥ 4/5. | testing/TIER9_REAL_PAGES.md | **round 53 reframe**: round-43 numbers for tailwind+twitter (both 92%) were stale — used a different IR sample than current (Phase 9d/9h filtered visibility). Re-rendered all sites with current adapter: tailwind 62/150 (41%) BUT 0 deep failures, mean SSIM 0.952; twitter 85/117 (73%); apple 27/48 (56%); cnn 45/144 (31%, was 9% pre-9e, deep failures 99 → 10); stripe 48/123 (39%); MDN 15/57 (26%). Numbers reflect different sample sizes after Phase 9d/9h, not real regressions — same renderer behavior. Bigger picture: Phase 9e (CNN +21%) is the biggest single fix. Phase 9j scoping (round 52) confirmed remaining deep failures are PlaceholderLabel architectural divergences requiring coordinated multi-platform changes. |
| 10 | Performance benchmarks | Render time + memory for 100 / 1000 / 10000-element trees on each platform. No platform > 2× slower than the slowest baseline. | testing/TIER10_PERF.md | **round 46 web-batching fix landed**: Phase 10b web slice DONE (commit 6a81428) — hoisted sharp.metadata() out of the per-component crop loop + parallelised extracts in batches of 16. Was 1845s for 10000-element web capture (20000 libvips re-decodes); expected ~200-400s after the fix. Smoke-tested via Apple Tier 9 re-render (31/54 reproduced byte-identical, no small-capture regression). 100/1000-tree captures unchanged. iOS 1906-cap + Android OOM at 10000 remain pending (~4-8h each). |
| 11 | Accessibility audit (WCAG 2.1 AA) | Every component → screen reader output, focus order, contrast ratio ≥ 4.5:1, keyboard navigation. All AA criteria pass. | testing/TIER11_A11Y.md | **Phase 11a DONE (round 41): web color-contrast slice working** — 15/15 fixtures scored, 2 real WCAG AA contrast failures found in shipped Tier-3 fixtures (NavHeader 2.28, PrimaryButton 3.67 — Material/Tailwind blue defaults). All 7 other WCAG criteria recorded as `blocked-IR` (no IRRole/IRAriaLabel/IRAlt — every component renders as `<div>`; honest classification, not "passing"). iOS XCUITest (11/9) + Android Espresso (11/10) harnesses still stubs. Pre-req IR semantic-model work (~2-3 days) tracked as Tier 13 candidate. |
| 12 | Snapshot stability across OS versions | Same fixtures rerun on iOS 16/17/18 and Android API 24/30/34/35 simulators. Every fixture passes within ±0.05 SSIM across versions. | testing/TIER12_OS_MATRIX.md | **Phase 12a DONE (round 42): cross-iOS-version slice working** — 109/109 fixtures byte-identical across iOS 26.0 ↔ iOS 26.2 (visual-test.json, real captures, real SSIM). New `SIM_UDID` env var added to test-all.sh disambiguates same-name devices across runtimes. iOS 16/17/18 + Android API 24/30/34/35 still `not-installed` on this machine — auto-detect wired so adding a runtime requires zero code changes. Phase 12b (Android AVD runner): pending ~3-4h. |

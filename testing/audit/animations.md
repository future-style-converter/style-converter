# Phase 12 Audit — animations

READ-ONLY audit. Scope: 26 IR properties under `irmodels/properties/animations/`
(Animation*, Transition*, ViewTimeline*, ViewTransition*, TimelineScope).

## Inventory

- **IR properties**: 26 (confirmed — one `*Property.kt` per)
- **CSS parsers**: present under `parsing/css/properties/longhands/animations/`
- **Fixtures**: 10 JSON files in `examples/properties/animations/` (excluding README).
  Good variant coverage for timing-functions, delays (incl. negative), iteration,
  transition, view-timeline, view-transition. No `audit-phase12.json` consolidated
  fixture exists; the existing ten already cover the needed variants.
- **Baselines**: **zero** animation baselines in `testing/baseline/` (0 of 327
  files match animation/transition/timeline). This category has never been
  baselined — consistent with its time-dynamic nature.

## Platform triplet structure (divergent, non-canonical)

The canonical contract ("one triplet per IR property") is **not** followed here.

| Platform | Shape | Files | Verdict |
|---|---|---|---|
| **Web** | Per-property triplet (38 TS files) | AnimationName/Delay/Duration/... each has Config+Extractor+Applier | Canonical |
| **iOS** | **Single merged triplet** for the whole category | `AnimationsConfig/Extractor/Applier.swift` (~1.3k lines total) | Non-canonical |
| **Android** | Category-level applier + keyframe engine (9 kt files) | `AnimationConfig/Extractor/Applier` + `AnimatedModifier` + `KeyframeRegistry` | Non-canonical |

Contract violation (CLAUDE.md "Per-property contract"): iOS and Android do not
ship `{Property}Config/Extractor/Applier` per IR property; they use a coarser
category-level triplet. Phase 12 should either (a) split them, or (b) formally
document animations as an approved exception because the SwiftUI/Compose
animation API surface does not decompose cleanly per-property (see the long
comment block at the top of `AnimationsApplier.swift`).

## Static-state rendering divergences (the actual risk)

All three platforms render the **pre-animation** visual state in screenshots.
That's WAI. But the platforms diverge on which inputs even reach a visible
state:

1. **AnimationName with @keyframes** — No `@keyframes` parser found in
   `src/main/kotlin/app/parsing/css/` (grep returned nothing). The parser
   carries the name only; keyframe bodies are dropped. Consequence:
   - **Web**: emits `animation-name: slide-in` — browser looks up a
     non-existent keyframes rule, renders identity. Visible state = base.
   - **Android** (`AnimatedModifier.kt:56-64`): does **substring keyword
     matching on the animation name** — `contains("fade")`, `contains("spin")`,
     `contains("pulse")`, `contains("slide")`, `contains("bounce")`,
     `contains("shake")`. Any fixture name containing those strings triggers
     a synthetic animation whose first frame (opacity 0, scale 0, translated,
     rotated, …) **diverges** from Web/iOS identity. The current fixture
     `AnimName_Multi` (`fade-in, slide-up, pulse`) will hit this on Android.
   - **iOS** (`AnimationsApplier.swift:37`): identity short-circuit. Matches Web.
   - **Impact**: guaranteed Android vs {Web,iOS} static-snapshot divergence
     for any animation-name whose string contains one of the six hardcoded
     keywords. This is a real bug, not a WAI time-dynamic artifact.

2. **AnimationPlayState: paused** — iOS applier (lines 54-56) freezes
   animations via `.transaction { animation = nil }`. Android `AnimationApplier`
   has no paused-gating; if a fixture also carries `animation-name: fade-*`
   the keyword-match animation runs regardless of `paused`. Web honors CSS
   natively. Divergence surface: Android ignores `paused` entirely for the
   keyword-matched pseudo-animations.

3. **AnimationDelay: negative** — Per CSS spec, a negative delay should jump
   the animation into the middle of its timeline. Web honors this. iOS is
   identity (no animation). Android's keyword animations use
   `rememberInfiniteTransition`/`tween` with `delayMillis = config.getDelay(i)`;
   Compose's `tween` treats negative delays as 0 (not mid-animation jump).
   Static snapshot: all three differ, but WAI given the static constraint.

4. **Cubic-bezier equivalent to linear** — Android maps `cubicBezier(0,0,1,1)`
   correctly via `CubicBezierEasing` (`AnimationApplier.kt:98-105`). iOS/Web
   don't execute; identity. No divergence.

5. **Steps timing function** — Android (`AnimationApplier.kt:90-95`) drops to
   `LinearEasing` with a TODO-style "Steps can't be perfectly mapped" comment.
   Static snapshot unaffected; flag as a dynamic-render gap.

6. **TransitionBehavior: allow-discrete**, **view-transition-name**,
   **ScrollTimeline/ViewTimeline referencing missing scrollers**, **animation
   composition (replace/add/accumulate)** — All three platforms render
   identity pre-transition. No divergence detectable from static snapshots.
   WAI.

## Static test run

Not executed. Rationale: with zero baselines and the dynamic nature of this
category, `./test-all.sh` over these fixtures produces noise rather than
signal. The code audit above is the load-bearing output. The Android keyword-
matching bug (#1) is deterministic and reproducible without a baseline run —
visible in `AnimatedModifier.kt:57-64`.

## Recommendations (for a follow-up implementation PR, out of scope here)

1. **Remove substring keyword matching in `AnimatedModifier.kt`**. Without a
   `@keyframes` parser carrying keyframe bodies into IR, Android should match
   iOS/Web identity behavior for unknown animation-names. The current heuristic
   creates spurious cross-platform diffs.
2. **Either split the iOS/Android category-level triplet into per-property
   triplets, or document animations as a formal exception** to the canonical
   contract in `CLAUDE.md` / `testing/ROLLOUT.md`.
3. **Add `@keyframes` block parsing** to the CSS parser if animations are to
   render dynamically. Otherwise mark AnimationName as "parser-level gap:
   keyframe bodies dropped, rendered as identity on all platforms by design"
   in `testing/README.md`.
4. **Baseline the ten existing fixtures as identity-state baselines** once
   recommendation #1 lands, so future regressions are caught.

## Done-definition status for animations category

| Criterion | Status |
|---|---|
| Fixtures cover parser variants | Mostly — one consolidated `audit-phase12.json` not required; 10 files suffice |
| Triplets on all three platforms | Partial — Web per-property, iOS/Android category-level (contract violation) |
| `test-all.sh` clean with SSIM ≥ 0.95 | Unverified (no baselines) |
| Baseline committed | **No** (0/N) |
| Coverage matrix row in `testing/README.md` | Not flipped |

**Overall: NOT DONE.** One real cross-platform bug (Android keyword heuristic),
one contract violation (iOS/Android not per-property), missing baselines.

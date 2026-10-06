# wave-52 lane L10 · flex-nowrap-gaps — lane note

Tree: `campaign/wave52` (HEAD 7d9c22a7), shared with up to eleven other lanes. Plan: `tools/titan/results/wave52-plan/PLAN.md` §2 L10
(+ §3 registry row `:2213/:2508`, §4 "L2 before L10", §5 `MarginApplier.kt` read-only / `MarginApplier.swift` mine, §9).
Brief: `wave52-plan/runtime-bugs-and-gaps.md` §3/§4 items 1 (7(b)), 3 (M2 iOS), 4 (M2+M3 Compose, GATED), 5 (7(a′)), 6(c).

## 0. Resume record (third start)

The two killed starts left NOTHING for L10: no results dir, `git diff` empty on every owned path, no untracked file under the owned
directories. Built from scratch on this start. L2's note (`wave52-composed-canvas/_note.md`) read first: L2 delivered **no seam
patches** (M1 and T3 went harness-side), so "HEAD with L2's applied first" **is HEAD** for both of my seam files; M1 itself (the
200-px body margin) is in the working tree's harness files, which is what 027's natives need from L2.

## 1. What changed and why

| item | file (owned) | change | spec |
|---|---|---|---|
| **7(b)** iOS percent basis, nowrap | `CSSFlexLayout.swift` (`CSSFlexMath.percentBasis`, `frameExtra`, `outer`; the Layout's per-item input) | `basis = basisPx ?? percent·available/100 ?? ideal`; the percent arm is a CONTENT-box size, so the frame adds the item's border+padding(+positive margins) band and §9.7 runs on OUTER sizes. background-clip-content-box-002: 50%+5 / 50%+6 → outer 111 > 100 → each content shrinks 5.5 → frames 49.5 + 50.5 = the 100-px green square | css-flexbox-1 §9.2 step 3.A, §7.2.3, §9.7; css-box-sizing-3 §3.1 |
| 7(b) claim facts | `ItemPlacement.swift` (`FlexClaim.margins`, `negativeMargins`, `boxExtraWidth/Height`; `FlexOuterFacts`) | concrete-px readers: signed margins; border (style ≠ none/hidden) + padding band, 0 under `box-sizing: border-box` | css-backgrounds-3 §4.3, CSS 2.1 §8.4 |
| **M2** iOS negative margin | `CSSFlexLayout.swift` (`ItemInput.outer`, `mainSizes`, `mainOffsets(outers:)`, hug size) | `outer` (default 0 ⇒ byte-identical arithmetic for every pre-wave-52 input) carries the negative main-axis margins: §9.7 sums and §9.5 advances by OUTER size — 027's slots `[0,−90,−30,30,90,150]`, sum exactly 200 | css-flexbox-1 §9.2 step 3, §9.7 step 1, §9.5 |
| M2 gap anchor (iOS) | `GapDecorationsPainter.swift` (`paintShift`, `gapDecorationItemFrame(index:active:paintShift:)`) | the item anchor follows the PAINTED box (MarginApplier's `.offset`) via a clear background moved by the same shift; zero shift = the wave-24 modifier verbatim | §9.5 outer sizes; css-gaps-1 gap = between margin boxes |
| **7(a′)** snap (iOS) | `GapDecorationsPainter.swift` (`snapped`) | every rule rect snapped edge-wise `floor(v+0.5)` before paint (Chromium `PixelSnappedIntRect`); integral rects unchanged | — (Blink paint snap) |
| **M2+M3** Compose, GATED | `FlexNowrapLine.kt` (new, 198 lines after the fix pass) | `engages` (the gate), `outerDeltaPx`, `paintShiftPx`, pure `mainOffsets`, composable `Line` (children measured main-axis-unbounded, placed by outer size, never clamped). Gate admits ONLY an **LTR** (fix pass: `rtl` refuses — §10), non-reversed nowrap row/column with a px main size, start packing, start/stretch cross alignment, every child an in-flow px-W×px-H box with no grow/basis/align-self/order and px-or-absent margins, AND (a negative main-axis margin with the line fitting or every shrink 0) OR (overflow with every shrink factor 0) | css-flexbox-1 §9.7 (shrink 0 ⇒ hypothetical), §9.5, §5.1; css-overflow-3 §2; css-writing-modes-4 §2.1 |
| M2 gap anchor (Compose) | `GapDecorationHook.kt` (`GapItemSink.paintShiftsDp`, `placedItems(density)`) | the probe (outside MarginApplier's `Modifier.offset`) reports the SLOT; the sink moves it by the item's paint shift (×density). Map empty for every container without a negative-margin item | as iOS |
| **7(a′)** Compose | `GapDecorationBands.kt` | §9.4 step-8 share is the SwiftUI twin's fractional `leftover/n`, no longer the layout's Int 57/57/56 split (046's second rule one row low; natives disagreed) | css-flexbox-1 §9.4 step 8 |
| 7(a′) Compose | `GapDecorationPainter.kt` (`snapped`) | same snap as iOS, applied to every segment rect in `paintOne` | — |
| **6(c)** | `fixtures/properties/effects/box-shadow-reach.json` (new) | the uncommitted 2026-08-28 reach probe RE-AUTHORED: 4 × 100×40 boxes, `box-shadow: 0 10px 10px {−8,0,+8,−1}px`; v1 `_expect` pins fill + box (knockout keeps the box); the bottom-most shadow row (spec: σ = 5, last 8-bit row = edge + 14 ⇒ component rows 56/64/72/63) is recorded in each `_expect.note` for the v2 position oracle — v1 (`spec-oracle.mjs`) cannot assert a row position | css-backgrounds-3 §6.1.1 |

`MarginApplier.swift` (owned) is **unchanged** — the design keeps its `.offset` paint pull and moves the anchor instead.
Not touched (as planned): `MarginApplier.kt` (L4), `FixedHoist.swift` (L3), the canvases (L2), 006 (declined), 6(e)/6(x²).

## 2. Seam patches (both cut against HEAD 7d9c22a7 = HEAD + L2's patches, L2 delivered none)

- **`seam-1.patch` — `runtimes/swiftui/…/Renderer/ComponentRenderer.swift`** (NEW registry row; the plan listed Compose seams only). Hunks:
  (a) `flexMainPlan` (~`:2446-2500`): the percent arm (`basisPx ?? pct ?? explicit`), `outer:` on every item, the border-box band
  added back to the percent arm's injected size (the `flexStretchWidth` fold treats the injected value as a border-box FRAME,
  `declaredFromFrame`), and no harness 50-px floor for the percent arm in WPT capture (the paint chain has none there; the px/explicit
  arms keep it byte-identically). Without (a) the iOS child ignores the Layout's proposal and paints its intrinsic 0 — the raster
  reproduces the device defect EXACTLY (8 900 red px = the capture's novel-red count). (b) the child loop's
  `.gapDecorationItemFrame(index:active:)` → the `paintShift:` overload (zero shift ⇒ the old modifier).
- **`seam-2.patch` — `runtimes/compose/…/core/renderer/ComponentRenderer.kt`**: engine `FlexContainerKind.Row` (~`:2213`) and `.Column`
  branches call `RenderNowrapLine(...)` first (returns false, emitting nothing, unless the gate admits); new private composable
  `RenderNowrapLine` (one `Box` per child so measurables stay 1:1 with the outer deltas; `LocalSelfAlignmentHandled provides true` like
  `RenderRowContent`). **Deviation:** the legacy `DisplayType.FLEX_ROW` route (~`:2508`) is NOT patched — both gate carriers take the engine route.
  **Fix pass (§10): seam-2 RE-CUT** — `RenderNowrapLine` now passes `rtl = LocalLayoutDirection.current == LayoutDirection.Rtl` to
  the five-argument `engages`. The pre-fix-pass version is archived as `logs/seam-2.pre-fix-pass.patch.txt` (do NOT apply it: it calls
  the old four-argument `engages` and no longer compiles against `FlexNowrapLine.kt`).
- **Verification (lock protocol, both files):** `mkdir tools/titan/runs/wave52-lock/<basename>` → sha256 → `git apply` → focused tests →
  `git show HEAD:<path> > <path>` → sha256 equal → `rmdir`. Swift: before = after = `d2afc70d…c4ee63`; with seam-1 applied, 74/74 green
  (`logs/swift-seam-green.summary.log`: FlexNowrapOuter/Raster + GapDecorationHook/Raster/Segments/Bands + FidelityWave2 +
  EmptyFlexContainer + FlexWrapStretch + FlowLayoutDistribution). Compose: before = after = `c4369165…03e652`; with seam-2 applied
  `compileDebugKotlin` ran and `layout.flexbox.*` + `columns.*` + `SeamReachabilityTest` + `BlockBoxAlignItemsTest` green
  (`logs/kotlin-final-seam.log`, on the FINAL shas). Seam files left clean (`git diff --quiet`) after every session.

## 3. Census (`flex-nowrap-gaps.census.mjs` → `flex-nowrap-gaps.census.json`, all 1435 wave51-fix per-test IR docs)

- **7(b)**: percent-basis children of a flex parent — **4 tests**; the iOS arm resolves for exactly **1** (background-clip-content-box-002,
  2 items); untouched **3**: flexbox-abspos-child-002 (abspos ⇒ not in `inFlowChildren`), flex-gap-decorations-025 and
  calc-size-flex-007 (wrap ⇒ FlowLayout). = the brief's 4.
- **M2** (iOS, ungated): in-flow child of a nowrap flex parent with a negative main-axis px margin — **2 tests**: 027 and
  position-absolute-dynamic-relayout-003 (raster-guarded: explicit widths floor §9.7 at 100 and the delta is the LAST item's ⇒ unchanged).
- **Compose gate** (JS port of `FlexNowrapLine.engages`, line by line): **2 containers / 2 tests — 008 and 027**, nothing else (≤ the 2
  carriers the plan allows). 003 is refused (`order` + an abspos child).
- **Gap-anchor shift**: decorated containers with a negative-margin child — **1** (027).
- **Decorated flex containers** (snap / fractional-band surface): **46 tests** (all css-gaps).
- **Pre-existing, NOT changed**: px basis on an item with no main size inside a planned nowrap container (the WPT-mode 50-px floor
  the static plan still applies) — **0** carriers.
- **Fix pass (RTL, §10)**: `R` RTL flex containers (self or an ancestor declares `direction: rtl` ⇔ Compose `LocalLayoutDirection`
  Rtl) — **14 containers / 7 tests** (= the skeptic's count: abspos-autopos-{htb,vlr,vrl}-rtl, flex-align-baseline-column-rtl-direction,
  …-vert-{lr,rl}-rtl-wrap-reverse, direction-upright-002); `G_rtlRefused` (RTL containers the pre-fix gate admitted) — **0**;
  `S_rtl` (decorated containers whose negative-margin child gets a MIRRORED paint shift) — **0**. `G` stays exactly {008, 027}; every
  pre-existing class is row-for-row identical to the pre-fix census (`logs/census.pre-fix-pass.json`; diff check in `logs/fixpass-census.log`).

## 4. PNG replay (`png-replay.mjs` → `png-replay.json`; ssim.js `fast`, same call as inject-wpt-block)

- 002 ios ← the Android capture (green 16–115, measured): **0.9984**, red 8 900 → **0**.
- 008 android ← the iOS capture: 0.9646 → **0.9996**.
- 027 ios/android ← web +200 (L2 M1) + L2 Fix A frame: **0.9723** with the band web painted off-canvas (x 68–199: "One", rule 1,
  "Two", rule 2) left blank (LOWER bound); **1.0000** with that band from the ref (UPPER bound; native glyph AA not modelled).
- 045 ios 0.9931 → **1.0000**, android 0.9994 → **1.0000**; 046 ios 0.9998 → **0.9999**, android 0.9960 → **0.9976** (Android's
  item rows stay Int-placed by the wrap layout; only the rule moves).
- `snapMovers` (web-oracle upper bound over the 46 decorated tests, both natives): rises ≤ 0.002 on 014/029/030/043/047/049/050;
  036/037 −0.0004 (stay P at 0.956), 034 −0.0003 (f, instrument e¹); "structural" rows (006, 008, 017/018, 027, 033, 035, 041, 050)
  are other defects or style-AA differences, not the snap. New watch lines: `watchlist-additions.txt` (14, `unmatched 0`).

## 5. Pins and executed mutations (`mutate.py` → `mutations.log`, logs per id in `logs/`)

`mutate.py` replaces exactly one site, runs the focused suite, restores from an in-memory copy and checks sha256 before == after;
RED only when the NAMED test fails by an assertion (compile errors / runner crashes = NOPROOF). Last line per id is authoritative.
- SwiftUI (Catalyst, `-derivedDataPath` private): `FlexNowrapOuterTests` — SW-B1 (percentBasis → nil), SW-B2 (boxExtra → 0), SW-M1
  (offsets ignore outers), SW-M2 (margins → 0), SW-S1 (snap identity); `FlexNowrapRasterTests` (seam-1 applied, under the lock) —
  SW-R1 (seam percent arm removed), SW-R2 (painted-anchor overload off), SW-R3 (Layout `outers: []`), SW-G1 (MarginApplier pull → 0,
  breaks the 003 guard). All **RED**, all restored ok.
- Compose (JVM JUnit4): `FlexNowrapLineTest` — KT-L1 (Row-like clamp), KT-L2 (outer delta ignored), KT-G1 (shrink-0 requirement
  dropped), KT-G2 (trigger dropped); `GapNowrapShiftAndSnapTest` — KT-H1 (shift ignored), KT-S1 (snap identity), KT-B1 (Int-like
  share). All **RED**, all restored ok. `GapDecorationBandsTest.kt` (the existing pins of my `GapDecorationBands.kt`) updated for
  the fractional share + snap: 11/11 green.
- Fix pass (RTL, §10): `FlexNowrapLineTest` — KT-D1 (gate drops `if (rtl) return false` → `an RTL line is refused…` :120), KT-D2
  (`paintShiftPx` ignores RTL → `the paint shift mirrors…` :128), KT-D3 (ignores the item's own `Direction` → same test :131), KT-D4
  (mirrors with `-x`, a −0f → same test :133); `GapNowrapShiftAndSnapTest` — KT-H2 (`paintShiftsFor` drops its `rtl` → `paint shifts
  follow the container direction…` :72). All **RED** by AssertionError at the line named, all restored ok (sha a4d6d16b… / 6212059b…).
- Payloads are VERBATIM wave51-fix per-test IR: background-clip-content-box-002, flex-gap-decorations-008 / -027,
  position-absolute-dynamic-relayout-003 (embedded — `tools/titan/runs/` is gitignored).

## 6. Predicted flips (wave51-fix → wave52 gate)

- **+1 HIGH** `css-backgrounds/background-clip-content-box-002 ios` f 0.9992 cF → P (raster: 0 red, ≥ 9 800 green, no overflow;
  replay 0.9984). Needs seam-1. Falsifier: any red at the gate ⇒ the injected frame is not what the device paints.
- **+1 MED-HIGH** `css-gaps/flex/flex-gap-decorations-027 ios` f 0.9030 → P (needs L2 M1 + seam-1; replay 0.9723…1.0000; raster
  pins the five rules and the outer-size slots). Falsifier: rule at page x 118 missing ⇒ the anchor does not resolve through
  `.offset` on device (it does under ImageRenderer).
- **+1 MED** `… 027 android` f 0.9085 → P (needs L2 M1 + seam-2 + the Hook shift). The composable never executed (no Compose UI test
  runtime on the JVM). Falsifier: Four/Five/Six still at 183/193/193 ⇒ the gate did not admit on device.
- No flip, rises: `008 android` P 0.9646 → ≈ 0.9996; `045 ios` 0.9931 → ≈ 1.0; `046 android` 0.9960 → ≈ 0.9976; `045 android`,
  `046 ios` ≈ +0.0001–0.0006.
- **Predicted P → f: 0.** At risk (all predicted to hold): flexbox-abspos-child-002 ×3 (untouched by construction),
  position-absolute-dynamic-relayout-003 ×3 (raster-guarded), 025 / calc-size-flex-007 (wrap path untouched), every other css-gaps
  cell (snap identity on integral rects; `snapMovers` above; gate `--movers 0.005`).

## 7. Not verified (honest)

- **No device render.** Compose `FlexNowrapLine.Line` was compiled (seam applied) but never executed; only its pure placement and
  gate are pinned. The Hook's shift assumes the probe reports the pre-offset slot (evidence: Android 027's rules at 68–77 sit between
  slots, not painted boxes).
- The iOS painted-anchor overlay was proven under ImageRenderer on Mac Catalyst, not on an iOS simulator/device capture.
- Kotlin snapping is in device px; the capture density is inferred as 1 from 046's integer Android rows, not read from the device.
- The legacy Compose `FLEX_ROW` route is unpatched (0 gate carriers reach it — inferred from the engine decision, not traced on device).
- The box-shadow-reach fixture converts (5 props, 0 generic) and its 4 `_expect` blocks parse; it has never been rendered, has no
  baselines, and is not in the gate set. The reach rows are spec-derived, not measured.
- `CSSFlexLayout.swift` is 414 lines (343 at HEAD — already past the ~300 split threshold); a split needs a new file outside the own list.
- Percent-arm box extras apply only to percent claims; px-basis items keep the pre-existing frame-unit approximation (no census
  of bordered px-basis items was run).
- Fix pass: `Line`'s `placeRelative` and the two `LocalLayoutDirection.current` reads (seam-2's `RenderNowrapLine`, the hook's
  `rememberGapDecorationSink`) are compile-verified only — no Compose UI test runtime on the JVM. The RTL guarantee rests on the
  pure, pinned `engages(…, rtl)` refusal and `paintShiftsFor(…, rtl)`; that `LocalLayoutDirection` is Rtl inside an RTL container's
  `RenderComponentContent` is read from source (ComponentRenderer.kt:1799-1810 → :2116-2121 provider), not traced on device.
- The skeptic's should-fix items #2 (iOS bare-number percent margin/padding read as px, 0 carriers), #3 (`Line` measure body unpinned)
  and nits #5–#10 are NOT addressed in this fix pass (not must-fix; disclosed). #4 is addressed in comments only (§10).
- iOS RTL was not re-examined in the fix pass (the skeptic flagged Compose only).

## 8. Hand-offs

- Received: L2's M1 (harness-side; no seam patches) — both L10 seam patches are cut against HEAD.
- Delivered: `seam-1.patch` (iOS, NEW §3 row), `seam-2.patch` (Compose `:2213` Row + Column; RE-CUT in the fix pass — take the
  current file, sha e2c0dbea…). Independent of every other lane's seam hunk (different functions); apply after L2 (nothing) in any
  order relative to L3/L7/L11/L4/L6/L8/L9. Fix pass: the re-cut seam-2 applies cleanly before AND after each of the 10 other lanes'
  Compose `ComponentRenderer.kt` patches (pairwise, scratch copy of HEAD).
- `GapDecorationBandsTest.kt` (existing test of my owned `GapDecorationBands.kt`, unowned in §2) was edited for the new share — flagged.
- Orchestrator: watch lines in `watchlist-additions.txt`; the sweep must include the three Swift test files and the two Compose test files.
- BACKLOG paragraph: "css-gaps residuals (wave 52 L10): 7(b) the iOS nowrap path resolves a percent flex-basis (content-box,
  outer-size §9.7) — background-clip-content-box-002; M2 both natives advance by OUTER size for negative main-axis margins (iOS
  CSSFlexLayout ungated; Compose via the GATED FlexNowrapLine, 2 carriers: 008/027) and the gap anchors follow the painted box;
  M3 Compose overflowing shrink-0 lines no longer squeeze (same gate); 7(a′) rule rects are pixel-snapped on both natives and the
  Kotlin band share is fractional (045/046). 6(c): fixtures/properties/effects/box-shadow-reach.json committed — the reach rows
  still need a device read. Open: 006 (declined), 6(e), 6(x²), the legacy Compose FLEX_ROW route."

## 9. Verification commands (final sources)

- SwiftUI: `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64' -only-testing:StyleConverterRuntimeTests/FlexNowrapOuterTests -only-testing:StyleConverterRuntimeTests/FlexNowrapRasterTests` (rasters need seam-1).
- Compose: `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests 'com.styleconverter.runtime.layout.flexbox.*' --tests 'com.styleconverter.runtime.columns.*')`.
- Census / replay: `node tools/titan/results/wave52-flex-nowrap-gaps/flex-nowrap-gaps.census.mjs`, `node …/png-replay.mjs`.
- Final Swift lock session (seam-1 applied, `ComponentRenderer.swift` sha `d2afc70d…` before = after, lock released): the
  74-test set green, then SW-G1 / SW-R1 / SW-R2 / SW-R3 re-run on the split `FlexNowrapRasterTests` class — all RED, restored ok.

## 10. Fix pass (skeptic `skeptic.md`, one must-fix) — 2026-10-05 ~19:30 CEST

- **Must-fix (Compose RTL)** — `FlexNowrapLine.Line` placed with `place` and `engages` read no direction, so an admitted RTL line
  would draw from the left edge where `Row` mirrors it to the right (css-flexbox-1 §5.1: a row's main-start is inline-start, the
  RIGHT edge under css-writing-modes-4 §2.1 `direction: rtl`). Fixed by REFUSAL plus mirroring:
  - `engages(container, children, rowAxis, hasText, rtl)` — `rtl` is a required argument (no default, so no caller can forget it);
    `if (rtl) return false` keeps every RTL row/column on `Row`/`Column` exactly as at HEAD (MarginApplier's `.offset` pull does not
    implement CSS RTL margins either, so a mirrored `Line` alone would still not be right).
  - seam-2 passes `LocalLayoutDirection.current == LayoutDirection.Rtl` — that local is provided by `RenderComponent` for an RTL
    component and every descendant, so own AND inherited `direction` are seen (the skeptic's "cannot see an inherited one").
  - `Line` places with `placeRelative` (equals `place` for every admitted, i.e. LTR, line; mirrors should the refusal be lifted).
  - The gap-anchor paint shift I added this wave had the same blind spot: the Dp `Modifier.offset` MarginApplier paints with is
    RTL-aware, so under Rtl the painted box moves by −negX. `paintShiftPx(props, inheritedRtl)` mirrors x when the container is Rtl or
    the item declares its own `direction: rtl` (`0f - x`, never a −0f); the hook reads `LocalLayoutDirection` (also a `remember`
    key) and builds its map through the new pure `paintShiftsFor(children, rtl)`.
- **Gate-neutral by census**: `G` = {008, 027} unchanged, `G_rtlRefused` 0, `S_rtl` 0 (§3). No flip moves; no new watch lines
  (the 7 RTL tests are refused before AND after — nothing about them changes).
- **Pins**: 2 new tests in `FlexNowrapLineTest` (verbatim 008/027 IR, admitted with `rtl = false`, refused with `rtl = true`; the
  mirrored shift for inherited Rtl, own `Direction RTL`, and +0 for a margin-less item), 1 in `GapNowrapShiftAndSnapTest`
  (`paintShiftsFor` LTR −150 / Rtl +150 on 027's verbatim `{"px":-150}` leaf). Mutations KT-D1…D4, KT-H2 all RED (§5).
- **Runs**: no seam — `layout.flexbox.*` + `columns.*` 413 tests / 0 failures, compileDebugKotlin executed
  (`logs/fixpass-kotlin-final-noseam.log`, XML in `logs/fixpass-noseam-xml/`). **Seam-2 under the lock** — `mkdir
  tools/titan/runs/wave52-lock/ComponentRenderer.kt` 19:35:14 → sha `c4369165…03e652` → `git apply seam-2.patch` → compileDebugKotlin
  executed, `layout.flexbox.*` + `columns.*` + `*SeamReachabilityTest` + `*BlockBoxAlignItemsTest` 418 tests / 0 failures
  (`logs/fixpass-kotlin-seam.log`, `logs/fixpass-seam-xml/`) → `git show HEAD:<path> > <path>` → sha equal, `git diff --quiet` clean →
  `rmdir` 19:35:18. seam-1 (Swift) unchanged by the fix pass, so its earlier verification stands.
- **Should-fix #4 (comments only)**: `GapDecorationsPainter.swift` / `GapDecorationPainter.kt` no longer claim "integral rects — every
  other css-gaps test — cannot move": half-pixel-centred rules (024/029/030/034–037/050) move ≤ 0.5 px (skeptic any-hue replay ≤ 0.0014,
  no P→f). Attribution: 024/034/035/036/037 are already in `wave52-plan/watchlist.txt` (other lanes' lines), 029/030/050 in my
  `watchlist-additions.txt`; I add no duplicate lines — the orchestrator should read those five as ALSO moved by L10's snap
  (≤ 0.0014). Swift edit is `///` only (`swiftc -parse` OK); Kotlin recompiled in the runs above.
- Final shas: `head-shas.txt` "post-fix-pass" lines (FlexNowrapLine.kt a4d6d16b…, GapDecorationHook.kt 6212059b…, seam-2 e2c0dbea…).
STATUS: COMPLETE

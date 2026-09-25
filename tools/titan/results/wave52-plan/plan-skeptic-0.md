# plan-skeptic-0 — pointer-resolution-and-evidence audit of PLAN.md (wave 52)

Lens: does every cell PLAN.md and its briefs cite resolve against the wave51-fix manifests (score, verdict, platform);
does at least one PNG per lane show the defect the plan describes; does every file:line say what is claimed.
Nothing was built or run. Every number below was re-derived with my own script
(`manifest.wpt.results[<test>].browserRef.diffs['<platform>-ref']` over the 30 `tools/titan/runs/wave51-fix/sections/*/manifest.json`),
not read from `gate-cells-wave51-fix.json`.

**Verdict: NOT refuted.** All 12 lanes' targets resolve to real wave51-fix cells with the verdict and score PLAN.md states;
the 23 PNG pairs I opened show the described defects; every file:line cited exists and says what is claimed
(one pointer drifts by 8 lines, mechanism intact). Corrections are arithmetic/wording, listed in §8.

## 1. Run of record — re-derived

| platform | pass / scored (mine) | PLAN.md | eligible docs | total docs |
|---|---|---|---|---|
| web | 1215 / 1379 | 1215/1379 | 1379 | 1435 |
| ios | 1089 / 1369 | 1089/1369 | | |
| android | 1080 / 1369 | 1080/1369 | | |

Headline arithmetic checked: 1220/1372 = 88.92 %, 1103/1363 = 80.92 %, 1093/1363 = 80.19 %; L12-A 1208/1372 = 88.05 %, 1083/1363 = 79.46 %, 1074/1363 = 78.80 % — all as printed.

## 2. Cell resolution — every target and at-risk cell named in PLAN.md §1/§2

Format: `wave51-fix <section>/<test> <platform> <P|f> <ssim>` as the manifest reads it; flags cF/nI/pF = colorFailed/novelInkFailed/presenceFailed.
"=" means PLAN.md states exactly this. 27 formal `wave51-fix …` citations in PLAN.md + 22 in the briefs (failure-ink 10, web-tail 9, harness 2, absence-only 1)
were machine-checked: 48/48 match within rounding; the one non-match is the range shorthand `abspos-auto-sizing-fit-content-percentage-001..004`, resolved by hand below.

### L1 web-tail-colour-vt
- css-color/display-p3-linear-001 web f 0.8775 cF · ios f 0.8764 · android f 0.8756 =; -002 web f 0.7977 =; -003 web f 0.9484 =; -004/-005/-006 ×3 P 0.9522–0.9568 (plan "≈0.953") =
- css-view-transitions/fractional-box-with-shadow-{new,old} web f 0.9763 cF (ios 0.9749 / android 0.9739) =; -with-overflow-children-{new,old} web f 0.9757 cF pF (ios 0.9757 / android 0.9753) =
- css-view-transitions/column-span-during-transition-doesnt-skip ×3 P 0.9879 =

### L2 composed-canvas
- css-masking/clip-path/clip-path-ellipse-006/007/008 ios f 0.9488 · android f 0.948 =; css-text-decor/text-decoration-propagation-shadow ios f 0.9484 · android f 0.9486 =
- css-flexbox/align-items-007 ios f 0.9974 cF nI · android f 0.9966 cF nI =; CSS2/css21-errata/s-11-1-1b-006 ios f 0.9321 · android f 0.9332 =; -005 web P 0.9947 · ios f 0.9538 pF · android f 0.9309 =
- css-gaps/flex/flex-gap-decorations-027 web f 0.9012 · ios f 0.903 · android f 0.9085 =; -040 web P 0.9939 · ios/android P 0.9934 =
- css-position/position-absolute-semi-replaced-stretch-input web f 0.9422 =; gaps-034 web f 0.9458 · ios/android f 0.9466 (plan "0.946") =
- css-contain/contain-inline-size-bfc-floats-001 ios f 0.9406 · android f 0.9394 (plan "0.940") =; css-anchor-position/anchor-position-multicol-007 android f 0.9499 =
- filter-effects/backdrop-filter-basic-blur ios f 0.9469 =; at-risk: css-break/borders-002 android P 0.9594 =, selectors/has-style-sharing-002 ios P 0.9633 =, anchor-center-safe android P 0.9522 =,
  css-lists/counter-list-item-slot-order web P 0.9509 =, text-decoration-skip-spaces-001 web P 0.9524 =, text-decoration-inset-009 ios P 0.9585, text-decoration-line-recalc ios P 0.9557,
  discard-multicol-003 android P 0.953, gaps-036/037 ×3 P 0.9557–0.9576 =, gradient-hue-direction ios P 0.997 · android P 0.9973 =

### L3 failure-ink
- css-display/display-contents-float-001 ios f 0.9417 nI · android f 0.941 nI =; css-writing-modes/flexbox_align-items-stretch-writing-modes ios f 0.999 cF nI · android P 0.998 nI =
- css-multicol/abspos-containing-block-outside-spanner ios f 0.9553 cF nI · android P 0.999 =
- at-risk: anchor-position-multicol-008/013/014/015/016/017 natives P 0.9511–0.9871, -colspan-003 P 0.9768/0.9919 (plan "0.951–0.992") =; display-contents-oof-001/002 natives P 0.9843/0.9837;
  td-different-subpixel-padding-in-same-row-vertical-rl ios P 0.9638 · android P 0.9609 =; flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse ios P 0.9721; float-vlr-014 ios P 0.9974; abs-pos-border-offset-003 ios f 0.9342 · android f 0.9262 (plan "both f") =

### L4 small-fixes
- css-transforms/composited-under-rotateY-180deg-preserve-3d ios f 0.9565 pF · android f 0.9565 pF =; css-transforms/css-transform-inherit-scale android f 0.9965 cF nI · ios P 0.9974 =
- css-sizing/abspos-auto-sizing-fit-content-percentage-001..004 android P 0.9984 (all four; -005..-008 identical) · web P 1 · ios P 0.9992 =
- at-risk: backface-visibility-hidden-animated-001/002 ios P 0.9646 (plan "0.9647") =; css-transform-3d-transform-style android P 0.9577 =; backdrop-filter-3d-transform-perspective android P 0.9868 =;
  "-nested-" = filter-effects/backdrop-filter-nested-3d-transform-perspective android P 0.9749 =; position-relative-002/-008 android P 0.9967/0.9984
- NOTE: backface-visibility-hidden-003/004 android are f today (0.9845 cF nI / 0.998 cF nI), not P — see §8.

### L5 extractor-cascade
- css-cascade/import-conditional-001 and -002 web f 0.999 cF nI · ios f 0.9974 · android f 0.9966 =; css-color/color-mix-percents-02 web f 0.9514 cF · ios f 0.9503 · android f 0.9496 =
- css-gaps/flex/flex-gap-decorations-024 web P 0.9901 nI · ios/android P 0.99 nI =; anchors css-values/angle-units-001 ×3 P and revert-layer-005 ×3 P =; revert-layer-010 P 0.9716/0.9699/0.9692 =;
  has-style-sharing-003 ×3 P 1.0; first-letter-background-image(-dynamic) ×3 P 0.9978/0.9977/0.9976

### L6 counters-and-lists
- css-counter-styles/armenian/css3-counter-styles-008 web f 0.9435 (ios f 0.942 · android f 0.9423, NOT nfp-excluded — 007 is) =
- css-counter-styles/cssom/cssom-pad-setter-invalid ×3 f 0.9821/0.9819/0.982 =; cssom-prefix-suffix-setter-invalid ×3 f 0.9844/0.9848/0.9849 =; cssom-negative-setter-invalid ios f 0.9849 · android f 0.9848 (web P 0.984) =
- css-counter-styles/counter-suffix ios f 0.9285 · android f 0.9051 (web P 0.9818) =; CSS2/floats-clear/floats-clear-multicol-003 android P 0.9881 =; -balancing-003 android P 0.9857 =
- at-risk: armenian-006/007/009 web P 0.995/0.9829/0.9971 =; counter-reset-reversed-nested ios/android P 0.9551/0.9509 =; change-list-style-type-002 P 0.9547/0.958 =; all-prop-001 P 0.964/0.9641 =;
  appearance-auto-details-list-item P 0.9676/0.9679 =; scope-pseudo-element android P 0.9746 =; descriptor-suffix ×3 P 0.9533/0.9596/0.9593 =; marker-text-matches-circle/disc/square ≥ 0.9894 (plan "≥0.989") =; display-contents-dynamic-generated-content-fieldset-001 android P 0.9981 =

### L7 static-position
- T1: 16 Android cells `css-grid/abspos/grid-abspos-staticpos-align-{self,items}-*` P 0.9698–0.9856 with web AND ios = 1.0000 — my census returns exactly 16, same range =; align-self-center android P 0.9808 =, align-self-end P 0.9704 =, align-items-self-end P 0.9715 =
- T2: css-flexbox/abspos/position-absolute-containing-block-002 ios f 0.9373 =; -001 ios P 0.9505 =; flex-abspos-staticpos-justify-self-001 ios P 0.9921 =; margin-001 ios P 0.9909
- T3: 12 web `*-last-baseline-*` cells P 0.9954–0.9983 — census returns exactly 12 (img-001/002, self-001/002, rtl-001..004, vertWM-001..004) =
- at-risk: flex-abspos-staticpos-align-self-safe-001/002/003 ios P 0.9937/0.9914/0.9981 =; abspos-autopos-* ios P 0.9974/0.9904 =; position-absolute-006..010 ios P 0.9974 (010 checked) =;
  position-absolute-center-003/004 ios P 0.9973 =; grid-abspos-staticpos-align-self-safe-001 ios f 0.9448 · android f 0.9441 (plan "0.944") =

### L8 vertical-wedges
- css-writing-modes/forms/input-range-zero-inline-size ios f 0.9747 · android f 0.9743 (web P 0.9964) =; ch-units-vrl-005..008 ios f 0.9159 cF · android f 0.9103 =; -001..004 ios f 0.8396 cF · android f 0.8404 cF =; web 005/006 P 0.9578 =
- at-risk (10 cells): hyphens-auto-001 ios/android P 0.995/0.991; hyphens-none-shy-on-2nd-line-001 P 0.9988/0.998; hyphens-out-of-flow-002 ios P 0.9786; hyphens-punctuation-001 ios P 0.9556; hyphens-span-002 P 0.9786/0.9602;
  text-decoration-inset-004 P 0.9634/0.9859 — 2+2+1+1+2+2 = 10 =; border-collapse-dynamic-col-001 android P 0.9812 =; ch-unit-001 ios f 0.8294 · android f 0.8362 (plan "0.83") =

### L9 inline-run-wall
- css-text/hanging-punctuation/hanging-punctuation-inline-001 android f 0.9495 · ios P 0.9756 =; css-overflow/line-clamp/block-ellipsis-032.tentative android f 0.9397 · ios P 0.9577 =; block-ellipsis-025 ios f 0.9495 · android P 0.9607 =
- css-text/hyphens/hyphens-manual-inline-012 android f 0.9419 =; at-risk: block-ellipsis-031 android P 0.9779, 004/005/006 android P 0.98/0.9789/0.9789 =; discard-multicol-001/002/004 ios P 0.9613/0.9606/0.9613 =;
  hyphens-manual-011/012/013 android P 0.9811/0.9811/0.9822, hyphens-manual-inline-011 android P 0.9739 =, block-ellipsis-014/028 android P 0.973/0.9847

### L10 flex-nowrap-gaps
- css-backgrounds/background-clip-content-box-002 ios f 0.9992 cF nI (android P 0.9984) =; gaps-027 ios f 0.903 · android f 0.9085 =; gaps-008 android P 0.9646 · ios P 0.9996 =; gaps-045 ios P 0.9931 =; gaps-046 ios P 0.9998 · android P 0.996 =
- at-risk: flexbox-abspos-child-002 ios P 1.0 (android f 0.9847) =; position-absolute-dynamic-relayout-003 ×3 P =; gaps-025 ×3 P 1.0 =; calc-size-flex-007 ×3 P =

### L11 all-reset-postload-colour
- css-cascade/all-prop-initial-color android P 0.9993 nI · ios P 0.9989 nI · web P 0.9783 nI =; all-prop-{inherit,revert,unset}-color natives P 0.9989/0.9993 =
- selectors/invalidation/nth-child-of-class android P 0.9965 · ios P 0.9976 · web P 0.999 =; nth-child-of-has ios f 0.9371 =; all-prop-001 web P 0.9689 · ios P 0.964 · android P 0.9641 =
- at-risk: display-contents-button/details/fieldset ×3 P 0.968–0.9765 (plan "0.968–0.977") =; all-prop-002 ios/android P 0.9709/0.9702 =; currentcolor-004 ×3 P; has-visited ios f 0.9469 (not P); any-link-attribute-removal ×3 P

### L12 instrument-and-calibration
- Absence-only re-derived (wptPass ∧ bCoveragePct < 0.02): **19 cells / 7 tests — web 7 / ios 6 / android 6** =; the 7 tests are the 7 the watchlist lists (L12-A block); background-color-animation-in-body ×3 P 1.0 has cov > 0 and is correctly NOT in the 19 =
- Blank-ref FAILS stay scored: initial-background-color ×3 f 0.5414 cF nI =, calc-in-media-queries-001/002 ×3 f 0.5535 =, animation-name-ua-prefix ios/android f
- paddingBox-1d ×3 P 0.9585 =; paddingBox-1e P 0.9697/0.9696/0.9696 =; contentBox-1d ×3 P 0.9585 =; contentBox-1e P 0.9696/0.9695/0.9695 =; css-tags-paint-order(-with-entry) ×3 P 0.9791 nI =;
  gaps-033 web f 0.94 · natives P 1.0; gaps-035 ×3 f 0.9206/0.922/0.922; hanging-punctuation-block-bound-001 ×3 f; anchor-position-circular / absolute-pos-box-inside-fixed… / hypothetical-dynamic-change-003 ×3 P 1.0 =

Brief-table cells (shorthand my regex did not catch) spot-checked and matching: static-position rows 045/036/000/020/076/032; native-near-misses T8 attr-style-sharing-1/-3, T9 gradient-*-hue-lch, T10 fixup-dynamic-anonymous-*,
T11 position-absolute-center-007; runtime-bugs 006 (0.8266/0.8223), background-attachment-fixed-inside-transform-1 android P 0.9629; counters T4 floats-clear-multicol-000/001 + balancing-000/001, B counter-reset-reversed-list-item(-start) web f 0.9369/0.9377,
C override/access/shadow-dom-part; colour first-letter-skip-empty-span; page-padding block-ellipsis-001/028; compose-cb-level 3/3′ rows. No mismatches found.

## 3. PNGs opened (capture vs frozen ref) — one or more per lane

| lane | cell | what the capture shows | what the ref shows | plan's description |
|---|---|---|---|---|
| L1 | display-p3-linear-001 web | caption only, NO square | green 192×192 square at (16,88) | "nothing painted" — confirmed |
| L2 | clip-path-ellipse-006 ios | green ellipse top edge y≈100 | ellipse top edge y≈118 | "16 px too HIGH" — confirmed (~16–18 px) |
| L2 | text-decoration-propagation-shadow ios | underlined line at y≈58 | line at y≈42 | "16 px too LOW" — confirmed |
| L2 | align-items-007 ios | RED 100×100 at (16,88) | GREEN 100×100 at (16,88) | "red img over the abspos green" — confirmed |
| L2 | s-11-1-1b-006 ios | prose at y≈26, black square y≈60 | prose y≈42, square directly under | "prose 16 px high" — confirmed |
| L2 | flex-gap-decorations-027 web | row starts off-canvas ("hree" at x 0), ends x 217 | "One" at x≈68, row to x≈373 | "whole page 200 px left" — confirmed |
| L3 | display-contents-float-001 ios | full-width RED bar behind "PASS" | "PASS" on white | confirmed |
| L3 | flexbox_align-items-stretch-writing-modes ios | green 150×100 + RED 100×100 at x 166–265 | green 250×100 | confirmed |
| L4 | composited-under-rotateY-180deg-preserve-3d ios | BLANK white canvas | green 100×100 at (16,16) | confirmed |
| L5 | import-conditional-002 web | RED 100×100 | GREEN 100×100 | "RED for GREEN" — confirmed |
| L5 | color-mix-percents-02 web | purple block 224×160 (rows t6–t7 missing) | purple 224×224 | "rows t6–t7 white" — confirmed |
| L6 | counter-suffix ios | LTR rows' markers start at x≈64; RTL rows text-only | markers hang at x≈46, text at x≈64; RTL rows carry `.1`/`.2`/`.א` | confirmed |
| L6 | css3-counter-styles-008 web | tofu box + `⁚ 10000` / `Ա. 10001` | `10000.` `10000` / `10001. 10001` | "tofu + wrong marker" — confirmed |
| L7 | position-absolute-containing-block-002 ios | green 100×100 at ≈(69,21) inside yellow 200×200 | green at (76,76) | confirmed |
| L7 | grid-abspos-staticpos-align-self-center android | green mark y≈67–116 | mark y≈42–91 | "+25 px" — confirmed |
| L8 | input-range-zero-inline-size ios | two blue range sliders 224×70 | a 16×22 green L-shaped stub only | slider group confirmed; ref is NOT blank (see §8) |
| L9 | hanging-punctuation-inline-001 android | `」` on its own black line below the orange run | `字字字字」` on one line, both colours | confirmed |
| L9 | block-ellipsis-032 android | three boxes, NO "…" anywhere | "…" at the end of each line | confirmed |
| L10 | background-clip-content-box-002 ios | ~5 px green bar x 16–20 then RED to x 115 | green 100×100 | "items 0 wide, red remains" — confirmed |
| L11 | all-prop-initial-color android / web | line BLACK (web: black + serif face) | line GREEN | confirmed |
| L11 | nth-child-of-class android | lines 5 and 7 BLACK | lines 5 and 7 GREEN | confirmed |
| L12 | initial-background-color ios | solid GREEN canvas | solid WHITE ref | confirmed (erased-ref calibration) |
| L12 | clip-path-paddingBox-1d ios | green square at (24,24)–(124,124) | at (16,16)–(116,116) | "displaced by the 8 px padding" — confirmed |

## 4. file:line citations — checked against the trusting-bohr tree

All resolve and say what PLAN.md claims. Highlights (relative to the worktree root):
- L1 `ColorParser.kt:247-259` — `when (colorSpace)` has srgb/srgb-linear/display-p3/a98-rgb/rec2020/xyz*/`else -> null`; no `display-p3-linear` arm. `ColorConversion.kt:507-510` `displayP3ToSrgb` uses `P3_TO_XYZ` after gamma decode. `view-transition-bake.mjs:408` `VT_OVERFLOW_INK_TOLERANCE_PX = 4`; `:2258` records the solve error; `:2297` `planViewTransitionBake` → `:2304 if (bail) return` BEFORE the ring shot `:2309-2314` and the step-2c stamp `:1734-1757`. (Plan's ":2296" is the blank line before the plan call — drift of 8 lines, mechanism intact.)
- L2 `ScreenshotCaptureScreen.kt:1590-1641` (`staticPos`, `.copy(marginTransparent = staticPos)`), `UaBlockMargins.kt:207-243` (`runningMax = maxOf(...)`), `CaptureCanvas.swift:414-434`, `ComposedRootStack.swift:272-300`, RC1 mount `CaptureCanvas.swift:622-624`, `ScreenshotCaptureScreen.kt:1063-1075`, `resolveCanvasPadding` `ComposedCaptureGallery.tsx:492`, `resolveComposedCanvasPadding` `:1341`, `resolvedPadding` `CaptureCanvas.swift:563`, `overflow: 'hidden'` `ComposedCaptureGallery.tsx:878` inside `composedCanvasStyle` (390 px), `composedIcbStyle :942-953` has no overflow, Android draw chain `:1716-1725` has no clip, `FixedHoistOverlay` `:811/:867`, RC1 branch `ComponentRenderer.kt:911-950`.
- L3 `ContentsUnboxing.kt:71-74` / `.swift:65-66` Float clause; `ComponentRenderer.kt:2781-2784` `anyBakedChild = Width ∧ Height` consumed `:2805`; `MulticolSpannerFlow.kt:322-323`; `ComponentRenderer.swift:4354-4361`; `FixedHoist.swift:111-118`; `isOutOfFlow :765`; `positionedChildren :2050`. `MulticolSpannerContainingBlock.kt` exists under `runtimes/compose/.../columns/`; no Swift twin exists (only `KoreanHangulFormal.swift`, `TransformInheritance.swift` found as the named iOS-only counterparts).
- L4 `StyleApplier.kt:532-557` `if (flipped) result = result.alpha(0f)`; `TransformsApplier.swift:174-177` `opacity(0)`; `TransformExtractor.kt:397-410` `markUnhandled("Transform")` on `inherit`; `MarginApplier.kt:88-93` / `PaddingApplier.kt:58` `LocalContainingBlock.current` inside `composed{}`; provider `ComponentRenderer.kt:2004` and `LocalElementContainingBlock :2021-2022`; consumer `PercentInsetPositioned.kt:73-77`; `:1886-1890` `childContainingBlock`; `buildSpacingContext :805-828`.
- L5 `extract-fixture.mjs:1158` `body.split(';')`, `:1175` `assignDeclaration`, `:1203` `important[k] = true`, `:1213-1218` rule push, `:1287-1299` `resolveOne` (rank + order only), `:905-921` `provablyInvalidDeclaration` (one rule R1), `:6298` inline split, `:6318-6337` unlayered merge loop (never reads `rule.important`), `:7095` frame split, `:8374` descriptor split, `:11028` 100×100 placeholder; `AngleParser.kt:24` regex has no exponent / IGNORE_CASE; `ColorParser.kt:371-375` falls to `toDoubleOrNull ?: 0.0`; `GradientPrefixGuard.kt:117-125` comment.
- L6 `ListStyleConfig.kt:42` last enumerated style `KATAKANA_IROHA` (no korean arm), `ListStyleExtractor.kt:175-176`, `ListStyleApplier.kt:46`, `SizingApplier.kt:435` `m.height(v.px.dp)`, `BorderSideApplier.kt:221-225` `size.height - width/2`, `ListStyleTypeApplier.ts:4-6`, `RenderListItemMarker :4021`, `markerPlacement :4405` + HStack `:4459-4470`, `counter-style-bake.mjs:60` `DYNAMIC_SIGNAL_RX` with `@counter-style|<script`, `inject-wpt-block.mjs:1308` `NATIVE_FONT_PARITY_REFUSED_TESTS`.
- L7 `GridRenderer.kt:367-378` overlay `contentAlignment = staticOverlayAlignment(...)`, `CanvasRootHoist.kt:334-352` (4 args, no owner flag), `:834` `layout(hoistedFlowReportPx(), …)`, `ComponentRenderer.kt:7931-7939` AlignItems fold lacks SELF_END/SELF_START, `.swift:1278-1284`, `:2102-2112` passes `childCB/childCBH`, `AbsposStaticOffset.swift:75-95` (an `extension AbsposStaticPosition`, `:15` — so `AbsposStaticPosition.staticOffset` at the call site is this function; no padding term), `AbsposGridStaticPosition.swift:102-115` has `paddingStart`, `AbsposStaticPosition.swift:134ff` `resolveStatic`, `AlignSelfPropertyParser.kt:8-27` single keywords → `else -> return null`, `StyleBuilder.ts:274-320` `applyGeneric` logs static values.
- L8 `ChUnitMetrics.kt:50-68` `cacheName` → `"default"`, `:91-92` `Typeface.DEFAULT`, `:103` `measureText("0")`; `.swift:107` `.horizontal`, `:146-147` `UIFont.systemFont`; Inter at `ComponentRenderer.kt:6638` / `.swift:5255`; `StyleBuilder.swift:357-364`; `VerticalRunIntrinsics.kt:234-243`; `VerticalTextFlow.kt:275`; `VerticalTextFlowLayout.swift:103-122`; `WptCaptureMode.kt:214` / `WPTCaptureMode.swift:146`; `TableBoxTree.kt:211-212` / `.swift:192-194`; `UAWidgetsGeometry.kt:75-76,179` / `.swift:79-80,182`; `ComponentRenderer.kt:7622-7637`; `.swift:4969-4987`.
- L9 `InlineSpanRing.kt:197-202` `admit(glyphless=false)`, `:299-300`, `:318-319`; `.swift:158-160` (no glyphless parameter), `:258` generic `member-prop:` refusal; `InlineRunFlow.swift:723-726`; `placeholderOverflow :7760`, `:7800-7803` returns `declared`; `TextStyleApplier.kt:1574` Clip default; `LineClampApplier.kt:95` Ellipsis; `.swift:4744-4748` pre-break guard, `:5071` lineLimit, `:5076-5078` fixedSize; `LineClampApplier.swift:13-14`; `PreBreakPipeline.kt:155-161` identity unless unbreakable overflow; call site `:6973-6981` `unclippedLineWidths = !wrapConfig.softWrap`; `LineClampUnderPreWave39Test.kt:155`; `InlineSpanRingTests.swift:175-179`.
- L10 `CSSFlexLayout.swift:262` `basis: claim.basisPx ?? ideal.main` (no `basisPercent`); `MarginApplier.swift:108-135` negative margin → `OffsetIf`; `Row(` at `ComponentRenderer.kt:2213` and `:2508`.
- L11 `ComponentRenderer.kt:1134-1137` `unresolvedProperties = if (allReset != null) emptyList()`; `GlobalExtractor.swift:41-49` returns `[]`; called on the MERGED list at `ComponentRenderer.swift:340`; `AllApplier.ts:4-6`; `StyleBuilder.ts:222` `applyGlobalPhase10` is the last engine-path assign; `post-load-extract.mjs:359` `color: { repairOnly: true }`; `ContentsUnboxingTests.swift:175-183` `testAllResetDropsEveryOtherDeclaration`.
- L12 `inject-wpt-block.mjs:743` `WPT_PRESENCE_REF_MIN_PCT = 0.02`, `:905` `computePresenceFailed`; `tools/titan/results/wave50-B10/capture-browser-ref-body-background.patch` exists (3.7 KB).

## 5. Census cross-checks

- L7 T1: 16 Android `grid-abspos-staticpos-align-*` cells P < 1 with web/ios 1.0 — re-derived 16 (0.9698–0.9856). T3: 12 web `*-last-baseline-*` — re-derived 12.
- L12-A: 19 absence-only cells / 7 tests / web 7 · ios 6 · android 6 — re-derived exactly; 11 blank-ref FAIL cells stay scored.
- L2 overrun: `overrun-ssim-prediction.json` has 274 rows (overrun-right 192 + overrun-left 82; web 101 / ios 95 / android 78), recorded == recomputed on all 274, 197 P rows, none predicted < 0.95, 4 P rows predicted slightly below recorded (borders-002 android among them). The 8 f-rows predicted ≥ 0.95 without cF/nI are exactly the plan's DEGENERATE/honest list: gaps-034 ×3 (→0.954), contain-inline-size-bfc-floats-001 ios/android (→0.953/0.952), anchor-position-multicol-007 android (→0.9519), semi-replaced-stretch-input web (→0.9596), backdrop-filter-basic-blur ios (→0.9526).
- `frame-ink-census.json` scored classes: overrun-right 208, overrun-left 84, overrun-top 23, mixed 38, frame-colour 47 — see §8 on the 274 vs 292 denominators.

## 6. BACKLOG pointers

Headings exist: "## Standing constraints", "## Next-wave obligations", "## Ranked queue (wave 51+)", "## Instrument decisions pending". Every quoted phrase resolves (items (a′)(a″)(a‴)(b′)(b″)(c)(e)(j)(k)(l)(m)(n)(aa)(ab); "The 16 px page-padding overrun", "Out-of-flow static position", "027 stays open and unexplained", "failure-indicator ink reaches the canvas",
"The SAME level defect exists", "Scientific-notation angles", "Web tail residuals", "Counter/marker follow-ups", "Vertical-wedge residuals", "Inline-run wall, corrected map", "css-gaps residuals", "A computed colour never reaches the text run", "absence-only class", "blank-capture guard", "erased-reference body background", "wave 52 therefore opens", "a score is not a look", "Suite runs on a shared mid-wave tree are single-writer").
Four phrases only match across a line wrap or bold markup: "The Compose twin does\n not exist" (:1420), "proven able to fail" (:99, bolded), "re-derived by a skeptic with its OWN\n census" (:157), "session scratchpad" (:127). None is a misquote.

## 7. Watchlist

`watchlist.txt` (195 lines) carries every predicted flip and at-risk family I checked — L6's `css3-counter-styles-00` and `cssom` family lines cover armenian-008 and the three cssom tests; L11's `css-cascade/all-prop` and `nth-child-of` cover the colour movers; L12-A lists the 7 absence-only tests by name (plus the olive `background-color-animation-in-body`, correctly, as a must-not-move).

## 8. Corrections (concrete; none refutes a lane)

1. L2 overrun arithmetic: §1 says "274 overrun cells", §2 says "gaps-040 … and 273 more cells", §2 census says "208 overrun-right / 84 left". The 274 is `overrun-ssim-prediction.json`'s same-size subset (192 right + 82 left) and it INCLUDES the three 040 cells, so "271 more", not 273; the census tripwire denominator is 292 (208+84) plus 23 overrun-top and 38 mixed that Fix A also touches. State one denominator.
2. L1 F-B pointer: the overflow bail is recorded at `view-transition-bake.mjs:2258` (`over.outside > VT_OVERFLOW_INK_TOLERANCE_PX` → `solved[key] = {error}`), surfaced by `planViewTransitionBake` (`:1497/:1557`) and returned at `:2304`, not `:2296`; ring sample `:2309-2314`. Mechanism as described.
3. L8 target wording: "a 224×70 slider group where the ref is blank" — the ref `forms__input-range-zero-inline-size.png` is not blank; it shows a 16×22 green L-shaped stub at (16,66). native-near-misses T12 has it right ("only the 16×20 green stub"); vertical-wedges T2 and PLAN L8 should say so, since the stub is ink the post-fix natives must still paint.
4. L4 at-risk list names `backface-visibility-hidden-003/004 android` beside passing cells; both are f today (android f 0.9845 cF nI / f 0.998 cF nI). They can move, not be lost — label them movers.
5. L10 target wording: "both flex-basis:50% items 0 wide" — the iOS PNG shows a ~5 px green bar at x 16–20 (the `.left` border, as runtime-bugs-and-gaps.md row 16 says) before the red; the plan's shorthand drops the bar. Cosmetic.
6. L6 hand-off: PLAN says css3-counter-styles-008's natives go to `NATIVE_FONT_PARITY_REFUSED_TESTS` (adjudication A); today -008 ios f 0.942 / android f 0.9423 are scored and only -007 is `native-font-parity`-excluded — consistent with "re-add", but the plan's §1 row credits only "+1 (HIGH)" for web and does not say the two native cells become `scoreExcluded` (a −0/−0 on pass but −2 on the denominator). Say so in the closing-gate expectation.

## 9. What I could not verify

- Predicted post-fix pictures (they need a device or a pixel simulation the plan owes per lane); I verified only that today's captures show the stated defects and that the prediction JSON is internally consistent.
- Whether the iOS `flexbox_align-items-stretch-writing-modes` re-stack lands at the 100-px line (L3 MED) — a runtime question, not a pointer question.
- Brief-internal pixel bounding boxes (e.g. "y 102–201", "342 black px") beyond eyeballing the PNGs at 1×; the ones I checked by eye are consistent with the images.
- The scorer-flip "DEGENERATE" labels depend on L12-B re-freezing the refs; I confirmed the eight cells match the prediction's ≥0.95 f-rows exactly, not that the re-freeze will land.

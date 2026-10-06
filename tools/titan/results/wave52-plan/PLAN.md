# Wave 52 — builder-lane plan (synthesized 2026-09-25 from the fourteen family briefs)

Inputs: every brief under `tools/titan/results/wave52-plan/*.md` (static-position, failure-ink, page-padding-overrun,
colour-not-reaching-text, absence-only-denominator, cascade-and-splitter, compose-containing-block-level, vertical-wedges,
counters-and-multicol, inline-run-wall, web-tail, native-near-misses, runtime-bugs-and-gaps, harness-and-android-resample),
`docs/BACKLOG.md` "## Standing constraints" and "## Next-wave obligations". Evidence run of record: `wave51-fix`
(web 1215/1379 · iOS 1089/1369 · Android 1080/1369). Every cell below is `wave51-fix <section>/<test> <platform> <P|f> <ssim>`
as the briefs read it from the manifests. Nothing was built, run or captured while planning (the device gate was live).

## 0. Rules every lane works under

- **Opening gate first** ("## Next-wave obligations" #0: "wave 52 therefore opens, as always, with the FULL gate on a quiet host …
  expected: zero per-cell change"). `tools/titan/gate-driver.sh wave52-open`, scored `score-gate.mjs wave51-fix wave52-open --watch
  tools/titan/results/wave50-gate/watchlist.txt`. The first divergence from 0/0/0/0 is the first item of the wave, ahead of every lane here.
- **Seam files are never edited by a lane.** `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`,
  `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`, `apps/web-harness/src/sdui/ComponentRenderer.tsx`,
  `tools/titan/extract-fixture.mjs`. A lane that needs a change there commits a verified patch (applies clean on the tree it names, its own
  pins green with the patch applied) under `tools/titan/results/wave52-<lane>/seam-<n>.patch`; the orchestrator applies them in the landing
  order of §4 using the hunk registry of §3.
- **One owner per non-seam file** (§2 "own:" lists; checked for duplicates — none). Where a brief wanted a hunk in a file another lane owns,
  §5 says who delivers what to whom. `docs/BACKLOG.md` / `docs/STATUS.md` are orchestrator-owned; lanes hand over paragraphs in their lane note.
- **Every artifact is committed the moment it exists** under `tools/titan/results/wave52-<lane>/` (standing constraint "Evidence pointers …
  are repo paths or gate cells — never a session scratchpad"; the six-day purge lesson of obligation #0).
- **Device-free verification owed by every lane** (the gate is the only device run): (i) JVM / Catalyst / vitest pins on the VERBATIM per-test
  IR payloads named in its brief, each proven able to fail by the named mutation ("A new check must be proven able to fail"); (ii) a corpus
  census over the 1435 `tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json` docs with the grep shape its brief gives, re-derived by a
  skeptic with its own script before it is believed ("A builder lane's blast radius is re-derived by a skeptic with its OWN census");
  (iii) a PNG replay — the predicted post-fix picture (from the web capture where web already matches the ref, or a pixel simulation as
  `page-padding-overrun` did) laid over the frozen ref, with the residual described in pixels; (iv) the "gate pins": the exact cells the lane
  predicts to move, listed in `watchlist.txt` (this directory), so `score-gate.mjs … --watch` attributes them — and every watchlist line must match at least
  one scored wave51-fix cell under the scorer's own substring rule (`score-gate.mjs:138-145`: `pat` is a substring of `${sectionDir}/${manifestKey}`, key shape
  `css/<section>/<path>.html`, optional second token = platform): `node tools/titan/results/wave52-plan/watchlist-check.mjs` imports `loadRun`/`watchCells` from the
  scorer itself and must print `unmatched 0` before the closing gate (plan-skeptic-1 C7 found 11 dead lines, L7's headline flip among them — all repaired, §9).
- **Labels are honest**: a P→P rise is "picture-correctness", a scorer flip on a wrong picture is "degenerate" (obligation #3: a score is not a
  look), and the two instrument halves LOWER or MOVE the score by construction and say so. The ring-fenced
  `filter-effects/backdrop-filter-basic-blur` is never a target; where a generic mechanism moves it (§2 L2) it is reported, never carved out.
- Say "runtime", not "engine".

## 1. Lane table — ordered by expected cells per unit of effort

| # | lane | brief(s) | targets (wave51-fix) | predicted flips (confidence) | effort |
|---|---|---|---|---|---|
| L1 | web-tail-colour-vt | web-tail F-A, F-B | display-p3-linear-001/002/003 web f 0.8775/0.7977/0.9484; fractional-box-with-{shadow,overflow-children}-{new,old} web f 0.9763/0.9757 | +9 (p3 ×3 platforms, HIGH) +4 web (HIGH) +8 native (MED-HIGH) | S |
| L2 | composed-canvas | page-padding-overrun Fix A; runtime-bugs M1; native-near-misses T1/T2-roots/T3/T6 | clip-path-ellipse-006/007/008 ios/android f 0.9488/0.9480; text-decoration-propagation-shadow ios/android f 0.9484/0.9486; align-items-007 ios/android f 0.9974/0.9966; s-11-1-1b-006 ios/android f 0.9321/0.9332; flex-gap-decorations-027 web f 0.9012; 292 overrun-right/left cells (tripwire; 274 SSIM-predicted) | +10 (HIGH) +2 (MED) +1 web 027 (HIGH) +1 web semi-replaced-stretch-input (MED); +6 scorer flips DEGENERATE (labelled) | L |
| L3 | failure-ink | failure-ink F1/F2/F3 | display-contents-float-001 ios/android f 0.9417/0.941; flexbox_align-items-stretch-writing-modes ios f 0.999; abspos-containing-block-outside-spanner ios f 0.9553 | +2 (HIGH) +1 (MED) +1 (MED) | M |
| L4 | small-fixes | native-near-misses T5/T7; compose-containing-block-level b″ (+b′) | composited-under-rotateY-180deg-preserve-3d ios/android f 0.9565; css-transform-inherit-scale android f 0.9965; abspos-auto-sizing-fit-content-percentage-001 / -002 / -003 / -004 android P 0.9984 ×4 (passing-wrong) | +2 (HIGH) +1 (MED-HIGH); b″ 0 — **correctness, byte-identical PNGs required** | S–M |
| L5 | extractor-cascade | cascade-and-splitter F1–F4; web-tail F-C/F-D/F-E (= counters T6) | import-conditional-001/002 web f 0.999 (natives 0.9974/0.9966); color-mix-percents-02 web f 0.9514 (natives 0.9503/0.9496); flex-gap-decorations-024 ×3 P 0.990 novelInk | +6 (HIGH per cell, blast radius UNMEASURED until the differential census) +1 web (HIGH) +2 native (MED); F-E 0 alone | M–L |
| L6 | counters-and-lists | counters-and-multicol T1/T2/T3/T5/T7 | css3-counter-styles-008 web f 0.9435; cssom-{pad,prefix-suffix}-setter-invalid ×3 f 0.982/0.984, cssom-negative-setter-invalid ios/android f 0.9849; counter-suffix ios f 0.9285, android f 0.9051 | +1 (HIGH) +8 (MED, needs L5 F-E) +1 (MED); adjudication A: -008 ios/android → `scoreExcluded` (0 on pass, −1 on each native denominator) | L |
| L7 | static-position | static-position T1/T2/T3 | position-absolute-containing-block-002 ios f 0.9373; 16 android css-grid/abspos/grid-abspos-staticpos-align-* P 0.9698–0.9856; 12 web *-last-baseline-* P 0.9954–0.9983; justify-self-001 ios P 0.9921 | +1 (MED-HIGH); 28 cells → 1.0000 (picture-correctness, HIGH) | M |
| L8 | vertical-wedges | vertical-wedges M-A/M-B/M-C/M-E/M-G (= native-near-misses T12) | input-range-zero-inline-size ios/android f 0.9747/0.9743; ch-units-vrl-005..008 ios/android f 0.9159/0.9103; -003/-004 ios/android f 0.8396/0.8404 | +2 (MED-HIGH) +8 (MED) +4 (MED-LOW) | L |
| L9 | inline-run-wall | inline-run-wall F1–F5 | hanging-punctuation-inline-001 android f 0.9495; block-ellipsis-032 android f 0.9397 (ios P 0.9577 wrong picture); block-ellipsis-025 ios f 0.9495; hyphens-manual-inline-012 android f 0.9419 | +1 (MED-HIGH) +1 (MED-LOW) +1 (MED) +1 (MED); 20 Android clamp hosts gain their "…" | M–L |
| L10 | flex-nowrap-gaps | runtime-bugs 7(b), M2, M3 (gated), 7(a′), 6(c) | background-clip-content-box-002 ios f 0.9992 cF; flex-gap-decorations-027 ios/android f 0.9030/0.9085 (with L2 M1); 008 android P 0.9646; 045/046 band edges | +1 (HIGH) +2 (MED-HIGH/MED) | L |
| L11 | all-reset-postload-colour | colour-not-reaching-text F1, F2 | all-prop-{initial,inherit,revert,unset}-color ×3 P (black text); nth-child-of-class (+7 twins) ×3 P (black lines) | 0 flips — **correctness**; 12+~24 cells rise; P→f risk all-prop-001 ×3 | M |
| L12 | instrument-and-calibration | absence-only-denominator A+B; page-padding-overrun Fix B; B10 patch (BACKLOG "Instrument decisions pending") | 19 absence-only PASS cells; initial-background-color ×3 f 0.5414; paddingBox/contentBox-1d/1e ×12 P 0.9585/0.9696 (wrong ref) | **lowers the score by construction: −7/−6/−6**; +3 (MED) after re-freeze; 12 cells degenerate→honest; **B: gaps-033 ios/android P → f (−2, pre-registered, honest)** and 033 web f → P (+1, MED) | M |

Expected headline if the HIGH / MED-HIGH predictions land: web ≈ 1220/1372 (88.9 %), iOS ≈ 1101/1362 (80.8 %), Android ≈ 1091/1362 (80.1 %) —
after L12-A's −7/−6/−6, after L12-B's pre-registered `css-gaps/flex/flex-gap-decorations-033` natives P → f (−2 on each native, C1), after adjudication A's
`armenian/css3-counter-styles-008` natives leaving the denominator (−1 on each native denominator, 0 on pass, shipped with L6 — C3), and before the MED tier
(up to ≈ +3 / +14 / +13 more, plus 033 web f → P +1 MED). **Lost must be exactly 2** — the two 033 native cells, named in advance; any other `LOST` row is
diagnosed. Every other mover is attributed by the watchlist or by name.

## 2. Lanes in detail

### L1 · web-tail-colour-vt (effort S) — brief `web-tail.md` §3-A/B, §4 F-A/F-B
Queue: "## Ranked queue (wave 51+)" → 5 "Web tail residuals" (the regenerated 164-cell tail).
- Targets: `wave51-fix css-color/display-p3-linear-001 web f 0.8775`, `-002 web f 0.7977`, `-003 web f 0.9484` (+ the same tests ios/android f);
  `wave51-fix css-view-transitions/fractional-box-with-shadow-new web f 0.9763`, `-old web f 0.9763`, `fractional-box-with-overflow-children-new web f 0.9757`, `-old web f 0.9757` (natives 0.9749/0.9739).
- Mechanism: `converter/…/primitiveParsers/ColorParser.kt:246-259` has no `display-p3-linear` / `a98-rgb-linear` / `rec2020-linear` arm → `srgb=null` → nothing painted;
  `ColorConversion.kt:507-510` already holds the P3 matrix (the linear variant skips the transfer decode, css-color-4 §10.2).
  `tools/titan/view-transition-bake.mjs:2258` records the overflow bail (`over.outside > VT_OVERFLOW_INK_TOLERANCE_PX` (:408) → `solved[key] = {error}`), which
  `planViewTransitionBake` (`:1497` / `:1557`) surfaces as `bail` and `:2304` (`if (bail) return`) returns BEFORE the ring sample (:2309-2314) and the body-root
  background stamp (step 2c :1729-1757, the `if (frameRing)` at :1734), so the author `::view-transition{background:lightpink}` backdrop never reaches the composed canvas.
- own: `converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/ColorParser.kt`
- own: `converter/src/main/kotlin/app/irmodels/ColorConversion.kt`
- own: `converter/src/test/kotlin/app/irmodels/ColorConversionTest.kt` (+ a new ColorParser linear-space test beside it)
- own: `tools/titan/view-transition-bake.mjs`
- own: `tools/titan/view-transition-bake.test.mjs`
- Seams: none. Read-only: the body-root background channel (`ComposedCaptureGallery.tsx:117`, `ScreenshotCaptureScreen.kt`, `CaptureCanvas.swift`) — L2 owns those files; F-B changes only the fixture.
- Verification owed: `displayP3LinearToSrgb(1,1,1)→(1,1,1)`, `(0.0383,0.2087,0.0156)→(0,0.502,0)±1/255` (mutation: route through the gamma-decoding `displayP3ToSrgb`);
  vt-bake §8 pins (active+solve-error stamps body-root background and leaves every `display` undefined; inactive writes nothing; white ring writes nothing);
  census: 6 `display-p3-linear-00{1..6}` docs, 29 backdrop authors / 8 active solve-bails (`web-tail.census.json`); PNG replay: the ref's solid square / pink canvas.
- Flips: +3 web +6 native p3 (HIGH; falsifier: -001 lands >1/255 off #008000); +4 web (HIGH) +8 native (MED-HIGH) fractional-box (falsifier: frozen-page ring reads white → no stamp).
- At risk: `css-color/display-p3-linear-004 ×3 P ≈0.953` (out-of-gamut; clip vs Chromium gamut-map — MED-LOW colour-veto risk), -005/-006 ×3 (movers up);
  **`css-view-transitions/column-span-during-transition-doesnt-skip ×3 P 0.9879`** — its live ring is pink but the frozen ref is WHITE (erased-ref sub-mechanism):
  F-B costs these 3 under today's refs and is what makes them pass after L12-B re-freezes. Land F-B in the same gate as L12-B or report −3/+3 plainly.

### L2 · composed-canvas (effort L) — briefs `page-padding-overrun.md` §3-A/§4 Fix A; `runtime-bugs-and-gaps.md` §3-M1/§4-2; `native-near-misses.md` §3.1/3.2(roots)/3.3/3.6
Queue: 0(l) "The 16 px page-padding overrun — 3 cells, possibly ONE root cause" (it is one cause, on all three platforms, harness-side);
0(j) "Out-of-flow static position … the mark is the right shape and the right colour, in the wrong place" (the 16-px root-stack families);
7(e³) "027 stays open and unexplained" (now explained: M1 + L10's M2/M3).
- Targets (all opened): `wave51-fix css-masking/clip-path/clip-path-ellipse-006 ios f 0.9488 · android f 0.9480` (-007/-008 identical; ellipse 16 px too HIGH — the abspos root's
  margin-top 50 is max()-joined with the `<p>`'s UA 16 instead of added, CSS 2.1 §8.3.1); `css-text-decor/text-decoration-propagation-shadow ios f 0.9484 · android f 0.9486`
  (16 px too LOW — the empty `height:0` `<p>` roots do not collapse through); `css-flexbox/align-items-007 ios f 0.9974 · android f 0.9966` (red `<img>` over the abspos green:
  the RC1 static-position root paints in slot order, Appendix E step 8); `CSS2/css21-errata/s-11-1-1b-006 ios f 0.9321 · android f 0.9332` (prose 16 px high: inset abspos
  `<p>` gets no UA margin, §9.3.2); `css-gaps/flex/flex-gap-decorations-027 web f 0.9012` (whole page 200 px left: declared body `margin-left` dropped by the canvases);
  `css-gaps/flex/flex-gap-decorations-040 web P 0.9939 / ios P 0.9934 / android P 0.9934` and 271 more cells whose only divergence is ink in the 16-px frame (the ref is a
  358-px viewport crop; the canvases clip at 390, not at the ICB). **Fix A population — ONE denominator (plan-skeptic-0 §8.1): 292 scored cells** = `frame-ink-census.json`
  overrun-right 208 + overrun-left 84, the tripwire that must print 0 / 0 after. Of those, **274** have a same-size capture and are the rows of `overrun-ssim-prediction.json`
  (192 right + 82 left; web 101 / ios 95 / android 78) — the SSIM-prediction SUBSET, which INCLUDES the three 040 cells (hence "271 more"). The census's other classes
  (overrun-top 23, mixed 38, frame-colour 47, height-mismatch 170) were counted, not diagnosed (brief §9): mixed cells carry left/right ink and WILL move under Fix A —
  watched as movers, not predicted; overrun-top ink lies on the axis Fix A leaves visible by design.
- Mechanism (file:line): fold `apps/android-harness/…/ScreenshotCaptureScreen.kt:1585-1641` (`marginTransparent = staticPos`, own margins join the set) + `UaBlockMargins.kt:207-243`
  (`runningMax = max(...)`), twins `apps/ios-harness/…/CaptureCanvas.swift:414-434` + `runtimes/swiftui/…/StyleEngine/spacing/ComposedRootStack.swift:272-300`; RC1 mount without
  z-order `CaptureCanvas.swift:614-625` (+ the Compose RC1 item, seam); inset abspos root anchored at the padding edge `ScreenshotCaptureScreen.kt:1063-1075`; canvases read the body-root
  for background + padding only (`ComposedCaptureGallery.tsx:492`, `ScreenshotCaptureScreen.kt:1341`, `CaptureCanvas.swift:563`), never margin; clip on the 390 canvas
  (`ComposedCaptureGallery.tsx:878` vs the un-clipped ICB div `:942-953`; Android draw chain `:1700-1725` has no clip; iOS `ComposedCaptureCanvas` `:277-870` has no `.clipped()`).
- Fix: (a) `overflowX:'clip'` on `composedIcbStyle` (css-overflow-3 §3.1/§3.2; vertical stays visible); Android `clipRect(frameLeft, -BIG, width-frameRight, +BIG)` around
  `drawContent()` inside the composed `graphicsLayer.record`, after `.background(canvasBackground)`, rect math as a pure helper beside `WptCaptureMode.composedIcbExtentDp`;
  iOS a band `Shape` x∈[16,374) via `.clipShape` on a group holding the VStack AND both `FixedHoistOverlay` attach points (`CaptureCanvas.swift:811/:867`).
  (b) `resolveCanvasMargin` twin of the padding resolver ×3 (concrete px only), spent on the in-flow root stack inside the ICB as a flow-root wrapper — never on canvas-hoisted
  abspos/fixed roots (CSS 2.1 §10.1); negative `margin-top` as a margin, not a padding. (c) fold: a `staticPos` root contributes (0,0) to the collapse set and keeps its declared
  margins on the box (§8.3.1 "margins of absolutely positioned boxes do not collapse"); an empty in-flow root (no text/children, `Height` absent or 0, `MinHeight` 0/absent, zero
  block border/padding, not OOF) is `marginTransparent` (collapses through); `Modifier.zIndex(1f)` / `.zIndex(1)` on the RC1 anchored host above in-flow roots, below hoisted
  overlays (Appendix E step 8); synthesise the UA vertical margin for an inset abspos root with a UA-margin tag and undeclared block margins (§9.3.2 offsets the margin edge).
- own: `apps/web-harness/src/ui/ComposedCaptureGallery.tsx`
- own: `apps/web-harness/tests/ui/ComposedCanvasIcbClip.test.tsx` (new) and a `resolveCanvasMargin` vitest beside the padding one
- own: `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`
- own: `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/UaBlockMargins.kt` (+ its JUnit table under `apps/android-harness/app/src/test/…/screenshot/`)
- own: `apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift`
- own: `apps/ios-harness/StyleConverterTestTests/ComposedCanvasIcbClipTests.swift` (new)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/ComposedRootStack.swift` (+ `UABlockMarginTests`/`ComposedRootStackTests` under `runtimes/swiftui/Tests/`)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/WptCaptureMode.kt` (pure helpers + rule constants) and `…/core/renderer/ComposedIcbClipTest.kt` (new)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/WPTCaptureMode.swift`
- Seams: Compose RC1 item z-order — `ComponentRenderer.kt:911-948` (patch; **OVERLAPS L7's `:927-950` fifth-argument hunk inside the one `val itemModifier = if (…)` expression
  at `:921-948` — L2's patch is cut against HEAD and lands FIRST; L7 re-cuts against the L2-applied tree**, plan-skeptic-1 C6). Read-only: `runtimes/compose/…/layout/position/CanvasRootHoist.kt` (L7 owns), `runtimes/swiftui/…/Renderer/FixedHoist.swift` (L3 owns) — if the iOS regroup needs a FixedHoist.swift change, hand the hunk to L3.
- Verification owed: pure-helper pins (canvas 390/frame 16 → 16/374; author `body{padding:40px}` must NOT move it); source pins that the composed canvases call the helpers; the fold table R/S/T
  (`[p 16/16, abspos static-pos m50]` → gap 66, mutation `max()` → 50; `[p 16/16, EMPTY h0 16/16, block]` → one 16 gap; RC1 root over an in-flow root → the RC1 colour wins the pixel);
  canvas margin pins (`MarginLeft 200` → flow inset 216; `auto`/`em` → 16; `MarginTop −15` on the wrapper); census: `frame-ink-census.json` 208 overrun-right + 84 overrun-left = 292 (tripwire: 0 / 0 after),
  `body-root` with non-zero `Margin*` = 9 tests / 27 cells, fold carriers T1 3 / T2-roots 8 / T3 22 / T6 1 (`native-near-misses.carriers.json`); PNG replay: `overrun-ssim-prediction.json`
  (274 same-size rows, 0 mismatches vs recorded ssim; 197 P cells rise, none < 0.95), the web captures of every T1–T6 test as post-fix oracle (≤0.001 off the ref).
- Flips: +6 ellipse, +2 propagation-shadow, +2 align-items-007 (HIGH; falsifiers: ellipse lands at y ≠ 118 → `withHoistBand` also owns the gap; RC1 ink not the overlapping green);
  +2 s-11-1-1b-006 (MED; small ink masses), s-11-1-1b-005 android (LOW); 027 web (HIGH — shifting the capture +200 maps every run onto the ref); Fix A: gaps-040 web 0.9939 → 1.0000 exactly
  (HIGH; falsifier: any ink at x ≥ 374 after), `css-position/position-absolute-semi-replaced-stretch-input web 0.9422 → ~0.96` (MED, honest); **DEGENERATE scorer flips to label, not claim**:
  `css-gaps/flex/flex-gap-decorations-034 ×3 0.946 → 0.954` (honest only after L12-B), `css-contain/contain-inline-size-bfc-floats-001 ios/android 0.940 → 0.953` (orange bar ~100 px low),
  `css-anchor-position/anchor-position-multicol-007 android 0.9499 → 0.9519` (red visible). Ring-fenced `filter-effects/backdrop-filter-basic-blur ios 0.9469 → ~0.9526` moves incidentally — REPORT.
- At risk (all predicted to hold; PNG-check at the gate): `css-break/borders-002 android P 0.9594 → 0.9581` (closest), `selectors/has-style-sharing-002 ios P 0.9633`, `anchor-center-safe android 0.9522`,
  `counter-list-item-slot-order web 0.9509`, `text-decoration-skip-spaces-001 web 0.9524`, `text-decoration-inset-009 ios`, `text-decoration-line-recalc ios`, `discard-multicol-003 android`,
  gaps-036/037 ×3 ~0.956; M1: `CSS2/css21-errata/s-11-1-1b-005 web P 0.9947`, `-006 web P 0.9656` (8-px correction; 006 has 0.016 headroom); T2-roots: `css-images/gradient/gradient-hue-direction ios/android P 0.997`
  (the baked `<hr>` — predicate must check its border), `has-style-sharing-002` (em-margin root); T3: the 19 passing RC1-root docs (watchlist) — Appendix E is what the ref painted, none predicted to move.
- Dependency: L10's 027 native flips need this lane's M1. L12-B's re-freeze makes 034/035 honest.

### L3 · failure-ink (effort M) — brief `failure-ink.md` §3 T1–T3, §4 F1–F3
Queue: 0(k) "The test's own failure-indicator ink reaches the canvas — 7 cells" ("splits by mechanism rather than by platform").
- Targets: `wave51-fix css-display/display-contents-float-001 ios f 0.9417 · android f 0.941` (full-width RED bar behind "PASS": `ContentsUnboxing` refuses any floated element —
  a misread of css-display-3 §2.7's no-box parenthetical; web P 1.0); `wave51-fix css-writing-modes/flexbox_align-items-stretch-writing-modes ios f 0.999 cF` (RED 100×100 at x 166–265: the
  Z2 vertical-block-flow seam's "baked-layout guard" trips on AUTHORED `width:50px;height:50px` and stacks the squares vertically; android P 0.998 same picture);
  `wave51-fix css-multicol/abspos-containing-block-outside-spanner ios f 0.9553 cF` (RED 100×100 uncovered at (16,16): a spanner's abspos descendant resolves against the relpos column item —
  Compose has `MulticolSpannerContainingBlock.kt`, iOS has no twin; android P 0.999).
- Mechanism: `ContentsUnboxing.kt:71-74` / `ContentsUnboxing.swift:65-66` (Float clause); `ComponentRenderer.kt:2781-2785` + `MulticolSpannerFlow.kt:322-323` (`bakedPhysicalSize`) +
  `ComponentRenderer.swift:4354-4361` (`verticalBlockFlowZ2` guard = Width ∧ Height); iOS chain `FixedHoist.swift:111-118` + overlay partition `ComponentRenderer.swift:765/:2050`.
- Fix: F1 one shared predicate per native `bakedPhysicalBox = Width && Height && BoxSizing && (PaddingTop || BorderTopStyle)` (real baked wires carry 9–18 of these; authored carry 0)
  in a new `BakedLayoutSignature.kt` + Swift twin, used at the three sites; F2 delete the Float clause (keep the position clause); F3 port `MulticolSpannerContainingBlock` to Swift (pure)
  and thread `positionedAtMulticol` through the iOS overlay partition (css-multicol-1 §6.1/§6.2, CSS 2.1 §10.1). **This lane is the sole owner of the baked-guard hunk** — `vertical-wedges` M-F
  and `native-near-misses` T4 proposed the same change and are folded here.
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ContentsUnboxing.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/BakedLayoutSignature.kt` (new)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/MulticolSpannerFlow.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/VerticalMulticolMeasure.kt`
- own: `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ContentsUnboxingTest.kt`
- own: `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/VerticalBlockFlowSeamGuardTest.kt` (new)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ContentsUnboxing.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/FixedHoist.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/MulticolSpannerContainingBlock.swift` (new)
- own: `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ContentsUnboxingTests.swift` (**L3 does NOT touch `:175-183` `testAllResetDropsEveryOtherDeclaration`** — L11 rewrites
  that one function in L11's OWN PR, after L3 has merged, in the same commit as its `GlobalExtractor.swift` change; plan-skeptic-1 C5)
- own: `runtimes/swiftui/Tests/StyleConverterRuntimeTests/MulticolSpannerContainingBlockTests.swift` (new) and the Swift twin of the seam-guard test
- Seams: `ComponentRenderer.kt:2781-2785` (+ consumer `:2800-2807`), `ComponentRenderer.swift:4354-4361` and the overlay partition around `:765`/`:2050` (patches). The iOS partition
  region is ALSO patched by L7 (`:2098-2112`, two arguments — INSIDE `positionedChildren`, which begins at `:2050`, the same function this lane's F3 edits) — L7 cuts its
  patch against the L3-applied tree; orchestrator applies L7's after L3's (C6).
- Verification owed: P1 seam-guard rows (authored Width+Height → false; 18-longhand baked → true; mutation restores the 2-property heuristic); P2 unboxing rows
  (`CONTENTS+Float=RIGHT+BackgroundColor` text leaf → unboxable; `Position=ABSOLUTE` contents → false); P3 the Kotlin table byte-for-byte in Swift; census F1 13 carriers
  (8 baked stay guarded — the anchor-position-multicol family — pin them), F2 exactly 1, F3 exactly 1 (`failure-ink-census.json`); PNG replay: the ref's single text line / 250×100 green.
- Flips: display-contents-float-001 ios+android (HIGH); flexbox_align-items-stretch ios (MED — falsified if the re-stacked 100×50 item is not stretched to the 100-px line on iOS `CSSFlexLayout`;
  android stays P, shape changes); abspos-containing-block-outside-spanner ios (MED — falsified if `bottom:0;right:0` resolve against the padded canvas instead of the ICB).
- At risk: `css-anchor-position/anchor-position-multicol-008/013/014/015/016/017`, `-colspan-003` natives P 0.951–0.992 (must stay guarded); `css-display/display-contents-oof-001/002` (abspos clause kept);
  `css-tables/height-distribution/td-different-subpixel-padding-in-same-row-vertical-rl ios P 0.9638 / android P 0.9609` and `flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse ios`,
  `float-vlr-014 ios` (F1's engaged seam on author-declared sizes — census both ways, PNG-check); side-effect `abs-pos-border-offset-003 ios/android` (both f today, direction unknown).

### L4 · small-fixes (effort S–M) — briefs `native-near-misses.md` §3.5 (T5), §3.7 (T7); `compose-containing-block-level.md` §4 (b″, b′)
Queue: 0(k) (T5, T7 expose the red square by their own mechanisms); 0(b″) "The SAME level defect exists in `spacing/MarginApplier.kt` and `spacing/PaddingApplier.kt`";
0(b′) "The CSS 2.1 §10.1 republish for NON-BLOCK-CONTAINER ancestors is a real and SEPARATE gap"; 0(c) "css-transform-3d-transform-style (android) passes at 0.9577 … FRAGILE" — HOLD (tripwire only).
- Targets: `wave51-fix css-transforms/composited-under-rotateY-180deg-preserve-3d ios f 0.9565 · android f 0.9565` (BLANK canvas: `backface-visibility:hidden` alpha-0s the whole subtree under
  `preserve-3d`; ref green 100×100 at (16,16)); `wave51-fix css-transforms/css-transform-inherit-scale android f 0.9965 cF` (red 200×200 with centred green: Compose drops `transform: inherit`; iOS P 0.9974);
  `wave51-fix css-sizing/abspos-auto-sizing-fit-content-percentage-001 android P 0.9984`, `-002 android P 0.9984`, `-003 android P 0.9984`, `-004 android P 0.9984` (four cells;
  web P 1.0000 / ios P 0.9992 each) — PASSING-WRONG (percent margin/padding read one containing-block level too deep; invisible because the box is unpainted). The corpus
  also has -005..-008 at the same android P 0.9984; `percent-spacing-census.json` names only 001–004 as carriers, so the b″ `cmp` A/B is on those four (the watchlist
  pattern `…-percentage-00 android` also watches 005–008, harmlessly — C8).
- Mechanism: `StyleApplier.kt:532-556` `result.alpha(0f)` on the element chain (children included); `TransformsApplier.swift:174-177` `opacity(0)`; `TransformExtractor.kt:397-410` `markUnhandled` for `inherit`;
  `MarginApplier.kt:93` / `PaddingApplier.kt:58` read `LocalContainingBlock.current` inside `composed{}` which materialises INSIDE the element's own provider (`ComponentRenderer.kt:2004`) — the levelled
  channel `LocalElementContainingBlock` (`:2021-2022`, consumed by `PercentInsetPositioned.kt:73-77`) exists and is not read.
- Fix: T5 when the culled element has `TransformStyle == PRESERVE_3D` suppress its OWN background/border paint instead of alpha-ing the subtree (css-transforms-2 §10; the animated-001/002 degree-rule
  reasoning in the `StyleApplier.kt` banner stays); T7 a `LocalInheritedTransform` CompositionLocal (parent's extracted function list) provided from the renderer (seam patch) and read by `TransformExtractor`
  for the `inherit` keyword (css-cascade-4 §7.3.2); b″ `ElementContainingBlock.containingBlockFor(element=…, ambient=…, breadcrumb=SPACING_UNPUBLISHED_BREADCRUMB)` at both reads, fix the stale
  comment `MarginApplier.kt:79-82`; b′ optional `BlockContainer.kt` predicate + one-hunk seam patch at `ComponentRenderer.kt:1886-1890` (zero corpus movement).
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt` (**L4 edits the backface region `:532-557` only. The `buildSpacingContext` `:805-830` hunk
  SHIPS IN L8's PR** — L8 edits this L4-owned file after L4 has merged, L4 told; a hunk passing a third argument to `ChUnitMetrics.measure` cannot compile in L4's PR because
  `ChUnitMetrics.kt:38` is `fun measure(fontFamily: FontFamily?, fontSizePx: Float): Float?` until L8 lands — plan-skeptic-1 C4; see L8 Seams)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/transforms/TransformExtractor.kt` (+ `TransformExtractorTest`)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/transforms/TransformsApplier.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/transforms/BackfaceCulling.swift` (+ XCTest)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/MarginApplier.kt` (L10 READS margins for `FlexNowrapLine`; it does not edit this file)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/PaddingApplier.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/ElementContainingBlock.kt`
- own: `runtimes/compose/src/test/java/com/styleconverter/runtime/spacing/PercentSpacingContainingBlockLevelTest.kt` (new)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/BlockContainer.kt` + `…/layout/position/BlockContainerTest.kt` (new, b′ only)
- Seams: `ComponentRenderer.kt` provider block `:1983-2030` (T7 provider) and `:1886-1890` (b′) — one patch, this lane.
- Verification owed: T5 pin (culled + PRESERVE_3D + child → child pixels present, own background absent; mutation restores `alpha(0)`); T7 pin (`inherit` under parent `scale(2)` → `[scale 2]`);
  b″ S1–S6 (level discriminator on the verbatim `.abs`/`.child` payloads; `resolveToDp(Relative(-50,PERCENT), parentWidthPx=null, percentIndefiniteAsZero)` = 0; source pin that the ONLY
  `LocalContainingBlock.current` in each applier is the `ambient=` argument — run once against pre-port bytes as the negative control); census T5 = 1 carrier (11 flat carriers untouched), T7 = 1,
  b″ = 4 carriers all `paints=false children=false` (`percent-spacing-census.json`); **b″ A/B at the gate: the four Android PNGs byte-identical (`cmp`), 0 movers; the A/B records the installed `base.apk` sha1 / `.app` hash against the build**
  (standing constraint: `adb shell pm path <app>` → `adb pull` → `shasum -a 1`, compared with `apps/android-harness/app/build/outputs/apk/debug/app-debug.apk`, per run, in the
  committed record — a run without that hash is a score, not evidence).
- Flips: composited-under-rotateY ×2 (HIGH), css-transform-inherit-scale android (MED-HIGH). b″/b′: none by design (**correctness; any movement on the four css-sizing cells or on
  `css-position/position-relative-002/-008 android` falsifies the port**).
- At risk: `css-transforms/backface-visibility-hidden-animated-001/002 ios P 0.9646` (the degree rule must stay); **movers, not at-risk** (f today — they can move, not be lost;
  plan-skeptic-0 §8.4): `wave51-fix css-transforms/backface-visibility-hidden-003 android f 0.9845 cF nI`, `-004 android f 0.9980 cF nI`; tripwire (HOLD, do not touch):
  `css-transforms/css-transform-3d-transform-style android P 0.9577`, `filter-effects/backdrop-filter-3d-transform-perspective android P 0.9868`, `-nested- android P 0.9749`.

### L5 · extractor-cascade (effort M–L) — briefs `cascade-and-splitter.md` §4 F1–F4; `web-tail.md` §4 F-C/F-D/F-E; `counters-and-multicol.md` §4 T6
Queue: 0(a′) "The IMPORTANCE half of the same collapse is still open"; 0(a″) "A splitter defect UPSTREAM of the oracle"; 0(a‴) "The LAYERED cascade path keeps the unguarded sort";
0(e) "Scientific-notation angles (`1e2deg`) survive as a Raw passthrough"; 5 "Web tail residuals". Combined because F1 (importance) and F-D (specificity) rewrite the SAME merge loop
(`extract-fixture.mjs:6318-6337`) and F-C/F3 both live in the oracle `:905-921`/`:1287-1299`; F-E and T6 are the same one-line exemption.
- Targets: `wave51-fix css-cascade/import-conditional-002 web f 0.999 cF` and `-001` (RED for GREEN: `.test{background:green}` inlined by the `@import` resolver loses to a LATER `div{background:red}`
  — no Selectors-4 §17 specificity in `propsForElement`); `wave51-fix css-color/color-mix-percents-02 web f 0.9514 cF` (rows t6–t7 white: an INVALID `color-mix(... 125%)` shadowed the valid `rgb()`,
  css-color-5 §3.1); `wave51-fix css-gaps/flex/flex-gap-decorations-024 web P 0.9901 · android/ios P 0.9900` (9 px DOTTED BLUE column rule where the ref is solid PINK 10 px: `#container` beats
  `.flex-container … !important` because the unlayered merge never reads `rule.important`); regression anchors `css-values/angle-units-001 ×3 P` (the only oracle-firing doc) and `revert-layer-005 ×3 P`.
- Mechanism: `extract-fixture.mjs:1158` quote/paren-blind `split(';')` (also `:6298`, `:7095`); `:1175/:1203` importance-blind last-wins + never-cleared `important[k]`; `:6318-6337` document-order merge;
  `:1287-1299` `resolveOne` picks by rank with no validity question; `:905-921` one oracle rule; `:10995-11028` 100×100 placeholder on rule-less `<li>`; `AngleParser.kt:24` no exponent / case-sensitive
  unit → `hsl(1.2e2deg,…)` parses as RED via `ColorParser.kt:371-376`.
- Fix: F3 (candidate filter in `resolveOne`) BEFORE F1 (importance-aware collapse + gate widening) BEFORE F-D (per-rule `(a,b,c)` at `:1213-1218`, stable-sort `buckets['']` before the merge,
  tie-break inside a layer rank — css-cascade-5 §6.1 order); F2 one css-syntax-3 splitter (`splitDeclarations`: `;` only at paren depth 0, outside `<string-token>`/`<url-token>`, escapes as pairs;
  comments stripped first) at `:1158/:6298/:7095/:8374`; F-C oracle rule R2 (color-mix percentage outside [0,100]); F-E/T6 `LIST_ITEM_TAGS={li,summary}` exemption at `:11028` (css-lists-3 §2/§3);
  F4 `^([+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?)(deg|rad|grad|turn)$` IGNORE_CASE and re-point the three Raw pins.
- own: `tools/titan/extract-fixture.test.mjs`
- own: `converter/src/main/kotlin/app/parsing/css/properties/primitiveParsers/AngleParser.kt` (+ new `AngleParserTest.kt`)
- own: `converter/src/test/kotlin/app/parsing/css/properties/InvalidDeclarationDropTest.kt`
- own: `converter/src/test/kotlin/app/parsing/css/properties/primitiveParsers/ColorParserHslHueTest.kt` (pin 10 — the `hsl(1.2e2deg)` red-before/green-after; `ColorParser.kt` itself is L1's)
- own: `converter/src/main/kotlin/app/parsing/css/properties/longhands/background/GradientPrefixGuard.kt` (comment `:117-125` only)
- Seams: `tools/titan/extract-fixture.mjs` — ONE ordered patch set (`seam-1-F3-oracle-filter`, `seam-2-F1-importance`, `seam-3-FD-specificity`, `seam-4-F2-splitter`, `seam-5-FC-colormix`, `seam-6-FE-li`).
  L11's F3 (`::first-letter`) is NOT staffed, so no other lane patches this seam this wave except L6's `counter-style-bake` (a different file).
- Verification owed: pins 1–11 of the cascade brief + F-C/F-D/F-E pins of the web-tail brief (each with its mutation); **the census is the extract+convert differential** (two runner dirs, HEAD vs tree,
  `split-combined-ir.mjs`, `diff -rq` per-test IR — `tools/titan/results/wave50-S2/_note.md` recipe) run with `POST_LOAD_EXTRACT` UNSET and SET: expected changed docs F1 = 1 (-024), F2 = 2 (the css-pseudo
  first-letter pair, pixel-inert), F3 = 0, F4 = 0, F-C = 1, F-E = 15 (all `css-counter-styles/cssom/`), **F-D = UNKNOWN until measured — F-D ships only if every changed document's cells are read and
  listed** ("a lane's own census under-reporting its radius is the dangerous direction"); PNG replay: the ref's solid pink rule / green square / purple square.
- Flips: import-conditional-001/002 web +2 (HIGH per cell) natives +4 (HIGH); color-mix-percents-02 web +1 (HIGH), natives +2 (MED); -024 ×3 P→P ≈0.999 with `novelInkFailed` clearing (MED —
  falsified if the re-extracted bag still reads `9px dotted blue`); F-E 0 alone (prerequisite for L6 T7).
- At risk: `css-cascade/revert-layer-010 web/ios/android P 0.9716/0.9699/0.9692` (thinnest layered cell), the other static-path layered css-cascade cells (`layer-media-toggle`, `revert-layer-001…012`,
  `important-prop`, `revert-val-002`), `css-text-decor/text-decoration-line ×3` (gate-routed, cannot move), `css-pseudo/first-letter-background-image(-dynamic) ×3` (bytes move, pixels cannot),
  `selectors/has-style-sharing-003` (known F-D member, today rescued by post-load), every angle-consuming cell through F4 (proof = byte-identical IR differential), the 9 passing `cssom` tests (F-E moves them toward the ref).

### L6 · counters-and-lists (effort L) — brief `counters-and-multicol.md` §4 T1/T2/T3/T5/T7 (+ adjudication A)
Queue: 2 "Counter/marker follow-ups from the Rule-43 unexclusion" — (c¹) "natives — armenian fallback-row line breaking", (c³) "The Compose twin does not exist", (c⁴) "RTL markers are BLOCKED UPSTREAM";
3(b) "Compose renders the 003/balancing-003 orange band w px too high, where w is the band's OWN width". 3(a) (`<br clear=all>` fold + rung) is NOT staffed (§6).
- Targets: `wave51-fix css-counter-styles/armenian/css3-counter-styles-008 web f 0.9435` (tofu + wrong marker; web ignores the CORRECT baked `meta.markerText "10000."` by design);
  `wave51-fix css-counter-styles/cssom/cssom-pad-setter-invalid web/ios/android f 0.982` (+ prefix-suffix-setter-invalid ×3 f 0.984, negative-setter-invalid ios/android f 0.9849: markers at 100-px pitch —
  the `<li>` placeholder (L5 F-E) — and decimal instead of `001.`/`(A)`/`(3).` because `counter-style-bake.mjs` bails any fixture containing `@counter-style`); `wave51-fix css-counter-styles/counter-suffix
  ios f 0.9285` (rows start at x 64 — outside markers painted INSIDE the content edge) and `android f 0.9051` (+ rows 7–8 decimal for `일,`/`이,`: no Compose korean-hangul-formal arm);
  correctness: `CSS2/floats-clear/floats-clear-multicol-003 android P 0.9881` / `-balancing-003 android P 0.9857` (orange band w px too HIGH — a 0-tall box's bottom stroke centred at −w/2).
- Mechanism: `ListStyleConfig.kt:42` / `ListStyleExtractor.kt:175` / `ListStyleApplier.kt:46` (no korean arm; iOS has `KoreanHangulFormal.swift`); `SizingApplier.kt:435` fixed 0 constraints +
  `BorderSideApplier.kt:221-225` stroke centre at `size.height − w/2`; web `ListStyleTypeApplier.ts:4-6` emits the keyword, no `markerText` consumer (bake header lines 31-35 "Blink's own ::marker is the
  better oracle" — falsified on this cell); natives compose `outside` markers as a leading inline box (`ComponentRenderer.kt:4021ff` Row, `ComponentRenderer.swift:4459-4470` HStack — "STILL DEFERRED B-RC3 part 3");
  `counter-style-bake.mjs` `DYNAMIC_SIGNAL_RX` (~:58) bails on `@counter-style|<script`.
- Fix: T1 `KOREAN_HANGUL_FORMAL` + arms + new `lists/KoreanHangulFormal.kt` byte-parallel with Swift (css-counter-styles-3 §6.3); T2 paint-only clamp of the stroke centre inside the box for BOTTOM/END/`doubleGeom`
  (band of a 0-tall box = [0,w)); T3 web consumes `meta.markerText` for the range-limited additive styles only (`armenian`, `upper/lower-armenian`, `georgian`, `hebrew`) via the css-lists-3 §3.3 `<string>` form
  and amends the bake header; T5 hang `outside` markers in the margin area on both natives (custom Layout at x = −(markerWidth+gap), reporting the item's size only — css-lists-3 §3.5) as a device A/B that
  records the installed `base.apk` sha1 / `.app` hash against the build (standing constraint; include-direction A/Bs record it too);
  T7 lift `@counter-style` out of `DYNAMIC_SIGNAL_RX`, parse author descriptors into the PREDEFINED table shape and run the §3 algorithms, keep `<script>` as a bail (tag the valid cssom tests `requires-script-mutation`).
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListStyleConfig.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListStyleExtractor.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/ListStyleApplier.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/lists/KoreanHangulFormal.kt` (new) and `lists/ListMarkerRow.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt` (paint-only clamp; the broad `SizingApplier.kt` height variant is NOT staffed)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/lists/ListMarkerRow.swift`
- own: `runtimes/web/src/engine/lists/ListStyleTypeApplier.ts`
- own: `runtimes/web/src/renderer/NodeRenderer.ts`
- own: `tools/titan/counter-style-bake.mjs`, `tools/titan/counter-style-table.mjs` (+ `counter-style-bake.test.mjs`)
- own: the Compose/Swift/web tests for the above (`ListStyleExtractorTest`, `ListStyleApplierTest`, a Compose paint test for the 0-tall border band, `ListMarkerRow` layout pins, a web markerText vitest)
- Seams: `ComponentRenderer.kt` `RenderListItemMarker :4021ff` and `ComponentRenderer.swift` `markerPlacement :4405-4480` (T5 patches); `ComponentRenderer.tsx` if the `<string>` marker needs `meta` at the
  web renderer seam (T3 patch). **Adjudication A ships in THIS lane's PR (plan-skeptic-1 C3, reversing the earlier hand-off)**: the one-line re-add of `armenian/css3-counter-styles-008` to
  `NATIVE_FONT_PARITY_REFUSED_TESTS` (`inject-wpt-block.mjs:1308`, consulted at `:1520`; the 155-vs-158 px measurement in the line's comment) is made by L6 in the L12-owned file
  AFTER L12 has merged (L12 told) — its reason is this lane's T3 web fix, so it lands with the lane that motivates it, not three landing steps earlier. Denominator effect
  (plan-skeptic-0 §8.6): `wave51-fix css-counter-styles/armenian/css3-counter-styles-008 ios f 0.9420` and `android f 0.9423` are SCORED today (only -007 is
  `native-font-parity`-excluded); the re-add stamps both, so the closing gate prints them under `UNMEASURED NOW (scored before, not now — excluded or missing)`
  (`score-gate.mjs:185`) — 0 on pass, −1 on each native denominator (1363 → 1362), and unmeasured-now = 19 + 2 = 21 (§7).
- Verification owed: T1 marker table transcribed from `KoreanHangulFormalTests`; T2 paint test (100×0 box, `border-bottom:3px solid` → ink rows [0,3), none at y<0; `MulticolFloatStripRefRowsTest` untouched);
  T3 (markerText + armenian → `list-style-type: "10000. "`, no markerText → keyword); T5 layout pin (item content x unchanged; marker.right+gap == item.left); T7 bake pins (`pad 3 "0"` → `001.`,
  `cyclic; symbols A B C; prefix "(" suffix ")"` → `(A)`, `negative "(" ")"` → `(3).`, `<script>` still declines); census (`counters-and-multicol.census.json`): T1 1 test, T2 3 tests, T3-narrow 6 tests,
  T5 127 items / 26 tests, T7 26 tests; PNG replay: the ref's `10000.` row wrap, `001.` rows at 20-px pitch, counter-suffix row 1 ink at x 46–58 + 64–87.
- Flips: armenian-008 web (HIGH); -008 ios/android → `scoreExcluded` by adjudication A (not a flip: −2 on the native denominator, 0 on pass — stated so the closing gate's
  unmeasured-now = 21 is expected, not diagnosed); pad/prefix-suffix-invalid ×3 + negative-invalid ×2 = +8 (MED; needs L5 F-E landed; falsified if the bake's pad/negative/prefix semantics differ from Blink);
  counter-suffix ios (MED — T5 moves 8 rows 18 px onto the ref); counter-suffix android stays f (T1 → ≈0.91; RTL padding-right offset is upstream, (c⁴)). T2: no flip claimed — the gate must
  show the band MOVED to rows 161–163 / 171–175 (a "no change" reading means the clamp did not paint).
- At risk: T3 `armenian-006/007/009 web P 0.9950/0.9829/0.9971`, `css-lists/content-property/marker-text-matches-armenian/-georgian web P 0.999`; T5 thin natives `counter-list-item-slot-order 0.9517/0.9523`,
  `counter-reset-reversed-nested 0.9551/0.9509`, `change-list-style-type-002 0.9547/0.958`, `all-prop-001 0.964/0.9641`, `css-ui/appearance-auto-details-list-item 0.9676/0.9679`, `css-cascade/scope-pseudo-element android 0.9746`;
  T7 `broken-symbols ×3 P`, `descriptor-suffix ×3 P 0.9533/0.9596/0.9593` (thin), `marker-text-matches-circle/disc/square ×3 P ≥0.989`; T2 `css-display/display-contents-dynamic-generated-content-fieldset-001 android P 0.9981` (moves toward the ref).

### L7 · static-position (effort M) — brief `static-position.md` §3/§4 T1–T3 (T4 deferred)
Queue: 0(j) "Out-of-flow static position — 7 of the 35 wrong cells, all three platforms, the single largest family".
- Targets: T1 Android css-grid §9.2 overlay aligns a 0×0 placeable — `wave51-fix css-grid/abspos/grid-abspos-staticpos-align-self-center android P 0.9808` (mark +25 px),
  `css-grid/abspos/grid-abspos-staticpos-align-self-end android P 0.9704` (+50), `css-grid/abspos/grid-abspos-staticpos-align-items-self-end android P 0.9715` (−50: no SELF_END arm),
  13 more (16 cells, web/iOS 1.0000); T2 iOS flex static position anchored at the PADDING-box corner, aligned in the padding box, `align-items`
  never read — `wave51-fix css-flexbox/abspos/position-absolute-containing-block-002 ios f 0.9373` (green (69,21) vs ref (76,76)), `css-flexbox/abspos/position-absolute-containing-block-001 ios P 0.9505`,
  `css-flexbox/abspos/flex-abspos-staticpos-justify-self-001 ios P 0.9921` (every mark (−2,−1));
  T3 `align-self: last baseline` never reaches a runtime — `wave51-fix css-grid/abspos/grid-abspos-staticpos-align-self-rtl-last-baseline-002 web P 0.9979` (+11 web cells 0.9954–0.9983; mark 14 px high).
- Mechanism: `GridRenderer.kt:366-378` overlay vs the RC1 branch `ComponentRenderer.kt:945-950` chaining `zeroFlowAnchor` (`CanvasRootHoist.kt:334-352`, reports 0×0 at `:834`); AlignItems fold
  `ComponentRenderer.kt:7931-7939` lacks SELF_END/SELF_START; iOS `ComponentRenderer.swift:1278-1284` anchor + `:2098-2112` passes padding-box extents to `AbsposStaticOffset.staticOffset` (`:75-99`, no padding
  term, unlike `AbsposGridStaticPosition.axisOffset :100-118`), `AbsposStaticPosition.resolveStatic :134-176` ignores `align-items`; `AlignSelfPropertyParser.kt:8-27` single keywords only → Generic; web
  `StyleBuilder.ts:274-320` drops static Generic; mobile `baseOf` maps no baseline keyword.
- Fix: T1 `CanvasRootHoist.LocalStaticPositionOwner` (default false; GridRenderer provides true around each overlay child; fifth argument to `rendersInFlowAsStaticPosition` — do NOT reuse `LocalHasPositionedAncestor`),
  add SELF_END/SELF_START arms (css-grid-1 §9.2, css-align-3 §6.1); T2 alignment container = CONTENT box, add the padding-start edge per axis, fall back to the container's `align-items` when the child has no
  `align-self` (css-flexbox-1 §4.1, css-align-3 §6.1/§6.2), pass `flexContentSize(style:vertical:)` at the call site; T3 parser accepts `normal`, `[first|last]? baseline`, `[safe|unsafe] <self-position>`, rejects
  `left|right`; typed `NORMAL/BASELINE/LAST_BASELINE`; web emits `'last baseline'` WITH the space (not `kebab()`); mobile `baseOf`: LAST_BASELINE → END, BASELINE → START (css-align-3 §4.2). No wire freeze
  (`schema/ir-v2.schema.json` has no AlignSelf enum). T4 (grid-area alignment container, RTL/vertical-rl, typed-raw `safe end`) is deferred — three of its four cells also need float packing.
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/grid/GridRenderer.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/CanvasRootHoist.kt` (additive flag only; L2 reads it)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/flexbox/AbsposStaticAlignment.kt`
- own: `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/grid/GridAbsposPartitionTest.kt`, `…/core/renderer/AbsposOverflowMeasureTest.kt`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/flexbox/AbsposStaticOffset.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/flexbox/AbsposStaticPosition.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/flexbox/AbsposStaticAlignment.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/grid/AbsposGridStaticPosition.swift`
- own: `runtimes/swiftui/Tests/StyleConverterRuntimeTests/AbsposStaticPositionTests.swift`, `AbsposStaticAlignmentTests.swift`, `AbsposGridStaticPositionTests.swift`
- own: `converter/src/main/kotlin/app/parsing/css/properties/longhands/layout/flexbox/AlignSelfPropertyParser.kt`
- own: `converter/src/main/kotlin/app/irmodels/properties/layout/flexbox/AlignSelfProperty.kt`
- own: `runtimes/web/src/engine/layout/flexbox/AlignSelfExtractor.ts`, `runtimes/web/src/engine/layout/flexbox/AlignSelfApplier.ts` (+ vitest)
- Seams: `ComponentRenderer.kt:927-950` (fifth argument — **OVERLAPS L2's `:911-948` RC1 `zIndex` hunk**: lines 927–948 are in both, inside the one `val itemModifier = if (…)` expression at
  `:921-948`; this lane cuts its Compose patch against the L2-APPLIED tree and names that tree in the patch header; landing order L2 then L7 — C6) and `:7931-7939` (fold arms);
  `ComponentRenderer.swift:2098-2112` (only the two `containerW/H` arguments — inside `positionedChildren`, which begins at `:2050`, the function L3's F3 patches; cut against the
  L3-APPLIED tree and name it; apply after L3).
  Do NOT widen `runtimes/web/src/core/renderer/StyleBuilder.ts` `applyGeneric` (L11 owns the file; its static-value drop is a deliberate parser-gap detector).
- Verification owed: pins 1–4 of the brief (`rendersInFlowAsStaticPosition(…, staticPositionOwned=true)` false on the verbatim `align-self-center` IR; iOS `staticOffset` on justify-self-001 == (2,1) and on
  containing-block-002 == (55,55) — today (0,0)/(47.5,0); parser `last baseline` typed, `left` null; web applier emits the space; `resolveCross(last baseline)` → END); the three Safe00x pins re-derived to the
  ref geometry, not deleted; census (`static-position.carriers.json`): T1 31 docs, T2 15 + 10, T3 30 Generic carriers; PNG replay: ref mark rows (42..91 / 67..116; (76,76)-(175,175); (30,34)-(37,39)).
- Flips: position-absolute-containing-block-002 ios (MED-HIGH; falsified if the Safe00x pins cannot be re-derived with a content-box container); T1 16 android cells → 1.0000 and T3 12 web cells → 1.0000
  (HIGH, picture-correctness); containing-block-001 ios 0.9505 → ~1.0, justify-self-001 ios → ~1.0, margin-001/002 + fallback-justify-content-001 ios row 1 only.
- At risk: every iOS cell of the 25 T2 docs — `flex-abspos-staticpos-align-self-safe-001/002/003 ios P 0.9937/0.9914/0.9981`, `abspos-autopos-* ios P 0.9974/0.9904`, `css-flexbox/abspos/position-absolute-006..010 ios P 0.9974`,
  `css-position/position-absolute-center-003/004 ios P 0.9973`; the 31 T1 Android docs (START-aligned ones must not move); the 30 T3 carriers on mobile — `last baseline` → END moves the mark PAST the ref
  (y 42 vs 34) until T4 lands: pixels move, truth does not improve there yet (watchlist).

### L8 · vertical-wedges (effort L) — brief `vertical-wedges.md` §3 M-A/M-B/M-C/M-E/M-G (M-F folded into L3)
Queue: 1 "Vertical-wedge residuals" ("the ch-units-vrl ×8 Android cells"; (b) "direction-upright-002 — the 'why 2805px?' diagnostic is DONE; three named modelling gaps"); 0(k) sizing member T12.
- Targets: `wave51-fix css-writing-modes/forms/input-range-zero-inline-size ios f 0.9747 · android f 0.9743` (a 224×70 slider group where the ref shows ONLY a 16×22 green L-shaped stub at (16,66) — the ref is NOT blank, and the stub
  is ink the post-fix natives must still paint (plan-skeptic-0 §8.3; native-near-misses T12 has it right): the UA widget mount paints the fixed 129×21 atom
  ignoring the IR's 16×0 box); `wave51-fix css-writing-modes/ch-units-vrl-005 ios f 0.9159 cF · android f 0.9103` (+006/007/008: green td 6×60/6×55 vs 63×63, blue 64×60 vs 63×63, orange 60×25 / 55×72 vs 120×120 —
  ch measures SF/Roboto not Inter (12/11 vs 12.6), ch ignores the upright inline axis (should be 24), the upright run declines on an unbounded budget); `ch-units-vrl-003/-004` additionally need `<col width:5ch>` harvested.
- Mechanism: `ChUnitMetrics.kt:50-68,91-92` / `ChUnitMetrics.swift:146-147` (platform default face vs the label's Inter at `ComponentRenderer.kt:6638` / `.swift:5255`); `ChUnitMetrics.swift:107` `.horizontal`,
  `ChUnitMetrics.kt:103` `measureText` — call sites `StyleApplier.kt:805-828`, `StyleBuilder.swift:357-386` pass no writing mode (css-values-4 §6.1.1 wants the inline-axis advance); `VerticalRunIntrinsics.kt:234-243` →
  `VerticalTextFlow.kt:275` null decline, `VerticalTextFlowLayout.swift:103-122` nil proposal (css-writing-modes-4 §7.3.1 fallback: ancestor max-block-size, else the ICB extent already exposed by `WptCaptureMode.kt:214` /
  `WPTCaptureMode.swift:146`); `TableBoxTree.kt:211-212` / `.swift:194` drop col/colgroup without harvesting `Width`; `UAWidgetsGeometry.kt:75-76,179` / `.swift:79-80,182` fixed atom.
- Fix: M-A bottom the default-family measurement out at Inter (a device A/B — it changes 10 passing cells' ch boxes — that records the installed `base.apk` sha1 / `.app` hash
  against the build, per the standing constraint); M-B `inlineAxisUpright` flag (vertical + upright → round(ascent)+round(descent)); M-C §7.3.1 fallback budget (nearest definite block size / max-block-size,
  else the ICB), keep the decline only for glyphless runs; M-E harvest col/colgroup `Width` as a column min/pref width (css-tables-3 §2.1/§3.2); M-G `UAWidgetsResolve.resolve` returns nil when the used Width or
  Height is 0px and sizes the atom to a declared px box otherwise (css-sizing-3 §5.1).
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/ChUnitMetrics.kt` (+ `ChUnitMetricsTest`)
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/VerticalRunIntrinsics.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/VerticalTextFlowLayout.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/text/VerticalTextFlow.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBoxTree.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/widgets/UAWidgetsResolve.kt`, `…/widgets/UAWidgetsGeometry.kt`, `…/layout/UAWidgetIntrinsics.kt`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/ChUnitMetrics.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/StyleBuilder.swift` (the ch block `:349-387`)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/VerticalTextFlowLayout.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/writing/VerticalTextFlow.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBoxTree.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/widgets/UAWidgetsResolve.swift`, `…/widgets/UAWidgetsGeometry.swift`
- own: the matching tests under `runtimes/compose/src/test/…` and `runtimes/swiftui/Tests/…`
- Seams and hand-offs: `ComponentRenderer.kt:7622-7637` (budget hand-off) and `ComponentRenderer.swift:4969-4987` (pass `budgetOverridePx`) — patches; **`StyleApplier.kt:805-830` `buildSpacingContext`
  (derive `inlineAxisUpright` from the merged WritingMode + TextOrientation and pass it to `ChUnitMetrics.measure`) SHIPS IN THIS LANE's PR, not as a hunk to L4** (plan-skeptic-1 C4:
  `ChUnitMetrics.kt:38` is `fun measure(fontFamily: FontFamily?, fontSizePx: Float): Float?` today, and `StyleApplier.kt:826-828` calls it with two arguments — a three-argument
  call delivered to L4 cannot compile there). Compile-safe at every step, the simplest form: (1) L8 adds the parameter WITH A DEFAULT — `fun measure(fontFamily: FontFamily?,
  fontSizePx: Float, inlineAxisUpright: Boolean = false): Float?` — so the existing two-argument call and every other caller (`ChUnitMetricsTest` included) compile unchanged;
  (2) in the SAME PR L8 edits `buildSpacingContext` in L4's file to derive and pass the flag (L4 has merged by then — §4 step 3 vs step 5 — and is told; the two regions are
  250 lines apart, so no textual conflict). If `buildSpacingContext(config: StyleConfig, …)` cannot read the merged `WritingMode` + `TextOrientation` from `StyleConfig`
  (plan-skeptic-1 §4 could not verify that it can), L8 adds a defaulted `inlineAxisUpright: Boolean = false` parameter to `buildSpacingContext` itself and its callers pass it —
  still one PR, still green at every commit.
  The baked-guard hunk (M-F) belongs to L3.
- Verification owed: pins 1–8 of the brief (`cacheName(null)` → the Inter key; `measure(inlineAxisUpright=true)` = ascent+descent; `uprightColumnIndices("00000", 24, budget 568)` one column of five; `orthogonalBudget(null, 568)`
  = 568, `(16, 568)` = 16; iOS layout plans with `budgetOverridePx`; col-width harvest → 120; `resolve(16×0)` → nil); census (`vertical-wedges.census.json`): `"u":"CH"` 74 docs / 18 with no FontFamily, M-C 15 docs
  (all failing today), M-E 5 docs, M-G 1 doc; PNG replay: web 005/006's picture (P 0.9578) is the target for the natives.
- Flips: input-range-zero-inline-size ios+android (MED-HIGH; falsified if the flex containers then collapse differently from the 16×0 boxes); ch-units-vrl-005..008 ×2 (MED — falsified if the upright glyph line box is
  not 24 px on the natives or iOS `colorFailed` persists); -003/-004 ×2 (MED-LOW, needs M-E); -001/-002 natives 0.84 → ≈0.925 no flip.
- At risk: M-A moves every undeclared-family ch box 55/60 → 63 — `css-text/hyphens/hyphens-auto-001 ios/android P`, `hyphens-none-shy-on-2nd-line-001 ios/android P`, `hyphens-out-of-flow-002 ios P`,
  `hyphens-punctuation-001 ios P`, `hyphens-span-002 ios/android P`, `css-text-decor/text-decoration-inset-004 ios/android P` (10 cells); M-E `css-tables/border-collapse-dynamic-col-001 android P 0.9812` (+ web P);
  `css-values/ch-unit-001 ios/android f 0.83` moves (M-A carrier, PNGs not opened). Web ch-units 001/002/007/008 fails are Chromium-vs-ref disagreements (inferred) — no web file is touched.

### L9 · inline-run-wall (effort M–L) — brief `inline-run-wall.md` §4 F1–F5
Queue: 4 "Inline-run wall, corrected map" — (c) "block-ellipsis-032 — the hanging-whitespace ring LANDED … pending the gate"; (a) "needs a device A/B, never a blind lift". Correction the lane carries into
the BACKLOG text: "Compose never paints a block-ellipsis marker on any clamp in the corpus" — the 'ellipsised' half of 4(c) is refuted.
- Targets: `wave51-fix css-text/hanging-punctuation/hanging-punctuation-inline-001 android f 0.9495` (the `」` span stacked as its own black line: ring refuses `member-prop:HangingPunctuation`; ios P 0.9756 same wrong render);
  `wave51-fix css-overflow/line-clamp/block-ellipsis-032.tentative android f 0.9397` (fold landed, NO "…" in any box: `placeholderOverflow` returns `declared` = Clip) and `ios P 0.9577` (only "This text is": the Swift ring
  has no glyphless arm and refuses `member-prop:WhiteSpace`); `wave51-fix css-overflow/line-clamp/block-ellipsis-025 ios f 0.9495` (34ch word + marker on line 4: clamp-unaware pre-break + `.fixedSize(horizontal:)`);
  `wave51-fix css-text/hyphens/hyphens-manual-inline-012 android f 0.9419` (16-glyph word broken every 7 glyphs, no hyphen: `PreBreakPipeline` discards the materialised soft-hyphen lines, Minikin ignores U+00AD).
- Mechanism: `InlineSpanRing.kt:197ff` / `InlineSpanRing.swift:158,258`; `InlineRunFlow.swift:725` (whitespace-only text = glyph member; Kotlin has the arm at `InlineSpanRing.kt:201/299/318`);
  `ComponentRenderer.kt:7760-7804` `placeholderOverflow` (`:7801-7803` returns `declared`) + `TextStyleApplier.kt:1574` (Clip default) while the config path `LineClampApplier.kt:95` already says Ellipsis;
  `ComponentRenderer.swift:4744-4799` pre-break, `:5076-5078` fixedSize, `:5071` lineLimit + `LineClampApplier.swift:14` truncation; `PreBreakPipeline.kt:156-161` identity unless unbreakable overflow.
- Fix: F1 admit `HangingPunctuation` as an inert member (both rings; css-text-3 §8.3, parse-only on both natives); F2 port the glyphless ring to Swift (`WhiteSpace` preserving keywords + `BackgroundColor` when glyphless —
  changes a fold decision on a PASSING host: stage as the 4(a) device A/B, which records the installed `base.apk` sha1 / `.app` hash against the build per the standing constraint); F3 `placeholderOverflow` → `Ellipsis` for a fixed-count `LineClamp` with an un-suppressed marker on a soft-wrapped run, gated on
  `!ruleBSoftWrap` (not `!wrapConfig.softWrap`) so rule-B pre-broken runs keep Visible (css-overflow-4 §5.1/§4.2 — the wave-39 landmine); F4 clamp-aware `GreedyLineBreaker.lines` on both twins (drop trailing words on line N
  until line+"…" fits, else "…" alone); F5 `PreBreakPipeline` fires when a soft-hyphen break was TAKEN under `manual` (css-text-3 §5.3).
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineSpanRing.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/inline/InlineRunFold.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/PreBreakPipeline.kt`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/wrapping/GreedyLineBreaker.kt`
- own: `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/PlaceholderOverflowMarkerTest.kt`, `…/core/renderer/LineClampUnderPreWave39Test.kt` (rewrite `:155`, do not delete), `…/typography/inline/InlineRunFoldTest.kt`, `…/typography/wrapping/PreBreakPipelineTest.kt`, `GreedyLineBreakerTest.kt`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/inline/InlineSpanRing.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/inline/InlineRunFlow.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/GreedyLineBreaker.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/typography/TypographyApplier.swift`
- own: `runtimes/swiftui/Tests/StyleConverterRuntimeTests/InlineSpanRingTests.swift` (flip `:173-179` into the two-case glyphless pin), `InlineRunFlowTests.swift`, `GreedyLineBreakerTests.swift`
- Seams: `ComponentRenderer.kt:6973-6981` (call site threading `!ruleBSoftWrap`) and `:7760-7804` (the function); `ComponentRenderer.swift:4744-4799`, `:5071`, `:5458ff` — patches. Never touched: `scrolling/LineClampCap*`, `LineBoxCensus*`, the web runtime.
- Verification owed: the brief's §7 pins (verbatim hanging-punctuation wire folds to `字字字字」`; glyphless `PRE_WRAP`+red admits with `background = red`, glyph member refuses, `NORMAL` refuses even glyphless; bare `line-clamp:1`
  soft-wrapped → Ellipsis, pre-broken → Visible; 025 words at 32.5ch clamp 4 → 4th line `"…"`; -012 text at 8ch → `fired` with `Deoxy-\nribonu-\ncleic`); census (`inline-run-wall.census.json`): F1 1 member, F2 3 members
  (all in 032), F3 34 `LineClamp` tests (20 soft-wrapped, 19 P on Android), F4 the same 34 on iOS (33 P), F5 17 U+00AD tests (9 Android P); PNG replay: web's two-line `字字字/字」` shape, the ref's "…" placements.
- Flips: hanging-punctuation-inline-001 android (MED-HIGH; falsified if Minikin breaks before `」` — LB13); block-ellipsis-032 android (MED-LOW, thin 0.95–0.97; falsified if StaticLayout puts "…" past col 28);
  block-ellipsis-025 ios (MED; falsified if SwiftUI double-marks); hyphens-manual-inline-012 android (MED; falsified if the 8ch box fits only 7 Minikin glyphs — the `ch` divergence L8 owns); 032 ios P 0.9577 → ≈0.98.
- At risk: the 19 Android-passing soft-wrapped clamp cells under F3 (`block-ellipsis-001…031`, lowest 031 0.9779, 004–006 0.978–0.980 — falsifier: any `pre`-less host is rule-B pre-broken without the gate → collapses to 1 line);
  the 33 iOS-passing line-clamp cells under F4 (lowest `discard-multicol-001/002/004 ios 0.9606–0.9613`); 9 Android-passing soft-hyphen carriers under F5 (`hyphens-manual-011/012/013`, `hyphens-manual-inline-011 0.9739`,
  `block-ellipsis-014/-028`); `hanging-punctuation-inline-001 ios P 0.9756` and `block-ellipsis-032 ios P 0.9577` (fold decisions change on passing hosts — device A/B by the standing 4(a) rule).

### L10 · flex-nowrap-gaps (effort L) — brief `runtime-bugs-and-gaps.md` §3/§4 items 1, 3, 4, 5, 6(c)
Queue: 7 "css-gaps residuals" — (b) "background-clip-content-box-002 iOS 0.9992 cF F: a decoded .percent flex-basis no nowrap path reads", (a′) "up to 1 px of band edge on non-integral geometry", (e³) "027 stays open and unexplained";
6(c) "reduced to the REACH question" (commit the probe fixture). 6(b)/6(c′)/6(d)/7(a)/7(f) are CLOSED by the gate (wave50-final = wave51-fix on every carrier) — no code.
- Targets: `wave51-fix css-backgrounds/background-clip-content-box-002 ios f 0.9992 cF` (both `flex-basis:50%` items 0 wide — the iOS PNG shows a ~5 px green bar at x 16–20, the `.left` item's border (`runtime-bugs-and-gaps.md` row 16),
  then RED to x 115 where the ref is green 100×100: `CSSFlexLayout.swift:262` ignores `FlexClaim.basisPercent`); `wave51-fix css-gaps/flex/flex-gap-decorations-027
  ios f 0.9030 · android f 0.9085` (after L2's M1 removes the 200-px shift: iOS a white hole where "One" should be and "Six" overflowing by 150 — a negative flex margin is a paint offset only, `MarginApplier.swift:108-136`, and
  `CSSFlexLayout` advances by the measured size; Android additionally squeezes the overflowing shrink-0 items — `Row` + `spacedBy` clamp, predicted 183/193/193 = measured); `css-gaps/flex/flex-gap-decorations-008 android P 0.9646`
  (same squeeze, iOS P 0.9996); `045 ios P 0.9931`, `046 ios P 0.9998 / android P 0.9960` (half-pixel band edges; Kotlin integer share +1).
- Fix: (1) `basis = claim.basisPx ?? claim.basisPercent.flatMap{ available.map{ a in a*$0/100 } } ?? ideal.main` (css-flexbox-1 §9.2 step 3.A, §7.2.3); (3) iOS main-axis margin deltas in `FlexClaim` → `outerDelta` in `ItemInput`,
  sum outer sizes, `cursor += s + outerDelta + gap`; (4) Compose `FlexNowrapLine.kt` custom Layout — **GATED: engage only when a child carries a negative main-axis margin or Σ(outer hypothetical)+gaps > container with every
  shrink factor 0; every other nowrap row/column stays on `Row`/`Column`** (the brief's own census is 1 carrier for M3; an ungated replacement would put every Android nowrap flex cell at risk); (5) pixel-snap band rects in
  both painters and align the Kotlin share distribution with Swift's `leftover/n` before rounding; (6c) commit `fixtures/properties/effects/box-shadow-reach.json` with an `_expect` on the bottom-most shadow row.
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/flexbox/CSSFlexLayout.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/core/placement/ItemPlacement.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/MarginApplier.swift`
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/GapDecorationBands.swift`, `…/columns/GapDecorationSegments.swift`, `…/columns/GapDecorationsPainter.swift`
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/flexbox/FlexNowrapLine.kt` (new) and its JUnit layout test
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/GapDecorationBands.kt`, `…/columns/GapDecorationPainter.kt`, `…/columns/GapDecorationHook.kt`
- own: `fixtures/properties/effects/box-shadow-reach.json` (new)
- own: `CSSFlexMathTests` / `FlexPercentBasis*` tests under `runtimes/swiftui/Tests/` and `runtimes/compose/src/test/…/layout/flexbox/`, band-snap pins on both natives
- Seams: `ComponentRenderer.kt:2213` and `:2508` (the two `Row` routes → the gated `FlexNowrapLine`) — patch. NOT owned: `runtimes/compose/…/spacing/MarginApplier.kt` (L4; read the margin config, do not edit),
  `runtimes/swiftui/…/Renderer/FixedHoist.swift` (L3; the 6(e) ICB A/B is not staffed), the three canvases (L2 owns M1).
- Verification owed: pins 1, 3, 4, 5, 7 of the brief (`basisPercent 50 ×2, available 100` → `[50,50]`; six 50-px items gap 10 `outerDelta [−150,0,…]` → offsets `[0,−90,−30,30,90,150]`; the JUnit layout row 200-px nowrap six
  50-px shrink-0 → x `[0,60,120,180,240,300]` widths all 50, mutation to `Row` → `[…,200,200]` widths `[…,20,0,0]`; band rects `[73,78)`/`[134,139)` identical on both natives); census (`runtime-bugs-and-gaps.census.json`):
  7(b) 4 tests, M2 2 tests, M3 1 test, 7(a′) 045/046 only — plus the NEW census the gate owes: every nowrap flex parent whose children the gate predicate would route to `FlexNowrapLine` (must be ≤ the 2 carriers).
- Flips: background-clip-content-box-002 ios (HIGH; falsified if red remains → the items are not `CSSFlexLayout` subviews); 027 ios (MED-HIGH) and android (MED) — both need L2's M1; 008 android → ≥0.999 (no flip); 045/046 → ≥0.999.
- At risk: `css-flexbox/abspos/flexbox-abspos-child-002 ios P 1.0000` (if abspos children are layout subviews); `css-position/position-absolute-dynamic-relayout-003 ×3 P` (M2 carrier, no ink — verify by raster);
  `css-gaps/flex/flex-gap-decorations-025` and `css-values/calc-size/calc-size-flex-007` (wrap path, must stay untouched); every other css-gaps cell for the band snap (inert by construction — movement = regression;
  the gate runs `--movers 0.005` over `css-gaps`); every Android nowrap flex cell if the `FlexNowrapLine` gate is wider than stated.

### L11 · all-reset-postload-colour (effort M) — brief `colour-not-reaching-text.md` §4 F1, F2 (F3 not staffed) — **correctness, 0 flips; may lower `all-prop-001`**
Queue: 0(m) "A computed colour never reaches the text run — 3 cells" (re-worded by the brief: only ONE of the three is runtime code; the other two are extractor-side).
- Targets: `wave51-fix css-cascade/all-prop-initial-color android P 0.9993 · ios P 0.9989 · web P 0.9783` (the one line paints BLACK where the ref is GREEN: the IR carries `[All INITIAL, Color green]` in source order and every
  runtime treats `All` as "drop everything" — `ComponentRenderer.kt:1134-1137`, `GlobalExtractor.swift:41-49` called at `ComponentRenderer.swift:340` on the MERGED list, web `AllApplier.ts:4-6` + `StyleBuilder.ts:222` inserting
  `all` LAST so the browser resets `color` and `font-family`); twins `all-prop-{inherit,revert,unset}-color ×3`; `wave51-fix selectors/invalidation/nth-child-of-class android P 0.9965 · ios P 0.9976 · web P 0.999` (lines 5 and 7
  black where the ref is green: `post-load-extract.mjs:359 color: { repairOnly: true }` cannot INTRODUCE `color` on the live matches p[4]/p[6]).
- Fix: F1 order-aware `all` reset, byte-parallel on the three: find the LAST `All` on the OWN list, drop own[0,i) except `Direction`/`UnicodeBidi` (css-cascade-4 §3.1), keep own(i,end]; inherited channel: INITIAL drops it,
  INHERIT/UNSET/REVERT/REVERT_LAYER keep it (§7.3); remove the `All` entry — Compose as a pure `global/AllReset.kt` applied BEFORE `mergeInherited`, iOS `applyingAllReset(own:inherited:)` before `InheritedText.merge`, web
  `applyAllReset` pre-filter in `_dispatch.ts` and `all` emitted as the FIRST key. F2 the post-load overlay introduces `color` when `props.color` is undefined AND the record's computed colour differs from the PARENT record's
  (css-color-4 §3.2 inheritance ⇒ a difference proves a cascaded declaration); skip `a, button, input, select, textarea, mark`.
- own: `runtimes/compose/src/main/java/com/styleconverter/runtime/global/AllReset.kt` (new) + `runtimes/compose/src/test/java/com/styleconverter/runtime/global/AllResetTest.kt` (new)
- own: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/global/GlobalExtractor.swift`, `…/StyleEngine/global/GlobalConfig.swift` + `runtimes/swiftui/Tests/StyleConverterRuntimeTests/AllResetTests.swift` (new)
- own: `runtimes/web/src/engine/global/AllApplier.ts`, `…/global/AllExtractor.ts`, `…/global/_dispatch.ts`
- own: `runtimes/web/src/core/renderer/StyleBuilder.ts` (L7 must not widen `applyGeneric` here)
- own: `runtimes/web/tests/global/globalPhase10.test.ts`
- own: `tools/titan/post-load-extract.mjs`, `tools/titan/post-load-extract.test.mjs`
- own: `fixtures/combinations/all-then-color.json` (new; baselines on all three after LOOKING)
- Seams and hand-offs: `ComponentRenderer.kt:1134-1137` (replace with the `AllReset` call before `mergeInherited`) and `ComponentRenderer.swift:330-345` (move the call off the merged list) — patches; the rewrite of
  `ContentsUnboxingTests.swift:175-183` (`testAllResetDropsEveryOtherDeclaration` → "drops declarations BEFORE `all` only") **SHIPS IN THIS LANE's PR, in the same commit as
  `GlobalExtractor.swift`** — L11 edits that one test function in L3's file after L3 has merged (L3 told; L3 does not touch `:175-183`). Why (plan-skeptic-1 C5): the pin today
  feeds `[All INITIAL, Width 160, BackgroundColor blue]` and asserts `applyingAllReset(...).isEmpty`; under the order-aware rule `Width` and `BackgroundColor` FOLLOW `all` and are
  KEPT, so the existing pin goes red the moment `GlobalExtractor.swift` changes, and the rewritten pin is red until it does — a hunk "delivered to L3" has no red-free landing
  order. Rule: the Catalyst suite is green at every step — before L11 the old pin and the old extractor agree; L11's single PR flips both together. L3 merges first (§4).
- Verification owed: pins 1–5 and 7 of the brief (own `[All INITIAL, Color green]` + inherited `[Color red]` → exactly `Color green`; `[Color red, All INITIAL]` → no Color; Direction/UnicodeBidi exemption; INHERIT keeps the
  inherited channel; last `All` governs; web `Object.keys(styles)[0] === 'all'`; converter order premise `{all:initial, color:green}` → `[All, Color]`; post-load `overlayComputedOnComponent` introduces on parent-differs,
  not on equal, repairs existing, skips `a`); census (`colour-not-reaching-text.census.json`): 9 `All` tests / 28 components (each listed with before/after type lists), 277 post-load tests / 83 colour candidates / 17 UA-tag
  tests; PNG replay: the ref's green line (646 px) / green lines 5 and 7.
- Flips: none. `all-prop-*-color android/ios 0.9989–0.9993 → ≈1.000` (12 cells, HIGH that they rise); web `all-prop-initial-color 0.9783` rises but stays face-divergent (UA-initial serif vs Inter — unresolved, §9 of the brief);
  `nth-child-of-class` + 7 twins rise by the green lines; `nth-child-of-has ios f 0.9371` is NOT predicted to flip.
- At risk (P→f): **`css-cascade/all-prop-001 web P 0.9689 / ios P 0.964 / android P 0.9641`** — the §3.1 exemption newly applies `Direction RTL` + `UnicodeBidi BIDI_OVERRIDE` to un-baked "321" text 0.014 above the bar (MED;
  open its PNG at the gate); `css-display/display-contents-button/details/fieldset ×3 0.968–0.977` (LOW; the RC6 strip should neutralise `All` first — verify on iOS); `all-prop-002 ios/android 0.970`; `all-prop-initial-visited ×3`;
  the 17 UA-tag post-load tests if the F2 tag skip is omitted (`currentcolor-004`, `has-visited`, `any-link-attribute-removal`, …).

### L12 · instrument-and-calibration (effort M) — briefs `absence-only-denominator.md` §4 A/B; `page-padding-overrun.md` §4 Fix B; BACKLOG "Instrument decisions pending" B10 — **instrument: lowers the score by construction**
Queue: 0(n) "The absence-only class — a DENOMINATOR problem, ~67 cells, needing a decision rather than a fix" ("whether these become `scoreExcluded` … or stay counted with the caveat documented");
"## Instrument decisions pending" → "The blank-capture guard — DECIDE IT, and key it on UNIFORM COLOUR, not on alpha"; → "The erased-reference body background — a CALIBRATION, not a render change"; 0(l)'s
"clip/mask reference-box pair (099 web, 028 Android — the mark displaced by exactly the 8 px padding on both axes)".
- Part A (scorer, S): DECISION A — EXCLUDE per cell with a named reason: stamp `wptPass = null`, `scoreExcluded = 'absence-only'` where `wptPass === true && semanticPresence.bCoveragePct < WPT_PRESENCE_REF_MIN_PCT` ("the pass a
  capture with no ink would also receive"; mechanism-free, reuses the instrument's own blank-ref floor at `inject-wpt-block.mjs:743`); never overwrite an existing stamp; fails on blank refs stay scored. **19 cells / 7 tests leave
  the pass column: web 7 / iOS 6 / Android 6** — the 3 olive `background-color-animation-in-body` cells are a positive assertion and stay counted (S6's 21 included them). DECISION B — the blank-capture guard becomes a TRIAGE
  stamp `captureUniform:[r,g,b,a]|null` (computed on the raw decoded capture before `padToCanvas`) + `blankCaptureVsInkedRef`, listed loudly in the inject summary; scoring UNCHANGED (all 33 scored blank-capture cells already
  fail via `presenceFailed` at `:905` — the BACKLOG's "no blank-capture guard anywhere" is wrong in substance); the exit-7 escalation is DECLINED with the reason recorded (it would have hidden the `counter-cjk-decimal` Compose
  fix as "newly measured" and made the gate permanently red on 15 known defects; the exact BACKLOG amendment paragraph is at the end of this lane — the orchestrator pastes it at ship time, C9). **`score-gate.mjs` is NOT changed** (plan-skeptic-1 C2): the standing constraint stands — "a cell whose `scoreExcluded` stamp was REMOVED between the two runs is
  NEWLY MEASURED, never lost" (`score-gate.mjs:122` `if (!a && b) { res.newlyMeasured.push(row); continue; }`; `isScored` at `:50` excludes any stamp). So a prev
  `absence-only` → cur scored-f cell prints NEWLY MEASURED, not LOST — a stamp is removed whenever the REF changes, and L12-B's own re-freeze can un-stamp a cell whose
  capture bytes are byte-identical; printing that LOST would be exactly the mis-report the constraint exists to prevent. The `absence-only-denominator.md` §4 item 3 /
  pin 5 LOST semantics are WITHDRAWN. A prev scored-P → cur stamped cell prints under the EXISTING heading `UNMEASURED NOW (scored before, not now — excluded or missing)`
  (`:185`) — that is where the 19 absence-only cells leaving the denominator appear; no new heading, no new list. If a later wave wants LOST semantics for un-stamped cells,
  the constraint text is amended in that PR with its reason — never a silent redefinition of the scorer of record.
- Part B (reference calibration, M): apply `tools/titan/results/wave50-B10/capture-browser-ref-body-background.patch` (moves `background` to `:where(html)` — the body half painted an opaque white box over `z-index:-1` children AND
  over an all-green root canvas: `css-cascade/initial-background-color ×3 f 0.5414` with captures solid green vs a WHITE frozen ref) AND page-padding Fix B (make `canvasFrameCss()` test-aware: when the TEST declares a body
  margin, inject `:where(html){margin:0}` + `:where(body){padding:0;…}` and leave the UA 8-px body margin in force — the condition WPT reftest pairs are authored under; undeclared tests keep the byte-identical sheet; guard the
  PNG height with `min-height: calc(100vh - 16px)` for the variant). ONE `CANVAS_REV` bump, re-freeze exactly the affected refs (B10's 8 rows: gaps-033/034/035/036/037, `hanging-punctuation-block-bound-001`,
  `css-tags-paint-order(-with-entry)`; the 3 all-green refs; Fix B's 10 pairs), sha1 every OTHER frozen ref against the previous freeze (the variant must not leak), and run the **Calibration-gate recipe** (capture-hash check:
  identical capture bytes → instrument-only flip). Rejected, recorded: restoring UA 8 px on both sides (moves 1285 docs).
- own: `tools/titan/inject-wpt-block.mjs`, `tools/titan/inject-wpt-block.test.mjs` (L6 makes its own one-line `NATIVE_FONT_PARITY_REFUSED_TESTS` re-add at `:1308` in L6's PR, after
  this lane has merged — C3; this lane does NOT carry it)
- own: `tools/titan/score-gate.mjs`, `tools/titan/score-gate.test.mjs` (ownership retained, **no semantic change to `diffRuns` / `isScored`** — C2; the only permitted addition is a
  committed-run replay pin in the test file asserting the CURRENT rule: stamped-now → UNMEASURED NOW, stamp-removed → NEWLY MEASURED)
- own: `tools/titan/capture-browser-ref.mjs`, `tools/titan/capture-browser-ref.test.mjs` (+ `CANVAS_REV` bump, the re-frozen refs under `tools/wpt/refs/…`)
- own: `tools/titan/README.md` ("The scorer idiom" sentence), `tools/titan/results/corpus-v6-18.json` (after the gate; criterion "+ per-cell absence-only exclusion (v6.18)"; the 19 listed by name in `_note`)
- Seams: none. Hand-offs IN: none — L6's `:1308` re-add lands in L6's PR after this lane merges (C3). Must not touch `post-load-extract.mjs` / `bidi-bake.mjs` semantics (they take the default sheet variant).
- Verification owed: pins 1–8 of the absence-only brief (`isAbsenceOnly` positive/negative rows incl. the olive `bCoveragePct 100` pass and the `calc-in-media-queries` blank-ref FAIL; never-overwrite; the `isNa ? [] :` source pin;
  `isScored` and `diffRuns` UNCHANGED (C2) — the replay pin asserts a stamped-now cell prints UNMEASURED NOW and a stamp-removed cell prints NEWLY MEASURED; `captureUniform` on synthetic 4×4 PNGs incl. `(0,0,0,0)`; the committed-run replay: applying the gate to a deep copy of the wave51-fix manifests stamps exactly the 19
  cells of `absence-only-denominator.census.json`); Fix B pins 4 of the page-padding brief (`canvasFrameCss({testDeclaresBodyMargin:true})` has no `margin` in the body half; default byte-identical; `bodyDeclaresMargin` true for the
  four css-masking tests, false for `block-ellipsis-001` and `display-contents-text-only-001`); B10's `zneg-ab-probe.mjs` / `canvas-css-ab.mjs` re-run on the re-frozen refs (the "28 of 42 rasterise differently" claim was never
  re-rasterised by a skeptic — do it now); census: 13 uniform refs / 19 absence-only cells / 73 uniform captures / 36 blank-vs-inked (`absence-only-denominator.census.json`), 19 body-margin pairs / 10 in the affected direction
  (`body-margin-census.json`).
- Predicted: A: `score-gate wave51-fix → adoption` = 0 gained / 0 lost / 0 newly measured / **19 unmeasured-now** / 0 movers; totals web 1215/1379 → 1208/1372 (88.05 %), iOS 1089/1369 → 1083/1363 (79.46 %), Android 1080/1369 →
  1074/1363 (78.80 %) (HIGH; falsified if any of the 19 shows non-zero `bCoveragePct` or unmeasured-now ≠ 19 on the L12-A-only adoption diff; at the CLOSING gate unmeasured-now = 21 because
  L6's adjudication A adds `armenian/css3-counter-styles-008 ios/android` — C3). B: `css-cascade/initial-background-color ×3 f → P` (MED); `calc-in-media-queries-001/002 ×3` stay f (honest);
  gaps-034/035 refs regain their rules (their flips then belong to L2's Fix A / L10 honestly); `column-span-during-transition-doesnt-skip ×3` stays P only with L1's F-B; paddingBox-1d ×3 0.9585 → 1.0000, paddingBox-1e ×3 → ≥0.999,
  contentBox-1d/-1e likewise (+12 cells degenerate → honest, no scorer flips); **`css-gaps/flex/flex-gap-decorations-033`: web f 0.9400 → P (+1, MED) and ios P 1.0000 / android
  P 1.0000 → f (−2)** — pre-declared here as the BACKLOG "Instrument decisions pending" paragraph pre-registers it ("033 web f → P (+1) and both natives P → f (−2), which is the
  honest direction"): after the re-freeze the ref carries the 10-px rules in 0-px gaps that the natives do not paint, and today's native P is a same-ink-both-sides pass on an
  erased ref, not a blank-vs-blank (033 is not in the absence-only 19). **Predicted P→f: 2 (033 natives, pre-registered, honest — plan-skeptic-1 C1).**
- At risk: the 19 by design (watchlist); ratchet cost documented (a repaired blank-ref FAIL moves to `absence-only`, never to P); Fix B: `CSS2/css21-errata/s-11-1-1b-005 web P 0.9947` / `-006 web P 0.9656` (their refs' in-flow prose
  gains the UA 8 px — L2's M1 changes the capture side in the same gate: attribute jointly), `css-anchor-position/anchor-position-circular ×3 P 1.0`, `css-position/absolute-pos-box-inside-fixed-pos-box-with-changing-height ×3 P 1.0`,
  `hypothetical-dynamic-change-003 ×3 P 1.0` (ref contents not opened; WPT validity says margin-insensitive); B10's two `css-tags-paint-order` rows (P 0.9791 ×3, `erased:false` — must not move).
- **BACKLOG amendment (Decision B) — the exact paragraph the orchestrator pastes into `docs/BACKLOG.md` "## Instrument decisions pending" at ship time, replacing the bullet
  that begins "**The blank-capture guard — DECIDE IT, and key it on UNIFORM COLOUR, not on alpha.**"** (lanes do not edit the BACKLOG, §0; plan-skeptic-1 C9 requires the
  declined form to reach the BACKLOG as an explicit amendment, not a lane note):

  > - ~~**The blank-capture guard — DECIDE IT, and key it on UNIFORM COLOUR, not on alpha.**~~ **DECIDED AND EXECUTED (wave 52, lane L12-A): a TRIAGE STAMP plus a loud
  >   listing; scoring UNCHANGED; the exit-7 capture-failure form DECLINED, reason recorded.** The predicate is keyed on UNIFORM COLOUR as this item required:
  >   `inject-wpt-block.mjs` stamps `captureUniform: [r,g,b,a] | null` on every `<platform>-ref` diff, computed on the raw decoded capture BEFORE `padToCanvas` (so the one
  >   fully transparent capture, `text-decoration-inset-025` Android, and a solid-white or solid-green one are the same class), plus `blankCaptureVsInkedRef` (uniform
  >   capture ∧ ref `bCoveragePct ≥ WPT_PRESENCE_REF_MIN_PCT`), listed in the inject summary as `blank-captures=<n>` and mutation-proved against the wave51-fix population
  >   (`tools/titan/results/wave52-plan/absence-only-denominator.census.json` `populations.blankCaptureVsInkedRef`: 36 rows / 15 tests — 33 scored, 3 already excluded —
  >   re-derived by plan-skeptic-1 with its own script). **Why not a CAPTURE FAILURE in the exit-7 family:** (1) the sentence "there is no blank-capture guard anywhere in
  >   `tools/` today" was wrong in substance — there is none by that NAME, but every one of the 33 scored blank-capture-vs-inked-ref cells ALREADY FAILS via
  >   `presenceFailed` (`inject-wpt-block.mjs:905`), so scoring is already correct on this class; (2) these captures are MEASUREMENTS of runtime defects, not pipeline
  >   failures — `composited-under-rotateY-180deg-preserve-3d` iOS+Android (queue 0(k), wave-52 lane L4 T5), `counter-cjk-decimal` Android (a Compose paint bug fixed in
  >   wave 50 and counted as a GAIN) — and an exit-7 guard would move them out of the denominator, so a fix prints as NEWLY MEASURED instead of GAINED and the gate is
  >   permanently red on 15 known, partly undiagnosed defects (queue 1(c)'s 9470-px blow-up among them): the pre-excused shape in reverse. Decision rule going forward:
  >   uniform-capture cells stay SCORED and FAILING; the stamp is for triage and feeds the standing "excluded tests whose platforms disagree" report. It does NOT address the
  >   blank-vs-blank PASS cells — those are 0(n), decided separately as the per-cell `absence-only` exclusion (Decision A: `wptPass === true ∧ bCoveragePct <
  >   WPT_PRESENCE_REF_MIN_PCT` → `scoreExcluded = 'absence-only'`; 19 cells / 7 tests in wave51-fix, web 7 / iOS 6 / Android 6, listed by name in
  >   `tools/titan/results/corpus-v6-18.json` `_note`). Evidence: `tools/titan/results/wave52-plan/absence-only-denominator.md` §4 and the census JSON beside it.

## 3. Seam-hunk registry (what the orchestrator applies, and where two lanes meet)

| seam file | hunk | lane | note |
|---|---|---|---|
| `…/core/renderer/ComponentRenderer.kt` | `:911-948` RC1 item `zIndex` | L2 | Appendix E step 8 |
| | `:927-950` fifth argument `staticPositionOwned` | L7 | **OVERLAPS L2's hunk** (C6): `:921-948` is ONE expression — `val itemModifier = if (hoistHostActive && CanvasRootHoist.rendersInFlowAsStaticPosition(component.properties, hoistHasPositionedAncestor, hoistHasTransformedAncestor, hoistHasClippingAncestor,)) { itemModifier.then(zeroFlowAnchor(…)) } else itemModifier`; L7's fifth argument lands inside the call (`:929-935`), L2's `zIndex` wraps the then-branch (`:940-948`); lines 927–948 are in BOTH hunks. L7 cuts its patch against the L2-applied tree and names that tree in the patch header; apply L2 then L7; re-run `AbsposOverflowMeasureTest` |
| | `:1134-1137` `AllReset` before `mergeInherited` | L11 | |
| | `:1886-1890` `BlockContainer` gate (b′, optional) | L4 | |
| | `:1983-2030` `LocalInheritedTransform` provider | L4 | one patch with the above |
| | `:2213`, `:2508` gated `FlexNowrapLine` routes | L10 | |
| | `:2781-2785` (+`:2800-2807`) `bakedPhysicalBox` | L3 | SOLE owner of the baked guard (L8 M-F and native-near-misses T4 folded here) |
| | `:4021ff` `RenderListItemMarker` outside-marker layout | L6 | device A/B |
| | `:6973-6981`, `:7760-7804` `placeholderOverflow` | L9 | |
| | `:7622-7637` vertical budget hand-off | L8 | |
| | `:7931-7939` AlignItems fold SELF_END/SELF_START | L7 | |
| `…/Renderer/ComponentRenderer.swift` | `:330-345` `applyingAllReset(own:inherited:)` | L11 | |
| | `:765`, `~:2050` overlay partition `positionedAtMulticol` | L3 | **hot region** with L7 |
| | `:2098-2112` two `containerW/H` arguments | L7 | **shares a function with L3** (C6): `positionedChildren` begins at `:2050` and `:2098-2112` is inside it. L7 cuts its patch against the L3-applied tree and names it; apply AFTER L3's partition patch; re-run `AbsposStaticPositionTests` |
| | `:4354-4361` `verticalBlockFlowZ2` baked guard | L3 | |
| | `:4405-4480` `markerPlacement` | L6 | |
| | `:4744-4799`, `:5071`, `:5458ff` pre-break / lineLimit / styledSpanText | L9 | |
| | `:4969-4987` pass `budgetOverridePx` | L8 | |
| `apps/web-harness/src/sdui/ComponentRenderer.tsx` | `<string>` marker via `meta.markerText` (if not reachable from `NodeRenderer.ts`) | L6 | only lane patching this seam |
| `tools/titan/extract-fixture.mjs` | `:905-921` oracle R2 · `:962-975` return value · `:1158-1203` collapse · `:1213-1218` specificity · `:1287-1299` filter · `:6294-6356` gate+merge · `:7095` frames · `:8308-8325` splitter · `:11028` li exemption | L5 | ONE ordered patch set; the only lane on this seam this wave |
| non-seam cross-lane hunks (REVISED — plan-skeptic-1 C3/C4/C5) | `StyleApplier.kt:805-830` — L8 edits L4's file in L8's PR, after L4 merges (defaulted `inlineAxisUpright` parameter on `ChUnitMetrics.measure`) · `ContentsUnboxingTests.swift:175-183` — L11 edits L3's file in L11's PR, after L3 merges, same commit as `GlobalExtractor.swift` · `inject-wpt-block.mjs:1308` — L6 edits L12's file in L6's PR, after L12 merges | L8 · L11 · L6 | "delivered as a hunk, applied by the owner before its PR" is WITHDRAWN for these three: a hunk whose pin and runtime change would be split across two PRs ships in the SAME PR as the runtime change; the later lane edits the earlier owner's file after that owner has merged, the owner is told in the lane note, and the JVM / Catalyst / node suite is green at every commit |

## 4. Landing order (differs from the rank; dependencies)

1. Commit `tools/titan/results/wave52-plan/` (the fourteen briefs, `PLAN.md`, `watchlist.txt`, `watchlist-check.mjs`, the two skeptic reviews) — at planning time `git status` showed
   the directory untracked (plan-skeptic-1 C9; "every artifact is committed the moment it exists"). Then the `wave52-open` gate (obligation #0) → 0/0/0/0 or its first divergence
   becomes item 1.
2. **L12-B first** (calibration): `CANVAS_REV` bump + re-freeze + Calibration-gate recipe on the UNMODIFIED tree, so every later render mover is attributable against the NEW refs (a render lane and a ref change in one
   gate are inseparable without the capture-hash check). L12-A (scorer stamp) may land at any time before the closing gate; its 19 print under the EXISTING `UNMEASURED NOW` heading, never LOST, and `score-gate.mjs` is not
   edited (C2). L12-B pre-declares gaps-033 natives P → f (−2, C1).
3. L1, L4 (S lanes; L4 owns `StyleApplier.kt`, edits its backface region only, and merges BEFORE L8 so L8's `buildSpacingContext` hunk lands on the merged tree — C4), L5 (its
   differential census gates F-D; F-E is a prerequisite for L6 T7).
4. **L2 before L7** (Compose RC1 branch: L7's `:927-950` patch is cut against the L2-applied tree — C6); L3 before L7 (iOS overlay partition: L7's `:2098-2112` patch is cut against
   the L3-applied tree); **L3 before L11** (L11 rewrites `ContentsUnboxingTests.swift:175-183` in its own PR on the L3-merged tree — C5); L2 before L10 (027 natives need M1).
5. L6 (after L12: its PR carries the `inject-wpt-block.mjs:1308` re-add — C3), L8 (after L4: its PR carries the `StyleApplier.kt:805-830` hunk — C4), L9 — device-A/B-shaped changes
   (T5 outside markers, F2 glyphless ring, M-A face change) are staged behind the closing gate with pins, never blind-lifted, and **every device A/B — include-direction or
   exclude-direction (L4 b″, L6 T5, L8 M-A, L9 F2) — records the installed `base.apk` sha1 / `.app` hash against the build it claims to test** (standing constraint: a run without
   that hash is a score, not evidence).
6. ONE sequential full sweep after all lanes (standing constraint "Suite runs on a shared mid-wave tree are single-writer"), INCLUDING the harness suites (`:app:testDebugUnitTest`, `npm -w apps/web-harness run test`) — L2 changes
   the harness fold pinned there. Then the closing gate on a quiet host, scored against `wave51-fix` (or against the L12-B calibration run for instrument-only attribution) with `--watch tools/titan/results/wave52-plan/watchlist.txt`.
   Fixture net `BASELINE=1 ./test-all.sh --gate-set` exit 0 ×8; L4's b″ A/B `cmp` on the four css-sizing Android PNGs + installed `base.apk` sha1 against the build; L2's Fix A
   tripwire (frame-ink census 208 + 84 = 292 → 0 / 0). Watchlist precondition: `node tools/titan/results/wave52-plan/watchlist-check.mjs` prints `unmatched 0` (C7).

## 5. Ownership conflicts resolved (who yielded what)

- Three composed canvases + `UaBlockMargins.kt` + `ComposedRootStack.swift` + `WptCaptureMode.kt/.swift`: L2 only. `runtime-bugs-and-gaps` M1, `native-near-misses` T1/T2-roots/T3/T6 and `page-padding-overrun` Fix A merge there;
  `harness-and-android-resample` 0(aa)/0(ab) yield (see §6).
- `StyleApplier.kt`: L4 (backface, `:532-557`). L8 edits `buildSpacingContext` `:805-830` in L8's OWN PR after L4 merges, with a defaulted `inlineAxisUpright` parameter on `ChUnitMetrics.measure` (C4). `TableBoxTree.{kt,swift}`: L8 (M-E); native-near-misses T10 (anonymous table fixup) not staffed. `UAWidgetIntrinsics.kt`/`UAWidgetsResolve.*`: L8 (M-G = T12).
- `MarginApplier.kt`/`PaddingApplier.kt`/`ElementContainingBlock.kt`: L4 (b″). L10 reads margins in `FlexNowrapLine`, does not edit. `MarginApplier.swift`: L10.
- `FixedHoist.swift`: L3 (F3). `runtime-bugs` 6(e) ICB A/B not staffed; L2's iOS regroup stays inside `CaptureCanvas.swift` or hands a hunk to L3.
- `ContentsUnboxing.{kt,swift}` + tests: L3. L11's `ContentsUnboxingTests.swift:175-183` rewrite ships in L11's OWN PR after L3 merges, in the same commit as `GlobalExtractor.swift` (C5).
- `inject-wpt-block.mjs`: L12. L6's `:1308` adjudication A re-add ships in L6's OWN PR after L12 merges (one line in an L12-owned file, L12 told — C3); native-near-misses §3.9 (coverage ratio on dark ink) is a NEW "Instrument decisions pending" item, not staffed.
- `extract-fixture.mjs` hunks: L5 only (cascade F1–F3, web-tail F-C/F-D/F-E, counters T6). Counters T4 fold and colour F3 (`::first-letter`) not staffed. `StyleBuilder.ts`: L11 (L7 must not widen `applyGeneric`).
- `ColorParser.kt`: L1 (F-A arms; the color-mix range twin is optional there). L5 keeps `AngleParser.kt` and the `ColorParserHslHueTest.kt` pin only.
- Baked-guard hunk (`ComponentRenderer.kt:2781-2785`, `.swift:4354-4361`): L3 only. `vertical-wedges` M-F and `native-near-misses` T4 drop it; L3's signature (`Width && Height && BoxSizing && (PaddingTop || BorderTopStyle)`) is the one
  with the 13-carrier census.

## 6. Not staffed this wave (with the reason)

- **`harness-and-android-resample` 0(aa)** (Android 1/256 glyph-edge leak on 4 fixture stems): device-only A/B, score-neutral by construction (no corpus cell carries the signature; the label is not drawn in WPT mode), and its
  arms A3/A5 edit `ScreenshotCaptureScreen.kt`, which L2 owns. Run as a post-integration A/B by the orchestrator if the closing gate leaves time, with the pre-stated Park rule (A1/A2-only ⇒ Park with the record).
- **`harness-and-android-resample` 0(ab)** (relocate `BlockLabel`/`BlockFont`/`HarnessLabelChrome` into the harnesses; ~35 files incl. `Package.swift`, `project.yml`, five count-quoting docs): pure move, zero cells, and it touches
  `ScreenshotCaptureScreen.kt:53` and `CaptureCanvas.swift:37/:193-196/:253-256` (L2's files). Wave 53, or the orchestrator's chore after L2 merges; the iOS half needs a new SwiftPM target so the 19 Catalyst pins keep running.
- **`counters-and-multicol` T4** (`<br clear=all>` fold + clearance rung; queue 3(a)): 0 flips, 12 passing cells at risk, B6's verdict "the fold alone moves ink without fixing it", and web's `.clear` BFC question is unanswered
  (needs a composed-capture DOM dump under vite first). Correctness-only; revisit with that dump.
- **`failure-ink` F4/F5/F6** (multicol sequential fill + monolithic chain; table-box clip in the block fallback; css-writing-modes-3 §7.3.1 orthogonal available block size): correctness-only on 7 passing cells; F4 exposes 27
  fully-passing column-fill:auto multicols; F6 is L-effort with 17 passing carriers. F6's own flips (`available-size-011 ios/android`, `column-fill-balance-orthog-block-001 ios`) are the wave-53 candidate.
- **`colour-not-reaching-text` F3** (`::first-letter` as a synthetic inline run): L effort, extractor seam, 0 flips (all 6 cells pass), and ~15 colour-only first-letter carriers at risk if a ring refuses the synthetic member.
- **`static-position` T4** (grid-area alignment container, RTL track order, vertical-rl, typed-raw `safe end`): no flip this wave — `grid-abspos-staticpos-align-self-safe-001 ios/android f 0.944` also needs float packing; needs T3 first.
- **`native-near-misses` T2-children, T8, T9, T10, T11**: T2-children (self-collapsing `height:0` child in the runtime plan) changes the S1–S12 pin table on both natives for 0 predicted flips with `change-list-style-type-002` /
  `anonymous-table-cell-margin-collapsing` thin; T8 (inline-tag components boxed block-level; 9 passing carriers) cannot reproduce the ref's same-line join in a Column of roots; T9 (LCH hue ramps — mechanism unverified on iOS);
  T10 (anonymous table fixup, CSS 2.1 §17.2.1, 5 cells) and T11 (abspos `display:table` centring, 2 cells) are real M–L fixes without a census — wave-53 lanes with their own briefs.
- **`native-near-misses` §3.9 instrument proposal** (coverage-ratio veto on ink >64/channel or 1-px-dilated masks — 9 cells where web passes the same render): a decision item; goes to "Instrument decisions pending" with its
  measured population, not into a lane (obligation #4: a scalar rule is adopted only pre-registered and measured on the committed sample).
- **`runtime-bugs-and-gaps` 006** (writing-mode flex main axis): priced and declined again — 2 cells up, 27 passing cells exposed. **6(e)** (FixedHoist ICB clause A/B): parity item, 0 flips, `FixedHoist.swift` is L3's this wave.
  **6(x²)** (iOS colour-matrix filter pass): fixture-net only, no corpus cell.
- **`vertical-wedges` T5** (`abs-pos-border-offset-003` block-level static position under vertical-rl): the writer was not located; it MOVES under L3's F1 (direction unknown) — watch, do not staff.
- **`web-tail` residual singletons** (`html-becomes-fixed` root-attr loss; `scope-implicit-006-print` template `<style>` collection with 7 passing web cells at risk; `colspan-004`; `caret-shape ×2`; `at-color-profile-001`):
  PNG-clustered, not root-caused to a fix; wave 53.
- **`counters-and-multicol` A/B/C** (armenian adjudication is a one-line hand-off to L12; `::marker{content:none}` bag; shadow-DOM walk): B/C need T7 and a shadowRoot walker the extractor lacks entirely.
- **B10-adjacent "Threshold study C4", "Extraction-wall re-measure", "Standing report of excluded tests disagreeing >0.1"**: their preconditions are met (harness brief §10) but none reaches a corpus cell; orchestrator chores if idle.

## 7. What the closing gate should print, and how to read it

- `score-gate.mjs wave51-fix wave52-final --watch tools/titan/results/wave52-plan/watchlist.txt --movers 0.005`: **lost = 2** — `css-gaps/flex/flex-gap-decorations-033 ios` and
  `… android` (P 1.0000 → f), both named here and both the BACKLOG's pre-registered direction (C1); any OTHER row under `LOST (P → f)` is diagnosed. **unmeasured-now = 21**: the 19
  absence-only cells (L12-A, by name; they print under the existing `UNMEASURED NOW (scored before, not now — excluded or missing)` heading — no scorer change, C2) +
  `css-counter-styles/armenian/css3-counter-styles-008 ios` and `… android` (font-parity adjudication A, shipped with L6 — C3). A cell that lost its stamp prints NEWLY MEASURED,
  never LOST (standing constraint). gained = the L1/L2/L3/L4/L5 HIGH tier (≈ +12 web / +20 iOS /
  +19 Android) plus whatever of the MED tier landed — every gained cell PNG-checked against the (re-frozen) ref before it is written into a "PASSES" sentence (standing constraint). Cells that flip on the scorer but are labelled
  DEGENERATE above (`gaps-034 ×3`, `contain-inline-size-bfc-floats-001 ×2`, `anchor-position-multicol-007 android`) are counted in the total and named as degenerate in the PR — never claimed as fixes.
- Instrument attribution: every mover on a test whose ref was re-frozen (L12-B list) or whose capture frame changed (L2 Fix A: 292 tripwire cells, 274 of them SSIM-predicted) is adjudicated by the capture-hash check — identical capture bytes ⇒ instrument-only.
- `filter-effects/backdrop-filter-basic-blur` moves incidentally under L2 Fix A (iOS 0.9469 → ~0.9526 predicted): report the number plainly in the PR body; no code names it.
- Fixture net exit 0 ×8; L4 b″: four Android PNGs `cmp`-identical; every device A/B (L4 b″, L6 T5, L8 M-A, L9 F2) records the installed `base.apk` sha1 / `.app` hash against the
  build; L2: frame-ink census 292 → 0 / 0; L12-A: `inject` summary prints `absence-only=19 blank-captures=36`; watchlist: `watchlist-check.mjs` `unmatched 0`.

## 8. The three biggest risks

1. **Seam integration on the two native `ComponentRenderer` files.** Nine lanes deliver eleven Compose hunks and eight iOS hunks; the iOS positioned-child overlay (`:765`/`~:2050`/`:2098-2112` — L3's and L7's hunks in the same `positionedChildren` function) and the Compose RC1 branch
   (`:911-950` — L2's and L7's hunks OVERLAP in the one `val itemModifier = if (…)` expression at `:921-948`, not "adjacent": C6) each receive two lanes' patches. A mis-ordered or hand-merged application regresses silently (the same class as PR #126's symlink). Mitigation: §3 registry + §4 order (L2 before L7, L3 before L7, L3 before L11), L7's two patches cut against the L2-/L3-applied trees and naming them, JVM/Catalyst suites re-run
   after each applied patch, and the closing gate's per-cell diff — never totals.
2. **Unmeasured blast radius of the cascade rewrite (L5 F-D) and of the harness fold (L2).** F-D re-orders every document where a higher-specificity rule precedes a lower one — it cannot be grepped from the IR and ships only
   after the extract+convert differential lists every changed document (with `POST_LOAD_EXTRACT` both ways); the fold change touches every composed capture's root stack on both natives (30 passing cells on the watchlist by IR
   shape, none opened as PNGs). Both lanes' censuses are re-derived by a skeptic with its own script before the PR (wave-50 precedent: 9 claimed vs 22 measured).
3. **Instrument and render changes in one gate.** L12-A removes 19 cells (+2 more leave with L6's adjudication A), L12-B re-freezes ~21 refs (and pre-registers gaps-033 natives P → f), L2 Fix A changes the frame of 292 captures, L1 F-B costs `column-span ×3` under the OLD refs and pays them back under the NEW —
   without the Calibration-gate capture-hash step and the landing order of §4, gained/lost is uninterpretable and a "zero lost" sentence cannot be derived from the per-cell diff (standing constraint). Land L12-B first, on the
   unmodified tree, and score render lanes against that run.

## 9. Plan-skeptic corrections applied (2026-09-25; `plan-skeptic-1.md` — the refuting review — and `plan-skeptic-0.md` — six non-refuting corrections)

Every correction from both reviews is applied in place above; nothing else moved — lane ownership and lane targets are as the reviews left them. The
BACKLOG is orchestrator-owned and is NOT edited here; the one amendment it needs is written out verbatim in §2 L12 for pasting at ship time.

| # | source | correction | what changed |
|---|---|---|---|
| 1 | skeptic-1 C1 | L12-B "predicted P→f: 0" contradicted the BACKLOG's pre-registration on `css-gaps/flex/flex-gap-decorations-033` (`wave51-fix … ios P 1.0000`, `… android P 1.0000`, `… web f 0.9400`) | L12 Part B now pre-declares 033 web f → P (+1, MED) and **033 ios/android P → f (−2, honest)** exactly as "The erased-reference body background — a CALIBRATION" pre-registers; "predicted P→f: 0" → "**2 (033 natives, pre-registered)**"; §1 table L12 row and §7 updated; §1 headline drops 2 on each native (iOS ≈ 1101, Android ≈ 1091); §4 step 2 and §8 risk 3 note it |
| 2 | skeptic-1 C2 | L12-A's `diffRuns` rule ("prev absence-only → cur scored-f is LOST") inverted the standing constraint "a cell whose `scoreExcluded` stamp was REMOVED is NEWLY MEASURED, never lost" (`score-gate.mjs:122`, `isScored` at `:50`) | **No scorer change is made.** L12 Part A states the constraint holds: stamp-removed cells print NEWLY MEASURED; the 19 absence-only cells leaving the denominator print under the EXISTING `UNMEASURED NOW (scored before, not now — excluded or missing)` heading (`:185`); the brief's §4 item 3 / pin 5 LOST semantics are withdrawn; L12's `score-gate.mjs` ownership is retained with "no semantic change to `diffRuns` / `isScored`"; verification list and §4 step 2 updated |
| 3 | skeptic-1 C3 + skeptic-0 §8.6 | the `armenian/css3-counter-styles-008` re-add to `NATIVE_FONT_PARITY_REFUSED_TESTS` (`inject-wpt-block.mjs:1308`) was handed L6 → L12 and would have shipped three landing steps before the lane that motivates it; `-008 ios f 0.9420 / android f 0.9423` are SCORED today, so excluding them adds 2 to unmeasured-now and −1 to each native denominator | the re-exclusion **ships with L6's PR** (L6 edits the L12-owned line after L12 has merged; L12 told); L6 Seams/Flips, L12 own/hand-offs, §3 registry, §4 step 5 and §5 rewritten; **§7 closing gate prints unmeasured-now = 21** (19 absence-only + 2); the −2 denominator effect (1363 → 1362) is stated in L6, §1 table and §1 headline |
| 4 | skeptic-1 C4 | the L8 → L4 `StyleApplier.kt:805-830` hunk could not compile in L4's PR — `ChUnitMetrics.kt:38` is `fun measure(fontFamily: FontFamily?, fontSizePx: Float): Float?` until L8 lands, and the hunk passes a third argument | **the hunk moves to L8's PR**, and L8 adds the parameter WITH A DEFAULT (`inlineAxisUpright: Boolean = false`) so the existing two-argument call and every caller compile at every commit; L4 edits the backface region only and merges first (§4 step 3); L4 own, L8 Seams, §3 registry, §4, §5 rewritten; the skeptic's open question (can `buildSpacingContext(config: StyleConfig)` read the merged `WritingMode` + `TextOrientation`) is carried with its fallback (a defaulted parameter on `buildSpacingContext` too) |
| 5 | skeptic-1 C5 | the L11 → L3 `ContentsUnboxingTests.swift:175-183` rewrite had no red-free landing order — the existing pin (`[All INITIAL, Width 160, BackgroundColor blue]` → `.isEmpty`) goes red the moment L11's `GlobalExtractor.swift` lands, and the rewritten pin is red until it does | **the rewrite ships in L11's PR, in the same commit as `GlobalExtractor.swift`**; L3 does not touch `:175-183`; **L3 before L11** added to §4 step 4; rule written in L11 ("the Catalyst suite is green at every step"); L3 own, §3 registry, §5 updated |
| 6 | skeptic-1 C6 | §3 called Compose L2 `:911-948` and L7 `:927-950` "ADJACENT" — they OVERLAP inside one expression (`val itemModifier = if (hoistHostActive && CanvasRootHoist.rendersInFlowAsStaticPosition(…)) { itemModifier.then(zeroFlowAnchor(…)) } else itemModifier`, `:921-948`); iOS L3 `~:2050` and L7 `:2098-2112` share `positionedChildren` | §3 rows record the overlap / shared function; **L7 cuts both patches against the L2-applied (Compose) and L3-applied (iOS) trees and names them**; **"L2 before L7" added to §4 step 4**; L2 and L7 Seams, L3 Seams and §8 risk 1 say "overlap", not "adjacent" |
| 7 | skeptic-1 C7 | 11 of 176 `watchlist.txt` lines matched zero scored cells under `score-gate.mjs:138-145`'s substring rule (L7's headline flip among them) | `watchlist-check.mjs` (this directory) imports `loadRun`/`watchCells` from the scorer and replays the same rule over the 30 wave51-fix manifests — it reproduced the 11 (176 lines / 855 distinct cells); the 11 patterns were replaced with manifest paths (`css-flexbox/abspos/…` ×6, `css-overflow/line-clamp/discard/discard-multicol-003`, `css-anchor-position/anchor-position-multicol-colspan-003`, `css-counter-styles/counter-style-at-rule/{broken-symbols,descriptor-suffix}`, `selectors/invalidation/any-link-attribute-removal`); the ring-fenced `filter-effects/backdrop-filter-basic-blur` line stays a REPORT-only watch; the script prints `unmatched 0` and is a §4 step 6 precondition (§0 rule iv) |
| 8 | skeptic-1 C8 | four citations used non-manifest paths (values right): `css-grid/grid-abspos-staticpos-align-self-center android P 0.9808`, `css-grid/grid-abspos-staticpos-align-self-rtl-last-baseline-002 web P 0.9979`, `css-flexbox/position-absolute-containing-block-002 ios f 0.9373`, and the range form `abspos-auto-sizing-fit-content-percentage-001..004` | paths corrected to `css-grid/abspos/…` and `css-flexbox/abspos/…` (L7 T1/T2/T3, §1 table); the range expanded to four named cells `-001 / -002 / -003 / -004 android P 0.9984` with the -005..-008 / carrier note (L4 Targets, §1 table); the `…-align-self-end` / `…-align-items-self-end` / `-001` / `justify-self-001` continuations written as full paths |
| 9 | skeptic-1 C9 (hash rule) | L6 T5, L8 M-A and L9 F2 were "device A/B" with pins but no hash sentence (L4 b″ had "APK sha1 recorded" only) | each of L4 b″, L6 T5, L8 M-A, L9 F2 now carries "records the installed `base.apk` sha1 / `.app` hash against the build" (standing constraint, include- or exclude-direction); §4 step 5 and §7 restate it for all four |
| 10 | skeptic-1 C9 (BACKLOG form) | L12-A Decision B declines the BACKLOG's "ship it as a CAPTURE FAILURE in the exit-7 family" — permitted, but the decision must reach the BACKLOG as an explicit amendment, and lanes do not edit the BACKLOG | the exact replacement paragraph for the "**The blank-capture guard — DECIDE IT …**" bullet of "## Instrument decisions pending" is written out in §2 L12 (blockquote) for the orchestrator to paste at ship time; `docs/BACKLOG.md` itself is untouched here |
| 11a | skeptic-0 §8.1 | L2 overrun arithmetic used three denominators (274 / "273 more" / 208+84) | one stated denominator: **292 scored tripwire cells** (208 right + 84 left → 0 / 0 after); 274 = the same-size SSIM-prediction subset (192 + 82), which INCLUDES the three 040 cells → "271 more"; the 23 overrun-top / 38 mixed / 47 frame-colour / 170 height-mismatch classes named as counted-not-diagnosed (mixed cells watched as movers); §1 table, L2 Targets/Verification, §4 step 6, §7, §8 unified |
| 11b | skeptic-0 §8.2 | L1 F-B pointer `view-transition-bake.mjs:2296` drifted 8 lines | now `:2258` (the overflow bail record `over.outside > VT_OVERFLOW_INK_TOLERANCE_PX` → `solved[key] = {error}`), surfaced by `planViewTransitionBake` (`:1497` / `:1557`) and returned at `:2304` (`if (bail) return`), before the ring sample `:2309-2314` and the step-2c stamp (`:1729-1757`, `if (frameRing)` at `:1734`) |
| 11c | skeptic-0 §8.3 | L8 target said the `input-range-zero-inline-size` ref is blank | the ref shows a 16×22 green L-shaped stub at (16,66) — NOT blank; the stub is ink the post-fix natives must still paint (native-near-misses T12 had it right) |
| 11d | skeptic-0 §8.4 | L4 at-risk listed `backface-visibility-hidden-003/004 android` beside passing cells; both are f today (`android f 0.9845 cF nI` / `f 0.9980 cF nI`) | relabelled **movers** (can move, cannot be lost) with their manifest values; `animated-001/002` written as `ios P 0.9646` as the manifest reads; watchlist L4 comment updated |
| 11e | skeptic-0 §8.5 | L10 target shorthand "both `flex-basis:50%` items 0 wide" dropped the ~5 px green bar | wording now names the bar at x 16–20 (the `.left` item's border, `runtime-bugs-and-gaps.md` row 16) before the red to x 115 |
| 11f | skeptic-0 §8.6 | L6's adjudication A credited "+1 (HIGH)" for web but did not say the two native cells become `scoreExcluded` | stated in L6 Flips/Seams, §1 table and §7: 0 on pass, −1 on each native denominator, unmeasured-now = 21 (merged with correction 3) |
| 12 | skeptic-1 C9 (commit rule) | `tools/titan/results/wave52-plan/` was untracked at review time ("every artifact is committed the moment it exists") | §4 step 1 now opens with committing the directory before the opening gate |

Re-verification after the edits: `node tools/titan/results/wave52-plan/watchlist-check.mjs` → `unmatched 0`; every `wave51-fix <section>/<test> <platform> <P|f> <ssim>`
citation in this file resolves against `tools/titan/runs/wave51-fix/sections/*/manifest.json` with the stated verdict and score (re-run of plan-skeptic-1's check over the
corrected text — see the report accompanying this revision).

## 10. Integration record and closing-gate expectations as amended (orchestrator, 2026-10-05, BEFORE the closing gate)

Written before `wave52-final` runs, so everything below is a pre-registration. Sources: `ORCHESTRATOR-TODO.md` (this directory), the twelve lane
notes and skeptic reviews, `build-v2-result.json`.

**Integration.** `integrate-seams.sh` applied 35/35 seam patches and cross-lane hunks in the §4 order (dry run in a throwaway worktree first, then
the tree; commit c565d2e3), plus L11's gate-list hunk after its must-fix RV-M1 was closed (the fixture asserts through fills only; the skeptic's
proxy through the real comparator exits 0 under both models). `tools/visual/gate-fixtures.txt` therefore lists **9** fixtures; the closing gate's
fixture net must print exit 0 ×9 — `all-then-color.json` is gate-only (no committed baseline). Withdrawn / not needed, nothing applied: L4's
b′ BlockContainer gate, L2's Compose RC1 zIndex hunk (went harness-side), L3's iOS overlay partition, L6's web marker row, L8's Compose budget
hand-off. Never committed: `vertical-wedges/ma-exclude-ios.patch`, `inline-run-wall/drop-F1.patch` (device-A/B arms).

**Deferred by the orchestrator, with the reason.** L6 asked for `requires-script-mutation` not-applicable tags on seven valid
`css-counter-styles/cssom/cssom-*-setter` tests. That tag excludes unconditionally: 21 scored cells would leave the denominator, 13 of them
passing, in the same gate as L5/L6's cssom work — the movement would be unreadable. NOT applied this wave; queued in BACKLOG with the census.

**§7 as amended — what `score-gate.mjs wave51-fix wave52-final` must print, and how to read it.**
- Instrument-only moves are known in advance from L12's calibration (`tools/titan/runs/wave52-calib` = `wave52-open` captures re-scored against
  the re-frozen refs; 4305/4305 capture PNGs byte-identical, 88 moved cells all attributed): **gained 13** (initial-background-color ×3,
  flex-gap-decorations-033 web, -034 ×3, -035 ×3, backdrop-filter-root-element ×3), **lost 8** (flex-gap-decorations-033 ios/android —
  pre-registered at plan time; `css-display/display-contents-root-background` ×3 P ≈1.0 → f 0.535 and
  `css-view-transitions/column-span-during-transition-doesnt-skip` ×3 P 0.9879 → f 0.9332 — NOT pre-registered at plan time, declared here:
  the old refs were erased/degenerate, the new refs show what Chrome paints and the runtimes do not), **unmeasured-now 19** (the absence-only
  stamps, by name in L12's note). L1 measured that its F-B does NOT repay column-span ×3; no lane repays display-contents-root-background ×3 —
  both become BACKLOG items with the calibration as their evidence.
- So "lost" in the score of record is **≥ 8, all instrument-only and capture-identical**; a lost cell outside that list of eight is a REAL
  regression and stops the ship. **unmeasured-now = 21** (19 + armenian/css3-counter-styles-008 ios/android via L6's adjudication hunk);
  the inject summary prints `absence-only=19` and `blank-captures=45`.
- Render lanes are attributed against the calibration run: `score-gate.mjs wave52-calib wave52-final` must show **lost = 0** and
  unmeasured-now = 2; every gained cell there is PNG-checked against the re-frozen ref before it is written as a pass, and cells labelled
  DEGENERATE in §2 are counted and named, never claimed as fixes.
- Baseline for the totals: calibration alone puts the corpus at web 1211/1372 · iOS 1084/1363 · Android 1075/1363. The lanes' HIGH and
  MED-HIGH predictions (per `build-v2-result.json`) add roughly +19 web / +27 iOS / +26 Android on top if all land; the watchlist
  (300 lines, 0 unmatched) attributes each.
- Tripwires on the final run: L2's frame-ink census → overrun-right + overrun-left = 0/0; L4's four
  `css-sizing/abspos-auto-sizing-fit-content-percentage-001…004` Android PNGs `cmp`-identical to wave52-open; the ring-fenced
  `filter-effects/backdrop-filter-basic-blur` reported plainly (L2's Fix A moves its frame).
- Device A/Bs owed after the gate, each recording the installed `base.apk` sha1 / `.app` hash: L4 b″, L6 T5 (outside markers), L8 M-A (face
  change), L9 F2/F1 (glyphless ring / hanging punctuation). The integrated tree is the include arm.

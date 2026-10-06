# Wave 52 — orchestrator TODO (compiled 2026-10-05 from the 12 lane notes + 12 skeptic reviews)

Read-only compilation. Citations: `<lane>/_note.md:<line>` / `<lane>/skeptic.md:<line>` under `tools/titan/results/wave52-<lane>/`;
`PLAN.md:<line>` and `integrate-seams.sh` under `wave52-plan/`. Lanes: L1 web-tail-colour-vt · L2 composed-canvas · L3 failure-ink ·
L4 small-fixes · L5 extractor-cascade · L6 counters-and-lists · L7 static-position · L8 vertical-wedges · L9 inline-run-wall ·
L10 flex-nowrap-gaps · L11 all-reset-postload-colour · L12 instrument-and-calibration. Notes cite HEAD 7d9c22a7; the branch is now at
the b35e203a checkpoint (seam files untouched). Two must-fix items were open at compile time and are being fixed now: L6 R1
(`counters-and-lists/skeptic.md:381-416`) and L11 RV-M1 (`all-reset-postload-colour/skeptic.md:138-157`).

## 1. Integration actions beyond the 35 ORDER patches

### 1a. Patch / hunk files present but NOT in `integrate-seams.sh` ORDER
- `all-reset-postload-colour/hunk-for-orchestrator-1.patch` — adds `fixtures/combinations/all-then-color.json` to `tools/visual/gate-fixtures.txt`
  and a row to `fixtures/combinations/README.md` (`all-reset-postload-colour/_note.md:107-110`). **DO NOT APPLY YET**: open must-fix RV-M1 —
  as handed off it lands a predicted-red cross-platform pair (`ATC_InitialUnderRedParent` iOS-web SSIM 0.9415, Android-web 0.9420 →
  `compare-screenshots` exit 4, so the fixture net "exit 0 ×8" breaks) (`all-reset-postload-colour/skeptic.md:138-157`). Apply only after the
  fixture fix (drop `_text`, or re-declare `font-family` after `all` on both text rows, or ship a ledger line inside the hunk) AND a re-run of
  the skeptic's proxy; land only together with web F1 + seam-4 (ORDER #30) (`all-reset-postload-colour/_note.md:110`). Baselines are NOT
  captured: `UPDATE_BASELINE=1` + LOOK before committing them (`all-reset-postload-colour/_note.md:120-123`, `:162`).
- `inline-run-wall/drop-F1.patch` — device-A/B arm A only (removes F1's hanging-punctuation arm + pins, 5 files). Apply ONLY if the F1 A/B
  confirms the drop (`inline-run-wall/_note.md:269-272`, `:335`); still `git apply --check`-clean (`inline-run-wall/skeptic.md:321`).
- `vertical-wedges/ma-exclude-ios.patch` — iOS M-A exclude-direction build: apply, build, record `.app` hash, capture, REVERSE. **Never commit**
  (its header: ChUnitInlineAxisTests' Inter pins go red under it by design) (`vertical-wedges/_note.md:60`, `:134-135`).
- `small-fixes/old-seam-1-v1-superseded.txt` — superseded v1 of L4 seam-1; do not apply (`small-fixes/_note.md:51-52`).
- `flex-nowrap-gaps/logs/seam-2.pre-fix-pass.patch.txt` — do NOT apply (old 4-arg `engages`, no longer compiles); ORDER's `seam-2.patch`
  is the re-cut (sha256 e2c0dbea…, = `head-shas.txt` post-fix-pass, checked) (`flex-nowrap-gaps/_note.md:45-47`, `:144-145`).
- L3 seam-1/seam-2/hunk shas in the dir = the fix-pass re-cuts 3e3c446e / 43809f52 / 4f64d533 (checked; `failure-ink/_note.md:223-225`).
- Withdrawn / never-delivered PLAN §3 rows (nothing to apply): L4 `:1886-1890` BlockContainer gate b′ WITHDRAWN (`small-fixes/_note.md:213-214`);
  L2 `:911-948` RC1 zIndex moved harness-side (`composed-canvas/_note.md:79-83`); L3 iOS `:765`/`~:2050` partition not needed
  (`failure-ink/_note.md:60-64`); L6 web `ComponentRenderer.tsx` marker row not needed (`counters-and-lists/_note.md:84-85`); L8 Compose
  `:7622-7637` budget hand-off not needed — M-C needs no seam (`vertical-wedges/_note.md:38`).

### 1b. PLAN §3 seam-registry corrections (record only — every hunk below IS inside an ORDER patch)
- L9 hunks missing from §3: Compose seam-1 `:6848`; iOS seam-2 `:3132, :3414, :3466, :4161, :4646` (`inline-run-wall/_note.md:340-344`;
  `inline-run-wall/skeptic.md:173-176`).
- L10 NEW row: iOS `ComponentRenderer.swift` seam-1 (`flexMainPlan` ~`:2446-2500` + child-loop `gapDecorationItemFrame`); Compose seam-2
  patches the engine `:2213` Row/Column only, legacy `:2508` FLEX_ROW unpatched (`flex-nowrap-gaps/_note.md:34-44`).
- L11 NEW rows: `extract-fixture.mjs :10054` `uaLinkProps` (seam-3) and `apps/web-harness/src/sdui/ComponentRenderer.tsx` `calibrateStyles`
  (seam-4) (`all-reset-postload-colour/_note.md:100-106`; `all-reset-postload-colour/skeptic.md:96-98`).
- L8 actual hunks: Compose ~`:1351` `bindInterFace` (s1), `RenderTableContent` (s3); iOS s2 ×4 (~4170, PlaceholderLabel, ~4908 veto, ~4970),
  s4 ×3 (`vertical-wedges/_note.md:55-58`). L7's seams are cut on HEAD(+L3), the C6 overlap with L2 is gone (`static-position/_note.md:8-11`).

### 1c. New files with no other owner — commit with the named lane (and amend the PR's own-lists)
- L1: `converter/src/test/.../primitiveParsers/ColorParserLinearSpaceTest.kt` (`web-tail-colour-vt/_note.md:56-58`).
- L2: `runtimes/swiftui/Tests/StyleConverterRuntimeTests/ComposedIcbClipTests.swift` (`composed-canvas/skeptic.md:116-117`).
- L3: `Renderer/BakedLayoutSignature.swift`, `Tests/.../MulticolSpannerHoistRasterTests.swift` (`failure-ink/_note.md:65-66`) — L3's hunk
  (ORDER #20) only COMPILES with the untracked `BakedLayoutSignature.swift` present (`failure-ink/skeptic.md:283-284`).
- L4: `transforms/{BackfaceCull,TransformInheritance}.kt`, tests `BackfaceCullTest.kt`, `BackfaceCullChainTest.kt`, `TransformInheritanceSeamTest.kt`,
  Swift `BackfacePreserve3DTests.swift` (`small-fixes/_note.md:133-136`; `small-fixes/skeptic.md:223-224`).
- L6: `lists/ListMarkerOutsideHang.{kt,swift}`, `lists/ListMarkerEmptyItem.{kt,swift}`, `counter-style-author.mjs`, `counter-style-descriptors.mjs`
  + tests — amend L6 own-list in the PR record (N5) (`counters-and-lists/skeptic.md:97-98`, `:131`).
- L7: `AlignSelfPropertyParserTest.kt`, `AbsposFlexStaticOffsetTests.swift`, `AlignSelfBaselinePosition.test.ts` (`static-position/skeptic.md:37`, `:71-72`).
- L8: `VerticalInlineAxis.{kt,swift}` + tests (`vertical-wedges/_note.md:170-171`); 22 lane files listed in `vertical-wedges/lane-files.txt`.
- L9: `runtimes/compose/.../typography/wrapping/DrawnLineClamp.kt`, `runtimes/swiftui/Tests/.../GreedyLineBreakerTests.swift` (`inline-run-wall/_note.md:345-347`).
- L10: `fixtures/properties/effects/box-shadow-reach.json` (`flex-nowrap-gaps/_note.md:27`); unowned EDIT `columns/GapDecorationBandsTest.kt`
  to flag in the PR (`flex-nowrap-gaps/_note.md:148`).
- L11: `global/AllReset.kt` (+ test), `fixtures/combinations/all-then-color.json` (`all-reset-postload-colour/_note.md:19`, `:120-123`).
- Every `wave52-*` results dir: commit it (L5's six seam patches are the only copy of the extractor implementation) (`extractor-cascade/_note.md:378-379`).

### 1d. Non-patch edits the orchestrator must make
- **L6 notApplicable tags** — `tools/titan/wpt-not-applicable.mjs` (no owner) should tag `requires-script-mutation` on exactly these 7 tests
  (`counters-and-lists/_note.md:117-120`; `census.json → T7_author_counter_style.scriptMutationWall`): `css/css-counter-styles/cssom/cssom-{additive-symbols,
  fallback,name,negative,pad,prefix-suffix,range}-setter.html`. Mechanism not specified by L6 (today's `RX.scriptDomMutation` does not match them).
  **GATE IMPACT (compiler's check, wave51-fix manifests):** all 7 have `postLoadExtracted=false`, `structureExtracted=false`, and the tag is in
  `EXTRACTION_WALL_TAGS` (`inject-wpt-block.mjs:1103-1104`), which excludes unconditionally → **21 scored cells leave the denominator, 13 of
  them P** (additive ×3, fallback ×3, name ×3, range ×3, negative web). That makes unmeasured-now 42, not PLAN §7's 21, and hides 8 of L5's
  18 watched native cssom cells. Defer to after the gate, or pre-register it in PLAN §7 before scoring.
- **L12 doc paths** (`instrument-and-calibration/_note.md:158`):
  `.claude/skills/wave/SKILL.md:93` and `:201`: `white-black-ink-font-lh-imgpad-htmlpins/` → `white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`
  (rest of each line unchanged). `tools/titan/section-runner.sh:377-378` comment ("Cache lives at tools/wpt/refs/<sha>/white-black-ink-font-lh/…;
  the `white-black-ink-font-lh` segment is … CANVAS_REV"): say the cache lives at `refs/<sha>/<CANVAS_REV>/…` (live rev
  `white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin`) and that the `white-black-ink-font-lh` literal at `:909`/`:943` is a
  `KNOWN_STALE_CANVAS_REVS` entry `inject-wpt-block.mjs` rewrites to `LIVE_CANVAS_REV`. The scorer path needs no edit. (NB: the comment names the even
  older `-lh` rev, not `-htmlpins`; the replacement text is the compiler's — L12 gave line numbers only.)
- **L12:** ship `instrument-and-calibration/absence-only-digest.wave51-fix.json` in the SAME commit as the scorer code, else inject pin 7 and the
  score-gate replay pin skip silently (`instrument-and-calibration/skeptic.md:116-117`). After the gate: `tools/titan/results/corpus-v6-18.json`
  with the 19 names (`instrument-and-calibration/_note.md:61-68`, `:175`). Amend PLAN §1/§7 BEFORE the gate (D3; see §4 below)
  (`instrument-and-calibration/skeptic.md:111-115`).
- **L2:** in the sweep, run `xcodegen generate` in `apps/ios-harness` then `xcodebuild build-for-testing -scheme StyleConverterTestTests -destination
  'generic/platform=iOS Simulator'` — the new harness XCTest `ComposedCanvasIcbClipTests.swift` has never compiled (`composed-canvas/_note.md:207-213`).
- **L2 watch lines** the skeptic asked for (lifted roots outside the T3 rows, 14 P tests / 28 native cells): add `css-images/gradient/gradient-single-stop-00`,
  `css-sizing/aspect-ratio/abspos-0`, `css-backgrounds/background-clip-content-box-001`, `css-masking/clip-path/clip-path-polygon-003`,
  `filter-effects/backdrop-filter-edge-pixels-2` (`composed-canvas/skeptic.md:242-254`); not in L2's additions file.
- **L3:** tell the holder of `wave52-lock/ComponentRenderer.swift` at ~17:11 that its lock dir mtime was reset (`failure-ink/_note.md:215-219`, `:232-233`).
- **Fix the iOS canvas-count test** (no lane owns it): `wpt-white-canvas.test.mjs:209` "swiftui: WPTCanvas is white…" counts
  `/\.background\(canvasBackground\)/g` = 3, but L2's `CaptureCanvas.swift` now has 4 — the 4th is a COMMENT at `:931` (compiler's grep). Reword L2's
  comment (L2's file) or adjust the test; otherwise red after integration (`instrument-and-calibration/_note.md:157`; `…/skeptic.md:75-77`).
- **L11:** hand L5 / a later wave the general extractor fix for S1 (bag order ≠ cascade order) (`all-reset-postload-colour/_note.md:194-199`).

## 2. Apply-order constraints vs `integrate-seams.sh` ORDER (positions #1–#35, checked literally)
| constraint | source | ORDER | verdict |
|---|---|---|---|
| L5 seam-1…5 in order, at step 3 | `extractor-cascade/_note.md:275-278` | #1–#5 | OK |
| L1 hunk-for-L5-1 any position / after L5's set | `web-tail-colour-vt/_note.md:151-153`; `extractor-cascade/_note.md:324-327` | #6 | OK (L5 verified order s1…5, L1, L11, s6) |
| L11 seam-3 after L5's set (offset only); same gate as F1 | `all-reset-postload-colour/_note.md:47-48`, `:100-102` | #7 | OK |
| L5 seam-6 WITH or AFTER L6 seam-3+seam-4, same commit/PR/gate; never alone | `extractor-cascade/_note.md:279-286`; `counters-and-lists/_note.md:86-88` | #31 after #14, #25 | OK; if seam-3/4 are ever reverted or deferred, withhold seam-6 (`extractor-cascade/_note.md:285-286`) |
| L4 seam-1 v2 + hunk-for-orchestrator-1 in the SAME commit, hunk never alone | `small-fixes/_note.md:53-54`, `:215-216` | #8, #9 adjacent | OK (commit-level: keep them in one commit) |
| L3 seam-1 before L7 seam-1; L3 seam-2 before L7 seam-2 | `static-position/_note.md:99-107`; `PLAN.md:586-587` | #10→#11; #19→#21 | OK |
| L7 + L3 seams applied WHOLE (each creates a test file) | `static-position/_note.md:107`; `failure-ink/_note.md:223-225` | `git apply` whole | OK |
| L3 hunk-for-orchestrator-1 WHOLE, order-independent of seams | `failure-ink/_note.md:225-228` | #20 | OK (needs L3's untracked `BakedLayoutSignature.swift`) |
| L3 before L11 (C5) | `PLAN.md:587` | #10/#19 before #13/#23 | OK |
| L6 seam-3 → seam-1 (Compose); seam-4 → seam-2 (Swift; seam-2 fails on bare HEAD) | `counters-and-lists/_note.md:83` | #14→#15; #25→#26 | OK |
| L8 seam-2/4 after L6 seam-4+2; seam-1/3, seam-2/4 independent; after L4 | `vertical-wedges/_note.md:62-63`; `vertical-wedges/skeptic.md:29-31` | #16,#17,#27,#28 | OK |
| L8 hunk-for-L4-1 after L4's StyleApplier edit, ONE commit with its new test | `vertical-wedges/_note.md:59`; `small-fixes/_note.md:223-224` | #32 | OK (L4's StyleApplier is in-tree) |
| L9 seam-1/2 on HEAD, any order | `inline-run-wall/_note.md:101-108` | #18, #29 | OK |
| L10 seams independent, any order; re-cut seam-2 only | `flex-nowrap-gaps/_note.md:144-147` | #12, #22 | OK |
| L11 hunk-for-L3-1 after L3 merges AND after L11 seam-2, in L11's commit | `all-reset-postload-colour/_note.md:111-112` | #24 after #19,#20,#23 | OK |
| L11 seam-1/2/3 + web F1 (+seam-4) in the same gate | `all-reset-postload-colour/_note.md:48`; `…/skeptic.md:96-98` | #13,#23,#7,#30 | OK |
| L6 hunk-for-L12-1 after L12's inject edit (re-add now at `:1326`) | `counters-and-lists/_note.md:115-116`; `instrument-and-calibration/_note.md:159` | #33 | OK (L12's edit is in-tree) |
| L12 hunk-for-orchestrator-1/2 in the SAME commit as the CANVAS_REV bump | `instrument-and-calibration/_note.md:153-156` | #34, #35 | OK (commit-level) |
| L6 seam-1/2 (T5) and L8 seam-1 (M-A) are DEVICE A/B — "never blind-lifted" | `counters-and-lists/_note.md:89-90`; `vertical-wedges/_note.md:134-135` | #15,#26,#16 applied | NOT a violation, but the integrated tree is the INCLUDE arm: the exclude arms in §6 are owed |
No ORDER violation found. The script applies; it does not commit — the five "same commit" rules above are the orchestrator's to keep.

## 3. Sweep — what must be green after integration (single-writer, one sequential pass, `PLAN.md:592-593`)
- **Whole suites** (PLAN + lanes): converter, web runtime, Compose `:runtime`, Android harness `:app:testDebugUnitTest`, `npm -w apps/web-harness run test`,
  Catalyst SwiftUI, tooling `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs`, conformance (`composed-canvas/_note.md:252-253`). L5 ran
  only 4 converter classes, never the whole `:converter:test` (`extractor-cascade/_note.md:352-353`).
- L1: `ColorConversionTest` 29, `ColorParserLinearSpaceTest` 9 (+ HslHue/LchPercent/SyntaxClassifier); `view-transition-bake.test.mjs` 140 (`web-tail-colour-vt/_note.md:79-81`; `…/skeptic.md:347-351`).
- L2: web-harness `tests/ui/ComposedCanvas*.test.tsx` + `ComposedCaptureGallery` 69, tsc 0; Compose `*ComposedIcbClipTest*` 6; harness `screenshot.*` 159;
  Catalyst `ComposedRootStackTests`/`ComposedIcbClipTests`/`UABlockMarginTests` 63; iOS harness app Debug build; harness test bundle never built (§1d) (`composed-canvas/_note.md:233-240`).
- L3: Compose 25 suites 223/0 in-tree, 226/0 with seam-1 (incl. `VerticalBlockFlowSeamWiringTest`); Catalyst L3 suites 73/0, with seam-2 117/0; the hunk's
  25 suites 258/0, with seam-2 281/0 (incl. `VerticalMulticolBakeGateTests`, `VerticalFragmentGeometryTests`, `Wave12BoxSizingBasisTests`) — the hunk was
  run by the lane only in a private copy, by the skeptic in the shared tree under locks (`failure-ink/_note.md:201-203`, `:273-276`; `…/skeptic.md:215-223`).
- L4: Compose `transforms.* spacing.* layout.position.* interactions.* effects.* borders.* color.* StyleApplier*` 1131/0; with seam-1 + hunk
  `SeamReachabilityTest` 3/3, `TransformInheritanceSeamTest` 3/3 (U3 vacuous until seam-1); Catalyst backface + transforms 65/0 (`small-fixes/_note.md:124-126`, `:191-195`, `:206-207`).
- L5: after s1…5 `extract-fixture` 482/0, after s6 483/0; siblings root-scope 6, col-placeholder 4, replaced-src 14, ua-hr 7, table-text 5, post-load-extract
  100, attr-bake 53, bidi-bake 50, widget-appearance-bake 13; converter `AngleParserTest`, `*ColorParserHslHueTest`, `*InvalidDeclarationDropTest`,
  `*GradientPrefixGuard*` 42 (`extractor-cascade/_note.md:87-121`, `:292-296`).
- L6: Compose `lists.* borders.* *MulticolFloatStrip*` 283/0; with seam-3+1 `lists.* borders.sides.* core.renderer.*` 504 run, only L9's 3 red; Swift list
  suites 59/59, with seam-4+2 86 (incl. `ListMarkerEmptyItemRasterTests`, `ListMarkerOutsideHangRasterTests` shipped in the seams); web `tests/lists tests/renderer`
  143; web-harness `tests/sdui` 192 (195 with L11 seam-4); node `counter-style-{bake,author}.test.mjs` 45; `inject-wpt-block.test.mjs` with the L12 hunk
  (`counters-and-lists/_note.md:77-79`, `:91-93`; `…/skeptic.md:17-27`, `:424-427`). **The fix agent's R1 change to `BorderSideApplier.kt` must re-run these.**
- L7: converter `AlignSelf*`, `InvalidDeclaration*`, `SchemaConformance*` 63; web `tests/layout` + conformance 95, tsc; Compose 8 classes 98, with seam-1 160
  (`StaticPositionSeamWiringTest`); Swift 71, with seam-2 77 (`AbsposStaticOffsetSeamWiringTests`, `AbsposCbUsedHeightTests`) (`static-position/_note.md:93-106`);
  PLAN asks to re-run `AbsposOverflowMeasureTest` and `AbsposStaticPositionTests` after the C6 stacks (`PLAN.md:555`, `:567`).
- L8: Compose `ChUnitMetricsTest` 5, `UprightOrthogonalBudgetTest` 4, `VerticalRunIntrinsicsTest` 9, `VerticalInlineAxisTest` 4, `UAWidgetsResolveTest` 11,
  `TableColumnWidthsTest` 4, `CssFontFamilyResolverTest` 10, + `ChUnitInlineAxisSpacingContextTest` 2 (in hunk-for-L4-1); Swift 82 with seam-2+4
  (`UprightChOrangeRasterTests`, `UprightColTableRasterTests` ship in the seams) (`vertical-wedges/_note.md:23-29`; `…/skeptic.md:143-145`).
- L9: Compose `InlineRunFold` 37, `InlineSpanRing` 18, `GreedyLineBreaker` 23, `PreBreakPipeline` 16, `PlaceholderOverflowMarker` 8, `LineClampUnderPreWave39` 11 =
  113 (with seam-1) + `BlockFlowFidelityWave3`, `InlineSpanContent`, `WordBreakOpportunities`, `SoftHyphenPolicy`; Catalyst six classes 121 with seam-2
  (`inline-run-wall/_note.md:116-124`; `…/skeptic.md:33-35`, `:236-245`).
- L10: Swift `FlexNowrapIR.swift`, `FlexNowrapOuterTests.swift`, `FlexNowrapRasterTests.swift` + GapDecorationHook/Raster/Segments/Bands, FidelityWave2,
  EmptyFlexContainer, FlexWrapStretch, FlowLayoutDistribution (74–80); Compose `FlexNowrapLineTest.kt`, `GapNowrapShiftAndSnapTest.kt`, `layout.flexbox.* columns.*`
  413, + `SeamReachabilityTest`, `BlockBoxAlignItemsTest` 418 with seam-2 (`flex-nowrap-gaps/_note.md:49-53`, `:149`, `:185-190`; file names from the tree).
- L11: Compose `AllResetTest` 8, `ContentsUnboxingTest` 11, `InheritanceAndFlexGatingTest`, `BackgroundColorInheritanceTest`, `TransformInheritanceSeamTest`; Catalyst
  `AllResetTests` 7 + `ContentsUnboxingTests` 11 + InheritanceWave9/IOSTextLane/Phase10/UAWidgets/FidelityWave1; web `globalPhase10.test.ts` 8 (+ conformance 74);
  `post-load-extract.test.mjs` 102 with seam-3; extract-fixture ua-link pins; web-harness `tests/sdui` 23 files / 195 with seam-4
  (`all-reset-postload-colour/_note.md:52-69`; `…/skeptic.md:30-35`, `:125`).
- L12: `capture-browser-ref` + `inject-wpt-block` + `score-gate` 176; consumers safe-name 10, section-runner 19, wpt-not-applicable 265, view-transition-bake 140,
  post-load-extract 100, bidi-bake 50, svg-preraster 20; `wpt-white-canvas` + `noto-pilot` with the hunks (`instrument-and-calibration/_note.md:150`; `…/skeptic.md:27-29`, `:73-78`).
- **Expected red until its patch lands (all fixed by ORDER):** `extract-fixture.test.mjs` 19 L5 pins until s1…5 (`extractor-cascade/skeptic.md:271`; L1/L12 quote 20 —
  pre-fix-pass count, `web-tail-colour-vt/skeptic.md:366`, `instrument-and-calibration/_note.md:177`); `PlaceholderOverflowMarkerTest` ×2 + `LineClampUnderPreWave39Test` ×1
  until L9 seam-1 (`inline-run-wall/_note.md:123`; `small-fixes/skeptic.md:56-58`; `counters-and-lists/_note.md:134-136`; `flex-nowrap-gaps/skeptic.md:37`);
  `FlexNowrapRasterTests` until L10 seam-1 (`flex-nowrap-gaps/_note.md:159`); `wpt-white-canvas`/`noto-pilot` 4 red until L12's hunks (`instrument-and-calibration/_note.md:156`);
  `SeamReachabilityTest` red if L4's hunk lands without seam-1 (`small-fixes/_note.md:190`).
- **Expected red AFTER integration unless fixed:** `wpt-white-canvas.test.mjs` "swiftui: WPTCanvas is white…" (§1d).

## 4. L12 calibration state
- **CANVAS_REV** `white-black-ink-font-lh-imgpad-htmlpins` → `white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin` (ROOTBG: canvas background on
  `:where(html)` only; UAMARGIN: `UA_BODY_CSS` when the test declares a body margin and the ref does not); `LIVE_CANVAS_REV` bumped, old rev appended to
  `KNOWN_STALE_CANVAS_REVS` (`instrument-and-calibration/_note.md:29-43`). No runtime change (`…/_note.md:5-6`).
- **New ref tree** (gitignored): `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/` (+ its
  `chrome-151.0.7922.47/` copy), holding ONLY the 30 gate sections' 1435 refs; 1412 cloned byte-identical (set digest 8c55b96b… old = new), 23 re-rendered;
  old tree untouched (digest 617cb0b6…) (`…/_note.md:88-94`, `:176`; `…/skeptic.md:59-62`).
- **The 23 re-frozen refs:** CSS2 s-11-1-1b-005/-006; initial-background-color; display-contents-root-background; display-contents-sharing-001; gaps-033/034/035/036/037;
  clip-path-{contentBox,paddingBox}-{1d,1e}; position-{absolute,fixed}-root-element-{flex,grid}; hanging-punctuation-block-bound-001; calc-in-media-queries-001/002;
  column-span-during-transition-doesnt-skip; backdrop-filter-root-element (`refreeze-apply.post.json`). 8 were NOT in the plan's list (`…/_note.md:80-86`); revert recipe `…/_note.md:87`.
- **Run dir** `tools/titan/runs/wave52-calib` = inject-only rescore of `wave52-open` captures; 30/30 rc 0 (`_status` lists 29 — css-masking ran separately, D8);
  4305/4305 capture PNGs byte-identical; 4217 cells unchanged; 88 moved = 19 stamp + 69 re-frozen-ref, 0 unattributed (`…/_note.md:99-103`; `…/skeptic.md:64-69`, `:123`).
- **Score `wave52-open → wave52-calib`: gained 13 · lost 8 · newly measured 0 · unmeasured-now 19**; totals web 1211/1372, iOS 1084/1363, Android 1075/1363 (`…/_note.md:104-105`).
  - GAINED 13: initial-background-color ×3 (PRE-REGISTERED, PLAN MED); gaps-033 web (PRE-REGISTERED, C1); gaps-034 ×3 and gaps-035 ×3 (NOT pre-registered as
    instrument — PLAN credited them to L2/L10, `PLAN.md:521`); backdrop-filter-root-element ×3 f→P (NOT predicted) (`…/_note.md:106-110`).
  - LOST 8: gaps-033 ios/android P→f (PRE-REGISTERED, C1); display-contents-root-background ×3 P ~1.0→f 0.535 (NOT pre-registered; old P degenerate);
    column-span-during-transition-doesnt-skip ×3 P 0.9879→f 0.9332 (PLAN: "stays P only with L1's F-B", `PLAN.md:521`) (`…/_note.md:111-118`).
  - UNMEASURED-NOW 19 (absence-only stamps): background-color-transparent-animation-in-body ×3, background-color-animation-with-zero-alpha ×3,
    scope-implicit-crash-print ×3, flexbox_inline-abspos ×3, gradient-refcrash ×3, backface-visibility-hidden-006 ×3, animation-name-ua-prefix web (`…/_note.md:61-68`).
  - Movers holding verdict: masking 1d/1e ×12 → ≥0.9997 (degenerate→honest, predicted); s-11-1-1b-005 web P 0.9947→0.9658 (degenerate: no black square);
    s-11-1-1b-006 web 0.9656→0.9923 (`…/_note.md:119-123`).
- **Disagreement L12 ↔ L1 on column-span ×3:** L12 says "L1's F-B may pay these back" (`…/_note.md:137`, `:160`); L1 MEASURED that F-B does not stamp it
  (ring non-uniform) and warned that a re-freeze alone makes it P→f with no L1 remedy (`web-tail-colour-vt/_note.md:113-116`, `:154-159`). L12 did re-freeze it
  → **pre-register −3, no payback.**
- **What the closing gate is scored against:** PLAN — record = `score-gate.mjs wave51-fix wave52-final --watch …/watchlist.txt --movers 0.005`; instrument-only
  attribution against the L12-B run; render lanes scored against that run (`PLAN.md:592-593`, `:638`, `:658-660`). L12 gives its own share "against wave51-fix"
  and says the calibration adds no render effect (`…/_note.md:131-142`, `:174`); it makes no other baseline recommendation. So: (a) wave51-fix → final = the
  record, where PLAN §7's "lost = 2" is falsified — L12 alone loses 8 (all instrument, capture-identical) (`…/_note.md:139-141`); (b) wave52-calib → final = the
  render-lane attribution (lanes predict P→f 0 there). PLAN §7 must also read `blank-captures=45` (not 36) and unmeasured-now 21 (19 + L6's 2) (`…/_note.md:142`;
  `…/skeptic.md:111-115`).

## 5. Closing-gate expectations per lane (against wave51-fix unless stated)
- **L1** +3 web +6 native `css-color/display-p3-linear-001/-002/-003` HIGH; +4 web +8 native `css-view-transitions/fractional-box-with-{shadow,overflow-children}-{new,old}`
  (web HIGH, natives MED-HIGH); P→P rises -004/-005/-006 ×3; negative controls (must not move): capture-with-visibility-mixed-descendants, class-specificity,
  clip-path-larger-than-border-box-on-child-of-named-element, far-away-capture, element-with-overflow, column-span ×3 (`web-tail-colour-vt/_note.md:134-147`).
  Tripwire: css-view-transitions `extract.log` must show `(frame-ring rgb(255, 182, 193) stamped)` on exactly the 4 fractional-box tests (`…/_note.md:18`, `:107-112`).
- **L2** +6 HIGH clip-path-ellipse-006/007/008 ios/android; +2 HIGH text-decoration-propagation-shadow; +2 HIGH align-items-007; +1 HIGH flex-gap-decorations-027 web;
  +2 MED s-11-1-1b-006 ios/android; Fix A: gaps-040 web 1.0, block-ellipsis-028 web 1.0, position-absolute-semi-replaced-stretch-input web f→P MED; DEGENERATE (label,
  never claim): gaps-034 ×3, contain-inline-size-bfc-floats-001 ios/android, anchor-position-multicol-007 android; report backdrop-filter-basic-blur ios ~0.9526; P→f 0;
  closest borders-002 android 0.9594→0.9581 (`composed-canvas/_note.md:175-194`). Tripwire: frame-ink census overrun-right + overrun-left 208 + 84 = 292 → **0 / 0**
  (method: same-width pair, frame pixel off the ref by >8/255, scored = numeric ssim, no `scoreExcluded`) (`…/_note.md:102-104`, `:252`; `…/skeptic.md:64-68`). Any T3 row
  in LOST falsifies §0b (`…/_note.md:250`).
- **L3** display-contents-float-001 ios/android f→P HIGH; abspos-containing-block-outside-spanner ios f→P MED-HIGH — **PNG-check for ZERO red before calling it a fix**
  (scorer passes a falsified picture at 0.9712); flexbox_align-items-stretch-writing-modes ios f→P MED (skeptic: HIGH by executed render); movers abs-pos-border-offset-003,
  baseline-vertical; must-not-move: anchor-position-multicol-007…017, -colspan-003, input-range-zero-inline-size, td-different-subpixel ios/android P
  (`failure-ink/_note.md:171-192`; `…/skeptic.md:106-120`).
- **L4** composited-under-rotateY-180deg-preserve-3d ios/android f→P HIGH; css-transform-inherit-scale android f→P MED-HIGH (needs seam-1); b″ 0 movers (A/B §6);
  must-not-move backface-visibility-hidden-animated-001/002 ios P 0.9646 and the 0(c) HOLD tripwires (css-transform-3d-transform-style android P 0.9577, two backdrop-filter-*3d*)
  (`small-fixes/_note.md:102-115`).
- **L5** import-conditional-001/-002 f→P web ×2 + natives ×4 HIGH; color-mix-percents-02 web HIGH, natives ×2 MED; flex-gap-decorations-024 ×3 P→P ≈1.0 MED;
  revert-val-002 web degenerate→honest; F-E with L6 s3/s4: 0 flips, 18 native cssom P stay P (−9/−9 if seam-6 lands alone); at risk important-prop ×3
  (`extractor-cascade/_note.md:236-272`).
- **L6** armenian-008 web f→P HIGH; armenian-008 ios/android → UNMEASURED NOW (adjudication A); cssom pad/prefix-suffix `-invalid` web f→P MED-HIGH; natives
  pad/prefix-suffix/negative `-invalid` ×6 MED-LOW; counter-suffix ios f→P MED (needs T5 seams); P→P picture rises armenian-006/-007/-009, georgian, 5 more cssom `-invalid`
  web (`counters-and-lists/_note.md:95-111`). Tripwire (T2, no flip): Android orange rows multicol-003 161–163, balancing-003 171–175 (`…/_note.md:102-103`).
- **L7** css-flexbox/abspos/position-absolute-containing-block-002 ios f→P MED-HIGH; 16 Android grid-abspos align cells and 10 web last-baseline → 1.0; drops (no flip):
  vertWM-003/004 android ~0.967, vertWM-last-baseline-003/004 ios 0.9950; P→f 0 (`static-position/_note.md:115-123`).
- **L8** input-range-zero-inline-size ios +1 HIGH, android +1 MED-HIGH; ch-units-vrl-003/-004 ×4 (iOS MED-HIGH, Android MED); ch-units-vrl-005/-006 ios +2 MED-LOW,
  android +2 LOW; at risk: M-A 10 cells + bidi-lines-001/002 ios + hyphenate-character-005 ios (`vertical-wedges/_note.md:111-132`).
- **L9** block-ellipsis-025 ios f→P MED-HIGH; hyphens-manual-inline-012 android f→P MED; block-ellipsis-032 android thin P MED-LOW; 032 ios ≈0.98; 030 ios 0.9983→0.9989;
  hanging-punctuation-inline-001 NO flip (−0.004/−0.007); named P→P movers 023/024 android (seam-1 call site), 19 Android clamp hosts gain "…" (`inline-run-wall/_note.md:249-301`).
- **L10** background-clip-content-box-002 ios f→P HIGH (falsifier: any red); flex-gap-decorations-027 ios MED-HIGH, android MED (needs L2 M1 + seams); rises 008 android,
  045/046; P→f 0; also attribute snap movers 024/029/030/034–037/050 (≤0.0014) to L10 (`flex-nowrap-gaps/_note.md:105-118`, `:191-195`).
- **L11** selectors/has-visited ios f→P MED (corrected sim 0.9573); colour cells P→P picture-correctness; at risk all-prop-001 ×3 (OPEN ITS PNG), all-prop-initial-visited ×3
  (neutral only with seam-3), display-contents-{button,details,fieldset} web LOW-MED (`all-reset-postload-colour/_note.md:131-155`; `…/skeptic.md:39-40`).
- **L12** gained 13 / lost 8 / unmeasured 19 as in §4; inject summary must print `absence-only=19 blank-captures=45` (`instrument-and-calibration/_note.md:131-142`).
- **Gate-wide:** fixture net `BASELINE=1 ./test-all.sh --gate-set` exit 0 ×8 (L11 hunk excluded until RV-M1 is closed); `watchlist-check.mjs` `unmatched 0` on the
  merged list (`PLAN.md:594-595`); every gained cell PNG-checked against the re-frozen ref (`PLAN.md:643`).
- **watchlist-additions.txt to merge into `wave52-plan/watchlist.txt`** (176 active lines today; total / active lines): L1 21/5 · L2 9/3 (+5 S1 lines, §1d) ·
  L3 22/5 · L4 18/0 (nothing; the 5 b′ lines must stay commented) · L5 27/21 · L6 43/34 · L7 9/4 · L8 18/8 · L9 6/3 · L10 17/14 · L11 3/1 · L12 23/21 → 119 active lines.

## 6. Device A/Bs owed at/after the gate (each run records the INSTALLED `base.apk` sha1 / `.app` hash against the build it claims to test — `PLAN.md:589-591`, `:647`)
- **L4 b″** (in-tree MarginApplier/PaddingApplier): `cmp` the four `css-sizing/abspos-auto-sizing-fit-content-percentage-001…004` Android PNGs vs `wave52-open`; 0 movers on
  `css-position/position-relative-002/-008 android`; exclude arm = the `wave52-open` capture; hash: `adb shell pm path` → `adb pull` → `shasum -a 1` vs
  `apps/android-harness/app/build/outputs/apk/debug/app-debug.apk` (`small-fixes/_note.md:65-69`; `…/skeptic.md:107`).
- **L6 T5** (outside-marker hang): include = integrated tree (L6 seam-1 #15, seam-2 #26); exclude = `git apply -R` of L6 seam-1 and seam-2 only (keep seam-3/4 — F-E
  coupling); cells: counter-suffix ios, counter-list-item(-2/-3), add-inline-child-after-marker-001/002, first-line-and-marker, first-letter-exclude-inline-marker,
  change-list-style-type-001 (`counters-and-lists/_note.md:16`, `:89-90`, `:109`; `watchlist-additions.txt:4-30`). Run `git apply -R --check` on the integrated tree first
  (L8/L9 patches land on the same files after them).
- **L8 M-A** (ch face): include = integrated tree; exclude = Compose `git apply -R vertical-wedges/seam-1.patch` + iOS `git apply ma-exclude-ios.patch` (reverse after;
  never commit); cells: the M-A at-risk list + ch-units-vrl flips (`vertical-wedges/_note.md:126-135`, `:60`).
- **L9 F2** (glyphless ring, 032): arm A = the wave51-fix capture with its recorded `.app` hash, or the lane build minus F2 (no `drop-F2.patch` exists — F2 spans the Swift ring,
  fold predicate, band helper and a seam-2 hunk); arm B = integrated build, installed `.app` hash recorded (`inline-run-wall/_note.md:331-335`; `…/skeptic.md:167-172`).
- **L9 F1** (hanging-punctuation): arm A = integrated + `drop-F1.patch`; arm B = integrated; cell hanging-punctuation-inline-001 ios/android; apply drop-F1 for good only if
  the A/B confirms (`inline-run-wall/_note.md:225-234`, `:269-272`, `:335`).

## 7. BACKLOG / STATUS text handed over (point, do not copy)
- L1 → "Web tail residuals" item 5: shipped F-A/F-B + open items (prophoto arm, shadow-aware window, 5 null-ring authors) — `web-tail-colour-vt/_note.md:160-163`.
- L2 → new "Composed canvases (wave 52 L2)" paragraph — `composed-canvas/_note.md:254-260`.
- L3 → queue 0(k): F1/F2/F3 shipped, F4/F5/F6 queued — `failure-ink/_note.md:231-232`.
- L4 → 0(b′): "the §10.1 republish is NOT zero-movement…" — `small-fixes/_note.md:220-222`.
- L5 → ranked item 0 tails (a′), (a″), (a‴), (e) replaced by CLOSED text + a new residual for item 5/0 (propsForBodyRoot) — `extractor-cascade/_note.md:304-316`.
- L6 → item 2 (c³) CLOSED; item 3(b) paint-clamped; new "outside markers hang (seam-1/2 device A/B)"; "author @counter-style resolved by the bake" — `counters-and-lists/_note.md:120-122`.
- L7 → new "Static position (wave 52 L7)" paragraph — `static-position/_note.md:138-143`.
- L8 → no paragraph handed over (none in the note).
- L9 → queue 4 "Inline-run wall, corrected map": (c) refuted half + (a) — `inline-run-wall/_note.md:354-355`.
- L10 → "css-gaps residuals (wave 52 L10)" paragraph — `flex-nowrap-gaps/_note.md:150-155`.
- L11 → 0(m) replacement — `all-reset-postload-colour/_note.md:115-119`.
- L12 → "## Instrument decisions pending": paste the PLAN §2 L12 Decision-B blockquote (`PLAN.md:533-548`) replacing the "blank-capture guard — DECIDE IT" bullet, BUT
  replace "36 rows / 15 tests — 33 scored, 3 already excluded" with the amendment at `instrument-and-calibration/_note.md:161-162`, append "`inject-wpt-block.mjs` also stamps
  `refUniform`" (`…:163`), and turn the "erased-reference body background" bullet into EXECUTED with the §4 numbers, naming the 8 extra refs (`…:164`). 0(n) Decision A
  (absence-only) is recorded in `corpus-v6-18.json` after the gate (`PLAN.md:510`).

## 8. Open should-fix / nits (not fixed) → BACKLOG queue entries
- L1: bail-path mint still tagged `baked-view-transition-tree` (`web-tail-colour-vt/_note.md:19`); census srgb 40/12 vs 43/14 + unlisted lch row (`…/skeptic.md:398-400`);
  converter `rgb(100%)` → 254 rounding (`…/skeptic.md:35-36`).
- L2: `blockMarginsInFold` unpinned both natives (SK-K1/S1); `LocalContainingBlock.widthPx` minus body margin unpinned (SK-K2); negative-margin offset unpinned (SK-K3);
  `isSelfCollapsingRoot` lacks §8.3.1 BFC clause; harness test bundle never compiled; note §6 "2 docs only" vs 35 roots / 24 docs (S1); positive-z lift unpinned (N1);
  Swift rule 3 `runs`/`pseudos` asymmetry (N2); census/replay scripts overwrite their JSON (N3); `ComposedCanvasMarginTest.kt` 205 lines; `UaBlockMargins.kt` 669 /
  `ComposedRootStack.swift` 674 need a split; nested T3 (7 tests); `display: unset` → Generic (`composed-canvas/_note.md:214-231`; `…/skeptic.md:103-117`, `:242-262`).
- L3: files over target (ContentsUnboxing.kt 214, .swift 206, tests 228/226, FixedHoist.swift 538) (`failure-ink/_note.md:206-207`); -017 "PASSES" rationale is a degenerate pass
  (RV-N1); hunk header omits its `BakedLayoutSignature.swift` prerequisite (RV-N2); no raster pin for an authored W+H sole child (RV-N3) (`…/skeptic.md:281-286`).
- L4: K4 block-form `graphicsLayer { alpha = 0f }` survives (`small-fixes/skeptic.md:197-203`); iOS keeps the culled element's own background/border (documented divergence);
  own text under CULL_OWN_FACE un-breadcrumbed (`small-fixes/_note.md:23-30`).
- L5: D2 `selectorSpecificity` throws on a trailing-backslash selector; D3 non-finite angles reachable; D4 pin gaps SK1/SK8/SK4; N1 `keepAll` dead; R1 PLAN §2/§4 still say
  "F-E 0 alone" / L5 whole at step 3; R2 Android one-band is code-reading (`extractor-cascade/_note.md:364-379`; `…/skeptic.md:370-375`).
- L6: **R1 must-fix** `doubleGeom` mirrors the inner line (p1–p7; 0 gate cells; fix in progress) (`counters-and-lists/skeptic.md:381-416`); S1 Compose seam-1/seam-3 have no
  source pin; N2 `<string>`/CSS-wide keyword guard; N3 BorderSideApplier.kt 565 / NodeRenderer.ts 432 / counter-style-bake.mjs 447; N4 Swift hang layout silent `.zero`;
  N6 uncommented runs (`counters-and-lists/_note.md:157-161`).
- L7: seam-2 coupling (AbsposStaticOffset.swift REQUIRES seam-2, else 4 docs over-shift) undocumented in the header; fold also moves fixture `flex-align-items.json AI_SelfEnd`;
  nits 3–8 (108 vs 112 movers, AbsposStaticAlignment.swift 279, flex/grid-align-self fixtures lack the new variants, K3b pins known-wrong `normal`); T4 open
  (`static-position/skeptic.md:55-78`; `…/_note.md:141-143`).
- L8: M-G second clause unbuilt; `text-orientation` not inherited; 007/008 unfixable; TableBoxTree.swift 318 / ChUnitMetrics.kt 270; colspan/`span` absent from the wire
  (v3-gated); loader race residual (`vertical-wedges/_note.md:47-49`, `:158-174`; `…/skeptic.md:188-190`).
- L9: R1 `PreBreakPipeline` `source = text` unpinned (SKK1 survives); R2 `markedLine` per display string; R3 em-dash cases; S2 seam-2 wiring unpinned; seam-1 call-site
  argument unpinned; N2 silent fallthroughs note-only; GreedyLineBreaker 566/463 → split to `BlockEllipsisClamp` (`inline-run-wall/skeptic.md:328-346`; `…/_note.md:71-75`, `:303-328`).
- L10: RTL wiring unpinned (V6/V7 survive); iOS bare-number percent margin/padding read as px (#2); `Line` measure body unpinned (#3); nits 5–10 (gate clauses, box-sizing fit,
  non-px margins silent, CSSFlexLayout.swift 429 / GapDecorationsPainter.swift 302 / GapDecorationHook.kt 222, unowned test edit, 6(c) note-only); legacy FLEX_ROW
  (`flex-nowrap-gaps/skeptic.md:99-113`, `:166-179`; `…/_note.md:137-139`).
- L11: **RV-M1 must-fix** (gate-list hunk, §1a; fixture fix in progress); S1 IR order ≠ cascade order; S2 natives don't model non-inherited INITIAL / REVERT values; N1 seam
  wiring unpinned; N2 `ol { all: initial }` gets UA decimal; RV-S1 DirectionSurvives cannot fail on direction; RV-N1 calibrateStyles floor drop (`all-reset-postload-colour/_note.md:194-202`;
  `…/skeptic.md:89-101`, `:159-169`).
- L12: D1 `refUniform` wiring unpinned (SK1); D2 comment omits `view-transition-bake.mjs`; D4 commit the digest (§1d); D5 root-relative links unpinned, scheme hrefs skipped
  silently; D6 comments; D7 M7/M7a naming; D8 `_status` 29; display-contents-sharing-001 ref renders injected CSS text; host glyph drift — any future bump must clone
  unmoved refs (`instrument-and-calibration/skeptic.md:105-123`; `…/_note.md:166-171`).

## 9. Other risks to the gate
- **Two must-fix fixes landing mid-integration** (L6 R1 `BorderSideApplier.kt`, L11 RV-M1 fixture): re-run L6's 283-test Compose set and the T2 tripwire, and L11's oracle +
  pair proxy, after they land; the integrate dry run did not cover them.
- **L2 Release-build SILGen crash** in `ComponentRenderer.swift` `styledContent(now:)` (seen with other lanes' seams transiently applied; Debug builds succeed) — try a
  Release/harness build of the INTEGRATED tree early (`composed-canvas/_note.md:203-206`).
- **PLAN §7 is stale in three numbers** (lost 2 → ≥8, blank-captures 36 → 45, unmeasured 21 → 42 if L6's na tags land) — amend before scoring or the gate reads as
  unexplained (`instrument-and-calibration/skeptic.md:111-115`).
- **Disagreement on gaps-034 ×3:** PLAN/L2 label it a DEGENERATE scorer flip (`PLAN.md:644`; `composed-canvas/_note.md:186`); L12 PNG-checked it as an honest
  instrument-only gain with matching rules (`instrument-and-calibration/_note.md:109`; `…/skeptic.md:87`). Decide the label from the final PNG.
- **Device-A/B exclude arms on an integrated tree:** `git apply -R` of L6 seam-1/2 and L8 seam-1 must be `--check`ed after all 35 patches; if they do not reverse,
  cut exclude patches against the integrated tree (the hash rule still applies).
- **Android-only predictions rest on code never executed on the JVM:** L10 `FlexNowrapLine.Line` (`flex-nowrap-gaps/_note.md:122-124`), L8 Compose line box / `widthIn(min)`
  (`vertical-wedges/_note.md:160-162`), L6 empty-item pitch (`counters-and-lists/_note.md:127-128`), L2 `clipRect`/`zIndex` in the composed layer (`composed-canvas/_note.md:198-201`).
- **L12 ref tree covers only the 30 gate sections**; any test added to the gate set re-renders lazily with host glyph drift (`instrument-and-calibration/_note.md:170-171`, `:176`).
- **L5 post-load differential includes L11's F2 colours** — attribute the 13 gainer tests to L11, not F-D (`all-reset-postload-colour/_note.md:113-114`).
- **L3/L7 lock and scratchpad incidents** (shared `…/scratchpad/dd`, a released foreign lock, a seam test file briefly left in the tree) — check `wave52-lock/` is empty and
  no seam-created test file exists before running `integrate-seams.sh` (a pre-existing file makes `git apply` refuse) (`failure-ink/_note.md:208-219`; `static-position/_note.md:108-111`).
- `integrate-seams.sh --dry-run` replays `git diff HEAD` only: untracked new files are absent from its worktree, so the dry run proves APPLY, not compile.

# failure-ink — wave 52 plan brief

Read-only planning lane, 2026-09-25. Evidence run: `wave51-fix`
(`tools/titan/runs/wave51-fix/sections/<sec>/{manifest.json,screenshots(web),ios-screenshots,android-screenshots,per-test-ir}`);
frozen refs `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`.
Every capture named below was OPENED beside its ref (4-panel contact sheets, full 390-px scale) — the defects
are described from pixels, the numbers from a read-only pixel probe (red = (255,0,0)-class px; bbox in canvas px).
Census beside this brief: `failure-ink-census.json` (every failing/passing red cell with a hand-assigned mechanism,
the 7 sampled cells' current state, and the IR carrier census over all 1435 per-test IR docs).

## 1. Queue item covered

`docs/BACKLOG.md` → "## Ranked queue (wave 51+)" → item 0 "Wave-49/50 discoveries, retro corrections and the
wave-50 skeptic findings" → **(k) "The test's own failure-indicator ink reaches the canvas — 7 cells"**
("splits by mechanism rather than by platform — orthogonal-flow / available-size sizing (012, 031, 068),
fragmentation (046, 091), anchor-position (038), float/clear (098). Worth a lane."). Indices resolve through
`tools/titan/results/wave50-B12/handverdicts.json` (population = wave49-final PASSING cells):
012 `css-writing-modes/flexbox_align-items-stretch-writing-modes` android · 031 `css-writing-modes/available-size-014` web ·
038 `css-anchor-position/anchor-name-multicol-003` ios · 046 `css-break/block-in-inline-009` ios ·
068 `css-writing-modes/available-size-001` ios · 091 `css-multicol/column-height-007` android ·
098 `CSS2/css21-errata/s-11-1-1b-008` android. Correction to the queue text: 098 is NOT float/clear — the test is
"overflow:hidden on a fixed-layout table with a caption" (CSS 2.1 errata s.11.1.1b); its mechanism is the table-box
vs table-wrapper-box clip (§3, 098).

Scorer facts that frame every prediction (`tools/titan/inject-wpt-block.mjs`): `computeWptPass` (:627-651) =
presence veto → colorFailed veto → coverageRatio veto → ssim ≥ 0.95; the novel-ink (:671) and degenerate (:711) vetoes
are OFF by default, so `novelInkFailed: true` alone never fails a cell (204 PASSING cells carry it). Red ink flips a
cell only through `colorFailed` (:830-837, needs colorDivergent AND ΔE mean ≥ floor) or by dragging ssim under 0.95.

## 2. Target cells (cheapest first) — FAILING cells this lane can flip

| # | cell (cite) | visible defect in the capture | what the ref shows |
|---|---|---|---|
| T1 | wave51-fix css-writing-modes/flexbox_align-items-stretch-writing-modes ios f 0.999 (cF=1, red 10 000 px bbox (166,88)-(265,187)) | green 150×100 at x 16-165 then a RED 100×100 block at x 166-265: the two `writing-mode: vertical-*` flex items are 50 px wide (their two 50×50 squares stacked VERTICALLY), so the 250-wide `.error` abspos shows through its right 100 px. Android twin identical (P 0.998, same 10 000 red px, colorFailed just under the floor). | one 250×100 green rectangle (16,88)-(265,187), no red |
| T2 | wave51-fix css-display/display-contents-float-001 ios f 0.9417 · android f 0.941 (cF=0, red 6 891 / 6 904 px) | a full-width RED bar 358×20 at y 88-107 behind the word "PASS" | the word "PASS" on white, nothing else |
| T3 | wave51-fix css-multicol/abspos-containing-block-outside-spanner ios f 0.9553 (cF=1, red 20 000 px) | RED 100×100 at the canvas top-left (16,16); the green 100×100 that should cover it sits 100 px lower, under the paragraph (its `top:0` resolved against the `position:relative` column item instead of the ICB); the bottom-right pair is off-canvas. Android P 0.999 (wave-49 lane A7 fixed it there) | two green 100×100 squares at the ICB's top-left and bottom-right corners, no red |

Sampled cells of 0(k) — all seven PASS in wave51-fix, so they are CORRECTNESS work (0 gate flips), ranked by cost:

| idx | cell (cite) | visible defect | ref |
|---|---|---|---|
| 098 | wave51-fix CSS2/css21-errata/s-11-1-1b-008 android P 0.9953 · ios P 0.9962 (red 100 px = 20×5 at (16,68)-(35,72)) · web P 0.9941 (red 180 px, 20×9) | a red 5-px strip above the black 20×20 square: the `<div>`'s 10-px red `border-top` (margin-top −15px) is clipped 5 px too LOW — the clip starts at the table WRAPPER box (which includes the empty `<caption>`'s 10-px margin-bottom), not the table box | black square only |
| 046 | wave51-fix css-break/block-in-inline-009 ios P 0.9907 · android P 0.9966 (red 500 px = 50×10 at (66,88)-(115,97)); ios green runs to y 197 | column 2 of the 2-column, `height:200px; column-fill:auto` multicol starts with the 10-px spacer (red shows) then the 40-px border + 60-px block; iOS overflows 10 px below the square. Both natives distribute WHOLE children with the greedy shortest-column rule instead of §7.2 sequential fill against H=200 | filled green 100×100 |
| 068/031 | wave51-fix css-writing-modes/available-size-001 ios P 0.9771 · android P 0.9791; available-size-014 web P 0.9879 · ios P 0.9771 · android P 0.978 (identical iOS captures for 001 and 014) | iOS: red `0` glyph box 13×25 at (16,88) and the green `0` box 25 px LOWER (y 112-125) — the vertical-rl run never wraps at the scroller's 8ch block size, so the 5th glyph is not on the second line; Android: the red `#red` aside is drawn as a HORIZONTAL 32×20 box and no green at all (span background dropped); web 014: red 25×12 where the green should be and GREEN=0 (span clipped out of the `overflow:hidden` scroller — wrap differs) | one green 25×12 box at (16,100)-(40,111) |
| 038 | wave51-fix css-anchor-position/anchor-name-multicol-003 ios P 0.9804 · android P 0.9598 (red 3 000 px) · web P 1.0000 (red 1 500 px, nIF=1) | natives: THREE red 20×50 bars — the red `.filler` fragmented over 3 columns with none of the anchor-positioned `.target::before` green covering it (no anchor() support); web: 10-px red bands left/right of a 70-wide green — Chrome's `anchor-size()` on an abspos anchor differs from the ref | one green 90×50 at (76,88)-(165,137) |
| 091 | wave51-fix css-multicol/column-height-007 web P 0.9831 · ios P 0.9749 · android P 0.9716 (red 2 500 / 4 000 px) | ALL THREE fail the picture (web too): css-multicol-2 `column-height` + `column-wrap` are not in the reference browser build. Not a runtime defect this wave can own — exclude | filled green 100×100 |

## 3. Mechanism, with file:line per platform

**T1 — the Z2 vertical-block-flow seam's "baked-layout guard" misfires on AUTHORED `width:50px; height:50px`.**
The seam that stacks a vertical-writing-mode container's block children horizontally (css-writing-modes-4 §6) exists on
both natives and is gated, among others, by "any in-flow child carries BOTH Width and Height ⇒ post-load-extracted
wire ⇒ keep the frozen Column/VStack":
Compose `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt:2781-2785`
(`anyBakedChild`), consumed at `:2800-2807`, seam at `:2814`; the same two-property signature in
`…/columns/MulticolSpannerFlow.kt:322-323` (`ChildSpec.bakedPhysicalSize`, `:169`) read by `…/columns/VerticalMulticolMeasure.kt:93`.
SwiftUI `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift:4354-4361` inside
`verticalBlockFlowZ2()` (`:4311`), reached from `:1823`. The 012 IR (`per-test-ir/wpt__css-writing-modes__flexbox_align-items-stretch-writing-modes.json`)
is `postLoadExtracted:false`; its `.square` children carry exactly `Width{px:50}` + `Height{px:50}` → guard trips →
squares stack vertically → item 50 wide. A REAL baked wire (`per-test-ir/wpt__css-anchor-position__anchor-name-multicol-003.json`,
`postLoadExtracted:true`) puts Position + BoxSizing + all four Margin*/Padding*/Border*Width/Style/Color + Display +
OverflowX/Y on EVERY box — the wire carries no explicit baked marker (0 hits for postLoad/baked in that IR; the
`_wpt.postLoadExtracted` stamp lives in the fixture envelope, `tools/titan/inject-wpt-block.mjs:1584-1609`).

**T2 — `display: contents` unboxing is refused when the element floats, on a misread of css-display-3 §2.7.**
Compose `…/core/renderer/ContentsUnboxing.kt:71-74` ("Float blockifies too (§2.7); any non-none float keeps the box");
SwiftUI `Renderer/ContentsUnboxing.swift:65-66` (same clause). §2.7's blockification sentence ends with the
parenthetical that it "has no effect on display types that generate no box at all" (none, contents) — a floated
contents element still generates NO box; the test's ref and Chrome agree (web P 1.0000). The IR is one root
component: `Display=CONTENTS, Float=RIGHT, BackgroundColor=red, text "PASS"` → the box is kept and its red
background paints as a full-width block.

**T3 — the spanner containing-block chain has no iOS twin.** css-multicol-1 §6: a `column-span:all` box is laid
out as a block of the MULTICOL CONTAINER, so CSS 2.1 §10.1's nearest-positioned-ancestor search for its abspos
descendants must skip the `position:relative` column item. Compose implements it in
`…/columns/MulticolSpannerContainingBlock.kt:119-160` (`childPositionedAncestor` / `childPositionedAtMulticol`),
called from `…/layout/position/CanvasRootHoist.kt:455-490`. `grep -rn SpannerContainingBlock runtimes/swiftui/Sources`
→ nothing; iOS's chain is the `hasPositionedAncestor` parameter of `Renderer/FixedHoist.swift:111-118`
(`rendersInFlowAsStaticPosition`) plus the per-container overlay partition `Renderer/ComponentRenderer.swift:765`
(`isOutOfFlow`) / `:2050` (`positionedChildren`) — the relpos column item collects the spanner's abspos children.

**046 — greedy whole-child distribution, not §7.2 sequential fill.** Compose `…/columns/MultiColumnApplier.kt:1016-1023`
→ `…/columns/MultiColumnDistribution.kt:63-84` (shortest running column wins); the H-aware run pass
(`:795-838`, `MulticolRunFragmentMeasure`) exists but H = `constraints.maxHeight`
(`fragmentainerBlockSizePx`, `:183-196`), and `…/sizing/SizingApplier.kt:435` applies `Modifier.height(px)`, which
Compose COERCES into the 100-px parent (the width twin documents exactly this coercion, `…/sizing/ExactWidthOverflow.kt:6-16`;
no height twin exists) — so either path sees H=100. SwiftUI: `StyleEngine/columns/MulticolGreedyLayout.swift:95-180` →
`MulticolDistribution.swift:56-85` (same greedy rule, H unused); `MulticolRunFragment.swift:26-31` states the iOS twin is
"pure module + pins only" (a ComponentRenderer seam). Trace: col-1 = green 100 (0 → 100), col-2 = spacer 10 + border-div 100
= 110 → the 10-px red strip + 10-px overflow the captures show. Correct css-break-3 §4.1 answer: the border-div (40-px
`border-top` + a 60-px childless `contain:size` block, monolithic — no class-A/B point inside, the class-C point before
the block needs a non-zero gap) does not fit the remaining 90 px of col-1 → moves whole to col-2 → col-2 = 100 green.

**098 — clip on the wrapper.** `…/table/TableBoxTree.kt:401-408` names s-11-1-1b-008: the `table → caption, tr` shape is not
a consumable row list, so the table renders through the BLOCK fallback with the caption as a first child; the
`overflow:hidden` then clips the whole block (caption margin included). iOS mirror `StyleEngine/table/TableBoxTree.swift:88,140`.
CSS 2.1 §17.4 + errata s.11.1.1b: `overflow` on a table applies to the table box, not the table wrapper box.

**031/068 — css-writing-modes-3 §7.3.1 orthogonal available block size is not modelled.** Compose reads `isVertical`
only for width/height swaps (`…/sizing/SizingExtractor.kt:77`) and unlocks fills via `LocalVerticalFlowScope`
(`ComponentRenderer.kt:2822-2826`); SwiftUI `ComponentRenderer.swift:1122-1130` keeps "the FROZEN horizontal-tb fills
(the available-size family passes on them today)". Neither derives the orthogonal child's block size from the nearest
fixed-size ancestor scroller / `max-height` / `height`+`min-height` (the test's assert). Android additionally draws the
abspos `#red` aside horizontally (ch advance via `…/spacing/SpacingResolve.kt:166`, `ctx.chAdvancePx ?: 0.5f*fontSize`).
Web 014: `SizeApplier.ts` emits `4ch`/`8ch` verbatim (`engine/core/types/LengthValue.ts:222`), so the size chain is
not the culprit — unverified (§9).

**038 — anchor positioning is web-only** (`runtimes/web/src/engine/layout/advanced/Anchor*.ts`; no `anchor()` /
`anchor-size()` resolver in compose or swiftui — `find … -iname '*anchor*'` yields only EndInsetAnchor /
PositionedAncestorAnchor / OffsetAnchorValue). Anchor lane, not this one.

## 4. Proposed fixes (CSS-correct, with the spec section)

F1 (T1, S): replace the two-property heuristic with the post-load extractor's real signature, in ONE shared predicate
per native: `bakedPhysicalBox(child) = has Width && has Height && has BoxSizing && (has PaddingTop || has BorderTopStyle)`
(the baked wires above carry 9-18 of these longhands on every box; authored docs carry 0 — census §5). Use it at
ComponentRenderer.kt:2781-2785, MulticolSpannerFlow.kt:322-323 and ComponentRenderer.swift:4360. Correctness:
css-writing-modes-4 §6 (block flow is horizontal under vertical-*), §7.3 unaffected (the orthogonal-child guard stays).
The Android correctness win additionally needs css-flexbox-1 §9.4 step 11 stretch of the 100×50 items to the LINE cross
size under an AUTO container cross size — `…/layout/flexbox/FlexCrossStretch.kt:60-63` stands down there ("CONTAINER
CROSS SIZE DEFINITE"); iOS `StyleEngine/layout/flexbox/CSSFlexLayout.swift:9-13,169-174` does true auto-item stretch.
F2 (T2, S): delete the Float clause at ContentsUnboxing.kt:71-74 and ContentsUnboxing.swift:65-66 (keep the position
clause — its containing-block-threading reason is real and documented). css-display-3 §2.5 (contents generates no box)
+ §2.7 parenthetical. The root-level text leaf self-strips to inheritable declarations + text (`resolve`, .swift:168-186;
Compose `ComponentRenderer.kt:389`).
F3 (T3, M): port `MulticolSpannerContainingBlock` to Swift (pure functions, XCTest twin of `MulticolSpannerContainingBlockTest.kt`)
and thread `positionedAtMulticol` through the iOS overlay partition so an abspos descendant of a spanner is collected by
the nearest positioned box OUTSIDE the multicol (here none → the ICB; with insets `top:0;left:0` it must take the ICB,
not a canvas flow slot). css-multicol-1 §6.1/§6.2, CSS 2.1 §10.1.
F4 (046, M-L, correctness only — defer unless F1-F3 land early): (a) Compose — feed `fragmentainerBlockSizePx` the
DECLARED px height (SizingExtractor) when the incoming constraint coerced it; (b) both — for `column-fill:auto` + definite
H + ≥2 flow children, use the run pass with a MONOLITHIC-CHAIN rule (a child whose only content is an unbreakable box and
whose class-C gap is zero is itself monolithic) instead of greedy; iOS must first consume `MulticolRunFragment`.
css-multicol-1 §7.2, css-break-3 §4.1 (class A/B/C), §4.4 (monolithic). Siblings block-in-inline-013/014 (P 0.9974/0.9967,
`height:400` in a 100-px parent, 4 children) PASS on greedy today — F4 is unsafe without them pinned.
F5 (098, M, correctness only): in the table block fallback, apply the `overflow` clip to the table box (children minus the
caption + its margin) — CSS 2.1 §17.4 wrapper/table split. 3 cells, all PASS today.
F6 (031/068, L, correctness only unless paired with the two failing orthogonal cells): implement §7.3.1 — an orthogonal
flow's available block size = nearest ancestor with definite block size or a fixed-size scroller (else `max-height`,
grown by `min-height`), then wrap the vertical run at that size. Carriers §5 (22 docs; failing today:
wave51-fix css-writing-modes/available-size-011 ios f 0.9427 · android f 0.9297, css-multicol/column-fill-balance-orthog-block-001 ios f 0.944).

## 5. Blast radius — how to census (all over `tools/titan/runs/<run>/sections/*/per-test-ir/*.json`, v2 flat list, parent = `slot.parent`)

Recipe (executed here, counts from `failure-ink-census.json → carriers`):
- F1 carriers: component with `WritingMode` ∈ VERTICAL_*/SIDEWAYS_*, no own `text`, ≥1 in-flow child, no inline-level
  `Display` child, no child declaring `WritingMode`, and ≥1 in-flow child with `Width`+`Height` → **13 docs**: 8 baked
  (`anchor-position-multicol-007/008/013/014/015/016/017`, `-colspan-003`; 9-18 signature longhands each → STILL guarded
  by F1), 5 authored: the target; `flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse` (container `Display=FLEX`
  → the block seam never runs; not carriers); `input-range-zero-inline-size` (FLEX, baked); and
  `css-writing-modes/abs-pos-border-offset-003` (block container, 1 in-flow 10×20 sibling + abspos item; ios f 0.9342 ·
  android f 0.9262 — will MOVE, direction unknown). **Passing cells at risk: none** (only 012 android P 0.998 changes shape).
- F2 carriers: `Display=CONTENTS` → 42 docs; with `Float` ≠ none → **exactly 1** (the target). Passing cells at risk: none.
- F3 carriers: `ColumnSpan=ALL` with a positioned descendant → **1 doc** (the target; android already P 0.999).
- F4 carriers: multicol (`ColumnCount`/`ColumnWidth`) with `ColumnFill=AUTO` and ≥2 in-flow children → 32 docs,
  27 of them fully PASSING (css-break block-in-inline-004/009/013/014, borders-000/001, background-image-005/006,
  CSS2 floats-clear-multicol-000..003, css-multicol baseline-001, abspos-after-spanner, …) — the at-risk set for F4;
  multicol with declared `Height` > parent `Height` → 7 docs (block-in-inline-009/013/014, broken-column-rule-1,
  change-transform-in-nested, column-height-005/007).
- F5 carriers: `sourceTag=table` (or `Display=TABLE`) with `Overflow*` ≠ visible → 8 docs (s-11-1-1b-001..009), 4 with
  a caption child; all PASS except -006 (ios/android f, no caption).
- F6 carriers: child with own vertical `WritingMode` under a non-vertical parent whose ancestor has `Height`/`MaxHeight`/`MinHeight`
  → 22 docs (available-size-001..018 family 14, position-absolute-center-002, float-in-htb-in-vrl, aspect-ratio abspos-004/015/020,
  flex-gap-decorations-031/032, anchor-position-multicol-007/008) — 17 PASS today, the at-risk set for F6.
Corpus-wide red census: 80 FAILING cells carry red novel ink (all 80 opened; per-cell mechanism + owner in the JSON:
4 this lane, 9 fragmentation-XL, 6 anchor, 2 static-position 0(j), 12 converter/cascade+media, 10 backgrounds, 8 containment,
11 tables, 5 transforms, 3 selectors, 2 flex-sizing, 8 structural/spec-ahead); **237 PASSING cells** also paint red the
ref hides (top of the list in the JSON, e.g. wave51-fix css-anchor-position/anchor-name-multicol-003 web P 1.0000 with
1 500 red px) — the queue's "sample count × sampling factor" is now a measured 237, and the scorer cannot see them.

## 6. Predicted flips

- T1 `flexbox_align-items-stretch-writing-modes` ios f 0.999 → **P** (F1; confidence MEDIUM). Falsifier: the item's cross
  size stays 50 after re-stacking (CSSFlexLayout not stretching a 100×50 auto item to the 100-px line) → red moves to the
  bottom halves, colorFailed persists. Android stays P (0.998 → ~0.998, shape change only) unless FlexCrossStretch's
  auto-container arm is added.
- T2 `display-contents-float-001` ios f 0.9417 → **P**, android f 0.941 → **P** (F2; confidence HIGH — the red bar is the
  only divergence; the ref is one text line). Falsifier: the root-level self-strip keeps a block box tall enough to move
  "PASS" (it should not — undecorated pass-through).
- T3 `abspos-containing-block-outside-spanner` ios f 0.9553 → **P** (F3; confidence MEDIUM). Falsifier: the ICB-anchored
  child hoists but `bottom:0; right:0` resolves against the padded canvas instead of the 390×600 ICB (Android's EndInsetAnchor
  precedent, `…/layout/position/EndInsetAnchor.kt:10`).
- Side-effect watch (both failing today, either direction): `abs-pos-border-offset-003` ios/android.
- No sampled 0(k) cell flips — all seven PASS already; F4-F6 are correctness lanes whose flips are the orthogonal trio in F6.

## 7. Pins a builder must write (each fails by the named mutation)

P1 Compose `VerticalBlockFlowSeamGuardTest` (beside `VerticalBlockFlowMathTest.kt`) + Swift twin: authored children with
ONLY Width+Height → `bakedPhysicalBox=false`; the 18-longhand baked shape → true. Mutation: restore the two-property
heuristic → first row fails. Also pin `MulticolSpannerFlow.ChildSpec.bakedPhysicalSize` on the same rows.
P2 `ContentsUnboxingTest.kt` / `ContentsUnboxingTests.swift`: `Display=CONTENTS + Float=RIGHT + BackgroundColor` text leaf →
`isUnboxable=true`; `Position=ABSOLUTE` contents → false (unchanged). Mutation: re-add the Float clause → row 1 fails.
P3 Swift `MulticolSpannerContainingBlockTests` = the Kotlin table byte-for-byte (`ancestorPositioned=true,
positionedAtMulticol=false, spanner ⇒ false`; non-spanner ⇒ true; null multicol ⇒ OR rule). Mutation: drop the spanner
branch → row 1 fails.
P4 Gate pins (`tools/titan/results/wave52-*/`): the 3 target cells before/after, `abs-pos-border-offset-003` ×2, the 8
baked anchor-position-multicol cells (must not move), block-in-inline-013/014 (must not move if F4 is touched).

## 8. File ownership the lane needs

Compose: `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` (lines 2740-2830
only — SHARED-RENDERER SEAM, coordinate with the static-position lane 0(j) which owns the abspos branches of the same
file), `…/core/renderer/ContentsUnboxing.kt`, `…/columns/MulticolSpannerFlow.kt` (:300-330), `…/columns/VerticalMulticolMeasure.kt`,
new `…/core/renderer/BakedLayoutSignature.kt`. SwiftUI: `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift`
(`verticalBlockFlowZ2`, the overlay partition around :2050 — shared seam), `Renderer/ContentsUnboxing.swift`,
`Renderer/FixedHoist.swift`, new `StyleEngine/columns/MulticolSpannerContainingBlock.swift`. Tests:
`runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/`, `…/columns/`,
`runtimes/swiftui/Tests/StyleConverterRuntimeTests/`. F4 would add `…/columns/MultiColumnApplier.kt`,
`MulticolRunFragmentMeasure.kt`, `…/sizing/SizingApplier.kt` (height axis) — the fragmentation lane's files, not to be
touched without that lane.

## 9. Not verified (static reading only; no build/capture was run)

- That the 012 flex ITEM's children are rendered through the block-flow branch (`ComponentRenderer.kt:2703` `else ->` /
  `.swift:1823` chain) on both natives — inferred from the item having no `Display` and the capture's vertical stacking.
- Whether the incoming Compose constraint really coerces the multicol's `height:200px` to 100 (046) or the greedy path
  alone explains the picture — both reproduce the capture; only a device run separates them.
- Web 014: `4ch`/`8ch` are emitted verbatim, so the wrap difference (red where the green `0` belongs, GREEN=0) must come
  from the inline `runs` composition or the `monospace` face — not located.
- The exact colorFailed ΔE floor and why 012 android (ΔE mean 3.195, colorComposite 0.9341) passes while ios (3.178,
  0.9354, colorDivergent=true) fails — `colorDivergent` differs; not traced into `computeColorDivergent`.
- iOS `CSSFlexLayout` stretching a re-stacked 100×50 auto item to the 100-px line (the T1 flip's precondition).
- 091 `column-height`/`column-wrap`: asserted spec-ahead from the WEB capture failing (Chrome build in the harness);
  the Chromium version was not read.

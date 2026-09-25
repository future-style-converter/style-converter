# wave52-plan — static-position (family key `static-position`)

Read-only brief. Evidence run `tools/titan/runs/wave51-fix` (manifest cells + captures); frozen refs
`tools/wpt/refs/9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins/`. Every pixel figure was measured on the PNGs
(colour-class bounding boxes; frame 390 wide, body origin (16,16)); cells are cited as `wave51-fix <section>/<test>
<platform> <P|f> <ssim>`. Beside this file: `static-position.census.json` (every grid/flexbox/position/CSS2 cell of
the run: ssim · wptPass · colorFailed · novelInkFailed · scoreExcluded), `static-position.census.py` (structural
carrier census, read-only) and its output `static-position.carriers.json`.

## 1. Queue item covered

`docs/BACKLOG.md` → "## Ranked queue (wave 51+)" → item 0 → **(j) "Out-of-flow static position — 7 of the 35
wrong cells, all three platforms, the single largest family"** ("wrong by 5–25 px, or its containing block has
its axes transposed"). Source: `tools/titan/results/wave50-B12/README.md` §4 row F1 and `handverdicts.json`
cells 000, 058, 076 (web) · 020, 032, 036 (iOS) · 045 (Android). Re-read against wave51-fix:

| B12 | wave51-fix cell | still wrong? | target |
|---|---|---|---|
| 045 | css-grid/grid-abspos-staticpos-align-self-center android P 0.9808 | yes, +25 px | **T1** Android grid overlay |
| 036 | css-flexbox/flex-abspos-staticpos-justify-self-001 ios P 0.9921 | yes, (−2,−1) px every mark | **T2** iOS flex anchor |
| 000/058 | css-grid/grid-abspos-staticpos-align-self-rtl-last-baseline-002 / -001 web P 0.9979 | yes, mark 14 px high | **T3** `last baseline` dropped |
| 020 | css-grid/grid-abspos-staticpos-align-self-rtl-last-baseline-002 ios P 0.9948 | yes, (+3,−16) px; small float below | **T3+T4** (+ float placement, other family) |
| 076 | css-grid/grid-abspos-staticpos-align-self-vertWM-003 web P 0.9871 | NOT static position (§9) | float-row pitch + frame crop |
| 032 | css-grid/descendant-static-position-004 ios P 0.9753 | NOT the §9.2 path (§9) | grid-item descendant + missing border |

"Axes transposed" (020) is a misread of the union bounding box: the `.small` float lands BELOW the `.big` float
(iOS yellow (17,56)-(24,59) vs ref (50,17)-(57,20)); the big grid container is 26×32 everywhere, ref included.

## 2. Target cells, cheapest first (PASSING unless marked f)

**T1 — Android, css-grid §9.2 overlay aligns a 0×0 box (16 cells, one seam).** Green mark 50×50 (100×50 in the
`-large-border-padding` (lbp) centre variants), content box 100×100 at x 17..116; web and iOS are 1.0000 on all 16.

| cell (android) | ssim | android green y | ref green y | defect |
|---|---|---|---|---|
| grid-abspos-staticpos-align-self-center · align-items-center | 0.9808 | 67..116 | 42..91 | +25 = child/2 |
| …align-self-end / -flex-end / -self-end · align-items-end / -flex-end | 0.9704 | 118..166 (row 117 under the border) | 67..116 | +50 = child |
| …align-items-self-end | 0.9715 | **17..66** | 67..116 | −50: `self-end` on align-items → START |
| …align-self-center-lbp · align-items-center-lbp | 0.9856 | 363..462 | 313..412 | +50 = child/2 (child 100) |
| …align-self-{end,flex-end,self-end}-lbp · align-items-{end,flex-end}-lbp | 0.9726 | 152..193 (rest under the 45 px border) | 102..151 | +50 = child |
| …align-items-self-end-lbp | 0.9698 | **52..101** | 102..151 | −50: same fold gap |

Signature: the mark's TOP edge lands on its alignment point (offset = factor × content, not factor × (content −
child)). x is right in all 16 (justify axis start).

**T2 — iOS flex container: static position anchored at the PADDING-box corner, aligned within the PADDING box,
`align-items` never consulted (6 cells, one module).** Android is right on all six (e.g. containing-block-002 android P 1.0000).

| cell | ssim | iOS | ref | defect |
|---|---|---|---|---|
| css-flexbox/flex-abspos-staticpos-justify-self-001 ios P | 0.9921 | 14 big marks at (17,17)+k·27 | (19,18)+k·27 | every mark (−2,−1) = −(padding-left, padding-top); the 14 small containers' 8×4 yellow is fully covered by the 8×6 mark (B12's "missing background") |
| css-flexbox/flex-abspos-staticpos-margin-001 ios P | 0.9909 | (53,17) (89,17) … | (55,18) (91,18) … | (−2,−1) rows 1–2; later rows float-pitch |
| css-flexbox/flex-abspos-staticpos-fallback-justify-content-001 ios P | 0.9874 | (17,17) | (19,18) | (−2,−1) row 1 |
| css-flexbox/flex-abspos-staticpos-margin-002 ios P | 0.9910 | (66,17) | (64,18) | (+2,−1): anchor error composed with a start margin |
| css-flexbox/position-absolute-containing-block-001 ios P | 0.9505 | green (66,16)-(165,115) | (66,66)-(165,165) | x centred (justify-content) but `align-items: center` ignored: y at the top |
| css-flexbox/position-absolute-containing-block-002 ios **f** | 0.9373 | green (69,21)-(168,120) | (76,76)-(175,175) | x centred in the 195-px PADDING box (16+5+47.5≈69) not the 180-px content box (76); y at the padding-box top (21): align-items ignored, no padding-top shift |

**T3 — `align-self: last baseline` (and `normal`/`left`/`right`) never reaches any runtime (12 web cells, one
converter arm).** IR: `{"type":"Generic","data":{"propertyName":"align-self","rawValue":"last baseline","_unmapped":true}}`;
web drops static Generic values, the browser falls back to `auto` → start.

| cell (web) | ssim | web teal | ref teal | defect |
|---|---|---|---|---|
| css-grid/grid-abspos-staticpos-align-self-rtl-last-baseline-002 (B12 000) | 0.9979 | big (30,20)-(37,25); small (45,18)-(52,23) | (30,34)-(37,39); (45,16)-(52,19) visible | mark at the grid-area block-START; ref at block-END (`last baseline` fallback): −14 / −4 px |
| …rtl-last-baseline-001 (B12 058) / -003 / -004 | 0.9979 / 0.9983 | same picture | same | same |
| …last-baseline-001/002 · vertWM-last-baseline-001..004 · img-last-baseline-001/002 | 0.9954–0.9983 | ~ | ~ | same family (Generic align-self census in §5) |

**T4 — iOS + Android grid: alignment container ignores the css-grid-1 §9.2 second sentence (grid AREA when the
grid container is the containing block), RTL track order and vertical-rl (most expensive; entangled with floats).**

| cell | ssim | capture | ref | defect |
|---|---|---|---|---|
| css-grid/…align-self-rtl-last-baseline-002 ios P (B12 020) · android P | 0.9948 · 0.9947 | big mark (33,18)-(40,23) | (30,34)-(37,39) | x: RTL start of the CONTENT box (right edge 41) instead of grid-area col 2 (RTL tracks x 24..38); y: content top instead of row-2 end |
| css-grid/grid-abspos-staticpos-align-self-safe-001 ios **f** 0.9448 · android **f** 0.9441 | | container 1 (vertical-rl) mark (18,21)-(38,41); container 2 mark (18,57) | (24,21)-(44,41); (59,22) = (3,6) into its box | vertical-rl block-start = right edge and grid-area col 2 ignored; `justify-self: safe end` typed-raw shape unread → START. All four floats also stack vertically (ref: one row) — float packing, other family |

## 3. Mechanism (file:line under the trusting-bohr worktree, HEAD 5d9ed628)

**T1 (Compose).** `GridRenderer.kt:157` partitions abspos children out; the §9.2 overlay
`Box(matchParentSize, contentAlignment = staticOverlayAlignment(...))` (`GridRenderer.kt:366-378`) aligns the
child's REPORTED box and passes `absposOverflowMeasure(blockSpec)` as `itemModifier` (`:397`). But
`RenderComponent` (`ComponentRenderer.kt:857`) first runs the wave-18 RC1 branch: `CanvasRootHoist.
rendersInFlowAsStaticPosition` (`CanvasRootHoist.kt:334-352`: ABSOLUTE ∧ no positioned ancestor ∧ no inset) is
TRUE for every child of an UNPOSITIONED grid, so `ComponentRenderer.kt:945-950` chains
`itemModifier.then(zeroFlowAnchor(...))`, which reports `layout(hoistedFlowReportPx(), hoistedFlowReportPx())` =
0×0 (`CanvasRootHoist.kt:834`, `:620`) while placing the ink at its own origin. The overlay Box aligns a 0×0
placeable: centre → y = content/2, end → y = content — the +25/+50 measured. Flex is spared (its loops place
children by `absposStaticMeasure` offsets from IR extents, `ComponentRenderer.kt:4714-4750`); positioned grids take
`RenderAbsoluteChild` (`:4467`) and never meet the zero anchor. Sub-defect: the `AlignItems` fold
(`ComponentRenderer.kt:7931-7939`) has no `SELF_END`/`SELF_START` arm, so `alignItemsBase` (`GridRenderer.kt:866-874`) → START.

**T2 (iOS).** The positioned-child overlay anchors children at the PADDING-box corner
(`Renderer/ComponentRenderer.swift:1278-1284`) and feeds `AbsposStaticPosition.staticOffset` the PADDING-box
extents `childCB`/`childCBH` (`:2063`, `:2081`; call `:2098-2112`). `AbsposStaticOffset.staticOffset`
(`AbsposStaticOffset.swift:75-99`) is pure `axisOffset(child, container, spec)` — no padding-start term, unlike
its grid twin `AbsposGridStaticPosition.axisOffset` (`AbsposGridStaticPosition.swift:100-118`: `paddingStart +
crossOffset`). `AbsposStaticPosition.resolveStatic` (`AbsposStaticPosition.swift:134-176`) takes the cross claim
ONLY from the child's align-self; the container's `align-items` is never read. Compose's twin reads it
(`GridRenderer.absposStaticSpecs` rule 3, `:795-832`, and the flex loops) — hence Android's 1.0000.

**T3 (converter → all three).** `AlignSelfPropertyParser.kt:8-27` accepts only single keywords
(`auto|flex-start|flex-end|center|baseline|stretch|start|end|self-start|self-end|anchor-center`); `normal`,
`first|last baseline`, `left|right`, `safe|unsafe <pos>` fall to Generic. Web `applyGeneric`
(`StyleBuilder.ts:274-320`) passes only `var(`/`calc(`/`calc-size(` and logs the rest. Compose
`AbsposStaticAlignment.resolveCross/parseRaw/baseOf` (`AbsposStaticAlignment.kt:57-123`) and iOS
`AbsposStaticAlignment.resolveSelf/baseOf` (`AbsposStaticAlignment.swift:58-137`) read the Generic channel but map
only `<self-position>`: `last baseline` → nil → START. css-align-3 §4.2: `first baseline` falls back to `safe
self-start`, `last baseline` to `safe self-end`; an abspos box has no shared alignment context, so the fallback IS
its alignment (refs: vertWM-003 `baseline` → alignStart; rtl-last-baseline-002 → alignEnd, margin-top 16 = 2+20−6).

**T4 (iOS + Compose grid).** `AbsposGridStaticPosition.staticOffset` (`AbsposGridStaticPosition.swift:190-240`)
uses `contentExtent` and reads left/top only ("LTR horizontal-tb — the runtime's only writing mode today",
`:159`); it never consults `GridRowStart/GridColumnStart` or the parent's `Position`. Compose's overlay is
`matchParentSize()` over the content box (`GridRenderer.kt:373`). css-grid-1 §9.2: "…as if it were the sole grid
item in a grid area whose edges coincide with the content edges of the grid container. However, if the grid
container parent is also the generator of the absolutely positioned element's containing block, instead use the
grid area determined in §9.1" — with `position: relative` + `grid-area: 2/2/3/3` the alignment container is
row 2 × column 2 in RTL track order (`GridContentDistribution` already mirrors tracks: `.swift:113-118`,
`.kt:107-112`). Typed-raw `JustifySelf {"type":"raw","value":"safe end"}` (`JustifySelfPropertyParser.kt:44`)
reads as the keyword "safe end" via `ValueExtractors.extractKeyword` (`Renderer/ValueExtractors.swift:77-84`) and
fails `baseOf`; Compose `ItemPlacementExtractor.justifySelf` (`ItemPlacementExtractor.kt:91-115`) folds it to AUTO.

## 4. Proposed fix (CSS-correct behaviour, spec sections)

**T1 (Compose, layout/position + layout/grid).** Add a CompositionLocal `CanvasRootHoist.LocalStaticPositionOwner`
(default false); `GridRenderer` provides `true` around each overlay child (`GridRenderer.kt:381-401`), and the RC1
branch (`ComponentRenderer.kt:927-950`) passes it as a fifth argument `staticPositionOwned` to
`rendersInFlowAsStaticPosition`, which returns false when set. css-grid-1 §9.2 makes the grid container the owner
of its out-of-flow children's static position; the zero-footprint mount is right for block flow (css-position-3
§3.1) but wrong under a parent that aligns the box by its own size. Do NOT reuse `LocalHasPositionedAncestor` —
it would also cancel the canvas hoist of INSET abspos children whose containing block is the ICB. Add
`"SELF_END","SELF-END" → FLEX_END` and `"SELF_START","SELF-START" → FLEX_START` to the AlignItems fold
(css-align-3 §6.1: positional keywords for a sole item in horizontal-tb LTR).

**T2 (iOS, layout/flexbox + Renderer).** In `AbsposStaticOffset.staticOffset`: (a) alignment container = CONTENT
box (css-flexbox-1 §4.1 "as if it were the sole flex item in the flex container"; css-align-3 §6.2); (b) add the
padding-start edge per axis as `AbsposGridStaticPosition.axisOffset` does (the anchor is the padding box,
css-position-3 §3.1); (c) in `resolveStatic`, fall back to the container's `align-items` when the child has no
`align-self` claim (css-align-3 §6.1: `auto` computes to the parent's `align-items`; `normal`/`stretch` behave as
`start` for an abspos box). Call site: pass content extents from `flexContentSize(style:vertical:)`
(`ComponentRenderer.swift:3560-3567`) instead of `childCB`/`childCBH`; leave `childCB` to the percent-basis channel.

**T3 (converter + three runtimes).** `AlignSelfPropertyParser`: accept `normal`, `[first|last]? baseline`,
`[safe|unsafe] <self-position>`; reject `left|right` (css-align-3 §6.1: not in the align-self grammar → invalid →
`auto`, which the refs show). Emit typed `NORMAL`, `BASELINE` (also for `first baseline`), `LAST_BASELINE`;
`safe|unsafe` may stay on the Generic wire (both mobile readers parse it). Decoders tolerate new keyword strings
(iOS `FlexboxExtractor.mapAlignment` default → `.normal`; web `kebab()` → undefined on non-strings; Compose
`baseOf` else → null — the Compose `FlexExtractor.parseAlignSelf` else-arm was not read, verify);
`schema/ir-v2.schema.json` has no AlignSelf enum and `schema/conformance/fixtures/v2/placement-claims.json:235`
only claims the type, so no wire freeze is touched. Web `AlignSelfApplier` must emit `'last baseline'` with a
SPACE — `kebab()` (`layout/_shared.ts:19-23`) would make `last-baseline`, invalid CSS. Mobile `baseOf`:
`LAST_BASELINE`/`last baseline` → END, `BASELINE`/`first baseline` → START (§4.2 fallback), both twins.

**T4 (iOS + Compose grid; after T3).** When the parent grid is positioned and the child carries a placement,
compute the grid area from the resolved tracks (iOS `GridTrackMath.columnWidths/rowHeights` + `GridContentDistribution`;
Compose `GridRenderer.placeItems` / `resolvedRowHeights`) and align within it (css-grid-1 §9.1 + §9.2 second
sentence), honouring RTL track order and the vertical-rl axis swap (css-writing-modes-4 §7); read typed-raw
`JustifySelf` through `parseRaw`. Three of its four cells also need float placement — do not staff before T1–T3 land.

## 5. Blast radius and census (`static-position.census.py`, executed on wave51-fix: 1435 docs)

Shape: a component with `{"type":"Position","data":"ABSOLUTE"}` and no `Top|Right|Bottom|Left|Inset*`, whose
`slot.parent` has `{"type":"Display","data":"GRID"}` — T1 if the parent has no `Position` (**31 docs**: 29 css-grid
+ css-contain/contain-inline-size-grid-{indefinite-height-min-height-flex-row,stretches-auto-rows}), T4 if the parent
is `RELATIVE|ABSOLUTE|FIXED|STICKY` and the child carries `GridRow*`/`GridColumn*` (**15 docs**, all css-grid
grid-abspos-staticpos-*-001/-002 + safe-001) — or `{"type":"Display","data":"FLEX"}` (41 docs; 27 inset-less):
T2 if the parent carries `Padding*`/`Border*Width` (**15 docs**: abspos-autopos-{htb,vlr,vrl}-{ltr,rtl},
flex-abspos-staticpos-align-self-safe-001..003, -fallback-justify-content-001, -justify-self-001, -margin-001..003,
position-absolute-containing-block-002) or a positional `AlignItems` with no child `AlignSelf` (**10 docs**:
margin-003, position-absolute-006..010, containing-block-001/002, css-position/position-absolute-center-003/004).
T3 = a `Generic` with `"propertyName":"align-self"` (30 docs, §2). Full lists: `static-position.carriers.json`.

Currently-PASSING cells at risk. **T1**: every Android cell of the 31 T1 docs (0.958–0.9998 P): START-aligned
ones must not move (offset 0 either way); the 16 above must move to 1.0. `positioned-grid-descendants-*` keep the
abspos under a grid ITEM — not on the overlay path. **T2**: every iOS cell of the 15 + 10 T2/T2b docs:
flex-abspos-staticpos-align-self-safe-001/002/003 ios P 0.9937/0.9914/0.9981 (pins `AbsposStaticPositionTests.
testSafe001FourSubContainers`/`Safe002`/`Safe003` encode padding-box extents — re-derive to the ref geometry, do
not delete), abspos-autopos-* ios P 0.9974/0.9904, position-absolute-006..010 ios P 0.9974, css-position/
position-absolute-center-003/004 ios P 0.9973. **T3**: the 30 Generic carriers on all three platforms; on mobile
`last baseline` → END within the CONTENT box moves the mark from the row-2 top PAST the ref (y 42 vs ref 34) until
T4 lands — pixels change, truth does not improve there yet; web is the only platform T3 makes right alone.

## 6. Predicted flips (wptPass) and score motion

- **T1**: 0 flips (all 16 already P); ssim 0.9698–0.9856 → 1.0000 on 16 Android cells. Confidence **high** — the
  +25/+50/−50 offsets equal factor × content with the child extent absent, i.e. a 0×0 aligned placeable. Falsified
  if, with the owner flag set, the mark stays at y 67 (then tight Box constraints + `absposReportedAxis` are the cause).
- **T2**: css-flexbox/position-absolute-containing-block-002 ios **f → P** (0.9373 → ~1.0); containing-block-001
  ios 0.9505 → ~1.0; justify-self-001 ios 0.9921 → ~1.0; margin-001/002, fallback-justify-content-001 improve on
  row 1 only (later rows carry float-pitch defects). Confidence **medium-high**; falsified if the Safe00x pins
  cannot be re-derived to the ref geometry with a content-box container.
- **T3**: 0 flips; 12 web css-grid cells 0.9954–0.9983 → 1.0000. Confidence **high** for web (the browser owns
  the fallback once the keyword arrives). Mobile: no flip, pixels move (§5).
- **T4**: no flip this wave; safe-001 (ios/android f 0.944) also needs float packing. Nothing in T1–T4 touches
  `filter-effects/backdrop-filter-basic-blur`.

## 7. Pins a builder must write (each proven by mutation)

1. Compose JVM: `rendersInFlowAsStaticPosition(props, false, false, false, staticPositionOwned = true)` is false on
   the verbatim `abspos__grid-abspos-staticpos-align-self-center__0__0` IR (Position ABSOLUTE, Width/Height 50,
   AlignSelf CENTER, no inset); mutation = ignore the flag → true. Pure twin of iOS `testAxisOffsetPlainVariantTable`:
   (child 50, content 100) centre → 25, end → 50. `"SELF_END"` fold → `alignItemsBase` → END (mutation: drop the arm).
2. iOS XCTest: `AbsposStaticOffset.staticOffset` on the justify-self-001 container IR (Padding 1/2/1/2, Border 1,
   Width 16, Height 10; child 8×6, JustifySelf auto) == (2, 1) (today (0, 0)); on containing-block-002 (content
   180×180, PaddingTop/Left 15, Border 5, AlignItems CENTER, JustifyContent CENTER; child 100) == (55, 55) (today
   (47.5, 0)). Mutations: drop the padding term → (0,0)/(40,40); drop the align-items fallback → y 15.
3. Converter: `AlignSelfPropertyParser.parse("last baseline")` is typed (today null → Generic); `parse("left")`
   is null. Web vitest: `applyAlignSelf({value:'last baseline'})` emits `alignSelf: 'last baseline'` (mutation:
   route through kebab → `'last-baseline'`; `CSS.supports('align-self','last-baseline')` is false).
4. Compose + iOS: `resolveCross` on Generic/typed `last baseline` → Spec(END); `baseline` → START (mutation:
   remove the arm → nil, today's "typed but unmappable → no claim").
5. Device truth (the gate): css-grid/grid-abspos-staticpos-align-self-center android green y 42..91;
   css-flexbox/position-absolute-containing-block-002 ios green (76,76)-(175,175);
   css-grid/…rtl-last-baseline-002 web big mark (30,34)-(37,39).

## 8. File ownership (exact paths) and shared seams

- Compose (T1): `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/grid/GridRenderer.kt`;
  `…/runtime/layout/position/CanvasRootHoist.kt` (**shared** with the hoist/position lanes — additive flag only);
  `…/runtime/core/renderer/ComponentRenderer.kt` lines 927-950 and 7931-7939 (**shared**); keyword map
  `…/runtime/layout/flexbox/AbsposStaticAlignment.kt`; tests `runtimes/compose/src/test/java/com/styleconverter/
  runtime/layout/grid/GridAbsposPartitionTest.kt`, `…/core/renderer/AbsposOverflowMeasureTest.kt`.
- iOS (T2, T4): `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/flexbox/{AbsposStaticOffset,
  AbsposStaticPosition,AbsposStaticAlignment}.swift`, `…/StyleEngine/layout/grid/AbsposGridStaticPosition.swift`;
  `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift` 2052-2170 (**shared** with the
  position/multicol overlay lanes — change only the two `containerW/H` arguments); tests `runtimes/swiftui/Tests/
  StyleConverterRuntimeTests/{AbsposStaticPositionTests,AbsposStaticAlignmentTests,AbsposGridStaticPositionTests}.swift`.
- Converter/web (T3): `converter/src/main/kotlin/app/parsing/css/properties/longhands/layout/flexbox/AlignSelfPropertyParser.kt`,
  `converter/src/main/kotlin/app/irmodels/properties/layout/flexbox/AlignSelfProperty.kt`,
  `runtimes/web/src/engine/layout/flexbox/{AlignSelfExtractor,AlignSelfApplier}.ts`; do NOT widen
  `runtimes/web/src/core/renderer/StyleBuilder.ts` `applyGeneric` (shared; its static-value drop is a deliberate
  parser-gap detector).

## 9. Not verified / re-classified

- Cell 076 (vertWM-003 web): big-group marks pixel-identical to the ref; the small group's rows 3–4 sit 5 px high
  (yellow rows 184/204 vs ref 189/209) after the single-float `baseline` row, and the `start` mark overhangs to x 14
  where the ref shows ink from x 16 only (frame crop). Float-row pitch + framing — floats (B12 F7), not this family.
- Cell 032 (descendant-static-position-004 ios): the abspos is a child of a grid ITEM; iOS pairs sit +2 px (rows
  1, 2, 4), +6 (row 5), −3 (row 6) and the item's `border-left: 2px` bar (ref x 88-89) is not painted; Android paints
  red failure ink at x 130-136 in rows 2, 3, 5, 6 (F2). Hypothesis (unverified): tracks centred in the padding box
  (+2 = (105−100)/2). Owner: grid distribution / descendant static position / border paint — not the §9.2 overlay.
- FAILING near-misses ≥ 0.93 in css-position/css-grid/css-flexbox that are NOT this signature (PNGs opened):
  flexbox-abspos-child-002 android f 0.9847 (`left:0;right:0` rows ~50 px wide: inset stretch); position-absolute-
  center-007 ios f 0.9451 / android f 0.9445 (abspos table with `top:0;bottom:0;margin:auto` is 200 tall, covers the
  caption); position-absolute-semi-replaced-stretch-button ios f 0.9783 / android f 0.9739 (button not stretched to
  its insets); …-stretch-input web f 0.9422 (form-control raster); align-items-007 ios f 0.9974 / android f 0.9966
  (colorFailed: red in-flow square).
- Not executed: whether Compose's flex loops also mount START-aligned children through the zero anchor; the
  Compose `FlexExtractor.parseAlignSelf` fold; `flexContentSize` reach at the iOS overlay call site. No build, test
  or capture was run; every code claim is a static read of the trusting-bohr tree.

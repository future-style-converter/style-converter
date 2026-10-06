# vertical-wedges — wave-52 plan brief

Evidence run: `wave51-fix`, section `css-writing-modes` (48 tests, 58 failing cells). All scores are
`manifest.wpt.results[<test>].browserRef.diffs['<p>-ref']`; refs are the frozen
`white-black-ink-font-lh-imgpad-htmlpins` PNGs (Inter, 390×600 image, 358×568 ICB). Every target PNG
below was opened and measured (pngjs bounding boxes of the coloured boxes / diff pixels).
Census JSON beside this file: `vertical-wedges.census.json`.

## 1. Queue items covered

`docs/BACKLOG.md` → "1. **Vertical-wedge residuals**": "the ch-units-vrl ×8 Android cells", "(b)
**`css-writing-modes/direction-upright-002` — the 'why 2805px?' diagnostic is DONE; three named
modelling gaps**", and the lane remit "every other css-writing-modes failing cell with ssim ≥ 0.90 on
any platform". Touches "(ab) Relocate the label machinery" only by sharing `ComponentRenderer` files (§8).

## 2. Target cells (cheapest first)

| # | cell (wave51-fix css-writing-modes/…) | platform | ssim | visible defect in the capture PNG | what the ref shows |
|---|---|---|---|---|---|
| T1 | flexbox_align-items-stretch-writing-modes | ios | f 0.9990 cF nI | 250×100 strip at (16,91): left 150 px green, right **100×100 red** (10 000 px ref-green→red). Both vertical-mode flex items stack their two 50×50 squares vertically (50 wide) so the abspos red `.error` shows through | one 250×100 green rectangle, no red |
| T2 | forms/input-range-zero-inline-size | ios / android | f 0.9747 / f 0.9743 | a 224×70 slider group (accent 0,117,255 + grey tracks) painted at (16,35)-(240,105) | blank — every range is `visibility:hidden; inline-size:0` |
| T3 | ch-units-vrl-005 … -008 (×4 tests) | ios / android | f 0.9159 cF / f 0.9103 | green td **6×60** (iOS) / **6×55** (Android); blue horizontal `inline-block` 64×60 / 64×55; orange vertical-rl upright div **60×25** (iOS: horizontal label) / **55×72** (Android: rotated run 64+8 pad) | green 63×63 @(16,184), blue 63×63 @(16,247), orange **120×120** @(16,310) |
| T3' | ch-units-vrl-001 … -004 (×4) | ios / android | f 0.8396 cF / f 0.8404 cF | green 6×60 / 6×55; blue upright div 60×25 / 55×72; orange 64×60 / 64×55 | green 120×120, blue 120×120, orange 63×63 |
| T4 | ch-units-vrl-003 / -004 (subset of T3') | ios / android | as above | green td is 6 px wide: `<col width:5ch>` contributes nothing | green 120 wide from the col |
| T5 | abs-pos-border-offset-003 | ios / android | f 0.9342 / f 0.9262 | blue 30×35 per row at x=41 (rows 1–4), 11 (iOS r5) / 31 (Android r5), 71 / 41 (r6); Android also +20 px lower in rows 1,6 | blue at x=81 (r1,5,6), 71 (r2–4), y offsets 10/30/30/30/45/10 inside each 80×100 green |

Ref/capture measurements (image px, 16 px frame included): ch upright = 24 px at 20 px Inter (5ch = 120;
Chromium rounds ascent 19 + descent 5), ch horizontal/sideways = 12.6 (5ch = 63). iOS uses 12.0 (SF "0"),
Android 11.0 (Roboto "0"), both for every orientation.

## 3. Mechanisms, with file:line per platform

**M-A · ch measures the wrong face.** Both twins measure the *platform default* face when no
`font-family` is declared, while the label paints the harness Inter face:
`runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/ChUnitMetrics.kt:50-68` (`cacheName` →
"default") and `:91-92` (`?: Typeface.DEFAULT`) vs the paint bottom-out
`core/renderer/ComponentRenderer.kt:6638` (`?: InterFontFamily`);
`runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/ChUnitMetrics.swift:146-147`
(`UIFont.systemFont`) vs `Renderer/ComponentRenderer.swift:5255` (`.custom("Inter")`). Effect: 5ch =
55 / 60 where Inter gives 63 (blue/orange/green boxes in every ch-units cell; css-values/ch-unit-001
iOS f 0.8294 cF · Android f 0.8362 cF is a carrier I did not open).

**M-B · ch ignores the inline axis.** css-values-4 §6.1.1: ch is "the advance width **or height**,
whichever is in the inline axis of the element". `ChUnitMetrics.swift:107` measures
`CTFontGetAdvancesForGlyphs(font, .horizontal, …)`; `ChUnitMetrics.kt:103` `paint.measureText("0")`;
neither call site passes writing-mode/text-orientation (`StyleApplier.kt:805-828 buildSpacingContext`,
`StyleBuilder.swift:357-386`). Under `vertical-*` + `text-orientation: upright` the "0" glyph's inline
advance is its vertical advance (Inter has no vmtx → ascent+descent = 24 px @20 px); under `sideways`
(or `mixed`, where "0" rotates) it stays the horizontal advance. Web needs nothing: Chromium resolves
the emitted `5ch` itself (`runtimes/web/src/engine/core/types/LengthValue.ts:200-205,222`,
`engine/typography/_shared.ts:38-48`).

**M-C · upright vertical run declines on an unbounded block-axis budget.** Gate accepts the run
(`typography/text/VerticalTextFlow.kt:162-183` — explicit `upright` ⇒ UPRIGHT), then the measure
declines: `core/renderer/VerticalRunIntrinsics.kt:234-243` (`budget = null` when
`!constraints.hasBoundedHeight`) → `VerticalTextFlow.kt:275` returns null → the rotated fallback
(the 55×72 orange: "00000" 64 px sideways + 2×4 dp `Modifier.padding(4.dp)` at
`ComponentRenderer.kt:7609`). iOS: `Renderer/VerticalTextFlowLayout.swift:103-122` — `proposal.height`
is nil because `PlaceholderLabel` ends in `.fixedSize(vertical: true)`; `budgetOverridePx` (`:86`) exists
and "nil means ask the proposal, which is all any caller does today" → the fallback is the horizontal
label (60×25). Spec: css-writing-modes-4 §7.3.1 — an orthogonal flow with an indefinite available
inline size uses the containing block's **max-block-size** if any, else the **ICB block size**, then
sizes fit-content. Both runtimes already expose the ICB: `core/renderer/WptCaptureMode.kt:214`
`composedIcbExtentDp` (568 at the defaults), `Renderer/WPTCaptureMode.swift:146 icbExtent`.
Same decline draws direction-upright-002's ASCII cells rotated on Android and horizontal on iOS, and
iOS available-size-011's "S S B A P" (Android's bounded `max-height: 1em` engages the plan and reads PASS).

**M-E · `<col width>` is dropped.** `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBoxTree.kt:211-212`
(`generatesNoBoxes` for col/colgroup) and `swiftui/…/StyleEngine/table/TableBoxTree.swift:194` drop the
box without harvesting its `Width` as a column constraint (css-tables-3 §2.1: columns carry width
contributions). Green td stays 6 px wide in 003/004/007/008 on both natives.

**M-F (T1) · vertical block-flow seam declines authored Width+Height children.**
`ComponentRenderer.swift:4360-4361` ("BAKED-LAYOUT guard": any child with both `Width` and `Height`
⇒ decline) — the test's `.square {height:50px;width:50px}` is authored, not post-load. Compose carries
the same text (`ComponentRenderer.kt:2781-2784`) yet passes this cell; why its flex-item path reaches
side-by-side stacking is **not verified** (§9). Spec: css-writing-modes-4 §6 (block flow is horizontal
under vertical-rl/lr), css-flexbox-1 §9.4 stretch.

**M-G (T2) · UA widget ignores its zero box.** The post-load IR carries `Width 16px × Height 0`
(vertical divs) / `0 × 16` (horizontal) on every `input[type=range]`, and no `Visibility` at all (0
occurrences — the extractor dropped `visibility:hidden`). The mount paints the fixed 129×21 atom:
`widgets/UAWidgetsGeometry.kt:75-76,179`, `ComponentRenderer.kt:3129-3152`;
`StyleEngine/widgets/UAWidgetsGeometry.swift:79-80,182`, `ComponentRenderer.swift:3187-3215`.

**T5 · block-level abspos static position in a vertical-rl container.** Not a flex case
(`layout/flexbox/AbsposStaticPosition.kt:3-30` is flex-only). The vertical block seam declines these
containers on the ORTHOGONAL-child guard (`ComponentRenderer.kt:2771-2773`, `.swift:4353`), so the
`.parent` is placed at the container's **left** instead of its block-start (right) edge, and the abspos
`.item` takes the parent's content origin (iOS: never advances past the 10×20 sibling) or a
horizontal-tb stack (Android: +20 px below the sibling). Ref arithmetic, row 1: parent border-box
25+10+15 = 50 wide at x 30..80, sibling at x 55..65, item static x = 65 → image x 81 ✓.

## 4. Proposed fixes (CSS-correct behaviour, spec section)

1. **M-A** — `ChUnitMetrics.measure(fontFamily = null)` / design "default" must measure the face the
   label paints: bottom out at `InterFontFamily` (Compose) / `UIFont(name:"Inter")` (iOS) exactly as
   `ComponentRenderer.kt:6638` / `.swift:5255` do. css-values-4 §6.1.1 "in the font used to render it".
2. **M-B** — add `inlineAxisUpright: Boolean` to both `measure`/`zeroAdvancePx` (derived from
   `TextExtractor.extractWritingModeConfig(propertyPairs)` in `buildSpacingContext`, and from
   `WritingModeExtractor`/`TextOrientationExtractor` in `StyleBuilder`): when the component's writing
   mode is `vertical-*` and text-orientation `upright`, return the vertical advance = round(ascent) +
   round(descent) of the same face (`Paint.FontMetrics` / `CTFontGetAscent+Descent`); `sideways`,
   `mixed`, `sideways-*` and horizontal keep the horizontal advance. The tr/tbody/col carriers resolve
   with their own computed (inherited) mode even though the property does not apply to them.
3. **M-C** — replace the null decline with the §7.3.1 fallback budget: nearest ancestor's definite
   block size or `max-block-size` (available through the merged property chain), else the ICB block
   extent (`composedIcbExtentDp` / `icbExtent`), then plan; iOS passes it via `budgetOverridePx`.
   Keep the decline only for the truly glyphless/zero-advance cases (`fallbackOwnsRun`).
4. **M-E** — in `TableBoxTree` harvest `Width` from the dropped col/colgroup as a column min/pref
   width (css-tables-3 §2.1, §3.2) before dropping the box.
5. **M-F** — narrow the baked guard to post-load stamps: a child is "baked" only when it also carries
   the post-load trio (`Position` + `BoxSizing` + `OverflowX`), not merely `Width`+`Height`. Mirror on
   Compose for twin parity, then verify Android still passes.
6. **M-G** — `UAWidgetsResolve.resolve` (both twins) returns nil when the component's used `Width` or
   `Height` is `0px`; when both are px, size the atom to the declared box instead of 129×21. Upstream
   (not this lane): the extractor must carry `visibility:hidden` on post-load wires.

Not proposed: modelling a vertical `<td>` whose line-height widens the cell (001/002/005/006 green 120
/ 63 wide) — Chromium itself lays these cells out horizontally (see §9), so the web cells are ref/browser
disagreements and the natives cannot pass 001/002 without diverging from Chromium.

## 5. Blast radius (census over the 1435 per-test IR docs; letters W/I/A = passing cell)

Grep shapes (walk `slot.parent` for inherited WritingMode/TextOrientation):
- M-A/M-B: `"u":"CH"` — **74 docs**; with **no `FontFamily` anywhere: 18** (8 ch-units, 8 css-text/hyphens,
  text-decoration-inset-004, css-values/ch-unit-001). Passing native cells at risk (box widths move
  55/60 → 63 per 5ch): hyphens-auto-001 I A · hyphens-none-shy-on-2nd-line-001 I A ·
  hyphens-out-of-flow-002 I · hyphens-punctuation-001 I · hyphens-span-002 I A ·
  text-decoration-inset-004 I A (**10 cells**). The 56 family-declaring docs (block-ellipsis,
  discard-multicol, bidi-lines …) are untouched by M-A; M-B touches none of them (all horizontal).
- M-C: text leaves under vertical WritingMode with `TextOrientation=UPRIGHT` — **10 docs** (8 ch-units,
  direction-upright-001/-002), plus `mixed` leaves with CJK/fullwidth glyphs — **5 docs**
  (available-size-011, 4 forms/*-appearance-native-vertical-*-baseline.optional). **All 15 fail on both
  natives today; no passing cell at risk.** ASCII-under-mixed runs stay ROTATED (gate unchanged).
- M-E: `meta.sourceTag` col/colgroup carrying `Width` — **5 docs**: ch-units 003/004/007/008 and
  css-tables/border-collapse-dynamic-col-001 (**Android P 0.9812 at risk**, web P; the queue names it).
- M-F: vertical container with an in-flow child carrying `Width`+`Height`, doc not post-load — **11
  docs**; iOS passing at risk: flex-align-baseline-column-vert-lr/-rl-rtl-wrap-reverse,
  height-distribution/td-different-subpixel-padding-in-same-row-vertical-rl, float-vlr-014 (**4 cells**).
- M-G: `input[type=range]` with a `0px` Width/Height — **1 doc** (the target). Vertical-line-height
  carriers (`LineHeight` under vertical WM) — 8 docs, untouched by these fixes.

## 6. Predicted flips (confidence · falsifier)

- T1 iOS +1 — **high**: SSIM is already 0.999, only the colour veto fails; falsified if another Z2 gate
  (inline-level / orthogonal / text-only) also declines, or if the flex item's cross-stretch is the
  cause (Android's pass path must be diffed first).
- T2 iOS +1, Android +1 — **medium-high**: the slider is the only diff (3.5 k px); falsified if the flex
  containers then collapse differently from the 16×0 baked boxes, or if `blockLead` still advances.
- T3 (005–008) iOS +4, Android +4 — **medium**: with M-A+M-B+M-C the picture equals web 005/006 (green
  6×63, blue 63×63, orange 120×120) which scores **P 0.9578**; falsified if the upright glyph line box
  is not 24 px on the natives (Inter 20 px: Compose/SwiftUI one-glyph Text height ≈ 24–25) or if
  `colorFailed` persists (iOS carries cF today on all eight).
- T3' (001–004) natives: 0.84 → ≈0.925 (the web 001/002 analogue with green 6×120) — **no flip**
  without M-E; with M-E 003/004 iOS +2, Android +2 — **medium-low** (column width must reach the cell
  through the natives' table layout; `border-collapse-dynamic-col-001` Android P must not move).
- T5: no flip claimed this wave (effort L, low-medium confidence on the exact placement model).
- direction-upright-001/-002, available-size-011, direction-propagation, bidi-plaintext-br-001: **no flip**
  (upstream structure / font fallback; see §9).

## 7. Pins the builder must write (and how each fails under mutation)

1. `ChUnitMetricsTest` (JVM, pure): `cacheName(null) == "inter"` (or the Inter key) — reverting M-A
   yields "default". Swift twin: `zeroAdvancePx(design:"default")` resolves the registered "Inter" face
   name when present — mutation back to `systemFont` changes the memo key/advance.
2. `ChUnitMetrics.measure(…, inlineAxisUpright = true)` returns ascent+descent, not the x-advance:
   pin with a fake `FontMetrics` (JVM stub) / a CTFont with known metrics on Catalyst; mutating the
   flag read back to `.horizontal` fails the equality.
3. `VerticalTextFlow.uprightColumnIndices(glyphs, 24.0, budget = 568.0)` for "00000" = one column of
   five; and the NEW budget resolver `orthogonalBudget(maxBlockSize = null, icb = 568) == 568`,
   `(maxBlockSize = 16, icb) == 16` — mutating the fallback to null re-declines (test asserts non-null).
4. iOS `VerticalUprightTextFlowLayout` with `budgetOverridePx` set plans instead of placing the
   fallback (Catalyst test: subview count placed == glyph count) — removing the override → 1 placed.
5. `TableBoxTree` column-width harvest: a `<col Width 120>` doc yields a column min-width 120 in the
   pure plan — mutation (drop before harvest) → 0.
6. `verticalBlockFlowZ2` baked predicate: authored `Width+Height` child ⇒ seam ENGAGES; post-load trio
   child ⇒ declines — flipping either branch fails one of the two cases (pure-function extraction,
   Catalyst + JVM twins).
7. `UAWidgetsResolve.resolve` with `Width 16px, Height 0px` ⇒ nil; with `129×21` absent ⇒ spec —
   mutation (drop the zero gate) ⇒ non-nil.
8. Gate pins: the eight ch-units native cells recorded ≥ 0.95 and the ten §5 passing native cells
   recorded flat-or-better (`score-gate.mjs wave51-fix <run>`).

## 8. File ownership (exact paths) and shared seams

- `runtimes/compose/src/main/java/com/styleconverter/runtime/spacing/ChUnitMetrics.kt`,
  `…/StyleApplier.kt` (buildSpacingContext only), `…/core/renderer/VerticalRunIntrinsics.kt`,
  `…/core/renderer/VerticalTextFlowLayout.kt`, `…/typography/text/VerticalTextFlow.kt`,
  `…/table/TableBoxTree.kt`, `…/widgets/UAWidgetsResolve.kt`.
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/spacing/ChUnitMetrics.swift`,
  `…/Renderer/StyleBuilder.swift` (the ch block :349-387), `…/Renderer/VerticalTextFlowLayout.swift`,
  `…/StyleEngine/typography/writing/VerticalTextFlow.swift`, `…/StyleEngine/table/TableBoxTree.swift`,
  `…/StyleEngine/widgets/UAWidgetsResolve.swift`.
- Shared-renderer seams (touch only the named hunks, other lanes own the files):
  `runtimes/compose/…/core/renderer/ComponentRenderer.kt:2774-2784` (anyBakedChild) and `:7622-7637`
  (budget hand-off), `runtimes/swiftui/…/Renderer/ComponentRenderer.swift:4354-4361` (baked guard) and
  `:4969-4987` (pass `budgetOverridePx`). `VerticalTextFlow.{kt,swift}` are byte-parallel twins — one
  spelling per change. Web: **no runtime file** (web is Chromium; §9).

## 9. Not verified / out of reach this wave

- **Web ch-units 001/002 (f 0.9254) and 007/008 (f 0.9170) are Chromium-vs-ref disagreements, not
  runtime defects** (inferred, not run): the harness emits real `<tr>/<td>` (`apps/web-harness/src/sdui/
  ComponentRenderer.tsx:651-655,812-819`) with `writing-mode`/`line-height: 5ch` verbatim; the observed
  6×120 td is a horizontal cell with the inherited 120 px line-height (Blink forces table-internal boxes
  to the table's writing mode), and 007/008's 63×120 is the td's own `text-orientation: upright` making
  `height:5ch` 120 while the ref (rendered from ch-units-vrl-005-ref.html) expects 63. Confirm on wpt.fyi
  before anyone spends a lane on them; if Chromium passes 001, the harness DOM differs and this brief is
  wrong about the vertical-cell model.
- Why Android passes T1 with the same baked predicate (`ComponentRenderer.kt:2781-2784`).
- The exact native one-glyph line-box height for Inter 20 px (assumed 24–25) and whether iOS `colorFailed`
  clears once the orange/blue boxes match.
- T5's placement model beyond the measured offsets; no file:line for the block static-position writer.
- direction-upright-002/-001: web canvas is now 953 vs ref 954 but box contents differ (line order per
  cell, missing magenta/purple cell backgrounds, box widths 150 vs 116); natives stack one float per
  row (2302/2318 px). Upstream of every runtime per the queue's three gaps; not re-diagnosed here.
- direction-propagation-body-contain-root ×3 (0.971): the script-inserted text node before `<body>` is
  absent from the 1-component IR — extractor-owned. bidi-plaintext-br-001 Android (0.9482): Hebrew
  fallback glyphs wider than Inter's, overflowing the 90 px box — font parity, not a wedge.
  available-size-011 natives: prose re-wraps around the fullwidth quotes (fallback font) — M-C fixes
  iOS's word but the wrap residual likely keeps both < 0.95.
- css-values/ch-unit-001 (iOS f 0.8294 cF, Android f 0.8362 cF) is an M-A carrier whose PNGs I did not open.

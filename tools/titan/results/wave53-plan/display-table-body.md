# display-table-body: wave 53 family brief

BACKLOG 0(l″), first bullet. The test is `css/CSS2/css21-errata/s-11-1-1b-006.html`, which puts `display: table` on `<body>`.
The evidence is the gate of record, `wave52-ship`. Everything below was measured or read from files. No device run, build or test suite was used, because the opening gate was running on the host.
Supporting files in this directory:
- `display-table-body.census.py` / `.census.json`: the wire census.
- `display-table-body.geometry.py`: the pixel probe. It is the closing-gate check that replaces SSIM for these cells.

## 1. Target cells

| cell | wave51-fix | wave52-calib | **wave52-ship** | picture |
|---|---|---|---|---|
| `CSS2/css21-errata/s-11-1-1b-006` ios | f 0.9321 | f 0.9288 | **P 0.9953** | DEGENERATE (cell-review, 2nd reader) |
| `CSS2/css21-errata/s-11-1-1b-006` android | f 0.9332 | f 0.9298 | **P 0.9944** | DEGENERATE (orchestrator, same geometry) |
| `CSS2/css21-errata/s-11-1-1b-006` web | P 0.9656 | P 0.9923 | **P 0.9941** | **also wrong, newly measured here.** The gate never reviewed it because it never flipped. |

The ship flip changed the text and the square's x position. It did not fix the square's y position. Two wave-52 L2 fixes did it:
- M1 moved the body margin onto the canvas, so the square went from x 16–35 to x 24–43.
- T6 gave the hoisted `<p>` its UA margin, so the text moved down 16 px.

On both natives the square's rows went from 59–78 (wave51-fix / calib) to 51–70. Both are wrong: first 3 px low, now 5 px high. Measured with `display-table-body.geometry.py wave51-fix 006`.

## 2. The picture

All images are full size, 390×600, with a 16-px frame. "Ink" means a pixel with an RGB sum below 600. "Square" means rows that hold an 18-px or longer run of pure black.

| image | text ink rows | black square rows / columns |
|---|---|---|
| reference (`…/CSS2/css21-errata__s-11-1-1b-006.png`) | 35–51, x 24–355 | **56–75**, x 24–43, which is document (8, 40) |
| ios capture | 35–51 | **51–70**, x 24–43. The text and square form one ink band, 35–70. |
| android capture | 36–50 | **51–70**, x 24–43. One ink band, 36–70. |
| web capture | 35–51 | **66–85**, x 24–43 |

- **Natives:** the square is 5 px too high and touches the last text row. The reference leaves a 5-px white gap there.
- **Web:** the square is 10 px too low.
- The text, the x position and the 20×20 size are right on all three platforms.
- The test asserts two things: the body's `overflow: hidden` goes to the viewport, so the square is unclipped, and the body is laid out as a table. The second assertion fails visibly on all three platforms.

## 3. The wire

From `runs/wave52-ship/sections/CSS2/per-test-ir/wpt__CSS2__css21-errata__s-11-1-1b-006.json`. It is flat. **No component has a `slot`.**

```
0-141 body-root: OverflowX/Y HIDDEN, Display "TABLE", BorderSpacing {single, px 0},
      MarginTop {px 40}, MarginRight/Bottom/Left {px 8}
1-142 (div.caption, role ws-after): Generic {propertyName "display", rawValue "caption", _unmapped true},
      MarginBottom {px 10}
2-143 (div.td, role ws-after): Display "TABLE_CELL", Width/Height {length, px 20},
      MarginTop {px -15}, BackgroundColor black
3-144 (p): Position ABSOLUTE, Top {px 0}, Left {px 8}, text "Test passes if there is a black square below."
```

- `display: caption` is not a CSS keyword. CSS 2.1 §4.2 says to ignore an illegal value, so the div is a plain block. The wire is right to leave it unmapped.
- The caption div and the td reach every platform as roots stacked after the body-root. They are siblings of the body-root, not its children.

## 4. Mechanism (traced in code; the arithmetic matches the pixels on all three platforms)

1. **Extractor.**
   - Body children become sibling roots. `tools/titan/extract-fixture.mjs` nests them under the body-root only in one case, the wave-17 BODY-HEIGHT SLOTTING rule (`bodyDeclaresAbsoluteHeight`, about line 9590): the body declares a nonzero absolute height. This body declares none.
   - So no runtime ever sees a table that contains the caption div and the td. `Display TABLE` applies to an empty box.
2. **Native canvas (Android and iOS, the same code in twin form).** The composed canvas stacks roots as a block flow.
   - **Caption div (1-142).** `composedRootStackPlan` handles it. The code is in `apps/android-harness/…/screenshot/UaBlockMargins.kt` on Android and `runtimes/swiftui/…/spacing/ComposedRootStack.swift` on iOS.
     - It returns static edges (0, 10): rule R2/R3, with `stripDeclared` set.
     - `isSelfCollapsingRoot` is true (no `Display`, no height, no content), so the root is `marginTransparent`. Its 10 px joins the open margin set.
   - **td (2-143).**
     - `MarginTop -15` fails `StaticEmMargin.edgePx`. Rule E5 says a negative value returns null; see `StaticEmMargins.kt` around line 150 and its Swift twin, `StaticEmMargins.swift` around line 132.
     - So `rootStackMargin` takes R4/R5: (0, 0) with `stripDeclared = false`. The root is opaque.
     - `collapsedRootStackGapsPx` / `stackedSpacing` then put a **10-px gap** above it.
     - The td paints its own −15 through MarginApplier's negative branch. On Android that is `Modifier.offset` (`spacing/MarginApplier.kt` around line 176); on iOS it is `.offset()` (`MarginApplier.swift` around line 92).
   - **Body-root.** It is an empty table whose margin the canvas owns (M1), so it adds 0 height.
   - **Result:** 16 (frame) + 40 (canvas margin) + 10 − 15 = **51**, as measured.
   - Neither native margin path knows that margins do not apply to table cells (CSS 2.1 §8.3, "Applies to"). The only §8.3 guard is the body-root's own: `bodyMarginDoesNotApply` in `ScreenshotCaptureScreen.kt` around line 1475, and `tableInternalDisplays` in `ComposedRootStack.swift` around line 565.
   - **Correction to the reviewers' guess:** the +10 and −15 do not collapse to −5. The fold floors negatives, and the −15 never enters it. The result is a 10-px gap followed by a −15-px offset. The net is the same −5, but the mechanism differs, so a fix for negative-margin collapsing would not move this cell.
3. **Web.**
   - `apps/web-harness/src/ui/ComposedCaptureGallery.tsx` renders `flowRoots` inside the M1 `data-capture-flow` `display: flow-root` wrapper (around lines 955–990).
   - The body-root node is an empty `display: table` box. The caption div's 10-px margin is a real margin.
   - The td is a real `table-cell`. Chrome wraps it in its own anonymous table and drops its margin.
   - **Result:** 16 + 40 + 10 = **66**, as measured.
4. **What the reference encodes (Chrome on a real table body, CSS 2.1 §17.2.1 rule 2).**
   - The caption div and the td are both not proper table children, so together they form one anonymous row.
   - The div gets an anonymous cell, which is 0 px wide because the div is empty.
   - The td's margin does not apply (§8.3). The div's margin-bottom stays inside its anonymous cell.
   - **Result:** the td is at (8, 40), which is image rows 56–75.

## 5. The fix

**Body-as-table on the canvas.** It is gated on the body-root's last `Display` being `TABLE` or `INLINE_TABLE`.

**Natives.**
- Add one pure helper with twins:
  - `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt`
  - `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBodyForest.swift`
  - The Swift twin must live in the runtime module because `IRComponent`'s memberwise init is internal. That is the same reason `withCanvasOwnedBodyMargin` lives in `ComposedRootStack.swift`.
- What it returns:
  - The body-root itself is left untouched, because every canvas resolver reads it.
  - The run of in-flow roots after the body-root is replaced, at the run's first position, by one synthetic `TABLE` root (id `<body-root id>#table`). That root carries the body-root's `BorderSpacing` / `BorderCollapse`.
  - Under it sits one synthetic `TABLE_ROW`. The row's children follow §17.2.1 rule 2:
    - Each run of consecutive non-cell roots goes into one synthetic `TABLE_CELL`.
    - Cell roots pass through unchanged, except that their `Margin*` declarations are removed (§8.3).
  - Out-of-flow roots stay roots in their place: hoisted ones, and RC1 static-position ones. Use the same predicates the fold uses: `CanvasRootHoist.shouldHoistToCanvasRoot` / `rendersInFlowAsStaticPosition`, and `FixedHoist` / `ComponentRenderer.isOutOfFlow`.
- When it returns its input unchanged (the same instance):
  - Anything other than a table body. 1434 of the 1435 documents take this path.
  - A proper table child among the run (row, row-group, caption or column). There are 0 carriers, and bailing keeps the scope honest.
  - A body-root that paints its own border or padding box (0 carriers).
- Call sites:
  - Android: `ScreenshotCaptureScreen.kt` `ComposedCaptureCanvas`, in the `remember(roots, canvasMargin)` rewrite around line 1705, after `withUaBlockMarginOnHoistedRoot`.
  - iOS: `CaptureCanvas.swift` around line 379, as the input to `FixedHoist.split`.
- The runtimes' existing `table → row → cell` path then lays it out (Compose `RenderTableContent`, SwiftUI table branch). `TableBoxTree.shrinkToFitBox` makes the table hug its columns.

**Web.**
- In `ComposedCaptureGallery.tsx`, when the body-root is a table:
  - Emit the `data-capture-flow` wrapper even at zero margin.
  - Give it `display: table` and the body-root's `border-spacing`.
- Chrome then does §17.2.1 itself. It is the reference engine. This is about 10 lines plus a `resolveCanvasTableBody(doc)` resolver.
- The empty body-root node inside lands in the first anonymous cell, at 0×0.

**Spec:**
- CSS 2.1 §17.2.1, "Anonymous table objects", rule 2, "generate missing child wrappers".
- CSS 2.1 §8.3, where the margin properties' "Applies to" excludes table-internal displays.
- The caption div is a plain block under CSS 2.1 §4.2.

**Seams: none.** No hunk is needed in `ComponentRenderer.{kt,swift}`, the web `sdui/ComponentRenderer.tsx` or `extract-fixture.mjs`. The three canvas files are shared harness files: coordinate with other lanes, but they are not seams.

**If the T10 lane is staffed.** T10 is 11(h), the anonymous table fixup for `css-tables/fixup-dynamic-anonymous-*`. It implements the same §17.2.1 rule-2 function inside `TableBoxTree.{kt,swift}`. In that case the native helper shrinks to "synthetic TABLE around the in-flow run, plus the margin strip", and it calls T10's fixup. That is why this brief recommends folding into T10.

**Rejected alternatives:**
- **(a) Only strip margins from table-internal roots.** The td would land at image rows 66–85, which is web's wrong picture. That trades one wrong picture for another that still passes, so it would be a fake fix.
- **(b) Extend BODY-HEIGHT SLOTTING to table bodies.**
  - It needs the extractor seam, plus a runtime T10 change for the misparented td.
  - It runs into the measured wave-30 "WHY NOT SLOTTING" record (extract-fixture.mjs around line 9617). Nesting under an unsized body-root moved `text-decoration-inset-001` android from 0.9742 to 0.8162.
  - The body's `overflow: hidden` would then clip its slotted children unless §11.1.1 propagation were modelled.
- **(c) A canvas `Row` hack.** It would be a second, partial copy of table layout.

## 6. Blast radius (`display-table-body.census.json`, wave52-ship per-test IR, 1435 docs)

- **Carrier predicate:** the body-root's last `Display` is `TABLE` or `INLINE_TABLE`. Exactly **1 document**.
- **Carrier cells, which are the allowed set for `control-check.mjs`:**
  `wpt__CSS2__css21-errata__s-11-1-1b-006` on web, ios and android.
  All three pass today, and all three are wrong pictures.
- **Body-root `Display` across the 284 documents that have a body-root:**

| display | docs |
|---|---|
| absent | 259 |
| NONE | 14 |
| CONTENTS | 3 |
| INLINE | 2 |
| FLEX | 2 |
| GRID | 2 |
| TABLE_CELL (`s-11-1-1b-005`) | 1 |
| TABLE | 1 |

  The flex and grid bodies have the same "the body's layout never reaches its children" shape. They are out of scope and have no carrier here.
- **Neighbours on the margin path.** These are not carriers; they matter if anyone widens the §8.3 strip beyond the trigger.
  - A nonzero margin on any table-internal box occurs in exactly 2 documents:
    - `s-11-1-1b-005`: the body-root. Its margin is already guarded by M1's §8.3 rule. Today it is web P 0.9658, ios f 0.9536, android f 0.9307.
    - `s-11-1-1b-006`: the td.
  - Table-internal roots occur in 4 documents: 005, 006, `css-position/position-absolute-dynamic-static-position-table-cell` (P ×3, at 0.9990, 0.9974 and 0.9967) and `css-tables/baseline-vertical` (f ×3).

## 7. Predictions

- **`s-11-1-1b-006` ios:** stays P, and the picture becomes right (square 56–75, x 24–43, red 0). **MED.** The native table path has never run on a synthetic table. The risk is the anonymous cell around an empty block taking the block-fill width and pushing the td right.
  The expected score is about 0.998, by analogy with 007 ios, the directory's one faithful twin, at 0.9992. **LOW** on the number.
- **`s-11-1-1b-006` android:** the same, with 007 android at 0.9983. **MED** for the picture, **LOW** for the number.
- **`s-11-1-1b-006` web:** stays P with the square at 56–75. **HIGH** for the geometry, because Chrome does the fixup. Score of at least 0.998: **MED**.
- **What falsifies a native prediction:** the probe shows the square at any position other than x 24–43, or not at rows 56–75. In that case the native half does not ship. Web can ship alone.
- **Must not move:** byte-identical captures everywhere outside the carrier. The named watch list:
  - `s-11-1-1b-001`…`-005`, `-007`…`-009` ×3
  - `position-absolute-dynamic-static-position-table-cell` ×3
  - `css-tables/baseline-vertical` ×3
  - If this is folded into T10: T10's own carriers in `css-tables/fixup-dynamic-*`. Those belong to T10.

## 8. Verification plan (the builder runs this after the opening gate frees the host)

**Native pins on the verbatim 006 payload (§3).** These go in a new `TableBodyForestTest` (JVM) and `TableBodyForestTests` (Catalyst).

| pin | asserts | mutation that turns it red |
|---|---|---|
| shape | Output is `[body-root unchanged, TABLE#table{BorderSpacing single 0} → ROW → [CELL{children:[1-142 verbatim]}, 2-143 with no Margin*], 3-144 unchanged]`. | — |
| M1, gate | Identity (the same instance) on the verbatim 005 document (body-root `TABLE_CELL`) and on the verbatim `s-11-1-1b-001` document. | Drop the trigger check. |
| M2, §8.3 | `2-143` carries no `MarginTop`. | Skip the strip. |
| M3, out-of-flow | `3-144` stays a root, at index 2. | Remove the out-of-flow filter. |
| M4, anonymous cell | `row.children[0]` is `TABLE_CELL` wrapping `1-142`. | Put the div straight into the row. |
| M5 | The table carries `BorderSpacing`. | Don't copy it. |
| stack | `composedRootStackPlan` + `collapsedRootStackGapsPx` (Swift: `stackedSpacing`) over the wrapped roots give a 0 gap above `#table`. | Keep `1-142` in the stack, which brings the 10-px gap back. |

**Web pin.** In `apps/web-harness/tests/ui/` (a sibling of `ComposedCanvasMargin.test.tsx`), render the verbatim 006 document.
- Assert that `[data-capture-flow]` has `display: table` and `border-spacing: 0px`.
- Mutation: remove the table branch.
- The existing M1 pins must stay green.

**Focused suites** (JDK 21):
- `(cd apps/android-harness && ./gradlew :app:testDebugUnitTest --tests '*UaBlockMargins*' --tests '*TableBodyForest*' :runtime:testDebugUnitTest --tests '*TableBoxTree*')`
- Catalyst: `-only-testing:StyleConverterRuntimeTests/ComposedRootStackTests -only-testing:StyleConverterRuntimeTests/TableBodyForestTests`
- `npm -w apps/web-harness run test -- tests/ui/ComposedCanvas`

**Closing gate.**
- **Probe sections:** **CSS2** (the target and its neighbours), **css-tables** (the table path, and T10 if folded), and **css-position** (the table-cell-root neighbour).
- **The verdict on 006 is geometry, not SSIM.** Run `python3 tools/titan/results/wave53-plan/display-table-body.geometry.py <run> 006`. Every platform must print `square rows 56-75 (20) x 24-43 | red px 0`.
- `control-check`: the allowed set is the one carrier on all three platforms. Everything else must be byte-identical.

## 9. Risks and recommendation

**Risks:**
- **Zero cell gain.** All three cells already pass, so the value is honesty: 3 wrong passes become right ones. A broken native table layout of the synthetic table could lose 2 cells (§7 falsifier).
- **The native table path is unexecuted on this shape.** A synthetic table, a synthetic row, an anonymous cell holding an empty self-collapsing block, and a `PlaceholderContent` fallback for the still-empty body-root table: none of these has run.
- **The three canvas files are hot.** Other wave-53 lanes may edit `ScreenshotCaptureScreen.kt`, `CaptureCanvas.swift` and `ComposedCaptureGallery.tsx`.
- **The overflow assertion stays untested.** Body `overflow: hidden` propagation (§11.1.1) is still not modelled. 006 doesn't need it because nothing overflows the 20×20 table.

**Recommendation: GO-SMALL.** Fold this into the T10 anonymous-table-fixup lane (11(h); PLAN §6 names it a wave-53 lane). It is the same §17.2.1 rule-2 function plus three gated call sites, with a one-document control set. If T10 is not staffed, it is **NO-GO** for this wave: keep 0(l″) open with this trace. It is not a wall.

## 10. Neighbour finding (not this fix; mechanisms untraced)

The same directory is mostly wrong pictures that pass. Measured with `display-table-body.geometry.py wave52-ship 001 002 003 004 006 007 008 009`, against the reference.

| test | ref square | web | ios | android |
|---|---|---|---|---|
| 001 | 78–97, x 16–35, red 0 | 77–101 (25 rows), x 18–37, **red 180** | 73–97 (25), **red 100** | 73–97 (25), **red 100** |
| 002 | 112–131 | 82–103 (22), x 18–37 | 78–102 (25), **red 200** | 78–102 (25), **red 200** |
| 003 / 004 | 78–97 | 55–74, x 18–37 | **68–72 (5 rows)** | **68–72 (5 rows)** |
| 007 | 68–87, x 26–45 | 68–79 (12), x 18–37 | ✓ | ✓ |
| 008 / 009 | 78–97 | 77/81–101/105 (25), **red 180** | 73–97 (25), **red 100** | 73–97 (25), **red 100** |

- **22 of the 24 passing cells** in 001–004 and 006–009 do not match their reference. Only 007 ios and 007 android do.
- Every one of the 22 scores at least 0.9866 (the lowest is 002 android). On these 20×20 objects, SSIM is blind to displacements of 5–34 px and to red failure ink.
- What the measurements show, mechanisms not traced:
  - The natives do not clip at a table box's `overflow` edge: 001, 002, 008 and 009 show the red border.
  - On 003/004 the natives keep only 5 rows of the square.
  - Web draws the square 2 px right and in the wrong rows.
- Recommendation for the orchestrator: book these as DEGENERATE in 0(b)/0(l″) so they are never counted as passes, and consider a wave-54 family brief for "table overflow clip / caption".

# compose-table-body-cell: wave 54 family brief

BACKLOG "Next-wave obligations (wave 54 opens with these)" 0(a)(2), with 0(b) and queue item 0(l″) riding on it. This is the
re-do of wave-53 unit **L3 B-android** (`276757ea`, reverted by `d773ff6a` at the probe): the Compose canvas's anonymous table
forest for a `display: table` body (CSS 2.1 §17.2.1). The only test is `css/CSS2/css21-errata/s-11-1-1b-006.html`.

Evidence is frozen: the gate of record `wave53-final`, the opening gate `wave53-open`, and the wave-53 probe run `wave53-probe`
(the B-android picture). Everything below was read from files, measured from PNGs, or computed by a node/python script that
only reads run directories and sources. No build, device, emulator, simulator or Chromium ran, because `wave54-open` was gating
on this host.

Files written beside this brief (all read-only over the runs):
- `compose-table-body-cell.geometry.py` → `.geometry.out.txt`: the gating probe (§7).
- `compose-table-body-cell.census.mjs` → `.census.json` / `.census.out.txt`: the corpus census over wave53-final's 1435 per-test
  IR documents, joined to the scored cells through `score-gate.mjs`'s own loader (§5).
- `compose-table-body-cell.replay.mjs` → `.replay.out.txt`: the score replay, using wave-53's method (§8).
- `compose-table-body-cell.corroborate.py` → `.corroborate.out.txt`: two corpus pictures that show the same two Compose
  behaviours outside 006, plus the row-by-row dump of the probe picture (§2, §3).

## 1. Target cells

From `node tools/titan/results/wave52-gate/cells.mjs 's-11-1-1b' wave53-open wave53-probe wave53-final`:

| cell | wave53-open | wave53-probe (B-android landed) | **wave53-final** (B-android reverted) | picture (§7 probe) |
|---|---|---|---|---|
| `CSS2/css21-errata/s-11-1-1b-006` **android** | P 0.9944 | P 0.9906 | **P 0.9944** | WRONG at every run: square 5 px high (open/final), outlined empty box plus a displaced square (probe) |
| … ios | P 0.9953 | P 0.9992 | P 0.9992 | OK since the probe (B-ios, kept) |
| … web | P 0.9941 | P 1 | P 1 | OK since the probe (B-web, kept) |

- **The target is one cell, and it already passes.** The value is honesty: a DEGENERATE pass (0(b), 0(l″)) becomes a faithful
  one.
- The per-test IR of 006 is byte-identical in wave53-open, wave53-probe and wave53-final (`cmp`). So the probe picture comes
  from the runtime, not from the wire.

## 2. The defect as seen in the pictures

PNGs opened: the reference `tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/CSS2/css21-errata__s-11-1-1b-006.png`,
and `tools/titan/runs/wave53-probe/sections/CSS2/{android,ios}-screenshots/wpt__CSS2__css21-errata__s-11-1-1b-006.png`.

- **Reference and iOS (probe and final):** one solid black 20×20 square at x 24–43, rows 56–75, under the text.
- **Android at the probe** (`.corroborate.out.txt`, rows 55–76):
  - A **1-px black outline of an empty 20×20 box** sits at x 24–43, rows 56–75. That is exactly where the square belongs.
    The outline has stroke columns x 24 and x 43, and full-width rows 56 and 75.
  - The **solid square is displaced 20 px right**, to x 44–63, rows 56–75.
  - Measured by the §7 probe: `square block solid 76/400 | stray ink 400 (x 44-63, rows 56-75)`.
    - 76 = 20 + 20 + 18 + 18 is the 1-px perimeter of a 20×20 box.
    - 400 = the whole 20×20 square, but outside its block.
- **Android at open and final:** the old picture. The square is at rows 51–70, 5 px high, touching the text. The probe prints
  `solid 300/400 | stray ink 80 (x 24-43, rows 52-55)`.

**Corrections to the wave-53 record:**
- The gate note says the extra box is "beside" the square, and the lane note's verdict says "to the left of" it. Neither is
  right: the empty box *takes the square's place*, and the square moves right.
- The lane predicted "no square, or a squeezed one". It did not foresee the spill or the outline. Both are explained in §3.
- The rows were already right on the probe (56–75). Only the x position and the outline were wrong.

## 3. Mechanism (traced in code; the arithmetic reproduces every pixel of the probe picture)

**The forest is the same on both natives; the drawing differs.**

The Kotlin rewrite (`git show 276757ea`, `runtimes/compose/…/table/TableBodyForest.kt` `rewrite`) and
`runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/table/TableBodyForest.swift` `rewrite` are line-for-line twins. Both
return:

```
[body-root, #table{Display TABLE, BorderSpacing 0} → #row → [#cell0{TABLE_CELL} ⊃ 1-142, 2-143 minus Margin*], 3-144]
```

- The six JVM pins of 276757ea fixed this shape.
- The iOS probe picture is right with the same shape.
- The two runtimes classify out-of-flow boxes through different predicates: Kotlin uses
  `CanvasRootHoist.shouldHoistToCanvasRoot || rendersInFlowAsStaticPosition`, Swift uses `ComponentRenderer.isOutOfFlow`. Both
  put the abspos `<p>` 3-144 out of flow (pin m3).

The defect lives in two Compose table-renderer behaviours that the synthetic table is the first box to hit together.

1. **The table hugs its content.**
   - `#table` declares `Display TABLE`, so it routes to `DisplayType.TABLE` (`runtimes/compose/…/core/renderer/ComponentRenderer.kt:8168`)
     and then to `TableApplier.Table(…)` (`:2638–2697`).
   - Its role is TABLE, so `shrinkToFit = TableBoxTree.enforcesAutoTableWidth(…)` is true (`TableBoxTree.kt:240/269`).
     `TableApplier.Table` then wraps the table Column in `IntrinsicChannel.widthAtMaxIntrinsic` (`TableApplier.kt:253`,
     `IntrinsicChannel.kt:207`).
   - The table width is therefore the sum of the cells' max-content widths, **0 + 20 = 20**.
2. **D1, the demo stroke.**
   - The call site computes `fabricatedCellBorder = !(LocalWptCaptureMode.current && component.properties.none { it.type == "Display" } && uaRoleOf(_tag) == TABLE)`
     (`ComponentRenderer.kt:2670`).
   - The synthetic table *declares* `Display` and has no `_tag`, so the value is **true**.
   - `TableApplier.TableCell` (`TableApplier.kt:434`) then paints `Modifier.border(1.dp, Color.Black)` on every cell of the
     separated model (`:455–466`, the stroke at `:465`).
   - The kdoc of `LocalTableFabricatedCellBorder` (`:105–145`) calls this a "demo default" with "NO CSS basis". It is kept only so
     the frozen captures of declared tables do not move.
   - On iOS nothing like it exists: `grep -i fabricat` finds nothing in the SwiftUI table path.
3. **D2, the first cell takes the block fill.**
   - `RenderTableContent` (`ComponentRenderer.kt:3073`) puts the cells in `TableApplier.TableRow`, a `Row` with `fillMaxWidth`
     (`TableApplier.kt:368/390`), each inside a `TableCell { RenderComponent(cell) }` (`ComponentRenderer.kt:3127–3141`).
   - `#cell0` is `TABLE_CELL`, so it routes to `DisplayType.BLOCK` (`:8169–8170`).
   - Its `blockFlowWidth` (`:1543–1579`) is `fillMaxWidth()` (`:1578`), because:
     - it is composed WPT;
     - it has no Width, MinWidth, InlineSize or MinInlineSize;
     - it is not abspos;
     - `isShrinkToFitTable` is false, since `shrinkToFitBox` is `role == TABLE` only (`TableBoxTree.kt:240`).
   - A `Row` measures non-weighted children in order against the remaining width. The anonymous cell, first in the row, takes
     **all 20 px**.
4. **The td spills.**
   - 2-143 is measured with a max width of 0.
   - Its `Width 20px` goes through `SizingApplier.kt:357` → `exactWidth`. `ExactWidthOverflow.measureSpec(20, 0, 0)` returns (20, 0)
     (`ExactWidthOverflow.kt:46–52`): it is measured at its declared 20, reported as 0, and placed `placeRelative(0, 0)`
     (`:63–80`).
   - So the black fill paints **start-anchored past its 0-wide slot, at x 44–63**.
   - Its own TableCell is 0 px wide, so its stroke is invisible.
5. **The arithmetic.**
   - The table origin is (16 + 8, 16 + 40) = (24, 56). The 40 is the canvas-owned body margin, and the stack pin of
     276757ea gives the 0 gap.
   - The anonymous cell is x 24–43 × rows 56–75 with a 1-dp stroke. The capture density is 1 px per dp (`StyleApplier.kt:1047`), so
     the stroke is the 76 px measured.
   - The td is x 44–63. That makes 40 px of ink, which matches the probe's `x 24-63`.

**Why iOS is right.**
- `TableSeparatedLayout` sizes every cell at `sizeThatFits(.unspecified)`, its ideal size
  (`runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/TableSeparatedLayout.swift:70–71`, reached from `tableTrackPlan`,
  `ComponentRenderer.swift:1655/2987`).
- So the empty anonymous cell is 0 wide, and there is no stroke.
- Web: Chrome runs §17.2.1 itself on the `display: table` wrapper (`apps/web-harness/src/ui/CanvasTableBody.ts`).

**Corroboration on shipped wave53-final captures** (`.corroborate.out.txt`; both PNGs opened):
- **D1:** `css-tables/height-distribution/percentage-sizing-of-table-cell-children-003` android (P 0.9954) has a 396-px black
  rim around its green cell.
  - Black (16, 88)–(115, 187) is the 1-px perimeter of a 100×100 box (4 × 100 − 4 = 396).
  - Green covers 9604 px, which is 98².
  - The reference, web and iOS have no rim and 10 000 green px.
  - The test declares `display: table`.
- **D2:** `css-tables/baseline-empty-cell-001` android (P 0.9956) has stroke columns [16, 78, 79], against the reference's
  [16, 73, 76]. One wide first cell is drawn and the second, empty cell is gone. The wave-53 lane already named this capture as
  its warning.

**Why "just don't fill" is not enough.**
- If the anonymous cell merely wraps its content, it still passes the Row's max width down to 1-142.
- 1-142 is an ordinary block, so it takes `fillMaxWidth()` itself and re-inflates the cell.
- The cell has to be **sized at its max-content width**. That is `IntrinsicChannel.widthAtMaxIntrinsic`, the same primitive
  the table already spends.
- This is CSS 2.1 §17.5.2.2 auto table layout when the shrink-to-fit table has room (sum of max-content ≤ available): each
  column gets its max-content width.

## 4. The fix

**Re-land 276757ea unchanged, then add two narrow pieces, both keyed on one marker.**

`git show 276757ea | git apply --check` is clean on HEAD 7cce3b22. `ComponentRenderer.kt` and `TableApplier.kt` are unchanged
since 276757ea (`git diff --stat 276757ea HEAD` touches only `ScreenshotCaptureScreen.kt`, i.e. the revert).

**(a) The marker.** In `runtimes/compose/…/table/TableBodyForest.kt`:
- Add `const val ANONYMOUS_ROLE = "anonymous-table"`.
- `synthetic(…)` passes `role = ANONYMOUS_ROLE` to the three synthetic boxes: `#table`, `#row` and `#cell<k>`.
- Add `fun isAnonymous(c: IRComponent) = c.role == ANONYMOUS_ROLE`.

Why this is safe:
- `IRComponent.role` is the decoded `meta.role` (`IRModels.kt:343`). These boxes exist only in memory and are never serialized.
- The only role values read anywhere are `body-root` and `line-break` (grep over `runtimes/compose/src/main` and
  `apps/android-harness/app/src/main`). Nothing else matches the new value.
- The Swift twin stays as it is (`meta: nil`). Record that asymmetry in the Kotlin header. Editing the Swift file would
  change the iOS build for nothing.

**(b) The chrome decision.** Add `runtimes/compose/…/table/TableCellHug.kt` (new, about 60 lines):
- `val LocalTableCellsHugContent = compositionLocalOf { false }`.
- A pure function
  `fun chrome(component: IRComponent, fabricatedDefault: Boolean) = Chrome(stroke = fabricatedDefault && !TableBodyForest.isAnonymous(component), hug = TableBodyForest.isAnonymous(component))`.
  It can be pinned on the JVM.
- `Modifier.hugColumn()`: `with(IntrinsicChannel) { widthAtMaxIntrinsic(TAG, REFUSAL) }` when the local is true, else `this`.
  REFUSAL reads: "§17.5.2 column max-content skipped — the cell keeps the fill".

**(c) The applier.** In `runtimes/compose/…/table/TableApplier.kt`:
- `Table(…)` gains `cellsHugContent: Boolean = false`. The default false is the frozen behaviour.
- `Table` provides `LocalTableCellsHugContent`.
- `TableCell` chains `.hugColumn()` before `.fillMaxHeight()`.
- That is about 8 lines. The logic lives in (b), because `TableApplier.kt` is already 761 lines.

**(d) One seam hunk.** In `ComponentRenderer.kt`'s `DisplayType.TABLE ->` branch, at the `TableApplier.Table(…)` call
(`:2638–2697`):
- Compute `val chrome = TableCellHug.chrome(component, fabricatedDefault = <today's expression, verbatim>)`.
- Pass `fabricatedCellBorder = chrome.stroke` and `cellsHugContent = chrome.hug`.
- The lane delivers this as a patch. It never edits the seam.

**Result on 006.**
- The anonymous cell hugs to 0 px: its run is the empty 1-142, and its max-content is 0 by the probe's own sum, 20 − 20.
- The td's cell hugs to 20: `measureSpec(20, 0, ∞)` = (20, 20).
- The table is 20 wide, so the td sits at x 24–43, rows 56–75. Nothing is stroked.

**Spec basis.**
- CSS 2.1 §17.2.1: the anonymous cell, row and table carry no border. Border is not inherited, so the anonymous boxes keep the
  initial `border-style: none`.
- §17.6.1: in the separated model a cell paints only its own borders.
- §17.5.2.2: the auto-layout column width.
- §8.3: no margins on cells. This is already in 276757ea.

**Rejected alternatives.**
- **(i) Elide an ink-free anonymous cell in the forest (no seam).**
  - It fixes 006's picture, but it deletes a box that CSS keeps: a fake fixup.
  - A run holding text would still be filled and stroked.
- **(ii) Give the anonymous cell `Width: max-content` in the forest (no seam).**
  - It fabricates an author declaration.
  - It makes the Kotlin and Swift forests diverge.
  - The stroke stays.
- **(iii) The general versions: every composed table's cells hug, and every declared table loses the stroke.**
  - That moves the 8 D2 documents and the 12 D1 documents listed in §5. Every declared-`display: table` capture in the frozen
    corpus was measured *with* the stroke (`TableApplier.kt:141–142`; `ComponentRenderer.kt:2659–2661`).
  - It is a separate lane with its own census and predictions (§9), not this family.
- **(iv) D2 alone.**
  - It gives the same 006 bytes (black stroke on black fill, `.replay.out.txt` "identical bytes to FIX: true").
  - But it keeps fabricated ink on an anonymous table, and its picture would then depend on how a 0-wide `Modifier.border`
    behaves.
  - D1 rides in the same seam hunk at no extra radius, so keep both.

## 5. Census of the corpus radius (`compose-table-body-cell.census.out.txt`, wave53-final, 1435 documents)

**What the corpus carries**

| construct | documents | cells |
|---|---|---|
| A table-family `Display` or a UA table tag (`table`/`tr`/`td`/`th`/`caption`/`tbody`/`thead`/`tfoot`/`col`/`colgroup`) | **86** | — |
| Route at least one box to Compose `DisplayType.TABLE` | **60** (22 declared, 38 by the wave-38 UA fold) | android **P 40 / f 20** |
| The TableBodyForest trigger (body-root's last `Display` ∈ {TABLE, INLINE_TABLE}) | **1**: `CSS2/css21-errata/s-11-1-1b-006` | — |
| Synthetic anonymous boxes, and so `isAnonymous` / `chrome.hug` / `!chrome.stroke` | only that 1 document | — |

**This unit's carrier set is one capture: `wpt__CSS2__css21-errata__s-11-1-1b-006` android.**
- The wire does not change.
- iOS and web files are not touched.
- On every other Compose table, `chrome(…)` returns (today's stroke, false) and `cellsHugContent` keeps its false default. Those
  tables are byte-identical by construction.
- The forest's identity elsewhere is already proven on device. The wave53-probe control shows css-tables android
  `compared 48 identical 48` and CSS2 android `changed 1 (carriers 1)`, which is 006
  (`tools/titan/results/wave53-gate/probe/control-L3-canvas-root.txt`).

**Not moved by this unit; recorded for the follow-up.** These are the general shapes the same two behaviours produce today.
Android cells are from wave53-final.

- **D1: a declared-`Display` table in the separated model, with rows (12 documents).**
  - Android passes, 8: `css-break/background-image-006` (0.9966), `css-position/position-absolute-center-006` (0.9692),
    `css-tables/abspos-container-change-dynamic-001` (0.9654), `percentage-sizing-of-table-cell-children-003…-006` (0.9954 each),
    `css-text/hanging-punctuation/hanging-scrollable-001` (0.9742).
  - Android fails, 4: `position-absolute-center-007` (0.9445), `css-tables/anonymous-table-cell-margin-collapsing` (0.9953),
    `css-tables/fixup-dynamic-anonymous-inline-table-002` (0.9434), `selectors/invalidation/nth-child-of-attr-largedom` (0.9442).
- **D2: a row of at least 2 cells where a non-last cell takes the fill (8 documents).**
  - Android passes, 3: `css-display/display-contents-td-001` (0.9753), `css-tables/baseline-empty-cell-001` (0.9956),
    `css-tables/colspan-004` (0.9873).
  - Android fails, 5: `css-contain/contain-content-004` (0.8286), `css-tables/baseline-vertical` (0.5727),
    `css-tables/border-conflict-resolution` (0.8394), `css-values/calc-height-table-1` (0.8989),
    `css-writing-modes/direction-upright-002` (0.5935).

**The 26 table-family documents that never reach Compose `DisplayType.TABLE`** (listed in `.census.json`). They are untouched
by any version of this fix. They include:
- `s-11-1-1b-001/-002/-005/-008/-009`;
- `css-position/position-absolute-dynamic-static-position-table-cell` (android P 0.9967);
- `fixup-dynamic-anonymous-inline-table-001/-003` and `fixup-dynamic-anonymous-table-001`.

`s-11-1-1b-009`'s `INLINE_TABLE` folds to `DisplayType.BLOCK` on Compose (`ComponentRenderer.kt:8173`, the `else` arm), while
`TableBoxTree.roleOf` calls it TABLE. That is a separate gap and not this family's.

## 6. Ownership (disjoint) and seam needs

**Owned by the lane:**
- `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt`: the re-land, plus `ANONYMOUS_ROLE` and
  `isAnonymous`.
- `runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableBodyForestTest.kt`: the re-land, plus pin m6.
- `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableCellHug.kt` (new) and
  `runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugTest.kt` (new).
- `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableApplier.kt`: the parameter, the local and the TableCell
  chain. The file is a size-rule exception, already 761 lines.
- `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`: the re-landed
  `composedCanvasRoots` call site.
  - This is a **shared harness file, not a seam**: coordinate it with any other wave-54 lane that touches the canvas.
  - The file already carries the +72 size exception from 0(d).
- `apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt`: the re-land.
- `tools/titan/results/wave54-<lane>/`: the lane note, the unit patch and the seam patch.

**Seam needs: one hunk.**
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`, the `TableApplier.Table(…)`
  call (`:2638–2697`).
- It is delivered as `seam-ComponentRenderer.kt.patch`.
- No hunk is needed in `ComponentRenderer.swift`, `apps/web-harness/src/sdui/ComponentRenderer.tsx` or
  `tools/titan/extract-fixture.mjs`.

**Not touched:** `runtimes/swiftui/**`, `apps/ios-harness/**`, `apps/web-harness/**`. B-ios and B-web shipped and are right.

## 7. Geometry probe design (`compose-table-body-cell.geometry.py`; gating, read instead of SSIM)

`python3 tools/titan/results/wave54-plan/compose-table-body-cell.geometry.py <run…>` checks three things:
1. The block x 24–43 × rows 56–75 is solid black, 400 of 400 px with RGB sum < 30.
2. There is no other ink (sum < 600) in x 0–389 × rows 52–140.
3. There is no red ink.

It prints one line per run and platform, ending in `→ GEOMETRY OK` or `GEOMETRY WRONG (<why>)`. The REF row is self-checked:
the script exits 1 if the reference does not print OK.

**The rule both passes and fails on recorded pictures** (`.geometry.out.txt`):
- The reference prints `square block solid 400/400 | stray ink 0 | red px 0 → GEOMETRY OK`.
- wave53-final web and ios print the same line.
- wave53-final android prints `square block solid 300/400 | stray ink 80 (x 24-43, rows 52-55) | red px 0 GEOMETRY WRONG (…)`.
- wave53-probe android prints `square block solid 76/400 | stray ink 400 (x 44-63, rows 56-75) | red px 0 GEOMETRY WRONG (…)`.

**The gating row**, at the lane's device probe and at the closing gate:

```
<run>  006 android  square block solid 400/400 | stray ink 0 | red px 0 → GEOMETRY OK
```

The wave-53 probe must agree. `display-table-body.geometry.py <run> 006`'s android row must print
`square rows 56-75 (20) x 24-43 | red px 0`.

**Why SSIM cannot gate this cell.** All three wrong pictures pass (`.replay.out.txt`, scored with ssim.js `fast`, as the scorer does):
- open/final, square 5 px high: 0.9944;
- probe, outline plus displaced square: 0.9906;
- the D1-only falsifier, square at x 44–63 with no outline: 0.9893.

## 8. Predictions

| cell | from → to | confidence | floor | gating |
|---|---|---|---|---|
| `CSS2/css21-errata/s-11-1-1b-006` android | P 0.9944 → **P ≈0.9983** (replay from both the final and the probe picture: 0.9983); geometry WRONG → **`→ GEOMETRY OK`** | **MED-HIGH** geometry / MED score | geometry OK **and** ssim ≥ 0.9960 | yes (revert rule: any other android line reverts the unit; the cell stays P 0.9944 DEGENERATE) |
| … ios | P 0.9992 → P 0.9992 (byte-identical) | HIGH | — | must not move |
| … web | P 1 → P 1 (byte-identical) | HIGH | — | must not move |

**Why MED-HIGH.**
- The traced arithmetic reproduces the probe picture to the pixel: the 76-px perimeter at x 24–43 and the 400-px spill at
  x 44–63.
- The table's own `widthAtMaxIntrinsic` was answered on device over the same subtree (table width 20 = 0 + 20), so the per-cell
  intrinsic reads the fix adds are known to answer and to answer 0 and 20.
- The rows are already right (56–75).
- **What remains unexecuted** is the per-cell `widthAtMaxIntrinsic` inside a `Row` on device. Compose UI has no JVM test
  infrastructure here, as the wave-53 lane note §7 records.
- The replay method predicted wave-53's iOS cell exactly (replay 0.9992 against device 0.9992, `wave53-canvas-root/s006.replay.out.txt`).
  The calibration lines here reproduce the gate's 0.9944 and 0.9906.

**Must not move.** The control-check allowed set is `{wpt__CSS2__css21-errata__s-11-1-1b-006 android}`; every other capture must
be byte-identical. Named watch list:
- `s-11-1-1b-001…005`, `-007…009` × 3.
- The 60 Compose-table documents on android, especially the 12 D1 and 8 D2 carriers in §5. They are the scoping proof: none may
  move.
- `css-position/position-absolute-dynamic-static-position-table-cell` × 3.
- `css-tables/baseline-vertical` × 3.
- `css-tables/fixup-dynamic-anonymous-*` × 3, which are T10's carriers.

## 9. Verification plan

**JVM pins** (JDK 21, after the opening gate frees the host):
- **`TableBodyForestTest`:** the six re-landed pins (shape, m1–m5), plus
  **m6** `syntheticBoxesAreAnonymous`.
  - It asserts that `#table`, `#row` and `#cell0` are anonymous, and that 2-143, 1-142, the body-root and 3-144 are not.
  - Mutation: drop the role in `synthetic` → red.
- **`TableCellHugTest`:** `chrome` on four payloads, taken verbatim from wave53-final per-test IR.

  | payload | expected (stroke, hug) |
  |---|---|
  | the synthetic `#table` of 006 | (false, true) |
  | 006's empty declared-TABLE body-root | (true, false) |
  | `percentage-sizing-of-table-cell-children-003`'s declared table | (true, false) |
  | `baseline-empty-cell-001`'s UA `<table>`, passing today's expression for `fabricatedDefault` | (false, false) |

  Mutations: drop `!isAnonymous` → the stroke pin goes red; hard-wire `hug = false` → the hug pin goes red.
- **Source pin, in the same class.**
  - It asserts that `ComponentRenderer.kt`'s `TableApplier.Table(` call passes `fabricatedCellBorder = chrome.stroke` and
    `cellsHugContent = chrome.hug`.
  - It also asserts that `TableApplier.TableCell` chains `hugColumn()` before `fillMaxHeight()`.
  - Precedent: wave-53 a7's source pin that `canvasModifier` spends its plan.
  - Mutation: revert either argument → red.
- **`ComposedCanvasTableBodyTest`:** the re-landed stack pin (0 gap above `#table`).
- **Focused suites:**
  - `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*TableBodyForest*' --tests '*TableCellHug*' --tests '*TableBoxTree*' :app:testDebugUnitTest --tests '*ComposedCanvas*' --tests '*UaBlockMargins*')`.
  - The full compose suite at the sweep.

**Device probe of the lane tree before the closing gate.** This follows the 0(a) rule; the wave-53 L1 U2 and B-android
lessons motivate it.
- Run Android only, on sections **CSS2** (the gating line) and **css-tables** (32 of the 60 Compose-table documents; it must be
  48/48 byte-identical).
- Read the result with this brief's geometry probe and with `tools/titan/results/wave53-gate/control-check.mjs` over the
  one-capture carrier set.

**Closing gate.**
- The geometry row above is gating.
- Control-check with the one-capture allowed set.
- Probe sections: CSS2, css-tables, css-position, css-break, css-contain, css-display, css-values, css-writing-modes, selectors,
  css-text and css-backgrounds. Together they hold every Compose-table carrier.

## 10. Risks

- **Zero cell gain.** The only movement is 006 android from DEGENERATE to faithful. A wrong device picture is caught by the
  geometry row and reverts the unit. The cell stays P either way, because all three wrong pictures score at least 0.9893.
- **The intrinsic channel could refuse.** If a `SubcomposeLayout` sits in the cell subtree, the cell keeps the fill and the
  probe picture comes back. The refusal is logged, never silent. This is LOW: the same subtree answered the table's own read
  on device.
- **A new runtime-internal role value.** Only the seam's `chrome` reads it, and it never reaches the wire. A future T10 fixup
  in `TableBoxTree` that synthesizes anonymous boxes should reuse the same marker to get the same chrome.
- **The general shapes stay in the corpus.** That is 12 D1 documents (one is a visible 396-px rim on a passing cell) and 8 D2
  documents. Queue them as "Compose table chrome: demo stroke on declared tables + first-cell block fill (CSS 2.1 §17.6.1 /
  §17.5.2.2)", with this census as its starting radius. Do not widen this unit.
- **Hot shared files.** Other canvas lanes may touch `ScreenshotCaptureScreen.kt`. `ComponentRenderer.kt` (8446 lines) is the
  Compose seam.
- **The evidence is wave53-final.** Once `wave54-open` is scored, re-read 006 ×3 and the watch list on it. 0 movers are
  expected.

## 11. Recommendation: **GO-SMALL**

Land one revert-unit commit:
- 276757ea re-applied;
- the anonymous marker;
- `TableCellHug.kt`;
- the 8-line applier change;
- one seam-patch hunk.

The control set is one capture. It is gated on the `→ GEOMETRY OK` android row after a CSS2 + css-tables Android device probe
of the lane tree.

It is not a wall. The mechanism is traced to two named Compose behaviours, and both are corroborated on shipped captures. The
fix is local to boxes that only the 006 forest creates.

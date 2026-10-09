# wave54 L2 · table-body-cell — lane note

Builder lane L2 of wave 54 (PLAN.md §2 "### L2 · table-body-cell", brief `wave54-plan/compose-table-body-cell.md`).
One revert unit, **TB-android**: the wave-53 L3 B-android forest (276757ea, reverted by d773ff6a at the wave-53 probe)
re-landed unchanged, plus the two narrow pieces the brief traced to the pixel — no demo stroke on the anonymous table
(D1) and max-content cells in it (D2) — keyed on ONE in-memory marker only the 006 forest creates.

Base: shared tree `campaign/applier-campaign` HEAD db6e8aa0 (= dev 7cce3b22 + the committed wave-54 plan). Nothing
committed, stashed or checked out by this lane. The seam file was edited only under the lock and restored byte-exact.

## 1. What changed, and why (file:symbol)

- **Re-land of 276757ea** (`git show 276757ea | git apply`, `--check` clean on the tree): `table/TableBodyForest.kt`
  (`TableBodyForest.rewrite`, CSS 2.1 §17.2.1 rule 2 + §8.3), `TableBodyForestTest.kt` (shape, m1–m5),
  `ScreenshotCaptureScreen.kt` `composedCanvasRoots` call site (item B), `ComposedCanvasTableBodyTest.kt` (stack pin).
  Verified: the ScreenshotCaptureScreen.kt diff equals 276757ea's hunk line for line; ComposedCanvasTableBodyTest.kt is
  byte-identical to 276757ea's. The re-landed pins' payloads (wave52-ship) are byte-identical to wave53-final and
  wave54-open (006 per-test IR sha256 `8d15388d34a24285…` in all three; 005 `0da87f25…`, 001 `17474f9b…`, a98rgb-003
  `631531a1…` wave52-ship = wave53-final).
- **(a) the marker** — `TableBodyForest.ANONYMOUS_ROLE = "anonymous-table"`, `TableBodyForest.isAnonymous(c)`, and
  `synthetic(…)` passes `role = ANONYMOUS_ROLE` to `#table`, `#row`, `#cell<k>`. Safe: every `role` reader in
  `runtimes/compose/src/main` + `apps/android-harness/app/src/main` compares against `"body-root"` (14×) or
  `"line-break"` (4×) only (grep, §5), so the new value reads exactly as `null` did; the wire never carries it (§5: 0 of
  10 713 components; the wire's roles are null / body-root / line-break / ws-after). Swift twin untouched (`meta: nil`),
  asymmetry recorded in the Kotlin header.
- **(b) NEW `table/TableCellHug.kt`** (111 lines, imports commented): `TableCellHug.LocalTableCellsHugContent` (default false),
  `TableCellHug.Chrome(stroke, hug)`, the pure `TableCellHug.chrome(component, fabricatedDefault) =
  Chrome(stroke = fabricatedDefault && !isAnonymous, hug = isAnonymous)`, and the top-level `Modifier.hugColumn(enabled)`
  = `IntrinsicChannel.widthAtMaxIntrinsic("TableCellHug", CELL_HUG_REFUSAL)` when enabled, **the receiver itself**
  otherwise (pinned with `assertSame`, so a non-hugging cell's chain is node-for-node the frozen one). Refusal logged once:
  "CSS 2.1 §17.5.2 column max-content skipped — … the cell keeps the fill." (no silent fallthrough).
  - Deviation from the brief's wording, tree wins: the brief sketched `Modifier.hugColumn()` reading the local itself; it
    takes the Boolean instead (TableCell reads `LocalTableCellsHugContent.current`), so it is pure and JVM-pinnable and is
    not a @Composable modifier factory.
- **(c) `table/TableApplier.kt`** (+12 / −1, all inside the size-rule exception's "parameter, provider, chain"):
  `Table(…, cellsHugContent: Boolean = false, …)` + kdoc; `TableCellHug.LocalTableCellsHugContent provides cellsHugContent`
  in Table's provider (per table, so a nested table resets it); `TableCell` chains
  `.hugColumn(TableCellHug.LocalTableCellsHugContent.current)` between `.then(cellModifier)` and `.fillMaxHeight()`.
- **(d) seam-1.patch** (never applied in the tree; §6): at the `DisplayType.TABLE ->` arm,
  `val chrome = com.styleconverter.runtime.table.TableCellHug.chrome(component, fabricatedDefault = <the wave-38
  expression, moved verbatim>)`, then `fabricatedCellBorder = chrome.stroke` and `cellsHugContent = chrome.hug`.

**Result on 006 (traced, not executed on device).** The anonymous cell's max-content is 0 (its run is the empty 1-142) and
the td's is 20 (exactWidth reports 20), the same sum the table's own `widthAtMaxIntrinsic` answered on device at wave53-probe
(table width 20). So `#cell0` hugs to 0, the td's cell hugs to 20 (`measureSpec(20, 0, 20)` fits), the td paints at x 24-43,
rows 56-75 (rows already right at the probe), and no cell of the anonymous table is stroked. Spec: CSS 2.1 §17.2.1, §17.6.1,
§17.5.2.2, §8.3.

**Tree wins over the brief — the source pin is split.** PLAN §2 puts the ComponentRenderer source pin "in the same class"
(TableCellHugTest). That pin is red by construction until the seam lands, which rule 2b forbids on the shared tree. So:
TableCellHugTest pins TableApplier's half (in the tree, green now); the renderer's half is the NEW
`TableCellHugSeamWiringTest.kt`, **patch-borne inside seam-1.patch** (the wave-53 FloatAvoidSeamWiringTest precedent), and
enters the tree only with the seam.

## 2. Revert unit TB-android — the exact paths of its commit

Owned files (working tree), plus the seam patch applied right before the commit (PLAN §4 step 3.3):
- `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableBodyForest.kt` (new)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableBodyForestTest.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableCellHug.kt` (new)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugTest.kt` (new)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/table/TableApplier.kt` (modified)
- `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt` (modified — the
  276757ea hunk ONLY; L4 does not edit this file, PLAN §4)
- `apps/android-harness/app/src/test/java/com/styleconverter/test/screenshot/ComposedCanvasTableBodyTest.kt` (new)
- from `tools/titan/results/wave54-table-body-cell/seam-1.patch`:
  `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` (the TABLE-arm hunk) and
  `runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugSeamWiringTest.kt` (new, patch-borne)
- `tools/titan/results/wave54-table-body-cell/` (this directory)

The unit stands alone: it compiles without any other lane's edit (its only cross-file reads are TableBoxTree,
IntrinsicChannel, CanvasRootHoist and the IR decoder, all at HEAD API). `revertOrder`: [TB-android]. Captures:
android `wpt__CSS2__css21-errata__s-11-1-1b-006` only; wire: none.

## 3. Blast-radius census (the lane's carrier set) — method and result

Two independent censuses, both mine (the brief's `compose-table-body-cell.census.mjs` was not re-used):

1. **Wire-level** (`census.py` → `census.out.txt`, wave53-final and wave54-open identical): 1435 per-test IR documents;
   `meta.role` values on the wire = null 5692 · body-root 284 · line-break 371 · ws-after 4366; **`anonymous-table` 0**;
   forest trigger (body-root whose last `Display` is TABLE/INLINE_TABLE) **1 document: `CSS2/css21-errata/s-11-1-1b-006`**;
   78 documents carry a declared-TABLE box or a UA `<table>` (the upper bound of the seam's `chrome` call radius — every one
   gets `(today's stroke, hug=false)` unless it holds a forest box).
2. **Executed** (`census-kotlin.py` + `census-kotlin.method.kt.txt` → `census-kotlin.<run>.out.txt`): the REAL Kotlin chain
   the composable runs (`SlotComposer.compose` → `withCanvasOwnedBodyMargin` / `withUaBlockMarginOnHoistedRoot` →
   `composedCanvasRoots`), over all 1435 documents, counting forest non-identity, documents holding an anonymous box, and
   boxes on which `TableCellHug.chrome(c, x) != Chrome(x, false)` for x ∈ {true, false}; it also asserts, per document,
   that the composed chain equals "item A, then the forest" (no other stage moved). **Result, identical on wave53-final and
   wave54-open:** `documents 1435 · decode errors 0 · boxes walked 10716 · forest non-identity 1 · documents with an
   anonymous box 1 · documents where chrome differs from (fabricatedDefault, hug=false) 1` — the one document is
   `s-11-1-1b-006`, its boxes `…0-141#table`, `#row`, `#cell0` (10 716 = the wire's 10 713 + these 3). The method is
   appended to the lane-owned ComposedCanvasTableBodyTest.kt only for the run and the file is restored byte-exact
   (`d42ce7891e20c7e7…` before and after, `census-kotlin.out.txt`). History, said plainly: the first attempt hit L3's
   in-flight compile error (rule 3 wait); the retry then ran for ~10 min because `File.walk()` followed each section's
   symlinked `web/node_modules`; I stopped my own census processes (SIGINT, so its finally-block restored the test file —
   verified sha) and re-ran it listing `<section>/per-test-ir/` directly.

**Carrier set (control-check.mjs):** web ∅ · iOS ∅ · android `{wpt__CSS2__css21-errata__s-11-1-1b-006}` · wire ∅ —
equal to `expectations.json` lanes["L2-table-body-cell"] captureCarriers / wireCarriers / revertUnits.TB-android
(cross-checked: 1 android capture, 0 web, 0 iOS, 0 wire, one unit). Passing today: the one carrier passes (P 0.9944,
DEGENERATE). Why nothing else can move: rewrite returns the SAME list instance unless triggered; chrome returns
`fabricatedDefault` unchanged and `hug=false` for every non-anonymous box; `hugColumn(false)` returns its receiver; the extra
`provides` supplies the local's own default value. No web/iOS/converter/extractor file is touched.

## 4. Pins and executed mutations (red → restore byte-exact → green)

All on VERBATIM wave53-final per-test IR (byte-identical in wave54-open; PLAN §10 item 1). TableCellHugTest's payloads are
spliced by `gen-pins.py` from the run files (each component asserted to be an exact substring of its file).

Focused suite (PLAN §2 "may run"): `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests
'*TableBodyForest*' --tests '*TableCellHug*' --tests '*TableBoxTree*' :app:testDebugUnitTest --tests '*ComposedCanvas*'
--tests '*UaBlockMargins*')`.

| pin (class::test) | mutation (file) | result |
|---|---|---|
| TableBodyForestTest::shape | BS re-parent the run under the body-root (TableBodyForest.kt) | red (+ m2–m6, the hug row: they index the table) |
| ::m1 identity on non-table bodies | BM1 trigger dropped | red |
| ::m2 cell loses margins | BM2 §8.3 strip skipped | red |
| ::m3 abspos stays a root | BM3 out-of-flow filter dropped | red (+ shape) |
| ::m4 non-cell run wrapped | BM4 div straight into the row | red (+ shape, m6) |
| ::m5 BorderSpacing | BM5 not copied | red |
| ::m6 synthetic boxes anonymous (NEW) | m6 `role = ANONYMOUS_ROLE` dropped | red (+ TableCellHug anonymous row, fold pin) |
| ComposedCanvasTableBodyTest::stack | BK `return owned` (ScreenshotCaptureScreen.kt) | red |
| TableCellHugTest::anonymousTable (false, true) | T1 drop `!anonymous` (TableCellHug.kt) | red |
| ″ | T2 `hug = false` | red |
| ::declaredBodyRoot / pct003 / bec001 / cc004 (stroke, false) | T3 `hug = true` for every table | 4 red |
| ::hugColumn(false) is the receiver | T4 `if (false) this` | red |
| ::applierSource (param, provider, chain order) | T5 drop `.hugColumn(…)` (TableApplier.kt) | red |
| ″ | T6 drop the provider | red |
| ::anonymousMarker survives the pre-display folds (NEW: ContentsUnboxing → PseudoTextFold → PseudoBoxFold → copy, the rebinding RenderComponent does before its display switch) | m6 / T1 / T2 | red (each; `mutations.rerun.out.txt`) |
| TableCellHugSeamWiringTest (patch-borne, 2 tests) | S0 renderer at HEAD bytes; S1 stroke back to the inline expression; S2 `cellsHugContent` dropped (ComponentRenderer.kt, under the lock) | S0 2 red; S1 1 red; S2 1 red |

Executed records: `mutations.out.txt` (first batch BS…T6: every one `RED-OK`, every file restored byte-exact,
GREEN-AFTER-RESTORE rc 0, 109 tests, 0 red) and `mutations.rerun.out.txt` (after adding the fold pin and commenting
TableCellHug.kt's imports: m6, T1–T4 re-executed, all `RED-OK`, GREEN-AFTER-RESTORE rc 0, **110 tests, 0 red**);
`mutations.json` holds both batches (the later entries supersede the first m6/T1–T4). Seam: `seam1-verify.out.txt` /
`.json` (final run) and `seam1-verify.run1.*` (the first, on the pre-fold-pin tree).
Final sha256 of the owned files (= the last mutated-run baselines, each restored byte-exact): TableBodyForest.kt
`d80054ce72a079d3…`, TableCellHug.kt `fac6c37431056468…`, TableApplier.kt `0d58a4633762893c…`, ScreenshotCaptureScreen.kt
`b98e80ee33db68fd…`, TableBodyForestTest.kt `e5ff3022bbe71c06…`, TableCellHugTest.kt `1afa7686e022b50a…` (re-generated by
`gen-pins.py` to the same bytes), ComposedCanvasTableBodyTest.kt `d42ce7891e20c7e7…` (= 276757ea's).

**Seam verified with the patch applied, under the lock** (`seam1-verify.py`): `mkdir tools/titan/runs/wave54-lock/ComponentRenderer.kt`
→ sha256 before `da2df0b4cca689ad30943213a5e10253881073b7287c45eabf42cb743e1d04db` (= HEAD = 7cce3b22) → `git apply seam-1.patch`
(patched `eaf9111e77596e0c…`) → GREEN rc 0, 11 classes, **112 tests** (TableBoxTree 11, TableBodyForest 7, TableCellHug 8,
TableCellHugSeamWiring 2, UaBlockMargins 49, ComposedCanvas* 35) → S1 / S2 / S0 red as above → restored from `git show
HEAD:` → sha256 after `da2df0b4…` byte-exact, patch-borne test deleted → `rmdir` the lock. Run three times in all: the
first died on a glob bug in my script before any suite ran (its finally-block restored the same bytes and released the
lock); the second (111 tests, `seam1-verify.run1.*`) and the third (final tree, 112 tests) both GREEN with S0–S2 red.
`git apply --check` clean on the tree and on the HEAD index (`--cached`); it also composes with L5's
`wave54-ua-heading-face/seam-1.patch` applied after it (scratch copy).

## 5. Geometry gate and the PNGs (looked at)

`geometry.out.txt`: `geometry-gate.py wave53-final --base wave53-open --lanes L2 --self-test` → gating 2 FAIL · control 4
PASS · report 0 · **self-test HOLDS, exit 0**; the same on wave54-open. Direct probe lines: wave53-final android
`solid 300/400 | stray ink 80 (x 24-43, rows 52-55) … GEOMETRY WRONG`; wave53-probe android `solid 76/400 | stray ink 400
(x 44-63, rows 56-75) … GEOMETRY WRONG`; `display-table-body.geometry.py` android rows 51-70 (final) / x 24-63 (probe); web /
ios / ref OK on every run.

PNGs opened (ref, wave53-final android/ios, wave53-probe android, cropped 3×): ref = one solid square at x 24-43 rows 56-75
under the text; final android = the square 5 px high, touching the text (DEGENERATE pass); probe android = the 1-px outlined
EMPTY box where the square belongs and the solid square displaced right — the brief's corrected reading holds; final iOS
right. Corroboration opened: percentage-sizing-003 android has the black rim the ref lacks (D1); baseline-empty-cell-001
android draws one wide cell where the ref has two (D2) — both stay as they are (must-not-move; general fix queued).

Score replay re-run (`node wave54-plan/compose-table-body-cell.replay.mjs` → `replay.rerun.out.txt`, byte-identical to the
brief's): calibration 0.9944 / 0.9906; FIX 0.9983 from both pictures; D1-only falsifier 0.9893. **A replay is a painted
picture, not a device capture: nothing here is labelled picture-correct.**

## 6. Predictions (per target cell, with confidence)

| cell | from → to | confidence | gate |
|---|---|---|---|
| `CSS2/css21-errata/s-11-1-1b-006` android | P 0.9944 (DEGENERATE) → **P ≈0.9983**; `→ GEOMETRY OK` (square block solid 400/400, stray ink 0, red 0) and `square rows 56-75 (20) x 24-43 \| red px 0` | MED-HIGH geometry / MED score | floor 0.996 + both android geometry lines (gating, stage 1) |
| … ios / web | P 0.9992 / P 1 → identical | HIGH (no file on those platforms touched) | must not move |

Flips: 0. The value is DEGENERATE → faithful, claimed ONLY if the device picture prints both geometry lines.

## 7. Must-not-move cells

The 97 lines of `expectations.json` lanes["L2-table-body-cell"].mustNotMove (67 android, 15 iOS, 15 web; today P 70 · f 27;
97/97 identical wave53-final → wave54-open): 006 web/ios; `s-11-1-1b-001…005, -007…009` ×3; the other Compose-table
documents on android incl. the 12 D1 and 8 D2 documents (minus `css-contain/contain-content-004` android, L4's carrier,
PLAN §4); `position-absolute-dynamic-static-position-table-cell` ×3, `baseline-vertical` ×3, `fixup-dynamic-anonymous-*` ×3.
Stage-1 control: css-tables android 48/48 identical; CSS2 android changed exactly 1 (006).

## 8. Hand-offs

- **Seam patch:** `tools/titan/results/wave54-table-body-cell/seam-1.patch` (sha256 `0e37f79d9b979da8…`), PLAN §3 row
  ":2653-2697", unit TB-android; hunks: ComponentRenderer.kt TABLE arm + NEW TableCellHugSeamWiringTest.kt. Re-cut source:
  `seam1-hunk.py` (post-image from base bytes) + `seam1-files/`.
- **Unit patch (record / re-cut aid):** `unit-TB-android.patch` (sha256 `0cb6f4baab9a6089…`) = the seven owned files vs HEAD;
  `git apply --check --cached` clean on HEAD and `-R --check` clean on the tree. The orchestrator commits the files
  themselves (§2 list).
- **Hunks for other lanes:** none.
- **Shared-module note:** the green runs above compiled with other lanes' in-flight Compose edits present (L4's
  CanvasRootHoist.kt among them, which my forest and stack pin read through `shouldHoistToCanvasRoot` /
  `rendersInFlowAsStaticPosition` / `hostActivates`); a later L4 change to those predicates is re-checked by the sweep.

## ORCHESTRATOR WINDOW REQUESTS

None beyond the pre-registered stage 1 (PLAN §2 L2 "Window requests: none"). For the stage-1 read of TB-android, verbatim:
1. `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-pre --base wave54-open --lanes L1,L2` — expected L2 rows:
   `006 android … PASS` on both probes (compose-table-body-cell: `square block solid 400/400 | stray ink 0 | red px 0 →
   GEOMETRY OK`; display-table-body: `006 android  square rows 56-75 (20) x 24-43 | red px 0`), 006 web/ios PASS.
2. `node tools/titan/results/wave52-gate/cells.mjs 's-11-1-1b-006' wave54-open wave54-pre` — expected android P ≥ 0.996
   (replay 0.9983), ios P 0.9992 and web P 1 unchanged.
3. `node tools/titan/results/wave54-gate/control-check.mjs wave54-open wave54-pre --sections CSS2,css-counter-styles,css-tables,css-text`
   — expected L2: css-tables android 48/48 identical; CSS2 android changed only 006 (plus other lanes' registered carriers).
4. OPEN `tools/titan/runs/wave54-pre/sections/CSS2/android-screenshots/wpt__CSS2__css21-errata__s-11-1-1b-006.png` next to
   the ref.
Decision (stage1Decision): TB-android stays only if lines 1 AND 2 hold; otherwise revert it (the cell stays P 0.9944
DEGENERATE). A WRONG line with the picture showing an outline again (stroke) points at the seam's `chrome.stroke` not
reaching the call (role lost before the TABLE arm); a square still at x 44-63 points at the per-cell intrinsic refusing
(look for the "TableCellHug" refusal in logcat).

## 9. What I could NOT verify

- The device picture. Compose UI does not execute on the JVM here: the per-cell `widthAtMaxIntrinsic` inside a `Row`, its
  interplay with the row's `heightAtMinIntrinsic`, and the role surviving `RenderComponent` up to the TABLE arm are traced
  by reading (and the folds' role-preservation pinned on the JVM), not executed. Stage 1 is the evidence.
- The closing gate / probes / control-check (orchestrator windows).
- The full Compose / harness suites (single-writer sweep).
- Whether a later edit by L4 to `CanvasRootHoist` changes 3-144's out-of-flow classification (my m3 / stack pins would go
  red if it did).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf

STATUS: COMPLETE

## S1 fix pass (wave-54 S1 fix lane table-body-cell, 2026-10-09; tree 4f3853d7, uncommitted — the orchestrator commits)

Scope: S1 should-fix **S5** ("L2 D2 quantity unpinned") and the L2 skeptic's nit 2 (the vacuous `.then(cellModifier)`
order assertion), from `tools/titan/results/wave54-S1/_note.md`. Owned files: `TableCellHug.kt` (unchanged) and
`TableCellHugTest.kt` (changed).

### What changed (`runtimes/compose/src/test/java/com/styleconverter/runtime/table/TableCellHugTest.kt`, 186 → 200 lines)

| item | change |
|---|---|
| S5 | new pin `hugColumnSource_enabledBranchReadsMaxContent_neverMin`. It takes the `fun Modifier.hugColumn(` body from `TableCellHug.kt`, bounded at the next `fun ` or EOF, so the KDoc that names `widthAtMaxIntrinsic` cannot answer. It then checks three things. (a) The enabled branch (after `else with(IntrinsicChannel) {`) calls `this@hugColumn.widthAtMaxIntrinsic(`. (b) `refusalContext = TableCellHug.CELL_HUG_REFUSAL` follows that call. (c) The function names neither `widthAtMinIntrinsic` nor `IntrinsicSize.Min`. The quantity is CSS 2.1 §17.5.2.2: in auto layout with room available, each column gets its MAX-content width |
| nit 2 | `applierSource_…` now bounds `cell` at the next `fun ` (that is `TableHeaderCell`), so a later composable's `.fillMaxHeight()` (`TableApplier.kt:666`) can no longer satisfy `hug < fill`. It also asserts that all three links are present (`minOf(then, hug, fill) >= 0`) before checking the order `then < hug < fill`. Before the fix, an absent `.then(cellModifier)` gave `-1 < hug`, which passed vacuously |
| helpers | the `applier` lazy val is replaced by `tableSource(name)`, using the same walk-up to the repo root, and by `funBody(src, start)`. `funBody` asserts that `start` is present, so a renamed function goes red instead of crashing on `substring(-1)` |
| header | the EXECUTED MUTATIONS list gains the S1-fix line (X3, X6 / X7) |

sha256:
- `TableCellHugTest.kt`: `1afa7686e022b50a…` → `ea5e7a9448b5a298…`.
- `TableCellHug.kt`: `fac6c37431056468…`, unchanged.
- `TableApplier.kt`: `0d58a4633762893c…`, unchanged. It was mutated only transiently and restored byte-exact.

### Executed mutations

Command: `python3 tools/titan/results/wave54-table-body-cell/s1fix-mutate.py <phase> <ids…>`. The script makes one exact-substring edit per mutation and asserts that the edit is unique. It runs `./gradlew :runtime:testDebugUnitTest --rerun --tests …TableCellHugTest --tests …TableBodyForestTest` with JDK 21, after deleting only those two classes' XML, so every count is fresh XML. It restores the original bytes in a `finally` block and re-hashes them. Full output: `s1fix-mutations.out.txt`.

| id | mutation | BEFORE (pins as landed, test sha `1afa7686…`) | AFTER (test sha `ea5e7a94…`) | restored |
|---|---|---|---|---|
| X3 (S1's survivor = L2 skeptic X3) | `TableCellHug.kt` `this@hugColumn.widthAtMaxIntrinsic(` → `…widthAtMinIntrinsic(` | **survived** (rc 0, 15 tests, 0 red) | **RED**: `hugColumnSource_…` — "the enabled hug does not read max-content" | `fac6c374…` → `fac6c374…` byte-exact |
| X6 (L2 skeptic X6, nit 2) | `TableApplier.kt` drops `.then(cellModifier)` | **survived** (rc 0, 15 / 0) | **RED**: `applierSource_…` — "TableCell lacks a link: then=-1 hug=2582 fill=2657" | `0d58a463…` byte-exact |
| X7 (new: the bound) | `TableApplier.kt` drops TableCell's `.fillMaxHeight()` | **survived** (rc 0, 15 / 0). The unbounded `cell` found `TableApplier.kt:666` | **RED**: `applierSource_…` — "TableCell lacks a link: then=2431 hug=2618 fill=-1" | `0d58a463…` byte-exact |
| X8 (new: clause c alone) | keeps the max read and chains `.widthAtMinIntrinsic(…)` after it | — | **RED**: "hugColumn reads min-content" | `fac6c374…` byte-exact |
| X9 (new: clause b alone) | `refusalContext = TableCellHug.CELL_HUG_REFUSAL,` → `refusalContext = "",` | — | **RED**: "the read is not tagged CELL_HUG_REFUSAL" | `fac6c374…` byte-exact |

Each mutation turned exactly one test red, the intended one. No mutation compiled with errors (`errs []` on every row).

### Focused suites (executed)

Classes: `TableCellHugTest` and `TableBodyForestTest`.

| run | result | output |
|---|---|---|
| before the fix | GREEN, rc 0, 15 tests, 0 red | `== before` block |
| after the fix | GREEN, rc 0, **16** tests, 0 red | `after-final` and `clauses` GREEN rows |

The 16 are 15 + the new pin, and the last GREEN came after every restore.

### Not done / limits

- **Source pins only.** They pin which call the code makes, not the pixels. The Compose layout (the intrinsic inside the
  `Row`) still does not execute on the JVM. Per CSS, 006's two cells have equal min- and max-content: an empty
  anonymous cell, and a td with `width: 20px`. By that reading, which is NOT executed, the device gate would not tell
  X3 apart on this corpus document either. That is why the pin is a source pin.
- I did not run `TableCellHugSeamWiringTest` or the full Compose suite (single-writer rule). The orchestrator's sweep is
  the count of record.

S1-FIX STATUS: COMPLETE

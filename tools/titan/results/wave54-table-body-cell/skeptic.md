# wave54 L2 · table-body-cell — SKEPTIC report

Adversarial skeptic of builder lane L2 (unit **TB-android**). Shared tree
`/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf`, branch
`campaign/applier-campaign`, HEAD `db6e8aa0`. Everything below was EXECUTED. Nothing was committed, stashed or checked out.
The lane's owned files were mutated only inside my mutation driver and restored byte-exact (sha256 logged). The seam file
was touched only under the lock and is back at HEAD bytes. No lock dir is left behind.

## Verdict: CLAIM-HOLDS (no must-fix)

The lane's claims survive every executed check:

- **The re-land is 276757ea.** `TableBodyForest.kt` differs from 276757ea's only by the marker and its comments. The
  `ScreenshotCaptureScreen.kt` +/− lines equal 276757ea's hunk. `ComposedCanvasTableBodyTest.kt` is byte-identical to
  276757ea's.
- **Pin payloads are verbatim.** Every pin payload equals the wave53-final per-test IR, as whole documents.
- **Every pin mutation goes red and restores green.** All 14 mutations the lane claims go red. Each restore is
  byte-exact, and the suite is green (110 tests) before and after.
- **The seam pins work with the patch applied.** S0–S2 go red under the lock, and the renderer is restored to `da2df0b4…`.
- **The unit stands alone on HEAD,** with and without its seam patch (rule 2b).
- **The carrier set is right.** My own census gives the same set as `expectations.json`: android
  {`s-11-1-1b-006`}, web/iOS/wire ∅.
- **The geometry gate is real.** Its self-test exits 0, and the gating probe rejects every plausible wrong device picture
  I fed it.

What is left is weak pinning around the D2 mechanism and some hygiene: one should-fix and five nits.

## Repros (outputs in this directory)

| # | what | command / script | result |
|---|---|---|---|
| R1 | Re-land fidelity | `git show 276757ea:<f> \| diff/cmp` on the 3 re-landed files and the SCS hunk | Forest: only the marker, `isAnonymous` and comments differ. `ComposedCanvasTableBodyTest.kt` IDENTICAL. SCS +/− lines IDENTICAL |
| R2 | Payloads verbatim | inline python: json-equality and substring test of every `{"id":…}` payload line against `tools/titan/runs/wave53-final/sections/*/per-test-ir/` | TableCellHugTest 23/23, TableBodyForestTest 16/16, ComposedCanvasTableBodyTest 4/4 are equal AND substrings, whole documents (006 4/4, pct003 5/5, bec001 4/4, cc004 10/10, 005 2/2, 001 6/6, a98rgb-003 4/4). No `$` in any raw string |
| R3 | Lane mutations replayed with MY driver | `skeptic-mutate.py` → `skeptic-mutations.out.txt` / `.json` | GREEN0 rc 0, 110 tests. BS, BM1–BM5, BK, m6, T1–T6: every one red on the expected tests, every file restored byte-exact (Forest `d80054ce…`, Hug `fac6c374…`, Applier `0d58a463…`, SCS `b98e80ee…`). GREEN1 rc 0, 110 tests |
| R4 | Extra mutations (mine) | same driver | X1 (hug after fillMaxHeight) **red**. X2 (`provides true`) **red**. X4 (enabled hug a no-op) **red**. X5 (null role reads anonymous) **red** ×4. **X3 (`widthAtMinIntrinsic` instead of Max) SURVIVES**. **X6 (`.then(cellModifier)` dropped) SURVIVES** |
| R5 | Seam replay under the lock | `skeptic-seam.py` → `skeptic-seam.out.txt` / `.json` | Lock taken. Renderer sha `da2df0b4…` = HEAD. `git apply` → `eaf9111e…`. GREEN 28 runtime tests incl. TableCellHugSeamWiringTest 2. S1 red, S2 red, S3 (negation dropped) red, S4 (`cellsHugContent = chrome.stroke`) red, **S5 (`chrome(component.copy(role = null), …)`) SURVIVES**, S0 (HEAD renderer) 2 red. Restored `da2df0b4…` byte-exact, patch-borne test removed, lock released |
| R6 | Seam applies and composes | `git apply --check` (tree, `--cached`). Scratch git repo at 7cce3b22's renderer: apply L2 seam-1, then L5 seam-1, then L3 seam-1 (`--include` the .kt) | All clean (L4 seam-1 not written yet) |
| R7 | Unit stands alone (rule 2b, revert-unit purity) | EXPORT tree = `git archive HEAD` (apps/android-harness, runtimes/compose, schema, fixtures) + ONLY the 7 TB-android files: E1 without the seam, E2 with `seam-1.patch` | E1: rc 0, **110 tests green**. E2: rc 0, **112 tests green** (+TableCellHugSeamWiring 2). The green does not depend on any other lane's in-flight edit (e.g. L4's CanvasRootHoist.kt) |
| R8 | Marker reaches the TABLE arm along the REAL path | `skeptic-chain-test.kt.txt`, run in the export tree. The path: forest → `ComponentHost.Render`'s `BackgroundColorInheritance.resolve` (NOT in the lane's fold pin) → ContentsUnboxing → PseudoTextFold → PseudoBoxFold → `copy` | GREEN. `chrome` = (false, true). `rowsOf` gives 1 row `[#cell0, 2-143]`. #cell0 holds 1-142. The td has no Margin*. `roleOf` = TABLE |
| R9 | Own census | `skeptic-census.py wave53-final wave54-open` → `skeptic-census.out.txt` | 1435 docs, corpus sha `9cf2f2cf…` on both runs. Wire roles {ws-after 4366, null 5692, line-break 371, body-root 284}. `anonymous-table` on wire: **0**. Forest trigger: **1** (006: TABLE, no own edges, after = [1-142, 2-143 TABLE_CELL, 3-144]). Generic table display on a body: 0. Equals expectations captureCarriers / wireCarriers / revertUnits.TB-android, and the lane's two censuses (wire + executed Kotlin) |
| R10 | Role readers | grep `.role` over `runtimes/compose/src/main` + `apps/android-harness/app/src/main` | Every comparison is against "body-root" or "line-break". Other readers pass `role` through to the same comparisons (PseudoGeneratedBox, PseudoBucketExtractor) or copy it (ContentsUnboxing, BackgroundColorInheritance). Nothing serializes IR into a capture path |
| R11 | Scores | `cells.mjs 's-11-1-1b-006' wave53-open wave53-probe wave53-final wave54-open` and the corroboration cells | android P 0.9944 / 0.9906 / 0.9944 / 0.9944. ios 0.9953 / 0.9992 / 0.9992 / 0.9992. web 0.9941 / 1 / 1 / 1. pct-003 android P 0.9954, bec-001 android P 0.9956: all as claimed |
| R12 | Must-not-move | `cells.mjs` over the 97 `mustNotMove` cells, wave53-final vs wave54-open | 97/97 found. **P 70 · f 27** (as claimed). 0 moved |
| R13 | Geometry gate | `geometry-gate.py wave53-final --base wave53-open --lanes L2 --self-test` (and `wave54-open --base wave53-final`) | Gating 2 FAIL, control 4 PASS, report 0, **self-test HOLDS, exit 0** (both) |
| R14 | Can the gating probe fail? | `skeptic-geometry-fakes.py` → `skeptic-geometry-fakes.out.txt` (fakes built from the real wave53-probe capture, fed to the probe's own `verdict()`) | fix → OK. A light-grey AA edge → OK. D1-only (square x44-63) → WRONG. Hug-OK with a 1-px stroke kept → WRONG. Square 1 px right → WRONG. 1 px low → WRONG. 19×20 → WRONG |
| R15 | PNGs looked at | ref, wave53-final android/iOS, wave53-probe android, 006, cropped 4× | Ref and iOS: solid 20×20 at x24-43 rows 56-75. Final android: square at rows 51-70, touching the text. Probe android: 1-px outlined EMPTY box at x24-43 rows 56-75 and the solid square at x44-63. The brief's corrected reading holds |
| R16 | Hygiene | `git status`, sha256, grep, `ls tools/titan/runs/wave54-lock/` | Only owned paths changed. Renderer at HEAD bytes. No lock dir. "probe" hits are run names or `IntrinsicChannel.probe`, not leftovers. No scratchpad path in the note, patch or scripts. New logic: TableCellHug.kt 111 lines, TableBodyForest.kt 134. TableApplier +12/−1 (param, provider, chain) |
| R17 | Unit patch | `git apply --check --cached unit-TB-android.patch`; `git apply -R --check` on the tree | Both clean. It holds exactly the 7 owned files |

**Side finding about the environment, not a lane defect:** my first mutation batch
(`skeptic-mutations.batch1-interfered.out.txt`) was disturbed by another lane's concurrent `:runtime:testDebugUnitTest`.
Its XMLs (UAElementFontRuleTest / UAHeadingFoldGateTest) landed in the same `test-results` dir, which produced a bogus
"GREEN1 tests 84" and a bogus "X6 tests 0".
- My driver now deletes and reads only its own suites' XMLs. The clean re-run is `skeptic-mutations.out.txt`.
- The lane's `mutate.py` already filters by class name, so its record is not affected the same way. It does still delete
  `TEST-*Table*` XMLs, which another lane could be reading.

## Defects (ranked)

1. **should-fix — the D2 quantity is unpinned** (`TableCellHug.kt` `hugColumn`, `TableCellHugTest`).
   - Mutation X3 (`widthAtMaxIntrinsic` → `widthAtMinIntrinsic`) keeps all 26 tests green.
   - The only check on the enabled branch is `assertNotSame(m, m.hugColumn(true))`.
   - On 006 min- and max-content coincide (an empty block 0, an exact-width td 20), so the stage-1 device gate cannot tell
     them apart either.
   - CSS 2.1 §17.5.2.2 asks for max-content. This becomes the next anonymous table's bug once it holds text.
   - **Fix:** a source pin in TableCellHugTest. It should assert that `hugColumn`'s enabled branch calls
     `widthAtMaxIntrinsic(` with `TableCellHug.CELL_HUG_REFUSAL`, and does not call `widthAtMinIntrinsic`. Then
     re-execute X3 red.
2. **nit — a vacuous order assert** (`TableCellHugTest.applierSource_…`).
   - `assertTrue(cell.indexOf(".then(cellModifier)") < hug)` passes when `.then(cellModifier)` is absent, because indexOf
     returns −1. Mutation X6 survives.
   - `cell` runs to EOF, so the `.fillMaxHeight()` lookup is not scoped to TableCell.
   - **Fix:** assert the index ≥ 0, and bound `cell` at the next `fun `.
3. **nit — the seam pin does not check `chrome`'s argument** (`TableCellHugSeamWiringTest`, inside `seam-1.patch`).
   - S5 (`chrome(component.copy(role = null), …)`) keeps the class green.
   - This is exactly the failure mode the note names for a stage-1 WRONG line: "role lost before the TABLE arm".
   - **Fix:** also assert the text `TableCellHug.chrome(\n                    component,`. The patch must then be re-cut and
     re-verified under the lock.
4. **nit — a full seam-file copy in the unit's results dir.**
   - `seam1-files/runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt` is the
     whole 8462-line patched renderer. The dir is 516 K, and it would be committed with TB-android, since the note lists
     this whole directory in the unit.
   - It is redundant: `seam1-hunk.py <HEAD renderer>` regenerates it byte-identically (sha `eaf9111e…` = the patched
     renderer, executed).
   - It would also pollute `git grep` for seam strings.
   - **Fix:** delete it, keeping `seam1-files/…/TableCellHugSeamWiringTest.kt`, or leave it out of the unit commit.
5. **nit — the refusal text overstates its scope** (`TableCellHug.CELL_HUG_REFUSAL`).
   - It says "this anonymous cell's subtree", but `hugColumn` runs on every cell of the anonymous table, including the
     author's td 2-143.
   - **Fix:** reword to "a cell of the anonymous table".
6. **nit — the fold pin skips one copy step on the real path.**
   - `anonymousMarker_survivesTheRenderersPreDisplayFolds` omits `ComponentHost.Render`'s
     `BackgroundColorInheritance.resolve`, which is on the real path.
   - R8 executed it, and it preserves the role (copy-based). So this is only a pin gap.
   - **Fix (optional):** add that step to the pin.

## Honesty / labels

- The 006 android target is labelled DEGENERATE → faithful, claimed only on both device geometry lines. Flips: 0.
- The replay 0.9983 is called a painted picture, never "picture-correct". The floor 0.996 and both gating probes match
  PLAN §2 and `expectations.json`.
- The note's role-reader counts (14× / 4×) are off by one each against my grep (15 / 3, plus a comment). This is
  immaterial: every reader compares against those two strings.

## What I could NOT check

- **The device picture.** Compose UI does not execute on the JVM here: no Robolectric or ui-test in
  `runtimes/compose/build.gradle.kts`. That leaves the following verified by reading and arithmetic only, plus R8's pure
  chain:
  - the per-cell `widthAtMaxIntrinsic` inside the `fillMaxWidth` TableRow;
  - its interplay with the row's `heightAtMinIntrinsic`;
  - the td at x24-43.

  Stage 1 (`wave54-pre`) is the evidence.
- **Stage 1 / the closing gate / control-check / probe-readout.** These are orchestrator windows.
- **Full suites.** These belong to the single-writer sweep.
- **L4's ComponentRenderer seam-1** is not written yet, so its composition with L2's hunk is unchecked. L5's and L3's
  compose cleanly (R6).
- Whether a later L4 edit moves 006's abspos text, which the stray-ink band (rows 52-140) would read as an L2 failure at
  stage 1. L4's current in-flight CanvasRootHoist diff does not reach 006 (3-144 has insets and still hoists), and the
  forest's m3 / stack pins are green both with L4's edits (shared tree) and without them (export).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (shared tree: mutations,
seam replay under the lock, census, geometry); export tree (scratch, regenerable: `git archive HEAD apps/android-harness
runtimes/compose schema fixtures` + the 7 unit files [+ seam-1.patch]) for R7 / R8.

# Wave 54 · L4 oof-layout — builder lane note

Contract: `tools/titan/results/wave54-plan/PLAN.md` "### L4 · oof-layout" (briefs `oof-containing-block.md`,
`compose-wpt-content-box-cb.md`, `flex-zero-gap-rules.md`). Five revert units: **OOF-android, OOF-ios, CBB-android,
GAP-android, GAP-ios**. Shared tree, no commits by this lane; one seam patch (CBB-android); one hunk for the plan owner.

This note was written by the second attempt. The first attempt died before writing a note. It left the full code and test edits,
the mutation runs M-a/M-b/M-c (Compose), M-a/M-b/M-d (Swift) and M-gap1-3 (both), the censuses and seam-1. Every mutation
summary records `restored=OK`, and every owned file's sha256 equalled its mutation "before" hash when this attempt started.
The second attempt then:
- re-verified all of it;
- added the CBB mutations, M-c-swift, M-e-compose and the census-base provenance checks;
- re-ran the seam verification, the geometry self-test and both final green runs;
- found the CBB blast-radius gap (§CBB census) and wrote the hunk.

## Where the brief / plan and the tree disagree (the tree wins)

1. **HEAD.** HEAD is `31b76944` (the plan's second fix pass), not `db6e8aa0`. The plan owner also has uncommitted fix-round-3
   edits under `wave54-plan/` (`plan-build.py`, `expectations.json`, `flex-zero-gap-rules.geometry.py`, written 20:38–20:45).
   - Every source/seam file this lane touches is byte-identical at `7cce3b22`, `db6e8aa0` and `31b76944`. Checked with
     `git diff --quiet 7cce3b22 HEAD -- ComponentRenderer.kt`.
   - The geometry self-test below was re-run on the fix-r3 probes.
2. **CBB carrier set.** It is incomplete: 3 android captures are missing (§CBB census). Handed off as
   `hunk-for-orchestrator-1.patch`.
3. **Call sites in oversized files.**
   - `CanvasRootHoist.kt`: the RC1 class (:403-411) is a 5-line `when`, not a one-line condition, so that FIXED can join on a
     different inset test from ABSOLUTE.
   - `FixedHoist.swift:115-124`: a 3-arm `switch`, for the same reason.
   - Both are still call sites only. All new logic is in the new ≤ 200-line files.
4. **Line anchors at HEAD** (they moved by the inserted comments):
   - Compose: OR `CanvasRootHoist.kt:231-232`; FIXED arm `:354`; RC1 `:405-410`.
   - Swift: RC1 `FixedHoist.swift:115-124`; OR `:321`; fixed strip `:362`.

## What changed and why (file:symbol)

### OOF: the containing block of an out-of-flow box (css-position-3 §2.1 / §3.5.3; css-contain-2 §3.2 / §3.4; filter-effects-1 §5; filter-effects-2 §2; css-will-change-1 §3)

**F1, the rule table (new twins, pure over the wire):**
- Compose `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt` (181 lines).
- Swift `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift` (176 lines).

`establishes(properties)` is true for:
- `Contain` ∋ LAYOUT | PAINT | STRICT | CONTENT;
- a non-empty `Filter` / `BackdropFilter` array, or an object leaf;
- `WillChange` naming filter / backdrop-filter / contain.

It is false when the box itself is ABSOLUTE | FIXED, and then leaves the breadcrumb
`ContainingBlock[positioned-establisher-fixed-descendant]`. Two more breadcrumbs keep nothing silent:
- `ContainingBlock[contain-decode-failed]`;
- `ContainingBlock[implied-containment-unclaimed]` (content-visibility / container-type, named but not claimed).

`declaresAnyInset(properties)` reads the inset as **declared**, not resolved: absent or "auto" means no inset. As a result
anchor-center-safe-rtl's unresolvable `calc(anchor(…))` fixed box keeps its viewport hoist.

**F2, the one OR per native:**
- Compose `CanvasRootHoist.establishesTransformContainingBlock` (:231-232) is `TransformContainingBlock.establishes(p) ||
  OutOfFlowContainingBlock.establishes(p)`. It is the single choke point that both pure walks (`collectCanvasHoisted` :535,
  `anyOutOfFlowBox` :628, which `hostActivates` reads) and `ComponentRenderer.kt` :2014 / :3473 read.
- Swift `FixedHoist.split` `childTransformed` (:321).

**F3, FIXED needs a declared inset to hoist, and joins the static-position (RC1) class:**
- Compose `shouldHoistToCanvasRoot` FIXED arm (:354) and `rendersInFlowAsStaticPosition` (:405-410).
- Swift: the fixed strip (:362) and `rendersInFlowAsStaticPosition` (:115-124).
- ABSOLUTE keeps its frozen resolved-px `hasAnyInset` test.

### CBB: Compose WPT containing-block bands (CSS 2.1 §10.1 / §10.2; css-sizing-3 §3)

- **New** `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt` (53 lines, ≤ 60).
  - `subtracts(properties, wptCaptureMode)` reads the EFFECTIVE box-sizing through the sizing path's own decoder
    (`SizingExtractor.extractSizingConfig(…, wptCaptureMode)`, which applies `SizingApplier.effectiveBoxSizing`), so the frame
    and the block cannot disagree.
  - Under WPT a content-box publishes its declared size, and an explicit border-box still subtracts.
  - The dark stage always subtracts. An explicit content-box there gets the breadcrumb
    `ContainingBlock[dark-stage-content-box-bands]`.
- `DynamicValueResolver.childContainingBlock(…, wptCaptureMode: Boolean = false)` gets a defaulted parameter (:214) and one
  condition (:242).
- The `ComponentRenderer.kt:1934` call passes `wptCaptureMode = wptCaptureModeForStretch` through **seam-1.patch** only. The
  tree compiles and stays byte-identical in behaviour without it, because the default is false.

### GAP: rules on zero-width gaps (css-gap-decorations-1)

- `GapInterval.isPositionable = end >= start`. Compose `GapDecorationGeometry.kt:65`; the Swift twin is
  `GapSpan.isPositionable` (`GapDecorationGeometry.swift:33`).
- It replaces the empty-gap filters at:
  - Compose `GapDecorationLines.kt:144` (main gaps), `:165` (`betweenLineGaps`), `GapDecorationSegments.kt:169`;
  - Swift `GapDecorationLines.swift:42` (`gapBands`), `:118` (`betweenLineGaps`), `GapDecorationSegments.swift:143`.
- `isEmpty` / `isPositive` keep their meaning for cuts: `union()` still drops zero-width cuts, so a zero gap cuts nothing.
- Negative (overlap) extents are still dropped. Paint order is unchanged: row over column.

## Revert units: exactly the paths of each commit

### Unit OOF-android
- `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt` (NEW)
- `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlockTest.kt` (NEW)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/CanvasRootHoist.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/CanvasRootHoistTest.kt`
- `tools/titan/results/wave54-oof-layout/census-compose.base.txt`
  - **Required by the corpus pin.** `OutOfFlowContainingBlockTest` reads it whenever the gitignored wave53-final corpus is
    present, so it must land in, or before, this commit.

### Unit OOF-ios
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift` (NEW)
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockTests.swift` (NEW)
- `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/FixedHoist.swift`
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/FixedHoistTests.swift`
- `tools/titan/results/wave54-oof-layout/census-swift.base.txt` (read by `testP3TheCorpusMovesInExactlyTheSixOOFiosCarriers`;
  the same rule as above)

### Unit CBB-android
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/ContainingBlockBands.kt` (NEW)
- `runtimes/compose/src/main/java/com/styleconverter/runtime/core/variables/DynamicValueResolver.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/core/variables/DynamicValueResolverTest.kt`
- **+ `tools/titan/results/wave54-oof-layout/seam-1.patch`**, applied right before this commit. It carries:
  - the `ComponentRenderer.kt:1934` hunk;
  - the NEW patch-borne `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/ChildContainingBlockSeamWiringTest.kt`.

### Unit GAP-android
- `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/GapDecorationGeometry.kt`
- `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/GapDecorationLines.kt`
- `runtimes/compose/src/main/java/com/styleconverter/runtime/columns/GapDecorationSegments.kt`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/columns/GapDecorationSegmentsTest.kt`

### Unit GAP-ios
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/GapDecorationGeometry.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/GapDecorationLines.swift`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/GapDecorationSegments.swift`
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/GapDecorationSegmentsTests.swift`

**Independence, by construction (each subset was not built separately).** The units are file-disjoint, and no test of one unit
references a symbol of another unit, so every unit should compile and its pins should pass with any subset of the other four.
- OOF pins do not read CBB/GAP code, and vice versa.
- The CBB census pin reads only the block threading.
- The OOF census pin reads only the hoist decisions.
- No revertOrder constraint inside L4.

The rest of `tools/titan/results/wave54-oof-layout/` is lane evidence (scripts, census outputs, mutation logs). Commit it with
the lane's results, not inside a runtime unit.

## Census (blast radius = the carrier set for `control-check.mjs`), with method

All on the VERBATIM per-test IR of `tools/titan/runs/wave53-final/sections/*/per-test-ir/` (1435 documents). PLAN §10 item 1
says it is byte-identical to wave54-open.

### OOF (both natives): a behavioural census, base proven to be HEAD's
- **Method.**
  - A JVM census line per document: hoist H, RC1 R, positioned-container Box B, out-of-flow-under-establisher T,
    PositionedParentFlowSlot S, `hostActivates`. It is computed by the runtime's own functions
    (`OutOfFlowContainingBlockTest.censusLine`) and walked exactly like `collectCanvasHoisted`.
  - A Swift census line per document: `FixedHoist.split` flow / hoisted, root static class, fixed boxes kept in tree
    (`OutOfFlowContainingBlockTests.censusLine`).
- **Base provenance (executed this attempt).** Each native's owned call-site file was swapped back to its HEAD bytes and the
  corpus pin run (`census-base-check.sh`; logs `mutations/B-{compose,swift}-census-under-HEAD.*`):
  - the pin went RED (nothing moves);
  - the census it wrote is **byte-equal** to `census-compose.base.txt` / `census-swift.base.txt` (1435 lines each);
  - the file was restored byte-exact: Compose `367775501a88…` before = after, Swift `e88a6433ec05…` before = after.
  - The lane code's census equals `census-{compose,swift}.after.txt` byte-for-byte (re-checked after the final runs).
- **Result.**
  - **Compose moves in exactly the 9 OOF-android carriers.** Host activation turns true → false in exactly 7
    (contain-content-003/-004/-011, change-insets, position-relative-003/-004, backdrop-filter-containing-block) = the plan's
    `hostFlips`.
  - **Swift moves in exactly the 6 OOF-ios carriers.** iOS contain-content-003/-004/-011 do not move: Swift touches FIXED only
    (§9 D3 holds).
  - The other 1426 / 1429 documents are byte-identical census lines.
- **Named controls** (`controls-census.out.txt`): 32 tests, all `same` on both natives. That is all 26 `fixedControls`
  (css-view-transitions ×2 included), the 4 positioned backdrop-filter establisher controls, backface-visibility-hidden-001,
  cross-fade-target-alpha, anchor-center-safe-rtl, anchor-name-006 and position-fixed-dynamic-transformed-sibling. P3's
  "hoist exactly what they hoist today" is shown for every one of them, not sampled.
- **Pass today** (`carrier-census.out.txt`):
  - OOF-android: 9 captures, P 4 (all DEGENERATE red-ink) · f 4 · unscored 1.
  - OOF-ios: 6 captures, P 4 (DEGENERATE) · f 1 · unscored 1.
- Matches expectations.json `revertUnits` OOF-android / OOF-ios captures exactly.

### CBB-android census
- **Method.** `DynamicValueResolverTest` "CBB census". Every document is walked twice, threading `childContainingBlock` with
  `wptCaptureMode` false (frozen) and true (WPT). For every box whose incoming block differs, the census records any reader
  that sees it:
  - `dyn`: the `DynamicValueResolver.resolve` pre-pass;
  - `oof`: `resolveOutOfFlowPercentSizes` → `AbsposInsetStretch` → `AbsposAutoMargin`;
  - `spc`: % margins, paddings and insets;
  - `vtext`: vertical-text budget.
- Output: `cbb-census.out.txt`, byte-equal to the pin's `runtimes/compose/build/cbb-census.now.txt` this attempt.
- **Result: 14 documents.**
  - The 10 planned carriers (abspos-autopos ×6, nested-border-radius-clip ×4) plus the side reader flex-gap-decorations-006
    (`vtext`).
  - **Plus 3 the plan does not carry:** `css-position/position-absolute-semi-replaced-stretch-{button,input,other}`. Their
    `top/right/bottom/left: 3px` stretch children of a 3px-bordered content-box 150×100 (350×100) parent read 144×94 →
    150×100 (`oof`) and grow 138×88 → 144×94.
  - The planning census (`queue-scout-layout.census.mjs` `cbborder`, re-run here as `layout-census.wave53-final.json`) requires
    a % child, so it missed px-inset stretch.
  - cross-fade-target-alpha (the in-flow control) is NOT in the list: invariant.
- **Picture checked.** Look-sheets of ref | web | ios | android, made with `look.py`, whose ref path was fixed for sub-directory
  stems. The android lime outlines stop 6 px short of the container's inner right and bottom edges; the ref's reach them.
- **Replay** (`cbb-semi-replaced.replay.py` grows each lime ring +6 px right/bottom; `cbb-semi-replaced.replay-score.mjs`
  scores with the gate's `diffWebVsRef`; sanity: today's captures reproduce the gate's numbers):

  | android cell | today | replay |
  |---|---|---|
  | button | 0.9739 | **0.9843** |
  | input | 0.4117 | **0.4555** |
  | other | 0.4396 | **0.4677** |

  - All stay f. Button's f is the coverage-ratio veto (capture ink 1.5 % vs ref 6.85 %, the native button chrome), which CBB
    does not touch.
  - The web-root-separator probe's android control line for input / other (`second atom edge xNone vs ref x192 on row 60`) is
    unchanged on the replays (`cbb-semi-replaced.rs-probe-line.out.txt`).
- **Consequence.** Unamended, the R4 capture control reads all three as CBB-android leaks, and two of them are L6 must-not-move
  lines (watchlist 700 / 702). Handed off as `hunk-for-orchestrator-1.patch`, dry-run proven (§Hand-offs).
- **Pass today:** 14 captures, P 9 (autopos ×6 DEGENERATE, nested-clip, -2, -4 short box) · f 5.

### GAP census
- **Method.** `layout-census.wave53-final.json` `gapzero`: 17 flex containers (0 grid) carry a rule family with a zero or
  absent gap on some axis. Each was read against its IR. The gap decoration hook is flex-only on both natives
  (`rememberGapDecorationSink(isFlex)`, `CSSFlexLayout.gapDecorations`).
- **Result: 033 is the only container whose DECORATED axis has touching neighbours or lines.**

  | containers | why the change cannot reach them |
  |---|---|
  | 007 / 008 / 027 / 044 | column gap 10 / 10 / 10 / 13, column rules only |
  | 017 / 018 | row gap 20, single column line |
  | 045 | column gap 5; its row rule's within-line gaps are opened by space-between |
  | 046 | row gap 5, row rules only; its touching items carry no column rule |
  | 050 | row gap 10; space-between opens the column gaps |
  | 040 / 043 | space-between |
  | 041 / 042 | space-around / space-evenly |
  | 047–049 | align-content separates the lines |

  - Of the 46 rule-carrying flex containers in the corpus, the only item with a negative margin is 027's -150 px paint shift.
    Its column gap stays 10.
- **Pass today:** 033 ios f 0.94, android f 0.94.

## Pins + executed mutations (red → restore → green)

Every mutation is a literal replacement by `mutate.sh`, which:
- asserts the anchor count is 1;
- runs the focused suite;
- restores from a byte copy and logs sha256 before / mutated / after.

All rows read `restored=OK`. Logs are in `mutations/<label>.{run,summary}.txt`; the literals are in `mutations/lit/`.

| pin(s) | mutation | file (sha256 before = after) | result |
|---|---|---|---|
| P1 contain row, P2 strict / content rows, P3 inline + corpus | **M-a** `"Contain" -> false` | Compose `OutOfFlowContainingBlock.kt` `ed0d29ac…` | RED 6/37 |
| same, Swift twin | **M-a-swift** | Swift `OutOfFlowContainingBlock.swift` `2682c94c…` | RED (FixedHoistTests P2 strict; OOF P1 contain, P3 corpus) |
| P2 "fixed with no inset does NOT hoist", P3 inline + corpus | **M-b** drop `declaresAnyInset` from the FIXED arm | `CanvasRootHoist.kt` `36777550…` | RED 3/37 |
| same, Swift | **M-b-swift** drop it from the fixed strip :362 | `FixedHoist.swift` `e88a6433…` | RED (P2 no-inset; P3 inline + corpus) |
| P1 positioned-establisher row, P3 corpus (moves +4: clip-rect, -zoom, -edge-clipping, -zero-size; `M-c-compose.census-moved.txt`) | **M-c** drop "not itself absolute/fixed" | Compose OOF `ed0d29ac…` | RED 2/37 |
| P1 positioned-establisher row (Swift corpus does NOT move: iOS positioned establishers hold ABSOLUTE children only, inert there; recorded, not a gap) | **M-c-swift** (new this attempt) | Swift OOF `2682c94c…` | RED 1 test (2 assertions) |
| P2 strict on iOS, P3 inline + corpus | **M-d** revert Swift :321 (`\|\| false`) | `FixedHoist.swift` `e88a6433…` | RED |
| P2 strict / content rows, P3 inline + corpus | **M-e** revert Compose :232 OR (new this attempt) | `CanvasRootHoist.kt` `36777550…` | RED 4/37 |
| CBB WPT rows (container 100×100; nested clip 200×100 ×2) + CBB census | **always subtract** (`if (wptCaptureMode) return true`) | `ContainingBlockBands.kt` `f4bb43ff…` | RED 3/32 |
| dark-stage rows (300 → 276 frozen; autopos dark 70×80) + CBB census | **never subtract** (dark-stage `return false`) | same | RED 3/32 |
| explicit BORDER_BOX under WPT still subtracts | WPT ignores declared border-box (`if (wptCaptureMode) return false`) | same | RED 1/32 |
| seam wiring source pin | drop the named argument (seam applied, under the lock) | `ComponentRenderer.kt` `da2df0b4…` restored from HEAD | RED. Re-verified this attempt: `seam-1.verify.r2.txt` |
| GAP 033 column rules 2+1+2 / row rules 2 full-width, painted last | **M-gap1** restore `!gap.isEmpty` (main gaps) | Compose `GapDecorationLines.kt` `0bd66570…` | RED 2/52 |
| GAP 033 row rules | **M-gap2** restore `gap.isEmpty` (between lines) | Compose `GapDecorationSegments.kt` `d2d4dc48…` | RED 1/52 |
| negative-overlap pin | **M-gap3** `isPositionable = true` (accept negative) | Compose `GapDecorationGeometry.kt` `ce69eb46…` | RED 1/52 |
| same three, Swift | **M-gap{1,2,3}-swift** (`gapBands` / `lineGapRuns` / `isPositionable`) | Swift `GapDecorationLines.swift` `e6300c6a…`, `-Segments.swift` `8f1bc461…`, `-Geometry.swift` `80a314e0…` | RED 2 / 3 / 1 of 19 |

**P4 (host flips exactly 7)** is asserted inside the Compose corpus pin. It goes red under M-a, M-b, M-c and M-e.

**Final green on restored bytes** (after every mutation and swap):
- Compose (`final-compose.run.txt`, `final-compose.counts.txt`), filter `*OutOfFlowContainingBlock* *CanvasRootHoist*
  *TransformContainingBlock* *DynamicValueResolver* *GapDecoration*`: **10 classes, 124 tests, 0 failures, 0 errors, 0 skipped.**
  The corpus pins ran; they were not skipped.
- Swift Catalyst (`final-swift.run.txt`), classes OutOfFlowContainingBlockTests, FixedHoistTests, TransformContainingBlockTests,
  GapDecorationSegmentsTests, GapDecorationBandsTests, GapDecorationGrammarTests:
  - xcodebuild: **"Executed 75 tests, with 0 failures"**, per class 8 / 16 / 10 / 19 / 11 / 11 (N > 0 for each);
    `** TEST SUCCEEDED **`.
  - The extra gap classes (Hook / Raster / Surface / Wire, `final-swift-gap-extra.run.txt`): **Executed 19 tests, 0 failures.**
- Seam-1 with the patch applied (`seam-1.verify.r2.txt`, lock `tools/titan/runs/wave54-lock/ComponentRenderer.kt`
  acquired 19:00:38Z, released 19:00:50Z): ChildContainingBlockSeamWiringTest 1/1, DynamicValueResolverTest 32/32,
  OutOfFlowContainingBlockTest 8/8, CanvasRootHoistTest 29/29 green. Seam sha256 before `da2df0b4cca689ad…` = after.
- Final owned-file hashes: `final-owned.sha256`.

## Geometry gate (`--lanes L4 --self-test`)

`python3 tools/titan/results/wave54-plan/geometry-gate.py wave53-final --base wave53-open --lanes L4 --self-test`
(`geometry-gate.wave53-final.L4.selftest.r3.out.txt`, on the fix-r3 probes; the first attempt's run is `…selftest.out.txt`):
- exit **0**; 54 keys;
- **gating 17, all FAIL · control 30, all PASS · report 7, all FAIL** · UNMEASURED 0 · self-check failures 0;
- `self-test: HOLDS`; revert rule 4 names CBB-android, GAP-android, GAP-ios, OOF-android, OOF-ios.

## Predictions (against wave54-open = wave53-final) with confidence

These are the plan's rows, which this lane keeps. Every native label below is a PREDICTION. None is claimed picture-correct
before the device probe and its geometry key.

| cell | from → predicted | confidence | gate |
|---|---|---|---|
| contain-content-003 android | f 0.9405 → P ≈0.997 | HIGH | floor 0.99 + geometry |
| contain-content-011 android | f 0.9284 → P ≈0.985 (its "25" counter stays wrong ×3) | MED-HIGH | 0.97 + geometry |
| backdrop-filter-containing-block ios / android | f 0.7955 / 0.7506 → P (bracket 0.94–1.0) | MED | report |
| position-relative-004, change-insets ios / android | P 0.9594 / 0.9589 DEGENERATE → ≈0.997 | MED-HIGH | 0.99 + geometry |
| static-fixed-inside-abspos ios / android | P 0.9841 / 0.9835 DEGENERATE → ≈0.997 | HIGH / MED-HIGH | 0.99 + geometry |
| position-relative-003 ios / android | P DEGENERATE → ≈0.997 | MED | report |
| contain-content-004 android | f 0.8286 → moves, stays f | MED, undirected | report |
| nested-border-radius-clip-3 android | f 0.9574 → P ≈0.99 | MED-HIGH | 0.96 + geometry |
| nested-border-radius-clip / -2 / -4 android | P 0.9649 / 0.9689 / 0.977 → ≈0.99 | MED-HIGH | 0.98 + geometry |
| abspos-autopos-{htb,vlr,vrl}-ltr android | P 0.9966 DEGENERATE → 0.9967 | HIGH | geometry (floor 0.995 vacuous) |
| abspos-autopos-*-rtl android | → ≈0.9967 | MED | report |
| flex-gap-decorations-006 android | f 0.8223 → may move, stays f | LOW, undirected | report |
| flex-gap-decorations-033 ios / android | f 0.94 → P ≈1.000 | HIGH | 0.99 + geometry |
| **NEW (hunk-for-orchestrator-1):** semi-replaced-stretch-button android | f 0.9739 → up ≈0.984, stays f (coverage veto) | MED (pixel replay) | report |
| **NEW:** semi-replaced-stretch-input android | f 0.4117 → up ≈0.456, stays f | MED (pixel replay) | report |
| **NEW:** semi-replaced-stretch-other android | f 0.4396 → up ≈0.468, stays f | MED (pixel replay) | report |

Expected L4 flips at HIGH / MED-HIGH:
- android +4: contain-content-003, -011, clip-3, 033;
- ios +1: 033.

The MED flips are backdrop-filter-containing-block ×2.

## Must-not-move

The 253 cells of `expectations.json` `lanes["L4-oof-layout"].mustNotMove`, unchanged by this lane:
- every web cell of the carrier tests;
- iOS contain-content-003/-004/-011;
- the 24 scored fixedControls ×3;
- backdrop-filter-zero-size ×3;
- backface-visibility-hidden-001 ×3;
- cross-fade-target-alpha ×3;
- iOS of the 10 CBB documents (the three `*-rtl` iOS rows keep their 10-px offset verbatim);
- every other css-gaps cell ×3.

Every one is invariant by construction:
- CBB and GAP-android are Compose-only, OOF-ios and GAP-ios are Swift-only, and nothing touches web.
- The OOF censuses show every control `same`.
- The CBB census shows cross-fade-target-alpha unread.
- The GAP census reaches 033 only.

The semi-replaced web / iOS cells stay must-not-move: CBB is Compose-only.

## Hand-offs

- **`seam-1.patch`** (CBB-android; PLAN §3 row `:1934`).
  - Header: base `db6e8aa0`, which is byte-identical to `7cce3b22` and HEAD `31b76944` for this file; the seam sha256 at base
    `da2df0b4cca689ad30943213a5e10253881073b7287c45eabf42cb743e1d04db`; anchor `:1930-1934`.
  - `git apply --check` is clean on the current tree.
  - It carries the patch-borne source pin `ChildContainingBlockSeamWiringTest.kt`.
  - Landing: after L5's `:1141` and L2's `:2653`; disjoint from L3's `:7072`.
- **`hunk-for-orchestrator-1.patch`** → `tools/titan/results/wave54-plan/plan-build.py`.
  - Base sha256 `plan-hunk/plan-build.base.sha256`: the fix-r3 working copy, 20:45.
  - It adds `CBB_STRETCH` (the 3 semi-replaced-stretch documents) to L4's android capture carriers and to the CBB-android unit,
    plus 3 MED up-or-stay prediction rows (no floor, not gating). `mnm()` then drops L6's two android must-not-move lines by
    itself.
  - **Dry run executed, read-only on the plan dir** (exec with `__file__` = the real path, `--out` into `plan-hunk/`):
    - the base copy reproduces the installed `expectations.json` and `watchlist.txt` byte-exact;
    - the patched copy exits rc 0 with every assertion holding: union android carriers 51 → 54, L4 predictions 26 → 29, L6
      must-not-move 68 → 66, watchlist 775 → 776;
    - `watchlist-check` reports `unmatched 0` on wave53-final and wave54-open.
  - `git apply --check` is clean.
  - PLAN.md §1 / §2 / §6 counts would need the same restatement.
  - **If the orchestrator declines it, the R4 control at the probe will flag these 3 android captures as CBB-android leaks.**
    They are leaks of a correct change, with the picture explained above.

## ORCHESTRATOR WINDOW REQUESTS

None of the device work is this lane's to run. PLAN §2 L4 says "Window requests: none. Every device read is the probe." What
the stage-2 probe must read for L4 is PLAN §6's L4 row. Additions from this lane:

1. In the same probe run, read `css-position` android for `position-absolute-semi-replaced-stretch-{button,input,other}`.
   - Command: `node tools/titan/results/wave52-gate/cells.mjs 'semi-replaced-stretch' wave54-open wave54-probe`.
   - Expected: android button / input / other move UP to about 0.984 / 0.456 / 0.468, all staying f. Web and iOS identical.
   - Decision: with hunk-for-orchestrator-1 applied these are carriers (rules 1 / 2 bind). Without it they are R4 leaks to
     attribute to CBB-android by this note, not a reason to revert it.
2. OPEN the probe PNGs of the paragraph-pitch risk (y≈44-83) on the 7 host-flip documents, android.
   - Documents: contain-content-003/-004/-011, change-insets, position-relative-003/-004, backdrop-filter-containing-block.
   - Brief R2: host activation turns off there, so the root paragraph takes the identity path.

## What this lane could NOT verify

- **Every device picture.** No emulator or simulator was run. Every native "→ P" above is a prediction; the census and
  replays are not captures.
- **Risks the plan names as device-only:**
  - 004's in-flow `FAIL` span inside the new positioned-container Box (R1);
  - the paragraph pitch on the 7 host-off documents (R2);
  - `contain: paint` does not clip (a named limitation, not implemented);
  - the backdrop two-pass must not invert green / red (backdrop-filter-containing-block);
  - where iOS places backdrop-filter-containing-block's ABSOLUTE red box (Swift never hoisted nested abspos; MED).
- **The semi-replaced replay is pixel surgery.** It grows the lime ring and leaves everything else as is. A device capture may
  differ in how Compose draws the outline of a stretched native control.
- **GAP reach** is bounded by a static census plus per-document reasoning. Item rects exist only on device, so a layout whose
  items touch for a reason the IR does not show (none found) would not be seen until the probe.
- **The CBB census covers the four reader families it names.** A reader of `LocalContainingBlock` outside them would not be
  listed. The VerticalTextFlowLayout budget is covered (`vtext`); 006 is watched.
- **The corpus pins** (OOF ×2 and CBB) are local-only. They `assume` / `XCTSkip` when `tools/titan/runs/wave53-final` is
  absent, as in CI. The inline verbatim payloads are the CI-resident part.
- **`integrate-seams.sh --dry-run`** with the other lanes' patches is the orchestrator's (§3). Only `--check` on the current
  tree was run here.
- **The Swift corpus pin** writes its census to `FileManager.temporaryDirectory`, the user temp dir, not to the tree.

## Files in this directory

| file(s) | what it is |
|---|---|
| `mutate.sh`, `xc.sh` | the mutation runner and the Catalyst runner (rule-3 retry) |
| `census-base-check.sh` | the HEAD-provenance swap check |
| `seam-verify.sh` | seam under the lock |
| `look.py` | ref \| web \| ios \| android sheets |
| `cbb-semi-replaced.replay.py`, `-score.mjs` | the replay and its score |
| `census-{compose,swift}.{base,after}.txt` | the OOF censuses |
| `cbb-census.out.txt` | the CBB census |
| `controls-census.out.txt` | the 32 named controls |
| `carrier-census.out.txt` | carriers with today's verdicts |
| `layout-census.wave53-final.{json,out.txt}` | the planning census, re-run |
| `geometry-gate.*.txt` | the self-test runs |
| `final-*.txt` | the final green runs |
| `seam-1.patch`, `seam-1.verify{,.r2}.txt` | the seam patch and its verification |
| `hunk-for-orchestrator-1.patch`, `plan-hunk/` | the plan hunk and its dry run |
| `mutations/` | mutation logs and literals |

`git diff` of the owned source files contains no "probe" / "Probe" leftovers (grep, this attempt).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (shared tree; Gradle via its apps/android-harness, xcodebuild Catalyst from its root; no export tree)

STATUS: COMPLETE

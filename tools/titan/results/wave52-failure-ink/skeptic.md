# L3 · failure-ink — executed-repro skeptic (wave 52, 2026-10-05)

Tree: shared worktree `trusting-bohr-bd6fbf`, branch `campaign/wave52`, HEAD `7d9c22a7`, other lanes' uncommitted work present.
Read: PLAN.md "### L3 ·" + §3/§5/§9, `failure-ink.md` brief, `_note.md` (ends `STATUS: COMPLETE`), every file in this directory.
Skeptic scratch (scripts + logs): the session scratchpad `…/scratchpad/sk/` — the scripts are reproduced in prose below so nothing
here depends on the scratchpad surviving. Nothing was committed; every mutation and seam application was restored byte-exact.

## 0. Verdict up front

The lane's code, pins, census and PNG predictions REPRODUCE. Independent census numbers match the lane's to the unit; the three
mutations I re-executed (2 Compose, 1 Swift) fail exactly the rows the note says; seam-1 and seam-2 apply clean on HEAD and every
L3 pin is green with each applied (Compose 93/0; Catalyst all L3 suites green). The scorer's own functions, run on simulated
post-fix pictures, give the predicted verdicts for all four flip cells and keep the at-risk Android T1 cell P; an executed iOS
render of the verbatim T1 IR with seam-2 applied paints 0 red / 25 000 green (the ref), settling the note's open stretch
precondition. No foreign edit.
**ONE must-fix**: the hand-off `hunk-for-orchestrator-1.patch` (Swift `VerticalMulticolPlan.swift`) turns an existing Catalyst pin
red (`VerticalFragmentGeometryTests.testVerticalPlanDeclinesBakedLayout`) — executed, §4. Should-fix / nit: the seam wiring itself
is unpinned (dropping seam-1/-2 leaves every pin green); a T3 scorer-degeneracy caveat the note does not state; comment carrier
counts quote own-mode 12 where the seams read used-mode 23; modified files over the 200-line target; results dir uncommitted.

## 1. Pins re-run (current shared tree, seams NOT applied)

`(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*ContentsUnboxingTest' --tests '*VerticalBlockFlowSeamGuardTest'
--tests '*MulticolSpannerContainingBlockTest' --tests '*MulticolSpannerFlowTest' --tests '*CanvasRootHoistTest' -I …/gradle-xml-redirect.init.gradle.kts
-Dwave52.xmlDir=<scratch>/xml-run1)` 17:40 → XML: MulticolSpannerContainingBlockTest 14/0, MulticolSpannerFlowTest 23/0,
ContentsUnboxingTest 11/0, VerticalBlockFlowSeamGuardTest 6/0, CanvasRootHoistTest 25/0 = **79/0** (matches the note's seam-1 run set).

## 2. Mutations re-executed independently (restored byte-exact, sha256-verified)

| id | file | mutation | result | restore |
|---|---|---|---|---|
| SK-M1 | `BakedLayoutSignature.kt` | predicate body → `WIDTH in types && HEIGHT in types` (the wave-47 heuristic) | VerticalBlockFlowSeamGuardTest **5/6 FAIL** (authored square, authored 10×20 sibling, BoxSizing-alone/band-alone, ChildSpec, td-different-subpixel row (1)); only "013 IS baked" stays green; MulticolSpannerFlowTest 23/0 | sha `3aa0f815…5e9fce` before = after |
| SK-M2 | `ContentsUnboxing.kt` | Float clause re-added after the position clause | ContentsUnboxingTest **2/11 FAIL** (the flipped float row + the verbatim `__1-218` row) | sha `3b3578f9…f88968` before = after |
| SK-M6 | `MulticolSpannerContainingBlock.swift` | `hoistsToInitialContainingBlock` → `return false` after its doc line (run 18:02:59–18:04:02 with the `ComponentRenderer.swift` lock held only to keep the seam at HEAD bytes while compiling; private `-derivedDataPath`) | `MulticolSpannerContainingBlockTests` S4 row 1 FAIL (2 asserts) + all 3 `MulticolSpannerHoistRasterTests` FAIL (3+3+1 asserts); FixedHoistTests 14 · ContentsUnboxingTests 11 · VerticalBlockFlowSeamGuardTests 5 · **VerticalFragmentGeometryTests 9** green | sha `83289165…6c4476` before = after |

## 3. Census — my OWN script (python, never reading census.json/census.mjs)

Walk: all 1435 `tools/titan/runs/wave51-fix/sections/*/per-test-ir/*.json`; parent = `slot.parent`; `postLoadExtracted` from the
30 manifests via each entry's component-name prefix. Seam gates mirrored from source (Compose `ComponentRenderer.kt:2702-2805`,
Swift `verticalBlockFlowZ2` `:4311-4370`): used WritingMode (nearest self-or-ancestor), no own text, ≥1 in-flow child, no
inline-level Display child (INLINE/INLINE_BLOCK/INLINE_FLEX/INLINE_GRID), no in-flow child declaring WritingMode, not
all-text-only-leaves, ≥1 in-flow child with Width+Height (old guard); new guard = Width∧Height∧BoxSizing∧(PaddingTop∨BorderTopStyle).

| quantity | lane (`_note.md` §3) | skeptic | |
|---|---|---|---|
| F1 used-mode old-guarded containers | 39 (19 own / 20 inherited) | **39 (19 / 20)**, 18 docs | = |
| … new guard true | 23, all baked | **23**, baked 23 in 9 docs, slip-through **0** | = |
| … authored, unguarded now | 7 block-arm movers in 3 docs + 7 `<tr>` (table path) + FLEX ×2 | target ×2, abs-pos-border-offset-003 ×3, baseline-vertical `<td>` ×2; `<tr>` ×7 (td-different-subpixel ×1, collapsed-border-* ×2 each); flex-align-baseline-…-wrap-reverse ×2 (FLEX) = 16 | = |
| F1 own-mode (brief recipe) | 20 containers / 14 docs, baked 12/12 in 9 | 17 / 13 with "no child declaring WM" over ALL children; the 3 abs-pos-border-offset-003 containers re-enter when the WM test is in-flow only (the seam's rule) → 20 / 14 | = (definition) |
| W+H components | 5353: plx 1478 T / 113 F; authored 39 T / 3723 F (17 docs) | **5353: 1478 / 113; 39 / 3723, 17 docs** (anchor-center-overflow-005 ×8, counter-suffix ×6, bidi-plaintext-br-001 ×6, backdrop-filter-clip-rect-2 ×3 …) | = |
| F2 `Display=CONTENTS` | 106 comps; non-none Float exactly 1 | **106 comps / 42 docs; Float: exactly 1** (`display-contents-float-001__1-218`) | = |
| F3 `ColumnSpan=ALL` | 28; with abs/fixed descendant exactly 1 (2 desc.) | **28; exactly 1, 2 descendants** | = |
| F1′ multicol sole-child W+H | 1 (anchor-position-multicol-017, baked, new=true) | **1, new=true** | = |

The `<tr>` protection claim was checked in source, not taken on faith: Swift `.block` evaluates `tableTrackPlan()` (`:1638`) before
`verticalBlockFlowZ2()` (`:1823`) and `tableTrackPlan` keeps the tracks under `border-collapse: collapse` (spacing → 0, tracks stay);
Compose folds an undeclared `<table>` with a consumable row list to `DisplayType.TABLE` (`:1658-1664`) — `Position: ABSOLUTE` on
td-different-subpixel's table does not gate it. L11's `hunk-for-L3-1.patch` still `git apply --check`s on L3's edited
`ContentsUnboxingTests.swift`.

## 4. Seam patches (`git apply --check` on the stated base, then applied under the per-file lock)

- Base: `git rev-parse HEAD:<file>` = `c25311b4` (ComponentRenderer.kt), `97993624` (ComponentRenderer.swift), `b6935082`
  (VerticalMulticolPlan.swift) — the `index` lines of the three patches. `git apply --cached --check` against a scratch index of HEAD:
  seam-1 OK, seam-2 OK, hunk OK. Patch shas `848bccea…` / `83defc39…` = the note's.
- **seam-1** — lock `wave52-lock/ComponentRenderer.kt` taken 17:43:58, sha before `c4369165af43797d…`, applied (`:2791`
  `BakedLayoutSignature.bakedPhysicalBox(child.properties)`), Compose compiled, XML: MultiColumnInheritedWritingModeTest 5,
  MulticolSpannerContainingBlockTest 14, MulticolSpannerFlowTest 23, VerticalMulticolBridgeTest 2, ContentsUnboxingTest 11,
  SeamReachabilityTest 3, VerticalBlockFlowMathTest 4, VerticalBlockFlowSeamGuardTest 6, CanvasRootHoistTest 25 = **93/0**;
  restored via `git show HEAD:`, sha after `c4369165af43797d…` (equal), `git diff --quiet` clean, lock released 17:44:08.
- **seam-2 + hunk-for-orchestrator-1** (applied together; locks `wave52-lock/ComponentRenderer.swift` + `wave52-lock/VerticalMulticolPlan.swift`
  taken 17:59:17 after queueing behind two other lanes): shas before `d2afc70d…c4ee63` / `f95f7d1d…2f838`, both applied
  (`ComponentRenderer.swift:4368`, `VerticalMulticolPlan.swift:59` read the predicate), Catalyst build in a private derived-data dir.
  ContentsUnboxingTests 11 · FixedHoistTests 14 · MulticolSpannerContainingBlockTests 14 · MulticolSpannerFlowTests 26 ·
  MulticolSpannerHoistRasterTests 3 · VerticalBlockFlowSeamGuardTests 5 · VerticalTextFlowTests 21 — all green; **but
  `VerticalFragmentGeometryTests.testVerticalPlanDeclinesBakedLayout` FAILS** (`VerticalFragmentGeometryTests.swift:154`,
  XCTAssertNil got a 3-fragment plan): 103 run, 1 failure, `** TEST FAILED **`. Both files restored (`git show HEAD:`), shas after
  equal, `git diff --quiet` clean, locks released 18:01:10. Attribution: SK-M6's run (§2) executed `VerticalFragmentGeometryTests`
  on a clean `VerticalMulticolPlan.swift` → 9/0, so the red row is the HUNK's, not seam-2's. The pin feeds
  `{BlockSize 350, Width 450, Height 20}` — the wave-47 "baked" shape with no BoxSizing — and expects the decline; the hunk's predicate
  reads it as authored and builds the plan. **seam-2 alone is verified** (every pin of this lane green with it applied, matching the
  lane's `_seam2-verify.log` 73/0); **the hunk is NOT** — the note's own "not compiled/run" disclosure hides a red Catalyst pin.

## 5. PNG check of every predicted-flip cell (opened: ref + wave51-fix web/ios/android) and scorer-function replay

Opened by eye: display-contents-float-001 (ref = "PASS" on white; ios/android = full-width red 358×20 bar behind "PASS");
flexbox_align-items-stretch-writing-modes (ref = one 250×100 green; ios/android = 150 green + 100×100 red at x 166–265);
abspos-containing-block-outside-spanner (ref = green at (16,16) and (274,484); ios = BOTH red probes uncovered + a green 100 px
below the paragraph; android = ref picture). My own PIL probe reproduces the lane's pixel table exactly (red 6 941 / 6 958,
≠ref 8 167 / 8 396; red 10 000 bbox (166,88)-(265,187), ≠ref 11 179 / 11 476; red 20 000, ≠ref 30 481 / android 609; web ≠ref 0 ×3).

Then the predicted post-fix pictures, scored with the scorer's OWN exported `diffWebVsRef` / `computeColorFailed` /
`computeWptPass` from `tools/titan/inject-wpt-block.mjs` (which first reproduces the wave51-fix verdicts exactly):

| cell | picture | ssim | colorFailed | verdict |
|---|---|---|---|---|
| T1 flexbox_align-items-stretch android | wave51-fix as captured | 0.998 | false | P (reproduced) |
| T1 ios | wave51-fix as captured | 0.999 | **true** | f (reproduced) |
| T1 android | post-fix, items 100×50 UNSTRETCHED (red 200×50 lower band) | 0.9979 | false | **P — stays P** |
| T1 ios | post-fix UNSTRETCHED (the falsifier) | 0.999 | true | f |
| T1 ios | post-fix STRETCHED to the 100-px line | 0.9991 | false | **P** |
| T2 display-contents-float-001 ios / android | bar removed, glyphs kept | 0.9987 / 0.9979 | false | **P / P** |
| T3 outside-spanner ios | greens hoisted to the ICB corners, mis-anchored green gone | 0.9994 | false | **P** |
| T3 ios | FALSIFIER: bottom-right green 16 px up-left (≈2 944 red px visible) | 0.9712 | false | **P — degenerate** |

**T1 iOS stretch precondition — executed (the note left it unverified).** A TEMPORARY test file
(`SkepticL3T1ProbeTests.swift`, created and deleted inside one locked run, 18:09:03–18:10:50, `wave52-lock/ComponentRenderer.swift`)
decoded the VERBATIM wave51-fix IR of `flexbox_align-items-stretch-writing-modes` through `IRDocument` and rendered it through the
`MulticolSpannerHoistRasterTests` composed-canvas replica (390 wide, 16-px frame, `wptCaptureMode`), counting pixels:
- HEAD (no seam): red **10 000** bbox (166,56)-(265,155), green 15 000 bbox (16,56)-(165,155) — the wave51-fix iOS picture exactly
  (32 px higher: the replica has no paragraph UA margins);
- seam-2 applied: red **0**, green **25 000** bbox (16,56)-(265,155) — the ref's single 250×100 green rectangle.
`VerticalBlockFlowLayout.sizeThatFits` returns `proposal.height ?? maxH`, so CSSFlexLayout's stretch proposal (lineCross 100) is
honoured. Seam restored (sha `d2afc70d…` before = after), temp file removed, lock released. T1 iOS therefore deserves HIGH on the
replica (the real canvas adds only the paragraph offset); the note's MED was conservative, not wrong.

So: T2 HIGH and T3 MED-HIGH are supported, T1 iOS is supported by an executed render; the Android T1 "stays P" claim — unmeasured in the note — measures as P 0.9979 under the no-stretch shape
`FlexCrossStretch.kt` (auto container cross size ⇒ stand down) predicts. The T3 falsifier row is the one caveat: a picture with
~2 900 visible red pixels still scores P, so the T3 gate flip is NOT evidence by itself — it must be PNG-checked for zero red
(the raster pin asserts 0 red on the replica; the real canvas is what the gate captures).

Every PASSING native cell on an L3 carrier document (wave51-fix manifests, `browserRef.diffs`): flexbox_align-items-stretch android
P 0.998 (stays P — simulated above); td-different-subpixel ios P 0.9638 / android P 0.9609 (row consumed by the table path on both
natives — source-checked §3); flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse ios P 0.9721 / android P 0.9713 (FLEX
container: Compose FLEX_COLUMN branch / Swift `.flex` case — the block seam is in the `else ->` / `.block` arm only); the 23 baked
containers (anchor-position-multicol family, input-range-zero-inline-size) stay guarded. Every other carrier cell is f today
(abs-pos-border-offset-003 0.9342/0.9262, baseline-vertical 0.6209/0.5727, collapsed-border-* ≈0.85, gaps-006 0.83) — movers that
cannot be LOST. Blast radius is therefore measured, not claimed.

## 6. Ownership / hygiene

- `git status` + `git diff --name-only` + a grep of every changed/untracked file for L3 fingerprints: L3 touched exactly
  `ContentsUnboxing.{kt,swift}`, `ContentsUnboxingTest(s)`, `MulticolSpannerFlow.kt`, `FixedHoist.swift`, new
  `BakedLayoutSignature.{kt,swift}`, `MulticolSpannerContainingBlock.swift`, `VerticalBlockFlowSeamGuardTest(s)`,
  `MulticolSpannerContainingBlockTests.swift`, `MulticolSpannerHoistRasterTests.swift`, and this directory. Two are outside the
  literal `own:` list (`BakedLayoutSignature.swift` — the "Swift twin" §2 Fix names; `MulticolSpannerHoistRasterTests.swift`) —
  declared in the note, new, owned by nobody else: not foreign edits. Seam files and `VerticalMulticolPlan.swift` are clean vs HEAD.
  (`GlobalExtractor.swift` mentions L3 — it is L11's file talking about L3's test file; not an L3 edit.)
- New files ≤ 200 lines (88 / 82 / 180 / 166 / 181 / 147 / 131); every statement commented. Modified files crossed the 200 target:
  `ContentsUnboxing.kt` 200→214, `ContentsUnboxing.swift` 189→206 (tests 187→228, 193→226); `FixedHoist.swift` 480→538 (already past ~300).
- No test-name carve-out in any L3 code path (test names appear in comments only). Ring-fence: `backdrop-filter-basic-blur` is not
  an F1/F2/F3 carrier (census) and no L3 file names it.
- No device A/B is staged by this lane (F1–F3 are gate-measured), so the device-A/B hash sentence is not owed.
- No silent fallthrough added: the new decline paths keep their existing logs; `transformChainRestartUnimplemented` names the one
  known limit on Swift as Compose does.

## 7. Defects

1. **MUST-FIX — `hunk-for-orchestrator-1.patch` reddens the Catalyst suite.** Applied on HEAD (with seam-2), it fails
   `VerticalFragmentGeometryTests.testVerticalPlanDeclinesBakedLayout` (`runtimes/swiftui/Tests/StyleConverterRuntimeTests/VerticalFragmentGeometryTests.swift:145-159`):
   that pin's "baked" child is `{BlockSize, Width, Height}` — no BoxSizing, no band — so the new predicate calls it authored and
   `verticalFragmentPlan` builds a 3-fragment plan where the pin expects nil. The same file passes 9/0 with the hunk NOT applied
   (SK-M6 run), so the hunk is the cause. The note hands the hunk to the orchestrator as "parity, zero corpus movement" and
   discloses only "not compiled/run" — PLAN §0 requires a handed-over patch to be verified ("applies clean … its own pins green
   with the patch applied"). Fix: ship the hunk WITH that pin row rewritten onto the real signature (e.g. the verbatim
   anchor-position-multicol-017 sole child, or `+BoxSizing +PaddingTop`) plus an authored-W+H row expecting a plan, re-run
   `VerticalFragmentGeometryTests` with the hunk applied — or withdraw the hunk. Until then the natives' rule tables DISAGREE:
   Compose `ChildSpec.bakedPhysicalSize` (in-tree, L3's `MulticolSpannerFlow.kt`) already uses the new predicate, the Swift
   twin `VerticalMulticolPlan` still uses the two-property heuristic (corpus effect 0 — the one carrier, -017, is baked — but the
   "can never disagree" claim in `MulticolSpannerFlow.kt`'s new comment is false on the current tree).
2. should-fix — **the seam wiring is unpinned.** No test anywhere references `anyBakedChild` / `verticalBlockFlowZ2` / the seam
   call (grep over all four test trees + tools: only the two predicate test files). Executed: every L3 pin is green on the
   UN-patched tree (§1), i.e. if the orchestrator drops or mis-merges seam-1/seam-2 (nine lanes deliver hunks into the same two
   files) nothing goes red and T1 simply does not flip. A source pin shipped inside each seam patch (assert the guard expression
   reads `BakedLayoutSignature.bakedPhysicalBox(child.properties)`) closes it.
3. should-fix — **T3's gate flip is not self-certifying.** The scorer passes a falsified T3 picture (bottom-right green 16 px
   off, ~2 900 red px showing) at P 0.9712 (§5), so a `P` on `abspos-containing-block-outside-spanner ios` must be PNG-checked for
   zero red before it is written as a fix; the note raises the cell to MED-HIGH on the replica raster pin without saying so.
4. nit — comment counts: `BakedLayoutSignature.{kt,swift}` headers, the `MulticolSpannerFlow.kt` doc and both seam-patch comments
   say "all 12 baked containers (9 docs)" — the OWN-mode count; both seams read the USED mode, where the number is 23 containers in
   9 docs (census `f1Used`, reproduced here). The claim (all baked stay guarded) holds under both.
5. nit — sizes: `ContentsUnboxing.kt` 200→214, `ContentsUnboxing.swift` 189→206, `ContentsUnboxingTest.kt` 187→228,
   `ContentsUnboxingTests.swift` 193→226 crossed the 200 target; `FixedHoist.swift` 480→538 (already past ~300, grew 58).
6. nit — `MulticolSpannerHoistRasterTests` calls its roots "VERBATIM" but drops the ids and `meta` (`sourceTag: br`, `role:
   line-break`, `sourceTag: p`); property payloads are verbatim, the paragraph's UA-margin path is not exercised (does not affect
   the two ICB-corner assertions).
7. nit — `tools/titan/results/wave52-failure-ink/` is untracked (PLAN §0 "committed the moment it exists"); every wave52 lane dir is
   in the same state, so this is an orchestrator commit, not an L3 edit.
8. nit — F3's "positioned box OUTSIDE the multicol" branch (`hoistsToInitialContainingBlock` → false, the child stays in the
   spanner's own overlay, which is NOT its CSS 2.1 §10.1 containing block) is documented in a doc comment only — no
   `PropertyTracker` record and no greppable constant like `transformChainRestartUnimplemented`. No corpus witness (census F3 = 1),
   so zero cells; the house rule asks for a log or a TODO marker on a known-wrong path.

## 8. Closing state (18:11)

HEAD still `7d9c22a7`; nothing committed, staged or stashed by the skeptic. L3 files byte-identical to before the audit
(`BakedLayoutSignature.kt` `3aa0f815…`, `ContentsUnboxing.kt` `3b3578f9…`, `MulticolSpannerContainingBlock.swift` `83289165…`);
`ComponentRenderer.{kt,swift}` and `VerticalMulticolPlan.swift` restored to HEAD bytes after every application; the temporary
probe test is gone; every lock this skeptic took was released by its own trap. mustFixBeforeGate: **yes** — only for the hand-off
hunk (fix it with its pin row, or withdraw it); seam-1, seam-2 and the in-tree L3 changes are fit to land as delivered.

## Re-verify (2026-10-05 19:13–19:27, after the lane's fix pass)

Scope: the ONE must-fix (§7.1, `hunk-for-orchestrator-1.patch` reddens `VerticalFragmentGeometryTests.testVerticalPlanDeclinesBakedLayout`),
then a regression hunt over everything the fix pass touched (the hunk's three ride-along test edits, the re-cut seam-1/seam-2, the
F3 breadcrumb restructure). Tree: shared worktree, HEAD `7d9c22a7`, other lanes' uncommitted work present. Everything below was
executed by this skeptic; scripts lived in the session scratchpad (`…/scratchpad/skrv/`) and are described here in prose so the
record does not depend on it. Nothing committed, staged or stashed; every lock taken was released by its own trap.

### RV-0 Inputs

- Patch shas: hunk `4f64d533…` (fix-pass re-cut; 4 paths: `VerticalMulticolPlan.swift`, `VerticalFragmentGeometryTests.swift`,
  `Wave12BoxSizingBasisTests.swift`, NEW `VerticalMulticolBakeGateTests.swift`), seam-1 `3e3c446e…`, seam-2 `43809f52…` (= the note's §5/§11).
- `index` lines = HEAD blobs (`b6935082`, `8472f392`, `7ee56f1e`). `git apply --cached --check` on a scratch index read from HEAD
  (`GIT_INDEX_FILE`, the real index untouched): hunk OK, seam-1 OK, seam-2 OK; hunk-then-seam-2 and seam-2-then-hunk both apply.
- None of the four hunk paths appears in PLAN.md §2 or in any other lane's `*.patch` (grep) — the "unowned, orchestrator hunk" claim holds.

### RV-1 The must-fix, executed: FIXED

Shared tree, locks `wave52-lock/{VerticalMulticolPlan.swift, VerticalFragmentGeometryTests.swift, Wave12BoxSizingBasisTests.swift,
VerticalMulticolBakeGateTests.swift, VerticalBlockFlowSeamWiringTests.swift, ComponentRenderer.swift}` taken 19:18:19 (renderer
confirmed at HEAD bytes `d2afc70d…` first), Catalyst, private `-derivedDataPath` prewarmed OUTSIDE the locks (19:15–19:17).
Shas before: plan `f95f7d1d…`, VFG `ce6d5c0f…`, Wave12 `34a018e6…`, renderer `d2afc70d…`; hunk applied → plan `b8f37ca7…` (= the lane's).

- **A — hunk alone, 25 suites chosen independently** (every Swift test file that calls `fragmentPlan`/`verticalFragmentPlan`, every
  file mentioning multicol, every ColumnCount×WritingMode fixture, plus L3's ContentsUnboxing/FixedHoist): VerticalMulticolBakeGateTests 3 ·
  VerticalFragmentGeometryTests 9 · Wave12BoxSizingBasisTests 11 · FragmentGeometryTests · MulticolClonePlan/CloneGeometry · MulticolSpannerFlow 26 ·
  VerticalBlockFlowSeamGuard 5 · MulticolFloatStripSeam/FloatStrip/DiscardThreading/Distribution/Math/LineSnap/RunFragment ·
  MulticolSpannerContainingBlock 14 · MulticolSpannerHoistRaster 3 · WPTBlockChildFill · FidelityWave3 · Placement · LogicalDirections ·
  StaticEmMargins · LhUnitLineHeight · ContentsUnboxing 11 · FixedHoist 14 = **258 tests, 0 failures, TEST SUCCEEDED** (19:19:23).
  The skeptic's red row (`VerticalFragmentGeometryTests.swift:154` at HEAD numbering) is GREEN with the hunk.
- **D — hunk + RE-CUT seam-2** (seam guard at `ComponentRenderer.swift:4369`), same 25 + VerticalBlockFlowSeamWiringTests 2 +
  VerticalTextFlowTests 21 = **281 tests, 0 failures** (19:20:46).
- Restore 19:20:47 via `git show HEAD:` for the 4 tracked paths, both new files removed: shas after = before (plan `f95f7d1d…`, VFG
  `ce6d5c0f…`, Wave12 `34a018e6…`, renderer `d2afc70d…`), `git diff --quiet` clean; six locks released 19:20:47.

### RV-2 Mutations re-executed independently (shared tree, locks on the 4 hunk paths 19:21:08–19:25:44; each restored to the
hunk-applied bytes, sha-checked, then everything to HEAD bytes, sha-checked, clean)

| id | edit (with the hunk applied) | result | lane twin |
|---|---|---|---|
| SK-H1 | gate → inline `contains(Width) && contains(Height)` (sed, not the HEAD blob) | BakeGate **2/3 FAIL** (`:119` authored W+H, `:141` BoxSizing-no-band); VFG 9/0, Wave12 11/0 green | H1 — same outcome |
| SK-H2 | `blockSlot = childSize.height` (block-extent choice dropped) — **not executed by the lane** | BakeGate **2/3 FAIL** (`:122` translations `[80.0]` ≠ the 5-band V-table; `:143` 1 fragment ≠ 5); VFG/Wave12 green — the new file is the only pin of the `hasPhysicalWidth` block-extent rule | — |
| SK-H3 | gate → `if false` | VFG `:159` (the REWRITTEN baked row) FAIL · BakeGate `:106` verbatim -017 FAIL · Wave12 `:401/:404` (the REWRITTEN TallChild) FAIL — the rewritten rows still route through the gate, not vacuous | H2 — same outcome |
| SK-H4 | the ORIGINAL must-fix repro on the re-cut hunk: source hunk kept, VFG at HEAD bytes | `VerticalFragmentGeometryTests.swift:154` XCTAssertNil **FAIL** (the defect exactly) → the row rewrite is what fixes it | H3 (Wave12 twin) |

Restores: plan `b8f37ca7…` after H1/H2/H3, VFG `ae42aef6…` after H4; final `f95f7d1d…` / `ce6d5c0f…` / `34a018e6…`, new file absent, clean.
(The renderer was HEAD `d2afc70d…` at the start of every build; another lane applied its own transient seam during H4 —
`17724601…` at H4's end — which cannot affect H4's pure `verticalFragmentPlan` assertion.)

### RV-3 Census — my OWN script (python; never reads census.json/census.mjs)

All 1435 wave51-fix per-test-IR docs; multicol = numeric ColumnCount ≥ 1 or px ColumnWidth; USED writing mode = nearest
self-or-ancestor WritingMode via `slot.parent`; roles = the `rolesFor` order (ABSOLUTE/FIXED → static, ColumnSpan ALL → spanner, else flow);
the call site's `siblingCount = flowCount` under capture. Result: 174 multicol containers, 18 under a vertical used mode, 11 sole-flow
candidates, **1 old-guarded child** (`anchor-position-multicol-017__1__0-744`, flow), new predicate **true** → **0 movers**; with the
sole-flow gate removed entirely (superset) still **0** movers. = the lane's `f1swiftUsed`. The new test's "VERBATIM" -017 child equals
the IR's 30 properties exactly (JSON compare) and the container geometry it assumes (vertical-rl, ColumnCount 5, gap 0, 100×100
content-box) is the IR's.

### RV-4 Regression hunt over the rest of the fix pass

- **Compose** (`gradle-xml-redirect` into a private XML dir, live runs — XML timestamps 17:21Z): in-tree L3 pins, no seam —
  MultiColumnInheritedWritingMode 5 · MulticolSpannerContainingBlock 14 · MulticolSpannerFlow 23 · VerticalMulticolBridge 2 · ContentsUnboxing 11 ·
  SeamReachability 3 · VerticalBlockFlowMath 4 · VerticalBlockFlowSeamGuard 6 · CanvasRootHoist 25 = **93/0**. Re-cut **seam-1** under
  `wave52-lock/ComponentRenderer.kt` (19:21:14–19:21:22; sha before `c4369165…`, guard at `:2792`): same + VerticalBlockFlowSeamWiringTest 3 =
  **96/0**; restored sha `c4369165…` (equal), wiring file removed, clean, lock released.
- **Swift in-tree L3 pins** ride in A (all green): the F3 restructure of `hoistsToInitialContainingBlock` (`MulticolSpannerContainingBlock.swift`,
  now 195 lines, sha `927b1475…`) is boolean-equivalent to the first-pass predicate (guards underSpanner ∧ ¬transformed ∧ ABSOLUTE ∧ inset,
  then `hasPositionedAncestor` → log + false, else true) and adds only the `multicol-spanner-abspos-outer-cb` breadcrumb.
- **Ownership**: every changed/untracked file carrying an L3 fingerprint is in L3's own list or the two declared new files (§6), plus
  L11's `GlobalExtractor.swift` (comment about L3's test file — L11's edit). The four hunk paths and both seam files are at HEAD bytes in
  the shared tree after this audit; no `VerticalMulticolBakeGateTests.swift` / `VerticalBlockFlowSeamWiring*` file is left in the tree.
- New file in the hunk: `VerticalMulticolBakeGateTests.swift` 146 lines (≤ 200), every test and fixture block commented; no test-name
  carve-out in the source hunk; no device A/B staged (hash sentence not owed).

### RV-5 PNG check (the hunk has no predicted flip; its one at-risk carrier, -017, opened)

ref: one 100×100 green, 0 red. wave51-fix **ios**: red **7 800 px** bbox (26,88)-(115,187), green 9 500; **android**: red **8 000 px**
bbox (16,108)-(115,187), green 3 600 — yet the manifest scores them **P 0.9511 / P 0.9823** (colorFailed false). The hunk keeps this cell
on the identical frozen path (new guard true, RV-3), so no picture moves; but the "PASSES today on that frozen render" justification
(HEAD doc text the hunk keeps, and the new test's "ios P 0.9511 / android P 0.9823 today") rests on two DEGENERATE passes with ~8 000 red px
showing — nit RV-N1.

### RV-6 Verdict

**Must-fix §7.1: CLOSED.** Executed: the re-cut hunk applies on its stated base, its pins and every reachable suite are green alone (258/0)
and with seam-2 (281/0); the original red row reproduces only when its rewrite is withheld (SK-H4); the rewritten rows are load-bearing
(SK-H3); the new pins catch the gate revert (SK-H1) and the block-extent rule (SK-H2, a mutation the lane did not run). No regression
found in the fix pass (Compose 93/0 → 96/0 with seam-1; Swift L3 pins green). New nits only:
- RV-N1 — the bake gate's protection rationale cites -017's P verdicts, which are degenerate (7 800 / 8 000 red px); say so in the
  comment or drop the "PASSES" wording. No render change.
- RV-N2 — the hunk compiles only with L3's untracked `Renderer/BakedLayoutSignature.swift`; its header says "Base: HEAD 7d9c22a7 … no
  ordering dependency on any other patch" without naming that prerequisite (it lands with L3's own PR, so harmless if applied there).
- RV-N3 — after the Wave12 fixture rewrite, no RASTER pin exercises an authored Width+Height child that now takes the wave-47 vertical
  plan (only the pure-plan rows in the new file); zero corpus carriers (RV-3), so not gate-visible.
mustFixBeforeGate: **no**.

# Wave 54 · L4 oof-layout — skeptic report

Tree: the shared worktree, branch `campaign/applier-campaign`, **HEAD 029139fd** (the plan's fix-round-3 commit; the lane
note still says 31b76944 — every file this lane touches is byte-identical across db6e8aa0 / 31b76944 / 029139fd, and
`seam-1.patch` and `hunk-for-orchestrator-1.patch` both pass `git apply --check` on 029139fd).
Every repro below was EXECUTED by this skeptic with its own scripts under `skeptic/` (never the lane's runners).
Owned-file sha256 at skeptic start = at end (`skeptic/owned-all.{start,end}.sha256`, 21 files, `diff` empty); seam file
clean; `tools/titan/runs/wave54-lock/` empty.

## Verdict: MIXED

The lane's runtime work holds: every pin it claims is red under its mutation and green on restored bytes, on both natives,
and my four extra mutations of untested-looking arms (RC1 FIXED arm ×2 natives, "auto" counts as declared ×2 natives,
will-change clause, strict-positive gaps ×2) all go red too. My independent censuses reproduce its blast radius exactly
(OOF 9 android / 6 iOS; CBB 14 android = the plan's 11 + the 3 semi-replaced-stretch the lane found; GAP 033 only), so
the radius is NOT under-reported by the lane. The open items are outside the lane's owned code but block a clean gate:
the plan has not absorbed the lane's CBB radius (hunk unapplied), the orchestrator's `land-units.sh` composes the OOF
units so that OOF-ios cannot stand alone after an OOF-android revert, and one predicted HIGH/MED-HIGH flip
(contain-content-011 android) would be a DEGENERATE pass counted as a gain.

## Repros (executed)

### A. Baselines
- Compose focused (`skeptic/kt.sh`, the PLAN §2 L4 filter): **124 tests, 0 failures, 0 skipped** (corpus pins ran).
- Catalyst (`skeptic/sw.sh`, OutOfFlowContainingBlockTests FixedHoistTests TransformContainingBlockTests
  GapDecorationSegmentsTests GapDecorationBandsTests GapDecorationGrammarTests): **"Executed 75 tests, with 0 failures"**,
  `testP3TheCorpusMovesInExactlyTheSixOOFiosCarriers` passed (4.1 s, not skipped).
- Final runs after all mutations: Compose 124/0, Catalyst 75/0 (`runs/FINAL-*`).

### B. Mutation replays (`skeptic/smut.sh`; literals `skeptic/lit/`; logs `skeptic/runs/<label>.mut.txt`) — every row restored=OK (before = after sha256)

| label | mutation | file sha256 (before = after) | result |
|---|---|---|---|
| K-Ma | `"Contain" -> false` | OOF.kt ed0d29ac7577 | RED 6/124 (P1 contain, P1 positioned, P2 strict, P2 content, P3 inline, P3/P4 corpus) |
| K-Mb | FIXED arm drops `declaresAnyInset` | CanvasRootHoist.kt 367775501a88 | RED 3/124 |
| K-Mc | drop "not itself absolute/fixed" | OOF.kt | RED 2/124 |
| K-Me | revert the :232 OR | CanvasRootHoist.kt | RED 4/124 |
| **K-X1** (new) | RC1 `PositionType.FIXED -> false` | CanvasRootHoist.kt | RED 2/124 (P2 no-inset, P3/P4 corpus) |
| **K-X2** (new) | `declaresAnyInset` counts `"auto"` | OOF.kt | RED 1/124 |
| **K-X3** (new) | `"WillChange" -> false` | OOF.kt | RED 1/124 |
| **K-X4** (new) | filter/backdrop-filter OBJECT leaf → false | OOF.kt | **GREEN** — untested branch (nit 4) |
| K-CBB1 | WPT always subtracts | ContainingBlockBands.kt f4bb43ff3ce0 | RED 3/124 (incl. the CBB census pin) |
| K-CBB2 | dark stage never subtracts | same | RED 3/124 |
| K-CBB3 | WPT never subtracts (ignores declared border-box) | same | RED 1/124 |
| **K-X9** (new) | DynamicValueResolver one condition → `if (false)` | DynamicValueResolver.kt 19b15addf916 | RED 3/124 |
| K-G1 / K-G2 / K-G3 | main gaps `!isEmpty` / row segments `isEmpty` / `isPositionable = true` | GapDecoration{Lines,Segments,Geometry}.kt | RED 2 / 1 / 1 |
| **K-X8** (new) | `isPositionable = end > start` | GapDecorationGeometry.kt ce69eb46626e | RED 2/124 |
| S-Ma / S-Mb / S-Mc / S-Md | Swift twins (contain→false; fixed strip drops inset; positioned clause; revert :321) | OOF.swift 2682c94c607c, FixedHoist.swift e88a6433ec05 | RED 8 / 5 / 2 / 4 of 75 |
| **S-X6** (new) | Swift RC1 `.fixed?: return false` | FixedHoist.swift | RED 3/75 |
| **S-X7** (new) | Swift contain STRING form → false | OOF.swift | **GREEN** — untested branch (nit 4) |
| **S-X10** (new) | Swift `declaresAnyInset` counts "auto" | OOF.swift | RED 1/75 |
| S-SG1 / S-SG2 / S-SG3 | Swift gap twins | GapDecoration{Lines,Segments,Geometry}.swift | RED 2 / 3 / 2 of 75 |
| **S-X11** (new) | Swift `isPositionable = end > start` | GapDecorationGeometry.swift 80a314e0c949 | RED 4/75 |

### C. Seam-1 replay under the lock (`skeptic/seam-replay.sh`, `skeptic/seam-replay.out.txt`)
Lock acquired 20:04:09Z, released 20:04:18Z. Seam sha256 before `da2df0b4cca6…` = HEAD blob; applied `b5921e56f825…`;
focused suite WITH the seam: **125/125 green** (ChildContainingBlockSeamWiringTest 1/1); named argument dropped
(`11a463a1ddf2…`) → wiring pin **RED**; restored from HEAD → `da2df0b4cca6…`; patch-borne test removed; `git status`
clean on both paths. Rule 2b holds: the tree compiles and is green WITHOUT the seam (A).

### D. Census base provenance (both natives, replayed)
- Compose: CanvasRootHoist.kt swapped to HEAD bytes (`53272e0b…`), corpus pin RED, the census it wrote is **byte-equal
  to `census-compose.base.txt`** (1435 lines); restored `367775501a88…`. Re-run on lane bytes: census = `census-compose.after.txt`,
  CBB census = `cbb-census.out.txt` (both byte-equal).
- Swift: FixedHoist.swift swapped to HEAD (`e3393794…`), census in `$DARWIN_USER_TEMP_DIR/oof-census-swift.now.txt`
  **byte-equal to `census-swift.base.txt`**; restored `e88a6433…`.

### E. Independent blast-radius censuses (my scripts; outputs beside them)
- **OOF** (`skeptic/oof_reach.py`, static re-implementation of the rule table from the spec text: FIXED-without-declared-inset,
  new establisher with an out-of-flow descendant / direct child / fixed descendant / spanner abspos):
  android reach **10** = the lane's 9 + `backface-visibility-hidden-004`; iOS reach **7** = the lane's 6 + the same.
  bvh-004 is reachable (a no-inset FIXED box) but invariant: its parent is ABSOLUTE (positioned) under a transformed root,
  so neither the hoist (tf) nor RC1 (positioned ancestor) changes — the lane's census lines for it are identical base/after
  on both natives. None of the 26 fixedControls / 4 establisher controls is in my reach set.
- **CBB** (`skeptic/cbb_reach.py`, strict mode): 23 documents reachable statically; minus the lane's 14, the 9 extras are
  all invariant on inspection: `block-max-height-004` (the %-width grandchildren sit under an auto-width child, whose own
  block is null in both modes), `cross-fade-target-alpha` (in-flow % sizes go through Compose constraints, not the block),
  `flex-abspos-staticpos-margin-001/-002/-003` (no insets → AbsposAutoMargin identity), `available-size-003/-013`,
  `inline-box-border-vlr-001`, `counter-style disclosure-styles` (vertical text readers — see nit 5). Two blind spots of
  the lane's census were probed explicitly: font-relative / %-expression bands the runtime resolves before
  `childContainingBlock` (`cbb_pct_band.wave53-final.out.txt`: only available-size-003/-013, whose abspos child does not
  stretch) and vertical text read at the RIGHT level (`cbb_vtext_own.py`: 2 own-level readers, both ASCII/U+00AD/U+2010
  runs that take the rotated path, never `VerticalUprightTextFlow`). **No under-report.** Matches the lane's 14 =
  expectations.json `CBB-android` (11) + the 3 semi-replaced-stretch the lane handed off.
- **GAP** (`skeptic/gap_reach.py`): 51 rule-painting containers (46 flex); 10 flex containers have a zero/absent gap on a
  ruled axis (033, 040–043, 045, 047–050); every one but 033 opens positive gaps (space-between/-around/-evenly with free
  space 50 px / 120 px; align-content space-* with 80 px free; 045's within-line gaps via space-between). Multicol rule
  boxes are out of reach (the hook is flex-only). Only negative-margin item in the 46: 027 (column gap 10). **033 only.**
- Fixture net: none of the 9 gate fixtures has a contain/filter/backdrop-filter/will-change box with an out-of-flow child
  (the two composition-test `glass` boxes are ABSOLUTE leaves — withheld), no fixed box, no gap rules.

### F. Scores and pictures
- `cells.mjs` on wave53-final reproduces every number in the note (003 f 0.9405, 011 f 0.9284, bf-cb ios f 0.7955 /
  android f 0.7506, rel-003/-004/change-insets P 0.9594 / 0.9589, sfia P 0.9841 / 0.9835, clip-3 f 0.9574, clip /-2 /-4
  P 0.9649 / 0.9689 / 0.977, autopos ltr P 0.9966, htb-rtl 0.9869, 033 f 0.94 ×2, 006 f 0.8223, semi-replaced android
  button/input/other f 0.9739 / 0.4117 / 0.4396). Carrier pass-today counts reproduce (OOF-android P4 f4 u1; OOF-ios P4 f1 u1;
  CBB P9 f5).
- PNG sheets (`skeptic/sheets/*.png`, ref | web | ios | android): the described geometry holds — 003 / 011 android green
  halves at the canvas top-right over a bare red square; rel-004 / sfia natives green at the canvas origin, red bare;
  autopos-htb-ltr android green 70×80 in the red 100×100; clip-3 android green 160×60; 033 natives no rules at all;
  semi-replaced-input android lime rings 6 px short. 033 ref measured: red x61-70 / x111-120, blue y61-70 / y111-120,
  col x65 red y16-60 / y121-165, 2200 red / 3000 blue px, blue over red — exactly the pins' content-space rects.
- Geometry gate `--lanes L4 --self-test` on wave53-final: **exit 0**, 54 keys, gating 17 all FAIL, control 30 all PASS,
  report 7 all FAIL, unmeasured 0, `self-test: HOLDS` (`skeptic/geometry-gate.wave53-final.L4.selftest.out.txt`).
- The lane's semi-replaced replay re-scores to the same numbers (0.9843 / 0.4555 / 0.4677).
- The plan hunk dry run replayed independently (`skeptic/plan-hunk-dry/`): base copy reproduces the installed
  expectations.json and watchlist.txt byte-for-byte; patched → rc 0, union android 51 → 54, L4 predictions 26 → 29,
  L6 must-not-move 68 → 66, watchlist 775 → 776, the two L6 android lines printed as exclusions.

## Defects, ranked

1. **must-fix (orchestrator; gate-blocking) — the plan does not carry CBB-android's real radius.** `hunk-for-orchestrator-1.patch`
   is unapplied: expectations.json `CBB-android.captures.android` is still 11. My independent census reproduces the 3
   extra android captures (semi-replaced-stretch button / input / other: `top/right/bottom/left: 3px` children of a
   3px-bordered content-box parent, block 144×94 → 150×100). Unamended, R4 at the stage-2 probe reads them as CBB-android
   leaks (revert rule 5 would revert a correct unit) and two are L6 must-not-move lines. The hunk applies clean on 029139fd
   (plan-build.py sha256 70646549… = the hunk's base) and its dry run reproduces. Fix: apply it, re-run plan-build.py,
   restate PLAN §1/§2/§6 counts, re-run watchlist-check.
2. **must-fix (orchestrator) — `land-units.sh` composes OOF-android so that OOF-ios cannot stand alone.** Its OOF-android
   `commit` adds `"$L4"` (the whole lane dir, including `census-swift.base.txt`); the lane note puts that file in OOF-ios
   and the rest of the dir in a separate evidence commit. A `git revert` of OOF-android (revert rule 6 — "a native that
   falsifies on device goes alone", PLAN §9 D1) deletes `census-swift.base.txt`, and OOF-ios's corpus pin then errors on
   every local Catalyst run. Executed: with the file moved aside, `OutOfFlowContainingBlockTests` → "Executed 8 tests,
   with 1 failure (1 unexpected)", `testP3TheCorpusMovesInExactlyTheSixOOFiosCarriers: caught error … Code=260 "The file
   "census-swift.base.txt" …` (`runs/STANDALONE-oofios-without-swift-base.xc.txt`); restored f7aabc60… = before. Fix:
   OOF-android commits `census-compose.base.txt` only, OOF-ios commits `census-swift.base.txt`, and the remaining lane
   evidence lands in its own commit (or with the last L4 unit), exactly as the lane note's unit sections say.
3. **should-fix (honesty) — contain-content-011 android's predicted flip is DEGENERATE.** The test's pass condition is "a
   filled green square, no red **and the number 17**"; the ref shows 17, web / iOS / android all show **25** (sheet
   `contain-content-011.png`). OOF only moves the green square; the 011 geometry key reads green/red boxes only, so a P
   ≈0.985 with GEOMETRY OK would still fail the test's own condition. The lane (and plan) count it among "Expected L4 flips
   at HIGH / MED-HIGH: android +4". It belongs in `stayDegenerateEvenIfPass` (as web / iOS 011 already are de facto), and
   the honest picture-earned count is android +3 (003, clip-3, 033). Keep it as a gating floor if wanted, but never write
   it as a fix.
4. **nit — untested branches.** Mutations that stay green: Compose filter/backdrop-filter OBJECT leaf (`url()` /
   unresolved expression) → false (K-X4); Swift `contain` space-separated STRING form → false (S-X7). Unpinned
   breadcrumbs: `ContainingBlock[dark-stage-content-box-bands]`, `[contain-decode-failed]`,
   `[implied-containment-unclaimed]` (only the positioned-establisher crumb is asserted, Compose only). The
   `betweenLineGaps` edits (Compose `GapDecorationLines.kt:165`, Swift `GapDecorationLines.swift:118`) are behaviourally
   inert — Compose uses the list only as cuts (zero-width cuts are dropped by `union`), Swift has no production caller —
   and no mutation covers them. None reaches a corpus document (no object filter leaf, every Contain is an array).
5. **nit — the lane's CBB census reads two things at a coarser level than the runtime**, in the safe direction here:
   (a) the `vtext` reader is tested on the INCOMING block, but `VerticalUprightTextFlow` reads `LocalContainingBlock`
   inside the text component's OWN provider and only for upright runs — so 006 (ASCII "One"…"Six", own height 70 px, no
   bands) is an over-report; (b) parent sizes / bands are read RAW, so font-relative or %-expression bands are invisible.
   My own-level and non-px-band scans found no reader the lane missed (E).
6. **nit — Swift `FixedHoist.swift:359-361` comment (the fixed-strip note ending at :361)** says a kept no-inset fixed box "paints at its parent's overlay origin
   — its static position (§3.5.3)". That equality holds only when no in-flow sibling precedes it; it holds for every
   corpus carrier (sfia, rel-003: only children).
7. **nit — call sites in oversized files**: the RC1 change is a 5-line `when` (CanvasRootHoist.kt :406-410) / 3-arm
   `switch` (FixedHoist.swift :120-124), not a one-line condition. Disclosed by the lane; logic is trivial.
8. **nit — the corpus pins are a whole-corpus tripwire** keyed to `wave53-final` and a results-dir base file: any future
   intended hoist change anywhere in the corpus turns them red until the base is re-cut. Fine for this wave (no other lane
   touches the walked code); worth a line in the next wave's notes.

## Honesty / labels checked
- Every native "→ P" in the note is labelled a prediction; no replay is called picture-correct. The semi-replaced replay
  is called pixel surgery. OK.
- Apart from defect 3, the HIGH / MED-HIGH targets' pictures would earn their pass if the geometry key prints OK (003,
  clip-3, 033 flips; rel-004 / change-insets / sfia / autopos-ltr / clip / -2 / -4 DEGENERATE → faithful).

## What I could not check
- No device run: every R1/R2 risk (004's in-flow FAIL span inside the new Box branch — the only new Box container with an
  in-flow child; paragraph pitch on the 7 host-off documents; backdrop two-pass; iOS placement of bf-cb's red box) is
  device-only, as the lane says.
- Revert-unit independence was checked by symbol/file disjointness and by the one executed stand-alone failure in
  defect 2; I did not build each unit subset separately.
- `integrate-seams.sh --dry-run` with the other lanes' seam patches (orchestrator's).
- Whether Compose item rects for 033 are exactly touching on device (positionInWindow + integer sizes make it likely; a
  sub-pixel negative gap would drop the rule and leave 033 f).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (shared tree; Gradle via apps/android-harness, xcodebuild Catalyst from the root; no export tree)

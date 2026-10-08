# queue-scout-layout: wave 54 planning miner (wave skill Phase 1)

**Run.** Read-only scout, 2026-10-08. The `wave54-open` gate ran on this tree's devices and builds the whole time, so nothing
here was built, captured or run on a device. Every file this scout wrote is under `tools/titan/results/wave54-plan/`.

**Brief.** Choose the two or three best NEW lanes among the LAYOUT families that wave 53 did not touch. Rank them against
the queue's candidates:
- 0(b), 0(d), 0(j)/(j′), 0(k), 0(l);
- 3(a)/(b), 7, 2(c³).

**Evidence base.**
- The gate of record is `wave53-final` (dev 7cce3b22), with web 1232/1372 · iOS 1125/1362 · Android 1115/1362 over 1435
  per-test IR documents. Those totals come from `score-gate.mjs loadRun`.
- Every cell was read with `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave53-open wave53-final`. Output is in
  `queue-scout-layout.cells.txt` and `queue-scout-layout.cells-rest.txt`. Every cell quoted here is unchanged between those
  two runs.
- Red-square class: `node tools/titan/red-square-census.mjs wave53-final --json queue-scout-layout.red-square.json` found
  164 passing and 62 failing red cells.
- Every PNG named here was opened side by side with its frozen ref.

**Scripts written beside this file.** All of them only read run directories and sources:
- `queue-scout-layout.census.mjs` → `.census.json` and `.census.out.txt`;
- `queue-scout-layout.ink.py`, the strict green/red bbox probe;
- `queue-scout-layout.sim-ssim.mjs` → `.sim-ssim.out.txt`, using the scorer's ssim.js;
- `queue-scout-layout.float-rows-sim.py` with `-score.mjs` → `.float-rows.out.txt`;
- `queue-scout-layout.gaps-scan.py` → `.gaps-scan.out.txt`;
- three geometry probes, each with its `.out.txt`.

## 0. Ranking

| rank | lane (brief file) | queue items | target cells at wave53-final | verdict |
|---|---|---|---|---|
| 1 | **oof-containing-block** (`oof-containing-block.md`) | 0(k) red-square class + 0(j) static position | contain-content-003 android f 0.9405, -011 android f 0.9284, backdrop-filter-containing-block ios f 0.7955 / android f 0.7506; 8 red-ink DEGENERATE passes (position-relative-003/-004, change-insets-inside-strict-containment-nested ios P 0.9594 / android P 0.9589 each; static-fixed-inside-abspos ios P 0.9841 / android P 0.9835) | **GO (M)**: +2 HIGH / MED-HIGH and +2 MED flips; 8 cells DEGENERATE → FAITHFUL; no seam |
| 2 | **compose-wpt-content-box-cb** (`compose-wpt-content-box-cb.md`) | 0(b) neighbourhood (0(b) itself CLOSED) + 0(k) | backdrop-filter-nested-border-radius-clip-3 android f 0.9574; -clip / -2 / -4 android P 0.9649 / 0.9689 / 0.977; abspos-autopos ×6 android P 0.9966 ×5 / 0.9869, all red-ink | **GO-SMALL (S)**: +1 MED-HIGH flip, 9 cells made faithful, 1 seam hunk |
| 3 | **flex-zero-gap-rules** (`flex-zero-gap-rules.md`) | 7(e¹) residual (obligation 0(c)) | flex-gap-decorations-033 ios f 0.94 / android f 0.94 | **GO-SMALL (S)**: +2 HIGH flips (sim 1.0000), no seam |
| 4 | composed-root float rows plus writing-mode static position | 0(j)/(j′) | abs-pos-border-offset-001 ios f 0.4519 / android f 0.4508, -002 f 0.3384 / 0.3383; grid-abspos-staticpos-align-self-safe-001 f 0.9448 / 0.9441 | **NO-GO this wave** (§2) |

Ranks 2 and 3 own disjoint files and can share one lane. Both can also fold into rank 1 as extra units: no file of any of
the three overlaps another, or overlaps the other wave-54 families (lists/markers, Compose table-body forest, web hyphens,
harness label chrome, hyphenate-character). One cell is shared:
- `css-contain/contain-content-004` is a carrier of rank 1, but no flip is predicted;
- it also sits in compose-table-body-cell's D2 radius;
- if it moves, A/B the two lanes separately.

**Expected movement against `wave54-open`** (which should equal wave53-final), counting all three lanes:
- HIGH and MED-HIGH only: iOS +1, Android +4 (rank 1: Android +2; rank 2: Android +1; rank 3: iOS +1, Android +1);
- including MED (rank 1's backdrop-filter-containing-block ×2): iOS +2, Android +5;
- 17 passes become picture-correct;
- lost: 0.

## 1. Summary of the three briefs (full sections — targets, picture, mechanism, census, ownership, probe, predictions, risks — in each file)

### 1.1 oof-containing-block (GO)

**Picture.** On both natives, every green box whose containing block (CB) is the test's subject lands at the canvas origin
or the canvas top-right. The red box the test hides behind it is left bare. Measured by `oof-containing-block.geometry.py`:
- natives: green (16, 16, 115, 115), red 7 200 px;
- ref: green (16, 88, 115, 187), red 0.

**Mechanism.**
- Compose `CanvasRootHoist.shouldHoistToCanvasRoot`: the FIXED arm (:340) is `!hasTransformedAncestor` and ignores insets.
  The only non-positioned CB clause is `establishesTransformContainingBlock` (:222, transform family only).
- iOS `FixedHoist.strippingFixedDescendants` :309 / :347 has the same two gaps.
- `contain: layout|paint|strict|content`, `filter`, `backdrop-filter` and their `will-change` hints never establish a CB.
- An all-auto-inset fixed box is never placed at its static position.

**Fix.** A new `OutOfFlowContainingBlock` rule table on each native, OR-ed into the one choke point each native has. Add an
inset test to the fixed hoist and FIXED to the RC1 static-position class. No seam.

**Census.**
- 142 establisher components in 91 tests; 11 have out-of-flow descendants.
- Carriers: M2 is 10 boxes in 6 tests; M1 is 4 boxes in 3 tests, one test unscored.
- Controls: 26 fixed-carrying tests and 4 positioned-establisher tests.
- Compose host activation flips true → false on exactly the 7 carrier documents.

### 1.2 compose-wpt-content-box-cb (GO-SMALL)

**Picture.** On Android, every `100%` out-of-flow box under a padded or bordered parent is short by exactly that parent's
bands. abspos-autopos measures green 70×80 in a 100×100 red box; nested-clip-3 measures 160×60 against 200×100.

**Mechanism.** `DynamicValueResolver.childContainingBlock` (:207–249) subtracts padding and border from the declared size
unconditionally, while WPT-mode `SizingApplier.effectiveBoxSizing` (:311–316) draws the box content-box. The out-of-flow
`resolveOutOfFlowPercentSizes` path consumes the wrong base.

**Fix.** WPT-gated subtraction, only for an effective `border-box`. One named argument at `ComponentRenderer.kt` :1934 is
delivered as a seam patch.

**Census.** 11 containers in 11 tests: 10 carriers and the inert in-flow control `cross-fade-target-alpha`. One side reader
is watched: `flex-gap-decorations-006`'s vertical text budget.

### 1.3 flex-zero-gap-rules (GO-SMALL)

**Picture.** 033 on both natives has every item pixel-exact and no rule at all. The ref has red 2 200 px and blue 3 000 px.

**Mechanism.** Compose `GapDecorationLines.kt` :138–143 / :156–165 and `GapDecorationSegments.kt` :166–169, and Swift
`GapDecorationLines.swift` :37–41 / :112–118 and `GapDecorationSegments.swift` :139–142, all drop zero-extent gaps.

**Fix.** Keep a zero-extent gap as a rule position.

**Census.** 17 zero-gap flex containers. Only 033 lacks ref decoration ink above 140 px per capture.

## 2. The rest: one line each on why not now

- **0(b) — CLOSED.** `css-position/position-relative-006` is web P 0.999 · iOS P 0.9974 · Android P 0.9967 at
  wave53-final. The live remainder of that neighbourhood is rank 2.
- **0(b′) (iOS twin; not now).** A percentage `top` on a block inside an inline relpos span resolves to 0 on iOS:
  position-relative-002 iOS P 0.9539 R, green at y188–287 against the ref's y88–187 (ink probe). The other carriers are
  multicol fragmentation: block-in-inline-001/-002/-003 iOS f 0.9459, also R. BACKLOG asks for a size-aware census before
  any lane, and this scout did not write one.
- **0(d) — CLOSED.**
  - clip-path-contentBox-1d is P 1 ×3.
  - -1e is web P 0.9998 · iOS P 0.9999 · Android P 0.9997.
- **0(j)/(j′) — rank 4, NO-GO this wave, for two reasons.**
  - The composed canvases stack body-level float roots in a Column / VStack. The only segmenter is the inline-block root
    run (`InlineBlockAtom.rootSegments`), and floats never get one. Census `floatroots`: 9 tests. Two of them are
    lists-family documents (name-case-sensitivity, counter-list-item).
  - Packing the roots alone does not flip. `queue-scout-layout.float-rows-sim.py` re-composites the native boxes at the
    ref's positions:
    - abs-pos-border-offset-001 scores 0.9063 iOS / 0.906 Android, with only 6 of 36 boxes ref-identical;
    - -002 scores 0.8368 / 0.8395, with 0 of 64 boxes ref-identical.
  - The abspos static position inside vertical and rtl parents is ALSO wrong. That is a writing-mode-aware static-position
    lane: the T4 extension of 0(j′).
  - The packer lives in `ScreenshotCaptureScreen.kt` / `CaptureCanvas.swift`, which the harness-label-chrome family edits
    this wave.
  - Stage it for wave 55 as one lane: root float rows plus writing-mode static position. Use the per-box identity count from
    the float-rows simulation as its geometry oracle.
- **0(k) F4 / F5 / F6 (not now).**
  - **F4** is block-in-inline-009, P ×3: web 0.999 · iOS 0.9907 · Android 0.9966. **F5** is s-11-1-1b-008, P ×3: 0.9941 /
    0.9962 / 0.9953. Both are correctness-only, and F5 is table-box clip territory (the Compose table family).
  - **F6** has two failing carriers, and neither failure is the §7.3.1 available size:
    - available-size-011 iOS f 0.9317 / Android f 0.9297. Both natives fit "(without" on the paragraph's first line, which
      the ref wraps, so it is a text-metric failure (PNG). iOS additionally stacks the vertical-rl run.
    - column-fill-balance-orthog-block-001 iOS f 0.944. iOS draws the vertical-rl "TEXT" unrotated inside the multicol
      (PNG), a vertical-in-multicol defect.
- **0(l) — CLOSED in wave 52.**
  - Lane L2 Fix A clips the ICB on all three platforms.
  - The clip/mask pair was the reference, re-frozen under UAMARGIN.
  - No 16-px overrun cell remains in the queue.
- **2(c³) — CLOSED in wave 52** (lane L6 T1). The Compose `KoreanHangulFormal.kt` and its three arms landed. What is left is
  2(c⁴), the RTL markers: counter-suffix iOS P 0.9802 / Android P 0.9547 DEGENERATE. That belongs to the lists/markers
  family (`rtl-marker-bake.md`), and it is not layout.
- **3(a) (NO-GO).** `<br clear>` in multicol has 12 DEGENERATE passes:
  - floats-clear-multicol-000 / -001: web P 0.989 · iOS P 0.9871 · Android P 0.9857;
  - -balancing-000 / -001: 0.987 / 0.9854 / 0.9839.

  The parked bake (`tools/titan/results/wave50-B6/br-clear-fold.patch`) needs the extract-fixture seam AND a device-verified
  `MulticolFloatStripMeasure` §9.5.2 line-box clearance rung. Applied alone it is "ink moved, not ink fixed", with 12
  passing cells at risk.
- **3(b) (NO-GO).** The paint was fixed in wave 52. -003 / -balancing-003 are Android P 0.9968 / P 0.997 and iOS P 0.9981 /
  P 0.9985. The open remainder is the 3–5 px short box. Its broad fix (a declared height must not clamp an overflowing child)
  reaches every explicit-height Compose box and is device-gated, with no cell to gain.
- **7, other than 033 (NO-GO).**
  - 006 is still priced-and-declined: iOS f 0.8266 / Android f 0.8223. Its upside is at most 4 failing cells against 27
    passing ones exposed (7(e²)).
  - 023 is P ×3 at web 0.9737 · iOS 0.9542 · Android 0.9541, yet all three captures lack the decorations its ref paints
    (gaps scan: 1 100 px per native; the PNG shows web lacks them too). Its decorations are set by script and are not on the
    wire, so it is an extraction issue, not a runtime one. It is recorded in §3.

## 3. New findings that are not queue items (seen in the pictures; none staffed here)

- **Negative block margin does not pull later siblings on either native.**
  - Cells: css-flexbox/align-self-003 and align-self-009, iOS P 0.9774 / Android P 0.9767 each, red-flagged (R).
  - Sibling `#top { margin-top: -100px }` paints in place.
  - The following `#bottom` (25 px tall) lands 100 px low, at y263–287 instead of y163–187, so the red flex container shows
    at y163–187: 2 500 px, from `queue-scout-layout.ink.out.txt`, in canvas px.
  - The mechanism is untraced. It needs its own census of negative block margins followed by an in-flow sibling.
- **The iOS abspos static position in rtl flex is 10 px left.**
  - Cells: abspos-autopos-htb-rtl / -vlr-rtl / -vrl-rtl, iOS P 0.9974 each.
  - Green x16–105 with 1 000 red px. That is under red-square-census's 0.5 % bar, so it is not flagged.
  - It is the border-left/right asymmetry, 20 against 10.
- **css-gaps 023 ×3 are degenerate passes.** The decorations are added by script after load and never reach the wire.
- **0(j′)'s safe-alignment residual.**
  - Cells: grid-abspos-staticpos-align-self-safe-001, iOS f 0.9448 / Android f 0.9441.
  - Its four containers are float roots, stacked as in §2.
  - Inside each, the `safe end` teal box is 6 px left of the ref: in the first container the native (dy, dx) offset is
    (5, 2) against the ref's (5, 8) (`queue-scout-layout.grid-safe.out.txt`).

## 4. Artifacts (all under `tools/titan/results/wave54-plan/`)

`queue-scout-layout.{md, census.mjs, census.json, census.out.txt, cells.txt, cells-rest.txt, red-square.json, ink.py, ink.out.txt, grid-safe.py, grid-safe.out.txt, sim-ssim.mjs, sim-ssim.out.txt, float-rows-sim.py, float-rows-score.mjs, float-rows.out.txt, float-rows/, gaps-scan.py, gaps-scan.out.txt}` ·
`oof-containing-block.{md, geometry.py, geometry.out.txt}` ·
`compose-wpt-content-box-cb.{md, geometry.py, geometry.out.txt}` ·
`flex-zero-gap-rules.{md, geometry.py, geometry.out.txt}`. The three probes import `../wave53-plan/geometry_common.py`.

STATUS: COMPLETE

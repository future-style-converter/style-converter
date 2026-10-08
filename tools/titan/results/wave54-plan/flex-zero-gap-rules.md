# flex-zero-gap-rules — wave 54 family brief (from the layout queue scout)

This is a read-only planning brief, written 2026-10-08 while `wave54-open` was running.

**Evidence**
- Gate of record: `wave53-final`.
- Cells: `node tools/titan/results/wave52-gate/cells.mjs 'flex-gap-decorations-033' wave53-open wave53-final` →
  `queue-scout-layout.cells.txt`.
- Pictures (opened): the 033 ref and the three captures; also 040 and 047 as zero-gap passing controls.
- Pixels: `flex-zero-gap-rules.geometry.py` → `.geometry.out.txt`, and `queue-scout-layout.gaps-scan.py` → `.gaps-scan.out.txt`.
- Simulation: `queue-scout-layout.sim-ssim.mjs` (033 rows).
- Census: `queue-scout-layout.census.mjs` (`gapzero`).

**Queue item.** BACKLOG 7(e¹), whose last line is "the natives' missing zero-width-gap decorations are now this item's
runtime work (obligation 0(c))". After the wave-52 re-freeze, the ref paints the rules, so 033 natives are HONEST_FAIL
(`tools/titan/results/wave52-gate/cell-review.json`).

**Verdict in one line: GO-SMALL (S, both natives).** Both natives' flex gap-decoration segment models drop a gap whose
extent is 0 (touching neighbours, touching lines) before any rule is placed. css-gap-decorations paints a rule centred on
the gap even when the gap is 0px; that is 033's title. One rule change per native, with its twin, gives 2 flips. Every
other 033 pixel is already identical to the ref.

---

## 1. Target cells

| cell | wave53-open → wave53-final | picture |
|---|---|---|
| css-gaps/flex/flex-gap-decorations-033 **ios** | f 0.94 → f 0.94 | The eight light item boxes (rows of 50+50+50, 100+50 and 50+50+50 px in a 150-px wrap container, no `gap`) are exact. NO red column rule and NO blue row rule: strict-red 0 px, strict-blue 0 px. |
| css-gaps/flex/flex-gap-decorations-033 **android** | f 0.94 → f 0.94 | same, 0 / 0 px |
| css-gaps/flex/flex-gap-decorations-033 web | P 1 → P 1 | ref-identical: red 2 200 px, bbox x61–120 y16–165; blue 3 000 px, bbox x16–165 y61–120 |

The ref has 10-px red column rules centred on the item boundaries at x61–70 and x111–120 (rows 1 and 3) and x111–120 (row 2,
where `#four` is 100 wide). It has 10-px blue row rules across the full width at y61–70 and y111–120, painted OVER the red
where they cross (scan of ref rows y20 / y63 / y90 / y113 / y140).

## 2. The defect as seen

The rules are entirely absent on both natives. The item rectangles match the ref to the pixel: the gaps scan finds 0
non-rule pixels differing. This is the cleanest possible target. Copying the ref's 5 200 rule pixels onto either native
capture re-scores **1.0000** (`queue-scout-layout.sim-ssim.out.txt`).

## 3. Mechanism (7cce3b22)

**Compose (`runtimes/compose/src/main/java/com/styleconverter/runtime/columns/`)**
- `GapDecorationLines.kt` :138–143: "Overlapping or touching neighbours open no gap → no rule". `GapInterval(a.end, b.start)`
  is kept only `if (!gap.isEmpty)`.
- `betweenLineGaps` :156–165: "A zero/negative inter-line gap paints no rule".
- `GapDecorationSegments.kt` :166–169: `if (gap.isEmpty) continue`.
- `GapDecorationGeometry.kt` :53–56: `isEmpty = end <= start`.

**Swift (`runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/columns/`)**
- `GapDecorationLines.swift` `gapBands` :37–41 and `betweenLineGaps` :112–118: `.filter(\.isPositive)`.
- `GapDecorationSegments.swift` :139–142: "A zero/negative band (touching lines) decorates nothing".

With `column-gap` and `row-gap` both 0, every item-to-item interval and every line-to-line interval is empty, so both
families produce zero segments. Spec: css-gap-decorations-1. A gap decoration is centred in the gap, its width is the
declared rule width, and a 0px gap still positions it. Under `*-rule-break: intersection`, cuts use the crossing GAP's extent,
which is 0, so nothing is cut. That matches the ref's continuous blue over red.

## 4. The fix

- **F1.** Keep a zero-extent interval (end == start) as a gap POSITION in both the item-gap and the line-gap families. The
  band builder `GapIntervals.band(gap, width)` already centres a band of the declared width on any interval.
- **Still drop NEGATIVE extents** (overlapping items or lines).
- **Leave `union()`'s degenerate-member filter as it is**, because a zero gap cuts nothing.
- **Twins byte-parallel.** The Kotlin `isEmpty` stays for "cuts nothing". Add `isPositionable = end >= start` on both sides.
  Do not reuse `isEmpty` for the new rule.
- **Paint order:** row rules over column rules at crossings, as in the 033 ref. Check that against the existing painter order
  before changing anything; the 009 / 034 refs pin the cut semantics.

## 5. Corpus radius

- **`gapzero`: 17 flex containers in 17 tests** carry a rule family on an axis whose gap is 0 or absent. All are
  css-gaps/flex: 007, 008, 017, 018, 027, 033, 040–050.
- **Every one except 033 passes on both natives today** (ios 0.9986–1, android 0.9856–1). In those, the decorated axis's
  neighbours are NOT touching:
  - `justify-content: space-between` opens the gap (040);
  - the decorated axis's gap is non-zero (007/008/017/018/027/044/045/046/050);
  - the lines are separated by `align-content` (047–049).
- **The gaps scan of all 48 css-gaps refs** counts the ref saturated ink each native capture lacks. Only three tests exceed
  140 px:
  - 033: 5 200 / 5 200, the target;
  - 006: 8 000 / 8 000, the priced-and-declined writing-mode transpose 7(e²);
  - 023: 1 100 / 1 100, scripted decorations that are not on the wire; web lacks them too.

  The rest are 66–140 px of anti-aliasing (014, 029, 030, 040, 043, 045, 046 android, 050).
- **Predicted movers: 033 ×2 only.** Any other css-gaps cell that moves must move UP and by ≤ 0.002, or it is a finding.

## 6. Ownership

**Owned (all under the 200-line target; edits are conditions plus one property each):**
- Compose `columns/GapDecorationLines.kt` (166 lines), `columns/GapDecorationSegments.kt` (188), `columns/GapDecorationGeometry.kt`
  (170);
- Swift `GapDecorationLines.swift` (119), `GapDecorationSegments.swift` (232: call site only), `GapDecorationGeometry.swift` (101).

**Pins:**
- the existing `GapDecoration*Test.kt` / `GapDecoration*Tests.swift` stay green;
- new rows built from 033's verbatim per-test IR item rects:
  - 5 column-rule segments: 2 + 1 + 2, at x 45–55 / 95–105 content-relative;
  - 2 row-rule segments, full width.

**Seams:** none. **Disjoint** from every other wave-54 family. The gap modules were last owned by wave 52 lane L10 and no
wave-54 brief names them.

## 7. Geometry probe — `tools/titan/results/wave54-plan/flex-zero-gap-rules.geometry.py [run]`

The rule: strict-red and strict-blue counts equal the ref's ±2 %, their bboxes ±1 px, and the red runs on row y20 equal the
ref's `[(61, 70), (111, 120)]`. At wave53-final it prints ref / web `→ GEOMETRY OK` and ios / android
`→ GEOMETRY WRONG (redPx 0 vs ref 2200)`. After the lane, both natives must print `→ GEOMETRY OK`.

## 8. Predictions

| cell | from → to | confidence | floor |
|---|---|---|---|
| flex-gap-decorations-033 ios | f 0.94 → **P ≈1.000** (sim 1.0000) | HIGH | 0.99 |
| flex-gap-decorations-033 android | f 0.94 → **P ≈1.000** (sim 1.0000) | HIGH | 0.99 |

**Must not move:**
- the other 47 css-gaps tests ×3, at their wave53-final scores. Upward ≤ 0.002 on a non-033 cell is a report, not a gain;
- every web cell: the web runtime emits the longhands and Chromium paints them.

## 9. Risks

- **R1 — a touching-neighbour pair in a passing test now gains a rule** the ref also has. That is correct, but unpredicted.
  The gaps scan bounds it at ≤ 140 px per capture.
- **R2 — rounding on Compose's pixel-snapped rule rects** (7(a′) closed in wave 52) at a 0-extent gap. Pin the snapped rect
  for 033's x = 50 / 100 boundaries.
- **R3 — `GapDecorationBands` §9.4 stretch lines.** With a 0 cross gap, a stretched line box can touch its neighbour exactly.
  That is still a 0-extent interval, the same rule.

**Recommendation: GO-SMALL**, effort S. It gives 2 HIGH flips, has no seam, and its radius is census-bounded. It can share a
lane with compose-wpt-content-box-cb, which owns disjoint files.

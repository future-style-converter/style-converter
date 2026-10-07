# wave53 L4 · float-avoid — SKEPTIC report

Reviewed: `_note.md` (STATUS: COMPLETE), PLAN.md §0/§1/§2 L4/§3/§4/§6/§10, brief `layout-degenerates.md`, the six owned
files, `seam-1.patch` / `seam-2.patch` and their patch-borne tests. Shared tree, HEAD `e330e255`. Every claim below
was checked by an EXECUTED run in this tree (logs beside this file), not by reading.

## Verdict: MIXED — the lane's claims hold; four should-fix gaps and some nits found, nothing must-fix before the gate

The carrier set holds (my own wider census: 3), every lane mutation replays red on both twins, M6 replays red/green
on both seams, both patches apply cleanly alone and stacked after L2, and every quoted score matches `cells.mjs`.
What is wrong:
- the Android adapter's density arithmetic has no pin;
- the gate admits `position: absolute|fixed` floats;
- the note's "runtime admits a subset of the census" argument is false (its number survives);
- the lane's census does not model the runtime's tree rewrite.

## Repros (executed)

| # | what | result | evidence |
|---|---|---|---|
| R1 | Baseline focused suites | Compose: FloatAvoidPlanTest 15, FloatClearance 10+6, FloatRowPacking 19, SeamReachability 3, all green. Catalyst: FloatAvoidPlanTests 15, FloatClearanceTests 10, FloatRowPackingTests 18 = 43, green. The census tests print `1435 documents, 10712 components, 3 admitted` on both | run 10:19Z / 10:2xZ |
| R2 | Lane mutations M1–M5, M7–M10 replayed with MY driver (`skeptic-mutate.py`, own anchors) | **all RED on both twins**, on the same pins the note names. Compose: M1 P5d-G7nc; M2 P1/P3/P4/P6; M3 P1/P3/P7; M4 both P5d-G7; M5 P5d-G5; M7 P6; M8 P7; M9 P5d-G8; M10 P5d-G2. Swift: identical set | `skeptic-mutations-kt.out.txt`, `skeptic-mutations-swift.out.txt` (+ .json) |
| R3 | Restores | `FloatAvoidPlan.kt` fb440ee4…0d47d7 → fb440ee4…0d47d7, green 50/50; `FloatAvoidPlan.swift` e7780896…0c36c4 → e7780896…0c36c4, green 15/15 | same files, `green` block |
| R4 | M6 Compose, under lock `wave53-lock/ComponentRenderer.kt` | seam-1 applied (37d7d2e7…d581) → 55 green incl. FloatAvoidSeamWiringTest 2/2. Renderer at HEAD bytes with the wiring test kept → **2/2 RED** ("the float-avoid seam binding is missing"). Restored e6c2a650…6adb, `git diff` clean, lock released | `skeptic-seam1.log` |
| R5 | M6 Swift, under lock `wave53-lock/ComponentRenderer.swift` | seam-2 applied (b18ed167…9f81) → 46 green (incl. raster 2/2 and my probe). HEAD renderer with the raster test kept → **2 tests, 6 asserts RED** (orange missing at rows 200-219 / 0-19, still at 300-319). Restored ffa03357…db5f, clean, released | `skeptic-seam2.log`, `_skeptic-xcb-seam*.trim.txt` |
| R6 | Patch application | both patches `git apply --check` clean on HEAD. Stacked in a scratch repo in the §3/§4 order (L2 seam-1 → L4 seam-2 → L2 seam-2 → L4 seam-1): all 4 apply | console |
| R7 | **My census** (`skeptic-census.py`, independent Python over all 1435 wave52-ship per-test IR) | Tier T0 (structural: k leading floats + a last non-float) = 22 containers in 20 documents. T1 (every child-level clause; container property clauses IGNORED, because the runtime merge, var() drop, AllReset or contents strip could remove them) = **3**. T2 (full gate) = **3**: the lane's three. **Re-run on the UNBOXED tree** (emulating `ContentsUnboxing.resolve`, which both natives apply before the block loop): 10668 components, same T0 22, T1 = T2 = 3. No under-reported radius | `skeptic-census.out.txt` |
| R8 | Scores, with `cells.mjs` against wave52-ship and wave53-open | all equal to the note | console |
| R9 | PNGs, opened and measured with my own bbox script | The ref and the captures agree with the note and the brief | console, PNGs |
| R10 | Geometry probe `float-avoid.geometry.py` | wave52-ship and wave53-open: 6 native rows WRONG, ref/web OK, rc 0 | console |
| R11 | Kotlin probe `SkepticL4ProbeTest.kt.txt` (temporary test, deleted after) | Q1/Q2: a float with `Position ABSOLUTE` / `FIXED` → `shape()` **admits**. Q3: place() at density 2.75 → float1.x 550, BFC (0,550) ✓. **Under mutation S1 (drop `* scale`) → float1.x −150, BFC (0,825)**, so a pin like Q3 catches S1 | `skeptic-probe-kt.out.txt` |
| R12 | Swift probe `SkepticL4ProbeTests.swift.txt` (temporary, under the R5 lock, deleted) | Q1: the Swift planner also admits abspos floats. Q4: the admitted abspos shape renders, via the drift fallback, the same ink as HEAD (blue (0,0)-(399,99) n=40000, orange (0,100)). Q5: on 002 the floats that overflow the 20-px container still paint (blue at (100,150) and (300,250)) | `_skeptic-xcb-seam.trim.txt` |
| R13 | Skeptic mutations S1–S5 (unpinned branches) | **GREEN = survivors.** Compose: S1 (container-width `* scale` deleted), S2 (§10.6.7 arm deleted), S3 (zero-height band clause), S4 (BFC-size `* scale` deleted), S5 (float Display refusal deleted). Swift: S2, S3 | `skeptic-mutations-*.out.txt` |

R9 measured geometry (canvas px):
- **contain-inline-size-bfc-floats-001:**
  - ref orange (16,288)-(215,307), blue 64600;
  - ios/android orange (16,388)-(215,407), blue 66600.
- **contain-inline-size-bfc-floats-002:**
  - ref orange (16,88)-(315,107);
  - natives (16,388)-(315,407).
- **display-flow-root-002:**
  - ref outline columns x265 and x266, y115-316 (202 rows each);
  - natives one column x16, y215-416. This is the right half of the 1-px ring; the left half is ICB-clipped.
- The iOS raster numbers (`outline-probe.out.txt`: (249,99)-(250,300) n=404) match the ref offset by (16,16).
- The Compose canvas clips only the inline axis (`COMPOSED_ICB_CLIP_VERTICAL_SLACK_PX`) and its height has a 600 floor. 002's overflowing floats are therefore not clipped on Android either, though this was read, not run.

## Defects, ranked

1. **should-fix — the Compose density scale is unpinned (`FloatAvoidPlan.kt` `place`: `containerWidthPx * scale`, `bfcInlineSizePx * scale`).**
   - Every pin runs at scale 1, so deleting either multiplication stays green (S1, S4).
   - On device the adapter passes `scale = density`, and all three carriers take `w` from the 400-px Width. At 2.75, S1 puts float 1 at x −150 and the BFC at (0,825) (R11).
   - Only the on-device probe would see it.
   - Fix: add a P9 "device px" pin on verbatim 001: place() with widths/heights/incoming/h ×2.75 and `scale = 2.75` → float1.x 550, BFC (0,550), width 1100. Add a derived px-width BFC so that S4 also goes red.
2. **should-fix — the gate admits `position: absolute|fixed` floats (both twins; G6 checks no float Position).**
   - CSS 2.1 §9.7: such a float computes to `float: none` and is out of flow, so the plan's model is wrong.
   - Executed: R11 (Kotlin) and R12 (Swift), both admitted.
   - Swift degrades safely: the subview drift falls back to VStack geometry, and the ink is identical to HEAD (R12 Q4).
   - Compose would hoist the box (RenderComponent composes nothing) and hit the drift fallback. That fallback composes the floats under `LocalSelfAlignmentHandled = true` and places every node at x = 0. For an (R, abs-L, R) list, the right floats would lose their frozen TopEnd alignment. This consequence is reasoned only: a Compose Layout cannot run on the JVM.
   - Unreachable this wave: census T1 = 3, and none of their floats declares Position.
   - Fix: refuse a float whose Position ∉ {absent, STATIC, RELATIVE} (or any Position), plus a derived P5d pin on each twin.
3. **should-fix (honesty) — the note's §2 "Raw vs runtime" argument is false.**
   - It says merging "only ADDS inherited types … so the runtime admits a subset of the census set". In fact both natives also:
     - drop guaranteed-invalid var() declarations (DynamicValueResolver);
     - apply AllReset;
     - run `ContentsUnboxing.resolve` before the block loop. This splices `display: contents` children into `component.children` and strips an unboxable root to its inheritable declarations.
   - Each of these can make the runtime admit a container the raw census refuses.
   - The NUMBER survives: R7's T1 tier ignores every container-property clause, and the unboxed pass, and both still give 3.
   - Fix: restate the sentence.
4. **should-fix — the lane's JVM and Catalyst census tests (`census - exactly the three carriers…`) run `shape()` on the raw SlotComposer tree, not on `ContentsUnboxing.resolve(c)`.**
   - A future `display: contents` wrapper around a float-then-BFC list would be invisible to the pin that guards the blast radius.
   - Fix: apply the renderer-entry rewrite (internal `ContentsUnboxing.resolve`, test-accessible in-module) before `shape()`.
5. **nit — untested branches in new logic (survivors S2, S3, S5).** Each would take one derived pin:
   - the §10.6.7 BFC-container height arm (no pin has a BFC container);
   - the zero-height-band clause `|| it[1] <= y`;
   - the float Display refusal.
6. **nit — the Compose drift fallback is not "the frozen geometry" as its comment claims (`FloatAvoidLayout.kt:37-38`).**
   - The seam has already composed the floats under `LocalSelfAlignmentHandled provides true`, so in the fallback a right float sits at x = 0, not at its frozen TopEnd position.
   - The Swift fallback does keep the frozen picture (R12 Q4).
   - Reachable only through a renderer bug or defect 2.
7. **nit — the raster pin's float probes cannot see a wrong container width.**
   - (300,50) and (300,250) are blue whether right floats are placed from 400 or 358. Float 1 spans x 200..399 vs 158..357; float 3 spans 100..399 vs 58..357.
   - Plan-level P7 holds M8. A discriminating pixel, for example (370,50) blue and (170,50) white, would let the raster test pin the adapter's width too.
8. **nit — size and style.**
   - `FloatAvoidPlanTests.swift` is 210 lines (target ≤ 200; the note says so).
   - `FloatAvoidPlan.swift` reaches 199 lines only through 14 lines over 120 chars (max 165); `FloatAvoidPlan.kt` has 6 (max 151).
   - Comment density follows the block-comment pattern, not one comment per line.

## Not defects (checked)
- **Ownership and leftovers:**
  - no seam file is modified (git status);
  - `tools/titan/runs/wave53-lock/` is empty;
  - no "probe" in the owned sources;
  - no scratchpad pointer in the lane's files;
  - the patch-borne wiring test equals `seam1-files/`.
- **Verbatim payloads:** all 8 sha1s are equal in wave52-ship and wave53-open, and the literals re-hash (testVerbatim green).
- **Twin parity:** both censuses give 3; the mutation red-sets are identical across the twins.
- **Labels:** the 002 flips are geometry-gated (`→ GEOMETRY OK` on the probe), not DEGENERATE by construction. The iOS raster puts the orange exactly where the ref has it. The Android rows rest on an adapter that has never run, and the note labels that MED-HIGH.

## Could not check
- **The Compose `FloatAvoidLayout` (none of it has ever run):**
  - node count;
  - density;
  - the Column's actual constraints;
  - native BFC heights (001 needs > 100; 002 needs ≤ 100 to land at (0,0));
  - the outline's 2 columns at x265-266.
  
  JVM-only host, no device.
- **iOS harness canvas:** the result in the harness canvas (`CaptureCanvas` / `ComposedRootStack`) rather than the 420×420 raster stage.
- **Captures and controls:**
  - SSIM of the new pictures;
  - R4/R7 byte-identity of the 155 must-not-move cells, including the claim that the extra Swift `_ConditionalContent` level is pixel-transparent.

  All need the orchestrator's probe and gate.
- **Full suites:** not run (§0: focused only).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf

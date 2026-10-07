# layout-degenerates — wave 53 family brief

Read-only planning lane, 2026-10-07, written while the `wave53-open` gate was running (no builds, no device, no
scorer run). Evidence: gate of record `wave52-ship` (dev tip cdb8a845), scores via `cells.mjs` / `score-gate.mjs
loadRun`, pictures from `tools/titan/runs/wave52-ship/sections/<sec>/{screenshots,ios-screenshots,android-screenshots}`
against the frozen refs `tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`. Every
PNG named below was opened at full size, and a pixel probe measured the boxes (canvas px, inclusive bboxes). Census
beside this brief: `layout-degenerates.census.json`. Method scripts are beside it too:
- `.dump-cells.mjs` writes `cells.json`;
- `.census.py <cells.json> <out>` is the float, BFC and contain census;
- `.census-anchor.py` is the anchor census;
- `.sim-ssim.mjs` is the SSIM simulation.

The two .py scripts and `.sim-ssim.mjs` read nothing but JSON and a handful of PNGs.

**Verdict in one line:** the two contain cells, plus three more cells with the same mechanism that the census found,
make one honest lane (**GO**). It is a §9.5 "BFC beside floats" layout for a narrow, fully gated shape on both natives.
`anchor-position-multicol-007` android is **RECORD-AS-WALL**: neither native has an anchor-positioning runtime, the
bake cannot deliver position-area geometry, and the anchor is never fragmented. It is one of 24 red-ink anchor passes.

---

## 1. Target cells

| cell | wave51-fix | wave52-calib | wave52-ship | cell-review (2 readers) |
|---|---|---|---|---|
| css-contain/contain-inline-size-bfc-floats-001 **ios** | f 0.9406 | f 0.9406 | **P 0.9531** | DEGENERATE / DEGENERATE |
| css-contain/contain-inline-size-bfc-floats-001 **android** | f 0.9394 | f 0.9394 | **P 0.9519** | DEGENERATE / DEGENERATE |
| css-anchor-position/anchor-position-multicol-007 **android** | f 0.9499 | f 0.9499 | **P 0.9519** | DEGENERATE / DEGENERATE |

Same mechanism, found by the census (§6). Neither cell was in cell-review, because neither flipped:

| cell | wave51-fix | wave52-ship | state |
|---|---|---|---|
| css-contain/contain-inline-size-bfc-floats-002 ios / android | f 0.9198 / f 0.9186 | f 0.9322 / f 0.9311 | honest fail, the twin of 001 |
| css-display/display-flow-root-002 ios / android | P 0.9655 / P 0.9655 | P 0.9734 / P 0.9734 | **unreviewed DEGENERATE pass** (§2) |

Context, not targets: web passes all of them (001/002/flow-root-002 web P 1.0000, captures pixel-identical to their
refs; multicol-007 web P 0.999). multicol-007 ios is an honest fail: f 0.9379 → f 0.9398.
All six native contain/flow-root cells carry `degenerateFailed: true` in the wave52-ship manifest (the disarmed
triage stamp, precision 0.93 per BACKLOG obligation #4). multicol-007 ios and android carry it too.

## 2. The picture

**contain-inline-size-bfc-floats-001.** The reference, the web capture and both natives show the same blue floats
(float1 x216–373 × y88–187, float2 x16–265 × y188–287, float3 x116–373 × y288–387; the right edge is clipped at the
ICB). The difference is the orange box:
- **Reference:** orange 200×20 at **x16–215, y288–307**, beside float3's top and painted over float3's left 100 px.
  Blue = 64 600 px, because the orange hides 2 000 px of float3.
- **ios and android (identical):** orange at **x16–215, y388–407**, wholly below float3 and overlapping no float.
  Blue = 66 600 px. The test's assertion ("to the left of the third float, overlapping the float") is visibly
  broken.

**contain-inline-size-bfc-floats-002.** The floats are the same. Reference: orange 300×20 at **x16–315, y88–107**,
beside float1 and over its left 100 px. Natives: **x16–315, y388–407**, the same "below every float" placement.

**display-flow-root-002.** The floats are blue 250×100: float1 at x166–373 × y16–115, float2 at x16–265 ×
y116–215. Reference: the 1-px black outline of the zero-width, 200-tall flow-root is at **x265–266, y115–316**
(404 px), beside float2. Natives: one black column at **x16, y215–416** (202 px), below both floats. It passes at
0.9734 with the asserted placement wrong by 250 px in x and 100 px in y.

**anchor-position-multicol-007 android.** Reference: one filled green 100×100 at **x16–115, y88–187**, with no red.
The capture is pixel-classified at 2-px pitch:
- **Red anchor:** a 70×20 red box at **x52–121, y88–107** with a green 60×10 rectangle inside it (x≈57–116,
  y93–102). That leaves a 5-px red ring of exactly **800 px** (70·20 − 60·10).
- **Stray green rectangle:** hollow, 70×20, at **x≈162–231, y88–107**.
- **Outside-area ring:** a 5-px green ring whose top bar is y85–87 and bottom bar y182–184, spanning x16 to ≈106.
  Its left side is at x7–15, which the ICB clips away (Fix A).
- **Filled green mass:** at x≈22–110, y93–182.

The test's whole assertion is "no red", and it is broken. ios fails it more visibly: 1 400 red px at x53–122,
y188–207.

## 3. The wire (`per-test-ir/…`, verbatim and trimmed to the elements involved)

contain-inline-size-bfc-floats-001 (002 is identical except that the orange is `px:300` and there is no filler):
```
{"id":"wpt__css-contain__contain-inline-size-bfc-floats-001__1-101","properties":[{"type":"Width","data":{"type":"length","px":400}}]}
{"id":"contain-inline-size-bfc-floats-001__1__0-102","properties":[{"type":"Float","data":"RIGHT"},…,{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":100}}],"slot":{"parent":"…__1-101"}}
{"id":"contain-inline-size-bfc-floats-001__1__1-103", … "Float":"LEFT" … Width 250, Height 100 …}
{"id":"contain-inline-size-bfc-floats-001__1__2-104", … "Float":"RIGHT" … Width 300, Height 100 …}
{"id":"contain-inline-size-bfc-floats-001__1__3-105","properties":[{"type":"Contain","data":["INLINE_SIZE"]},{"type":"Display","data":"FLOW_ROOT"},{"type":"Width","data":"fit-content"},{"type":"LineHeight","data":{"original":{"type":"length","original":{"v":1,"u":"EM"}}}}],"slot":{"parent":"…__1-101"}}
{"id":"…__1__3__0-106","properties":[{"type":"Display","data":"INLINE_BLOCK"},{"type":"Width","data":{"type":"length","px":200}},{"type":"Height","data":{"type":"length","px":20}},{"type":"BackgroundColor",…"orange"},{"type":"VerticalAlign","data":{"type":"keyword","value":"TOP"}}],"slot":{"parent":"…__1__3-105"},"meta":{"sourceTag":"span",…}}
{"id":"…__1__3__1-107","properties":[{"type":"Height","data":{"type":"length","px":150}}],"slot":{"parent":"…__1__3-105"}}
```
display-flow-root-002:
```
{"id":"wpt__css-display__display-flow-root-002__0-291","properties":[{"type":"Width","data":{"type":"length","px":400}}]}
{"id":"display-flow-root-002__0__0-292", … Width 250, Height 100, {"type":"Float","data":"RIGHT"}], …}
{"id":"display-flow-root-002__0__1-293", … Width 250, Height 100, {"type":"Float","data":"LEFT"}], …}
{"id":"display-flow-root-002__0__2-294","properties":[{"type":"Display","data":"FLOW_ROOT"},{"type":"Width","data":{"type":"length","px":0}},{"type":"OutlineWidth",…1},{"type":"OutlineStyle","data":"SOLID"},{"type":"OutlineColor",…black},{"type":"Height","data":{"type":"length","px":200}}],…}
```
The wire is faithful. The web runtime renders this same IR to the right picture, and all three containers are
composed ROOTS (no `slot`).

multicol-007 is `postLoadExtracted: true` (tag `requires-anchor-positioning-runtime` ∈ `EXTRACTION_WALL_TAGS`,
`inject-wpt-block.mjs:1103-1107`). Every box carries Chromium's CSSOM values:
- **anchor:** `"AnchorName":{"name":"--a1"}, "InlineSize":{"px":20}, "BlockSize":{"px":70}, "Width":{"px":70},
  "Height":{"px":20}, red`.
- **inside func box:** `"Top":{"px":0},"Left":{"px":110},"Width":{"px":60},"Height":{"px":10}`, border 5 green.
- **inside area box:** `"PositionArea":{center,center},"Top":0,"Left":0,"Width":50,"Height":0`, margin 5,
  border 5.
- **outside area box:** `"PositionArea":{center,center},"ZIndex":-1,"Top":{"px":0},"Left":{"px":0},"Width":90,
  "Height":90`, border 5 green.
- **outside func box:** `"Top":{"px":3},"Left":{"px":9}`, 90×90 green, margin 5.

## 4. The mechanism (traced)

### 4.1 contain / flow-root: neither native has float avoidance, so a BFC sibling stacks below the floats

CSS (2.1 §9.5): floats are out of flow, and an in-flow box that establishes a BFC "must not overlap the margin box
of any floats in the same block formatting context". Here is how each carrier should lay out:
- **001:** the flow-root's used inline size is 0 (contain: inline-size makes both intrinsic sizes 0; `fit-content`
  = min(max-content, max(min-content, available)) = 0). Its height is ≈170 (line 20 + filler 150). At y=0 the band
  0–170 is closed (float2 left edge 250 > float1 left edge 200). At y=100 it is closed (250 > float3's 100). At
  y=200 there is a 0–100 gap and 0 fits, so the box goes to **(0,200)**. The orange overflows the box and paints
  over float3, because inline content paints at Appendix E step 7, above floats at step 5.
- **002:** the box is 20 tall, so at y=0 the band 0–20 has a 0–200 gap and the box goes to **(0,0)**.
- **flow-root-002:** the box is width 0 and height 200. At y=0 the band is closed (250 > 150). At y=100 there is a
  250–400 gap, so the box goes to **(250,100)**.

**Compose:**
1. In `core/renderer/ComponentRenderer.kt`, `blockFloatSegments` (:5046) returns null. `FloatRowPacking.segment`
   (`layout/FloatRowPacking.kt:127-172`) ends a streak on every side flip (:158). R,L,R gives three singles, and
   `"0 or 1 floats — each keeps the plain block path"` (:148-149).
2. `FloatClearance.resolve` returns null at its pre-gate (`layout/FloatClearance.kt:72`, no `Clear` wire).
3. The children therefore take the frozen per-child loop `renderBlockChild` (:3565) inside the block Column. Each
   float is an **in-flow block that takes its 100 px**; FloatClearance.kt's banner records exactly this wave-41
   finding: "single floats were laid as IN-FLOW stacked blocks".
4. Right floats are parked by `floatEndAlignment` (:5027), applied at :2156 as `Box(fillMaxWidth, TopEnd)`.
5. The third float ends at y=300, so the flow-root starts at y=300. That puts the orange at canvas y388 (001/002)
   and the outline at y215 (flow-root-002: floats end at y=200).

**SwiftUI:** in `Renderer/ComponentRenderer.swift`, `blockFloatSegments()` (:3027-3053) returns nil for the same
facts. The branch chain falls through to the plain `VStack` (:1950-1962), and right floats are
`.frame(maxWidth: .infinity, alignment: .topTrailing)` (:1395). The stacking is the same.

**Why the floats still look right:** in all three carriers each float is too wide to sit beside the earlier ones, so
its §9.5.1 position coincides with its stacked position (y 0/100/200). The defect shows only on the BFC.

**Containment is not consumed by either native, but it is not the cause.**
- Compose `ContainConfig.inlineSize` (`performance/PerformanceConfig.kt:36`, set in `PerformanceExtractor.kt:69`)
  has no consumer.
- iOS `PerformanceExtractor.swift:35-49` keeps only raw strings.
- With zero inline size and no float avoidance, the box would still start at y=300.

**Ruled out:**
- **The wire:** web is pixel-identical from the same IR.
- **The ICB clip:** float edges match the ref, x373.
- **The BFC's measured height:** any height > 100 gives 001's answer.
- **Clearance (W5) and float runs (wave-19):** neither engages, by their own gates.

### 4.2 anchor-position-multicol-007 android: three independent gaps (traced to the wire, no runtime path exists)

1. **No native anchor runtime.** Compose `layout/LayoutExtractor.kt:84-97` registers AnchorName / PositionAnchor /
   PositionArea as "All parse-only on Compose today"; iOS `StyleEngine/layout/LayoutExtractor.swift:108` registers
   them the same way. The test renders only through the post-load bake.
2. **The bake cannot deliver position-area geometry.**
   - For a `position-area` box, CSSOM's resolved `top/left` (`0/0` here) is the inset inside the area cell, not
     the painted offset.
   - The delivery probe `anchorInsetMismatch` (`tools/titan/post-load-extract.mjs:722`) only measures boxes whose
     `align-self`/`justify-self` contains "anchor" (the walker gate near :636). `place-self: stretch` boxes are
     never probed.
   - So the outside-area ring is drawn at the wrapper origin, page x7–106 × y85–184, instead of x16–115 × y88–187.
     That is the y85–87 / y182–184 bars in the capture.
   - This hole is wider than this test: 7 of the corpus's 8 position-area tests carry a baked `Top 0 / Left 0`
     position-area box (census `anchorFamily.positionArea`).
3. **No fragmentation of the relpos subtree.**
   - In Chromium the anchor's 70-px block extent splits across two vertical-rl columns: x16–65 × y88–107 in column
     1, and x95–115 × y168–187 in column 2. The inside boxes, positioned in the same flow-thread space (the
     wire's `Left 110` and the 60×10 / 50×0 sizes are flow-thread values), are fragmented identically and cover
     both pieces.
   - The native draws the anchor whole (70×20 at x52–121) and places the inside-func box at flow-thread x 110
     (page x162–231, the stray rectangle). The inside-area box (60×10 border box at relpos 5,5) covers only the
     anchor's middle, which leaves the 800-px red ring.
   - Untraced detail, which does not change the verdict: why the native relpos/anchor origin lands at page x52.

Both gaps 2 and 3 have to close before the red can honestly disappear. A wave-53 lane can close neither:
- **Gap 2** is an instrument change and needs Chromium to measure painted offsets, so it cannot run on a gated host.
- **Gap 3** is block fragmentation of a relpos subtree together with its abspos descendants inside vertical-rl
  multicol. No native has this; the test carries the `requires-fragmentation` + `requires-orthogonal-flow` tags.

The cell is not unusual. **24 native cells in the anchor-in-multicol / position-area population pass while their
capture carries strict-red ink and their ref none** (census `anchorFamily.passingWithForbiddenRed`; for example
`multicol-013/-014/-015/-016/-017` android 8 000 red px at P 0.98–0.99). multicol-007 android is the one with
**800 px = 0.34 %** of the canvas. That is under `red-square-census.mjs`'s 0.5 % bar, which is why it was not
already on the red-square list.

Side observation, not this family's: `anchor-name-multicol-003` **web** scores P ssim 1 while its capture holds
1 500 strict-red px at x76–165 × y88–137 against a red-free ref. Its own diff record says 1 500 mismatched px, and
the disarmed novel-ink and degenerate stamps fire. Someone should check why SSIM reads 1.0000 there.

## 5. The fix

### 5.1 contain / flow-root: `FloatAvoidPlan` + `FloatAvoidLayout` on both natives

The fix is a pure planner plus a thin Layout adapter, following the FloatRowPacking / FloatRowLayout /
FloatBlockLayout precedent. It is dispatched in the block child loop under the WPT capture gate.

**Gate.** The planner returns null (identity, frozen rendering) unless ALL of these hold. Every clause is satisfied
by the 3 carriers and refuses something named in §6:
- **G1. Capture mode only:** Compose `LocalWptCaptureMode.current`; iOS `wptCaptureMode`.
- **G2. The container is a composed root (no `slot.parent`) or a BFC root.** This is the same attach rule as
  `FloatClearance.resolve`, so no float from outside the container can intrude. In addition, no Float wire may
  appear in an earlier root's subtree; the residual in the corpus is 0 carriers.
- **G3. Plain horizontal-tb LTR container.** No `WritingMode`/`Direction` wire, no text, no `runs`, no pseudos, no
  selectors/media.
- **G4. Child shape.** The in-flow children are k ≥ 1 floats followed by exactly ONE in-flow BFC root as the LAST
  in-flow child. A BFC root here is `Display FLOW_ROOT`, a non-visible `Overflow*`, or `Contain` layout/paint/
  strict/content. abspos children ride the overlay as today.
- **G5. No `Clear` on any child** (W5 owns clearance) and no ≥2 consecutive same-side floats (FloatRowPacking owns
  runs).
- **G6. Floats:** px `Width` + px `Height`, and no Margin*/Padding*/Border*Width/BoxSizing wire. This is
  FloatClearance's exactness discipline.
- **G7. The BFC's used inline size must be provable,** in one of two ways:
  - a px `Width`; or
  - `Width` ∈ {`fit-content`, `min-content`, `max-content`} with `Contain` ∋ INLINE_SIZE | SIZE | STRICT. This
    gives 0, by inline-size containment (the test's own `rel=help`, css-contain-3 #containment-inline-size) and
    the fit-content formula in css-sizing-3.

  Auto width bails (display-flow-root-001), and so do BFC margins.
- **G8. The BFC paints nothing of its own** (no BackgroundColor / Border* / BoxShadow) unless its width is px. In
  wire order a BFC background would paint ABOVE the floats, where CSS 2.1 Appendix E paints it below (step 4 vs
  5). The contain-sized box is also measured at its content width natively (see the algorithm), so a visible box
  would show the wrong width. Outline is allowed: it paints on top in Chromium too, as flow-root-002's ref shows
  over float2.

**Algorithm (pure, px):**
1. Place the floats by §9.5.1. Each float is placed as high as possible, not above the previous float's top, at
   the left edge (left floats) or right edge (right floats) of the space between existing floats. If the band is
   too narrow it moves down to the next float bottom.
2. Place the BFC at the first candidate y ∈ {0} ∪ {float bottoms} (ascending) where, over the band [y, y+h), the
   gap between the left floats' right edges and the right floats' left edges is ≥ the used inline size. Its x is
   the gap's left edge.
3. Bands are **half-open**: a float ending at y does not intersect a band starting at y.
4. The container reports its in-flow height (BFC bottom), because floats do not contribute (CSS 2.1 §10.6.3). The
   width comes from the incoming constraint or proposal. That width is 400 here, not the 358 ICB; reading the
   wrong one moves right floats by 42 px.

**Adapters:**
- **Compose `FloatAvoidLayout`:**
  - Measure floats unbounded (`Constraints()`, the FloatRowLayout P7 precedent) under `LocalSelfAlignmentHandled
    provides true`, so `floatEndAlignment`'s TopEnd wrapper does not place them a second time.
  - Measure the BFC with the Column's own constraints (its rendering stays byte-identical), take its measured
    height as h, and place everything at the plan's coordinates in wire order (floats, then BFC, so the orange
    paints over the blue).
- **SwiftUI `FloatAvoidLayout: Layout`:** measure floats `.unspecified`. This is the `FloatBlockLayout.swift:109`
  precedent, and it neutralises `.frame(maxWidth:.infinity)`. Measure the BFC with `ProposedViewSize(width:
  proposal.width, height: nil)` (:94/:135 precedent).

**Files the lane owns (new):**
- Compose: `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/FloatAvoidPlan.kt` and
  `…/layout/FloatAvoidLayout.kt`.
- Swift: `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/FloatAvoidPlan.swift` and
  `…/Renderer/FloatAvoidLayout.swift`.
- Tests: Compose `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/FloatAvoidPlanTest.kt` and
  `…/core/renderer/FloatAvoidSeamWiringTest.kt`; Swift `runtimes/swiftui/Tests/StyleConverterRuntimeTests/FloatAvoidPlanTests.swift`
  and `FloatAvoidLayoutRasterTests.swift`.
- Each file stays ≤ 200 lines with every line commented.

**Seam hunks (delivered as patch files, not edited):**
- `runtimes/compose/…/core/renderer/ComponentRenderer.kt`, block child loop: a new branch tried FIRST, before
  `val floatSegments =` (:3670). It is mutually exclusive with float runs by G5.
- `runtimes/swiftui/…/Renderer/ComponentRenderer.swift`, the branch chain: a new `else if` before
  `blockFloatSegments()` (:1876). It re-publishes `.environment(\.floatClearancePlan, clearanceScopePlan)` like the
  VStack branch.
- No `extract-fixture.mjs` change is needed: the wire already carries every input.

**Spec:**
- CSS 2.1 §9.5 (BFC must not overlap float margin boxes), §9.5.1 (float rules), §10.6.3 (floats excluded from
  auto height), Appendix E (paint order).
- css-display-3 (`flow-root` establishes a BFC).
- css-contain-3 #containment-inline-size, css-sizing-3 fit-content.

**Rejected alternatives:**
- **Offset only the BFC** (dy = plan − stacked, keeping the floats stacked). It needs a negative-offset channel that
  W5 deliberately refused, and it reports the wrong container height (002: 300 instead of 20).
- **Implement `contain: inline-size` in general sizing.** It changes no carrier's picture on its own and widens the
  blast radius to the 25 contain-size tests.

### 5.2 multicol-007 android: no runtime fix (RECORD-AS-WALL)

Record it in BACKLOG 0(l″) as a wall and stop calling it "pending". The cell needs a native anchor runtime or an
honest bake, plus fragmentation of positioned subtrees in vertical-rl multicol.

Optional instrument follow-up for "Instrument decisions pending", not a lane: widen `anchorInsetMismatch` to
`position-area` boxes.
- It would probably bail the 7 position-area tests to the wall, which is honest (not delivered, not scored).
- It needs a Chromium measurement of resolved-vs-painted first, which this host cannot run during the gate.
- Pre-register it like every instrument change.

## 6. Blast radius (census over all 1435 wave52-ship per-test IR docs)

**Fix carrier set: exactly 3 tests, 6 native cells.** For `control-check.mjs`, the allowed changed stems on ios and
android are:
```
wpt__css-contain__contain-inline-size-bfc-floats-001
wpt__css-contain__contain-inline-size-bfc-floats-002
wpt__css-display__display-flow-root-002
```
Web allows nothing: its 3 carrier cells (all P 1.0000) must be byte-identical. Of the 6 native cells, 4 pass today,
and all 4 are DEGENERATE (001 ×2 reviewed; flow-root-002 ×2 measured here). The other 2 fail (002 ×2).

**Float-then-BFC sibling shapes the gate must refuse: 7 tests, all byte-identical.**

| test | why the gate refuses it | ios / android today |
|---|---|---|
| CSS2/floats-clear/adjoining-float-new-fc | Clear | P / P |
| CSS2/floats-clear/floats-bfc-003 | Clear, same-side run | P / P |
| CSS2/floats-clear/adjoining-float-nested-forced-clearance-002 | Clear, non-float first child | P / P |
| css-display/display-flow-root-001 | auto-width BFC with text and border | f 0.8156 / f 0.8156 |
| css-contain/contain-content-001 | em widths, text | f / f |
| css-writing-modes/direction-upright-001 | Clear, runs, text | f / f |
| css-writing-modes/direction-upright-002 | Clear, runs, text | f / f |

**Control set:** all **92 other float-carrying tests**. They give 143 passing and 31 failing scored native cells
(listed in the census). The gate is evaluated in every capture-mode block loop, so all of them must come out
byte-identical. web is byte-identical everywhere.

## 7. Predictions

Simulated with the scorer's own SSIM: ssim.js fast mssim, per `inject-wpt-block.mjs`. Each wave52-ship capture was
re-scored after repainting only the misplaced box at the ref position, and the calibration reproduced the scored
numbers exactly.

- **contain-inline-size-bfc-floats-001 ios:** P 0.9531 → **P ≈0.998** (sim 0.9984), DEGENERATE → FAITHFUL —
  **HIGH**. Orange at x16–215, y288–307; blue 64 600 px.
- **contain-inline-size-bfc-floats-001 android:** P 0.9519 → **P ≈0.997** (sim 0.9972), FAITHFUL — **HIGH**.
- **contain-inline-size-bfc-floats-002 ios:** f 0.9322 → **P ≈0.998** (sim 0.9985), a flip — **HIGH**.
- **contain-inline-size-bfc-floats-002 android:** f 0.9311 → **P ≈0.997** (sim 0.9974), a flip — **HIGH**.
- **display-flow-root-002 ios / android:** P 0.9734 → **P ≈1.000** (sim 1.0000), DEGENERATE → FAITHFUL — **MED**.
  The outline must render 2 px at x265–266 once it is no longer at the clip edge; the 1-px-vs-2-px question is the
  only unknown.
- **anchor-position-multicol-007 android:** stays **P 0.9519**, byte-identical, still DEGENERATE (recorded wall) —
  **HIGH**.
- **Net:** +2 passes (the 002 pair). 4 passes stop being degenerate. `degenerateFailed` turns false on all 6 cells
  (MED).

**Must not move (byte-identical):**
- all 3 web carrier cells;
- the 7 refused shapes in §6, especially floats-clear `adjoining-float-new-fc` / `floats-bfc-003` /
  `adjoining-float-nested-forced-clearance-002` (P on both natives);
- the 92-test float control set (143 passing native cells);
- every anchor-position cell.

## 8. Verification plan

**Unit pins on verbatim payloads:** the components arrays are copied byte-for-byte from the wave52-ship per-test IR,
and the BFC height h is injected because it is a device measurement.
- **P1 (001 placement).** Floats placed at (200,0), (0,100), (100,200); h=170 puts the BFC at **(0,200)**.
- **P2 (002 placement).** h=20 puts the BFC at **(0,0)**.
- **P3 (flow-root-002 placement).** Floats placed at (150,0), (0,100); h=200 puts the BFC at **(250,100)**.
- **P4 (the "affected by height" boundary).** On 001's floats, h=100 gives (0,0) and h=101 gives (0,200).
- **P5 (gate refusals).** Each of these returns null, with each verbatim sibling list taken from wave52-ship:
  display-flow-root-001's third div, adjoining-float-new-fc, floats-bfc-003's outer, contain-content-001, and
  direction-upright-001.
- **P6 (reported height).** The container reports 370 / 20 / 300.
- **P7 (container width).** Right-float x is read from the container width 400, not the canvas.

**Mutations that must turn a pin red:**

| mutation | pins that go red |
|---|---|
| M1: drop containment (the used width becomes the measured 200 / 300) | P1 → (0,300) and P2 → (0,300) |
| M2: closed band intervals | P4 h=100, and P3 → (0,200) |
| M3: right floats placed at x=0 | P1, P3 |
| M4: delete the G7 auto-width bail | P5 display-flow-root-001 |
| M5: delete the G5 Clear bail | P5 adjoining-float-new-fc |
| M6: delete the seam branch | the Compose source-scan `FloatAvoidSeamWiringTest` (the SeamReachabilityTest idiom; compose tests are JVM-only and cannot execute the Layout) and the iOS `FloatAvoidLayoutRasterTests` (ImageRenderer under Catalyst, AbsposFlexOverlayRasterTests precedent: render 001's subtree and assert orange at container rows 200–219) |

**Focused suites (after the gate, on a quiet host):**
- Compose: `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*FloatAvoid*' --tests
  '*FloatClearance*' --tests '*FloatRowPacking*' --tests '*SeamReachability*')`.
- iOS: `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'
  -only-testing:StyleConverterRuntimeTests/FloatAvoidPlanTests -only-testing:StyleConverterRuntimeTests/FloatAvoidLayoutRasterTests
  -only-testing:StyleConverterRuntimeTests/FloatClearanceTests -only-testing:StyleConverterRuntimeTests/FloatRowPackingTests`.
- Then both full suites.

**Closing-gate probe sections:**
- `css-contain` and `css-display` (all carriers).
- `CSS2`: 26 float-carrying tests, including the floats-clear controls.
- `css-grid`: 32 float-carrying tests, the largest control block.
- Then `control-check.mjs <pre> <post>` with the 3 stems above as the only allowed ios/android changes.
- Open the 6 carrier PNGs. Orange must be at y288–307 (001) and y88–107 (002); the outline at x265–266 ×
  y115–316 (flow-root-002).

## 9. Risks and recommendation

- **R1. The Compose adapter is first executed on device.** JVM tests cannot run a `Layout`. Mitigation: the pure
  pins, the seam source-scan, and the probe sections before the full gate (probe-then-ship).
- **R2. The right-float wrapper must not place twice.** Compose needs `LocalSelfAlignmentHandled`; iOS needs
  `.unspecified`. Mitigation: P1/P3 x-values are asserted on device too, with float1 at x216 / x166.
- **R3. Container width source.** A 358-wide proposal instead of the root's own 400 shifts right floats 42 px. P7
  pins it.
- **R4. Seam contention.** Both ComponentRenderer block-loop regions (.kt :3663-3720 and .swift :1876) are hot
  spots. Deliver the patch files against cdb8a845 and re-cut them at integration if another lane's hunk shares the
  context.
- **R5. Gate leakage.** The control set is 92 tests. `control-check` is the arbiter: any changed capture outside
  the 3 stems is a finding, not noise.
- **R6. The BFC's native height for 001 must exceed 100.** It is the 150-px filler plus a line box, so this is
  safe; the probe PNG confirms it.
- **R7. Paint order.** This holds only under G8 (no BFC background or border); a later widening must revisit
  Appendix E step 4 vs 5.

**Recommendation:**
- **GO** for one lane, "BFC beside floats", on both natives. It has 6 target cells: 2 flips and 4
  degenerate-to-faithful. Its new files are listed in §5.1, and the 2 seam hunks go as patches. The census proves
  its blast radius is 3 tests.
- **RECORD-AS-WALL** for `anchor-position-multicol-007` android, together with the 24-cell red-ink anchor population
  in §4.2: no anchor runtime, an undeliverable position-area bake, and no positioned-subtree fragmentation.

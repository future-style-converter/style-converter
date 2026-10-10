# oof-containing-block — wave 54 family brief (from the layout queue scout)

This is a read-only planning brief, written 2026-10-08 while the `wave54-open` gate was running. Nothing was built, run or
captured for it.

**Evidence**
- The gate of record is `wave53-final` (dev 7cce3b22): web 1232/1372 · iOS 1125/1362 · Android 1115/1362 over 1435 per-test IR
  documents.
- Every score comes from `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave53-open wave53-final`, saved as
  `queue-scout-layout.cells.txt`. All lane cells are unchanged between the two runs.
- Pictures are `tools/titan/runs/wave53-final/sections/<sec>/{screenshots,ios-screenshots,android-screenshots}/` compared
  against `tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/<sec>/`. Every PNG named below
  was opened side by side with its ref.
- Pixel numbers come from `queue-scout-layout.ink.py` (strict green and red bboxes) and `oof-containing-block.geometry.py`
  (output: `oof-containing-block.geometry.out.txt`).
- The census is `queue-scout-layout.census.mjs` → `queue-scout-layout.census.json` (`oofcb.*`).
- The simulations are `queue-scout-layout.sim-ssim.mjs` → `.sim-ssim.out.txt`.
- The red-square predicate is `node tools/titan/red-square-census.mjs wave53-final --json queue-scout-layout.red-square.json`.
  It finds 164 passing and 62 failing red cells. Every lane target below that carries an `R` in the census is in that set.

**Queue items.** This brief covers BACKLOG 0(k) (the test's failure-indicator ink reaches the canvas; red-square class) and
0(j) (out-of-flow static position). It is a NEW lane: no wave-53 lane touched these files.

**Verdict in one line: GO (M).** Both natives treat `position: fixed` as viewport-anchored whenever no TRANSFORM ancestor
exists. Compose also hoists every inset abspos box with no positioned ancestor to the canvas. Two spec rules are therefore
missing on both natives:
- an all-auto-inset fixed box sits at its STATIC position;
- `contain: layout|paint|strict|content`, `filter`, `backdrop-filter` and their `will-change` hints ALSO establish the
  containing block for absolute and fixed descendants.

One pure rule table per native plus three one-line conditions fixes 4 failing cells (2 HIGH / MED-HIGH, 2 MED) and turns
8 red-ink DEGENERATE passes faithful. No seam hunk is needed.

---

## 1. Target cells (`wave53-open → wave53-final`, cells.mjs; `R` = flagged by red-square-census)

| cell | score | what the capture shows (ink probe, canvas px, inclusive) |
|---|---|---|
| css-contain/contain-content-003 **android** | f 0.9405 → f 0.9405 R | The two green 100×50 abspos boxes are hoisted to the CANVAS top-right (green bbox x274–373, y16–583: one box at y16–65 over the paragraph, one at y534–583). The 100×100 `contain: content` box shows bare red at x16–115, y88–187 (10 000 px). The ref has green x16–115, y88–187 and no red. |
| css-contain/contain-content-011 **android** | f 0.9284 → f 0.9284 R | Same picture, same numbers. The number "25" under it is wrong on ALL THREE platforms (ref "17"; web P 0.9873, ios P 0.9854 carry it too), so it is not this lane's. |
| filter-effects/backdrop-filter-containing-block **ios** | f 0.7955 → f 0.7955 | The fixed child (`top:0; width:100%; height:200px`) is hoisted to the canvas top and sized against the 358-px canvas: green x16–373, y16–215 (71 600 px) over the paragraph. The red abspos sibling sits in the container's overlay at y88 but is covered down to y215, so only y216–287 shows (10 656 px). |
| filter-effects/backdrop-filter-containing-block **android** | f 0.7506 → f 0.7506 | Both children are hoisted: green x16–225, y16–215 (42 000 px), red x226–373, y16–215. The ref has green 200×200 at x16–215, y88–287 and red x226–373, y88–287, which is REQUIRED ink, clipped at the ICB. |
| css-position/position-relative-003 ios / android | P 0.9594 / P 0.9589 R | The green 100×100 sits at the canvas origin x16–115, y16–115, over the paragraph. The red container shows at y116–187 (7 200 px). DEGENERATE. |
| css-position/position-relative-004 ios / android | P 0.9594 / P 0.9589 R | Identical numbers. The fixed box's containing block is the `contain: paint` div. DEGENERATE. |
| css-position/change-insets-inside-strict-containment-nested ios / android | P 0.9594 / P 0.9589 R | Identical numbers. Two fixed 50×100 halves belong inside two nested `contain: strict` boxes. DEGENERATE. |
| CSS2/abspos/static-fixed-inside-abspos ios / android | P 0.9841 / P 0.9835 R | A 50×50 green at the canvas origin over "Test p" (green bbox x16–115, y16–187). The abspos red 50×50 is bare at x66–115, y138–187 (2 500 px). DEGENERATE. |

Web passes every one of these at P 0.999–1.000 with the ref's picture, and `oof-containing-block.geometry.py` prints
`GEOMETRY OK` for every web row. iOS already draws contain-content-003/011 right (P 0.9974 / 0.9854, geometry OK). The reason
is in §3.

**Touched, no flip predicted:** `css-contain/contain-content-004` ios f 0.8094 / android f 0.8286 R. It is a `contain: content`
table cell with an abspos child. The table itself renders wrong on both natives (blue-filled, rows collapsed), and it is
also in the compose-table-body-cell family's D2 radius (`compose-table-body-cell.md`). It is a shared cell: if it moves,
A/B the two lanes separately.

## 2. The defect as seen

All seven tests are "filled green square, no red" or "one green and one red square" pictures. On the natives, every green
box whose containing block (CB) the test is about lands at the CANVAS origin or the canvas top-right instead of inside the
box the spec makes its CB:
- `contain:*` (contain-content-003/011, change-insets, position-relative-004);
- `backdrop-filter` (backdrop-filter-containing-block);
- the box's STATIC position (static-fixed-inside-abspos, position-relative-003).

Each time the red box the test hides behind it is left bare. For four of these tests the scorer still passes the cell
(P 0.959–0.984), because the misplaced green covers paragraph text and the red is under 5 % of the canvas.

## 3. Mechanism (traced to symbols; line numbers at 7cce3b22)

**Compose (`runtimes/compose/src/main/java/com/styleconverter/runtime/`).**
- `layout/position/CanvasRootHoist.kt` `shouldHoistToCanvasRoot` (:311). The FIXED arm (:340) is `!hasTransformedAncestor`.
  It is insensitive to insets, and its comment says so: "A no-inset fixed box with no such ancestor keeps the wave-17
  canvas-origin anchor". The ABSOLUTE arm is `!hasPositionedAncestor && !hasTransformedAncestor && hasAnyInset(properties)`.
- The only non-positioned containing-block clause is `establishesTransformContainingBlock` (:222), which delegates to
  `TransformContainingBlock.establishes`. That accepts Transform, Translate, Rotate, Scale, Perspective, TransformStyle
  `preserve-3d` and WillChange `transform|perspective`. Contain, Filter and BackdropFilter are never read. The contain
  keywords ARE decoded, as `performance/PerformanceConfig.kt` `ContainConfig.Paint/Content/Strict` (:53–56), but no
  containing-block code consumes them.
- The clause reaches every consumer through one function:
  - the pure walks `collectCanvasHoisted` (:469, flag :516) and `anyOutOfFlowBox` (:553, flag :609 → `hostActivates` :650);
  - the composition channel in `core/renderer/ComponentRenderer.kt`: `childHasTransformedAncestor` (:2010) and
    `transformCbHostsOutOfFlowChild` → `isPositionedContainer` (:3471/:3475), which mounts a containing block's
    out-of-flow children at its padding-box origin.
- The RC1 static-position class `rendersInFlowAsStaticPosition` (:366) is ABSOLUTE-only (:389).
- Trace for contain-content-003: the two abspos children (`top/bottom:0; right:0`) have no positioned or transformed
  ancestor, so the ABSOLUTE arm hoists them to the canvas, where `right:0` anchors at x373. That is the measured x274–373.

**SwiftUI (`runtimes/swiftui/Sources/StyleConverterRuntime/`).**
- In `Renderer/FixedHoist.swift`, `strippingFixedDescendants` (:297) computes
  `childTransformed = hasTransformedAncestor || TransformContainingBlock.establishes(component)` (:308–309). It hoists every
  child with `isFixed(child) && !childTransformed` (:347), with no inset test.
- `rendersInFlowAsStaticPosition` (:111) is ABSOLUTE-only (:115).
- Nested ABSOLUTE descendants are never hoisted on iOS (file banner, "Nested ABSOLUTE descendants are never touched").
  Every container mounts its out-of-flow children in a ZStack overlay at its own padding box:
  `ComponentRenderer.swift` `outOfFlowChildren` :793, `overlayChildren` :812, `absoluteOverlay` :2048,
  `positionedChildren` :2094. That is why iOS draws the abspos half of the family right and Compose does not. On iOS only
  the FIXED half is wrong.

**Spec.** Cite through `tools/visual/spec-cite-validate.mjs`; the validator maps the static position to css-position-3 §3.5.3.
- css-position-3 §2.1: the CB of a fixed box is the viewport UNLESS an ancestor establishes one.
- css-position-3 static position: with both insets on an axis `auto`, the box sits where it would have been in flow.
- css-contain-2: layout and paint containment make the element "a containing block for absolutely positioned and fixed
  positioned descendants". `strict` and `content` include both.
- filter-effects-1 (`filter` ≠ none) and filter-effects-2 (`backdrop-filter` ≠ none) give the same containing-block clause.
- css-will-change-1: a `will-change` of such a property has the same effect.

**Ruled out:**
- The wire: web is pixel-identical from the same IR. `position-relative-004` and `change-insets` are post-load-extracted
  and carry Chromium's resolved `Top/Left {px:0}` relative to the contain box, which is exactly what the CB fix needs.
- The ICB clip: the green edges match.
- 0(b): position-relative-006 is P ×3 at wave53-final.

## 4. The fix

- **F1 — the missing clauses as one pure rule table per native, each a NEW file of ≤ 200 lines.**
  `layout/position/OutOfFlowContainingBlock.kt` and `StyleEngine/layout/position/OutOfFlowContainingBlock.swift`.
  `establishes(properties)` is true when any of these holds:
  - Contain ∋ LAYOUT | PAINT | STRICT | CONTENT. Compose decodes it through `PerformanceExtractor` → `ContainConfig.layout ||
    .paint`, the one existing decoder. Swift reads the `Contain` array, because `PerformanceExtractor.swift` keeps raw
    strings.
  - Filter is a non-empty list.
  - BackdropFilter is a non-empty list.
  - WillChange ∋ `filter` | `backdrop-filter` | `contain`.

  **And the element is not itself `position: absolute | fixed`.** Without that restriction, the widened predicate would flip
  the four positioned backdrop-filter containers (census `establisherControls`: backdrop-filter-clip-rect, -clip-rect-zoom,
  -edge-clipping, -zero-size, all holding ABSOLUTE children only) into ComponentRenderer's positioned-container branch. Those
  are cells the lane must not move (§6). Their FIXED descendants would still hoist, which is spec-wrong. There are 0 corpus
  carriers; leave a `PropertyTracker` breadcrumb `ContainingBlock[positioned-establisher-fixed-descendant]` so it is not silent.
- **F2 — one OR at each choke point.**
  - Compose `CanvasRootHoist.establishesTransformContainingBlock` (:222) returns `TransformContainingBlock.establishes(p) ||
    OutOfFlowContainingBlock.establishes(p)`. Every consumer listed in §3 reads this one function, so the composition channel
    and the two pure walks cannot disagree, and ComponentRenderer.kt needs no hunk. The name keeps its wave-35 meaning
    "claims both out-of-flow classes"; re-true its kdoc.
  - Swift `FixedHoist.swift` :309 gets the same OR.
- **F3 — static position for an all-auto fixed box.**
  - Compose FIXED arm (:340): `!hasTransformedAncestor && hasAnyInset(properties)`. RC1 (:389): `positionType ∈ {ABSOLUTE,
    FIXED}`.
  - Swift :347: `isFixed(child) && !childTransformed && FixedHoist.hasAnyInset(child)`. Swift :115: `.absolute || .fixed`.
  - The un-hoisted box then rides the path its parent already provides:
    - under a RELATIVE parent, the Compose `isPositionedContainer` Box (position-relative-003's inner span);
    - under an ABSOLUTE parent, `PositionedParentFlowSlot.mount`, which already admits FIXED children (:132)
      (static-fixed-inside-abspos's red box);
    - on iOS, the parent overlay.

    Each places it at the parent's padding-box origin, which is the static position for both carriers: the fixed box is
    its parent's first content.
  - A fixed box with no positioned ancestor takes the RC1 zero-flow anchor. That has 0 scored carriers; the only one is the
    unscored `position-fixed-scroll-nested-fixed`.

**Rejected alternatives:**
- **A seam hunk in ComponentRenderer.kt that renames the channel.** It is cosmetic, and it adds a hot-spot conflict with the
  compose-table-body-cell lane.
- **Make `contain` establish the CB only on Compose.** The FIXED half is wrong on iOS too, so the twins would diverge.
- **Implement paint-containment clipping in the same lane.** No carrier needs it. Every lane box fits inside its contain
  box. Keep it queued.

## 5. Corpus radius (census over all 1435 wave53-final per-test IR docs; `oofcb` in `queue-scout-layout.census.json`)

- **Establishers the new table claims:** 142 components in 91 tests. Of those, 11 have an out-of-flow descendant at all:
  6 tests are carriers and the other 4 positioned establishers are controls. Every other establisher has no out-of-flow
  descendant, so the change is inert there **by construction**:
  - the Compose branch at :3471 is gated on `hasOutOfFlowChild`;
  - the hoist flags only matter for out-of-flow boxes.
- **M2 carriers (spec CB ≠ native CB): 10 boxes in 6 tests.**
  - contain-content-003 (A ×2, contain: content);
  - contain-content-004 (A, td contain: content);
  - contain-content-011 (A ×2);
  - change-insets-inside-strict-containment-nested (F ×2, contain: strict);
  - position-relative-004 (F, contain: paint);
  - backdrop-filter-containing-block (F + A).

  In every one the native CB today is the canvas.
- **M1 carriers (all-auto FIXED, hoisted today): 4 boxes in 3 tests.** static-fixed-inside-abspos, position-relative-003,
  and the unscored position-fixed-scroll-nested-fixed (×2).
- **Fixed-carrying controls that must not move: 26 tests** (`oofcb.fixedControls`), listed with their cells in the JSON.
  Examples:
  - anchor-center-safe android P 0.9534;
  - anchor-position-multicol-002 ios f 0.9175 / android f 0.9381;
  - position-fixed-root-element-flex / -grid f ×3;
  - backface-visibility-hidden-004 android f 0.998 R. Its fixed box already sits under a transform CB, so M1 does not fire;
  - backdrop-filter-fixed-clip ios f 0.9733 / android f 0.9481;
  - 3d-rendering-context-and-fixpos P ×3;
  - the view-transition and hypothetical-dynamic-change P rows.
- **Positioned-establisher controls: 4 tests**, kept inert by F1's restriction:
  - backdrop-filter-clip-rect web P 0.959 · ios P 0.956 · android f 0.9315;
  - -edge-clipping P 0.9595 / P 0.9578 / f 0.929;
  - -zero-size P ×3;
  - -clip-rect-zoom (unscored).
- **Compose host activation flips on exactly the 7 carrier documents and on no control** (`oofcb.hostFlips`: every M2 test
  plus position-relative-003, true → false). After the change nothing in them hoists, so `CanvasRootHoist.Host` takes its
  identity path.
  - The lane boxes render through the positioned-container Box branch, which is host-independent.
  - The harness root fold also reads `hostActive` (`ScreenshotCaptureScreen.kt` `composedRootStackPlan(root, hostActive)`,
    `composedRootsPaintingAboveFlow`). For these 7 documents the fold must equal the "no out-of-flow root" fold. This is
    risk R2.
- **Ring-fence.** `filter-effects/backdrop-filter-basic-blur` is inert by construction. Its three `backdrop-filter` boxes
  are LEAVES, with no descendant at all (wave53-final per-test IR), so the predicate claims them but nothing reads the claim.
  Report it plainly if it moves; never name it in code.

## 6. Ownership (disjoint from lists/markers, Compose table-body forest, web hyphens, harness label chrome, hyphenate-character)

**New files:**
- `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlock.kt`
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/layout/position/OutOfFlowContainingBlock.swift`
- `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/OutOfFlowContainingBlockTest.kt`
- `runtimes/swiftui/Tests/StyleConverterRuntimeTests/OutOfFlowContainingBlockTests.swift`

**Call-site lines only (both files are oversized):**
- `layout/position/CanvasRootHoist.kt` (1072 lines): :222, :340, :389.
- `Renderer/FixedHoist.swift` (538 lines): :309, :347, :115.
- Neither is a seam.

**Pins in existing suites:**
- rows added to `CanvasRootHoistTest.kt` and to the Swift FixedHoist tests;
- `TransformContainingBlockTest.kt` / `TransformContainingBlockTests.swift` stay green, unchanged.

**Seams:** none.
- Neither ComponentRenderer is edited. Compose reaches the predicate through `CanvasRootHoist`. iOS already overlays
  out-of-flow children at their parent.
- `extract-fixture.mjs` is not edited: the wire carries every input.
- The web runtime is untouched (web P everywhere).

**Shared cells:** contain-content-004 is shared with compose-table-body-cell (D2). No shared files.

## 7. Geometry probe — `tools/titan/results/wave54-plan/oof-containing-block.geometry.py [run]`

It is pure PIL over 28 PNGs and exits 1 if a ref row fails. The rule:
- the strict-green bbox equals the ref's ±1 px;
- the strict-red count equals the ref's ±2 %;
- where the ref has red (backdrop-filter-containing-block), the red bbox equals the ref's ±1 px.

Each line ends in `→ GEOMETRY OK` or `→ GEOMETRY WRONG (<why>)`. At wave53-final it prints OK for every ref and web row and
for iOS contain-content-003/011. It prints WRONG for the other 12 native rows, for example:

```
position-relative-004 android  {'green': (16, 16, 115, 115), 'greenPx': 10000, 'red': (16, 116, 115, 187), 'redPx': 7200} → GEOMETRY WRONG (green (16, 16, 115, 115) vs ref (16, 88, 115, 187))
```

After the lane, every native row must print `→ GEOMETRY OK`, with green (16, 88, 115, 187) and red 0. For
backdrop-filter-containing-block that means green (16, 88, 215, 287) and red (226, 88, 373, 287) at 29 600 px.

## 8. Predictions (from → to; simulated with the scorer's ssim.js in `queue-scout-layout.sim-ssim.mjs`)

**Sibling substitution.** For the green-square tests, the post-fix picture is the same platform's capture of a sibling test
with the same paragraph and the green square already right: position-relative-002 android, position-relative-006 ios.

| cell | from → to | confidence | floor |
|---|---|---|---|
| contain-content-003 android | f 0.9405 → **P ≈0.997** (sim 0.9967) | HIGH | 0.99 |
| contain-content-011 android | f 0.9284 → **P ≈0.985** (sim ≤0.9854, an upper bound because ref text replaces the hoisted box's area; web/ios with the same wrong counter sit at 0.9873/0.9854) | MED-HIGH | 0.97 |
| backdrop-filter-containing-block ios | f 0.7955 → **P** (sim bracket 0.9414 with the paragraph erased … 1.0 with the ref band) | MED | — |
| backdrop-filter-containing-block android | f 0.7506 → **P** (same bracket) | MED | — |
| position-relative-004 ios / android | P 0.9594 / 0.9589 → P 0.9974 / 0.9967, DEGENERATE → FAITHFUL | MED-HIGH | 0.99 |
| change-insets-inside-strict-containment-nested ios / android | P 0.9594 / 0.9589 → P 0.9974 / 0.9967, FAITHFUL | MED-HIGH | 0.99 |
| static-fixed-inside-abspos ios / android | P 0.9841 / 0.9835 → P 0.9974 / 0.9967, FAITHFUL | HIGH (ios) / MED-HIGH (android, via `PositionedParentFlowSlot`) | 0.99 |
| position-relative-003 ios / android | P 0.9594 / 0.9589 → P 0.9974 / 0.9967, FAITHFUL | MED: the inner relpos span chain must net 0, from `Top: 100` (bare %) then `-100px` | — |
| contain-content-004 ios / android | f 0.8094 / f 0.8286 → moves, stays f | MED | — |

**Net (lane alone):** Android +2 (HIGH / MED-HIGH), plus +1 Android and +1 iOS at MED. The ceiling is iOS +1 and Android +3.
8 passes stop being DEGENERATE.

**Must not move (byte-identical PNGs, verified by `control-check` on decoded pixels):**
- every web capture;
- iOS contain-content-003 / -011 (abspos is unchanged on iOS);
- the 26 `fixedControls` tests;
- the 4 `establisherControls` tests;
- every transform-CB container with out-of-flow children. Wave 35 enumerated five, and the comment at ComponentRenderer.kt
  :3455–3470 names backface-visibility-hidden-001's `.card` as the one its OR flipped. Add 3d-rendering-context-and-fixpos
  P ×3. The transform clause is OR-ed and never edited;
- every other out-of-flow box in the corpus.

**Report-only watch:** backdrop-filter-basic-blur ×3 (ios P 0.9526 / android f 0.904 / web P 1).

## 9. Verification plan

**Pins (Kotlin and Swift twins, on VERBATIM wave53-final per-test IR):**
- **P1 — the rule table:**
  - contain PAINT / STRICT / CONTENT / LAYOUT → true;
  - SIZE / INLINE_SIZE / STYLE / NONE → false;
  - Filter `[invert]` → true; BackdropFilter `[blur]` → true;
  - WillChange `backdrop-filter` → true; WillChange `opacity` → false;
  - the same BackdropFilter on a `Position ABSOLUTE` box → false.
- **P2 — the hoist decision table:**
  - FIXED with no insets → no hoist;
  - FIXED with `Top` and no establisher → hoist;
  - FIXED with `Top` under a contain:strict ancestor → no hoist;
  - ABSOLUTE with `Top` under a static contain:content parent → no hoist.
- **P3 — the pure walks on the verbatim payloads:**
  - `collectCanvasHoisted` / `FixedHoist.split` hoist NOTHING for the 6 M2 and 2 scored M1 tests;
  - they hoist exactly what they hoist today for backdrop-filter-clip-rect, -edge-clipping, backface-visibility-hidden-004,
    anchor-name-006 and position-fixed-dynamic-transformed-sibling.
- **P4 — host:** `hostActivates` turns false for exactly the 7 documents in `oofcb.hostFlips`.

**Mutations (each must turn a named pin red):**
- M-a: drop the contain clause → P1 rows 1–4 and P3 change-insets / -004 / contain-content-003.
- M-b: drop `hasAnyInset` from F3 → P2 row 1 and P3 static-fixed / -003.
- M-c: drop F1's "not itself absolute/fixed" restriction → P1 last row.
- M-d: on Swift, revert :309 → P3 Swift change-insets.

**Device:** one probe of css-position, css-contain, CSS2, filter-effects and css-transforms before the closing gate, read
with `oof-containing-block.geometry.py <probe-run>`. Then run `control-check` against the fixed and establisher controls.

## 10. Risks

- **R1 — the Compose positioned-container Box branch now hosts STATIC containers** (the contain divs, the td, the backdrop
  container). This is the wave-35 precedent, one level wider. The in-flow children of those containers are the risk.
  - contain-content-004's td holds an in-flow `<span>FAIL</span>`. That is why it is "moves, stays f".
  - For backdrop-filter-containing-block, the container's auto height must stay 0, because both children are out of flow
    (§10.6.3).
- **R2 — Compose host activation turns off in 7 documents.** The harness root fold reads `hostActive`, and none of these
  documents has an out-of-flow ROOT, so it should be the plain fold. Only the device can confirm it. Watch the paragraph
  pitch in the probe PNGs (text at y≈44–83).
- **R3 — the iOS static position is the parent overlay origin**, not a true in-flow slot. It is exact for both carriers,
  because the fixed box is the parent's first content, and wrong for a fixed box after in-flow siblings (0 carriers). Say
  so in the banner.
- **R4 — paint containment does not clip.** `contain: paint` clips descendants to the padding box, and Compose / SwiftUI do
  not implement that. Out-of-flow boxes that used to escape to the canvas now render inside the contain box but unclipped.
  This is harmless for every carrier (all fit), and is a named limitation.
- **R5 — backdrop-filter two-pass.** The children now render INSIDE a `backdropFilterTwoPass` element. The Compose SAMPLE pass
  suppresses the element's children by design (`BackdropModifier.kt` banner), and the COMPOSITE pass paints them normally.
  Confirm on device that the green and red are NOT inverted: the patch area is the container's 200×0 border box, which is
  empty.
- **R6 — the counter in contain-content-011** ("25" on all three platforms) caps that cell at about web's 0.9873. A flip is
  still predicted, at the 0.97 floor.

**Recommendation: GO**, one lane, effort M: 2 new pure files plus 6 one-line call sites, 4 pin files and one device probe. It
gives 4 flips (2 at HIGH / MED-HIGH) and 8 DEGENERATE → FAITHFUL cells. There is no seam, and file ownership is disjoint.

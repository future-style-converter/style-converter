# positioned-parent-slot — wave-55 lane brief L3 (BACKLOG "Next-wave obligations" 0(i))

**Evidence run:** `tools/titan/runs/wave54-final` (HEAD 0d0d3a3d, the wave-54 merge). Scores come from one of two sources:
- `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave54-open wave54-final`, saved as
  `positioned-parent-slot.cells.txt`;
- the gate's own composed metric (`inject-wpt-block.mjs` `diffWebVsRef`, plus its two exported vetoes) run by
  `positioned-parent-slot.replay-score.mjs`.

**Conventions.** Every PNG named below was opened, next to its frozen ref
(`tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`). The side-by-side strips are in
`positioned-parent-slot-look/`. Line numbers are at 0d0d3a3d. Seam-file sha256 at base:
- `ComponentRenderer.kt` (Compose) `2445d9ea…a026aa5`;
- `PositionedParentFlowSlot.kt` `67d69d61…7ffbc53b`;
- `CanvasRootHoist.kt` `36777550…77565603`.

**Nothing was built, run or captured.** The opening gate `wave55-open` is live on this host. Every script below only
reads files.

**Files written by this lane (all in `tools/titan/results/wave55-plan/`):**
- `positioned-parent-slot.census.mjs` → `.census.out.txt` / `.census.json`: 1435 per-test IR docs, plus the scorer's
  `loadRun`.
- `positioned-parent-slot.replay.py` → `positioned-parent-slot-replay/*.png`: built from the device's OWN pixels.
- `positioned-parent-slot.replay-score.mjs` → `.replay.out.txt`.
- `positioned-parent-slot.geometry.py`, with these executed outputs:
  - `.geometry.wave54-final.txt`;
  - `.geometry.replays.txt`;
  - `.geometry.control-can-fail.txt`.
- `positioned-parent-slot.cells.txt` and `positioned-parent-slot-look/`.

## Summary

The obligation as written is true. `PositionedParentFlowSlot.mount` is gated on `CanvasRootHoist.LocalActive`
(`ComponentRenderer.kt:3650-3661`). A host-inactive `absolute`/`fixed` parent therefore stacks its out-of-flow children in
the block Column. But three things in the BACKLOG item need correcting before anyone builds it:

1. **The radius is 8 parents in 5 documents, not 10 in 6.**
   - The wave-54 census (`wave54-plan/rtl-marker-bake.census.mjs`) did not exclude the Box branch.
   - `content-visibility-auto-shared-element`'s two parents carry `Transform matrix(…)`. Through
     `transformCbHostsOutOfFlowChild` they take the positioned-container Box branch (`:3504-3512`) and never reach the
     Column loop.
   - Either way, the mount reaches every host-inactive Column-loop parent, not only the ≥ 2-children shape: **53 parents
     (65 out-of-flow children) in 17 documents**.
2. **"Its three dots" is half of `dir-style-02a`'s defect.**
   - Rows 6 and 9 are also MIRRORED: their text is painted at x246-373 instead of x17-153.
   - Mounting the slot as it is today, with no host gate, does not fix that. It does worse: under an RTL parent, the
     mounted slot sits at the parent's END edge. That is device-measured on four host-ACTIVE documents that already get
     the mount: `dynamic-offset-rtl-001` android paints its magenta box at x186, which is 16 + 100 + 70, where the ref
     has x86.
   - With that literal fix, dir-style-02a rows 6/9 go blank and the cell still "passes" at 0.9663. That is a
     DEGENERATE pass. `bidi-tab-001` android drops from 0.9805 to 0.977.
   - The dots alone (U1) move the score by +0.0003.
3. **The fix is therefore two separately revertible units.**
   - **U1** engages the mount for host-inactive composed captures whose parent is laid out LTR.
   - **U2** gives an RTL parent's slot its physical-left origin (CSS 2.1 §10.3.7: `left` is physical).
   - U1 + U2 replay dir-style-02a android at **P 0.9866**.
   - U2 also makes four currently passing DEGENERATE cells faithful: bidi-tab-001 and three `dynamic-offset-*rtl*`
     documents.

## 1. Targets (cells at `wave54-final`, re-derived) and what the pictures show

| cell | wave54-open → **wave54-final** | label |
|---|---|---|
| `selectors/dir-style-02a android` | f 0.9425 → **f 0.9425** | the target; three dots missing and rows 6/9 mirrored |
| `selectors/dir-style-02a ios` | f 0.9455 → **f 0.9455** | rows 6/9 mirrored (iOS twin defect, not this lane, §9) |
| `selectors/dir-style-02a web` | P 1 → **P 1** | — |
| `css-text/bidi/bidi-tab-001 android` | P 0.9805 → **P 0.9805** | DEGENERATE: four RTL rows' `0` misplaced |
| `css-writing-modes/dynamic-offset-rtl-001 android` | P 0.9952 → **P 0.9952** | DEGENERATE: magenta outside the yellow box |
| `css-writing-modes/dynamic-offset-vrl-rtl-001 android` | P 0.9952 → **P 0.9952** | same |
| `css-writing-modes/dynamic-offset-rtl-002 android` | P 0.9953 → **P 0.9953** | same; the ref is post-script (§2.4) |
| `css-writing-modes/dynamic-offset-vrl-rtl-002 android` | P 0.9952 → **P 0.9952** | same |
| `css-anchor-position/anchor-position-multicol-nested-001 android` | f 0.9573 → **f 0.9573** | stacked squares; coverage veto |

### dir-style-02a

Ref (`…/selectors/dir-style-02a.png`), web, iOS and Android are side by side in
`positioned-parent-slot-look/dir-style-02a.ref-web-ios-android.png`. The page has eleven 20-px rows from y16, each a green
or blue line. `.`-led rows read ".This element is rtl" in the ref.

Measured per row (ink = min channel < 200):

| row (y band) | wire box | ref ink | android ink | iOS ink |
|---|---|---|---|---|
| 6 (116-136) | `dir-style-02a__3__2-092` ABSOLUTE, **Direction RTL**, W358 H20; runs `This element is rtl` (Left 4.61, W133.88) + `.` (Left 0) | x17-153, dot at x17-18 y130-131 | **x246-373, no dot** | x236-371, dot painted after the text |
| 7 (136-156) | `__4__0-096` ABSOLUTE, LTR (bake root `__5-095`), same two-run shape | x17-243, dot | **x22-243, no dot** | x17-243, dot |
| 9 (176-196) | `__4__2-101` ABSOLUTE, Direction RTL, as row 6 | x17-153, dot | **x246-373, no dot** | x236-371 |
| 3 (56-76) | `__3-084` RELATIVE bake root, runs as row 6 (Box branch) | x17-153 | **x17-153, dot** (right) | right |

- Row 3 is the control inside the document: the same two runs, owned by a RELATIVE root, paint right on Android.
- Rows 1-5, 8, 10 and 11 agree with the ref on Android.
- `dir-style-02a__3__0` and `__3__1` (rows 4/5) are host-inactive single-run parents. They are already in the right
  place, because the first child sits at cursor 0.

### bidi-tab-001

Strip: `positioned-parent-slot-look/bidi-tab-001.ref-web-ios-android.png`.

- Ref: alternating rows `0▮` / `▮0`. The four RTL boxes `bidi__bidi-tab-001__{2,3,7,9}__0` (Direction RTL, W108.38 H24)
  each hold one `0` run.
- Android blue-ink x-extents per RTL band (ref in brackets):
  - y86 x16-135 (ref x16-38): the `0` sits at x127-135, mirrored;
  - y106 x124-219 (ref x114-135): the `0` sits at x210-219, outside the yellow box;
  - y186 / y226 the same as y86.
- The LTR rows match the ref.
- iOS mirrors the same four rows (§9).

### dynamic-offset-rtl-001 / -vrl-rtl-001

A yellow 100×100 `position: absolute; direction: rtl` box with a magenta 10×10 child at `left: 70; top: 10`.
- Magenta bbox:
  - ref (86,26)-(95,35);
  - web the same;
  - **android (186,26)-(195,35)**, outside the yellow box (yellow pixel count 10000 against the ref's 9900);
  - iOS (36,26)-(45,35).
- Both documents are host-ACTIVE, so this is the mount TODAY under RTL.
- **-002 / -vrl-rtl-002** carry the pre-script `left: 10` / `70` on the wire, while their refs show the post-script state:
  - -002: web (26,26), android (126,26);
  - -vrl-rtl-002: web (86,96), android (186,96).

### anchor-position-multicol-nested-001

- web squares: (190,78) (360,78) (190,94) (270,94) (350,116) (190,168) (360,168).
- Android: (190,78) **(360,88)** (191,90) (271,100) (351,132) **(190,188) (360,198)**.
- The ABSOLUTE parent `__1__2-776` holds four 10×10 children at tops 0/0/90/90. Android paints them at +0/+10/+20/+30:
  the Column cursor.
- None of the three platforms paints the ref's tall green fragments. Web passes anyway: DEGENERATE.

## 2. Mechanism (traced at 0d0d3a3d)

### 2.1 The gate

`ComponentRenderer.kt` `RenderContent`, `renderBlockChild` (`:3598`). The mount at `:3650-3661` is:
```kotlin
val outOfFlowSlotMount: Modifier =
    if (CanvasRootHoist.LocalActive.current && PositionedParentFlowSlot.applies(child.properties, component.properties))
        PositionedParentFlowSlot.mount(child.properties) else Modifier
```
- `applies` = child ABSOLUTE|FIXED and parent ABSOLUTE|FIXED|STICKY (`PositionedParentFlowSlot.kt:127`).
- `LocalActive` is provided only by `CanvasRootHoist.Host` (`CanvasRootHoist.kt:991`).
- `Host` activates iff `hostActivates(roots)` = `anyOutOfFlowBox` (`:669`, `:572`): some box hoists (`:322`) or is an RC1
  static-position box (`:380`). Otherwise it takes the identity fast path (`:1041`).
- `Host` is called only from the harness's `ComposedCaptureCanvas` (`ScreenshotCaptureScreen.kt:2062`), inside the one
  provider of `LocalWptComposedMode provides true` (`:587`).
- So `LocalActive ⇒ composed capture`. The dark stage, the per-component inbox path and every committed baseline see
  both false.
- The header comment ("Host-gated like RC1", `PositionedParentFlowSlot.kt:87-89`) gives baseline byte-stability as the
  only reason for the gate. `LocalWptComposedMode` gives the same protection without depending on whether the document
  happens to activate the host.

### 2.2 What the plain Column loop does

The arithmetic for child k of a positioned parent of content box W × H:
- The Compose Column hands each non-weighted child the REMAINING main-axis space (the `PositionedParentFlowSlot.kt`
  header).
- Today, without the mount: `y_k = Σ_{j<k} h_j + top_k`, measured with `maxHeight = H − Σ_{j<k} h_j`.
- `x_k = align_Start(w_k) + left_k`, with:
  - `align_Start = 0` (LTR) or `W − w_k` (RTL): the Column's default `Alignment.Start`, which `Alignment.Horizontal.align`
    resolves to `space − size` under `LayoutDirection.Rtl`;
  - `left_k` is PositionApplier's PHYSICAL `absoluteOffset` (`PositionApplier.kt:227-238`).
- An RTL Column exists because `ComponentRenderer.kt:2189-2195` provides `LocalLayoutDirection Rtl` for a `Direction: RTL`
  box. Nothing re-provides Ltr below it.

### 2.3 What the spec says

- **Out of flow.** CSS 2.1 §9.3.1 and §9.6, css-position-3 §2.1: an absolutely positioned box is out of normal flow and
  has no effect on later siblings. So every out-of-flow sibling's static position is the parent's content-box start.
  CSS 2.1 §10.6.4 defines it through the hypothetical first box, so `y_k = top_k`.
- **Containing block.** CSS 2.1 §10.1 item 4: the parent is the containing block.
- **Inline axis.** CSS 2.1 §10.3.7 and css-position-3 §3.1: a declared `left` with `right: auto` is the offset from the
  containing block's LEFT edge, whatever its `direction`. Direction only picks:
  - which of `left`/`right` takes the static position when both are `auto`;
  - which one is ignored when the box is over-constrained.
- **Direction.** css-writing-modes-4 §2.1: `direction` sets the inline base direction, not the physical meaning of
  `left`. So `x_k = left_k` under RTL too.

### 2.4 The mount, and why it is not enough under RTL

`PositionedParentFlowSlot.mount` is `CanvasRootHoist.zeroFlowAnchor` (`:806`):
- it measures with `Constraints()`, or the §10.3.7 shrink-to-fit band for a width-auto child;
- it reports 0×0 (`hoistedFlowReportPx`);
- it places the ink at the slot origin with the UNMIRRORED `placeable.place`.

That fixes y: `y_k = top_k`. For x, the 0-wide slot is placed by the RTL Column at `align_Start(0) = W`, so
`x_k = W + left_k`.

**Device proof (host-active today):** `dynamic-offset-rtl-001` android magenta at x186 = 16 + **100** + 70. Every
`*-rtl-*` document in §1 fits `16 + W + left`.

Worked cases:

| case | today | plain mount (U1 without the guard, "PLAIN") | U1 + U2 | ref |
|---|---|---|---|---|
| dir-style-02a row 7 `.` (LTR, H20; run 1 takes the full 20 px) | maxHeight 0 → not painted | (16, 136) | (16, 136): dot ink y150-151 | dot y150-151 |
| row 6 text (RTL, W358) | 16+358−133.88+4.61 = **244.7** (ink x246) | 16+358+4.61 = **378.6** (past the 374 box edge; today's capture already clips row 6 at x373) | 16+358−358+4.61 = **20.6** | x21 |
| row 6 `.` | maxHeight 0 → not painted | 374 | **16** | x17 |
| bidi-tab-001 row 2 `0` (box Left 12.03, run Left 0, w12.05) | 16+12.03+96.33 = **124.4** (ink x127) | 136.4 | **28.0** (band x16-38) | x16-38 |
| bidi-tab-001 row 3 `0` (box Left 0, run Left 96.33) | 16+96.33+96.33 = **208.7** (ink x210) | 220.7 | **112.3** | x114-135 |
| dynamic-offset-rtl-001 magenta (W100, left 70) | — (host-active: already mounted) | **186** (today) | **86** | 86 |
| nested-001 `__1__2` squares (H100, tops 0/0/90/90) | y78/88/188/198 | y78/78/168/168 | same | web 78/78/168/168 |

### 2.5 The fix

- **U1:** engage = `hostActive || (composedCapture && !parentRtl)`.
  - Under an active host, every parent keeps today's mount byte for byte (54 parents, 44 documents).
  - A host-inactive composed capture mounts LTR parents.
  - RTL parents stay unmounted under U1, with a PropertyTracker breadcrumb (repo rule "no silent fallthroughs").
- **U2:** when the parent is laid out RTL, its width is definite, and the child resolves an inline inset (`left` or
  `right`), an outer layout modifier places the zero-flow anchor at `x = −constraints.maxWidth`.
  - The RTL Column puts the 0-wide slot at `colWidth`, and `colWidth = maxWidth` for a definite-width parent (the
    width modifier makes the Column's constraints exact). So the ink lands at `colWidth − maxWidth + left = left`.
  - A `right`-only child: the slot's end anchor gives `maxWidth − w`, plus its own `absoluteOffset(−right)`, which comes
    to `colWidth − w − right` (physical right, correct).
  - An RTL parent of auto width, or a child whose inline insets are both `auto`, keeps today's placement with a
    breadcrumb. No carrier has either shape (§5): all 11 RTL Column-loop parents declare a px Width, and all 13 of their
    children declare `left`.
- Both units read the PARENT's layout direction at the seam (`LocalLayoutDirection.current` inside `renderBlockChild`,
  which is the parent's content scope). They never read the child's measure-scope direction: a `Direction: RTL` child
  under an LTR parent provides Rtl around its own content, and that must not trigger the origin.

## 3. Ownership (disjoint from L1, L2, L4–L7)

**Owned, Compose only:**

| file | unit | edit |
|---|---|---|
| `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGate.kt` | U1 (new), U2 | `engages(hostActive, composedCapture, parentRtl)` pure, plus `slotModifier(childProps, parentProps, hostActive, composedCapture, parentRtl)`, the call-site helper with the breadcrumbs; ≤ 200 lines |
| `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOrigin.kt` | U2 (new) | `applies(parentRtl, parentProps, childProps)`, `originPx(boundedMaxPx)`, the pure `inkLeftPx(...)` model, and the outer `Modifier.layout`; ≤ 200 lines |
| `runtimes/compose/src/main/java/com/styleconverter/runtime/layout/position/PositionedParentFlowSlot.kt` (169 lines) | U1: the `:87-89` header paragraph re-trued (comment only). U2: `mount(childProperties, rtlOrigin: Boolean = false)`, a defaulted parameter plus one `.then` | — |
| `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/PositionedParentSlotGateTest.kt` | U1 (new) | — |
| `runtimes/compose/src/test/java/com/styleconverter/runtime/layout/position/PositionedParentRtlOriginTest.kt` | U2 (new) | — |
| `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/SeamReachabilityTest.kt` | U1 line patch-borne in seam 1; U2 line in U2's commit | its `modules` list gains `layout/position/PositionedParentSlotGate.kt` (U1) and `…/PositionedParentRtlOrigin.kt` (U2), the wave-52 "lands in the SAME commit as that seam" rule. **Shared test file:** if L7 holds the test-file pins this wave, these two list lines are L7 hand-offs. |
| `tools/titan/results/wave55-positioned-parent-slot/` | — | lane note, mutation logs, the seam patch |

**Seam (a patch, never a direct edit):**
- **seam 1:** `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/ComponentRenderer.kt`, region
  `RenderContent` → `renderBlockChild`, the `outOfFlowSlotMount` val and its comment (`:3633-3661`).
  - It replaces the `CanvasRootHoist.LocalActive.current && PositionedParentFlowSlot.applies(…)` if/else with one call:
    `PositionedParentSlotGate.slotModifier(child.properties, component.properties, hostActive = CanvasRootHoist.LocalActive.current, composedCapture = LocalWptComposedMode.current, parentRtl = LocalLayoutDirection.current == LayoutDirection.Rtl)`.
    `LocalLayoutDirection` and `LayoutDirection` are already imported at `:22` / `:27`.
  - It re-trues the "Host-gated exactly like RC1" comment.
  - Patch-borne files: the new `runtimes/compose/src/test/java/com/styleconverter/runtime/core/renderer/PositionedParentSlotSeamWiringTest.kt`
    (the `StaticPositionSeamWiringTest` idiom), and the `SeamReachabilityTest.kt` U1 list line.
  - U2 needs no seam hunk, because seam 1 already passes `parentRtl` and the parent properties.
- No `ComponentRenderer.swift`, `ComponentRenderer.tsx` or `extract-fixture.mjs` hunk.

**Read-only:**
- `CanvasRootHoist.kt` (1091 lines). It is reserved to L3 this wave so no other lane edits the activation walk or
  `zeroFlowAnchor`, but neither unit needs a hunk: `hostActivates` keeps its meaning, so its harness consumers
  `ScreenshotCaptureScreen.kt:1821` and `UaBlockMargins.kt` are untouched.
- `PositionedParentFlowSlotTest.kt`: must stay green, including the one-element pin on `mount(…)` at `:161`.
- `PositionApplier.kt`, `PositionedAncestorAnchor.kt`, every web/iOS file.

**Docs (orchestrator hand-offs):**
- BACKLOG 0(i) re-true: 8 parents / 5 docs; reach 53 / 17 plus the 4 host-active RTL documents; dots alone = +0.0003.
- The new SwiftUI twin item (§9 R7).
- The Compose test count in `CLAUDE.md` / `docs/STATUS.md` (`doc-staleness-check.sh`).
- Neither new file is an `<Name>Applier`, so the coverage-audit `real` floor does not move.

**Overlap check:**
- L7 U5's radius is the 17 bidi-baked documents (`instruments.md` §6). It predicts every non-list wire identical, and
  covers dir-style-02a, bidi-tab-001, hyphenate-character-005 and four of my controls.
  - The two lanes share wire stems, not files.
  - If L7's wire moves, my identity controls (§7) say so.
- L5 carries counter-suffix (`counter-suffix-rows.*`). Its four `<li>` boxes are identity-class for U1, but it is NOT
  one of my controls.
- L6's paint-order census carries `backdrop-filter-edge-clipping`, an active-host stack parent. It is NOT a control.
- L4's UA-margin census fires on `anchor-center-safe-rtl`'s two `<p>` boxes. It stays a control (BACKLOG names it); plan
  synthesis drops it if L4 carries it.

## 4. Units (one commit each; landing and revert order U1 then U2, `revertOrder U1 = [U2, U1]`)

### U1 — composed-capture engagement (LTR parents)

**Code:** `PositionedParentSlotGate.engages` plus seam 1.

**Predicts:**
- dir-style-02a android row 7 gets its dot: f 0.9425 → f ≈0.9428 (replay U1).
- anchor-position-multicol-nested-001 android f 0.9573 → f ≈0.969 (replay 0.9688). The **coverage veto holds**: capture
  ink 1.079 % against ref 2.239 % is a ratio of 2.07, over the limit of 2 (`computeCoverageRatioFailed`). Web passes at
  1.128 / 2.239 = 1.98.
- anchor-position-multicol-004 android stays P. It is a LOW mover: its mounted green children sit inside the clipped
  multicol, and its red is unchanged.
- The 13 U1-control documents stay byte-identical (§5).
- RTL parents are unmounted (logged).
- 0 lost.

**Pins (JVM, verbatim wire; each mutation executed red → restore → green, with sha256):**
- (a) Truth table:

  | hostActive | composedCapture | parentRtl | `engages` |
  |---|---|---|---|
  | T | * | * | T |
  | F | T | F | T |
  | F | T | T | F |
  | F | F | * | F |

  Mutations:
  - drop `composedCapture &&` → the dark-stage row goes red;
  - drop `!parentRtl` → the RTL row goes red;
  - collapse to `hostActive` → the (F,T,F) row goes red.
- (b) On the verbatim `wave54-final` per-test IR of `selectors/dir-style-02a`, the mounted children are exactly those of
  `__3__0`, `__3__1`, `__4__0` and `__4__1`, and not those of `__3__2` or `__4__2`. Mutation: drop `!parentRtl`.
- (c) Seam wiring (patch-borne): `renderBlockChild` calls `PositionedParentSlotGate.slotModifier(` with
  `LocalWptComposedMode.current` and the parent `LocalLayoutDirection`, and no `LocalActive.current &&` guard remains on
  the mount. Mutation: the pre-seam text goes red.
- (d) `SeamReachabilityTest` with the U1 line. Mutation: un-apply seam 1 → `dead` names the gate file.

### U2 — RTL physical-left origin (any host state)

**Code:** `PositionedParentRtlOrigin`, plus the RTL branch of the gate. `mount(…, rtlOrigin = true)` adds one outer layout
element.

**Predicts:**
- dir-style-02a android → **P ≈0.987** (U1 + U2 replay 0.9866, no veto).
- bidi-tab-001 android P 0.9805 → P ≈0.990 (replay 0.9901): picture-correct.
- dynamic-offset-rtl-001 and -vrl-rtl-001 android → P ≈1 (replay 1): picture-correct.
- -rtl-002 → 0.9956 and -vrl-rtl-002 → 0.9955, equal to web's own picture.
- hyphenate-character-005 android: a LOW mover. Its kashida run moves 4.5 px left (27.95 → 23.45).

**Pins:**
- (a) `originPx(358) == −358`, `originPx(null) == null`.
- (b) `applies` truth table:

  | case | `applies` |
  |---|---|
  | RTL, Width px, child `left` | T |
  | LTR | F |
  | RTL, no Width | F (breadcrumb) |
  | RTL, child with no inline inset | F (breadcrumb) |
  | RTL, child `right` only | T |

- (c) `inkLeftPx(colW, maxW, rtl, origin, left)`:

  | inputs | result |
  |---|---|
  | (100, 100, rtl, no origin, 70) | 170 (the device's x186 − 16) |
  | (100, 100, rtl, origin, 70) | 70 (ref x86 − 16) |
  | (358, 358, rtl, origin, 4.61) | 4.61 |
  | LTR | `left` |

  Mutations: drop the origin; use `placeRelative` semantics (`−w − x`).
- (d) The verbatim IR of `bidi-tab-001` `__2__0` / `__3__0` and `dynamic-offset-rtl-001` `__1`: `applies` is T.
- (e) `PositionedParentFlowSlotTest`'s one-element pin stays green (the default `rtlOrigin = false`), and a new two-element
  pin covers `rtlOrigin = true`.

**Never ship U1 without the RTL guard ("PLAIN", the BACKLOG's literal "drop the LocalActive gate").** It is a pre-registered
loss: dir-style-02a passes DEGENERATE at 0.9663 with rows 6/9 blank, and bidi-tab-001 drops to 0.977.

**Suites, after the gate releases the host:**
- focused: `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*PositionedParent*' --tests '*SeamReachabilityTest' --tests '*CanvasRootHoistTest' --tests '*StaticPositionSeamWiringTest' --tests '*OutOfFlowContainingBlockTest')`;
- then the orchestrator's one full sweep.

## 5. Carrier census (`positioned-parent-slot.census.mjs` → `.census.out.txt`, run `wave54-final`)

**How the census reads the wire.**
- It ports `anyOutOfFlowBox` with the wave-54 L4 predicates: `OutOfFlowContainingBlock`, the FIXED `declaresAnyInset`
  arm, and the multicol spanner restart.
- Approximations, stated in the script's banner: the clip-path and contain decoders, and the multicol and spanner tests.
- It excludes Box-branch parents (`:3504-3512`).
- It classifies every reached parent:
  - **STACK**: an out-of-flow child follows one of non-zero height;
  - **CLAMP**: a child is wider or taller than the parent's declared box;
  - **AUTO-W / AUTO-H**: a width-auto child, or an auto-height parent;
  - **INFLOW**: in-flow siblings follow (0 occurrences);
  - **RTL**.

**Corpus totals.** Of the 1435 docs, the host is inactive on 1221.

| reach | count |
|---|---|
| Column-loop containing-block parents with ≥ 1 out-of-flow child | 107 in 61 docs |
| — host-active (mounted today) | 54 / 44 docs (14 with ≥ 2 children) |
| — host-inactive | **53 / 17 docs** (8 / 5 docs with ≥ 2 children) |
| Box-branch parents (unaffected) | 28 |
| classes of the host-inactive parents | identity 38 · STACK 6 · RTL 7 · CLAMP 2 · AUTO-W 2 · AUTO-H 2 |
| RTL Column-loop parents (CSS direction; Compose's used direction agrees on every one) | 11 / 7 docs (7 inactive, 4 active) |

None of the 69 reached children carries auto inline margins, `justify-self`, `align-self`, `float` or `clear`.

**Capture carriers, android only.** Web, iOS and the wire carry nothing: no web, iOS, converter or extractor file
changes.
- **U1 (3):**
  - `wpt__selectors__dir-style-02a`;
  - `wpt__css-anchor-position__anchor-position-multicol-nested-001`;
  - `wpt__css-anchor-position__anchor-position-multicol-004`.
- **U2 (7):**
  - `wpt__selectors__dir-style-02a`;
  - `wpt__css-text__bidi__bidi-tab-001`;
  - `wpt__css-text__hyphens__hyphenate-character-005`;
  - `wpt__css-writing-modes__dynamic-offset-rtl-001`, `-rtl-002`, `-vrl-rtl-001`, `-vrl-rtl-002`.
- **Union: 9.**

**U1 controls — reached, predicted byte-identical (13).**
- **Identity:**
  - `attachment-local-positioning-3/-4` (unscored);
  - `counter-suffix` (L5's carrier);
  - `bidi-lines-002`;
  - `bidi-tab-001` (U1 alone; RTL rows excluded);
  - `boundary-shaping-009`;
  - `css-tags-paint-order` and `-with-entry`;
  - `dir-selector-change-003` and `-004`: their 2-child parents' first child is H0, so cursor 0 = top 0.
- **CLAMP:**
  - `block-end-aligned-abspos-nested`: the unbounded 50×50 child is green inside the 100×100 green multicol
    background, so green on green;
  - `position-absolute-scrollable-overflow-zero`: an H0 child, no ink.
- **AUTO-W/H:** `text-decoration-subelements-002`. The parent is measured unbounded by `absposOverflowMeasure`, so the
  shrink-to-fit band is unbounded too, and the text keeps its single line.

**Currently-passing cells whose render path changes.**
- U1: 10 android cells.
  - `anchor-position-multicol-004` P 0.9686, `block-end-aligned-abspos-nested` P 0.9967, `counter-suffix` P 0.989;
  - `position-absolute-scrollable-overflow-zero` P 0.9984, `bidi-lines-002` P 0.9818, `bidi-tab-001` P 0.9805;
  - `css-tags-paint-order` P 0.9791, `css-tags-paint-order-with-entry` P 0.9791;
  - `dir-selector-change-003` P 0.9994, `-004` P 0.9944.
- U2: 5 android cells.
  - `bidi-tab-001` P 0.9805;
  - `dynamic-offset-rtl-001` P 0.9952, `-rtl-002` P 0.9953, `-vrl-rtl-001` P 0.9952, `-vrl-rtl-002` P 0.9952.
- Failing cells reached: `dir-style-02a`, `anchor-position-multicol-nested-001`, `boundary-shaping-009` f 0.7531,
  `text-decoration-subelements-002` f 0.9047, `hyphenate-character-005` f 0.9474.

## 6. Predictions (wave54-final → the wave-55 closing gate; floors on geometry-gated rows = 0.95)

All replays use the device's own pixels (`.replay.out.txt`). Each calibration row reproduces cells.mjs exactly, so no
ref-pixel raster allowance applies.

| cell | from | → predicted | tier | floor · geometry key | dir | unit |
|---|---|---|---|---|---|---|
| `selectors/dir-style-02a android` | f 0.9425 | **P ≈0.987** (replay 0.9866) | MED-HIGH | 0.95 · `[S] … row6` / `row7` / `row9` gating | up | U1+U2 |
| same, U1 only | f 0.9425 | f ≈0.943 (0.9428) | HIGH | — · `row7` | up | U1 |
| `css-text/bidi/bidi-tab-001 android` | P 0.9805 | P ≈0.990 (0.9901); DEGENERATE → faithful | MED-HIGH | 0.95 · `[S] bidi-tab-001 android rtl-rows` gating | up | U2 |
| `css-writing-modes/dynamic-offset-rtl-001 android` | P 0.9952 | P ≈1 (1); DEGENERATE → faithful | MED-HIGH | 0.95 · `[S] dynamic-offset-rtl-001 android` gating | up | U2 |
| `css-writing-modes/dynamic-offset-rtl-002 android` | P 0.9953 | P ≈0.9956 (web's picture; stays DEGENERATE: the ref is post-script) | MED | 0.95 · `[W] dynamic-offset-rtl-002 android` gating | ≈flat | U2 |
| `css-writing-modes/dynamic-offset-vrl-rtl-001 android` | P 0.9952 | P ≈1 (1) | MED (vertical-rl, R2) | 0.95 · report key | up | U2 |
| `css-writing-modes/dynamic-offset-vrl-rtl-002 android` | P 0.9952 | P ≈0.9955 (web's picture; DEGENERATE) | MED | 0.95 · report key | ≈flat | U2 |
| `css-anchor-position/anchor-position-multicol-nested-001 android` | f 0.9573 | **f** ≈0.969 (coverage veto holds; NOT a flip) | MED | — · `[W] … stack` gating (mechanism) | up | U1 |
| `css-anchor-position/anchor-position-multicol-004 android` | P 0.9686 | P, undirected, \|Δ\| ≤ 0.01; red unchanged | LOW | 0.95 must-not-lose · `[C] … red` control | — | U1 |
| `css-text/hyphens/hyphenate-character-005 android` | f 0.9474 | f, undirected | LOW | — | — | U2 |

**Totals:** Android **+1** flip (dir-style-02a), **0 lost**, 0 newly measured or unmeasured; web and iOS 0.

**Must not move, byte-identical:**
- Every Android capture outside the 9 carriers: 1426. That includes 12 of the 13 U1 controls and the 40 host-active LTR
  documents mounted today. The 13th control, `bidi-tab-001`, is a U2 carrier: under U1 alone the whole capture stays put,
  and under U1 + U2 only its LTR bands do.
- All 1435 web and all 1435 iOS captures.
- The 1435 per-test IR documents.
- The fixture net exits 0 with committed baselines byte-identical: the dark stage and per-component inbox path have
  `composedCapture = false`, so `engages = false`, the identity modifier, as today.

`stayDegenerateEvenIfPass`: `dynamic-offset-rtl-002` and `-vrl-rtl-002` android (the wire lacks the scripted inset), and
`anchor-position-multicol-004` android (red visible).

## 7. Geometry probe — `positioned-parent-slot.geometry.py <run> [<base>] [--replay ARM]` (written, run)

**Format.** Every line starts with its key and ends in `→ GEOMETRY OK` or `→ GEOMETRY WRONG (<why>)`. The exact expect
string for every key below is `→ GEOMETRY OK`. Every `[C]` key also `contains` `identical to <base>`.

**Self-check:**
- every `[S]` ref row and every `[W]` web row must print OK, else exit 1;
- `[W]` rules describe the WIRE's picture (CSS itself on the same wire), because those refs show a post-script state.

| key | class | unit | rule |
|---|---|---|---|
| `[S] dir-style-02a android row6` | gating | U2 | ink starts ≤ x18 and ends at the ref's x ±3 (153); nothing past +10; ≥ 2 dark px of the dot at x15-19, y+12..+16 |
| `[S] dir-style-02a android row7` | gating | U1 | same rule (ref right edge 243) |
| `[S] dir-style-02a android row9` | gating | U2 | as row 6 |
| `[S] bidi-tab-001 android rtl-rows` | gating | U2 | blue extent on bands y86/106/186/226 equals the ref's ±3 |
| `[W] anchor-position-multicol-nested-001 android stack` | gating | U1 | the 10×10 cells at (190,78) (360,78) (190,168) (360,168) are ≥ 95 % green |
| `[S] dynamic-offset-rtl-001 android` | gating | U2 | magenta bbox = ref ±1 |
| `[W] dynamic-offset-rtl-002 android` | gating | U2 | magenta x0 = 16 + wire `left` 10, ±1 |
| `[S] dynamic-offset-vrl-rtl-001 android` | report | U2 | as rtl-001 |
| `[W] dynamic-offset-vrl-rtl-002 android` | report | U2 | x0 = 16 + 70 |

**Controls.** Each control has the expect string `→ GEOMETRY OK`.
- `[S] dir-style-02a web row6`, `row7`, `row9`.
- `[S] bidi-tab-001 web rtl-rows`.
- `[W] anchor-position-multicol-nested-001 web stack`.
- `[S] dynamic-offset-rtl-001 web`, `[S] dynamic-offset-vrl-rtl-001 web`.
- `[W] dynamic-offset-rtl-002 web`, `[W] dynamic-offset-vrl-rtl-002 web`.
- `[C] <name> android`, for each of:
  - `bidi-lines-002`, `boundary-shaping-009`;
  - `css-tags-paint-order`, `css-tags-paint-order-with-entry`;
  - `dir-selector-change-003`, `dir-selector-change-004`;
  - `block-end-aligned-abspos-nested`, `position-absolute-scrollable-overflow-zero`;
  - `text-decoration-subelements-002`, `attachment-local-positioning-3`, `attachment-local-positioning-4`;
  - `composited-filters-under-opacity`, `css-transform-scale-002`;
  - `conic-gradient-line-height-relative-units-003`, `conic-gradient-line-height-relative-units-004`;
  - `anchor-center-safe-rtl`, `dynamic-offset-vrl-001`.
- `[C] dir-style-02a android unaffected-rows`: rows 1-5, 8, 10, 11.
- `[C] dir-style-02a web`, `[C] dir-style-02a ios`.
- `[C] bidi-tab-001 android ltr-rows`.
- `[C] bidi-tab-001 web`, `[C] bidi-tab-001 ios`.
- `[C] anchor-position-multicol-004 android red`: the red pixel mask is identical.

**Executed:**
- **`wave54-final wave54-final`** (`.geometry.wave54-final.txt`, exit 0): all 7 gating keys WRONG, both report keys
  WRONG, all 33 controls OK. That is the self-test shape `geometry-gate.py --self-test` requires. Example lines:
  - `[S] dir-style-02a android row6 ink (246, 120, 373, 131) dot 0px → GEOMETRY WRONG (extent x246-373 vs ref x17-153; ink past the ref right edge (mirrored); dot: none at x15-19 y128-132)`;
  - `… row7 … → GEOMETRY WRONG (extent x22-243 vs ref x17-243; dot: none at x15-19 y148-152)`;
  - `[S] dynamic-offset-rtl-001 android magenta (186, 26, 195, 35) → GEOMETRY WRONG (magenta (186, 26, 195, 35) vs ref (86, 26, 95, 35))`.
- **Replays** (`.geometry.replays.txt`):
  - U1U2 prints OK on every android gating key it replays: row 6/7/9, rtl-rows, stack, rtl-001, and also -vrl-rtl-001
    and both `[W]`.
  - U1 prints OK only on row 7 and stack.
  - U2 prints OK only on the four dynamic-offset rows.

**The fakes it rejects, each executed:**
1. Today's android picture: mirrored rows 6/9 and a missing dot on row 7.
2. **PLAIN**, the unguarded mount: `row6 ink None … → GEOMETRY WRONG (no ink in the row; …)`, at a passing score of
   0.9663.
3. The U1-only picture: rows 6/9 still mirrored.
4. iOS's mirrored rows 6/9, with the dot drawn after the text: `ink (236, 119, 371, 131) … WRONG`.
5. bidi-tab-001 PLAIN (+12 px): `band y86 blue x(16, 147) …`.
6. The stacked squares: `cells [100, 0, 0, 0]`.
7. The mounted-but-mirrored x186.

**The identity controls can fail.** `wave54-open wave54-final` (`.geometry.control-can-fail.txt`) prints WRONG on exactly
the two control documents wave 54's hunk P moved: `bidi-lines-002 android … (2929 px differ)` and
`anchor-center-safe-rtl android … (5993 px differ)`. Every other control prints OK.

## 8. Device probe before the closing gate — section `selectors` (one section)

**Why selectors.** It holds the only document where U1's and U2's rows coexist: dir-style-02a row 7 is U1, rows 6/9
are U2. It also holds the two `dir-selector-change` U1 controls.
- Run on the integrated tree: `tools/titan/gate-driver.sh wave55-pre --sections selectors --skip-fixture-net` (about
  2.5 min on a quiet host). The orchestrator's window, read with the union carrier set.
- Read with:
  - `python3 tools/titan/results/wave55-plan/positioned-parent-slot.geometry.py wave55-pre wave55-open`. Read the
    selectors keys from the probe's own output. The other sections' keys print MISSING by design; `geometry-gate.py
    --lanes` narrows by lane, not by section, so on this run it would report L3's other gating keys UNMEASURED
    (exit 3).
  - `node tools/titan/results/wave52-gate/cells.mjs 'dir-style|dir-selector' wave55-open wave55-pre`.
  - The control-check over `selectors`.
  - OPEN `dir-style-02a` android next to its ref. Rows 6, 7 and 9 must read ".This element is rtl" /
    ".This element should inherit rtl" from x16.

**Expect:**
- `[S] dir-style-02a android row6` / `row7` / `row9 → GEOMETRY OK`.
- `[C] dir-selector-change-003 android`, `-004`, `dir-style-02a android unaffected-rows`, `web` and `ios`, each
  `identical to wave55-open → GEOMETRY OK`.
- `selectors/dir-style-02a android` P ≥ 0.95, expected ≈0.987.
- Every other selectors capture × 3 byte-identical.

**Decision:**
- Row 6 or row 9 not OK, or the cell < 0.95 → revert U2 (the score falls back to U1's f ≈0.943).
- Row 7 not OK, or any other selectors capture moved → revert U2, then U1.

**Measured first at the closing gate.** U2's host-ACTIVE effect (css-writing-modes) and bidi-tab-001 (css-text) are
measured there, gated by their keys (§7).
- **Optional extension if the window allows:** add `css-text,css-writing-modes` to the probe.

## 9. Risks, and what I could not determine

- **R1 — the RTL origin assumes the Column's width equals its max constraint (MED).** This holds for a definite-width
  parent, so `applies` requires a px Width. All 11 RTL Column-loop parents in the corpus declare one. An auto-width RTL
  parent keeps today's placement, with a breadcrumb.
- **R2 — the vertical-rl RTL documents (MED).** The wave-47 Z2 `VerticalBlockFlowLayout` may place these instead of the
  Column, because `writing-mode` inherits from `dynamic-offset-vrl-rtl-*`'s root. Today's x186 fits the Column
  arithmetic, but that is not traced, so their keys are REPORT only.
- **R3 — a probe blind spot (MED).** The selectors probe does not see U2 on the active host. A wrong origin there
  surfaces only at the closing gate, where its gating keys revert U2. Mitigation: the optional probe extension (§8).
- **R4 — the PLAIN trap (HIGH impact).** The BACKLOG's own phrasing ("drop the LocalActive gate") produces a passing
  DEGENERATE dir-style-02a (0.9663) and costs bidi-tab-001. The geometry keys, not the score, are the ship criterion.
- **R5 — control collisions (LOW-MED).** `anchor-center-safe-rtl` is a candidate carrier for L4. L7 U5 shares the 17
  bidi-baked wire stems. A collision drops a control at synthesis; it does not change a unit.
- **R6 — `anchor-position-multicol-004` (LOW).** Where the mounted green children land inside Compose's multicol
  clipping was not determined. Neither web nor Android paints them today. The red control guards the visible part.
- **R7 — the iOS twin defect stays (out of lane, new queue item).** SwiftUI's out-of-flow ZStack overlay treats a
  physical `left` as leading under RTL:
  - dir-style-02a ios rows 6/9 at x236 (f 0.9455);
  - bidi-tab-001 ios's four RTL rows (P 0.9829, DEGENERATE);
  - dynamic-offset-rtl-001 ios magenta at x36 (P 0.9955, DEGENERATE).

  The radius is the same 11 parents / 7 documents (iOS is not host-gated). After this lane, dir-style-02a fails on iOS
  only.
- **Not determined:**
  1. Whether the capture canvas clips at x374. That decides whether PLAIN's rows 6/9 are blank or show a sliver of
     "Th"; either way the key rejects it.
  2. Exactly how Compose's text draw skips a 0-px slot (wave-54 note `wave54-rtl-marker-bake/_note.md:225-227`). It is
     moot once children are measured unbounded.
  3. The census approximations for the clip-path, contain and multicol decoders. A mis-ported activation would mislabel
     a document's host state: the 1221 count is the port's, not the device's.
  4. `hyphenate-character-005`'s score direction.

STATUS: COMPLETE

# android-paint-order — wave-55 brief, lane L6 (BACKLOG "Next-wave obligations" 0(k))

**Lane.** L6 · Compose borders paint order + effects drop-shadow / clip. Lane directory at landing:
`tools/titan/results/wave55-android-paint-order/` (note `_note.md`).

**Evidence run: `wave54-final`** (dev tip 0d0d3a3d has exactly its content; tree at HEAD 0d0d3a3d, branch
campaign/applier-campaign). Every cell below is written `wave54-final <sec>/<test> <platform> <P|f> <ssim>` as
`node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave54-open wave54-final` prints it (the scorer's own
`loadRun`: a cell counts only if `typeof ssim === "number" && !scoreExcluded`, P iff `wptPass === true`). Every replay
score is the gate metric (`tools/titan/inject-wpt-block.mjs` `diffWebVsRef`), and each script re-scores the untouched
capture first: all eight reproduce the cell exactly (`android-paint-order.replay.out.txt`).

**Nothing was built, run or captured.** The opening gate `wave55-open` was capturing on this host. No source, script or
config outside `tools/titan/results/wave55-plan/` was edited; no git state changed. Every PNG named below was opened
(Read tool, plus 2–4× crops): the capture next to the frozen ref (`tools/wpt/refs/<WPT_REF 9b5435e5>/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`),
on all three platforms. WPT sources are cited as `WPT 9b5435e5 css/…` (the pin in the tracked `tools/titan/WPT_REF`;
the `tools/wpt/` mirror itself is gitignored).

**Written beside this file** (all read-only over the run dirs and refs):
- `android-paint-order.census.mjs` → `.census.out.txt` / `.census.json` — the IR census (1435 per-test IR documents):
  the self-paint and legacy border routes, child-into-band overlap classes, opacity-bordered components, every
  `drop-shadow()` carrier, and (§9) the out-of-lane leading-space radius.
- `android-paint-order.crossings-scan.py` → `.crossings-scan.out.txt` — the PIXEL corroboration of the legacy-route
  census over the 252 documents the wire says can carry it; emits U2's carrier list and may-move set.
- `android-paint-order.replay.py` + `.replay-score.mjs` → `.replay.out.txt` (pictures in `android-paint-order-replay/`).
- `android-paint-order.gate-fixtures-scan.py` → `.gate-fixtures-scan.out.txt` — the fixture net's exposure (§9 R1).
- `android-paint-order.geometry.py` → `.geometry.wave54-final.txt`, `.geometry.wave54-open.txt`,
  `.geometry.fakes.txt` (pictures in `android-paint-order-fakes/`), `.geometry.replays.txt`.

## Summary

Both residues are real, but they are not the same kind of thing.

- **(1) `backdrop-filter-clip-rect` android is a paint-order defect, and it is corpus-wide.** Compose paints every
  border band AFTER the element's content. androidx foundation 1.11.4 `BorderModifierNode` calls `drawContent()` first in
  every outline branch (javap of the AAR in the Gradle cache: the lambdas of `drawRectBorder`, `drawRoundRectBorder`
  and `drawGenericBorder` each start with `ContentDrawScope.drawContent()`), and `BorderSideApplier`'s slow path is
  `drawWithContent { drawContent(); paintSide… }` (`borders/sides/BorderSideApplier.kt:98-99`).
  - The children are composed INSIDE the node that carries that chain, so the parent's band overpaints them. CSS 2.1
    Appendix E paints them over it.
  - On clip-rect this is the 416 px where the red `.menu` borders cross the navbar's blue border.
  - The SAME defect sits under 25 other Android captures, all the flex / grid abspos-static-position family: up to 1152
    px of a black container border drawn across a yellow or teal child, where web and iOS show 0.
- **(2) `clip-path-filter-order` android has no defect in the ORDER.**
  - Compose chains `ClipPathApplier.applyClipPath` (`effects/EffectsFacade.kt:168`, a `Modifier.clip` graphicsLayer)
    OUTER of `FilterApplier.applyForegroundFilters` (`:234`). That call holds the drop-shadow `drawBehind`
    (`effects/filter/FilterApplier.kt:127` → `:422-465`).
  - So the shadow is drawn, then clipped, which is the filter-effects-1 §5 / css-masking-1 order. The verbatim wire's
    shadow is extracted, and that is already pinned (`FilterExtractorDropShadowInkTest.kt:78-95`).
  - What no artifact proves is that the shadow is RENDERED on device. The test cannot show it: in the ref the shadow
    lies wholly under the opaque box inside the clip, so "rendered then clipped" and "dropped" are the same picture.
  - The only other corpus carrier, `css-color/currentcolor-003`, is confounded: its box-shadow, text-shadow and red
    background fill the same rows.
  - So the unit for (2) is a JVM chain-order pin (U3). The geometry key for this test is a CONTROL that says what it
    can and cannot see (§7).
- **What still caps both target cells is neither defect.** On Android a run that follows a `<br>` keeps its leading
  space (" blue border", " There should…"). The `{text, <br>}*`-only host is stacked
  (`typography/inline/InlineRunFold.kt:546` `br-stacked-equivalent`), and css-text-3 §4.1.2 removes that space at the
  start of the line. This is Compose typography: 30 hosts in 27 documents (census (4)). It is not this lane and not
  any wave-55 lane listed; it is queued in §9.

**Recommendation: GO**, three separately revertible units, Android only, no seam:
- **U1 `PO-selfpaint-android`**: the self-paint route's band paints behind content. Target clip-rect. The 8 other self-paint
  documents must stay decoded-pixel identical (5 of them are `[ID]` keys).
- **U2 `PO-sides-android`**: the legacy route's band moves inner of the background and paints behind content. Carriers:
  the 25-capture flex / grid family. It is probe-decided by six gating crossing keys and 5 raster-identity `[ID]` controls.
- **U3 `FO-pin`**: test-only. The clip-path-filter-order wire's chain must install the clip OUTER of a present
  drop-shadow node.

**Predicted flips: 0.** Two P→P rises: clip-rect +0.0015, and flex safe-001 / -002 +0.03 / +0.028. 27 Android captures
change (U1 2, U2 25); 8 of them carry a geometry key, and only those are ever labelled paint-order-correct. GO-SMALL is U1 + U3 only.

## 1. Targets, and what the pictures show

### Target cells (re-derived; `cells.mjs … wave54-open wave54-final`)

| cell | wave54-open | **wave54-final** | lane role |
|---|---|---|---|
| `filter-effects/backdrop-filter-clip-rect` android | f 0.9315 | **P 0.9559** | U1 target (0(k)(1)) |
| `filter-effects/backdrop-filter-clip-rect` web / ios | P 0.959 / P 0.956 | P 1 / P 0.9961 | controls |
| `filter-effects/backdrop-filter-clip-rect-zoom` ×3 | — | — (manifest `scoreEligible: false`) | U1 capture carrier (android), report key |
| `css-masking/clip-path/clip-path-filter-order` android | f 0.8406 | **P 0.9548** | 0(k)(2); U3 pins its order; control key |
| `css-masking/clip-path/clip-path-filter-order` web / ios | f 0.8586 / f 0.8578 | P 1 / P 0.9978 | controls |
| `css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-001` android | P 0.9619 | **P 0.9619** | U2 target |
| `css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-002` android | P 0.9635 | **P 0.9635** | U2 target |
| `css-grid/abspos/grid-abspos-staticpos-align-self-001` android | P 0.9673 | **P 0.9673** | U2 gating picture |
| `css-grid/abspos/grid-abspos-staticpos-justify-self-001` android | P 0.9667 | **P 0.9667** | U2 gating picture |
| `css-grid/abspos/grid-abspos-staticpos-align-self-vertWM-001` android | P 0.9612 | **P 0.9612** | U2 gating picture |
| `css-grid/abspos/grid-abspos-staticpos-align-self-rtl-001` android | P 0.9646 | **P 0.9646** | U2 gating picture |

The other 19 U2 carriers are in §5.

### `backdrop-filter-clip-rect` (WPT 9b5435e5 `css/filter-effects/backdrop-filter-clip-rect.html`)

**The document.** A green 200×200 `.box` sits under an abspos `.navbar` (300×50, `border: 2px solid blue`,
`border-radius: 10px 20px 30px 40px`, `backdrop-filter: invert(1)`). The navbar holds two abspos children with
`border: 2px solid red`:
- `.menu`: 100×150 at top 50 / left 95;
- `.menu2`: 100×30 at top −34 / left 95.

**The geometry.** The capture origin is +16 px, as in the ref.
- The navbar's border box is x66–369 / y166–219; its bands are rows 166–167 and 218–219.
- Its padding box starts at (68,168). The children resolve against it (CSS 2.1 §10.1 item 4), so both menus span
  x 163–266 (68 + 95, 104 px wide).
- `.menu2` spans y 134–167, so its bottom band is rows 166–167. `.menu` spans y 218–371, so its top band is rows
  218–219.
- The crossing is 2 rows × 104 px × 2 = **416 px**, that is 416 / 234 000 = 0.18 % of the frame.

**Measured** (`.geometry.wave54-final.txt`, same at `wave54-open`):

| picture | overlap rows red / blue | navbar band outside the menus (blue /320) | inverted pink, x116-315 y168-217 | red px |
|---|---|---|---|---|
| ref | 416 / 0 | 320 | 10000 | 1552 |
| web | 416 / 0 | 320 | 10000 | 1552 |
| ios | 416 / 0 | 320 | 10000 | 1552 |
| **android** | **0 / 416** | 320 | 10000 | **1136** (= 1552 − 416) |

- **Looked at.** On the 4× crop of rows 155–230 / x140–290 (ref, web, iOS, Android), the ref, web and iOS paint the
  red menu edges ON the navbar's blue band, and Android paints blue across them. Everything else is pixel-equal in
  class: the backdrop inversion, the black rounded ends, the menus' boxes.
- **The rest of the Android gap** (iOS 0.9961 vs Android 0.9559) is the description text: lines 2–4 start with a space
  (" blue border", " particular", " or anywhere").
- **The `-zoom` twin.** Its captures are unzoomed while its ref is rendered at `zoom: 5`, hence unscored. It carries the
  identical 0 / 416 on Android; web and iOS show 416 / 0. Its manifest pair `iOS-Android` reads
  `pixelMismatchedCount 417`.

### `clip-path-filter-order` (WPT 9b5435e5 `css/css-masking/clip-path/clip-path-filter-order.html`)

**The document.** A 400×400 green abspos box with `will-change: transform`, `filter: drop-shadow(16px 16px 20px red)`
and `clip-path: inset(0 200px 200px 0)`. Expected: a crisp 200×200 green square, no red, no blur.

**Measured:**
- Ref and all three captures at `wave54-final`: reddish 0; green bbox (16,128)–(215,327), 40 000 px.
- Halo: the minimum channel in the 45-px strips right of and below the box is 255, and 254 on Android. Android's x = 216
  column is (254,254,254): a 1/255 green coverage at the clip edge, NOT red.
- At `wave54-open` all three platforms had the box at y148–347: the stray blank line above it, gone since wave 54 U3.

**Looked at.** Android lines 2–3 of the description keep a leading space; nothing else differs. A correct
filter→clip render and a render with no shadow at all produce THIS picture (§2.B).

### The U2 family (example: `flex-abspos-staticpos-align-self-safe-001`)

Each `.flex` is 50×50 with `border: 3px solid black` and `position: relative`. Each holds an abspos 65×65 yellow child
with a 2px dotted purple border, which overflows across the container's right / bottom band.
- **Ref, web and iOS** paint the child over the black band: crossings 0.
- **Android** paints the black square outline through every child: 1152 crossing px, and the yellow fill is 16 560 px
  against the ref's 17 956.
- **The grid family** (1-px black `.container` borders, 8×6 teal abspos children overflowing a 4×2 content box) shows
  the same thing: Android 79–117 crossing px, web and iOS 0. Seen on the 8× crop of row 1 of
  `grid-abspos-staticpos-align-self-001`.

## 2. Mechanism (traced; file:line at 0d0d3a3d)

### A. Paint order: the parent's border band over its descendants

**The spec.**
- CSS 2.1 Appendix E §E.2 paints, within a stacking context: the element's own background and **border** first
  (step 2), then in-flow block descendants' backgrounds and borders (step 4), inline content (step 7), and **positioned
  descendants with `z-index: auto | 0`, in tree order** (step 8).
- The navbar forms a stacking context: filter-effects-2 §2 says a `backdrop-filter` other than `none` creates "a
  stacking context and a containing block for absolute and fixed position descendants". `.menu` and `.menu2` are its
  step-8 descendants.
- In the flex / grid family, the relative container and its abspos child are both step-8 boxes of the root context.
  The child comes later in tree order, so it paints over the container (whose border is part of the container's own
  paint).
- In every case the descendant wins every pixel it shares with the band.

**Compose, the self-paint route.** It is taken when `BorderRadiusApplier.selfPaintsWithoutClip` holds: a radius, no
overflow clip, no background layers, no clip insets, opacity 1, and a uniform-solid or absent band.
- `StyleApplier.applyConfig:735` calls `BorderRadiusApplier.applySelfPaint`, which chains
  `result.background(color, shape)` then `result.border(width, color, shape)` (`borders/radius/BorderRadiusApplier.kt:207`).
- androidx `Modifier.border` is `BorderModifierNodeElement` → `BorderModifierNode`, a `CacheDrawModifierNode` whose
  three outline branches (Rectangle / Rounded / Generic) each return `onDrawWithContent { drawContent(); <band> }`.
  The draw order is therefore **background → content (children) → band**.
- The navbar takes this route. Its exact wire is pinned through the real extractors in
  `BorderRadiusSelfPaintTest.kt:187-236`, "clip-rect navbar wire reaches self-paint".

**Compose, the legacy route** (every other bordered box: 1428 of the corpus's 1438 bordered components).
- `StyleApplier.applyConfig:753-754` chains `BordersFacade.apply` (radius clip → `BorderSideApplier.applyBorders` →
  outline, `borders/BordersFacade.kt:85-98`) OUTER of `ColorApplier.applyColors` (opacity group → background → layers).
- `applyBorders` is the androidx `Modifier.border(width, color)` fast path (`BorderSideApplier.kt:79-83`), or
  `drawWithContent { drawContent(); paintSide ×4 }` (`:98-124`). The file's comment at `:86-97` records why it paints
  after content: a `drawBehind` at that chain position would be overpainted by the background inner of it.
- The draw order is therefore **[clip] → background → content → band**. The band also sits OUTSIDE ColorApplier's
  opacity group.

**Why the children are "content".**
- Both captures show the children under the band, so the children are composed inside the parent's styled node:
  `ComponentRenderer.kt:3063-3068` (`Box(modifier = modifier, contentAlignment = TopStart) { RenderContent(…) }`) or the Box / flex branches beside it.
- The positioned-container Box (`:3512-3549`) and its `RenderAbsoluteChild` mounts sit inside that node.
- Children that CanvasRootHoist lifts to the canvas root paint above everything, so they are not carriers. The census
  counts only children whose containing block IS the bordered parent.

**The arithmetic of the fix.**
- The correct order needs the band drawn AFTER the background and BEFORE the content: a draw node that paints the band
  and then calls `drawContent()`, placed inner of the background.
- On the self-paint route the background is already outer of the band (`applySelfPaint` chains it first), so U1 only
  changes the band's own node.
- On the legacy route the band must move from step 5 to just inner of ColorApplier (U2). That also puts it inside
  ColorApplier's opacity group, where css-color-4 §3.3 wants it: the element is composited as one group, border included.
- Size is unchanged at either position: ColorApplier adds no layout modifier, and padding is the innermost step 8.

**Raster identity is the constraint.**
- The behind-content band must rasterise exactly as androidx did, or every bordered capture in the corpus moves.
- From the bytecode (`BorderModifierNode.drawWithCacheModifierNode$lambda$0`):
  - `strokePx = min(width == Hairline ? 1 : ceil(width.toPx()), ceil(size.minDimension / 2))`;
  - `topLeft = (strokePx/2, strokePx/2)` and `borderSize = (W − strokePx, H − strokePx)`;
  - `fillArea = strokePx·2 > size.minDimension`.
- **Rectangle:** `drawRect(brush, fillArea ? 0 : topLeft, fillArea ? size : borderSize, fillArea ? Fill : Stroke(strokePx))`.
- **Rounded:**
  - a simple RoundRect (`RoundRectKt.isSimple`) draws `drawRoundRect` with a `Stroke`;
  - any other RoundRect (the navbar's 10 / 20 / 30 / 40) goes through `BorderKt.createRoundRectPath(path, roundRect,
    strokePx, fillArea)`, i.e. the outer RoundRect minus `createInsetRoundedRect(strokePx, rr)` with each corner
    `shrink(r, strokePx)`, then `drawPath(path, brush)`.
- **Generic:** an ImageBitmap-cached mask (`BorderCache`). No corpus border goes through it.
- U1 must mirror this to the float. The lane re-reads the bytecode (`javap -p -c` on
  `androidx/compose/foundation/{BorderModifierNode,BorderKt}` from
  `~/.gradle/caches/…/foundation-android/1.11.4/…/foundation.aar`) rather than trusting this summary.

### B. Filter → clip on `clip-path-filter-order`: correct by construction, unprovable on its pixels

**The spec.** filter-effects-1 §5 ("The filter property"): "first any filter effect is applied, then any clipping,
masking and opacity". css-masking-1 is the test's own `rel=help` anchor, `#placement`.

**Compose.** The chain is outermost-first. `EffectsFacade.apply` installs:
- blend (`:162`);
- **clip-path** (`:168`, `Modifier.clip(shape)`, a clipping graphicsLayer element);
- mask, colour-matrix group (`:192`), backdrop (`:205`), box-shadow (`:225`);
- **foreground filters** (`:234`): blur layers, then `applyDropShadow` (`FilterApplier.kt:127` → `:422-465`,
  `drawBehind { drawRect(offset (dx,dy), node size, BlurMaskFilter(σ = r/2)) }`), then opacity.

So the shadow is recorded inside the clip layer and clipped: filter first, then clip.

**The wire is read.** `{"fn":"drop-shadow","x":{"px":16},"y":{"px":16},"r":{"px":20},"c":{srgb red}}` → `DropShadow(16dp,
16dp, 20dp, red)` (`FilterExtractor.kt:176-196`; colour pinned at `FilterExtractorDropShadowInkTest.kt:78-85`).

**Why no picture can tell "clipped" from "dropped" here.**
- The shadow of the 400×400 box spans (32,144)–(431,543), blurred by σ = 10.
- The clip keeps (16,128)–(215,327), and that whole region is the box's opaque green, painted after the `drawBehind`.
- The ref therefore shows no shadow pixel anywhere, and a runtime that dropped the shadow paints the same 40 000 green px.
- Only the WRONG order is visible. Clipping first, then filtering, casts the clipped square's shadow at
  (32,144)–(231,343): the fake `FO-filter-after-clip` prints 10 335 reddish px and a halo min channel of 16
  (`.geometry.fakes.txt`).
- The suggested key, "absence outside the clip AND presence where the ref shows it", has an empty second domain: the
  ref shows it nowhere.

**Is the drop-shadow rendered on Compose at all?**
- By the IR and the applier: yes. It is extracted (pinned), installed (`FilterApplier.kt:127`) and drawn with a
  `BlurMaskFilter` rect (`:446-462`). The `:429-437` comment records the earlier `setShadowLayer` version that rendered
  nothing.
- By committed pixels: **not determinable**.
  - `css-color/currentcolor-003` is the corpus's only other `drop-shadow()` carrier (census (3): 3 components in 2
    documents), and it is confounded: the shadow lands at +333 px in rows already red with the span's background,
    box-shadow and text-shadow (looked at).
  - `fixtures/properties/effects/filter-functions.json` has three `Filter_DropShadow_*` components but no committed
    baseline (`git ls-files tools/visual/baseline | grep -i drop` is empty).
- The blast radius of "not rendered" would be exactly those 3 components. Device evidence is orchestrator window W1
  (§8).

**Not the order: the silhouette.** Compose's drop-shadow silhouette is the node's RECT, not the alpha mask of its
rendering. filter-effects-1 §6.1 `drop-shadow()` is a blurred, offset copy of the input's alpha mask. That is
immaterial for an opaque box (clip-path-filter-order) and wrong for text or bordered transparent boxes (currentcolor-003
only). Recorded, not staffed (§9).

### C. Both target cells' remaining gap: out of this lane

The runs host `<p>` is `{text, <br>}*`-only, so Compose bails the fold (`InlineRunFold.kt:530-547`
`br-stacked-equivalent`). The stacked fallback paints each run as written, so the run after each `<br>` keeps its
leading collapsible space. css-text-3 §4.1.2 phase II removes it ("a sequence of collapsible spaces at the beginning of
a line … is removed"), and web and iOS do. Census (4): 30 hosts in 27 documents.

## 3. Ownership (every file; Compose only; disjoint from L1–L5, L7)

| unit | file | change | size today |
|---|---|---|---|
| U1 | **NEW** `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BehindContentBorder.kt` | `Modifier.behindContentBorder(width, color, shape)` → `BehindContentBorderElement` / `BehindContentBorderNode : DrawModifierNode` (band, THEN `drawContent()`), its pure band geometry (the §2.A androidx arithmetic) and `drawInOrder(band, content)` | new, ≤ 200 |
| U1 | `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/radius/BorderRadiusApplier.kt` | `:207` `result.border(` → `result.behindContentBorder(`, plus the kdoc lines at `:165-185` that describe the order | 281 (≤ 300: edit allowed) |
| U1 | **NEW** `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BehindContentBorderTest.kt` | pins P1, P2 (§4) | new |
| U1 | `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/BorderRadiusSelfPaintTest.kt` | `:158-185`: the band element is `BehindContentBorderElement`, never androidx `BorderModifierNodeElement` | 249 |
| U2 | `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/sides/BorderSideApplier.kt` | one DEFAULTED parameter `behindContent: Boolean = false` on `applyBorders` (`:71`) plus two one-line conditions: `:82` `if (behindContent) return modifier.behindContentBorder(width, color, RectangleShape)`; `:98-99` the `drawContent()` call moved behind the four `paintSide` calls when `behindContent`, routed through `BehindContentBorder.drawInOrder` | 636 (oversized: call site / defaulted parameter / one-line conditions only) |
| U2 | `runtimes/compose/src/main/java/com/styleconverter/runtime/borders/BordersFacade.kt` | defaulted `sidesBehindContent: Boolean = false` on `apply` (skips the sides), and new `applySidesBehindContent(modifier, config)` | 129 |
| U2 | `runtimes/compose/src/main/java/com/styleconverter/runtime/StyleApplier.kt` | legacy branch `:753-754` → three call-site lines: `BordersFacade.apply(result, paint.borders, sidesBehindContent = true)`; `ColorApplier.applyColors(…)`; `BordersFacade.applySidesBehindContent(result, paint.borders)` | 1188 (oversized: call-site lines only) |
| U2 | **NEW** `runtimes/compose/src/test/java/com/styleconverter/runtime/borders/sides/BorderSidesBehindContentTest.kt` | pin P3 | new |
| U3 | **NEW** `runtimes/compose/src/test/java/com/styleconverter/runtime/effects/EffectsClipFilterOrderTest.kt` | pin P4 (test only) | new |

- **Seams: none.** No `ComponentRenderer.kt` / `.swift` / `.tsx` or `extract-fixture.mjs` hunk; nothing in this lane
  changes a wire byte.
- **Read-only:**
  - `effects/EffectsFacade.kt`, `effects/filter/FilterApplier.kt` and `FilterExtractor.kt` (U3 pins them as they are);
  - `ColorApplier.kt`, `OutlineApplier.kt`, `BorderImageApplier.kt`, `TableApplier.kt` (fabricated cell borders are not
    on either route);
  - all three ComponentRenderers, `InlineRunFold.kt` (§2.C).
- **Collision watch.**
  - **`StyleApplier.kt`** is the one file another Compose lane (L2 `DynamicValueResolver`, L4 spacing) could also
    need. If the plan grants it elsewhere, U2's three lines travel as
    `tools/titan/results/wave55-android-paint-order/u2-styleapplier.patch` (header: base 0d0d3a3d, sha256 prefix
    `ae930e030eaaff20`, anchor `:753`).
  - L3's census lists the clip-rect / clip-rect-zoom / basic-blur navbars in its section (B), "host-ACTIVE parents —
    already mounted today". These are evidence there, not carriers: U1 owns the clip-rect captures (§5).
- **sha256 prefixes at 0d0d3a3d:** `BorderRadiusApplier.kt` 18fc384978066baf · `BorderSideApplier.kt` 7bcc1fe79280d4b5 ·
  `BordersFacade.kt` 5c8ff4558ff84ebe · `StyleApplier.kt` ae930e030eaaff20 · `BorderRadiusSelfPaintTest.kt`
  0fde0df8d65ce9c3.
- **Docs:** none move.
  - The new `BehindContentBorder.kt` is a support file, not a `<Name>Applier`, so `coverage-audit.mjs`'s `real` count
    stays.
  - The JVM test count grows. The orchestrator's sweep re-derives it for `doc-staleness-check.sh`.

## 4. Units (one commit each, separately revertible; `revertOrder`: U1 = [U2, U1] — U2's fast path calls U1's painter)

### U1 `PO-selfpaint-android` — the self-paint band paints behind content

**Change.** `applySelfPaint` installs `behindContentBorder(width, color, shape)` in place of androidx
`border(width, color, shape)`.
- The node's `ContentDrawScope.draw()` is `drawInOrder(band = { drawBand() }, content = { drawContent() })`.
- `drawBand()` reproduces §2.A's rectangle / simple-RoundRect / path branches on the same Outline
  (`shape.createOutline(size, layoutDirection, density)`), with the same stroke arithmetic.
- The band is cached per size in `CacheDrawModifierNode` style, or recomputed (the lane's choice, stated in its note).

**Predicts:**
- `backdrop-filter-clip-rect` android: overlap rows red 416 / blue 0, red px 1552. The replay is P 0.9559 → 0.9574.
- `-zoom` android: the same 416 px.
- The 8 other self-paint documents (census: 10 self-paint bordered components): decoded-pixel IDENTICAL. 5 are `[ID]`
  keys (borders-008, the three clip-path-*Box-1e, clip-rect-2); borders-003 / -004 / -005 are read by the control-check.

**Pins** (verbatim configs; every mutation executed red → restore → green, file sha256 verified):
- **P1** `drawInOrder` calls band, then content, with recorders. Mutation: swap the two calls → red.
  - `applySelfPaint(navbarRadius, uniformSolidBlue, …)` installs `BehindContentBorderElement` and no androidx
    `BorderModifierNodeElement`.
  - Mutation: restore `result.border(` at `:207` → red.
- **P2** band geometry against the androidx arithmetic.
  - 2 px on 304×54 → topLeft (1,1), size (302,52), stroke.
  - 30 px on 50×50 → fillArea, so a full fill.
  - 1.5 px → ceil 2.
  - Navbar inner radii 8 / 18 / 28 / 38: each `max(0, r − 2)`, and a 1-px radius under a 2-px band shrinks to 0.
  - Mutations: drop the `ceil` → red; drop the `max(0, …)` clamp → red.

**Device:** the §8 probe (filter-effects, css-masking).

### U2 `PO-sides-android` — the legacy band moves inner of the background and paints behind content

**Change.** §3 rows U2:
- `StyleApplier`'s legacy branch keeps radius-clip + outline outer, keeps ColorApplier, then adds
  `applySidesBehindContent`.
- `BorderSideApplier.applyBorders(…, behindContent = true)`: the fast path is the shared U1 painter on `RectangleShape`
  (androidx `drawRectBorder` arithmetic). The slow path is the existing four-`paintSide` lambda with `drawContent()`
  moved behind it.
- Default `false` keeps every other caller byte-identical. `applyVisualsOnly` (`StyleApplier.kt:1079`) is unreferenced,
  per its own comment at `:1090-1092`.

**Predicts:**
- the 25 Android carriers of §5: crossings → 0;
- flex safe-001 / -002: P 0.9619 → ≈ 0.994 and P 0.9635 → ≈ 0.991;
- the grid family and the rest: ±0.0005;
- the five `[ID]` raster rows on legacy bands: identical;
- every Android capture outside the 252-document may-move set: decoded-pixel identical;
- inside it, but not a carrier: |Δssim| ≤ 0.002;
- web and iOS: untouched by construction (Compose files only).

**Pins:**
- **P3** On the verbatim `.flex` wire of flex safe-001 (`Display FLEX`, 50×50, 3 px solid black, `Position RELATIVE`,
  margin 20) through `StyleApplier.applyConfig(Modifier, StyleApplier.extractConfig(pairs))` (the JVM chain technique
  of `BackfaceCullChainTest.kt:82`):
  - the folded chain's band element sits AFTER (inner of) the `Background` element;
  - on the verbatim `background-clip-002` DOTTED wire, the slow-path draw element sits after the `Background` element
    as well;
  - mutation: revert the `StyleApplier` hunk → both red.
- P1 also covers the slow path's order, because the slow path routes through the same `drawInOrder`.

**Device:** the §8 probe (css-flexbox, css-grid), A/B arm `drop-U2`.

### U3 `FO-pin` — test only: filter → clip on the verbatim clip-path-filter-order wire

**Change.** A new JVM test. Input: `EffectsFacade.extractConfig(…)` of the seven verbatim properties of
`wpt__css-masking__clip-path__clip-path-filter-order__1-072` (`Position`, `Width`, `Height`, `BackgroundColor`,
`WillChange`, `Filter`, `ClipPath`, read from `tools/titan/runs/wave54-final/sections/css-masking/per-test-ir/`), then
`EffectsFacade.apply(Modifier, cfg)`.

**Fold the elements.** The test asserts:
- a clipping `GraphicsLayer` element exists;
- a `DrawBehind` element exists (the drop-shadow);
- the clip comes FIRST (outer).

**Mutations:**
- move `applyClipPath` below `applyForegroundFilters` in `EffectsFacade.apply` → red ("clip inner of the filter");
- make `FilterExtractor`'s `"drop-shadow"` branch return null → red ("shadow dropped").

**Predicts:** no pixel and no cell. It closes 0(k)(2)'s order question at the chain level. Whether the shadow paints
on DEVICE is W1's question.

## 5. Carrier census (`android-paint-order.census.out.txt`, `.crossings-scan.out.txt`)

**Routes.** 1438 bordered components (in 349 of the 1435 documents):
- **self-paint 10.** borders-003 / -004 / -005 / -008 (css-break); clip-path-borderBox / contentBox / paddingBox-1e;
  backdrop-filter-clip-rect-2 / -clip-rect-zoom / -clip-rect.
- **legacy 1428.**

**U1 (IR census (1)).** 2 parents in 2 documents, 4 `abspos-band` children: clip-rect and clip-rect-zoom.
- Capture carriers: android `{wpt__filter-effects__backdrop-filter-clip-rect, wpt__filter-effects__backdrop-filter-clip-rect-zoom}`;
  web and iOS none.
- Wire: none.
- Raster identity: the 8 other self-paint documents (5 `[ID]` keys; the control-check reads all 8).

**U2 (IR census (2)).** 136 parents in 26 documents. 101 are resolvable, in 19 documents; the 35 `abspos-unknown` are a
declared non-px inset or size, in 7 documents.
- **The pixel scan** reads every document with a bordered component that has a non-`<br>` child or own text (the
  may-move set, **252 documents**, 27 sections).
- It counts "crossings" on ref and captures. A carrier is Android excess ≥ 20 with web / iOS within 5: **25 capture
  carriers, Android only**:
  - `wpt__css-flexbox__abspos__flex-abspos-staticpos-`: `align-self-safe-001`, `-safe-002`, `fallback-justify-content-001`,
    `justify-self-001`, `margin-001`, `-002`, `-003` (7);
  - `wpt__css-grid__abspos__grid-abspos-staticpos-`: `align-self-001`, `-002`, `align-self-img-001`, `-002`,
    `align-self-rtl-001`…`-004`, `align-self-safe-001`, `align-self-vertWM-001`…`-004`, `justify-self-001`, `-002` (15);
  - `wpt__css-images__gradient__gradient-powerless-hue-`: `hsl`, `hwb`, `lch` (3).
- `-oklch` (Android +106, iOS +16) stays in the may-move set, not a carrier.
- Wire: none.
- **IR-only rows** the scan does not corroborate are may-move, not carriers: anchor-position-multicol-007 / -008,
  align-self-016, flex-gap-decorations-027, bidi-plaintext-br-001.
- **(2b)** One legacy bordered component has opacity < 1:
  `wpt__filter-effects__backdrop-filter-basic-blur` `__1__2-012` (opacity 0.6, android f 0.904). It is ring-fenced: not a
  carrier, never named in code; U2 moves its band inside the group (§9 R3).

**U3.** No carrier.

**drop-shadow (census (3)).** 3 components in 2 documents: currentcolor-003 ×2 (android f 0.7794) and
clip-path-filter-order ×1.

**No stem is a carrier of two units.** U1's two captures are not in U2's carrier list; their parents are self-paint.

## 6. Predictions (from `wave54-final`; the replay is a SCORE prediction, never a picture label)

| cell | from → to | tier | floor (rule 3) · geometry | direction | unit |
|---|---|---|---|---|---|
| `filter-effects/backdrop-filter-clip-rect` android | P 0.9559 → **P ≈ 0.957** (replay 0.9574) | HIGH (stays P); picture-correct paint order MED-HIGH, claimed only on `[PO] … android` OK | 0.95 · `[PO] backdrop-filter-clip-rect android` **gating** | up +0.0015 | U1 |
| `filter-effects/backdrop-filter-clip-rect-zoom` android | unscored (`scoreEligible: false`) | — | — · `[PO] backdrop-filter-clip-rect-zoom android` report | — | U1 |
| `css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-001` android | P 0.9619 → **P ≈ 0.994** (replay 0.9938) | MED-HIGH | 0.95 · `[X] flex-abspos-staticpos-align-self-safe-001 android` **gating** | up | U2 |
| `css-flexbox/abspos/flex-abspos-staticpos-align-self-safe-002` android | P 0.9635 → **P ≈ 0.991** (replay 0.991, a lower bound: §7) | MED-HIGH | 0.95 · `[X] flex-abspos-staticpos-align-self-safe-002 android` **gating** | up | U2 |
| `css-grid/abspos/grid-abspos-staticpos-align-self-001` android | P 0.9673 → P ≈ 0.9672 | MED (flat); picture-correct only on its key | 0.95 · `[X] grid-abspos-staticpos-align-self-001 android` **gating** | flat | U2 |
| `css-grid/abspos/grid-abspos-staticpos-justify-self-001` android | P 0.9667 → P ≈ 0.9667 | MED | 0.95 · `[X] … justify-self-001 android` **gating** | flat | U2 |
| `css-grid/abspos/grid-abspos-staticpos-align-self-vertWM-001` android | P 0.9612 → P ≈ 0.9613 | MED | 0.95 · `[X] … align-self-vertWM-001 android` **gating** | flat | U2 |
| `css-grid/abspos/grid-abspos-staticpos-align-self-rtl-001` android | P 0.9646 → P ≈ 0.9645 | MED | 0.95 · `[X] … align-self-rtl-001 android` **gating** | flat | U2 |
| the other 15 Android U2 carriers (§5: 25 − the 6 keyed − grid align-self-safe-001 − the 3 css-images rows) | their wave54-final values, ±0.0005 (replay) | MED | — (no key) | flat | U2 |
| `css-grid/abspos/grid-abspos-staticpos-align-self-safe-001` android | f 0.9441 → f ≈ 0.9448 | LOW mover, stays f | — | up | U2 |
| `css-images/gradient/gradient-powerless-hue-{hsl,hwb,lch}` android | f 0.7542 / 0.755 / 0.7013 → +0.0002–0.0003 | LOW, stays f | — | flat | U2 |
| `css-masking/clip-path/clip-path-filter-order` web / ios / android | P 1 / P 0.9978 / P 0.9548 → **byte-identical** (the document has no border band) | HIGH | control `[FO] … {ref,web,ios,android}` | none | — |
| the 10 `[ID]` documents (android) | byte → decoded-pixel identical to `wave55-open` | MED-HIGH (raster parity: §9 R1) | control `[ID] …` | none | U1 / U2 |
| `filter-effects/backdrop-filter-basic-blur` android (ring-fenced) | f 0.904 → undirected mover under U2, reported plainly, never claimed | — | — | ? | U2 |

**Totals and the rules that read them.**
- **Flips: 0 · Lost: 0 · newly measured / unmeasured-now: 0.**
- **Picture-correct (paint-order) claims: at most 8 Android captures**, each only where its own key prints OK:
  clip-rect (`[PO]` gating), clip-rect-zoom (`[PO]` report; unscored) and the six `[X]` rows. The other 19 U2 carriers
  (27 changed captures in all: U1's 2 + U2's 25) are written "crossings expected 0, not keyed" and are never claimed.
- **Must not move:**
  - web and iOS, every cell (Compose-only lane);
  - Android outside the 252-stem may-move set, decoded-pixel identical (control-check);
  - Android inside it but not a carrier: |Δ| ≤ 0.002, else stop and look (picture plus wire).

## 7. Geometry probe — `android-paint-order.geometry.py <run> --base wave55-open` (written, run)

Each line ends in `→ GEOMETRY OK` or `→ GEOMETRY WRONG (<reasons>)`. Its leading tokens are the key, so
`geometry-gate.py find_line` matches them. A ref row that prints WRONG makes the probe exit 1 (the rule is wrong, not
the lane). The `-zoom` ref prints `n/a … → GEOMETRY OK`: it is rendered at zoom 5.

**Rules.**
- **`[PO] <test> <platform>`**: overlap rows red ≥ 400 and blue ≤ 8. Guards:
  - navbar band outside the menus (rows 166 / 167 / 218 / 219, x120-159 and x270-309): ≥ 312 / 320 blue;
  - pink 10000 ± 100;
  - red bbox (163,134,266,371) ± 1 and 1552 ± 16 red px.
- **`[FO] clip-path-filter-order <platform>`**: reddish 0 below y115; green bbox (16,128,215,327) ± 1, n ≥ 39600; halo
  min channel ≥ 250.
- **`[X] <test> <platform>`**: crossings ≤ 4 AND child fill (teal or yellow) ≥ 0.90 × the ref's.
- **`[ID] <test> android`**: `ImageChops.difference(run, base).getbbox() is None`.

**Keys for `expectations.json`** (`lanes.L6-android-paint-order.geometryProbes[0]`). `expect` maps every one of the 46
keys below to exactly `"GEOMETRY OK"`. Controls are the `expect` keys that are neither `gating` nor `report` (the
wave-54 convention); they are listed here only for the reader.

```json
{"cmd": "python3 tools/titan/results/wave55-plan/android-paint-order.geometry.py <run> --base wave55-open",
 "gating": {
  "[PO] backdrop-filter-clip-rect android": "U1",
  "[X] flex-abspos-staticpos-align-self-safe-001 android": "U2",
  "[X] flex-abspos-staticpos-align-self-safe-002 android": "U2",
  "[X] grid-abspos-staticpos-align-self-001 android": "U2",
  "[X] grid-abspos-staticpos-justify-self-001 android": "U2",
  "[X] grid-abspos-staticpos-align-self-vertWM-001 android": "U2",
  "[X] grid-abspos-staticpos-align-self-rtl-001 android": "U2"},
 "report": ["[PO] backdrop-filter-clip-rect-zoom android"],
 "control": ["[PO] backdrop-filter-clip-rect {ref,web,ios}", "[PO] backdrop-filter-clip-rect-zoom {ref,web,ios}",
  "[FO] clip-path-filter-order {ref,web,ios,android}",
  "[X] <each of the six tests> {ref,web,ios}",
  "[ID] borders-008 android", "[ID] clip-path-borderBox-1e android", "[ID] clip-path-contentBox-1e android",
  "[ID] clip-path-paddingBox-1e android", "[ID] backdrop-filter-clip-rect-2 android",
  "[ID] background-clip-002 android", "[ID] s-11-1-1b-001 android", "[ID] backdrop-filter-clip-radius-zoom android",
  "[ID] grid-abspos-staticpos-align-items-center android", "[ID] flex-abspos-staticpos-align-self-safe-003 android"]}
```

That is 46 keys: 7 gating, 1 report, 38 control. Brace-expanded, the controls are 3 + 3 + 4 + 18 + 10 = 38.

**`[ID]` scope.** The `[ID]` rows are controls, not gates: another lane may legitimately move one of those pictures.
A FAIL is loud and is diagnosed through the control-check and R4 / R5. Of the 10:
- U1's are the 5 self-paint rows: borders-008, the three clip-path-*Box-1e, clip-rect-2;
- U2's are the 5 legacy rows: background-clip-002 (DOTTED, slow path), s-11-1-1b-001 (SOLID fast path),
  clip-radius-zoom (DASHED + radius clip), grid align-items-center and flex safe-003 (both abspos children that stay
  clear of the band).

**Executed.**
- `wave54-final --base wave54-open` → **38 OK · 8 WRONG**: exactly the 7 gating and the 1 report key; exit 0. On these
  pictures the planned `geometry-gate.py … --lanes L6 --self-test` reading holds: gating FAIL, control PASS, report FAIL.
- `wave54-open --base wave54-open` → 35 OK · 11 WRONG: the same 8, plus `[FO]` web / ios / android with the box
  20 px low.

**WRONG lines today, verbatim.**
- `[PO] backdrop-filter-clip-rect android … → GEOMETRY WRONG (paint order: overlap rows red 0 / blue 416 of 416 (parent border over positioned children, CSS 2.1 App. E); menus: red bbox (163, 134, 266, 371) n 1136 vs (163,134,266,371) n 1552)`
- `[X] flex-abspos-staticpos-align-self-safe-001 android | crossings 1152 | child fill 16560 (ref 17956) → GEOMETRY WRONG (1152 crossing px …)`

**Fakes it must reject** (`--fakes`, `.geometry.fakes.txt`). Every fake prints WRONG on the named reason:

| fake | prints |
|---|---|
| `PO-today-android` (the capture itself) | paint order + menus |
| `PO-parent-border-dropped`: the band erased outside the menus, so "red over blue" would be trivially true | `navbar band … blue 0/320` |
| `PO-menus-dropped` | paint order + `red bbox None n 0` |
| `PO-menus-through-navbar`: children's side borders drawn across the inverted interior | pink 9800 + red n 1752 |
| `PO-backdrop-lost`: the reorder disturbs the backdrop node | pink 0 |
| `FO-filter-after-clip`: the wrong order, modelled | 10335 reddish + halo 16 |
| `FO-blurred-edge` | bbox + halo 59 |
| `FO-box-20px-low` (the wave54-open picture) | bbox |
| `X-border-across-children` | 112 crossings + fill |
| `X-children-dropped`: no crossings because no child | `child fill 0 … < 0.90 x ref` |
| `X-today-android` | 79 crossings + fill |

**The documented blind spot.** `FO-shadow-dropped` (the ref itself) prints **GEOMETRY OK**. That is why `[FO]` is a
control and U3 is the order's proof.

**Replays** (`--replays`, `.geometry.replays.txt`):
- the U1 replay prints `[PO] … OK`;
- the U2 replays print `[X] … OK` on 5 of 6. **flex safe-002's replay keeps 50 crossing px, a replay artifact.** The
  replay repaints only fill–dark–fill runs and leaves the band where the child's dotted purple border meets it.
- On a correct device render the child's yellow background (border-box) covers the band under its dotted border, as the
  ref shows: 0. So 0.991 is that row's lower bound. A device row that keeps crossings there is WRONG, as the key says.

## 8. Device probe (before the closing gate; orchestrator window)

**[W2] the lane probe**, run once on the integrated lane tree with U1 + U2 + U3 landed:

`tools/titan/gate-driver.sh wave55-l6-probe --sections filter-effects,css-masking,css-flexbox,css-grid --skip-fixture-net`

That is ≈ 10 min on a quiet host. filter-effects and css-masking are U1's and U3's sections; css-flexbox and css-grid
hold 22 of U2's 25 carriers.

**Read, in order:**
1. `python3 tools/titan/results/wave55-plan/android-paint-order.geometry.py wave55-l6-probe --base wave55-open`, then
   `geometry-gate.py wave55-l6-probe --base wave55-open --lanes L6` (exit 0 = every gating key PASSED).
2. `node tools/titan/results/wave53-gate/control-check.mjs wave55-open wave55-l6-probe --lane L6 --sections <the four>`.
   Expect: web and iOS byte-identical; Android changed ⊆ the §5 carriers ∪ the may-move set.
3. `node tools/titan/results/wave52-gate/cells.mjs 'backdrop-filter-clip-rect android|flex-abspos-staticpos android|grid-abspos-staticpos android|clip-path-filter-order' wave55-open wave55-l6-probe`.
4. Open the clip-rect and flex safe-001 Android PNGs next to the ref before any label is written.

**Decisions (pre-registered):**
- U2 is reverted on any of:
  - an `[X] … android` gating WRONG;
  - an `[ID]` U2 row WRONG that L6 caused (in the four probe sections: clip-radius-zoom, grid align-items-center, flex
    safe-003);
  - an Android mover outside the may-move set;
  - a may-move non-carrier with |Δ| > 0.002 whose picture shows anything but a band now under its child.
- U1 is reverted on `[PO] backdrop-filter-clip-rect android` WRONG, or an `[ID]` U1 row WRONG
  (clip-path-*Box-1e, clip-rect-2). U2 goes first (revertOrder).
- U3 never touches a picture. Its only gate is its own mutation log.
- The `[ID]` rows in sections outside the probe (borders-008, background-clip-002, s-11-1-1b-001) and U2's 3
  css-images carriers are read at the closing gate.

**[W1] optional, evidence only** (it decides 0(k)(2)'s wording, not a unit):
- Run `SKIP_IOS=1 ./test-all.sh fixtures/properties/effects/filter-functions.json` on the CURRENT tree (no lane change
  needed).
- Read `Filter_DropShadow_NoBlur` (160×80 `#ecf0f1`, `drop-shadow(4px 4px #111)`) on Android: count the #111-class
  pixels (sum < 80) in the 4-px strips right of and below the box's bbox. Nominal is 4·80 + 4·156 = 944, so expect ≥ 900.
- **≥ 900:** 0(k)(2) closes as "order right by construction (U3), shadow renders on device".
- **0:** the shadow is not rendered; a unit for next wave, radius 3 components (census (3)).
- **Capture frame cropped at the box bounds:** UNMEASURABLE, say so; never read as a pass.

## 9. Risks, and what could not be determined

- **R1 · raster parity (MED, the decisive one).** A behind-content band that does not rasterise exactly like androidx
  `BorderModifierNode` changes bands everywhere. U1 can move 10 self-paint components; U2 can move every legacy band in
  the corpus and the fixture net's committed Android baselines.
  - `android-paint-order.gate-fixtures-scan.out.txt` finds 9 bordered components in the 9 gate fixtures: 8 in
    visual-test (no child, no text) and composition-test `Nested_BoxModel_PaddingBorder` (1 child, inside 16-px
    padding). None carries opacity. So the fixture net's only exposure is parity, plus whatever the dark stage's
    placeholder content paints inside a bordered box (not traced).
  - The defence: copy the bytecode arithmetic (§2.A), the 10 `[ID]` rows, the fixture net (`BASELINE=1`), and the
    control-check.
  - **Could not determine** without a device: whether `drawPath(outer − inner)` drawn by our node is bit-identical to
    androidx's cached `Path`. The same calls at the same coordinates should be, but that is unproven.
- **R2 · U2's reach (MED).** The may-move set is 252 documents in 27 sections, but only 25 Android captures are
  predicted to move. Text ink touching its own box's band is the unknown: 77 documents carry a bordered component with own text.
  An unpredicted mover stops the ship until its picture shows a band now under content. Drop-U2 is the fallback, and
  U1 + U3 stand alone.
- **R3 · the ring-fence.** U2 moves `backdrop-filter-basic-blur`'s opacity-0.6 bordered box's band inside its
  opacity group. That is CSS-correct (css-color-4 §3.3) and the ONLY opacity-bordered component in the corpus (census
  (2b)). Report it plainly in the PR if it moves; no carve-out, no claim.
- **R4 · StyleApplier.kt ownership.** See §3: the 3-line hunk becomes a patch if another lane holds the file.
- **R5 · the slow path is device-unexercised.** No corpus carrier crosses a dotted / dashed / double / 3D band with a
  child. The slow path's behind-content order is pinned in the JVM (P1 / P3) and covered on device only by raster
  identity (background-clip-002, clip-radius-zoom).
- **R6 · not in the change.**
  - Outlines stay on top: css-ui-4 and CSS 2.1 E.2 step 10 put them last.
  - `BorderImageBox`, `TableApplier`'s fabricated cell borders and gap decorations keep their own order.
  - A collapsed-border table with an overlapping cell child is not represented in the census.
- **R7 · out of lane, queued.**
  - (a) **The leading-space residual** on both target cells. Compose's stacked `{text, <br>}*` runs keep a run's
    leading collapsible space after a forced break (css-text-3 §4.1.2). The radius is 30 hosts in 27 documents
    (census (4)), including backdrop-filter-edge-clipping / -paint-order android P 0.9533 and
    text-decoration-skip-spaces-001 android f 0.9389.
    - The fold's `br-stacked-equivalent` rule (`InlineRunFold.kt:530-547`) was re-measured in wave 48 against lifting
      it. A leading-space strip inside the stacked fallback is a different, narrower change.
    - Owner: a Compose typography lane, not L1–L7 this wave.
  - (b) **The drop-shadow silhouette** is the node's rect, not the rendering's alpha mask (filter-effects-1 §6.1). Only
    currentcolor-003 is exposed.
- **R8 · U1 alone is nearly invisible to SSIM** (+0.0015). Its value is the picture, so it is claimed only on its
  `[PO]` key. A "P→P picture-correct" label without that key is not written.
- **Could not determine:**
  - Whether Compose's drop-shadow paints pixels on device: no committed capture shows one (W1).
  - Whether the gradient-powerless-hue Android crossings are U2's. Their f 0.70–0.755 is dominated by other defects, and
    the IR places them in `abspos-unknown`. Listed as carriers on the scan, flat in §6.
  - The exact `Path.op` constant inside androidx `createRoundRectPath`: the call graph was read, its body was not
    decoded. The lane decodes it.

STATUS: COMPLETE

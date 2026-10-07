# Wave-53 family brief — `contents-root-background` (BACKLOG 0(ac))

All paths are relative to `.claude/worktrees/trusting-bohr-bd6fbf`. Cells are `wave52-ship <section>/<test> <platform> <P|f> <ssim>`,
read with `tools/titan/results/wave52-gate/cells.mjs`. The opening gate was running when this was written, so nothing was built, run or
captured. Beside this brief:
- `contents-root-background.census.mjs` → `.census.json`: the IR and WPT-source census, joined to cells through `score-gate.mjs loadRun`.
- `contents-root-background.replay.mjs` and `.geometry.mjs` (each with an `.out.txt`): PNG replays scored with the scorer's own SSIM call
  (`ssim.js` with `{ssim:'fast'}`, `inject-wpt-block.mjs:478`). Each one reads 4–5 PNGs.

**Headline.** The defect is one mechanism, and each of the three composed canvases carries its own copy of it. Root-to-canvas background
propagation (css-backgrounds-3 §2.11.2) is implemented for the **colour layer only**. Every canvas resolver reads the body-root's
`BackgroundColor` and never reads its `BackgroundImage`. `display: contents` is not the cause. It only decides that the root's own box
paints nothing either, and an unsized body-root box paints nothing anyway. The same mechanism explains the only other root-background-image
carriers in the corpus, `css-backgrounds/background-attachment-margin-root-001/-002` ×3. So the family is **3 tests / 9 cells**. All 9 fail
today and no passing cell can reach the changed path.

## 1. Target cells

| cell | wave51-fix | wave52-calib | **wave52-ship** |
|---|---|---|---|
| css-display/display-contents-root-background web | P 1 | f 0.534 | **f 0.534** |
| … ios | P 0.9994 | f 0.5346 | **f 0.5346** |
| … android | P 0.9988 | f 0.5355 | **f 0.5355** |

The captures did not change between wave51-fix and wave52-calib (byte-identical). Only the reference was re-frozen, by L12-B's ROOTBG
contract. The old pass was degenerate: the erasing body background had whitened the old reference. `cell-review.json` gives all three
cells HONEST_FAIL, and the second reviewer on android agrees.

Second-tier carriers, same mechanism (§6), named by the wave-52 brief `runtime-bugs-and-gaps.md:32` as "root-background canvas
propagation … on a sized root":
- `css-backgrounds/background-attachment-margin-root-001` web / ios / android: f 0.4511 / 0.4509 / 0.451.
- `css-backgrounds/background-attachment-margin-root-002` web / ios / android: f 0.339 / 0.3391 / 0.3389.

## 2. The picture (390×600 full size; ref and captures opened, pixels counted)

- **Reference** (`tools/wpt/refs/9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/css-display/display-contents-root-background.png`):
  - Every background pixel is (0,128,0), 232 481 px in all, frame included. The four corners and the centre (195,300) are (0,128,0).
  - The 8-point ring is uniform, so `padColorFor` (`capture-browser-ref.mjs:728`) fills the 16-px frame with green.
  - Black ink "Pass if the background is green." has bbox (17,35)–(258,51), 1519 non-background px.
- **Web capture** (`tools/titan/runs/wave52-ship/sections/css-display/screenshots/…`):
  - The background is (255,255,255) everywhere: 232 481 px, 0 green px.
  - The text sits at the same bbox (17,35)–(258,51) with the same 1519 ink px. The glyph geometry is identical to the reference.
- **iOS capture:** white everywhere, 0 green px. Text bbox (17,35)–(257,51), 1415 ink px.
- **Android capture:** white everywhere, 0 green px. Text bbox (17,35)–(258,51), 1415 ink px.

So the text is right on all three platforms. The whole divergence is the ~232 k background pixels: white where the reference is green.

## 3. The wire

`tools/titan/runs/wave52-ship/sections/css-display/per-test-ir/wpt__css-display__display-contents-root-background.json` (954 B) holds two
FLAT roots. The extractor merges `:root{…}` into the synthetic body-root bag (`extract-fixture.mjs:10875-10893`, `:11038`):

```
{"id":"…__0-234","properties":[{"type":"Display","data":"CONTENTS"},
  {"type":"BackgroundImage","data":[{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03…","data":true}]}],
 "meta":{"role":"body-root"}}
{"id":"…__1-235","properties":[],"text":"Pass if the background is green.","meta":{"sourceTag":"p"}}
```

- The payload is `tools/wpt/css/support/1x1-green.png`: a 1×1 image (IHDR `00000001 00000001`) whose palette entry is `00 80 00`, i.e. the
  reference's (0,128,0).
- The extractor and converter lose nothing. The WPT-source census finds exactly the 3 IR carriers (§6).
- The painters decode this exact payload:
  - `css-images/css-image-fallbacks-and-annotations002` web / ios / android: P 1 / 0.999 / 0.9982.
  - `…003`: P 1 / 0.999 / 0.9982.

## 4. The mechanism (traced)

**The drop.** Each of the three canvases reads only `BackgroundColor` from the body-root.

*Web* (`apps/web-harness/src/ui/ComposedCaptureGallery.tsx`):
- `resolveCanvasBackground` (:117) returns `buildStyles(bodyRoot.properties).backgroundColor` (:128). For this doc that value is
  `undefined`, so the function returns `CANVAS_BG_DEFAULT` `#FFFFFF`.
- The outer canvas div paints only that string (`background: … canvasBackground`, :885; the clip-path variant is at :951).
- `backgroundImage`, which the same `buildStyles` call computes, is discarded.

*Android* (`apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`):
- `resolveComposedCanvasBackground` (:1308) reads only the `"BackgroundColor"` property (:1314-1316), which is null here.
- The runtime rule `composedCanvasBackground` (`runtimes/compose/…/core/renderer/WptCaptureMode.kt:352`) then returns
  `WPT_CANVAS_BACKGROUND` (white).
- That colour is painted by `.background(canvasBackground)` (:1839/:1841).

*iOS* (`apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift`):
- `ComposedCaptureCanvas.canvasBackground` (:442) calls `ComponentRenderer.resolvedBackgroundColor(from:)` (:447).
  That function (`runtimes/swiftui/…/Renderer/ComponentRenderer.swift:758`, a seam file, read-only here) returns
  `StyleBuilder.build(from:).backgroundColor`, which is nil here.
- `WPTCanvas.composedBackground` (`runtimes/swiftui/…/Renderer/WPTCaptureMode.swift:278`) then returns white.
- That colour is painted by `.background(canvasBackground)` (:947).

**Why nothing else paints the image.** The body-root is a sibling root, not the parent of the `<p>`. Its own box therefore holds no flow
content (`extract-fixture.mjs:10878-10886`).
- Compose and iOS go further: `ContentsUnboxing.resolve` self-strips a `display: contents` root to its inheritable subset, which drops
  `BackgroundImage` (`runtimes/compose/…/core/renderer/ContentsUnboxing.kt:198-205`, `runtimes/swiftui/…/Renderer/ContentsUnboxing.swift:185-194`).
- Web emits `display: contents` on an empty element.

Whatever `display` the root had, no box carries green across the canvas. The spec says no box should:
- css-display-3 §2.7: a root `display: contents` computes to `block`.
- css-backgrounds-3 §2.11.2: the root's background becomes the canvas background, its painting area covers the whole canvas, and the root
  does not paint it again.

**The same chain, visible, on the margin-root pair.**
- Their body-root is SIZED (`Height 300`, `Margin* 50`), so the root's own box does paint its `BackgroundImage`. On all three captures it
  paints the gradient box at (66,66)–(323,365) on white.
- Inside that box the tiles match the reference to within 0 / 2 / 1 channel levels (web / ios / android).
- The reference tiles the whole 358×568 viewport, anchored at (66,66) for 001 (a `scroll` layer, so the root box) and at (16,16) for 002
  (a `fixed` layer, so the viewport). Its 16-px frame is white because the ring is not uniform.

**One mechanism on three platforms.** The propagation rule exists once per canvas (web / Compose / iOS twins, sharing the pure runtime
helpers `composedCanvasBackground` / `WPTCanvas.composedBackground` / `containmentBlocksCanvasPropagation`). All three twins stop at the
colour layer.

Ruled out:
- The extractor and converter: the IR carries the image.
- The data-URI decoders: the same payload passes elsewhere.
- Text placement: the bboxes match the reference.
- The containment gate: there is no `Contain` on this body-root.

## 5. The fix

**Shape: three twins of one rule.** When the body-root is on the propagation path (same containment gate as the colour), the canvas
paints the body-root's background-IMAGE layers as the canvas background, behind the root forest. The body-root node in the forest then
loses its `BackgroundImage`/`Size`/`Position*`/`Repeat`/`Attachment`/`Origin`/`Clip`. This is the twin of the existing
`withCanvasOwnedBodyMargin` strip (web :661, Compose :1513, iOS `ComposedRootStack.swift:612`), and it follows §2.11.2's "the root does
not paint it again". Colour handling is untouched. The geometry:

- **(F1, the target) Tile origin per layer.**
  - `fixed` → the ICB origin (the reference's viewport, image (16,16)).
  - `scroll`/`local` → the root's padding box, i.e. the ICB plus the canvas-owned root margin (`resolveCanvasMargin` and its twins).
  - The painting area is the whole canvas.
  - Never pass `background-attachment: fixed` through verbatim on web: it would anchor to the gallery page's viewport.
  - Web realization: one div per canvas whose `padding` equals the root margin, with `background-origin` set per layer
    (`content-box` for scroll, `padding-box` for fixed), `background-attachment: scroll`, and clip `border-box`.
  - Natives: pass a per-layer offset into `ColorApplier.applyColors` (Compose; url layers decode synchronously, `ColorApplier.kt:718`)
    and through a new public wrapper over the internal `engineBackgroundImage` (`BackgroundImageApplier.swift:214`).
- **(F2, the margin-root pair) Painting surface, mirroring the reference frame.**
  - If the layer stack is *uniform by construction*, paint it over the framed outer surface (where the colour goes today).
    Uniform means every layer is either a gradient whose stops all share one sRGBA, or a `data:` PNG whose IHDR is 1×1, and every layer
    repeats on both axes.
  - Otherwise paint it on the ICB only (358 × ≥568, inside the frame), and let the frame keep today's colour-layer value.
  - This is the twin of the reference's uniform-ring rule `padColorFor` (`capture-browser-ref.mjs:728`/`:760`). It is capture-frame
    chrome, so say so in the code.

**Files** (no seam hunk needed; `ComponentRenderer.*` and `extract-fixture.mjs` are untouched):

| platform | edit | add |
|---|---|---|
| web | `apps/web-harness/src/ui/ComposedCaptureGallery.tsx` (resolver beside :117; outer div :885; ICB div ~:900-955; `flowRoots` :834) | pure `runtimes/web/src/engine/background/RootBackgroundPropagation.ts`; tests `apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx`, `runtimes/web/tests/background/RootBackgroundPropagation.test.ts` |
| Compose | `ScreenshotCaptureScreen.kt` (beside :1308; chain :1832-1842; roots rewrite :1705); `runtimes/compose/…/core/renderer/WptCaptureMode.kt` (pure rule beside :352/:395) | tests `runtimes/compose/src/test/…/core/renderer/WptCanvasBackgroundTest.kt` (extend), `apps/android-harness/app/src/test/…/screenshot/ComposedCanvasRootBackgroundTest.kt` |
| iOS | `CaptureCanvas.swift` (`ComposedCaptureCanvas` :442, :947, split :379); `runtimes/swiftui/…/Renderer/WPTCaptureMode.swift` (pure rule beside :278/:341); `runtimes/swiftui/…/StyleEngine/spacing/ComposedRootStack.swift` (strip twin beside :612) | `runtimes/swiftui/…/StyleEngine/background/RootBackgroundPropagation.swift` (public modifier); tests `runtimes/swiftui/Tests/StyleConverterRuntimeTests/WPTCaptureModeTests.swift` (extend; Catalyst-runnable — ios-harness tests need a simulator) |

**Spec:** css-backgrounds-3 §2.11.2 (The Canvas Background and the Root Element), §3.4 (`background-attachment: fixed` is relative to the
viewport); css-display-3 §2.7 (a root `contents` computes to `block`); css-contain-2 §2 (the existing containment gate, as cited at `ComposedCaptureGallery.tsx:104-110`).

**Not in scope.** The natives' root `contents` self-strip stays spec-divergent: §2.7 says it should be a block. That is not this failure
and no carrier needs it; record it, don't fix it here.

## 6. Blast radius (`contents-root-background.census.json`)

**Carrier predicate** (for `control-check.mjs`): a per-test IR component with `meta.role == 'body-root'` carrying a property of type
`BackgroundImage`. Over the 1435 wave52-ship docs it matches **3 tests / 9 cells, 0 passing**:

| test | web | ios | android |
|---|---|---|---|
| `wpt__css-display__display-contents-root-background` | f 0.534 | f 0.5346 | f 0.5355 |
| `wpt__css-backgrounds__background-attachment-margin-root-001` | f 0.4511 | f 0.4509 | f 0.451 |
| `wpt__css-backgrounds__background-attachment-margin-root-002` | f 0.339 | f 0.3391 | f 0.3389 |

**WPT-source census** (`<style>` rules on html/body/:root with `background(-image)` holding `url(`/`gradient(`; `style=`/`background=`
attributes; `.style.background` script writes): the same 3 tests, 0 script writes. Nothing is lost before the wire.

**Outside the predicate, captures must be byte-identical:**
- 86 colour-only body-roots (252 cells, 178 P).
- 195 body-roots with no background (555 cells, 444 P).
- 1151 docs with no body-root.

## 7. Predictions

Predicted scores come from the PNG replays: today's capture re-composited over the tile, or each platform's own shipped tiles re-laid in
the reference geometry.

- **display-contents-root-background web: f 0.534 → P (HIGH).** Replay 1.0000.
- **… ios: f 0.5346 → P (HIGH).** Replay 0.9995.
- **… android: f 0.5355 → P (HIGH).** Replay 0.9991.
- These need F1 with the frame painted. ICB-only painting with a white frame replays at 0.8795 / 0.8790 / 0.8787 (f). That is why F2
  must classify this stack as uniform.
- **margin-root-001 ×3: f 0.451 → P (MED)** with F1+F2. Replay from each platform's own tiles: 0.9996 / 0.9992 / 0.9997. Other variants:
  - F1 alone (framed surface, spec origin): f 0.8785.
  - ICB origin used for the scroll layer: f 0.51 framed, 0.63 ICB-only.
- **margin-root-002 ×3: f 0.339 → P (MED; natives MED-LOW).** Replay 0.9996 / 0.9992 / 0.9997; F1 alone gives f 0.8693. The natives
  must translate `fixed` themselves: iOS `BackgroundImageApplier` treats attachment as a deliberate no-op, and Compose's `ColorApplier`
  does not read it.
- **Must not move (byte-identical PNGs):** every capture outside the 3 carrier stems. Watch by name:
  - `css-contain/contain-{body,html}-bg-001..004` ×3 (P 1 / 0.9995 / 0.9988); the containment gate must also gate images.
  - `css-cascade/initial-background-color` ×3 (P 1).
  - `css-color/a98rgb-003` ×3 (P 1 / 0.9985 / 0.9976).
  - `css-masking/clip-path/clip-path-document-element{,-will-change}` ×3 (P 1).
  - `filter-effects/backdrop-filter-root-element` ×3 (P 1 / 1 / 0.9998).
  - `css-display/display-contents-text-only-001` ×3 (P 1 / 0.999 / 0.9979): a contents root with no background.
  - `css-images/css-image-fallbacks-and-annotations002/003` ×3 (same payload; the painter is untouched).
  - (`filter-effects/backdrop-filter-basic-blur` is ring-fenced: reported only.)

## 8. Verification plan

**Unit pins, on VERBATIM wave52-ship body-root payloads.** Each pin is followed by the mutation that must turn it red.

1. Target body-root (`Display CONTENTS` plus the data-URI `BackgroundImage`, copied from §3): the resolver returns one image layer
   containing that exact URL, `uniform = true`, framed surface; the body-root node in the forest has no `Background*` left.
   - Mutation: revert to the colour-only read → no layer.
   - Mutation: drop the strip → the forest node keeps `BackgroundImage`.
2. Margin-root-001 body-root (2 gradients; `BackgroundAttachment [scroll, fixed]`; `BackgroundSize` 100×100 ×2; `Height 300`;
   `Margin* 50`): `uniform = false` → ICB-only; origins = [root box (50,50) for layer 0, ICB (0,0) for layer 1]. Margin-root-002 gives the
   mirror image.
   - Mutation: "every stack is uniform" → 001 goes framed.
   - Mutation: ignore attachment → 002's origin is wrong.
3. IHDR test: the 1×1 payload → uniform; the same payload with IHDR patched to 2×2 → not uniform.
   - Mutation: return true for any url.
4. Containment: the target payload plus `Contain ["LAYOUT"]` → no propagation, and the body-root keeps its image.
   - Mutation: drop the gate.
5. Colour-only control (verbatim `initial-background-color` and `a98rgb-003` body-roots): the canvas style objects and modifiers are
   identical to today (deep-equal snapshot), and no image keys are written.
   - Mutation: always emit `backgroundImage: 'none'`.
6. Twins: pins 1–5 run on Compose (JVM: the pure rule in `WptCaptureMode.kt` plus the harness resolver) and on iOS (Catalyst: the
   `WPTCanvas` pure rule plus the `ComposedRootStack` strip).

**Focused suites, after the opening gate only:**
- `npm -w apps/web-harness run test` (ComposedCanvas\*) and `npm -w runtimes/web run test -- background`.
- `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest --tests '*WptCanvas*' :app:testDebugUnitTest --tests '*ComposedCanvas*')`.
- Catalyst `xcodebuild test … -only-testing:StyleConverterRuntimeTests/WPTCaptureModeTests`.

**Closing gate.**
- Probe sections: `css-display` and `css-backgrounds` (the carriers); `css-contain`, `css-cascade`, `css-color`, `css-masking`,
  `filter-effects` and `css-images` (controls).
- `control-check.mjs`: the allowed set is the predicate above on all three platforms.
- Watchlist lines: `css-display/display-contents-root-background`, `css-backgrounds/background-attachment-margin-root-001`,
  `css-backgrounds/background-attachment-margin-root-002`.

## 9. Risks and recommendation

- **The F2 frame rule mirrors an instrument rule (`padColorFor`).** A future `CANVAS_REV` that changes the ring rule must move it too.
  Keep it in one pure function per runtime, cite `capture-browser-ref.mjs:728`, and pin it.
- **Merged html+body bag.** A `body { margin }` cannot be told apart from an `html { margin }`, so with no carrier the scroll-layer origin
  would be guessed. That is the same caveat `bodyRootHasContainment` documents. A non-initial `background-position` combined with a root
  margin has no carrier either: log it, don't guess.
- **Double paint if the strip is skipped.** 002's sized body-root box would repaint wrong-phase tiles over (66,66)–(323,365).
- **Native tile-anchor offsets** (Compose `ColorApplier.tileAnchor`, iOS `BackgroundImageGeometry`) are the likeliest off-by-frame bug.
  Pin 2 checks the numbers. The PNG replay assumes the platform tiles are exact, and they are inside the shipped boxes (≤2 levels).
- **Web data-image decode timing** before `data-capture-ready`: LOW. Per-component url backgrounds already pass on web.
- **The three canvas files are hot.** Wave 52's L2 edited all three, so the orchestrator must check that no other wave-53 lane owns them.

**Recommendation: GO.** One small lane (S–M): three twins of one rule, no seam hunk, a 3-test carrier set with 0 passing cells reachable,
and +3 HIGH / +6 MED predicted. If wave 53 already staffs a composed-canvas lane, fold it in as that lane's item: the files are the same.

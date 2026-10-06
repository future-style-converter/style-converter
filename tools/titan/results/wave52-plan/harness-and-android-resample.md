# Wave 52 plan — family `harness-and-android-resample`

Read-only brief, written 2026-09-25 while the `wave52-open` gate ran. Every byte number below comes from
`harness-and-android-resample.probe.mjs` (beside this file; `node <probe> [repo-root]`, reads only) over the
committed `tools/visual/baseline/` PNGs and the `wave51-fix` gate captures. Every code claim cites the gate tree.

## 1. Queue items covered

- `## Ranked queue (wave 51+)` 0(aa) — "Android resamples the capture by a sub-pixel amount on stems with a
  BLURRED box-shadow" (wave 51 PR 3 review, `tools/titan/results/wave51-A/refresh/review.md`).
- `## Ranked queue (wave 51+)` 0(ab) — "Relocate the label machinery into the harnesses".
- `## Instrument decisions pending (decide, then execute or Park — never drift)` — state of each item (§10).
- Governing constraints: `## Standing constraints` "A device A/B that claims to EXCLUDE a code change records the
  installed `base.apk` sha1"; `## Next-wave obligations` #0 "PR 3 cannot reach the corpus by construction".

Both queue items are **score-neutral by construction** (§5): neither can flip a gate cell. Rank: 0(aa) A/B first
(cheapest, one emulator session), 0(ab) web + Compose halves second (mechanical), 0(ab) iOS half last (module design).

## 2. Target cells (cheapest first)

| cell (what carries it) | platform | ssim | visible defect, read from the PNG | what the reference shows |
|---|---|---|---|---|
| `tools/visual/baseline/Android__023_Shadow_Simple.png` (fixture net, `visual-test.json`) | Android | 1.0000 vs its own baseline; tripwire glyph-mask stem | 390×72, label "SHADOW SIMPLE" rows 6..12, white 50×40 box at x16..65/y16..55 with a dark blur to the lower-right. Invisible to the eye. Bytes: 70 glyph px read **173** where web/iOS read 174; 77 ground px immediately RIGHT of a glyph read **27** (ground 26); the box's LEFT column x=16 reads **254** (iOS/web 255) while its TOP row y=16 reads 255; right-of-box x=66 reads 21 vs iOS 20, below-box y=56 reads 20 = iOS. | web/iOS: 174 on every glyph px, 26 on every ground px in the band, 255 on both box edges |
| `Android__025_Shadow_Multiple.png` | Android | 1.0000 | same signature: 69 glyph px at 173, 90 right-neighbours at 27; box left column 254 | same |
| `Android__093_Avatar_Circle.png` | Android | 1.0000 | 390×92, purple 60-px circle with a 3-px white ring at x16..76; 46 glyph px at 173, 55 right-neighbours at 27 | same |
| `Android__024_Shadow_Colored.png` | Android | 1.0000 | blue glow reaches the band so the exact-ground census undercounts; 15 glyph px at 173, box left column 254 | same |
| wave51-fix filter-effects/backdrop-filter-box-shadow | android | P 0.9954 (web P 1.0000, ios P 0.9855) | the ONLY score-bearing Android cell with a blurred outset `BoxShadow` that passes: pink box over a green plate with a grey Gaussian shadow — matches the ref by eye; Android vs web 4.62 % px differ, only 877 of 10 822 are byte-equal to a web pixel one column away → **no horizontal-shift structure** above text-AA noise | identical scene |
| wave51-fix css-color/currentcolor-003 | android | f 0.7655 colorFailed (web f 0.9862, ios f 0.7604) | Android paints one full-width red block y≈85..240 with blurred black "Lorem ipsum"; NOT this family — a currentColor-shadow ink defect on all three | three rows of green/red bars, solid + blurred, coloured text |

Controls that carry NO signature (0 differing px in rows 0..15, glyph bytes 174 ×all): 027 (spread only), 026/107
(inset), 000, 081, and — decisive — three OTHER blurred-shadow stems: **089 Card (blur 6/3, r12, w250), 096 Tooltip
(blur 8, r6), 098 Neumorphic (blur 16 ×2)**. 093 and 096 share the same blur (8 px → `blurMaskRadius` 6.06) and only
093 shows it: the queue text's predicate "BLURRED box-shadow ⇒ resample" is an over-statement.

## 3. Mechanism — what the bytes prove, with code

**Where the label is drawn.** `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/
ScreenshotCaptureScreen.kt:938-939` (`showsLabel = !LocalWptCaptureMode.current && …`), `:946` (`widthPx`),
`:1005-1026` — the per-component `CaptureCanvas` Box's `drawWithContent { drawContent(); … drawRect(BlockLabel.COLOR,
Offset(x,y), Size(1f,1f)) }`, one 1×1 rect per atlas bit, integer coordinates, in the SAME display list as the
component (`drawContent()` at `:1008`, `.padding` after it at `:1027`). The canvas Box is `.width(canvasWidth)`
(`:964`) inside `Box(fillMaxWidth().weight(1f).verticalScroll(), TopCenter)` (`:826-831`) → integer position.
**Capture.** `:643-668` `captureWithPixelCopy(window, bounds)`; bounds are `roundToInt()` of `positionInWindow`
(`:839-844`); `ScreenshotManager.kt:127` `bitmap.compress(PNG, 100)` — no rescale (the `createScaledBitmap` at
`:463` is the 4× metric-probe path only). Emulator: `test-all.sh:866-872` `-gpu swiftshader_indirect`,
`:975-976` `wm size 390x844` / `wm density 160`.
**The shadow.** `runtimes/compose/src/main/java/com/styleconverter/runtime/effects/shadow/OutsetShadowPainter.kt:
93-101` — "NOTE: no elevation fast path", one `drawBehind`; `:165-166` `clipPath(borderBox, ClipOp.Difference)`;
`:179-203` `android.graphics.Paint` + `BlurMaskFilter(maskRadius, NORMAL)`; `:236-237` `canvas.nativeCanvas.
drawPath(path.asAndroidPath(), nativePaint)`; `ShadowApplier.kt:218-219` `blurMaskRadius`. Chain position:
`effects/EffectsFacade.kt:225-227`. No `graphicsLayer` anywhere in the shadow chain (the only `graphicsLayer` uses in
`core/renderer/ComponentRenderer.kt` are text-run compensations at `:4140` and `:7444`).
**Composed (corpus) path is different machinery:** `ScreenshotCaptureScreen.kt:1686-1697` `graphicsLayer.record {
drawContent() }; drawLayer(graphicsLayer)`; `:697-710` `captureComposedLayer` → `layer.toImageBitmap()` →
`asAndroidBitmap().copy(ARGB_8888)`. Label-free (`:938` WPT term; design-record §6 "What the corpus will NOT see").

**What the bytes say (probe §1–2).** (i) The leak is on VERTICAL edges only: glyph strokes (right neighbour +1,
glyph −1) and the box's left column (254) and right-of-box (+1); horizontal edges are exact (top row 255 ×3, below-
glyph 8/58 vs right-of-glyph 38/87 on 023). (ii) Magnitude is exactly one 1/256 step: coverage 0.996 × alpha 179
→ 178 → (173,173,179); 0.004 × 179 → 1 → (27,27,47); 255 × (1 − 1/255) = 254. (iii) It is **x-periodic, period
24 px, not uniform**: lit right-neighbour columns 023 = 15,19,21 | 39,41,43 | 63..72; 093 = 15..19,21 | 39,42,43 |
63..72; 025 = 15,19,21 | 39,43 | 63,71 | 91,93,96; P-1 glyph columns 14..21 | 38..42 | 64..72 (| 90 on 024) —
glyph indices 1, 5, 9, 13 (+ spill into 10/14), while 'O' (x32/36), 'D' (26/30), 'I' (58), 'L' (74), 'E' (80)
strokes are exact on the same PNGs. Both box edges of 023 (x=16, x=66) fall inside affected windows. (iv) Left
neighbours are rarely lit (15/85 on 023, 0/15 on 024) → a rightward shift, not a symmetric blur.

**Hypotheses.** H1 — the queue's suspect ("the elevation/blur path puts the component in a hardware layer whose
composite into the PixelCopy source is sampled at a fractional offset"): **refuted at the code level** (no elevation,
no layer — cites above) **and by the bytes** (a layer/readback offset is uniform in x and would touch every stroke;
(iii) says otherwise). H3 — a fractional canvas CTM: refuted (integer layout, and 000/027/081 are byte-exact ×3; a
CTM offset moves every stem). H2 — **a GPU-raster property of the frame under `swiftshader_indirect`**: an
anti-aliased 1-px quad (Compose `drawRect`/`background` paint antialiased) whose edge lands 1/256 px off its
integer position in some x-windows, on frames whose op sequence contains a `BlurMaskFilter` draw — consistent
with (i)–(iv) and with the artefact having existed beside the OLD in-component label (same display list, different
node; review.md). H2 is a hypothesis, not a finding: nothing in this tree names the rasterizer's sub-pixel grid,
and 089/096/098 show the trigger is narrower than "any mask-filter draw".

## 4. Proposed fix (and why 0(aa) is a diagnosis, not a render change)

There is no CSS-correctness content: css-backgrounds-3 §6.1 is honoured (Gaussian σ = r/2, `blurMaskRadius`), the
label is chrome outside every CSS box (docs/DYNAMIC_CAPTURE.md §5 "the runtimes render no label"). The only
deliverable is the A/B record plus, if arm A3 wins, a 3-line harness hardening:

**A/B (device, after the gate; one private `-read-only` emulator, `ANDROID_SERIAL` set, installed `base.apk` sha1
recorded per arm — standing constraint).** Stems: visual-test 000/023/024/025/027/089/093/096, `SKIP_IOS=1
SKIP_WEB=1 ./test-all.sh fixtures/visual-test.json`, judged by this brief's probe (pass = 0 lit right-neighbour
columns AND glyph bytes 174 on all four stems) — never by SSIM, which cannot see 1 LSB.
- A0 control: current tree, `swiftshader_indirect` → must reproduce the four stems byte-for-byte (determinism, as
  `wave51-open` did for the corpus). Falsifier of everything below if it does not.
- A1 GPU backend: `-gpu host` (and/or `angle_indirect`) in `test-all.sh:872`. Vanishes → SwiftShader rasterizer;
  persists unchanged → Skia/HWUI-side.
- A2 HWUI backend: `adb shell setprop debug.hwui.renderer skiavk` vs `skiagl` before launch. Discriminates GL vs
  Vulkan Ganesh on the same rasterizer.
- A3 non-AA label paint (harness only): at `ScreenshotCaptureScreen.kt:1023` draw the rects via `drawIntoCanvas {
  nativeCanvas.drawRect(x, y, x+1, y+1, Paint().apply { isAntiAlias = false; color = 0xB3EDEDED }) }`. Label leak
  gone while the box's x=16 column stays 254 → the leak is coverage-AA specific and the harness can be hardened
  alone (predicted: 4 Android baselines move by ≤1 LSB on ≤150 px each, tripwire still 136/136, corpus untouched).
- A4 trigger isolation (runtime MUTATION, never shipped): `ShadowApplier.blurMaskRadius` → 0 for the run (keeps the
  Difference clip and the drawPath, drops the mask filter). Gone → the mask-filter op is the trigger; stays →
  `clipPath(Difference)` or the extra drawPath is.
- A5 layer boundary: wrap the canvas Box in `Modifier.graphicsLayer()` (a RenderNode around the whole canvas).
  Gone → a layer boundary isolates the label from the shadow's op sequence (a cheap harness fix candidate).
**Decision rule stated in advance:** A1/A2-only wins ⇒ an INSTRUMENT property; changing the emulator GPU mode is a
capture-contract change on every Android capture (1369 scored cells) and must go through the Calibration-gate
recipe (sha1 every PNG against the previous gate) — not worth it for an invisible artefact: **Park with the record**.
A3 or A5 wins ⇒ ship the harness change (+ refresh 4 Android PNGs after LOOKING). A4-only ⇒ runtime-side, still
invisible ⇒ Park, add the finding to the OutsetShadowPainter header.

## 5. Blast radius and carriers

**0(aa) corpus census** (grep `"type": "BoxShadow"` over the 1435 `per-test-ir/*.json`; shape `data: [{x,y,blur?,
spread?,c,inset?}]`): 7 documents carry `BoxShadow`; 2 carry a BLURRED outset layer — css-color/currentcolor-003
(`blur 5`, currentColor) and filter-effects/backdrop-filter-box-shadow (`blur 10`); 3 have no blur key (0 blur), 1 is
inset, 1 is spread-only (the two view-transitions twins). Cells: wave51-fix filter-effects/backdrop-filter-box-shadow
android P 0.9954; wave51-fix css-color/currentcolor-003 android f 0.7655 colorFailed. The corpus draws no label
(WPT mode) and captures through `GraphicsLayer.toImageBitmap`, so **no corpus cell carries the signature**; the one
passing carrier shows no ±1-px shift structure (§2). Cells at risk from any emulator-mode change: all 1369 Android
scored cells (BACKLOG 0(z): "Android 1077 → 1080/1369") — hence the Park rule above.
**0(ab)**: zero corpus cells (label never drawn in WPT/inbox/composed runs on any platform: `ScreenshotCaptureScreen.
kt:938`, `HarnessLabelChrome.swift` header "WHEN IT SHOWS … !wptCaptureMode", web `CaptureGallery` `!WPT_MODE`).
At risk only if a byte moves: the 390 committed baselines — proof of a pure move is `label-chrome-tripwire.test.mjs`
136/136 + `BASELINE=1 ./test-all.sh --gate-set` exit 0 ×8 + the three atlas-checksum pins green.

## 6. Predicted flips

None, on either item (confidence high): 0(aa) is below every threshold and off-corpus; 0(ab) is a move. What would
falsify: a gained/lost cell in the post-move gate attributable to a runtime file — impossible if `git diff --stat
runtimes/` shows only the deletions and two comment edits (§7). For the A/B: A0 not reproducing the four stems
byte-for-byte falsifies determinism and voids the arms (medium confidence it reproduces — the artefact survived
the label's relocation from inside the box to the chrome band, and two captures months apart).

## 7. 0(ab) — the exact move plan

Referrers (grep of `BlockLabel|BlockFontLabel|HarnessLabelChrome|BlockFont`, build dirs excluded): no runtime
SOURCE file uses any of them except their own three files per platform and comments (`ComponentRenderer.kt:6214`,
`ComponentRenderer.swift:4034/4037/4096/5144`, web `sdui/ComponentRenderer.tsx:1287`). `runtimes/web/src/index.ts`
never exported `BlockFontLabel` — the harness reaches it through the `"./*": "./src/*"` export + `tsconfig.json:22-23`
paths. So the product runtimes lose 0 public API in use.

**Compose (S).** Move `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/BlockLabel.kt` (180
lines) + `BlockFont.gen.kt` (58) → `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/`
(package `com.styleconverter.test.screenshot`; `object BlockLabel` public → internal, its header's "PUBLIC because the
harness is a different Gradle module" rationale goes). Drop the import at `ScreenshotCaptureScreen.kt:53`. Move
`runtimes/compose/src/test/…/core/renderer/BlockLabelTest.kt` (14 `@Test`, plain JUnit4 + `toArgb`) → `apps/
android-harness/app/src/test/java/com/styleconverter/test/screenshot/`. Re-point `HarnessLabelChromeSourceTest.kt:188`
(`labelPath` read from disk for the absence pins at `:196-197`). Update `ComponentRenderer.kt:6214` prose and
`tools/visual/gen-block-font.mjs:63`. Counts: compose 3232 → 3218, android-harness 129 → 143 — quoted at
`README.md:212-213`, `CLAUDE.md:292-293`, `docs/STATUS.md:2454-2455`, `runtimes/compose/README.md:15`,
`apps/android-harness/README.md:29`; `tools/visual/doc-staleness-check.sh:169-172,192` hold them.

**Web (S).** Move `runtimes/web/src/renderer/BlockFontLabel.ts` (185) + `BlockFont.gen.ts` (55) → `apps/web-harness/
src/ui/`; imports at `apps/web-harness/src/ui/LabelChrome.tsx:64` and `tests/ui/LabelChrome.raster.test.tsx:46`
(`@style-converter/web/renderer/BlockFontLabel` → `../../src/ui/BlockFontLabel`); move `runtimes/web/tests/renderer/
BlockFontLabel.test.ts` (13 `it`) → `apps/web-harness/tests/ui/`; `gen-block-font.mjs:46`; `RendererParity.test.tsx:21`
comment. Counts: web 1338 → 1325, web-harness 307 → 320 — the live vitest run decides (`it.each`); rows at
`README.md:211,215`, `CLAUDE.md:291,295`, `docs/STATUS.md:2453,2457`, `runtimes/web/README.md:71`.

**iOS (M — the real module-boundary cost the queue text glosses over).** Files: `runtimes/swiftui/Sources/
StyleConverterRuntime/Renderer/HarnessLabelChrome.swift` (130, `public struct`), `BlockLabel.swift` (212: `public enum
BlockLabelLayout`, `public struct BlockLabel: View`, `static let labelColor`), `BlockFont.gen.swift` (57). Mount
sites: `apps/ios-harness/StyleConverterTest/Screenshot/CaptureCanvas.swift:193-196` and `:253-256`. Tests:
`BlockLabelTests.swift` (8), `HarnessLabelChromeRasterTests.swift` (11), helper `CaptureCanvasMirror.swift`
(`withLabelChrome` at `:47-48,:60,:73,:82`, `ImageRenderer` at `:93-96`), all `@testable import StyleConverterRuntime`.
Constraint: `HarnessLabelChrome.swift:19-24` "WHERE IT LIVES: in the runtime package … ONLY so the Mac Catalyst test
target can compose and rasterize it — apps/ios-harness has no runnable test destination", and `doc-staleness-check.sh:
194-200` warns the ios-harness XCTest bundle (`project.yml:82-96`, needs a simulator) is in no documented row. Moving
the view into the app target strands 19 device-free pins behind a simulator — do not. **Recommended shape:** a NEW
SwiftPM library target in `Package.swift` — `StyleConverterHarnessChrome`, path `apps/ios-harness/HarnessChrome/
Sources`, depends on `StyleConverterRuntime`, `.swiftLanguageMode(.v5)` like `Package.swift:60-66` — holding the three
files (`public` dropped where only the app needs it), plus test target `StyleConverterHarnessChromeTests` (path `apps/
ios-harness/HarnessChrome/Tests`) holding `BlockLabelTests` + `HarnessLabelChromeRasterTests` + a chrome-side copy of
the mirror chain (the runtime mirror keeps its plain chain for `BlendGroupCompositingTests`; `CaptureCanvasMirror.
swift:9-12` already demands lock-step with `CaptureCanvas.swift`). `project.yml:44-47` gains `product:
StyleConverterHarnessChrome`; `CaptureCanvas.swift:37` gains the import. Counts: swiftui 2020 → 2001 (derived by
`doc-staleness-check.sh:144` from `func test` under `runtimes/swiftui/Tests`); the 19 tests need a NEW table row +
derivation (or the swiftui row's command becomes the package scheme that runs both test targets — verify it runs
under the Catalyst destination, the only one that works locally). CI `swiftui-conformance` runs `ConformanceTests`
only → unaffected.

**Shared.** `gen-block-font.mjs` output paths `:46,:63,:83`; `block-font.json` stays (tripwire reads it at
`label-chrome-tripwire.test.mjs:44`); `docs/DYNAMIC_CAPTURE.md` §5 table drawer paths; BACKLOG 0(ab) struck;
`wave51-A/design-record.md` untouched (history). Regenerate with `node tools/visual/gen-block-font.mjs` and require a
byte-identical result at the new paths.

## 8. Pins a builder must write, and how each fails by mutation

1. `HarnessLabelChromeSourceTest.labelPath` → the harness path. Mutation: point it back at the runtime path → the
   test must ERROR on the missing file (it reads from disk), never pass vacuously.
2. NEW absence pin, one per platform suite (or one node test under `tools/visual/`): no `BlockLabel`, `BlockFont` or
   `HarnessLabelChrome` identifier under `runtimes/{compose/src/main,swiftui/Sources,web/src}`. Mutation: re-add a
   one-line stub `object BlockLabel` in the runtime → red.
3. Generated-file pin: the three `BlockFont.gen.*` exist at the harness paths and embed checksum `cb3c6e411c7b2859`
   (`BlockFontLabel.ts:3`); the existing atlas-checksum tests (`BlockFontLabel.test.ts`, `BlockLabelTest.kt`,
   `BlockLabelTests.testAtlasChecksumMatchesEmbeddedConstants`) move with their code. Mutation: edit one row int in
   a generated file → that platform's checksum pin red.
4. Tripwire unchanged, 136/136; its header already records the negative control (130/130 red on pre-chrome PNGs).
5. For 0(aa) A3/A5 only: extend the tripwire's glyph-mask clause with "on Android, the P-mask bytes of 023/024/025/093
   equal web's" — red today (the 173s), green after the fix; mutation: restore the AA paint → red again.

## 9. File ownership the lane needs

0(aa): `apps/android-harness/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`
(canvas draw node only), `test-all.sh` emulator flags (`:862-872`), and — for arm A4 only, as an unshipped mutation —
`runtimes/compose/…/effects/shadow/ShadowApplier.kt`. Seam: none shared with a renderer lane; the shadow files are
owner `android-effects-shadow` (queue text) and must not change in this lane.
0(ab): the files listed in §7, `Package.swift`, `apps/ios-harness/project.yml`, `tools/visual/gen-block-font.mjs`,
`docs/DYNAMIC_CAPTURE.md`, the five count-quoting docs, `tools/visual/doc-staleness-check.sh`. Shared seam:
`runtimes/swiftui/Tests/StyleConverterRuntimeTests/CaptureCanvasMirror.swift` (also used by
`BlendGroupCompositingTests`) — split, do not move.

## 10. "Instrument decisions pending" — current state and readiness

| item | state in tree (2026-09-25) | ready? |
|---|---|---|
| Successor to the novel-ink veto | DECIDED + EXECUTED; `tools/titan/degenerate-veto-probe.mjs` present, disarmed stamp | nothing to do |
| Blank-capture guard on UNIFORM COLOUR | OPEN. No guard in `tools/` (only comments: `inject-wpt-block.mjs:69,1748`, `novel-ink.mjs:164,226`); population `wave50-S6/blank-capture-vs-inked-ref.json` (42 rows) present; exit-7 family exists (gate-driver: "exit 7 is a missing/short column") | READY, device-free: predicate + mutation-proof on the 42 rows; a gate only observes it firing |
| Erased-reference body background (calibration) | OPEN. Patch `wave50-B10/capture-browser-ref-body-background.patch`, `census.mjs`, `zneg-ab-probe.mjs`, `canvas-css-ab.mjs` present; `capture-browser-ref.mjs:289 CANVAS_REV` unbumped | READY but gate-coupled (CANVAS_REV bump + re-freeze + Calibration-gate recipe); land it early so the closing gate adjudicates the pre-stated +5/−2 |
| Threshold study C4 half-flipped | OPEN. `cross-platform-gate.mjs:90 DEFAULT_DELTA_E_THRESHOLD = 5.0`, no dpxStrict; STATUS heading "PROPOSAL (not flipped here…)" at `docs/STATUS.md:2546` | its precondition is now MET (`wave51-open` 0/0/0/0 over 4117 cells; PR 3 `BASELINE=1 --gate-set` exit 0 ×8) → READY; fixture-net only |
| Extraction-wall re-measure | OPEN. `EXTRACTION_WALL_ADMITTED_TESTS` absent (`inject-wpt-block.mjs:1085` has only `EXTRACTION_WALL_TAGS`) | ready after the gate (needs the refs' headless Chromium); expect few admissions |
| Standing report: excluded tests disagreeing >0.1 | OPEN. No such summary line (`inject-wpt-block.mjs` "disagree" hits are comments `:254,:553,:563`) | READY, S, no scoring change |
| Compose `PropertyRegistry` theatre | DECIDED (b) + EXECUTED | nothing to do |
| Mechanisms with no production caller | partly decided; `RepeatingGradientHelper.kt`, `ViewTimelineExtractor.kt`, `MulticolLineSnap.kt`, `MulticolRunFragment*.kt`, `soleFlowFragmentReplay`, `TextStyleApplier.extractHasOverline` (`:979`), `FlexAutoMinSize.MECHANISM_ENABLED = false` (`:113`) all still present | each a Compose-only S decision; every deletion moves the compose count (doc-staleness) — batch them with 0(ab)'s count edit |

## 11. What I could not verify

- No device: H2 is untested; I did not identify the sub-pixel grid or the batching rule that makes 023/024/025/093
  differ from 089/096/098. The A/B arms are designed to discriminate, not to confirm a favourite.
- Whether the period-24 column pattern is a property of x alone or of glyph index (the two are confounded at
  advance 6 — the A3/A5 arms should also capture one stem whose name shifts the glyphs, e.g. `CAPTURE_WIDTH`
  unchanged but a renamed fixture copy, off-tree).
- The composed path: the corpus has no integer-aligned 1-px chrome to reveal a 1/256 leak; the "no shift structure"
  reading of backdrop-filter-box-shadow is a negative over text-AA noise, not a proof.
- Live vitest counts after the web move; whether an `-scheme StyleConverterRuntime-Package` run works under the
  Catalyst destination; that Skia's AA-downgrade for pixel-aligned rects behaves as H2 assumes on this HWUI build.
- Git history of the label files (git was not run against the gate tree by rule).

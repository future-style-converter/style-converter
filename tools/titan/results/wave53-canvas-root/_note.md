# wave-53 lane L3 · canvas-root — lane note

Contract: `tools/titan/results/wave53-plan/PLAN.md` "### L3 · canvas-root" (§2), briefs `contents-root-background.md`
(item A) and `display-table-body.md` (item B). Shared tree, no commits (PLAN §10 item 3). Base: campaign/applier-campaign
762d3d30 (dev cdb8a845 + the committed wave-53 plan; no runtime source differs from cdb8a845); HEAD moved to e330e255 during the
lane (the orchestrator's wave53-gate note only — `git diff --name-only 762d3d30 e330e255` touches no L3 path). Evidence run: `wave52-ship`
(per-test IR byte-identical to `wave53-open` for all four carriers — sha1 checked; the censuses below ran on both).

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf

## 1. What changed, and why (file:symbol)

### Item A — the root's background-IMAGE layers propagate to the canvas (css-backgrounds-3 §2.11.2, §3.4; css-contain-2 §2)

Mechanism confirmed in the tree as the brief traced it: all three canvases propagated `BackgroundColor` only
(web `resolveCanvasBackground`, Android `resolveComposedCanvasBackground`, iOS `ComposedCaptureCanvas.canvasBackground`);
the body-root is a sibling of the flow content, so nobody painted the image. Looked at the PNGs first: the three captures
are white where the ref is (0,128,0); margin-root-001/-002 paint the gradients only in the 258×300 root box at (66,66).

One pure rule, three twins (new files, ≤ 223 lines each):
- `runtimes/web/src/engine/background/RootBackgroundPropagation.ts` — `rootBackgroundPlan`, `dataPngIs1x1`,
  `rootCanvasBackgroundStyle`, `withRootBackgroundStripped`.
- `runtimes/compose/src/main/java/com/styleconverter/runtime/background/RootBackgroundPropagation.kt` — `plan`,
  `dataPngIs1x1`, `withCanvasOwnedRootBackground`, `layerConfigs`, `canvasModifier`.
- `runtimes/swiftui/Sources/StyleConverterRuntime/StyleEngine/background/RootBackgroundPropagation.swift` —
  `RootBackgroundPropagation.plan / dataPngIs1x1 / withCanvasOwnedRootBackground` + the public `RootCanvasBackground` view.

The rule (identical decisions on all three, pinned on the same verbatim payloads):
- **Gate**: no body-root / no image layer / only `none` layers / `contained` (the caller's EXISTING containment gate —
  web `bodyRootHasContainment`, Compose `containmentBlocksCanvasPropagation(containKeywords(…))`, Swift
  `WPTCanvas.containmentBlocksPropagation(containKeywords(of:))`) → no plan; the natives also refuse a layer their
  extractor would drop (index parity, breadcrumbed).
- **F1 origins** per layer: `fixed` → the ICB corner (0,0); `scroll`/`local` → the root box = ICB + the canvas-owned margin
  (`resolveCanvasMargin` / `resolveComposedCanvasMargin` / `UABlockMargin.canvasBodyMargin`). Attachment is never passed
  through: web writes `background-attachment: scroll` on every layer and realises §3.4 through `background-position`.
- **F2 surface**: uniform-by-construction (every layer a single-sRGBA gradient or a `data:image/png` whose IHDR is 1×1, and
  every `BackgroundRepeat` entry `repeat` on both axes) → the framed outer surface; otherwise the ICB only, frame left at the
  colour-layer value. Cited in code as capture-frame chrome mirroring `capture-browser-ref.mjs:728 padColorFor`.
- **Strip** (§2.11.2 "not painted again"): the body-root node loses `BackgroundImage/Size/Repeat/Attachment/Position*/
  Origin/Clip/BlendMode` (never `BackgroundColor` — colour handling untouched).
- Knobs with no corpus carrier (author `background-position*`, `-origin`, `-clip`, `-blend-mode` on a propagated root) are
  breadcrumbed through each platform's PropertyTracker, not composed.

Call sites (oversized files get call sites only):
- `apps/web-harness/src/ui/ComposedCaptureGallery.tsx`: `resolveCanvasRootBackground(doc)` + `withCanvasOwnedRootBackground`
  (thin wrappers beside the existing resolvers); `ComposedTestCanvas` paints framed plans on the outer div (shorthand
  `background` set `undefined`, colour moved to `backgroundColor` — no shorthand/longhand mix) and ICB plans (and every plan
  under a root clip) on the ICB div; both spreads are LAST and conditional, so plan-less style objects are unchanged.
- `apps/android-harness/.../ScreenshotCaptureScreen.kt`: `resolveComposedCanvasRootBackground(roots)` beside the colour
  resolver; `rootImagePlan` + `rootImageModifier` (computed from the UNSTRIPPED roots) in `ComposedCaptureCanvas`;
  `.then(rootImageModifier)` right after the colour chain (identity `Modifier` without a plan); the strip rides the new
  `composedCanvasRoots(...)`, called AFTER the verbatim `withCanvasOwnedBodyMargin(roots, canvasMargin).map(::withUaBlockMarginOnHoistedRoot)`
  (kept as a literal because the NOT-owned `ComposedCanvasRc1ZOrderSourceTest.t6Rewrite_isWiredIntoTheComposedRoots` pins it;
  the two rewrites commute — they touch only the body-root and synthetic/in-flow roots, T6 only hoisted roots).
  Android paints the layers on the framed surface with origins offset by the frame and, for a non-uniform stack, repaints
  the 16-px frame band in the colour above them (`canvasModifier`): the instrument's own picture (padPngBuffer fills the
  frame with the pad colour); the tile planner box is the framed surface, which only matters for space/round/% knobs
  (breadcrumbed, no carrier).
- `apps/ios-harness/.../CaptureCanvas.swift`: `rootImagePlan` / `rootImageBackground`; `.background(rootImageBackground)`
  between the inline-axis clip and `.background(canvasBackground)` (above the colour, below every root, outside the band
  clip, inside a root clip); the strip in `splitRoots` as the first argument of `withCanvasOwnedBodyMargin`.

**Where the tree won over the plan / brief:**
- `ColorApplier.kt` and `BackgroundImageApplier.swift` are NOT edited (§9 D5 is unnecessary): each layer is painted as a
  single-layer config through the EXISTING appliers (`ColorApplier.applyColors` per `layerConfigs` entry; Swift
  `engineBackgroundImage` called from inside the runtime module, where it is visible), with the §3.4 origin as that
  layer's position offset. Fewer hot-file edits, every existing call byte-identical by construction.
- `WptCaptureMode.kt`, `WPTCaptureMode.swift`, `ComposedRootStack.swift`, `WptCanvasBackgroundTest.kt`,
  `ComposedRootStackTests.swift` are NOT edited: the containment gate is reused by passing the existing harness decision in
  as `contained`; the Swift strip lives in the runtime's RootBackgroundPropagation.swift (same-module memberwise init), and
  the Compose/iOS pins live in `ComposedCanvasRootBackgroundTest.kt` / `WPTCaptureModeTests.swift`.
- F2 for a NON-uniform stack keeps the colour-layer frame (the plan's rule). `padColorFor` would fall back to white for a
  non-uniform ring; the two agree on every carrier (none pairs a root colour with a non-uniform image). Recorded, no carrier.
- Not in scope (recorded, not fixed, per the plan): the natives' root-`contents` self-strip stays spec-divergent
  (css-display-3 §2.7); no carrier needs it.

### Item B — a `display: table` body forms a table on the canvas (CSS 2.1 §17.2.1 rule 2, §8.3, §4.2)

Mechanism confirmed: 006's body children are sibling roots (no body height → no BODY-HEIGHT SLOTTING), so `Display TABLE`
applies to an empty box. Looked at the PNGs: ref square rows 56-75; web 66-85; natives 51-70 (`display-table-body.geometry.py
wave52-ship 006`).
- `runtimes/compose/.../table/TableBodyForest.kt` and `runtimes/swiftui/.../StyleEngine/table/TableBodyForest.swift`
  (twins, 115/119 lines): `rewrite(roots)` — gated on the body-root's LAST `Display` ∈ {TABLE, INLINE_TABLE}; the in-flow
  run after the body-root → one synthetic `TABLE` (`<body id>#table`, carrying `BorderSpacing`/`BorderCollapse`) → one
  `TABLE_ROW` → cells (§17.2.1 rule 2: consecutive non-cells share one anonymous `TABLE_CELL` `#cell<k>`; cell roots pass
  through minus `Margin*` per §8.3). Out-of-flow roots stay roots after the table (Compose `CanvasRootHoist.shouldHoistToCanvasRoot
  || rendersInFlowAsStaticPosition`, Swift `ComponentRenderer.isOutOfFlow`). Identity (same instance / same array) when no
  trigger, when the run holds a proper non-cell table child, when the run is empty, or when the body declares a non-zero
  padding or border width.
- Call sites: Android `composedCanvasRoots` (`return com.styleconverter.runtime.table.TableBodyForest.rewrite(owned)`);
  iOS `splitRoots` (`TableBodyForest.rewrite(` around the item-A strip, the input of `FixedHoist.split`).
- Web: `apps/web-harness/src/ui/CanvasTableBody.ts` `resolveCanvasTableBody(doc)` → `{display: 'table'|'inline-table',
  borderSpacing?}`; the gallery's `data-capture-flow` wrapper takes that `display` and `border-spacing` and is emitted even
  at zero margin. Chrome then runs the fixup. Tree finding: the web runtime's phase-10 table serializer DROPS the
  `{type:'single', px:N}` BorderSpacing leaf (`_phase10_shared.keywordOrRaw` has no length arm; `buildStyles` emits no
  `borderSpacing` for 006), so CanvasTableBody reads the raw leaf (`single` / `two-values`, px only; anything else
  breadcrumbed). The runtime gap itself is NOT widened or fixed here — hand-off below.

## 2. Census (blast radius) — the lane's CARRIER SET

Method: executed on the edited tree, not inferred.
- **Web** — `web-markup-census.sh HEAD <run>` renders every per-test IR document through the tree's gallery and through
  `git show HEAD:` of it (imports re-pointed) and lists the documents whose static markup differs
  (`web-markup-census.wave52-ship.txt`, `…wave53-open.txt`): **1435 rendered, 4 differ** — `background-attachment-margin-root-001`, `-002`,
  `display-contents-root-background`, `CSS2/css21-errata/s-11-1-1b-006`; same 4 on `wave53-open`. (Item A alone, before B:
  the 3 A carriers.)
- **Compose (JVM)** — a temporary census test (run, then removed; the test file restored byte-exact, sha256 d42ce789…)
  over both runs through the real `resolveComposedCanvasRootBackground` + `composedCanvasRoots` chain
  (`native-census.compose.txt`): **1435 decoded, 0 errors, 3 root-image plans (001/002 uniform=false, target uniform=true),
  4 forests changed** (the same 4). Every other document gets `then(Modifier)` (identity) and an equal forest.
- **SwiftUI (Catalyst)** — the same census through `RootBackgroundPropagation.plan` + `TableBodyForest.rewrite` on the
  split input (temporary test, file restored byte-exact, sha256 37c6b8b8…) (`native-census.swiftui.txt`): **1435, 0 errors,
  3 plans, 4 forests changed**, identical sets. Plan-less documents get an empty `Optional` background, pinned paint-neutral
  (`testRootBgA5…`: identical bitmap bytes with/without the slot).
- **Cross-check with `expectations.json` `lanes.L3-canvas-root.captureCarriers`**: identical (4 documents × 3 platforms =
  12 captures; `wireCarriers` = [] — no extractor/converter change). Carrier cells passing today: item A 0 of 9 (all f);
  item B 3 of 3 (all P, all wrong pictures).
- Outside the 4 documents every capture must be byte-identical (86 colour-only and 195 background-less body-root documents,
  `s-11-1-1b-005`'s TABLE_CELL body among them, and the 1151 documents with no body-root).

## 3. Pins and EXECUTED mutations (red → restore byte-exact → green)

Every payload is verbatim from `tools/titan/runs/wave52-ship/sections/<section>/per-test-ir/` (embedded as constants; the
runs dir is gitignored). Runner: `mutate.py <batch>` with `mutations.json` (exact-string edit asserted to match once, the
named test(s) must report failing, the file restored from its original bytes and its sha256 re-checked, then the batch is
re-run green). Full log of the FINAL run on the final sources: `mutations.log` — **36 mutations, 36 red, 36 restored
byte-exact, 0 skipped, all 7 batches GREEN-AFTER-RESTORE** (an earlier run on the pre-comment-pass sources is kept in
`mutations.pre-final.log`). Final sha256 (first 16 hex) of every L3 path: `owned-shas.txt`. Summary:

| batch | file mutated (sha256 before = after) | pins | mutations → red |
|---|---|---|---|
| web-runtime | `runtimes/web/src/engine/background/RootBackgroundPropagation.ts` | `runtimes/web/tests/background/RootBackgroundPropagation.test.ts` (8) | RW1 uniform:true → a2_marginRoot001; RW2 attachment ignored → a2_marginRoot002; RW3 IHDR any → a3; RW4 gate dropped → a4 |
| web-gallery | `apps/web-harness/src/ui/ComposedCaptureGallery.tsx` | `apps/web-harness/tests/ui/ComposedCanvasRootBackgroundImage.test.tsx` (6) | GW1 colour-only read → target_framed…; GW2 strip dropped → both *_bodyRootLosesImage; GW3 every stack framed → marginRoot001_icbOnly; GW4 containment dropped → contained_…; GW5 always `backgroundImage:'none'` → colourOnly_… |
| web-B | gallery | `apps/web-harness/tests/ui/ComposedCanvasTableBody.test.tsx` (3) | BW1 table branch removed → both s006_* |
| compose-A | `ScreenshotCaptureScreen.kt` (CA1), runtime `RootBackgroundPropagation.kt` (CA2-6) | `ComposedCanvasRootBackgroundTest.kt` (8) | CA1 resolver null → a1_target; CA2 strip → a1_strip (through the REAL `composedCanvasRoots`); CA3 uniform → a2_001; CA4 fixed→SCROLL → a2_002; CA5 IHDR any → a3; CA6 gate → a4 |
| compose-B | runtime `TableBodyForest.kt` (BS, BM1-5), `ScreenshotCaptureScreen.kt` (BK) | `TableBodyForestTest.kt` (6), `ComposedCanvasTableBodyTest.kt` (1) | BS re-parent the run under the body-root → shape; BM1 trigger dropped → m1; BM2 §8.3 strip skipped → m2; BM3 out-of-flow filter dropped → m3; BM4 div straight into the row → m4; BM5 BorderSpacing not copied → m5; BK `return owned` → stack (the 10-px gap is back) |
| swift-A | `RootBackgroundPropagation.swift` | `WPTCaptureModeTests.swift` testRootBg* (8, incl. 3 ImageRenderer pixel passes) | SA1 plan nil → A1 plan+strip and A1 pixels; SA2 strip → A1; SA3 uniform → A2-001 plan+pixels; SA4 fixed→scroll → A2-002 pixels; SA5 IHDR any → A3; SA6 gate → A4 |
| swift-B | `TableBodyForest.swift` | `TableBodyForestTests.swift` (8, incl. the raster pin) | BS → shape; BM1 → m1; BM2 → m2; BM3 → m3; BM4 → m4; BM5 → m5; BK rewrite short-circuited → stack + raster |

Notes on the pins:
- **B-M1 (plan) could not fail on 005 and 001 alone** — executed reasoning, then the mutation: with the trigger dropped,
  005 is still identity (its only other box is slotted under the body, so the run is empty) and 001 has no body-root. The
  pin therefore also carries the verbatim `css-color/a98rgb-003` (a colour body followed by in-flow roots), which turns
  BM1 red on both twins.
- **iOS pixel passes (Catalyst ImageRenderer)**: `RootCanvasBackground` mounted exactly as the canvas mounts it (390×600,
  behind the content, colour behind it): target → every frame pixel and the centre (0,128,0); 001 → 0 non-white frame
  pixels, band starts [66,166,266,366,466,566] (= the ref); 002 → band starts [116…516] with green at y16 (= the ref's
  phase 16). These are the native picture, not just the plan.
- **iOS item-B raster pin** (`testRasterSquareAtTheReferenceRows`): the rewritten flow mounted as `ComposedCaptureCanvas`
  mounts it (frame 16 + body margin 8/40, 342-px fill channel, wptCaptureMode, ComponentHost + the stack fold) reads
  **square rows 56-75 x 24-43** (= the reference). With the rewrite short-circuited the SAME mount reads **51-70 x 24-43**
  — exactly the wave52-ship ios device capture — so the emulation reproduces the device on both sides (mutations.log
  `BK-probe-readout`).
- Android cannot draw a Compose chain on the JVM (no Robolectric); the item-A Android pins are the plan, the per-layer
  configs the existing ColorApplier paints (offsets 66/16 for 001), the strip through the real `composedCanvasRoots`, and a
  source pin of the call-site placement (`.then(rootImageModifier)` after `?: Modifier.background(canvasBackground)`, before
  `composedIcbClipBandPx(`). On the JVM the url layer's synchronous BitmapFactory decode is stubbed, so ColorApplier skips
  it there (measured: the target's `canvasModifier` is `Modifier` on the JVM); on device the same layer SHAPE decodes —
  **corrected citation (fix pass, L3 skeptic should-fix 3):** `css-break/background-image-000/-001/-002` android P 1
  (`cells.mjs`, wave52-ship and wave53-open), plain `{url: data:image/png…}` layers through ColorApplier, the target's own
  path. The earlier citation, `css-image-fallbacks-and-annotations002` android P 0.9982, is the `image()` notation
  (`ImageNotation`), a different path.

Focused suites, all green on the final tree:
- web-harness `npx vitest run tests/ui/ComposedCanvas tests/ui/ComposedCaptureGallery.test.tsx` — 11 files, 78 tests;
  web runtime `npx vitest run tests/background` — 4 files, 89 tests; `tsc --noEmit` clean on runtimes/web and apps/web-harness.
- Compose `:runtime:testDebugUnitTest --tests '*WptCanvas*' '*TableBodyForest*' '*ColorApplier*' '*SeamReachability*'
  '*TableBoxTree*'` + `:app:testDebugUnitTest --tests '*ComposedCanvas*' '*UaBlockMargins*'` — 175 tests, 0 failures
  (SeamReachabilityTest included: the new runtime modules are reachable from the harness).
- Catalyst `WPTCaptureModeTests`, `ComposedRootStackTests`, `TableBodyForestTests`, `BackgroundImageURLTests` — 101 tests,
  0 failures (own `-derivedDataPath` under the session scratchpad, so no DerivedData contention with other lanes).
- `node tools/titan/results/wave52-composed-canvas/ios-source-pins.mjs` (no `--record`) on the edited CaptureCanvas.swift:
  **pins=7/7 GREEN**, IOS-M1…M7 all RED.

## 4. Predictions (against `wave53-open`; replays scored with the scorer's ssim.js `{ssim:'fast'}`)

| cell | prediction | confidence | basis |
|---|---|---|---|
| `css-display/display-contents-root-background` web | f 0.534 → **P ≥ 0.99**, `GEOMETRY OK` | HIGH | brief replay 1.0000; web markup pinned |
| … ios | f 0.5346 → **P ≥ 0.99**, `GEOMETRY OK` | HIGH | replay 0.9995; Catalyst pixel pass: whole frame (0,128,0) |
| … android | f 0.5355 → **P ≥ 0.99**, `GEOMETRY OK` | HIGH | replay 0.9991; url layer via the shipped ColorApplier dense-shader path (device-only) |
| `css-backgrounds/background-attachment-margin-root-001` web / ios / android | f → **P** (≈0.9996 / 0.9992 / 0.9997) | MED | brief replays; ios pixel pass matches the ref band phase + white frame; android configs pinned |
| `…-002` web | f 0.339 → **P** | MED | per-layer `background-position` pinned (0,0 / 50,50) |
| `…-002` ios | f 0.3391 → **P** | MED (plan: MED-LOW) | the native `fixed` translation is now pixel-pinned on Catalyst (band phase 16) |
| `…-002` android | f 0.3389 → **P** | MED-LOW (unchanged) | config offsets pinned; the frame overpaint and the tile draw are device-only |
| `CSS2/css21-errata/s-11-1-1b-006` web | stays P, **0.9941 → ≈1.0000**, square 56-75 x 24-43 | HIGH geometry / HIGH score | Chrome does the fixup; replay of the shipped capture with the square at y56: 1.0000 (`s006.replay.out.txt`) |
| … ios | stays P, **0.9953 → ≈0.9992**, square 56-75 | MED-HIGH geometry (plan: MED) / MED score | Catalyst raster pin reproduces both the device's 51-70 (rewrite off) and the ref's 56-75 (on); replay 0.9992 |
| … android | **B-android predicted to FAIL its probe line** (no square, or a squeezed one); score ≈0.9928 if the square vanishes (stays P, Δ −0.0016) | LOW geometry (plan: MED) | traced below; the probe-gated revert of the B-android commit is the expected outcome |

**Why B-android is expected to fail (traced, not run):** the anonymous cell is a `TABLE_CELL` with no declared width, so
`ComponentRenderer.kt`'s composed-WPT `blockFlowWidth` (≈:1553-1572; `isShrinkToFitTable` is true only for the TABLE role,
`TableBoxTree.shrinkToFitBox`) gives it `fillMaxWidth()`. `TableApplier.TableRow` is a `Row` whose width is the table's
max-intrinsic width (0 + 20 = 20, `widthAtMaxIntrinsic`), and a Row measures non-weighted children sequentially against
the remaining width: the anonymous cell takes all 20 and the td is measured at max width 0. Corroborated on a shipped
capture (PNG looked at): `css-tables/baseline-empty-cell-001` android — the first auto cell takes the whole table width and
the second cell collapses. The fix is a Compose seam change (a table cell must not take the composed-WPT block fill —
§17.5.2 content-based column widths), out of this lane's ownership; recommended as a wave-54 seam item. The orchestrator
may land B-android and let the probe revert it (as pre-declared), or hold it — both leave the empty lost list intact.

Geometry probes the closing gate reads (unchanged, committed in wave53-plan/): `canvas-root.geometry.py <run>` must print
`→ GEOMETRY OK` on the nine item-A rows; `display-table-body.geometry.py <run> 006` must print
`square rows 56-75 (20) x 24-43 | red px 0` for web (gating) and ios; android predicted not to (see above). On wave52-ship
all nine A rows print GEOMETRY WRONG and 006 prints 66-85 / 51-70 / 51-70 (re-run here).

## 5. Must-not-move cells (78, `watchlist.txt` L3 block; all ×3)

`css-contain/contain-{body,html}-bg-001…004`, `css-cascade/initial-background-color`, `css-color/a98rgb-003`,
`css-masking/clip-path/clip-path-document-element{,-will-change}`, `filter-effects/backdrop-filter-root-element`,
`css-display/display-contents-text-only-001`, `css-images/css-image-fallbacks-and-annotations002/003`,
`CSS2/css21-errata/s-11-1-1b-001…005`, `-007…009`, `css-position/position-absolute-dynamic-static-position-table-cell`,
`css-tables/baseline-vertical`. All are outside the executed census's 4 documents on all three platforms, so their markup /
forest / modifiers are unchanged by construction; the colour-only, contained and root-clip bodies are additionally pinned
directly (web `colourOnly_noImageKeyAnywhere` over initial-background-color, a98rgb-003, contain-body-bg-001 and
clip-path-document-element; Compose `a5_colourOnlyBodies_identity`; Swift `testRootBgA5ColourOnlyBodiesAreIdentity`).
The ring-fenced `filter-effects/backdrop-filter-basic-blur` is report-only and outside the census set (no body-root image,
not a table body).

## 6. Hand-offs

- **Seam patches: none** (as registered in §3 for L3). No seam file was read-modified.
- **Hunks for other lanes: none.**
- **Revert units** (PLAN §4 step 6): `make-units.py` writes `unit-A.patch` (10 paths), `unit-B-web.patch` (3),
  `unit-B-ios.patch` (3), `unit-B-android.patch` (4) (header names the base; apply in that order on HEAD's — = 762d3d30's —
  bytes of these paths). Executed: "replay: 4 patches on HEAD bytes == tree for all 17 paths (sha256)" and "B-web /
  B-ios / B-android each reverse-apply alone on the final state". It derives each
  shared file's A-only text by removing the B hunks, replays all four patches on HEAD's bytes and checks the result is
  byte-identical to the tree (sha256, every path), and checks each B patch reverse-applies alone on the final state.
  Unit A names no item-B symbol (asserted). Paths per unit are listed at the top of make-units.py.
- **Web runtime gap found (not this lane's file):** `runtimes/web/src/engine/table/BorderSpacingExtractor.ts` →
  `_phase10_shared.keywordOrRaw` returns undefined for the converter's `{type:'single', px:N}` / `{type:'two-values', …}`
  BorderSpacing leaves, so no web table ever gets its declared `border-spacing` through `buildStyles` (33 corpus leaves).
  Queue candidate; measure its carriers before touching it (it can move css-tables cells).
- **Compose table-cell fill (B-android's blocker):** see §4. Seam work (ComponentRenderer.kt), wave-54.
- **Brief §10 neighbour finding (recorded, not fixed):** 22 of the 24 passing `s-11-1-1b-00x` cells (001-004, 006-009)
  do not match their reference — the natives do not clip at a table box's `overflow` edge (001/002/008/009 show red), keep
  5 rows of the square on 003/004, and web draws the square 2 px right in the wrong rows. Book as DEGENERATE in 0(b)/0(l″);
  wave-54 brief "table overflow clip / caption". This lane moves none of them (outside the census set).

## ORCHESTRATOR WINDOW REQUESTS

Nothing this lane needs BEFORE landing requires a device. At integration / probe, per PLAN §4 and §6:
1. ios-harness XCTest scheme on the integrated L5 + L3 tree (needs L5's project.yml and a booted simulator), expect 30/30:
   `(cd apps/ios-harness && xcodegen generate && xcodebuild test -project StyleConverterTest.xcodeproj -scheme StyleConverterTestTests -destination 'platform=iOS Simulator,name=<a booted device>')`.
   Look for: ComposedCanvasIcbClipTests 7/7 (this lane's replay is 7/7 on the edited CaptureCanvas.swift).
2. After `wave53-probe`: `python3 tools/titan/results/wave53-plan/canvas-root.geometry.py wave53-probe` → `GEOMETRY OK` on
   the nine item-A rows (exit 0); `python3 tools/titan/results/wave53-plan/display-table-body.geometry.py wave53-probe 006` →
   the 006 string on web (gating) and ios; android predicted NOT to print it → revert the B-android commit (rule 4).
3. The R4 control with this lane's carrier set (12 captures; `expectations.json` captureCarriers) — every other composed
   capture identical by decoded pixels.

## 7. What I could NOT verify

- No device / Chromium / emulator / simulator run (lane rules): every capture-level prediction above is a prediction.
- Android item A and item B layout are unexecuted on the JVM (no Compose UI test infrastructure): the drawn picture of
  `canvasModifier` (url dense-shader path, gradient tiles at the 66/16 offsets, frame-band overpaint) and the synthetic
  table's layout are device-only. B-android is predicted to fail (§4).
- iOS item A is pixel-pinned on Catalyst for the background view in isolation, not through the whole ComposedCaptureCanvas
  (the harness target is not part of the runtime test bundle); iOS item B's raster pin is an emulation of the canvas mount
  (validated against the device's shipped geometry, but not the device itself).
- Web markup is pinned with `renderToStaticMarkup` outside `?wpt=1` mode (the harness's WPT_MODE min-size suppression is
  off there); the census compares like with like, and the asserted keys (canvas/ICB/wrapper styles) are WPT_MODE-independent.
- The full suites were not run (single-writer rule); the orchestrator's sweep owns them.

STATUS: COMPLETE

## Fix pass (wave-53 S1, 2026-10-07; full record in `tools/titan/results/wave53-S1/_note.md` "## Fix pass")

- **S1 M1 — unit A is not independently revertible; an A-only revert is now PREPARED.** `a-only-revert.patch` (this
  directory; header = provenance, base sha256 per path, the export verification) removes item A on all three platforms
  and keeps B-web / B-ios / B-android on the unstripped forest. Verified on exports: web-harness 324/324, web runtime
  1361/1361, both `tsc` 0, compose 3434/3434, android-harness 160/160 (B-android's stack pin red under BK), Catalyst
  2184/2184; patch-on-the-fixed-tree equals the verified tree byte for byte (tracked files). PLAN §10 item 8 and
  `expectations.json` `revertUnits.A` (`revertOrder` B-android → B-ios → B-web → A, `aOnlyRevert`).
- **Skeptic should-fix 2 (XI1) — the iOS call sites are pinned.** `ios-callsite-pins.mjs` (no device): five pins on
  `CaptureCanvas.swift` (image layer between the ICB clip and the colour, before the root clip; the split strips the image
  then builds the table forest; the plan reads the canvas-owned margin and the colour's containment gate; the paint reads
  the unstripped body at the frame), six in-memory mutations XI1-XI6 all RED (`ios-callsite-pins.out.txt`).
  `../wave52-composed-canvas/ios-source-pins.mjs` stays 7/7.
- **Skeptic should-fix 1 (XC1-3) — the Android paint composition is pure and pinned.** `RootBackgroundPropagation.kt`
  `canvasPaintPlan(props, plan, frame)` → `CanvasPaint(bottomUp, overpaintFrame)`; `canvasModifier` consumes it (same
  order, base, overpaint). Pin **a7** in ComposedCanvasRootBackgroundTest on verbatim 001: bottom-up [(16,16) black,
  (66,66) ramp], overpaint true; the target overpaint false; a source pin that `canvasModifier` spends the plan. XC1, XC2,
  XC3 and XC9 (the modifier ignoring the flag) → red.
- **Skeptic should-fix 3 — citation corrected** (§ "Android cannot draw…" above): `css-break/background-image-000/-001/-002`
  android P 1.
- **S1 S7 — size.** The F2 uniformity predicates moved verbatim to `RootBackgroundUniformity.{kt,swift,ts}`:
  RootBackgroundPropagation.kt 223 → 188 (+ 75), .swift 218 → 161 (+ 84), .ts 211 → 138 (+ 91). Replayed: web RW1/RW3,
  Swift SA5 and a stack-uniformity mutation all red. Remaining exceptions (call sites in already-oversized files, recorded
  for the PR): ComposedCaptureGallery.tsx +95, ScreenshotCaptureScreen.kt +72, CaptureCanvas.swift +42 (each holds the
  canvas-local helpers A/B need: the plan resolver, the forest strip, the paint view); ComposedCanvasRootBackgroundTest.kt
  250 lines.
- **Nit 8 — "the engine's own" ×4 → "the runtime's own".**
- TREES (fix pass): Gradle ran only in exports under the session scratchpad (`--no-daemon`), never in this tree.

STATUS: COMPLETE

## Probe verdict (orchestrator, 2026-10-07) — A, B-web, B-ios KEPT; B-android REVERTED (as pre-declared)

- **A**: display-contents-root-background web/ios/android f 0.53 → **P 1 / 0.9995 / 0.9989**; background-attachment-margin-root-001
  and -002 f 0.45 / 0.34 → P ≥ 0.9994 on all three; canvas-root geometry 12 / 12 OK.
- **B-web / B-ios**: s-11-1-1b-006 web P 0.9941 → **1** (square y56-75), ios 0.9953 → **0.9992** (square y56-75); geometry OK.
- **B-android reverted** (`d773ff6a` reverts `276757ea`) — the lane's own probeGatedRevert row: 006 android square
  `x 24-63` vs ref `x 24-43` (40 px wide) and 0.9944 → 0.9906 (rule 2 too). The picture: an extra EMPTY outlined cell to the
  left of the black square — the Compose anonymous table forest draws a second cell. The web and iOS halves of the same forest
  are right, so the defect is in the Compose fixup's cell emission for the display: table body (queued with the picture).
- A's revert order (B-android → B-ios → B-web → A; `a-only-revert.patch`) was not needed.


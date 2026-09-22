# Wave 51, PR 3 — harness debug-label chrome drawn OUTSIDE the paint chain (decided-PR (A))

Design record, reconstructed into the repo on 2026-09-22 from the three
review workflows' journals after the session scratchpad that held the full
v1/v2/v3 drafts was purged by the OS (the standing constraint "evidence
pointers are repo paths, never a session scratchpad" applies to design
documents too — this file is the fix). The implementation it describes is
in the tree (commit `WIP (unreviewed): harness label chrome …`); the lane
reports with their executed mutation records are in `lane-reports.json`
beside this file.

## 1. Problem (BACKLOG `## Decided, and still unstarted`, item (A))

The shared spec promises byte-identical label rects at (8,6), but each
platform composited the label INSIDE the styled component, so the element's
own blend / opacity / clip / transform / filter / `align-items` /
`text-align` moved or recoloured it and the three platforms disagreed:
iOS drew it inside the `.blendMode` group (blend-isolation `003_wrapper`
0.9492, exit 4 at wave 50); Android placed it in the content slot under
the Box's `contentAlignment` (Layout_C01_AlignContent bottom-right,
Layout_TextBlock centred — the 9(g) placement bug, fixed separately in PR 2);
web's zero-footprint absolute svg spilled past width-less padded boxes that
the natives clip (14 WEB-LABEL-SPILL fidelity rows, ≥ 30 label-moved rows in
`retro-2026-09-04/fidelity-classified.csv` once the platform-asymmetric
`label_ink` rows are counted). Two further mechanisms measured during the
review: (iv) Android CLIPS the spilled run at the box edge under
filter/mix-blend where web and iOS paint it (033/034/088: 0 non-ground px
at x ≥ 66 on Android vs 196/175/194 on web and iOS); (v) Android truncates
the label at the CONTENT width where web/iOS use the border-box width
(099_Edge_NegativeOffset: 9 glyphs to x=81 vs 14 to x=111). Every one of
the 390 committed baselines (130 stems × 3 platforms; 109 visual-test +
9 aspect-ratio + 8 filter-sepia-amounts + 4 filter-grayscale-basis, all
childless and textless) carries a label, so any origin change moves all
390.

## 2. Design principle (confirmed by three review rounds)

- The label is HARNESS CHROME: drawn by the capture app AFTER the element's
  whole paint chain, as a SIBLING layer over the capture root — never a
  descendant of the styled element, never inside any runtime modifier or
  CSS box. The runtimes render no label.
- Origin: literal (8,6) in the CAPTURE FRAME (PNG pixel space). Glyph rows
  6..12 (cell 5×7, advance 6, line height 10 — `tools/visual/block-font.json`)
  lie in the 16 px top pad band over `#1A1A2E`, so the ink never overlaps
  the border box of an in-flow root with non-negative `margin-top` and
  non-negative relative `top`; penumbra, transforms, outlines and negative
  margins that paint into rows 0..15 get the chrome composited over them.
- Truncation against the FRAME width: largest n with 8 + 6n ≤ frameWidth − 8
  → 62 glyphs at 390, 39 at `CAPTURE_WIDTH=250`, 0 below 22 (logged once,
  never silent). The per-component `componentWidth` channel is deleted on
  all three — it closes mechanisms (iii), (iv), (v) in one move.
- Text: the PLAIN component name, `_`→space, through the existing
  `normalize` (upper-case, atlas-unknown → `-`) on all three. Android used
  to run `placeholderDisplayText` (text-transform + tab-size) on the label;
  it no longer does. The only text-transform stem in the four baselined
  fixtures, 065_Typography_Uppercase, is glyph-neutral (normalize
  upper-cases) → zero extra movers from text parity.
- Colour: rgba(237,237,237, 179/255) on the natives → (174,174,180) over
  the ground; web used α 0.7 → (173,173,179), one LSB darker (measured on
  every unfiltered spill: 107 ×257 px, 108 ×281, 009 ×48). Web moves to
  179/255 so the composited byte is identical on all three.
- Exactly one label per capture, iff the COMPOSED root of that capture
  (post `composeTree` / `SlotComposer.compose` / IRComposer) has zero
  composed children and no non-empty text, and the run is not
  WPT/inbox/composed. The v2 IR has no `children` field on IRComponent —
  web tests the ComposedNode's children, iOS/Android the composed root's.
  Gated by the SAME flags that suppress the label today: web `WPT_MODE`,
  iOS `\.wptCaptureMode`, Android `LocalWptCaptureMode` — so the TITAN
  corpus stays label-free (§6).
- Spec home: `docs/DYNAMIC_CAPTURE.md` section "Harness label chrome"
  (numbered `## 5.` in the file's voice); the three implementations point
  at it.

## 3. Per-platform patch plan — as implemented (lane reports in `lane-reports.json`)

### 3.1 iOS
- `Renderer/BlockLabel.swift`: `BlockLabelLayout` / `BlockLabel` public with
  an EXPLICIT `public init(label:componentWidth:)` (Swift synthesises no
  public memberwise init); header on the capture-frame rule; zero-glyph
  case logged once via PropertyTracker.
- NEW `Renderer/HarnessLabelChrome.swift` (public view in the runtime so the
  Catalyst target can compose it): reads `@Environment(\.wptCaptureMode)`
  and `@Environment(\.backdropPass)` itself; `showsLabel` = !wpt && pass !=
  .sampling && composed root childless && textless (exposed as a pure static
  for pinning); `.sampling` is labelled defence-in-depth — both two-pass
  callers already run under `wptCaptureMode == true`, the bundled baseline
  path is single-pass.
- `apps/ios-harness/…/CaptureCanvas.swift`: one
  `.overlay(alignment: .topLeading) { HarnessLabelChrome(component:, frameWidth: CaptureCanvas.width) }`
  per root branch, immediately after `.background(canvasBackground)`
  (out-of-flow: after `.clipped()`). Everything above the insert point is
  paint-neutral (environment writes, DynamicCaptureHooks, the backdrop
  coordinate space); every runtime modifier sits inside `ComponentHost`.
- `Renderer/ComponentRenderer.swift` nameless-leaf branch renders
  `EmptyView()` (usedWidth + BlockLabel removed; branch kept so real-text
  leaves reach PlaceholderLabel). It cannot collapse the styled box: every
  `contentOrPlaceholder` call site is inside a container that StyleBuilder
  frames with the 50×30 min floor.

### 3.2 Android
- `core/renderer/ComponentRenderer.kt`: the block-font label branch is a
  bare `if (!hasRawText) return` (WPT gate, placeholderDisplayText,
  applyTabSize untouched for real `_text`).
- `core/renderer/BlockLabel.kt`: `BlockLabelPlaceholder` deleted; `object
  BlockLabel` public (the harness is Gradle module `:app`);
  `BlockFont.gen.kt` stays internal; `truncatedCount(frameWidthPx)`.
- `apps/android-harness/…/ScreenshotCaptureScreen.kt`, per-component
  `CaptureCanvas` only: pure `internal fun harnessLabelRects(name,
  frameWidthPx)`; `showsLabel = !LocalWptCaptureMode.current &&
  component.children.isNullOrEmpty() && component._text.isNullOrEmpty()`
  (the SlotComposer root); `labelRects = remember(name, widthPx)` with one
  `Log.w` when the frame is < 22 px; a `.drawWithContent { drawContent();
  … drawRect(BlockLabel.COLOR, Offset(x,y), Size(1f,1f)) }` node BETWEEN
  `.onGloballyPositioned` and `.padding(CaptureCanvasPadding)` — that
  node's coordinate space IS the PixelCopy rect; plus a runtime `Log.w` if
  the measured width differs from `widthPx` (px == dp only at density 160).
  `ComposedCaptureCanvas` untouched.

### 3.3 Web
- NEW `apps/web-harness/src/ui/LabelChrome.tsx`: `<svg data-label-chrome
  role="img" aria-label=… shapeRendering="crispEdges" fill={BLOCK_LABEL_FILL}
  style={position:absolute; left:8px; top:6px; z-index max; pointer-events:none}>`
  from `layoutBlockLabel(label, frameWidth)`; returns null + console.warn
  when no glyph fits.
- `CaptureGallery.tsx` CaptureCanvas: mounted as the LAST child of
  `[data-capture-canvas]` after `</RootErrorBoundary>`, guarded by
  `showLabel = !WPT_MODE && node.children.length === 0 && !(text non-empty)`,
  `frameWidth = CANVAS_WIDTH_PX`. The canvas already supplies containing
  block (`position: relative`), stacking root (`translateZ(0)`) and frame
  clip (`overflow: hidden`).
- `FixtureCanvas.tsx`: its own ComposedNode predicate, no WPT term, frame
  width = its own 390 (`FIXTURE_FRAME_WIDTH_PX`, CSS emitted byte-identical).
- `sdui/ComponentRenderer.tsx`: block-label svg branch, `blockLayout`,
  `componentWidth` plumbing and `usedPxWidth/parsePx` removed; the 0-height
  wrapper span keeps its geometry (padding 0, height 0, position relative,
  overflow visible) keyed on `isLabelSlot = text === undefined && !WPT_MODE`;
  `PlaceholderContent` lost its unused `name` prop; `renderEmptyContent`
  hook contract unchanged.
- `runtimes/web/src/renderer/BlockFontLabel.ts`: `BLOCK_LABEL_FILL` α
  0.7 → 0.70196 (179/255) for byte parity; comments on the capture-frame rule.

### 3.4 Tooling and docs
- `docs/DYNAMIC_CAPTURE.md` `## 5. Harness label chrome` — the normative
  section.
- NEW `tools/visual/label-chrome-tripwire.test.mjs` (node --test): per
  baseline stem derives the expected glyph set P for the stem's label at
  (8,6) truncated to the PNG width; mode per stem — band-identical when rows
  0..15 outside P are ground on all three (111 stems), else glyph-mask (19);
  asserts (i) every p ∈ P non-ground ×3, (ii) rows 0..15 byte-identical ×3 in
  band-identical mode, (iii) P-mask bytes within ±1/channel ×3 in glyph-mask
  mode; six PNG-independent controls (atlas geometry, synthetic triplets,
  in-file mutations). NEGATIVE CONTROL on the pre-refresh baselines: 136
  tests, 6 pass, 130 fail — every stem red on (i). It goes green only after
  the refresh in §5, so the refresh MUST land in the same PR (the file is in
  CI's `node --test tools/visual/*.test.mjs` glob).

## 4. Pins (all executed; mutation records in `lane-reports.json`)
- iOS `HarnessLabelChromeRasterTests` (Catalyst): variants (a) plain 120×40
  leaf, (b) multiply, (c) opacity 0.5, (d) rotate(15deg) + margin-top 40,
  (e) clip-path circle, (f) overflow hidden 100×50, (g) `.sampling`; band
  rows 6..12 byte-equal to (a), ink (174,174,180), zero lit pixels outside
  the band; negative pin `wptCaptureMode = true` → zero ink. Mutations M1
  (overlay on ComponentHost → zero band ink, ink at (24,22)), M2 (drop
  the wpt guard), M3 (drop the sampling guard), M4 (drop
  `.compositingGroup()` in BlendModeApplier → the re-targeted blend pin
  red: child (0,8,11) vs group (0,26,46)), M5 (restore BlockLabel in the
  leaf branch → 271 stray px). `CaptureCanvasMirror.swift` hoists the
  byte-mirror canvas builders (view-returning, `withLabelChrome:` flag).
  `WPTCaptureModeTests` and `EmptyFlexContainerTests` re-targeted off the
  runtime-drawn label.
- Android `HarnessLabelChromeTest` (JVM): P1 geometry ((8,6) first pixel,
  62/39/1/0 truncation, `_`→space, case-insensitivity through normalize);
  P2 source scan sliced to the CaptureCanvas region (drawWithContent between
  onGloballyPositioned and padding, WPT gate present, ComposedCaptureCanvas
  free of it); P3 no `BlockLabelPlaceholder(` / no `@Composable` in
  BlockLabel.kt. Mutations: draw after padding, gate dropped, origin (24,22).
- Web: three URL-scoped vitest files (`?mode=capture`, `&width=250`,
  `&wpt=1`) — one svg per canvas, sibling not descendant, style tokens,
  no svg under `[data-component-id]`, 39-glyph truncation via
  `getAttribute('width')`, zero chrome under WPT / with text / with
  children / in ComposedCaptureGallery; parity golden regenerated.
  Mutations: chrome inside the component element, Infinity frame width,
  `!WPT_MODE` dropped.
- Tooling: the tripwire's in-file controls and its pre-refresh red.

## 5. Baseline refresh procedure (from what the comparator does)
Baseline pairs regress only via `pairRegressed` (px > 2 % OR ΔE95 > 5.0 OR
SSIM < 0.95; pixelmatch 0.02, includeAA:false), so most of the 130 stems
change pixels WITHOUT tripping exit 1: by committed ink count only 26 stems
clearly exceed 2 % by relocation, three more sit at the knife edge (022, 077,
106). Exit order in `compare-screenshots.mjs`: 4 (unledgered divergence) >
6 (oracle) > 5 oracle-stale > 6 (oracle checked == 0) > 5 gate-stale > 1.
Procedure, on a quiet host, ONE emulator, the seed simulator:
1. `BASELINE=1 ./test-all.sh --gate-set` once; record every exit. Expected:
   `visual-test.json` exit 5 naming the two `Edge_GradientWithRadius`
   ledger lines (their reason is the web label past the box) — DELETE them;
   possibly exit 4 where the departing label UNMASKS a divergence; then exit 1
   on the relocating stems. `filter-sepia-amounts.json`: exit 1 on its 8
   stems; the two Sepia_Translucent ledger lines and the fixture's own
   `_expect.waive.iOS` stay (a filter-composite drift, not the label).
   The five `combinations/*` and `composition-test.json` are gate-only:
   expected exit 0 (their oracles probe fills/boxes the chrome never
   touches; `003_wrapper` was 1.00 ×3 at wave51-open).
2. LOOK at every changed PNG, all 390, at ≥ 3× against its committed twin.
   The legitimate diff of a PNG is exactly two regions: (i) the OLD label
   footprint at the element's content-box origin AFTER its own layout —
   read off the diff, never a fixed formula (008 (54,42); 086/039 (59,47);
   099 (29,32); 037 rotated rows 43..71; 043 zoomed from (43,40)) — where
   label ink becomes the underlying paint, and (ii) the NEW band rows 6..12,
   cols 8..≤382. STOP conditions, checkable: S1 PNG dimensions unchanged on
   all 390; S2 band ink == P exactly on the 111 ground-only stems and
   byte-identical ×3; S3 every diff pixel outside (i)+(ii) is a defect —
   count-bounded on the 19 filter/blend stems; S4 on the 19 band-paint
   stems the glyph mask is identical ×3. Any violation: do not refresh,
   revert the platform's half, bisect.
3. `UPDATE_BASELINE=1` for ALL FOUR baselined fixtures unconditionally
   (`visual-test.json`, `filter-sepia-amounts.json`, and the off-list
   `properties/sizing/aspect-ratio.json`, `properties/effects/filter-grayscale-basis.json`),
   `node tools/visual/baseline-stats.mjs`, re-run `BASELINE=1 --gate-set`
   → exit 0 ×8; the tripwire → green (expected 126/130; the tooling lane
   predicts 4 glyph-mask stems red on clause (iii) at ±1 — 027_Shadow_Spread
   and three others where one platform paints under the glyphs — a decision
   the refresh must take: relax (iii) to positional-only on those stems or
   ledger them; see `lane-reports.json` tooling openQuestions).
4. Staleness of `Neumorphic_Light` ×2 and `Edge_InsetRoundShadow` ×2 ledger
   lines is a MEASUREMENT (their HEAD iOS PNGs already carry the spilled
   label positionally identical ×3); the stale criterion is SSIM ≥ 0.95 AND
   Δpx ≤ 2 % AND ΔE95 ≤ 5.0.

## 6. What the corpus (TITAN) will NOT see
Every corpus capture runs with the suppression flag on (web `WPT_MODE=1`
from section-runner, iOS `TITAN_INBOX` → `.wptCaptureMode`, Android inbox /
composed → `LocalWptCaptureMode`), and the chrome is gated by exactly those
flags, so no corpus-v6 cell can move; a corpus delta after this PR is a bug
in the gate wiring, not a result. The gate measures this PR only through
the fixture net; the fidelity wins (14 spill rows + the two 9(g) carriers'
label halves) are measured by a manual `./test-all.sh` over
`fixtures/fidelity/layout.combos.json`, `animations.combos.json`,
`background.combos.json`, `borders.combos.json`, `pairwise/pairs-02.json`,
`pairs-03.json`.

## 7. Risks, decisions and open questions
- 390 baselines move at once; the comparator has no label-aware masking, so
  the §5 review is the only defence against a real regression hiding under
  the refresh.
- Frame width: web `CANVAS_WIDTH_PX`, iOS `CaptureCanvas.width`, Android
  `size.width` at density 160 must be the number the PNG is cut to — the
  Android `Log.w` and the tripwire's width check are the proofs.
- The block-font machinery stays in the product runtimes (harness-only
  now); "relocate label machinery into the harnesses" is a follow-up.
- Nested leaves in tree fixtures lose their in-parent labels; roots gain
  none (no baselines affected; gate cells move 3-way identically).
- The gallery card on Android loses the in-component label — accepted.
- `BlockLabel.swift` is 209 lines after the header rewrite (existing file).
- Tooling lane clause (iii) decision (§5 step 3).

## 8. Out of scope
The iOS clip half (retro R4, landed); the iOS blend-group self-multiply
(wave 50, landed; this PR only moves the label out of the group); PR (B)
(the web `width: fit-content` initialiser); 9(g) as a fix (PR 2, landed
first, so this PR is not counted as closing it); any TITAN corpus change;
the atlas, `block-font.json`, `gen-block-font.mjs`, the ink colour value.

## 9. Revision history
- v1 (2026-09-15): four platform readings + synthesis; refuted 2/3 (blast
  radius, harness contract) — 30 corrections.
- v2: 29 applied; refuted 2/3 — 28 corrections (baseline footprints follow
  the element's layout, exit-1 expectations wrong under the 2 % threshold,
  mechanism (v), stale predictions for two refreshed PNGs, web vitest URL
  scoping, Swift access control, BlendGroup pin numbers).
- v3: 24 applied; ACCEPTED (platform-semantics and pins lenses pass;
  blast-radius lens lists 9 non-load-bearing corrections, notably the web α
  0.7 vs 179/255 parity, folded into §2).
- Built 2026-09-16 by four lanes (`lane-reports.json`); skeptics with
  executed repros follow.

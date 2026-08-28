# Project status

One page, honest. This file replaces the retired `docs/reports/` campaign
tree (3,500+ per-round audit reports); the durable conclusions live here,
the full history lives in git history.

## What works

- **Converter** (`converter/`, Gradle `:converter`) — parses CSS
  declarations (JSON envelope in) into the typed IR and emits it as JSON
  (`--to ir`, the only output target today).
- **Three runtime style engines** render that IR live:
  `runtimes/web` (React → DOM/CSS), `runtimes/compose` (Jetpack Compose),
  `runtimes/swiftui` (SwiftUI). Every property ships as a
  Config/Extractor/Applier triplet at the same canonical path on all three.
- **Visual harness** — `./test-all.sh` renders any fixture on all three
  platforms and compares captures with SSIM;
  `BASELINE=1` gates against the 363 committed baseline PNGs in
  `tools/visual/baseline/`.
- **Wire contract** — the converter emits IR v2 by default (the flat-list
  slot/placement wire); the JSON is machine-checked against
  `schema/ir-v2.schema.json` (`schema/ir-v1.schema.json` is the deprecated
  legacy contract for the `--emit-ir v1` compat wire) + the normative spec
  in [`schema/spec/`](../schema/spec/); 39 golden fixtures (12 v1 + 27 v2)
  are decoded by conformance tests on all four codebases
  (`node schema/conformance/run.mjs`).

## Coverage — three numbers, all true

| claim | number | source of truth |
|---|---|---|
| Registration coverage — triplet exists + claims the IR type (a string-presence facade; not native rendering) | **558 / 558 per platform** (Android 558 / 558 · iOS 558 / 558 · Web 558 / 558) | `node tools/visual/coverage-audit.mjs` (`registered:`) → `tools/visual/COVERAGE.md` |
| Real-applier floor — a dedicated `<Name>Applier` file exists (under-counts grouped appliers) | **Android 19 / 558 · iOS 74 / 558 · Web 516 / 558** | `coverage-audit.mjs` (`real:` line) |
| Verified rendering coverage — SSIM ≥ 0.95, every variant, every platform pair | **91 / 550 (~17%)** | converged audit campaign, round 40 (below) |

"Registered" is only a string-presence facade — a triplet exists and
claims the IR type; it does NOT mean a dedicated applier renders the
property natively. The stricter real-applier floor counts a dedicated
`<Name>Applier.<ext>` file per property; it under-counts grouped appliers
(one file — e.g. Compose `LayoutApplier.kt`, iOS `FlexboxApplier.swift`,
web `ScrollMarginApplier.ts` — renders many properties but its basename
matches at most one IR name, so the raw dedicated-applier file counts
Android 60 · iOS 119 · Web 530 sit above the per-property floor). Some
registered appliers are intentional no-op + TODO where no mobile analogue
exists (speech/, regions/, print/, …).

### The verified-coverage headline (audit campaign, converged round 40)

A 70-round auditor-driven campaign drove the per-property tracker from a
dishonest "548/548 passing" (~85% degenerate fixtures — empty boxes
matching empty boxes) to a substantively honest classification:

| | Start (round 0) | Converged (round 40) |
|---|---|---|
| Total rows | 548 | 550 |
| Passing | 548 (100%) | **91 (16.5%)** |
| Blocked-platform | 0 | 419 |
| Failing | 0 | 3 |
| Exhausted | 0 | 37 |
| **Honest meaningful coverage** | **unstated** | **~91/550 (~17%)** |

- **Passing (91)** — SSIM ≥ 0.95 on every value variant on every
  platform pair against committed baselines.
- **Blocked-platform (419)** — cannot manifest in static-mobile
  fixtures: needs animation state, scroll context, real tables/lists,
  aural rendering, paged media, cursor/input state, etc. Each row
  carried a root-cause note.
- **Exhausted (37)** — no meaningful visual test exists.
- **Failing (3)** — known real cross-platform divergences (e.g. Android
  `Modifier.scale()` clips to parent bounds at scale ≥ 1.5 where
  iOS/web extend beyond).

The campaign also produced 31 gold-standard fixture rewrites and 4 real
renderer fixes (iOS mask-image:none short-circuit, Android
gradient-brush size, iOS FilterApplier .thinMaterial removal, a
test-all.sh octal-arithmetic crash).

## Testing-tier record

Where each testing axis landed when the campaign closed. Tiers whose
bespoke drivers were retired in the repo prune keep their last verified
result here as the historical record.

| # | axis | status |
|---|---|---|
| 1 | Variant depth (every parser branch) | partial — the 91/419/37/3 classification above |
| 2 | Category-pair combos (33×33) | partial — 526/561 cells passed, but ~99 used no-op defaults (shallow); needs combo-fixture redesign |
| 3 | Realistic components | partial — 8/15 passing; 6 share an Android flex+text+border-radius root cause, 1 iOS font-metrics |
| 4 | Keyframe snapshots (t=0/50/100%) | partial — 14/15 passing; scale keyframe blocked on the Android clip-to-parent gap |
| 5 | Interaction states (:hover/:focus/:active) | web slice done — 90/90 captures via `tools/visual/interaction-states.mjs`; captures browser-default state changes only (IR has no pseudo-class support yet); iOS/Android harnesses were stubs |
| 6 | Layout under constraint changes | complete (5/5) |
| 7 | Parser fuzz (malformed CSS never panics) | complete (15/15) |
| 8 | SSIM history tracking | retired — history tool pruned; BASELINE=1 regression gating supersedes it |
| 9 | Real-page conversion | retired — best result: twitter 73%, tailwind 41% (0 deep failures, mean SSIM 0.952); adapter + scraped CSS dumps pruned |
| 10 | Performance benchmarks | retired — web capture batching fix landed (test-all.sh keeps it); bench driver pruned |
| 11 | Accessibility (WCAG 2.1 AA) | web color-contrast slice done — 15/15 fixtures scored via `tools/visual/a11y-audit.mjs`, 2 real AA contrast failures found; 7 other criteria blocked on missing IR semantics (no IRRole/IRAriaLabel/IRAlt) |
| 12 | OS-version snapshot matrix | retired — last result: 109/109 fixtures byte-identical across iOS 26.0 ↔ 26.2; driver pruned |

Still-live harnesses: `tools/visual/smoke.sh` (unit tests + Tier 5 +
Tier 11 + BASELINE=1 + baseline classifier), `tools/titan/` (WPT-corpus
pipeline), and the full `./test-all.sh` visual pipeline.

### WPT fidelity — composed 3-platform corpus

The `tools/titan/` pipeline diffs each WPT test rendered on all three
runtimes against the **same** upstream Chromium browser-ref (composed
per-test capture, SSIM). Two committed, hand-pinned snapshots (the run
dirs + WPT corpus are gitignored):

- `tools/titan/results/smoke-latest.json` — 33-test gated smoke: web 0.935
  (16/33 ≥ 0.95), iOS 0.916 (13/33), Android 0.918 (14/33).
- `tools/titan/results/corpus-v1.json` — first real **multi-section**
  corpus (7 sections × 12 bucket-A tests = 84, via `section-runner.sh
  --all-platforms`): web 0.915 (40/82 ≥ 0.95), iOS 0.895 (29/82), Android
  0.892 (28/75). `corpus-v2.json` re-measures the same corpus after
  applier-campaign waves 1–3: web/iOS byte-identical (the sampled tests
  don't exercise the fixed properties — and capture determinism holds to
  4 decimals), Android 0.898 (32/81, +4 passing and +6 measurable via the
  feeder cascade-recovery). The applier campaign's own axis is tracked in
  `tools/visual/reverify-wave{0,4}.json`: deep divergences 33 → 21,
  16 properties improved / 0 lost (BackgroundRepeat +0.29, Position
  +0.28, MaskImage +0.19, border-image family resurrected). Wave 5
  (`tools/visual/wave5-solo-gate.json`) mined the deep-19 typography set
  and found a bug farm behind the "font wall": ~20 real renderer bugs
  fixed across the three platforms (Compose skipped text-transform on
  real text and rendered word-spacing as letter-spacing; iOS never
  rendered word-spacing, dropped line-through to an underscore-token
  miss, used push-out line breaking against Chromium/Compose's greedy
  breaks, and rounded font weights down at 600/800; both natives dropped
  em/rem spacing and currentColor; even the web reference dropped em/rem/%
  font-size and the slice-`fill` keyword). Both natives now OWN their
  decoration lines at Chromium-measured 2px geometry. Solo device mins
  moved: word-spacing 0.763→0.868, text-decoration 0.763→0.864 (Triple
  0.737→0.893), font-weight 0.785→0.870, font-size 0.786→0.860 (9/17
  variants now ≥0.95 on every pair; em/rem fixed outright), slice-fill
  0.866→0.986. What remains on these fixtures is the measured Android-web
  ~0.87 rasterization wall (edgeSsim 1.0, identical glyph extents — AA
  and stem-weight divergence only, content-dependent). Honest holes it surfaced: `css-break` background-images
  don't render in fragmentation contexts (SSIM 0.16–0.38), `css-flexbox`
  mobile hits 0/12 ≥ 0.95, and one `css-transforms` fixture
  (`3d-rendering-context-and-z-ordering-003`) wedges the Android composed
  capture. (That last one made `css-transforms` Android read 5/12 in this
  snapshot: the wedge cascade-timed-out the 6 fixtures queued behind it —
  since fixed by restart-on-timeout recovery in the native feeders, so a
  re-feed now yields 11/12.) Full 10,944-test bucket-A remains the
  long-horizon target; this is a stratified sample. Wave 6
  (`tools/visual/reverify-wave5.json` + `wave6-solo-gate.json`) re-measured
  the full 94-set (bands 72/3/19 → 73/5/16), then broke four walls: CSS2
  §8.3.1 margin-collapse emulated in both natives' block paths under a
  unified cross-native-pinned gate contract (margin-trim 0.832 → **1.0000
  flat** vs Chromium); much of the "Android glyph wall" fell — the drift
  was hinting-quantized glyph advances (Compose `TextMotion.Static`), not
  antialiasing, and fractional advances lift typography to 0.96–0.99
  (initial-letter 0.911→0.9907, font-style 0.822→0.964 incl. new
  oblique-angle support + a converter grad/rad suffix-order fix); iOS
  gained a real background-blend-mode compositor (0.934→0.983+); and the
  z-ordering-003 Android composed-capture wedge is FIXED — a negative
  multicol column width (css-multicol §3.4 used-value clamp; the fixture
  now captures in ~1s). The stale Scale "failing" record was refuted with
  fresh device pixels (0.994+). Wave-7 residue: iOS line-height
  rem/unitless (0.876/0.907), TextAlign_Center 0.937 iOS-Android. Wave 7
  (`tools/visual/wave7-gate.json` + `tools/titan/results/corpus-v3.json`)
  measured the TextMotion harvest — 13 of 18 typography rows moved to
  hold ≥0.95 (LetterSpacing/WordSpacing +0.105, TextDecorationLine
  +0.102, FontWeight +0.101, LineSnap +0.099, …) — and closed
  line-height: a THIRD wire generation (nested length, no top-level px)
  was silently dropped (iOS: all lengths; Compose: nested plain-px);
  unwrapped with em resolving against the element's OWN font size
  (css-values-4 §6.1), plus a cross-native (L−natural)/2 placement
  compensation for sub-natural line-heights — 0.876 → **0.9927 flat**
  across all nine variants. corpus-v3: **Android measured 82/82 for the
  first time** (z-ordering-003 on the board; css-transforms Android
  0.973, 10/12). Remaining below-bar typography: FontFamily_Cursive
  0.819 (blocked — platform cursive stacks), FontSize keyword-fractional
  0.864 (wall — identical extents), TextOrientation_Upright 0.869
  (capability tier), TextAlign_Center 0.937 (run-width divergence under
  centering — wave-8 queue). Wave 8 (`tools/visual/wave8-gate.json`)
  repaired the WPT harness itself: `extract-fixture.mjs` gained the child
  combinator (seven flexbox fixtures had shipped styleless placeholder
  children) and inlines WPT support/ rasters as percent-encoded data URIs
  (three css-break tests had 404ed on every platform — web now paints the
  ref's ink volume, exposing the true fragmentainer-background gap
  beneath); the converter preserves url() payload case in four parsers
  (base64 data URIs survive byte-exact); TextAlign_Center closed 0.937 →
  **0.9857** by modeling Android's DRAW-path even-truncation (the report
  API uses a different formula — the wave-7 probe read the wrong oracle);
  iOS positioned descendants now paint above their ancestor's border
  (CSS2 Appendix E) via a StyleBuilder box-decoration/group-effects
  split, byte-identical for plain renders; fallback-justify-content
  passes on all three platforms (web 0.724 → 0.983). Wave-9 seam:
  percentage Width/Height (px:null wire) dropped on abspos children by
  every runtime including web; native tiled-url() backgrounds;
  fragmentainer background geometry. Wave 9 (`tools/visual/wave9-gate.json`)
  then overturned half its own premise: the abspos overlay WAS painting
  on web and Android — the flat 0.899 was **SSIM color-blindness** (a
  full red→green repaint moves SSIM by 0.0001; labDeltaE/histogramKL
  carry the color signal). The real bugs: iOS percent *heights* were
  unconditionally skipped (a containingBlockHeight channel now exists,
  gated on a definite basis; the % basis moved to the css-position-3
  padding box); a wave-8 interaction bug made Compose percent sizing a
  no-op under the unbounded abspos measure (pre-resolved against the
  containing block — regression prevented before reaching devices);
  Android url() backgrounds were a silent no-op with a dead zero-call
  renderer (now tiled through the shared decode + BackgroundTileMath);
  the BackgroundPosition *shorthand* wire was consumed by no runtime
  including web (all three extractors now parse it); and iOS gained a
  multicol-aware WPT auto-width fill. Operational discovery: pool
  feeders run --skip-install against the provisioned app — every native
  section capture since provisioning used a stale binary; reprovision
  before section runs that must reflect renderer changes. Wave 10
  (`tools/titan/results/corpus-v4.json`) shipped the WHITE-CANVAS
  boundary: WPT captures now render on a white canvas on all four
  surfaces (refs cache under a /white segment; v4 is NOT comparable to
  v1–v3), a color-aware reftest signal (colorComposite + colorDivergent
  — raw SSIM moved 0.0001 on a full red→green repaint), native multicol
  child fragmentation (css-break-3 §4 clip+translate, shared S-table
  pinned identically on both natives), the abspos safe-alignment
  fallback on both natives, and the composed-mode maxWidth clamp fix
  that was the real web fragmentainer mechanism. corpus-v4: web mean
  0.9593 (61/78 ≥ 0.95, **css-break web 12/12**), Android 46 passing
  (was 33), iOS 36 (was 29); score-exclusions are delivery gaps only. Wave 11 (`tools/titan/results/corpus-v4-1.json`) completed the
  typographic contract: default prose ink flipped to spec BLACK (v4.0's
  default-ink text tests matched *vacuously* — neither side showed the
  text), the ref pins the harness Inter stack (with a fonts.ready await
  — the un-awaited load raced the screenshot and collapsed the first
  run), and line-height:1.25 is pinned on all four surfaces (proven
  ref-vs-web SSIM 1.0000 on the calibration test). Plus: CSS comments
  are token separators in the extractor (hsla comment variants), leading
  anonymous body text becomes a component, WPT-mode content-box default
  and the css-grid-1 §9 abspos partition on both natives, and the
  multicol fragmentation gate now fires in the real tree. corpus-v4.1 —
  the first fully-real snapshot: css-color web 11/iOS 11/Android 10 of
  11 (means 0.986–0.996), **iOS total 36 → 56 passing**, css-transforms
  iOS 12/12, css-grid Android 4 → 8. Widest remaining native gap:
  Android 16px prose rhythm (css-flexbox Android 1/12) — the wave-12
  seam. Wave 12 (`tools/titan/results/corpus-v4-2.json`) refuted that
  seam with sub-pixel measurement (Android 16px advances match the ref
  to ≤0.27px — TextMotion holds) and found the real bugs: the ONE
  surface the ink flip missed (the web composed canvas — inheriting
  prose was invisible), Android RTL relative-position mirroring (CSS
  insets are physical; the named applier had zero live callers — the
  legacy chain was fixed), Android explicit-width clamping, the iOS
  containing-block box-sizing axis (fragmentainer sliced at 118 not
  120; the abspos child 70×80 not 100×100), WritingMode never inherited
  (the vertical bail could not fire), and the extractor's inline-run
  fragmentation (`<strong>` runs became block components — now merged
  into parent `_text`, marked lossy). corpus-v4.2: **web 0.9787 mean,
  71/78 — css-grid 1.000/12 perfect, css-break 0.999/12**; Android
  46 → 58; iOS 56 → 59. Remaining: the css-ui form-control wall, the
  native float-layout wall, vertical writing modes, and the
  out-of-contract multi-child multicol family — all classified. Wave 13
  (`tools/visual/reverify-wave12.json`) re-measured the full 94-set:
  **90/94 properties hold ≥ 0.95** (wave-5: 73; wave-0: 11), zero
  regressions — the property axis is essentially complete within
  current capability tiers; the 4 remaining rows are all named walls
  (SVG-source blocked variant, cursive stacks, keyword-fractional
  sizes, vertical text). iOS gained Android-parity greedy multi-child
  multicol distribution (the shared loop extracted pure and pinned
  identically on both platforms). Metrology follow-ups queued from the
  residue audit: the browser-ref semanticPresence pass-gate (blank
  captures can currently "pass" against tiny-widget refs — a
  corpus-version decision), the static @keyframes t-sampler for
  negative-delay animation tests, and delivery-aware score-exclusions
  (three css-backgrounds exclusions are stale post-inliner). Wave 14
  (`tools/titan/results/corpus-v4-3.json`) shipped all three as the
  HONEST-SCORING boundary: the semanticPresence pass gate (calibrated on
  246 pairs with 3× margins — the vacuous passes flip; css-ui honestly
  reads 3/10, matching the audit prediction), delivery-aware exclusions
  (denominator restored), the static @keyframes t-sampler (negative-delay
  animation tests bake their sampled values and score real), the
  nativeParity metric, and a bonus fix (composed-mode cross-platform
  pairs were silently null). Two new sections joined: **css-text lands
  at a uniform 8/12 on all three platforms** — the typographic contract
  holding on virgin tests — and css-position (~0.91, 4–5/12) is the
  next mineable seam. Honest totals: **web 84/106 · iOS 71 · Android
  68** (not comparable to raw-SSIM v4.2 counts). Wave 15
  (`tools/titan/results/corpus-v4-4.json`) extended the boundary with
  the extraction-wall exclusion (script execution has no delivery
  record by construction — css-position honestly reads as an EMPTY
  eligible set; the widened top-layer rule catches popover/dialog
  APIs), decoded character references and the `dir` attribute in the
  extractor (bidi tests carried literal `&#9;` strings), made text
  collapse white-space-aware, and fixed the native composed-canvas
  alpha bug (a sampled `rgba(0,0,0,0)` body painted VERBATIM — iOS
  scored 0.000; both natives now composite over the white canvas):
  css-backgrounds natives recovered to 0.970 means (Android 9, iOS 10
  of 12). Queued: the Android window-bound composed capture
  (scroll-and-stitch), body-root height sibling-stacking, the
  non-Latin shaping wall. Wave 16 (`tools/titan/results/corpus-v4-5.json`)
  built the strategic capability: **post-load state extraction** — the
  pipeline loads the test page in Chromium under the exact ref canvas
  contract, snapshots a 34-property computed set per element, and bakes
  the post-script state into the fixture (honest bails for
  timers/scroll/structural mutation; top-layer declined). Proven on
  device: css-position went from an empty eligible set to **6 scored
  tests, web 6/6 passing** (three at a perfect 1.000) — script
  mutations land as typed IR and render correctly; the natives' 3/6 at
  ~0.95 gives the next wave newly-visible real targets. The Android
  composed capture is no longer window-bound (offscreen full-height
  route; the 5132px tall-doc test captures fully — css-backgrounds
  Android 10/12). Wave 17 (`tools/titan/results/corpus-v4-6.json`)
  landed the out-of-flow contract on the natives: both runtimes had
  positioned `fixed` elements *parent*-relatively (Compose's viewport
  wrapper was dead code; iOS merged fixed into absolute) and anchored
  `absolute` at flow-slot + offset. The canvas-root hoist implements
  css-position-3 identically on both — fixed always hoists to the
  unpadded canvas origin, out-of-flow boxes reserve no flow space —
  gated so the 327 dark-stage corpus is structurally untouched.
  **css-position: 6/6 on all three platforms** (natives at three
  perfect 1.000s), the first section in campaign history where every
  platform passes every scored test — on post-load-extracted dynamic
  tests. Wave 18 (`tools/titan/results/corpus-v4-7.json`) broadened the
  corpus to **12 sections**: css-sizing, css-display and css-overflow
  first-captured under the mature instrument — and web passed **all 35
  scored new-section tests on first capture** (css-sizing a perfect
  1.000 mean). The native residue diagnosed to six mechanisms, fixed in
  one round: the wave-17 hoist made inset-aware (no-inset absolute
  renders at its static position, css-position-3 §3.1, with css-position
  6/6 regression-guarded), abspos inset-stretch sizing with aspect-ratio
  inline-priority, percent-padding basis (real containing block;
  indefinite → 0), the `ch` unit resolved from font metrics (Android had
  collapsed ch to width 0 — an entirely blank canvas), axis-selective
  overflow clip, and real `display:contents` unboxing (children spliced
  into grid/flex item collection) plus the iOS `all:initial`
  sole-override port. Cross-native skeptic probes (a 247-component
  classifier diff; a 22-expression calc table run through both real
  evaluators) caught four more divergences pre-gate — iOS gained a
  line-for-line port of Compose's calc evaluator, last-write-wins
  logical-overflow resolution, and outline-outside-own-clip ordering.
  The wave-15 body-root defect fixed by child-slotting (an
  explicit-height body nests its children in the IR instead of
  sibling-stacking them below a 4000px block); its css-backgrounds
  target flipped to pass on Android, web at 1.000. **css-sizing: 12/12
  on all three platforms** — the second-ever perfect section (from
  Android 6/12, iOS 8/12 at first capture); css-display web+iOS 12/12;
  zero regressions across all 12 sections; 327-net clean. Corpus
  totals: web 119/134, iOS 107/134, Android 103/134. Wave 19
  (`tools/titan/results/corpus-v4-8.json`) extended the static-position
  rule to flex and grid formatting contexts — **css-flexbox and
  css-display now 12/12 on all three platforms**, joining css-position
  and css-sizing at four perfect sections. Landed (byte-parallel twins
  on both natives): the full flex static-position resolver (one
  axis-mapping table over flex-direction × writing-mode × direction,
  main axis via justify-content-as-sole-item, cross via align-self);
  grid content-distribution offsets (justify-content on tracks + rtl —
  the skeptic caught a SwiftUI double-RTL-flip, fixed by solving in
  logical space); a narrow float row-packing contract (CSS2.1 §9.5) on
  all three engines, including the web engine's own br/clear
  segmentation bug (justify-self-001 recovered on every platform);
  border/outline `currentColor` resolving to WPT black ink — css-break
  background-image-000 hit a literal 1.000 on both natives; the harness
  root-stack margin fold collapsing through zero-flow-space out-of-flow
  roots (§8.3.1); post-load extraction engagement for style-mutation
  tests; and the iOS line-clamp chain (unread `count` wire key + an
  inner `.lineLimit(nil)` clobbering the cap — block-ellipsis-001
  converged onto Android at 0.925/0.924, a shared wrap-point metric
  residual). Zero regressions, 327-net clean. Corpus totals: **web
  120/134, iOS 115/134, Android 114/134**. Named walls this wave:
  vertical-writing-mode multicol layout transposition (css-break
  background-image-001/002) and Compose ancestor-3D matrix propagation
  (backface-visibility). Wave 20 (`tools/titan/results/corpus-v4-9.json`)
  attacked the css-ui widget wall and gave the instrument its second
  strategic capability: **post-load STRUCTURE extraction** — when a
  script mutates DOM structure, the settled live DOM is serialized and
  re-fed through the same extraction pipeline, so created nodes become
  first-class components (css-position grew to **7/7 on all three
  platforms**). Widget identity now flows end-to-end: the extractor
  emits `meta.attrs` (10-key allowlist, XHTML-namespace-gated) through a
  converter passthrough + v2 schema extension; the web harness renders
  REAL form controls in WPT mode (pixel-identical to the ref's Chromium
  by construction); both natives gained UAWidgets replica modules
  (shared Chromium-lookalike geometry tables), inline-atom row flow, and
  right-float run packing — **css-grid 12/12 on all three platforms**,
  the fifth perfect section. accent-color-visited, the non-widget test,
  and the createElementNS namespace test all pass everywhere. Residual:
  the five appearance-alias tests (one shared ref) sit at web 0.827 /
  natives ~0.876 — the natives now beat web there, and since web paints
  real Chromium widgets with ref-matching rows, the divergence points at
  a ref-vs-harness capture contract difference (form-control theming),
  queued. Zero regressions; 327-net clean. Corpus totals: **web
  124/136, iOS 121/136, Android 119/136**.
  Wave 21 (`tools/titan/results/corpus-v4-10.json`) broadened the corpus
  to **15 sections** (css-images, css-multicol, css-text-decor) and
  self-corrected the instrument twice more. The capture-contract
  diagnosis found the harness's own universal CSS reset stripping UA
  form-control padding — one `revert` rule took **all five
  appearance-alias tests to exactly 1.000 on web**. Extraction fixes: a
  silent head-unwrap catastrophe (all body content destroyed for
  `<head>`-no-`<body>` docs, marked lossy:false), bare-`<br>` phantom
  boxes replaced by a line-context rule, dropped body-level text,
  `sibling-index()` baked at extract time, reorder honesty flags. The
  gradient parser family was overhauled (double-position stops, length
  centers, interpolation-method peel, mask degraded-twin deleted) and
  the web engine had **never applied any gradient center** (a
  `pos`-vs-`position` field mismatch, corpus-wide). `cross-fade()`
  implemented end-to-end on all three engines with premultiplied math.
  Native text-decoration style/thickness painting (Blink-matched dotted
  rhythm), multicol spanner semantics + intrinsic sizing, the Android
  nested-multicol wedge (a snapshot write during measure), and a
  headless composed-page paint divergence worked around via isolated
  re-capture. **Honest-frame SSIM** replaced union-frame scoring (white
  padding had inflated wrong renders above honest ones — zero pass
  flips). Bucketer: 295 mismatch-only reftests moved to C, 71 .sub.html
  tagged requires-wpt-server. Result: css-flexbox/grid/display/sizing/
  position all perfect on all three platforms, css-multicol web+iOS
  12/12, css-images 12/11/11, zero pass-regressions, 327-net clean.
  Corpus totals: **web 163/174, iOS 148/174, Android 146/174**.
  Wave 22 (`tools/titan/results/corpus-v4-11.json`): **css-ui 12/12 on
  all three platforms** — the sixth perfect section, from 2/9 when the
  widget wall was first hit — and **css-multicol joins at 12/12** (the
  seventh). The native widget band was precision-diffed against the
  pixel-exact web oracle: UA-metric label fonts, the range atom's
  missing Blink UA margin, ref-probed ink constants, `:not()` selector
  support in the extractor (menulist-button web 0.554→1.000), and
  intrinsic-widget empty bags — every alias test now 0.994/0.995 on the
  natives. Compose's bottom/right inset anchoring bug fixed
  (`bottom:0;right:0` resolved to the top-left corner). The decorations
  seam landed end-to-end (extractor → converter meta hop → schema →
  native decoders → per-line-color painters → web nested spans), with
  the full 148-name CSS color table now shared by both natives —
  including CSS-correct gray/darkgray/lightgray (Compose had mapped
  them to its built-in constants). The `font:` shorthand emits its
  line-height reset; em margins statically resolve; 52 fixture stem
  collisions disambiguated; post-load extraction folds
  collapsed-wrapper records. Zero pass-regressions; 327-net clean.
  Corpus totals: **web 165/174, Android 156/174, iOS 155/174**.
  Wave 23 (`tools/titan/results/corpus-v4-12.json`) broke the bidi wall
  with the instrument's third bake: Chromium's own visual text geometry
  (per-character client rects grouped into level runs by geometric
  adjacency — never a UAX#9 reimplementation) re-expressed as ordinary
  absolutely-positioned single-level text components every engine
  renders without new machinery. bidi-tab-001 passes on all three
  platforms; bidi-lines-001 web 1.000/iOS 0.993; the native residual
  (~0.93) is now isolated to pure Hebrew glyph ink — the standing
  non-Latin shaping wall. The bake's ink-band verifier independently
  caught Chromium diverging from css-text-3's neutral-paragraph rule.
  The iOS decoration insets cleared exactly as the skeptic's calibrated
  proxy predicted (a line-box cap identity, not the overlay). Three new
  sections joined: **css-masking and css-lists both 10/12 on every
  platform at first capture**; css-gaps exposes the unimplemented
  css-gaps-1 gap-decoration spec on all three engines (the queued
  feature brief). 18 sections, 210 scored tests; zero pass-regressions;
  327-net clean. Corpus totals: **web 190/210, iOS 186/210, Android
  180/210**.
  Wave 24 (`tools/titan/results/corpus-v4-13.json`) landed the
  css-gaps-1 **gap-decorations feature** end-to-end: eight new IR
  properties (the row-rule family, breaks, insets, rule-overlap — the
  catalogue grew 550 → 558), parsers whose skeptic caught
  thin/medium/thick being claimed as named *colors* in both rule
  shorthands, web pass-through emission (capture Chromium implements
  css-gaps natively), and pure segment-geometry painters on both
  natives (the SwiftUI lane's cross-line merge model was refuted by a
  Chrome probe and rewritten to the one-run-per-line truth).
  flex-gap-decorations-002 passes on all three; on-device geometry
  calibration is the named residual. The ref cache key now folds in the
  browser version (grandfathered-claim migration). Canvas-pad
  resolvers on all three composed canvases: clip-path-circle-007 web
  1.000 / iOS 0.995 / Android 0.952 all pass; filter-effects web 5 →
  8/11. Placeholder container guard + list-style bake:
  change-list-style-position web 0.994. Native markers read the
  child's own list-style; Compose's list-form font resolver walks the
  list. Two new sections at first capture: filter-effects
  (backdrop-filter — natives 1/11, the named native feature gap) and
  css-values (the attr() family, the queued bake). 20 sections, 233
  scored; zero pass-regressions; 327-net clean. Corpus totals: **web
  207/233, iOS 192/233, Android 188/233**.
  Wave 25 (`tools/titan/results/corpus-v5-0.json`) is the **image-pad
  boundary** — the most consequential instrument correction since the
  white canvas. The ref contract itself was broken: `:where(body)
  {padding:16px}` cannot move `position:absolute` overlays in ref pages,
  so any ref using abs-positioned expected-result bars was internally
  misaligned by (−16,−16) — painting *correctly* was penalized (92–100%
  of every css-gaps mismatch). Refs now render unpadded at 358×568 and
  the 16px frame is added in image space; the out-of-flow contract
  migrated on all three platforms (css-position 7/7 held); both bake
  paths share one injection factory with the ref pipeline. Scoring
  gained the colorDivergent+ΔE≥2.3 hard fail and the coverage-ratio
  gate — 57 predicted dishonest passes flipped, zero true passes lost.
  With the fixed ref, the wave-24 gap painters were vindicated:
  **css-gaps web 12/12, natives 11/12** (from 2–6/12), alongside three
  general flex fixes (wrapping main-axis gap, shrink-0 overflow,
  cross-axis stretch). The **attr() bake** (the fourth) took css-values
  to 11/12 everywhere. UA block margins extended to block descendants.
  Corpus totals under the strictest contract yet: **web 202/233, iOS
  188/233, Android 183/233** — down ~5 per platform in exchange for
  honesty. Six sections perfect on all three platforms; 327-net clean.
  Wave 26 (`tools/titan/results/corpus-v5-1.json`) landed
  **backdrop-filter** on both natives — a two-pass controlled-canvas
  model (pass A renders the composed canvas with backdrop elements'
  paint suppressed; pass B samples, filters, clips to the border box,
  and paints under the element) with a skeptic-arbitrated unified
  contract (one blur edge model, all-or-nothing chains, foreground
  filters outside the backplate, element opacity attenuating it). iOS
  validates the model (basic 0.966, box-shadow 0.978, opacity twins
  0.999 — 3/11 → 5/11); web's inter-inline-block whitespace fix took
  clip-rect-2 to exactly 1.000; Android's backplate paints but carries
  an accuracy gap (0.881 vs iOS 0.966) — the named follow-up. And the
  headline: after a full worktree recycle, ref-cache rebuild, and
  emulator-pool swap, **18 of 20 sections byte-matched the v5.0
  snapshot** — the instrument's determinism is now measured, not
  asserted. Totals: **web 203/233, iOS 190/233, Android 182/233**;
  327-net clean.
  Wave 27 (`tools/titan/results/corpus-v5-2.json`) grew the corpus to
  **22 sections** and landed **the fifth bake**. css-contain: web
  3→11/12 via the canvas containment gate (a contained body leaves the
  background-propagation path), the body-root pseudo-element leak fix
  (~32 corpus files), and the non-animatable keyframe set.
  css-counter-styles: web 4→11/12 via counter-style-bake.mjs (the §6
  predefined table + five systems + `ol@start`; the skeptic killed
  `<ol reversed>` baking forward, negative-ordinal empty markers, and
  shorthand silent-decimal). css-lists web 12/12 — a new perfect web
  section. The Android backdrop gap was **one term**: the backdrop
  draw node sat outer of `absoluteOffset`, sampling a pixel-perfect
  filter of the wrong rectangle (both alternate suspects disproven
  with device evidence) — filter-effects Android 2→4/11. Five more
  Dependabot PRs merged through the CI gate. Totals: **web 226/257,
  iOS 202/257, Android 194/257**; 327-net clean.
  Wave 28 (`tools/titan/results/corpus-v5-3.json`) resolved the
  contain-dir known-divergent passes on the natives (**css-contain
  7→11/12 on both** — the body-root `::before` box now places per
  inline-start semantics under rtl), fixed the inside-marker model
  (css-lists-3 §3.2: the marker is the item's first inline box, not a
  displacing sibling — plus Compose's alignByBaseline extent bug),
  landed Compose nested end-inset anchoring (two skeptic-caught
  regressions fixed pre-gate), and merged the spun-off shadow-offset
  session's PR (filter-effects Android 4→5/11). Two new sections at
  first capture: css-pseudo (the active-selection family — wall-or-bake
  candidate) and css-anchor-position (the anchored-geometry post-load
  eligibility extension is the queued cheap path). 24 sections, 277
  scored; zero regressions; 327-net clean. Totals: **web 231/277, iOS
  212/277, Android 204/277**.
  Wave 29 (`tools/titan/results/corpus-v5-4.json`) was the honesty
  triad. **css-pseudo web 4→7/7 (perfect)**: docs with an *unclosed*
  `<body ...>` start tag fell through `locateBodyContent` and rendered
  as a phantom 100×100 box — fixed with a guarded step 1.5 (non-markup
  masking + before-body prefix check), plus `&NewLine;`/`&Tab;` named
  references and a post-load `_pseudo`-bag re-derivation through the
  same extractFixture round-trip (class-only mutations now land).
  The four `active-selection-051..054` tests joined the new
  `REF_UNACHIEVABLE_TAGS` family (Rule 42 `browser-ref-divergent`):
  Chromium itself fails those reftests, so no runtime score against the
  ref is meaningful — unconditional exclusion, no stamp re-admits.
  css-anchor-position honest at 2/3 ×3: a crux probe proved Chromium
  never folds the anchor-center alignment shift into serialized insets
  (up to 50px), so the bake *cannot* deliver that family — the new
  `anchorInsetMismatch` guard bails (`anchor-inset-undeliverable`)
  rather than minting dishonest geometry; `calc(anchor())` cases do
  serialize as used offsets and pass. Android marker-row pitch fixed
  on-device (34→31px, matching web/iOS). selectors joined at first
  capture: 6/6/5 of 12, every failure a 0.95–0.999 near-miss veto (the
  `:dir()` family is queued diagnosis material). 25 sections, 279
  scored; zero regressions; 327-net clean. Totals: **web 241/279, iOS
  220/279, Android 213/279**. Seven web-perfect sections; six perfect
  on all three platforms.
  Wave 30 (`tools/titan/results/corpus-v5-5.json`) was the selector
  engine wave. **selectors: natives PERFECT 12/12 both, web 11/12**
  (from 6/6/5): the extractor gained `:dir()` (the HTML §3.2.6.4
  directionality ladder, including the document element) and the
  `+`/`~` sibling combinators; the post-load gate widened to a second
  route (any rule dropped as unsupported earns the computed-style bake
  — 4.9% corpus fire rate, measured); root-scope inherited properties
  bake down onto top-level body children (value-only,
  `body-inherited-baked` — the first cut slotted them under an unsized
  body-root, which both natives mis-rendered identically at 0.816;
  caught at the gate, reworked, re-gated clean). UA link styling bakes
  `#0000EE`+underline with per-property suppression when a dropped
  rule could target links (52 wrong bakes repaired, skeptic-found).
  The ref contract moved to `…-imgpad-htmlpins`: the `:where(body)`
  inherited pins clobbered refs declaring color/font/line-height at
  `:root` (29 refs, 23 moved; child-indexed-no-parent web now 1.0000
  pixel-exact). iOS line-box calibration pins composed text boxes to
  N × declared line box (device A/B over 300 docs: 117 improved,
  3 micro-regressions, paragraph pitch exactly 36.0). Native markers:
  `display:list-item` leading-line-box model (position-003
  0.93→0.995/0.993 both natives), Compose accumulated-position row
  rounding, direction-aware UA 40px list padding, bullet shapes at
  0.35em. A **fourth, per-platform exclusion family** landed:
  `requires-non-latin-font-parity` (Rule 43, css-counter-styles-3 §6
  table, author-shadow guard) excludes android/ios diffs only — web
  stays scored; css-counter-styles' native denominator honestly drops
  to 0/0 pending the font-boundary wave (bundle Noto faces in all four
  pipelines, then delete the rule). css-tables joined at first
  capture: web 10/11, iOS 7/11, Android 6/11 — the abspos-table head
  degrades gracefully without native table layout, exactly as the
  recon probe predicted. 26 sections; zero regressions elsewhere;
  327-net clean. Totals: **web 256/290, iOS 229/277, Android
  222/277**.
  Wave 31 (`tools/titan/results/corpus-v5-6.json`) brought CSS2 into
  the corpus and made **selectors the seventh all-three-perfect
  section (12/12 × 3)**. Span-tag forwarding: `<span>` left the
  generic-wrapper set, so a surviving span stamps its tag and web
  renders a real inline span instead of a block placeholder that
  split the line box — 377 fixtures gained the tag with zero property
  drift (deep-diffed), native inertness device-proven (41/41 Android
  captures byte-identical); it recovered dir-selector-change-001 on
  web. The inline-run reorder was measured NOT bounded (1,203
  components across 472 fixtures) — honest hard bail shipped; the v3
  `_runs` wire design is written into the extractor banner. **CSS2
  first capture: web 11/12, iOS 9/12, Android 5/12** — the same-wave
  §10.3.7 auto-margin work (AbsposAutoMargin twins; the skeptic
  Chromium-probed two spec bugs pre-gate — declared opposite margins
  dropped from free space, percent margins deleted opposite auto —
  both fixed to 14/14 Chromium parity) beat the recon's native
  ceiling on iOS, and lifted css-tables Android 6→8/11. The font
  boundary was **attempted and honestly declined**: measurement
  showed ref-only Noto pinning regresses iOS (Chromium-macOS and iOS
  resolve Arabic to the same face today — pinning the ref destroys
  accidental parity), the Armenian counter gap is one wrap point (not
  glyph ink), and neither native has per-character font-fallback
  machinery (Compose's is API 29 vs minSdk 24); Rule 43 stays intact
  with the real closing move specified in its banner. filter-effects
  web 5→6/11. 27 sections; zero regressions; 327-net clean. Totals:
  **web 269/317, iOS 238/289, Android 229/289**.
  Wave 32 (`tools/titan/results/corpus-v5-7.json`) landed the **v3
  `meta.runs` wire** — the inline anonymous-run box deferred since
  wave 21. An ordered `[{text}|{child}]` list rides the same meta hop
  as `_decorations` (schema + spec §4.1 with seven frozen rules + a
  new conformance golden decoded by all four codebases); the extractor
  emits it exactly where the old `inline-run-reordered` bail fired
  (1,138 components across 456 files; 69 keep the honest bail), web
  renders text runs as bare text nodes, and the natives consume run
  ORDER in plain block containers (order, not inline layout — scoped,
  null-fallback pinned). CSS2 static-inside-inline-001/-003 went
  0.956→0.999 on web with the green square byte-exact on the ref box,
  and **CSS2 Android jumped 5→9/12, level with iOS** — helped by the
  Compose table-model fix: TABLE_CELL/TABLE_CAPTION are block
  containers per css-tables-3 §2.1 (the cell re-entry had erased
  childless children into placeholders), implied-tbody splicing, the
  positioned-`<td>` containing-block mirror repair, and a fabricated
  8dp cell padding removed. iOS gained the zero-is-definite
  containing-block clamp (absolute-tables-015 0.904→0.997). css-tables
  now Android 9/11, iOS 8/11. absolute-tables-010 was **reclassified
  by evidence**: all three engines faithfully render an IR in which
  the extractor flattened the wrapper nesting — an extractor-structure
  item, not an engine defect. Post-load gained wedged-Chromium
  recovery. Operational contract: pre-`_runs` harness builds hard-fail
  the new meta key, so gates force fresh installs. 27 sections; zero
  regressions; 327-net clean. Totals: **web 269/317, iOS 239/289,
  Android 234/289**.
  Wave 33 (`tools/titan/results/corpus-v5-8.json`): **css-tables
  perfect on web (11/11) and Android (11/11), iOS 10/11**. The
  extractor's wrapper-nesting fix — an unclosed tag whose depth
  counter consumed the closer left the `-100px` wrapper childless, so
  the abspos table emitted as its sibling and no engine could compute
  the §10.3.7 static position; a conservative adoption gate (measured
  against the HTML-spec blunt rule, which loses content via the
  depth-5 recursion cap) re-parents it, and absolute-tables-010
  flipped on **all three platforms** with zero engine changes (corpus
  drift: 9 of 10,681 fixtures, no violations). The containing-block
  used-height channel landed on both natives (`AbsposCbUsedHeight`
  twins, CSS 2.2 §10.6.3 behind a 7-gate refusal table, abspos-only)
  plus a Compose CSS 2.1 Appendix-E paint-order fix —
  absolute-tables-007 flipped on both natives. The inline-block atom
  family joined the wave-20 inline flow (author `display:inline-block`
  boxes feed the pinned §9.4.2 packer): backdrop-filter-boundary
  +0.28 on both natives. The **inline-FC ROI was measured before
  building**: the native line-box gap is worth 4 cells, not 40 — of
  105 failing native cells, 4 are stacking-recoverable, 18 are
  common-mode with web (extractor-side splits behind the font wall),
  77 have no stacking term; the campaign will NOT build a native
  inline formatting context. One harness item queued from that
  diagnosis: composed-canvas document ROOTS stack in the harness's own
  Column. 27 sections; zero regressions; 327-net clean. Totals:
  **web 270/317, iOS 241/289, Android 236/289**. Eight web-perfect
  sections.
  Wave 34 (`tools/titan/results/corpus-v5-9.json`) was the maximum
  wave — nine builder lanes. **css-tables is perfect on all three
  platforms** (the iOS separated-borders track model with the §17.6.1
  2px UA band, arithmetically cross-checked against the converter's
  own serialized geometry; dynamic-001 iOS 0.9489→1.0000). The
  harness learned root row-flow (consecutive inline-level document
  roots pack per §9.4.2 instead of stacking in the canvas Column):
  static-inside-inline-block and clip-rect-2 flipped on both natives,
  each landing at its probe ceiling exactly — CSS2 natives 10/12,
  filter-effects natives 6/11. The backdrop edge model was corrected
  against browser ground truth (Chrome mirrors at the sample edge;
  the wave-26 grow-by-3σ decision had never checked a browser) — the
  lime-bleed signature now matches Chrome to 0.2. The font boundary
  shipped honestly asymmetric: per-script run-split fallback + five
  checksummed Noto faces on both natives, enabled on Android
  (measured +0.14), disabled on iOS (measured −0.31 — its system
  faces are already metrically Noto); the '10+' family does not
  converge, so Rule 43 stays intact and no ref-rev was bumped. The
  `meta.fontFaces` wire landed end-to-end (schema + golden × 4
  codebases + the web /wpt-font/ route); the section-pipeline hop is
  the named next step. `_runs` generalized to every text+child
  interleave (935 files, addition-only proven); `:has()` went static.
  Two sections joined: **css-cascade 10/10/10** and
  **css-writing-modes with web perfect 12/12** at first capture.
  Depth honesty, measured web-only: at --max-tests 24 the seven
  perfect sections hold 67/82 in the tail — only css-position stays
  perfect; the gate is NOT expanded until natives are measured at
  depth. The transforms premise was refuted (the real gap is
  css-transforms-1 §3 transform-as-containing-block, designed for
  wave 35), and the two natives' out-of-flow rule tables were found
  to have diverged (parity item). 29 sections; zero regressions;
  327-net clean. Totals: **web 292/326, iOS 262/313, Android
  254/313**. Nine web-perfect sections.
  Wave 35 (`tools/titan/results/corpus-v6-0.json`) was the 20× wave,
  and it changed what a wave is. **The corpus boundary moved to
  depth-48**: 29 sections × 48 bucket-A tests on all three platforms
  (v5.x sampled 12) — totals **web 1054/1333 (79.1%), iOS 869/1305
  (66.6%), Android 815/1302 (62.6%)**, the honest full-depth numbers
  the wave-34 depth probe predicted. The wave also shipped the
  **first full-corpus web map**
  (`tools/titan/results/webmap-v1.json`): all 65 bucket-A sections,
  10,201 tests scored web-only at full cap — **7,138 pass (70.0%)**
  — with 1,164 of the 3,060 failing cells mechanism-classified
  (`webmap-v1-classification.json`) and the top mining opportunities
  ranked (object-fit 154 cells, grid-lanes 384, size containment 104,
  print/paged 136, gap decorations 76…). Nine builder lanes landed:
  the css-transforms-1 §3 transform-as-containing-block rule
  (**Android css-transforms 10→12/12 device-confirmed**, iOS 12/12),
  the §3 singular-transform rule, the Appendix-E product un-gate,
  fontFaces delivery end-to-end + the font-shorthand root bake
  (boundary-shaping now renders its downloaded serif — and honestly
  scores *lower*: the wall moved from font-not-delivered to real
  shaping divergence), the multicol Android color triad (red→green),
  a software gaussian (per-tile MAE 6.2→0.16), web inline tags (+17
  web passes), vertical-text-flow twins, and depth-tail fixes
  (fieldset/legend +0.20). The 327-net went red once at the gate — a
  placeholder-floor ternary in the web harness flipped by the widened
  Max*/Min* percentage wire — root-caused by live DOM probe, fixed
  size-derived-only with a control run proving the gate, 109/109 web
  captures byte-identical. Known opens recorded: the committed iOS
  baseline for 004_Sizing_MaxWidthPercent carries no box
  (pre-existing), and basic-blur's capture moved deterministically
  under the runtime-wide gaussian fix. Zero regressions elsewhere.
  Wave 36 (`tools/titan/results/corpus-v6-1.json`) mined the map —
  eight lanes on the ranked opportunities. **Object-fit** (#1, 154
  cells): the full image-delivery chain landed (extractor
  `meta.attrs.src` replaced-element lane, the `/wpt-image/` route,
  an `img.decode()` capture gate, schema-additive across all four
  codebases) plus a converter `<position>` grammar rewrite — the old
  parser read tokens positionally, so `top right` reached the
  runtimes axis-swapped in six of every seven boxes across 132
  tests: css-images full-cap 136→266/365. **Shape-outside** was
  never emitted on web — emission + a shaped-float gate: css-shapes
  33→83/102. **Size containment** fixed at the extractor
  (css-contain +45 full-cap); the quote-depth bake landed;
  the widget 0.9782 cluster (progress/meter appearance) fixed; the
  **zoom** property landed on web *and then converged on both
  natives* after the dark-stage net went red exactly as predicted —
  real measure-then-scale zoom triplets, cross-pairs ≥0.99, nine
  Zoom baselines + the iOS 004 divergence repaired (the only
  baseline changes). The print/paged decision was **measured, not
  assumed**: `requires-print-medium` stays scored; the `border="1"`
  presentational-attribute mapping recovered the non-paged print
  failures instead; two deliberate honest cell losses shipped
  (cancelling-error scoring lies repaired). The monospace-quirk
  glyph-advance fix lifted css-overflow natives +6/+9. Grid-lanes
  (384 cells) is confirmed masonry — a feature brief, not a bug
  hunt. Depth-48 gate: zero regressions, net clean. Totals:
  **web 1079/1333 (81.0%), iOS 880/1305 (67.4%), Android 835/1301
  (64.2%)**. css-grid web perfect 48/48 at depth.
  Wave 37 (`tools/titan/results/corpus-v6-2.json`) answered the
  map's biggest question: **the 384-cell grid-lanes pool is the
  browser's ceiling, not ours** — Chromium-151 itself does not
  implement `display: grid-lanes` (the refs hand-build lanes geometry
  from supported CSS), and a 437-cell ceiling measurement showed our
  captures sit at the Chromium ceiling with a median gap of 0.0008.
  The exclusion was **refused** on precision (0.842, would cost 48–54
  passing cells) — Rule 44 ships informational,
  denominator-neutrality proven by A/B replay over 11,534 frozen
  cells. The view-transitions exclusion was refused the same way
  (0.74), with the closing move designed (a VT capture mode). What
  did land: the near-miss sweep (+27 depth-48 web cells, 0 lost —
  and the cascade counter/at-rule machinery reached the natives:
  **css-cascade +11/+11/+10 on all three**), `line-clamp: auto`,
  gradient hue-interpolation (css-images web +11 at the head),
  clip-path `at <position>` + fill-rule (converter 258→317 tests),
  the **lang wire** end-to-end, vertical-rl root propagation on web
  (wm-propagation 0.808→0.897), and device-verified iOS
  overflow-wrap rules — after correcting the wave-36 hyphens
  taxonomy, which had the direction backwards. Known artifacts:
  webmap-v1 is a stale-refs baseline for css-shapes (45/102 ref inks
  changed under htmlpins); the 22-cell image-set residue is a corpus
  sparse-checkout gap, not code. Depth-48 gate: zero regressions,
  net clean. Totals: **web 1120/1333 (84.0%), iOS 890/1305 (68.2%),
  Android 847/1302 (65.1%)**.
  Wave 38 (`tools/titan/results/corpus-v6-3.json`) was the native
  wave — eight lanes on the web-native gap, and the corpus grew to
  **30 sections**: css-view-transitions joined via the **sixth bake**
  (`view-transition-bake.mjs` drives each transition to its frozen
  state in the ref browser and serializes the settled
  `::view-transition` pseudo tree as ordinary boxes; measured
  +21/−1 web, 50→70/192, then pinned into section-runner per the
  engagement-never-ambient principle). **iOS gained +45**: css-tables
  25→36 (the UA display channel + shrink-to-fit + separated-tracks
  completion, device-verified twice independently with zero losses),
  css-sizing +8, css-ui/css-values/text-decor singles. **Android went
  red first, then finished +27**: the unverified Compose table fold
  had lost 13 cells on a false premise (the extractor never emits an
  implied `<tbody>`; the table path is a rewrite only total for
  canonical trees) — the fix landed the structural precondition,
  stopped folding orphan rows, and removed a fabricated 1dp demo
  cell border with no CSS basis; all 13 restored plus a masked
  css-backgrounds loss the fix lane found itself. Six lanes died on
  API disconnects mid-wave and were recovered by the
  assess-and-finish pattern with zero work lost; the scoring harness
  itself was hardened when a fix lane caught its local pass rule
  skipping the veto predicates. Quick wins: the `/images/`
  sparse-checkout gap closed (69 stale refs recaptured with assets),
  conditional `@import`, the PositionParser migration, native lang
  locales. Depth-48 gate: zero regressions, net clean. Totals:
  **web 1143/1379 (82.9%), iOS 935/1351 (69.2%), Android 874/1347
  (64.9%)**.
  Wave 39 (`tools/titan/results/corpus-v6-4.json`) was the Android
  wave, and it **closed the Android–iOS gap to 0.4 points** (was
  4.3). The centerpiece: the 1px half-leading cluster's root cause is
  a *rounding-tie asymmetry* — Blink rounds the positive ascent
  magnitude (`floor(a+0.5)`) while Android's Paint rounds the
  negative scalar (`= ceil(a−0.5)`); the two differ only at exact .5
  ties, and Inter's ascent (1984/2048) lands on a half-integer
  exactly at 16px, the corpus body size. `HalfLeadingBaseline`
  corrects the draw-time delta behind a ±2px refusal guard;
  the predicted +59 flips verified at the gate across 16 sections
  (css-tables +10, css-ui +8, overflow +5…). The wave also survived
  two instrument events, both resolved with evidence: a pool-emulator
  flake killed two Android section feeds (re-fed clean), and nine
  multicol captures vanished — root-caused **from disassembled
  bytecode** (SubcomposeLayout's measure policy throws on all
  intrinsic queries; a new flex auto-min mechanism probed them), and
  the offending mechanism was gated off after an isolation A/B
  proved it had won zero cells (its apparent gains were the
  half-leading uplift — a false attribution the two-APK experiment
  corrected). Also landed: the native image hop (feeder asset bundle
  + img rendering through the object-fit appliers; SVG stays the
  honest wall), the css-overflow one-defect fix (+5, the monospace
  hypothesis refuted by measurement), css-values web 37→42, the
  direction body-root minting, and honest-flat VT mining (four real
  defects fixed; three vacuous passes traded for three earned ones).
  Depth-48 gate: zero regressions, net clean. Totals: **web
  1151/1379 (83.5%), iOS 939/1351 (69.5%), Android 931/1347
  (69.1%)**.
  Wave 40 (`tools/titan/results/corpus-v6-5.json`) was the ICB wave.
  The centerpiece is a **15-wave-old harness bug**: the composed
  capture canvas ICB was a plain block, so root-level margins
  collapsed *through* it and transformed/filtered roots painted
  against a shifted origin; one line — `display: flow-root` on the
  composed ICB — makes it a formatting-context root exactly like the
  WPT harness viewport. css-transforms web 33→47 (+14), filter-effects
  31→40 (+9), zero regressions corpus-wide, pinned by a dedicated
  harness test. Also landed: the will-change list-wire rewrite (every
  `will-change` had been silently dropped), `transform: inherit`,
  gradient angle strictness (css-images +3 web, +1/+1 native),
  `hyphens: auto` switched on for both natives (Minikin dictionary
  hyphenation on Android, soft-hyphen insertion on iOS; css-text iOS
  +3), the two-colour view-transition bake (css-view-transitions +1 on
  all three), and iOS UA-element font + border-box floor rules. The
  standing multicol hang premise was **refuted** — IntrinsicChannel
  (PR #111) already guards it, 48/48 captures clean — and the SVG
  pre-raster hop was built but ships **default-OFF**: it would add 8
  vacuous passes (honesty finding). One honest −1: css-sizing Android
  `aspect-ratio/abspos-016` (0.997→0.9525, reproducible AA drift on
  the API-36.1 emulator image, not a code regression). The gate itself
  consumed ~30 hours before its root cause fell: **on API-36.1
  emulator images, shell-created dirs under the app's external files
  are invisible to the app's FUSE view** — the screenshot dir fails
  the app's own `canWrite` probe and a shell-created inbox cannot be
  *listed*, so the app polls forever and every fixture times out with
  zero PNGs. The feeder now lets the app create its own dirs first and
  waits (loudly, bounded) for the app-created inbox before pushing;
  fonts/images subtrees remain shell-created — a known residual hazard
  on such images. Lesson pinned: verify *capture counts*, never
  process liveness. Depth-48 gate: zero regressions, net clean.
  Totals: **web 1180/1379 (85.6%), iOS 946/1351 (70.0%), Android
  936/1350 (69.3%)**.
  Wave 41 (`tools/titan/results/corpus-v6-6.json`) was the skeptic
  wave: seven builder lanes, then six adversarial skeptics who had to
  *execute* their refutations. The web gain is all view-transitions —
  measuring wave-40's two-colour extension at full cap (+3) exposed
  the **frame-ring defect**: fifteen tests failed on nothing but the
  16px pad ring because the bake never delivered the settled
  `::view-transition` background to the body-root channel all three
  composed canvases pad from; the stamp (byte-identical for white
  rings, skeptic-verified) plus two VT2 guards took full-cap web from
  70 to **90/192** and the gate sample from 23 to 30. Android +12:
  css-overflow +5 (the cross-component line-clamp cap — a skeptic
  reproduced its geometry from the real code and simulated all 15
  fire-cases before the gate ran), css-text +3, view-transitions +4,
  and the wave-40 `abspos-016` loss recovered by the instrument fix.
  iOS +7: the pre-break was blind to in-word break opportunities
  (soft and literal hyphens), sizing boxes to fewer lines than
  TextKit then rendered. The instrument work proved the API-36.1
  shell-dir invisibility extends to **direct-path opens** on the
  fonts/images channels (uid evidence; the app now creates all asset
  roots itself and the feeder refuses to push until they exist), and
  the SVG pre-raster A/B verdict came back: **default stays OFF** —
  it costs 7-8 passes per platform until replaced boxes learn CSS2
  §10.4 constraint sizing. Skeptics found one real missed fix (the
  positionOffset resolve gate lacked the new color-matrix consumer),
  the gradle filtered-run-state hazard (a plain suite run after a
  `--tests` run silently re-runs only the filtered class), and one
  honest −1 shipped knowingly: counter-styles `broken-symbols`
  Android (0.9527→0.9481), named at-risk pre-gate — markers now
  inherit the document's 16px Inter per css-lists-3 §3.2 instead of
  an off-spec 14sp, and the old pass was metric-lucky. Depth-48 gate:
  all capture columns full, zero watchdogs, 327-net clean. Totals:
  **web 1187/1379 (86.1%), iOS 953/1351 (70.5%), Android 948/1350
  (70.2%)**.
  Wave 42 (`tools/titan/results/corpus-v6-7.json`) was the dead-wire
  wave — nine builder lanes, six executed-repro skeptics, four fix
  lanes. The headline: ordinary-element `::before`/`::after` baked
  text was **dead code on the v2 wire in both native core renderers**
  (Compose read a channel no v2 document emits — its own TODO said
  so; iOS had no seam at all). Wiring it delivered css-lists Android
  +9 / iOS +11 and counter-styles +1/+1. `calc-size()` went from
  Generic-unmapped (Android collapsed flex items to nothing) to a
  typed wire evaluated on all three platforms: css-values iOS +8,
  Android +2. CSS2 §9.5.2 float clearance landed as byte-parallel
  twins (identity unless every simulated position is exact): CSS2
  +5/+4. Plus the line-clamp two-value grammar, the Android
  border-radius self-paint split (`Modifier.clip` clips children;
  §4.3 doesn't), abspos shrink-to-fit, and the WOFF→TTF host hop — a
  deliberate zero-flip fidelity fix that ends the sans-serif
  fallback for every corpus font. The skeptics earned their keep:
  one **ship-stopper caught pre-gate** (a multicol intrinsic probe
  width 3.8× over Compose's Constraints packing limit — the wave-39
  capture-death class), the §10.4 row-9 mis-transcription in both
  twins, a pseudo `display:none` that would have painted the word
  FAIL on a WPT test while the metric said PASS, the padded-float
  clearance hole, and a WOFF cache that would have shipped a
  truncated font forever after one interrupted run. The SVG
  pre-raster A/B re-ran post-§10.4: default stays OFF — the blocker
  moved to inline-atom flow not admitting replaced images. One
  instrument flake at the gate (the known adb-pull truncation,
  single-fixture refeed recovered it at 0.9967). Depth-48 gate: zero
  watchdogs, all columns full, audited baseline refresh (18 radius
  captures moved toward the ref under self-paint) + 327-net clean.
  Totals: **web 1187/1379 (86.1%), iOS 977/1351 (72.3%), Android
  970/1350 (71.9%)**.
  Wave 43 (`tools/titan/results/corpus-v6-8.json`) was the hardening
  wave, and its corpus delta is **zero flips — stated plainly**. What
  moved sub-threshold: the discard-multicol family gained +0.02–0.03
  on both natives (the `lh` unit now resolves against the used
  line-height instead of a hardcoded 1.2, and iOS finally threads
  `continue: discard`); `<ol start>`/`<li value>` ordinals are live
  and capture-verified on both natives (the residual is the `reversed`
  attribute, which the producer never puts on the wire). The converter
  inherit fix reached the gate IR but pixels stayed flat — exposing
  **the inline-run stacking wall** (`meta.runs` inline content renders
  as stacked blocks on both natives) as the single recurring residual
  behind inherit-computed, before-as-flex, the hyphens-manual-inline
  family, and iOS raw-text multicol: the named wave-44 headline
  target. Correctness and instrument work that carries no score claim:
  CSS opacity no longer clips on Android (the `Modifier.alpha`
  clip=true physics, bytecode-proven, replaced with an unbounded
  save-layer group), styled pseudo text (em-based sizes, colors; the
  iOS refusal doctrine relaxed only for spec-dead declarations),
  per-test fontFaces scoping (a face-parse crash can no longer poison
  sibling tests), the UA-styled-tag scope on the inherit drop (on
  web, absence ≠ inherit when the UA sheet is an origin), a
  negative-ordinal crash guard, and the reversed-anchor comments
  inverted to the truth — a skeptic adjudicated the code
  spec-correct per css-lists-3 §4.4.2 and the comments claiming
  "ref-pinned, not spec" were the defect. The replaced-image
  inline-atom predicate shipped dormant after the wave-42 layer
  diagnosis was corrected (the box-sizing images are document roots),
  and the SVG pre-raster A/B re-ran: still OFF. Depth-48 gate: zero
  watchdogs, all columns full under a tightened per-section capture
  check, 327-net clean. Totals: unchanged — **web 1187/1379 (86.1%),
  iOS 977/1351 (72.3%), Android 970/1350 (71.9%)**.
  Wave 44 (`tools/titan/results/corpus-v6-9.json`) was the inline-fold
  wave. The **SVG pre-raster flipped ON** — after the root-facade
  replaced-image wiring and the §10.8.1 margin-baseline math landed,
  the A/B measured on-device said arm B stopped costing passes
  (Android 15/19 vs 8/19; a skeptic independently reproduced the
  numbers from the captures) — and the gate delivered exactly the
  measured css-ui gains: **Android +7, iOS +8**. The inline-run wall
  half-fell: the Compose fold (a 298-host simulation proved exactly
  the five failing victims fold) flipped two css-text cells and moved
  inherit-computed +0.06; the iOS fold engaged partially — its gate
  list is stricter, wave-45 widens it to parity. Two cells were lost
  **honestly**: align-items-007's passes on both natives were vacuous
  (absent image + white-canvas slack); the now-delivered raster
  reveals a real color divergence the flip's skeptic had flagged
  pre-gate. One web cell is the known view-transition settle flake.
  Reversed lists now count down end-to-end (wire, both decoders,
  schema, and the counter bake's spec-walk replacing a wave-37
  reference-fitted formula). The float strip improved all eight
  Android floats-multicol cells without flipping any — wave 45 has
  capture evidence where wave 44 had hand geometry. Skeptics caught
  and fixes closed pre-gate: a post-load truth-regression (class
  *removals* baked stale styles), a double-measure crash, and a batch
  of comment-truth corrections. Operationally the gate hit a full
  disk mid-run (the wave-38 lesson unapplied) — recovered and the
  df preflight is now part of the gate template. Rerun gate: zero
  watchdogs, all columns full, 327-net clean. Totals: **web
  1186/1379 (86.0%), iOS 984/1351 (72.8%), Android 978/1350
  (72.4%)**.
  Wave 45 (`tools/titan/results/corpus-v6-10.json`) was the
  convergence wave. Two lanes independently measured the same +16px
  composed-canvas offset behind two failing families — the harness
  em-margin resolver bailed on any root margin in `em` without its
  own font size, emitting the UA gap *and* the full declared margin.
  One four-rung resolution ladder on both harness stackers flipped
  the floats-multicol family (**CSS2 Android 31→39**) and, with the
  em-basis and iOS discard threading, the discard-multicol family
  (**css-overflow +4/+4**). The iOS inline fold reached Compose
  parity (css-text iOS +1), the Compose atom ring landed, and the VT
  flake hardening — root-caused as *two* mechanisms and verified by
  fault injection — paid immediately (web +1, the settle cell
  recovered at this very gate). The Noto pilot answered the
  three-wave-old Rule-43 question by measurement: **net zero** new
  passes from bundling fonts — the exclusion is vindicated, and the
  real constraints are named (Compose line-pitch drift ~1px/row, CJK
  wrap divergence). Operationally the gate exposed a provisioning
  wedge (reinstall without `pm clear` breaks the app's external dir
  on API-36.1 — the first seven sections produced zero Android
  captures with the feeder's loud-fail working exactly as designed);
  the provision script now clears app data after every install, and
  the refeed recipe learned it must carry `--wpt-dir`. Totals: **web
  1187/1379 (86.1%), iOS 990/1351 (73.3%), Android 991/1350
  (73.5%)** — both natives cross 73%.
  Wave 46 (`tools/titan/results/corpus-v6-11.json`) was the
  clean-sweep wave: **zero regressions anywhere**, and every lane's
  projection landed within a cell. The clip-path reference box
  (css-masking-1 §7.1 geometry-box forms, ignored on both natives —
  Android rendered such elements *unclipped*) delivered css-masking
  **+5 / +11** exactly as projected, after a skeptic caught a
  regression-grade defect pre-gate (the clip read its collapsed
  margin through a `composed` modifier that materialises inside the
  provider nulling it — three ellipse tests would have rendered
  empty). The iOS line-clamp twin shipped with a line-box census the
  Compose model lacks (css-overflow iOS +5, the 005/006/007 trio
  protected); iOS gradient machinery landed repeating periods and
  hue-arc interpolation (css-images +2/+4); clone decoration closed
  four css-break cells, and the catastrophic 0.14 pair was
  reclassified as the vertical-writing-mode transposition wall.
  inherit-computed-001 finally flipped on both natives — the UA
  paragraph margin resolving `1em` against 16px instead of the
  element's own 19.2px was the entire residual. The Android abspos
  static-position fix (consecutive abspos siblings were stacking)
  paid across five sections, including the three filter-effects
  cells the wave-41 backdrop work had waited on — and moved the
  ring-fenced `backdrop-filter-basic-blur` render by generic
  mechanism (0.915→0.899, still failing; reported plainly, no
  carve-out). The line-pitch drift was fixed where it exists but
  honestly found *not* to be the non-Latin family's constraint; the
  monospace font-metric wall was measured (nine face-only cells) and
  its pilot ships OFF pending two named seams. First fully clean
  gate in four waves. Totals: **web 1187/1379 (86.1%), iOS
  1013/1351 (75.0%), Android 1008/1350 (74.7%)** — iOS crosses 75%,
  Android crosses 1000 passes.
  Wave 47 (`tools/titan/results/corpus-v6-12.json`) was the pin wave.
  The **monospace font pin earned its default flip** — both measurement
  seams closed (`ch`, the pre-break, and the line grid all measure the
  registered document face now) and the on-device A/B delivered
  exactly: css-text iOS +8, css-overflow +6/+3. The flex lane
  *revised its own brief*: the real defect was `align-self: auto`
  never resolving to stretch (css-align-3 §6.4) — the prescribed fix
  would have broken 13 passers and was measured out; the correct one
  fired on exactly nine cells (css-values Android +7). css-gaps
  +3/+8 came from layout distribution (per-line justify/align-content
  and a sizeThatFits starvation bug), the Android gradient pipeline
  landed byte-parallel with wave-46's iOS twins (+3, both natives
  pinned against the same frozen Chromium pixels), and the Rule-43
  mechanism was **named and fixed**: a baseline-claim channel split
  between the marker's direct Text and the item's wrapper-folded
  half-leading translation. A skeptic caught the vertical seam firing
  on four passing select-appearance cells pre-gate (text-only children
  carry no Display wire); the guard landed and all four held. The
  gate's own crash — the Constraints packing family again, fed by
  transposed vertical sizes into an Infinity band — is fixed and
  pinned in IntrinsicChannel; two fixtures still wedge post-fix
  (a layout hang and a corrupt-encoder render) and ship unmeasured as
  the named wave-48 item. The iOS view-transitions column was lost to
  sim-pool exhaustion and recovered by section refeed. Totals: **web
  1187/1379 (86.1%), iOS 1038/1351 (76.8%), Android 1029/1349
  (76.3%)**.

## Known-broken: iOS harness cold build

`xcodebuild -target StyleConverterTest -sdk iphonesimulator -arch arm64` fails
from a COLD build directory with a swift-frontend crash (Swift 6.3.3):

```
While evaluating ASTLoweringRequest (Lowering AST to SIL for StyleConverterRuntime)
While silgen emitFunction for 'styledContent(now:)'
  at runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/ComponentRenderer.swift:835:13
```

Release config (`-O -enable-default-cmo`); the stack dump shows a very deep
nested `struct_type` chain of Applier types — the SwiftUI modifier-chain type
blowup tipping the optimizer over.

It is **pre-existing** (reproduces at ba92a4e3 with no local changes) and it
is **configuration-specific, not warmth-specific**. Corrected 2026-08-27 —
the earlier note here said warm incremental builds were why local runs
survived. They are not: `test-all.sh` does `rm -rf StyleConverterTest.xcodeproj
build` at the top of every iOS phase, so every harness run is already COLD.

What actually separates the two is the optimiser. Measured on Swift 6.3.3,
both from a cold `build/` and a cold generated project:

| invocation | flags | result |
|---|---|---|
| `xcodebuild -target StyleConverterTest -sdk iphonesimulator -arch arm64` | `-O -enable-default-cmo` (Release, the default) | swift-frontend crash |
| the same plus `-configuration Debug` (what `test-all.sh` passes) | `-Onone -disable-cmo` | builds, ~20 s |

So the capture pipeline is not blocked by this, and never was; a CI runner
hits it only if it builds Release. The crash is real and still unfixed — the
deep nested `struct_type` chain of Applier types tips the optimizer over — but
scope it as "Release builds of the runtime are broken", not "the harness
cannot be built".

## Fixed: iOS rendered out-of-range filters into Display P3

**Fixed 2026-08-27** in `runtimes/swiftui/.../Renderer/CaptureColorSpace.swift`;
the history below is kept because the negative result at the end is still
load-bearing.

The colour-space tripwire added in the measurement campaign fired on its
first large run. Of 359 iOS captures, exactly ONE carried colour chunks:

```
088_Filter_Brightness.png  ->  iCCP "kCGColorSpaceDisplayP3" + cICP(primaries=12)
```

All 358 others are bare IHDR/IDAT/IEND. The trigger is narrow and specific:
`filter: brightness(1.5)`. Blur, grayscale, sepia and contrast+saturate all
stay sRGB — only the filter whose result exceeds 1.0 promotes the render to an
extended-range wide-gamut context, which ImageIO then tags.

This matters because nothing downstream is colour-managed: pngjs discards
profile chunks without applying them, so those pixels would have been compared
as sRGB. `normalize-pngs.mjs` now REFUSES the file instead of silently
stripping the tag, and the comparator exits 3.

**It does NOT explain the Filter_Brightness divergence.** Converting the iOS
capture P3 -> sRGB before scoring moves deltaE95 only 18.697 -> 17.385, while
Android-vs-web on the same component is 0.373 mean / 0.000 p95. So iOS is the
lone outlier on the filter maths itself; the P3 tag is a separate, smaller
defect that happens to share a trigger. Both are real; neither is the other.
**The filter-arithmetic half remains open** — SwiftUI's `.brightness(_:)` is
an additive shift where CSS `brightness()` is multiplicative
(`StyleEngine/effects/filter/FilterApplier.swift` maps `pct` to
`(pct - 100) / 100`), which is the likely root cause and is still unverified.

### The fix

`ImageRenderer.uiImage` chooses its destination colour space from the
CONTENT: sRGB while the render stays inside [0,1], and a wide-gamut
extended-range space once it does not (Display P3 on the simulator,
extended sRGB under Mac Catalyst). `CaptureColorSpace.capture` now checks
that choice — `isWideGamutRGB` — and re-renders through an explicit sRGB
`CGContext` only when the framework promoted.

The gate is narrow on purpose. Re-rendering EVERY capture through a
constructed sRGB context also satisfies the tripwire, but it is a different
rasterisation: measured on this fixture it moved **355 of 359** captures,
mostly 1-2 LSB on antialiased edges but up to 202/255 on gradients, shadows
and blends, and it shortened five captures by 1 px (its context rounded to
nearest where `uiImage` ceils). The harness is byte-deterministic — two runs
of identical code gave 359/359 pixel-identical captures — so that drift would
have been a real regression against every committed baseline.

Measured result of the shipped fix on `fixtures/visual-test-controls.json`:
**358 of 359 captures byte-identical** to the pre-fix run, the 359th being
`088_Filter_Brightness.png` itself, which now encodes as
`IHDR sRGB eXIf pHYs iDOT IDAT IEND` like every other capture and scores
SSIM 0.96 / 0.95 / 0.97 across the three pairs. Pinned by
`CaptureColorSpaceTests` in the swiftui runtime suite (6 tests; the two
defect tests fail against the pre-fix code).

One correction to the original report: the `eXIf` and `iDOT` chunks are NOT
specific to the offending capture — every iOS capture carries them before
normalization. Only `iCCP`/`cICP` were ever the signal.

## Control-fixture findings (3-platform, 2026-08-27)

`tools/visual/gen-control-fixture.mjs` derives `X__no_<decl>` controls from
`fixtures/visual-test.json` — each is X with exactly one declaration removed,
so case and control differ if and only if that declaration has a visible
effect on the platform that rendered them. **Zero difference is the finding.**
359 components captured on all three platforms; numbers below are differing
pixels, case vs its own control, within one platform.

### Runtime gaps

| declaration | iOS | Android | web | reading |
|---|---:|---:|---:|---|
| `backdrop-filter: blur(10px)` (Glass_Effect) | 0 | 0 | 264 | NOT a gap — capture-mode gated, see below |
| `perspective: 500px` (Perspective_Rotate) | 2209 | ~~**0**~~ **1802** | 2097 | FIXED 2026-08-28 — was dropped on Android |
| `transform: rotateY(30deg)` (same component) | 3082 | 2709 | 2840 | applied everywhere |

**CORRECTED 2026-08-28.** `backdrop-filter` is NOT dropped. It is implemented on
both natives (wave 26 above: two-pass controlled-canvas model, iOS validated
against browser refs at 0.966 / 0.978 / 0.999). What the control measured is a
CAPTURE-MODE gate: the two-pass path arms only on the COMPOSED capture path and
is deliberately disarmed on the per-component path these runs used, to keep the
committed 327-pair baselines byte-identical. Android gates on
`composedMode && documentDeclaresBackdropFilter` (ScreenshotCaptureScreen.kt:309);
iOS says so outright in ScreenshotCaptureView.

The real gap is in the HARNESS, not the runtimes: composition-test.json was
authored precisely so backdrop-filter has a non-uniform backdrop, and test-all.sh
has no way to arm composed capture — so the ordinary fixture pipeline cannot
exercise the property on either native. Worth closing, but it is a capture-path
gap, not a missing applier.

Also worth keeping: this is the second finding this campaign that looked like a
native gap and was not (the other being the under-size trio, which is placeholder
scaffolding). A control proves a property had NO VISIBLE EFFECT on a platform; it
does not prove WHY, and the why has been different every time.

`perspective` is FIXED. Android applied the rotation but ignored the
perspective, so 3D transforms rendered flat. `applyTransformFunctions` --
the branch taken whenever a transform list is present -- sourced perspective
only from the `transform: perspective()` FUNCTION and never read the
standalone property; the branch that did read it only runs when there are no
transform functions, i.e. exactly when perspective cannot matter.

Worth keeping for the pattern: the extractor was already unit-tested and
green (`Perspective {px:500}` -> `config.perspective == 500f`), and the
cross-platform gate never flagged the component -- it scores SSIM 0.9558 /
0.9979 / 0.9563 and was above threshold before the fix too. A passing unit
test plus a passing similarity gate, and only the control saw it.

### A divergence in the other direction

`vertical-align: sub` / `super` is set on a BLOCK-level component. Per CSS it
applies to inline-level and table-cell boxes, so it should do nothing. web does
nothing. Both natives grow the box, and disagree about how much:

    Typography_Subscript    iOS 390x81  ·  Android 390x85  ·  control 390x77
    Typography_Superscript  iOS 390x81  ·  Android 390x83  ·  control 390x77

web is correct; both natives apply it, by different amounts.

### Fixture weaknesses, now measured rather than asserted

`overflow` / `text-overflow` / `white-space` on TextOverflow_Ellipsis are 0 on
ALL three — the text does not overflow its 150px box, so none of the three
declarations can do anything. `z-index: 10` sits on a lone component with no
sibling to stack against. 6 of 24 `font-size` declarations set 16px, the
initial value. `border-top-right-radius: 0` and `border-bottom-left-radius: 0`
are likewise the initial value. `font-stretch` is inert on all three by
design — its web applier is an explicit parity no-op.

Run control fixtures with `NO_CROSS_PLATFORM_GATE=1`: case and control are
*supposed* to differ, so scoring their cross-platform pairs against the
conformance ledger asks the wrong question.

### The three Android under-size components are a floor bug, not a box-model bug

`Button_Outline`, `Input_Field` and `Edge_DeepNesting` render 2x(border-width)
shorter on Android than on iOS/web. First recorded as an under-size bug; two
measurements narrow it considerably.

With real text pinned (the control fixture), removing the border changes the
Android box by EXACTLY twice its width — Input_Field 54 -> 52 at 1px,
Edge_DeepNesting 52 -> 48 at 2px. So the border box is computed correctly
whenever CONTENT determines the size. And Input_Field's explicit
`width: 200px` is exact on all three platforms; only its unconstrained,
floor-driven height is short.

The defect is therefore in `StyleApplier.placeholderFloorMinSize` /
`borderBoxFloorMins`, which converts web's border-box `minHeight: 30px` into a
Compose content-box `defaultMinSize` by subtracting padding and the border
band. That floor exists ONLY because `fixtures/visual-test.json` has no text —
it is harness scaffolding matching web's scaffolding, not product behaviour.
Components with borders but no floor-driven height (Border_Solid 3px,
Border_Dashed 2px, Border_Mixed per-side, Button_Primary) are all exact.

Handle with care: the same arithmetic produced +2px regressions twice before,
in wave 1 (Input_Field / Glass_Effect) and wave 4 (every Decorated row). Change
it with a measured before/after, not by reasoning about the model.

## The harness's A/A noise floor is zero (2026-08-28)

Every threshold in the visual pipeline — SSIM ≥ 0.95, Δpx ≤ 2% — was
picked by eye, and both gates that use them carried the same caveat in
their source: the harness's own run-to-run variance had never been
measured. That caveat was load-bearing. It was the stated reason the
cross-platform gate reported stale expectations as *warnings* rather
than failing on them.

`tools/visual/noise-floor.sh` measures it. The method is an A/A study:
run the same code against the same fixture more than once and compare
the runs. Anything that differs is pure harness noise, because nothing
else changed. Each run is a full `test-all.sh` — fresh convert, fresh
iOS build (it `rm -rf`s the xcodeproj and build dir), fresh emulator
boot (`-no-snapshot`), fresh install, fresh capture — so the
independence matches what a human re-running the suite actually gets.

Result:

| fixture | captures | runs | outcome |
|---|---:|---:|---|
| `fixtures/visual-test.json` | 327 | 2 (web 3) | byte-identical |
| `fixtures/composition-test.json` | 96 | 2 | byte-identical |

**423 captures, bit-for-bit identical across independent runs, on all
three platforms.** Not "small noise" — zero. SSIM = 1.0000, Δpx = 0.00%,
ΔE = 0 between any two runs, by construction.

### What it licenses, and what it does not

It licenses promoting stale expectations from warning to failure
(exit 5, `EXIT_STALE_EXPECTATION`). The fear was that a pair sitting at
0.9499 would flap across the line run-to-run and a stale failure would be
indistinguishable from a real fix. A metric computed from identical bytes
is identical, so that flapping is not rare here — it is impossible.

It does **not** license loosening anything, and it does not mean the
metrics are well-calibrated. Two separate limits stand:

- **Scope is same-machine.** Cross-machine variance — a different
  runner, Xcode version, or emulator image — is unmeasured. Device-level
  visual jobs are local-only today (see CLAUDE.md), so same-machine is
  currently the whole population. Re-run `noise-floor.sh` before those
  jobs move to CI; if captures stop being byte-identical there, the
  stale-fails-the-build promotion is the first thing to revisit.
- **A zero floor makes the *tolerances* harder to justify, not easier.**
  With no noise to absorb, every point of slack below SSIM 1.0 is pure
  tolerance for real change. The baseline gate is in effect an
  exact-match check spelled as a similarity threshold, and a regression
  that shifts many pixels slightly — a 1px baseline move, a small
  uniform colour shift — still sails through at SSIM 0.999. That is the
  same degenerate-pass family already recorded elsewhere in this file,
  and the noise-floor result removes the last excuse for it. Tightening
  is follow-up work, deliberately not bundled with the promotion.

### Reproducing

```bash
bash tools/visual/noise-floor.sh                          # visual-test, 2 runs (~6 min)
bash tools/visual/noise-floor.sh fixtures/composition-test.json 3
```

Exits 0 when captures are byte-identical. When they are not, it scores
each platform's run-1-vs-run-2 captures through the *shipping* metric
code (by mapping the two runs onto two of the comparator's platform
slots) and prints the spread, so the number is directly comparable to
the thresholds it is meant to justify.

The script excludes any platform that wrote no captures during a run,
via an mtime guard against a reference file stamped before the run. That
guard is not incidental: without it a `SKIP_IOS=1` run would copy the
*previous* run's stale iOS PNGs, compare them against themselves, and
report a perfect noise floor for a platform that never executed — a
study that cannot fail is worth nothing.

## Test suites

| suite | command | tests |
|---|---|---:|
| converter (Kotlin) | `./gradlew :converter:test` | 368 |
| web runtime (vitest) | `npm -w runtimes/web run test` | 1308 |
| compose runtime (JUnit) | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` | 2761 |
| swiftui runtime (XCTest) | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` | 1802 |
| tooling (node --test) | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` | 1733 |
| IR conformance | `node schema/conformance/run.mjs --emit` | 39 goldens (12 v1 + 27 v2) × 4 codebases |

## Roadmap

Static code **writers** (Compose / SwiftUI / Tailwind source from IR); a
future leaf-strictness revision closing the still-permissive per-property
`data` leaves; retiring the deprecated `--emit-ir v1` path after its
one-release deprecation window; IR semantic model (roles/labels) to
unblock the accessibility criteria above. The **flat-IR v2** wire already
shipped (PR #30) — it is the current default, with the slot/placement
children contract frozen in `schema/spec/03-children.md` +
`05-versioning.md` and the known v1 wire defects repaired at that freeze.

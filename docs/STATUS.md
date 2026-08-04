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
  in [`schema/spec/`](../schema/spec/); 36 golden fixtures (12 v1 + 24 v2)
  are decoded by conformance tests on all four codebases
  (`node schema/conformance/run.mjs`).

## Coverage — three numbers, all true

| claim | number | source of truth |
|---|---|---|
| Registration coverage — triplet exists + claims the IR type (a string-presence facade; not native rendering) | **558 / 558 per platform** (Android 558 / 558 · iOS 558 / 558 · Web 558 / 558) | `node tools/visual/coverage-audit.mjs` (`registered:`) → `tools/visual/COVERAGE.md` |
| Real-applier floor — a dedicated `<Name>Applier` file exists (under-counts grouped appliers) | **Android 18 / 558 · iOS 73 / 558 · Web 516 / 558** | `coverage-audit.mjs` (`real:` line) |
| Verified rendering coverage — SSIM ≥ 0.95, every variant, every platform pair | **91 / 550 (~17%)** | converged audit campaign, round 40 (below) |

"Registered" is only a string-presence facade — a triplet exists and
claims the IR type; it does NOT mean a dedicated applier renders the
property natively. The stricter real-applier floor counts a dedicated
`<Name>Applier.<ext>` file per property; it under-counts grouped appliers
(one file — e.g. Compose `LayoutApplier.kt`, iOS `FlexboxApplier.swift`,
web `ScrollMarginApplier.ts` — renders many properties but its basename
matches at most one IR name, so the raw dedicated-applier file counts
Android 59 · iOS 117 · Web 530 sit above the per-property floor). Some
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

## Test suites

| suite | command | tests |
|---|---|---:|
| converter (Kotlin) | `./gradlew :converter:test` | 237 |
| web runtime (vitest) | `npm -w runtimes/web run test` | 1088 |
| compose runtime (JUnit) | `(cd apps/android-harness && ./gradlew :runtime:testDebugUnitTest)` | 1731 |
| swiftui runtime (XCTest) | `xcodebuild test -scheme StyleConverterRuntime -destination 'platform=macOS,variant=Mac Catalyst,arch=arm64'` | 1023 |
| tooling (node --test) | `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` | 1019 |
| IR conformance | `node schema/conformance/run.mjs --emit` | 36 goldens (12 v1 + 24 v2) × 4 codebases |

## Roadmap

Static code **writers** (Compose / SwiftUI / Tailwind source from IR); a
future leaf-strictness revision closing the still-permissive per-property
`data` leaves; retiring the deprecated `--emit-ir v1` path after its
one-release deprecation window; IR semantic model (roles/labels) to
unblock the accessibility criteria above. The **flat-IR v2** wire already
shipped (PR #30) — it is the current default, with the slot/placement
children contract frozen in `schema/spec/03-children.md` +
`05-versioning.md` and the known v1 wire defects repaired at that freeze.

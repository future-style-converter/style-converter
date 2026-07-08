# Style-Converter — Deep testing research

A comprehensive map of every way Style-Converter could be tested,
ranked roughly by depth, with concrete methodology proposals for
each axis. The current 12-tier plan in `TIERS.md` covers ~30% of
what's listed here.

Format per axis:
- **what** — what we measure
- **why** — what bug class it catches
- **how** — concrete methodology + tooling
- **effort** — small (S, ≤1d), medium (M, 1-3d), large (L, week+)
- **status** — covered / partial / proposed

---

## A. Correctness & semantic axes

### A1. CSS spec conformance (WPT integration)
- **what**: run subsets of the Web Platform Tests CSS suite through the converter
- **why**: catches spec-deviation bugs (wrong default value, wrong inheritance, wrong cascade)
- **how**: clone wpt/css, pick the property tests for properties we claim to support, convert each test's `<style>` block to IR, render via three platforms, compare to WPT's reference image. Pass = matches reference within SSIM ≥0.95.
- **effort**: L (5-10 days; WPT is huge)
- **status**: proposed

### A2. Computed-value correctness
- **what**: not just "renders identically" but "produces the same `getComputedStyle()` result"
- **why**: SSIM can pass even when computed values diverge (e.g. `em` resolves to 14px vs 16px). Catches the gap between "looks right" and "is right"
- **how**: emit a JSON dump of every component's computed style after rendering, diff structurally against the CSS spec's expected computed value (per-property tables in the spec)
- **effort**: M
- **status**: proposed

### A3. Cascade specificity / !important handling
- **what**: when 2+ rules target the same property, does the right one win?
- **how**: synthesize fixtures with intentional specificity races, e.g. `.a` (specificity 010) vs `#x` (100) vs inline (1000) vs `!important` (∞)
- **effort**: M
- **status**: proposed

### A4. Inheritance chain
- **what**: when child has no `color`, does it inherit parent's `color` correctly?
- **how**: nested-component fixtures with parent-child relationships; assert child renders as if it had the inherited value
- **effort**: M
- **status**: gap (Tier 1 is single-component only)

### A5. `inherit` / `initial` / `unset` / `revert` / `revert-layer` semantics
- **what**: each global keyword's exact behavior (we already saw `all: initial` diverges)
- **how**: a fixture per property × per global keyword (~550 × 5 = 2750 cells)
- **effort**: L (could be auto-generated)
- **status**: partial (one row in Tier 1)

### A6. Default UA stylesheet emulation
- **what**: when no CSS is provided, the converter should emit sensible defaults (e.g. `display: block` on `<div>`, `margin: 1em 0` on `<p>`)
- **how**: empty-fixture renders should look like Chrome's default
- **effort**: S
- **status**: proposed

### A7. CSS Cascade Layers (`@layer`)
- **what**: layer order overrides specificity
- **how**: fixture pairs with `@layer base, override` and conflicting rules
- **effort**: M
- **status**: proposed (parser may not even support yet)

### A8. Container queries (`@container`)
- **what**: child styles depend on container size, not viewport
- **how**: fixture with parent at 500px wide and 300px wide; assert different child rendering
- **effort**: L
- **status**: proposed

### A9. `:has()` / `:is()` / `:where()` selector logic
- **what**: complex selector matching
- **how**: fixture trees that test selector evaluation
- **effort**: M
- **status**: proposed

### A10. View transitions (`@view-transition` / `view-transition-name`)
- **what**: smooth state-change animations
- **how**: capture before+after frames; assert intermediate easing matches across platforms
- **effort**: L
- **status**: proposed

---

## B. Visual / pixel-level axes

> **STATUS: Section B fully shipped.**
>
> **B-FOUNDATION (round 90):** B2–B7 live in `testing/compare-screenshots-metrics.mjs`
> (per-channel SSIM, edge SSIM, histogram KL, pHash, DSSIM, LAB ΔE2000), wired into
> every pair in `testing/report/manifest.json` (manifestVersion = 2 then 3). The
> 7-label divergence classifier (`testing/classify-divergence.mjs`) and empirical
> distribution snapshot (`testing/baseline-stats.mjs` → `testing/baseline-stats.json`)
> sit on top. Spec at `testing/COMPARE_METRICS.md` (319 lines). Baseline distribution
> across the visual-test 327 pairs: identical=2 · sub-pixel-noise=77 · color-drift=24
> · edge-shift=0 · structural-divergence=74 · mixed=150 · unknown=0.
>
> **B-EXTENSION (round 91):** B8–B10 live in
> `testing/compare-screenshots-text-metrics.mjs`. New `examples/_metric_probes/`
> probe fixtures + `testing/probe-text-metrics.sh` driver + 4× hires capture path
> (web complete; iOS/Android scaffolded — `renderHires()` in
> `ScreenshotManager.swift`, `saveProbeScreenshot()` in `ScreenshotManager.kt`,
> awaiting round 92+ Xcode/emulator validation). Probe outputs land in
> `manifest.perPlatformProbes` (manifestVersion = 3, backward-compat). 4-label
> probe classifier (`testing/classify-text-probe.mjs`) sits alongside the 7-label
> pair classifier. Spec at `testing/COMPARE_METRICS_B8-B10.md` (398 lines).
> Test count grew 90 → 119 (+29 across both campaigns).

### B1. Pixel-exact diff (current uses SSIM)
- **what**: count pixel-by-pixel mismatches (not just structural similarity)
- **why**: SSIM ≥0.95 still permits ~hundreds of pixel differences; pixel-exact catches sub-pixel drift
- **how**: pixelmatch with threshold 0; report % of pixels different
- **effort**: S (already use pixelmatch in compare-screenshots.mjs)
- **status**: partial

### B2. Per-channel (R/G/B/A) diff
- **what**: which color channel diverges most? (e.g. blue channel anti-aliasing differs)
- **how**: split image into channels, SSIM each separately
- **effort**: S
- **status**: proposed

### B3. Edge detection (Canny/Sobel) diff
- **what**: are the EDGES of shapes in the same place?
- **why**: catches anti-aliasing differences (current sepia divergence) without conflating with fill differences
- **how**: run Sobel on both images, then SSIM the edge maps
- **effort**: S
- **status**: proposed

### B4. Color histogram diff
- **what**: distribution of colors used (regardless of position)
- **why**: catches "right colors, wrong layout" vs "right layout, wrong colors"
- **how**: 256-bucket histogram per channel, compute KL divergence
- **effort**: S
- **status**: proposed

### B5. Perceptual hash (pHash) similarity
- **what**: 64-bit fingerprint of image; Hamming distance ≤ 10 = visually identical
- **why**: very fast for triage of regressions
- **how**: imghash library; build pHash index of all baselines
- **effort**: S
- **status**: proposed

### B6. DSSIM (more sensitive SSIM)
- **what**: replaces SSIM with DSSIM (Kornel Lesinski's tool)
- **why**: DSSIM correlates better with human perception than SSIM
- **how**: install `dssim` CLI, swap in compare-screenshots.mjs
- **effort**: S
- **status**: proposed

### B7. CIE LAB ΔE color difference
- **what**: perceptual color distance (where ΔE < 1 is imperceptible to humans)
- **why**: catches "looks the same to a machine, looks different to a human" (e.g. SSIM 0.97 with shifted hue that humans notice)
- **how**: convert each pixel to LAB, compute mean ΔE
- **effort**: S
- **status**: proposed

### B8. Sub-pixel positioning analysis
- **what**: where iOS / Android / web place glyph baselines, exactly
- **why**: 1-px text shift is the dominant Tier 3 / typography divergence
- **how**: render text fixtures at 4× resolution, measure baseline-to-edge distance per platform
- **effort**: M
- **status**: proposed

### B9. Anti-aliasing strategy comparison
- **what**: which anti-aliasing each rasterizer uses (greyscale, subpixel, none)
- **how**: render a single 1px line at multiple angles, FFT the result, check frequency distribution
- **effort**: M
- **status**: proposed

### B10. Text rendering kerning/hinting
- **what**: glyph positioning under different rendering settings
- **how**: render long text strings, measure inter-glyph distances on each platform
- **effort**: L
- **status**: proposed

---

## C. Round-trip / symmetry axes

### C1. CSS → IR → CSS round-trip
- **what**: regenerate CSS from IR, parse again, assert IR is bitwise identical
- **why**: catches lossy normalization (e.g. `red` → `#ff0000` should still parse back to red)
- **how**: pipe every fixture through `convert --from css --to css`, diff vs original
- **effort**: S
- **status**: proposed

### C2. IR → output → IR closed loop
- **what**: render the output (Compose / SwiftUI / CSS), use computer vision to extract IR back, compare
- **why**: catches semantic loss in code generation
- **how**: render → screenshot → ML model trained on style+screenshot pairs → predicted IR → diff
- **effort**: L (research project)
- **status**: proposed

### C3. Cross-target equivalence
- **what**: convert(CSS → Compose) → convert(Compose → CSS) — round trip across formats
- **why**: catches asymmetric format support
- **effort**: M
- **status**: proposed

### C4. Idempotency
- **what**: `convert(convert(x))` == `convert(x)`
- **why**: catches accumulating floating-point noise, normalization drift
- **effort**: S
- **status**: proposed

### C5. Determinism (machine independence)
- **what**: same input on machine A and machine B produces byte-identical output
- **why**: catches ordering bugs (HashMap iteration), system-locale dependency
- **how**: run converter on Linux + macOS + Docker, hash outputs, compare
- **effort**: S
- **status**: proposed

### C6. Lossy property catalogue
- **what**: enumerate every CSS property whose value is lost or normalized in round-trip
- **why**: the truthful "what does this tool actually preserve?" answer
- **how**: per-property: random-generate 20 CSS values, round-trip, count exact matches
- **effort**: M
- **status**: proposed

---

## D. Stress / scale axes

### D1. Large-tree benchmarks (Tier 10 partial)
- **what**: 100, 1k, 10k, 100k components in one fixture
- **how**: synthesize, render, time
- **effort**: S
- **status**: partial (Tier 10 scaffold exists, never run)

### D2. Deep-nesting trees
- **what**: 20+ levels of nested boxes; tests inheritance + layout cost
- **effort**: S
- **status**: proposed

### D3. Wide trees
- **what**: 1000 siblings in a flexbox; tests layout-engine complexity
- **effort**: S
- **status**: proposed

### D4. Memory pressure (RSS / GPU memory)
- **what**: track resident memory growth during rendering of large fixtures
- **how**: hook macOS `vm_region`, Android `Debug.MemoryInfo`, Chrome `memory.measureUserAgentSpecificMemory`
- **effort**: M
- **status**: proposed

### D5. Pathological calc() chains
- **what**: `calc(calc(calc(...100 levels...)))` — tests parser robustness + perf
- **effort**: S
- **status**: proposed

### D6. Maximum simultaneous animations
- **what**: 1000 elements all animating simultaneously — tests animation engine throughput
- **effort**: M
- **status**: proposed

### D7. Long-string property values
- **what**: 10K-character gradient with 1000 stops; tests parser memory
- **effort**: S
- **status**: proposed

---

## E. Property-interaction edge cases

### E1. Stacking context creation
- **what**: every CSS property that creates a new stacking context (z-index, opacity, transform, filter, isolation, position+z-index, ...)
- **how**: fixture with overlapping siblings + each stacking-context trigger; assert ordering
- **effort**: M
- **status**: proposed

### E2. Containing block establishment
- **what**: when does an element become the containing block for `position: absolute` descendants? (transform, perspective, will-change, contain)
- **effort**: M
- **status**: proposed

### E3. Block formatting context (BFC)
- **what**: when does an element create a new BFC? (overflow != visible, display: flow-root, etc.)
- **effort**: M
- **status**: proposed

### E4. Flexbox + min/max-content
- **what**: `flex-basis: min-content` interacting with `flex-grow: 1` — known divergence area
- **effort**: M
- **status**: proposed

### E5. Grid auto-flow corner cases
- **what**: `grid-auto-flow: dense` with mixed-size items
- **effort**: M
- **status**: proposed

### E6. backdrop-filter + opacity + transform interaction
- **what**: any of these create a stacking context and interact with backdrop scope
- **effort**: M
- **status**: proposed

### E7. Border-collapse + table-layout
- **what**: complex table rendering rules
- **effort**: M
- **status**: proposed

### E8. Counter scopes
- **what**: counter-reset / counter-increment / content: counter()
- **effort**: M
- **status**: proposed

### E9. Mask + opacity
- **what**: mask layer composition modes interact with opacity
- **effort**: M
- **status**: proposed

### E10. Transform 3D + perspective + transform-origin
- **what**: 3D transform chains with non-default origins
- **effort**: M
- **status**: proposed

---

## F. CSS feature coverage axes

### F1. Modern color spaces (oklch, lab, lch, hwb, color())
- **what**: each modern color space round-trips correctly; renders correctly per spec
- **how**: per-space, generate 20 colors at corners + interior of gamut, render, compare
- **effort**: M
- **status**: proposed

### F2. `color-mix()`, `light-dark()`, `color-contrast()`
- **what**: dynamic color computation
- **effort**: M
- **status**: proposed

### F3. CSS custom properties (variables) + `@property` registration
- **what**: `--my-var: 4px` → `width: var(--my-var)` works; typed `@property --x: <length>` enforces type
- **effort**: M
- **status**: proposed

### F4. `attr()` function
- **what**: `content: attr(data-x)` reads HTML attribute
- **effort**: S
- **status**: proposed

### F5. `env()` function
- **what**: safe-area-inset values and other UA-defined env variables
- **effort**: S
- **status**: proposed

### F6. Logical properties round-trip
- **what**: `padding-block-start` → physical `padding-top` (in LTR horizontal-tb writing-mode)
- **how**: assert logical and physical fixtures render identically when writing-mode is default
- **effort**: M
- **status**: proposed

### F7. `@scope` rules
- **what**: scoped style isolation
- **effort**: M
- **status**: proposed

### F8. `@starting-style` (entry transitions)
- **what**: animations that fire when an element first appears
- **effort**: M
- **status**: proposed

### F9. Subgrid
- **what**: nested grids inheriting parent track sizes
- **effort**: M
- **status**: proposed

### F10. Anchor positioning (`anchor()`, `position-anchor`)
- **what**: tooltips/popovers anchored to other elements
- **effort**: L
- **status**: proposed

### F11. Scroll-driven animations (Tier 4 partial)
- **what**: `animation-timeline: scroll(...)` — animation tied to scroll position
- **effort**: M
- **status**: partial

---

## G. Internationalization

### G1. RTL rendering (writing-mode + direction)
- **what**: every layout property in `direction: rtl` mode
- **how**: each Tier-3 component re-rendered with `dir=rtl`, compare cross-platform
- **effort**: M
- **status**: proposed

### G2. CJK character rendering
- **what**: glyph metrics for Chinese/Japanese/Korean text
- **how**: text fixtures in CJK fonts
- **effort**: M
- **status**: proposed

### G3. Bidi text
- **what**: mixed Arabic + Latin text rendering
- **effort**: M
- **status**: proposed

### G4. Vertical writing-modes (vertical-rl, vertical-lr, sideways-rl)
- **what**: text flow rotation; we saw this diverged in Tier 1
- **how**: deeper fixture set per writing-mode value
- **effort**: M
- **status**: proposed

### G5. Variable-font axis fidelity
- **what**: `font-variation-settings: "wght" 567` produces an interpolated weight
- **how**: per-axis interpolation tests
- **effort**: M
- **status**: proposed

### G6. Hyphenation
- **what**: `hyphens: auto` produces correct break points per language
- **effort**: M
- **status**: proposed

### G7. Color emoji
- **what**: emoji rendering with skin-tone modifiers, ZWJ sequences
- **effort**: M
- **status**: proposed

---

## H. Accessibility (deeper than Tier 11 placeholder)

### H1. Screen reader DOM order
- **what**: tab order through a complex fixture matches visual order
- **how**: VoiceOver / TalkBack / NVDA scripted walk
- **effort**: L
- **status**: proposed

### H2. Focus traversal
- **what**: `tabindex` ordering correctness
- **effort**: M
- **status**: proposed

### H3. Reduced motion (`prefers-reduced-motion`)
- **what**: animations are suppressed when this media query matches
- **effort**: M
- **status**: proposed

### H4. Forced colors mode
- **what**: high-contrast OS theme overrides applied
- **effort**: M
- **status**: proposed

### H5. Increased font size
- **what**: layout doesn't break at 200% / 400% font scale
- **effort**: M
- **status**: proposed

### H6. `prefers-color-scheme` dark/light
- **what**: dark/light variants both render
- **how**: each fixture has explicit light + dark variants
- **effort**: M
- **status**: proposed

### H7. Color contrast (WCAG AA / AAA)
- **what**: each fixture's text/bg contrast meets ratio
- **how**: extract dominant fg + bg per component, compute WCAG ratio
- **effort**: S
- **status**: partial (Tier 11 placeholder)

### H8. Touch target size
- **what**: every interactive element ≥ 44×44 pt (HIG / WCAG)
- **effort**: S
- **status**: proposed

---

## I. Browser / runtime compatibility

### I1. Chrome / Firefox / Safari rendering parity
- **what**: web target works identically in all three browsers (currently only tested in headless Chrome)
- **how**: Playwright with all 3 engines
- **effort**: M
- **status**: proposed

### I2. WebKit version sweep
- **what**: iOS 16 WebKit vs iOS 18 WebKit
- **effort**: M
- **status**: subset of Tier 12

### I3. SwiftUI version sweep (iOS 16 → 18 → 26)
- **what**: same fixture behaves identically across SwiftUI versions
- **effort**: M
- **status**: Tier 12 scope

### I4. Compose version sweep
- **what**: API 24 / 30 / 34 / 35
- **effort**: M
- **status**: Tier 12 scope

### I5. Gradle / Kotlin / Java version sweep
- **what**: converter builds + tests pass with Kotlin 2.0 / 2.1, Gradle 8.x, Java 17 / 21
- **effort**: S
- **status**: proposed

### I6. Android device-class sweep
- **what**: low-end Android with Skia software rendering vs high-end with hardware
- **effort**: M
- **status**: proposed

---

## J. State / interaction (beyond hover)

### J1. Pseudo-elements (`::before`, `::after`, `::first-line`, `::first-letter`)
- **what**: each pseudo-element renders correctly
- **effort**: M
- **status**: proposed

### J2. Form input states (`:placeholder-shown`, `:required`, `:valid`, `:invalid`, `:user-invalid`)
- **how**: drive form state programmatically; compare per state
- **effort**: M
- **status**: proposed

### J3. Scroll position effects (`background-attachment: fixed`)
- **what**: parallax-style backgrounds
- **effort**: M
- **status**: proposed

### J4. Live state changes during render
- **what**: animation playing while screenshot fires; non-deterministic frames
- **how**: pause the animation at known progress %, snapshot
- **effort**: M
- **status**: proposed (Tier 4 partial)

### J5. Container query state
- **what**: parent resize triggers child restyle
- **effort**: M
- **status**: proposed

---

## K. Generative / property-based testing

### K1. Random valid CSS generator
- **what**: QuickCheck-style generator of arbitrary valid CSS values per property
- **why**: parser/converter must accept any valid CSS
- **how**: per-property grammar → random walk → emit CSS → parse → verify no crash + IR is well-formed
- **effort**: L (one-time investment, infinite leverage)
- **status**: proposed

### K2. Mutation testing (fixture mutation)
- **what**: take a passing fixture, mutate one value at a time (insert space, change unit, swap keyword), verify renders identically OR fails predictably
- **why**: catches "lenient parser accepts garbage" bugs
- **effort**: M
- **status**: proposed

### K3. Mutation testing (renderer code)
- **what**: deliberately break renderer code (one statement at a time), verify a test catches it
- **why**: measures actual test coverage, not just LOC coverage
- **how**: PIT for Kotlin/Android, Mutant for Swift, Stryker for TypeScript
- **effort**: M
- **status**: proposed

### K4. Snapshot baseline drift detection
- **what**: per-fixture, per-platform SSIM tracked over time; alert on any drop > 0.05
- **status**: Tier 8 (built but not enforced in CI)

### K5. Random fixture combinations (Tier 2 deepening)
- **what**: 3-property, 4-property, 5-property combo fixtures (current Tier 2 only does 1+1)
- **effort**: M
- **status**: proposed

---

## L. Reference-rendering / gold-standard

### L1. Chrome headless as gold standard
- **what**: instead of pairwise platform comparison, compare each platform to Chrome
- **why**: removes the "all three could be wrong identically" failure mode
- **effort**: S
- **status**: proposed

### L2. WPT reference images
- **what**: WPT ships pixel-exact reference renders; compare each platform to those
- **effort**: M
- **status**: subset of A1

### L3. Skia headless (without device)
- **what**: render via Skia directly (Android's renderer, no emulator); compare to current Android emulator output
- **why**: removes emulator overhead from baseline
- **effort**: M
- **status**: proposed

### L4. CSS spec example renders
- **what**: every CSS spec contains illustrative examples; render each, compare to spec figures
- **effort**: L
- **status**: proposed

---

## M. Negative / coverage testing

### M1. "What CSS does the converter silently drop?"
- **what**: per CSS property, log every input that produced no IR change
- **why**: silent drops are the worst class of bug — user thinks it works
- **how**: instrument the parser to record every "couldn't parse" outcome
- **effort**: S
- **status**: proposed

### M2. "What CSS produces converter warnings?"
- **what**: every warning emitted; coverage of warning paths
- **effort**: S
- **status**: proposed

### M3. "What CSS isn't even attempted?"
- **what**: properties in the CSS spec that the converter has no parser for
- **how**: cross-reference parser registry against spec property list
- **effort**: S
- **status**: proposed

---

## N. Performance / runtime metrics

### N1. Cold-start render time
- **what**: ms to first paint after app launch
- **status**: proposed

### N2. Steady-state render time
- **what**: ms per re-render after warmup
- **status**: proposed

### N3. Memory: RSS, GPU, allocations
- **status**: Tier 10 placeholder

### N4. Frame rate during animations
- **what**: 60fps achieved while rendering N animating elements
- **how**: instrument each platform's frame callback
- **effort**: M
- **status**: proposed

### N5. Compositing layer count
- **what**: number of layers each platform allocates
- **how**: Chrome DevTools Layers panel; Android GPU profiler; Instruments
- **effort**: M
- **status**: proposed

### N6. Layout shift (CLS)
- **what**: cumulative layout shift during render
- **effort**: S
- **status**: proposed

### N7. Paint flashing (per-region paint count)
- **what**: how many times each region was repainted
- **effort**: M
- **status**: proposed

### N8. Battery impact (Android battery historian, Xcode energy log)
- **effort**: M
- **status**: proposed

---

## O. Security / robustness

### O1. CSS injection through style strings
- **what**: malicious CSS values can't break out of property scope
- **how**: fuzz with `}`, `<`, `</style>`, `*/`, control chars
- **effort**: S
- **status**: proposed

### O2. Crashing inputs (Tier 7 partial)
- **status**: complete (9/9)

### O3. DOS via huge inputs
- **what**: 10MB CSS file → does converter OOM or terminate?
- **effort**: S
- **status**: proposed

### O4. Deeply recursive inputs
- **what**: nested `calc()` or recursive `var()` references
- **effort**: S
- **status**: partial (Tier 7)

### O5. URL injection in `url()`
- **what**: `background-image: url("javascript:...")` — does converter sanitize?
- **effort**: S
- **status**: proposed

### O6. Container/parser limits
- **what**: documented max values for nesting depth, file size, gradient stops, etc.
- **effort**: S
- **status**: proposed

---

## P. Comparison testing (vs other tools)

### P1. vs styled-components (CSS-in-JS)
- **what**: same input style → same output structure?
- **effort**: M
- **status**: proposed

### P2. vs Tailwind compiler
- **what**: convert tailwind utility classes; output should match Tailwind's compiled CSS
- **effort**: M
- **status**: proposed

### P3. vs Web's `getComputedStyle()` API
- **what**: converter's computed value matches browser's
- **effort**: S
- **status**: proposed

### P4. vs CSS-Tree (parser)
- **what**: AST shape compatibility with the de facto CSS parser
- **effort**: S
- **status**: proposed

### P5. vs Compose's actual Modifier behavior
- **what**: emitted Compose code, when run, produces the same Modifier tree as hand-written equivalent
- **effort**: M
- **status**: proposed

---

## Q. Determinism / hermeticity

### Q1. No timestamps in output
- **what**: convert at 12:00, convert at 13:00, output identical
- **status**: proposed

### Q2. No floating-point noise
- **what**: small-difference inputs (1.0px vs 1.0000001px) produce identical IR
- **status**: proposed

### Q3. Locale-independence
- **what**: convert with `LANG=en` and `LANG=fr` → identical output
- **effort**: S
- **status**: proposed

### Q4. No hidden ordering bugs (HashMap iteration)
- **what**: same input → same output across many runs
- **status**: subset of C5

---

## R. Compose-specific deep tests

### R1. Recomposition correctness
- **what**: state change → only affected modifiers recompute
- **how**: Composable inspector tools to count recompositions
- **effort**: M
- **status**: proposed

### R2. Modifier order significance
- **what**: `Modifier.padding(8.dp).background(red)` differs from `.background(red).padding(8.dp)` — converter must respect this
- **effort**: S
- **status**: proposed

### R3. State hoisting through styled containers
- **what**: ViewModel state reaches styled descendants intact
- **effort**: M
- **status**: proposed

### R4. Animation system compatibility
- **what**: emitted animations work with Compose's `animateFloatAsState`, `Transition`, `InfiniteTransition`
- **effort**: M
- **status**: proposed

---

## S. SwiftUI-specific deep tests

### S1. View identity preservation
- **what**: structurally-stable view tree across re-renders
- **effort**: M
- **status**: proposed

### S2. Animation transitions
- **what**: state change with `.animation(...)` modifier produces smooth transition
- **effort**: M
- **status**: proposed

### S3. @State / @Binding / @Environment interactions with styled subtree
- **effort**: M
- **status**: proposed

### S4. UIVisualEffectView interop
- **what**: backdrop-blur fixture using UIViewRepresentable wrapper
- **effort**: M
- **status**: proposed (would unblock current backdrop-filter limitation)

---

## T. Tooling / CI investments

### T1. Diff-aware testing
- **what**: only re-run tests for properties whose code changed
- **how**: dependency graph: parser file → fixture set → renderer triplet
- **effort**: M
- **status**: proposed

### T2. Per-platform parallelism
- **what**: iOS / Android / web render in parallel (currently sequential per fixture)
- **effort**: S
- **status**: proposed

### T3. Cache invalidation correctness
- **what**: build caches don't mask parser changes
- **effort**: S
- **status**: proposed

### T4. Reproducible CI (Docker pinned versions)
- **what**: identical results from any CI runner
- **effort**: M
- **status**: proposed

### T5. Snapshot review UI
- **what**: GitHub PR comment with side-by-side baseline + new + diff for every regression
- **effort**: M
- **status**: proposed

### T6. Per-property change-impact CI gate
- **what**: PR that touches `style/borders/` triggers ONLY borders fixtures (fast)
- **effort**: M
- **status**: proposed

### T7. Flake detection (re-run failures, mark flaky)
- **effort**: S
- **status**: proposed

### T8. Visual baseline approval workflow
- **what**: when SSIM drops, surface diff to human for approve/reject; auto-update baseline on approve
- **effort**: M
- **status**: proposed

### T9. Test sharding across machines
- **what**: 548-property suite distributed across 8 CI runners → 8× faster
- **effort**: M
- **status**: proposed

---

## U. Data / corpus expansion

### U1. CSS-in-the-wild corpus
- **what**: scrape top 10k websites, extract CSS, run through converter
- **why**: discovers parser gaps that synthetic tests miss
- **effort**: L
- **status**: proposed (Tier 9 fetched 10 sites only)

### U2. Open-source design system corpus
- **what**: convert Material, Tailwind, Bootstrap, Carbon, Lightning, Polaris, Fluent
- **effort**: M
- **status**: proposed

### U3. Figma-export corpus
- **what**: real designs exported to CSS, run through converter
- **effort**: M
- **status**: proposed

### U4. Historical CSS (CSS 1, CSS 2.1, CSS 3 specs separately)
- **what**: ensure backward-compatible properties still work
- **effort**: M
- **status**: proposed

---

## V. Documentation testing

### V1. README example correctness
- **what**: every example in README actually runs and produces claimed output
- **how**: extract code blocks, execute, diff against expected output in README
- **effort**: S
- **status**: proposed

### V2. Generated docs match implementation
- **what**: kdoc / javadoc generated docs match actual function signatures
- **effort**: S
- **status**: proposed

### V3. Error messages are actionable
- **what**: every error message contains: what went wrong, where, how to fix
- **how**: corpus of error inputs, assert all messages have the 3 parts
- **effort**: S
- **status**: proposed

---

## W. Type-system / property-type tests

### W1. Type narrowing
- **what**: an `IRColor` always resolves to a valid sRGB tuple (not null)
- **effort**: S
- **status**: proposed

### W2. Range constraints
- **what**: `font-weight` value in [1, 1000]; `opacity` in [0, 1]
- **effort**: S
- **status**: proposed

### W3. Unit conversions
- **what**: `1in == 96px == 72pt == 25.4mm` — all units convert correctly
- **effort**: S
- **status**: proposed

### W4. Color space conversions
- **what**: `rgb(255, 0, 0)` ≡ `#ff0000` ≡ `oklch(0.628 0.258 29.23)` (within ΔE < 2)
- **effort**: S
- **status**: proposed

### W5. Radian/degree/turn/grad conversions
- **what**: `1turn == 360deg == 6.283rad == 400grad`
- **effort**: S
- **status**: proposed

---

## X. Cross-cutting analyses (not tier work, but reports)

### X1. Property coverage matrix vs CSS spec
- **what**: WHAT % of CSS the converter actually handles, vs claims
- **status**: partial (testing/COVERAGE.md)

### X2. Per-platform feature gap report
- **what**: categorized list of "iOS supports, Android doesn't" etc.
- **status**: emerging (blocked-platform notes)

### X3. Bug class catalogue
- **what**: post-mortem of every fixed bug; cluster into classes for future test design
- **effort**: S
- **status**: proposed

### X4. Performance regression history
- **what**: time-series of render time per fixture across commits
- **status**: proposed

### X5. Real-world CSS coverage gap
- **what**: among tailwind / bootstrap / etc., what % of selectors/properties are handled
- **status**: partial (Tier 9 IR conversion works for tailwind)

---

## Priority ranking (to actually do next)

The most-leveraged additions ordered by ROI:

1. **K1 (random CSS generator)** — once built, infinite test cases for free
2. **L1 (Chrome as gold standard)** — eliminates "all three wrong identically" failure mode
3. **B6 (DSSIM) + B7 (LAB ΔE)** — better divergence detection for tricky variant fails
4. **A2 (computed value)** — separates "looks right" from "is right"
5. **A4 (inheritance fixtures)** — expands beyond single-component to nested trees
6. **K2 (mutation testing)** — measures actual coverage of existing tests
7. **M1 (silent-drop log)** — catches the worst class of bug (lying success)
8. **G1 (RTL rendering)** — entire layout subspace untested
9. **A1 (WPT integration)** — borrows the entire industry's test corpus
10. **U2 (design system corpus)** — real-world coverage anchor

The first 5 above could plausibly land within 2 weeks each. Items
8-10 are larger investments but each adds an entire untested
dimension.

---

## Anti-patterns observed (lessons from current 12-tier rollout)

The auditor caught these during Tiers 1-3:

1. **False-PASS via parse failure** — when both renderers fail to parse the same value identically (e.g. `filter: blur` no-args), SSIM passes trivially. Parsers must validate inputs before claiming coverage.
2. **Bare-keyword leakage** — extracting `"blur"` from parser source code as a CSS value produces invalid CSS. Hand-written fixture syntax for function-valued props is the only reliable path.
3. **Status-vs-data divergence** — tracker rows can claim "passing" while underlying fixtures are degenerate (zero declarations of the property under test). Resync passes are required.
4. **Lock-as-file vs directory** — silent test-infra bugs that mask real progress. Reading the lock-file impl matters.
5. **Mass-batch commits** — 100+ rows per commit hides per-property failures. Per-row commits are the contract.
6. **Cosmetic dropping** — removing a variant because it fails, without root-cause analysis, is the dominant laziness pattern. Each drop needs a `blocked-platform: <reason>` note.

These anti-patterns generalize beyond Tier 1 — every tier needs guards against them.

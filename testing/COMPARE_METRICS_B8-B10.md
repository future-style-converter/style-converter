# COMPARE_METRICS_B8-B10 — typography divergence probes

**Status:** spec for the B-EXTENSION implementer agent.
**Target files:** new `testing/compare-screenshots-text-metrics.mjs`,
new probe fixtures under `examples/_metric_probes/`, manifest bump to
`manifestVersion: 3`.
**Companion spec:** `testing/COMPARE_METRICS.md` (B1–B7). Section
numbers and structure mirror it.

Adds three text-rendering metrics the existing seven do not capture:

- **B8** sub-pixel baseline positioning
- **B9** anti-aliasing strategy (greyscale vs subpixel vs none)
- **B10** inter-glyph spacing / kerning + hinting

Key shape difference vs B1–B7: these are **per-platform probes**, not
pair metrics. Each scores a platform individually; the 3-way
divergence is *derived* from the three per-platform numbers. They run
off a small dedicated probe-fixture suite, not the 327-pair regression
baseline.

**Out of scope:** gating CI regression on B8/B9/B10 — they are
decoration only. The existing pixel/SSIM gate is unchanged.

---

## 1. Library + capture-pipeline choices

### 1.1 Hi-res text capture (B8 + B10 share)

B8 needs **4× resolution** so a 0.25-px baseline shift on the 1×
canvas surfaces as a 1-px shift on the 4× buffer (above AA noise).
B10 benefits from the same input.

All three platforms today capture at 1×:
- web: `testing/web/capture-screenshots.mjs:68` (`deviceScaleFactor: 1`)
- iOS: `testing/iOS/StyleConverterTest/Screenshot/ScreenshotManager.swift:50` (`renderer.scale = 1.0`)
- Android: emulator at density 160 (`ScreenshotCaptureScreen.kt:360`) → 1 dp = 1 px

**Dedicated hires capture mode**, not a query-param toggle on the
existing canvas. The 327-pair regression baseline is locked at 1× —
flipping the same pipeline to 4× would invalidate every committed PNG
in `testing/baseline/`. The probe set is ~20 fixtures, so a second
pass costs <30 s.

Per platform: web → separate driver `capture-screenshots-hires.mjs`
with `deviceScaleFactor: 4`; iOS → `--hires` flag bumping
`renderer.scale` to 4.0 for `_metric_probes/` paths only; Android →
ask for a Bitmap 4× the source bounds for probe components (simpler
than adb-changing emulator density mid-run).

**Library:** `sharp@^0.33.0` (already installed) for downsample +
raw-pixel readback.

### 1.2 FFT for B9

**Library:** `fft.js@^4.x` — pure JS, ~5 KB, MIT, no native deps.
Rejected: `fftjs`/`ndarray-fft` (unmaintained since 2018),
`kissfft-js` (WASM, adds CI build step). 1-D FFT on a 64- or 128-px
region only; the power-of-two constraint matches our deliberately-sized
fixtures.

### 1.3 Glyph-edge detection for B10

**Reuse the Sobel-3 kernel** in
`compare-screenshots-metrics.mjs:193-213` (`sobelEdges()`). A
column-histogram of dark pixels fails for variable-width fonts and
any text with descenders. The extension: instead of feeding the edge
map into SSIM, project it to a 1-D signal by summing along the
vertical axis within the text band. Local maxima in the projection =
glyph boundaries; inter-distance = the kerning measurement.

---

## 2. Canonical fixture design

All fixtures live under `examples/_metric_probes/` (new directory —
keeps them off the 327-pair regression path). Same JSON shape as
`examples/properties/typography/font-family.json`. Component names
follow `B<N>_<Family>_<Variant>` so the metric helper can locate them.

### 2.1 B8 — `b8_baseline_glyphs/`

Three files (`b8_serif.json`, `b8_sans.json`, `b8_mono.json`); each
has three components at sizes 16/24/48 (e.g. `B8_Serif_16`).
Per component: 240×80 px, `background:#ffffff`, `color:#000000`
(max contrast for column-scan baseline detection), text `"AVATAR"`
(all-caps → no descenders → baseline = bottom of visible bbox;
A/V/T/R covers every stroke angle).

### 2.2 B9 — `b9_aa_lines/b9_diagonals.json`

Single file, five components: `B9_Diag_{0,22_5,45,67_5,90}`. Each is
128×128 px white box with a 1-px black line at the named angle
(power-of-two parent → FFT input needs no cropping). Angles chosen
for AA discriminator power: 0°/90° expose pure horizontal/vertical
AA; 45° forces some AA; **22.5° and 67.5°** make subpixel rasterizers
(e.g. ClearType-style web) emit telltale chroma fringing.

### 2.3 B10 — `b10_kerning/`

Three files (`b10_{mono,sans,serif}.json`), three components per file
at 320×60, font-size 24:

- `B10_<Family>_Short` — `"abcdefg"`
- `B10_<Family>_Long` — `"abcdefghijklmnopqrstuvwxyz"`
- `B10_<Family>_Pairs` — `"AVATAR WAVE"` — AV/VA/WA are textbook
  kerning pairs; an unkerned platform shows visibly wider gaps.

### 2.4 Pipeline placement

Probe fixtures **do not** flow through the standard comparison harness:

1. `test-all.sh` skips any path under `examples/_metric_probes/`.
2. New `testing/probe-text-metrics.sh` runs the hires capture pass
   per-platform on probe fixtures only.
3. Captures land in `testing/probes/<platform>__<comp>.png` (separate
   from `testing/screenshots/` so the standard loop never diffs them).
4. New `testing/compute-text-metrics.mjs` walks the probes, calls
   helpers, writes `testing/text-metrics.json`, inlines into
   `manifest.json` under `perPlatformProbes`.

---

## 3. Per-metric output shape

Lives on a **new top-level manifest field** `perPlatformProbes` —
**not** inside `pairs.<pair-name>` (rationale below).

```ts
// Manifest gains ONE new top-level field; existing v2 fields unchanged.
type Manifest = {
  manifestVersion: 3,                                 // bumped from 2
  // ... existing v2 fields ...
  perPlatformProbes?: {
    b8_subpixelBaseline?: B8Result | null,
    b9_aaStrategy?:       B9Result | null,
    b10_glyphSpacing?:    B10Result | null,
  } | null,
};

type Platforms<T> = { iOS: Record<string,T>, Android: Record<string,T>, web: Record<string,T> };

type B8Result = {
  perPlatform: Platforms<number>,                     // baseline Y in 1×-px (4× capture / 4)
  maxBaselineDeltaPx: number,                         // headline: max across (fixture × pair)
  divergentFixtureCount: number,                      // count where any pair > 0.5 px
};

type B9Result = {
  perPlatform: Platforms<'greyscale'|'subpixel'|'none'|'unknown'>,
  agreement: 'agree' | 'partial' | 'differ',          // 'agree' = all 3 match all angles
};

type B10Result = {
  perPlatform: Platforms<{ mean: number, stddev: number, count: number, meanEm: number }>,
  maxMeanDeltaPx: number,                             // mean-shift = em scaling drift
  maxStddevDeltaPx: number,                           // stddev-shift = hinting drift
};
```

**Why top-level not per-pair:** these describe each platform
individually. Replicating the same data on three pair records
(`iOS-Android`, `iOS-web`, `Android-web`) is 3× duplication and makes
the divergence summary hard to compute. The HTML report renders this
as a single typography section near the top, not inside each pair card.

`perPlatformProbes` is **null** if the probe pass was skipped —
backward-compatible with v2 readers.

---

## 4. Pass / fail thresholds

These metrics are **decoration-only** (do not gate CI). Thresholds
exist solely to color-code the report and feed the `agreement` field.

### B8 baseline

| Δ (px @ 1×) | Verdict       | Color  | Why                                                                    |
|-------------|---------------|--------|------------------------------------------------------------------------|
| `< 0.25`    | agree         | green  | Below 4×-capture quantization floor (0.25 px @ 1× = 1 px @ 4×).        |
| `0.25–0.5`  | sub-px drift  | yellow | Detectable but typically imperceptible.                                |
| `0.5–1.0`   | drift         | orange | Human-noticeable in side-by-side; ITU-R BT.500 1-px JND.               |
| `> 1.0`     | broken        | red    | At 16-px text, 1 px = ~6% of cap height — visible non-side-by-side.    |

The 0.5/1.0-px boundaries come from the same psychophysics that backs
COMPARE_METRICS.md Section 1 / B7's `labDeltaE` thresholds: humans
resolve ~1-arc-min features → ~0.5 px on a typical 390-dpi phone screen.

### B9 AA strategy

Categorical verdict (`agree`/`partial`/`differ`); no numeric threshold.
Per-platform per-angle classification heuristic (Section 1.2):

```
L = 1-D FFT magnitude of luminance along line normal
C = 1-D FFT magnitude of (R - B) chroma along same axis
ratio = energyHi(C) / energyHi(L)

  ratio > 0.3                                            → 'subpixel'
  ratio < 0.05  AND  energyHi(L)/totalEnergy(L) > 0.4    → 'greyscale'
  ratio < 0.05  AND  energyHi(L)/totalEnergy(L) < 0.1    → 'none'
  otherwise                                              → 'unknown'
```

Numbers from prior FreeType/DirectWrite analysis (RESEARCH.md:152-156).
Implementer should refine after first probe pass and commit refined
constants as named exports with empirical justification in the comment.

### B10 kerning / hinting

| Mean-Δ (px @ 1×) | Stddev-Δ (px @ 1×) | Verdict        |
|------------------|--------------------|----------------|
| `< 0.5`          | `< 0.5`            | agree          |
| `0.5 – 1.5`      | any                | kerning drift  |
| any              | `> 1.5`            | hinting drift  |
| `> 1.5`          | `> 1.5`            | broken         |

Stddev separately because failure modes differ: mean-shift = "same
kerning rules, different em scaling"; stddev-shift = "different hinting
decisions per glyph". 1.5-px = 3× the B8 noise floor (peak-find
compounds error).

---

## 5. Classifier integration

**Option (a) — keep the existing 7 labels untouched.** B8/B9/B10
measure a different population (probe fixtures, not the 327-pair
set), so no probe metric ever fires on those 327 pairs. An 8th label
would only ever apply to ~20 probes — confusing in the report ("why
does only typography ever say subpixel-shift?"). Probe results get
their own section (Section 7 step 12). `classify-divergence.mjs`
stays untouched, preserving `CLASSIFIER_VERSION = 1`.

Ship a *separate* helper
`classifyTextProbe(b8, b9, b10) → 'agree'|'sub-px-drift'|'kerning-drift'|'broken'`
in `testing/classify-text-probe.mjs` (mirrors `classify-divergence.mjs`
structure for testability).

---

## 6. Manifest shape

**Bump `manifestVersion: 2 → 3`.** Reasoning: the new top-level
`perPlatformProbes` field is structurally novel (every v2 addition
went inside `pairs.*`); v3 readers can detect probe data in one field
check; existing v2 consumers (`smoke.sh`, `baseline-stats.mjs`) ignore
unknown top-level fields, so the bump is informational, not breaking.

`pairs.*` shape **unchanged**. No new per-pair fields.

`thresholds` block gains three optional info-only entries:
```json
"thresholds": {
  ...,
  "b8BaselineDeltaPx": 1.0,
  "b10MeanDeltaPx": 1.5,
  "b10StddevDeltaPx": 1.5
}
```

---

## 7. Implementation order

Each item ≤1 hr. Order interleaves fixture authoring → capture
pipeline → metric impl → tests so each step has a runnable end state.

1. **B8 fixtures** (3 files / 9 components). Validate via
   `./gradlew run --args="convert ..."`.
2. **B9 fixtures** (1 file / 5 components).
3. **B10 fixtures** (3 files / 9 components).
4. **Probe-fixture exclusion filter** in `test-all.sh` + per-platform
   capture scripts — skip any path beginning `_metric_probes/`.
   Prevents pollution of the 327-pair baseline before the probe
   pipeline ships.
5. **Hires capture, web** — `capture-screenshots-hires.mjs` with
   `deviceScaleFactor: 4` → `testing/probes/web__<comp>.png`.
6. **Hires capture, iOS** — `--hires` flag, `renderer.scale = 4.0`
   for `_metric_probes/` paths.
7. **Hires capture, Android** — Bitmap 4× source bounds for probe
   components.
8. **B8 helper + unit test.** Two synthetic 4× PNGs (text bottom at
   row 200 vs row 204); assert helper reports 1.0-px delta on 1×.
9. **B9 helper + unit test.** Hand-crafted greyscale-AA + subpixel-AA
   PNGs; assert classifier returns the right label per fixture.
10. **B10 helper + unit test.** Synthetic monospaced + kerned lines;
    assert mean + stddev in expected ranges.
11. **Top-level driver** `testing/compute-text-metrics.mjs` walks
    `testing/probes/`, calls helpers, writes
    `testing/text-metrics.json` + inlines into `manifest.json`. Plus
    `testing/probe-text-metrics.sh` orchestrating capture → compute.
12. **HTML report extension** — "Typography probes" section near the
    top of the page; render only when `manifest.perPlatformProbes`
    is non-null. Missing field → render nothing.

Total: ~10 hr (within the 1-hr-per-item budget).

---

## 8. Open questions (each with recommended answer)

1. **Hires capture: shared dev server or separate driver?**
   *Separate driver script* (`capture-screenshots-hires.mjs`) reusing
   the vite server. A `?mode=hires` route adds a FixtureCanvas code
   path that's hard to keep deactivated for regression captures.

2. **3-platform divergence aggregation: mean-of-3 or per-pair?**
   *Per-pair max* — `max(|iOS-Android|, |iOS-web|, |Android-web|)`.
   If 2 platforms agree and 1 differs, the mean dilutes the signal;
   max reports "the worst pair drifted by N". Mirrors `worstLabel()`
   in `classify-divergence.mjs:216-228`.

3. **B9 FFT output: full spectrum or just classification?**
   *Classification only* in the manifest; full spectrum to gitignored
   `testing/text-metrics-debug.json` when `DEBUG_FFT=1`. Spectrum is
   ~8 KB of noise nobody reads; classification is the actionable bit.

4. **B10 normalization: raw px or normalized by font size?**
   *Both* — `mean`/`stddev` in raw 1×-px plus `meanEm = mean /
   fontSizeFromFixture`. Raw drives pixel comparison; normalized tells
   you whether 0.5-px divergence at 12-px is a bigger deal than at 48-px.

5. **Probe fixtures in the 327-pair regression check?**
   *No* — step 4 excludes them. (a) 4× vs 1× = apples-to-oranges;
   (b) the 327-pair count is quoted throughout the report; (c) probe
   "failures" are descriptive, not bugs.

6. **B9: Lab-space chroma signal instead of `R - B`?**
   *`R - B` for now.* Lab is more perceptually correct but adds a
   per-pixel converter on top of the FFT; `R - B` gives the same
   discriminator at a fraction of the cost. Revisit if FP rate is bad.

7. **Where do unit-test fixture PNGs live?**
   *`testing/fixtures/text-metrics/`* (new dir, mirrors
   `testing/baseline/` convention). Each PNG ≤4 KB; total ≤30 KB.
   Don't generate at test time — sharp's font rendering varies
   across CI runners, breaking the test.

8. **Manifest v3 rollout: hard cutover or graceful?**
   *Graceful* — writer always writes v3; `perPlatformProbes` is
   `null` if probes were skipped. v2 consumers ignore unknown
   top-level fields → no code change in any v2 consumer.

---

## Files to create / modify

### Create — fixtures (10 files)
- `examples/_metric_probes/b8_baseline_glyphs/b8_{serif,sans,mono}.json`
- `examples/_metric_probes/b9_aa_lines/b9_diagonals.json`
- `examples/_metric_probes/b10_kerning/b10_{mono,sans,serif}.json`

### Create — helpers + tests
- `testing/compare-screenshots-text-metrics.mjs` — B8/B9/B10 helpers,
  one exported function per metric, mirrors the `safeXxx`
  null-on-failure contract from `compare-screenshots-metrics.mjs:15-17`.
- `testing/compare-screenshots-text-metrics.test.mjs`
- `testing/classify-text-probe.mjs` — pure function, mirrors
  `classify-divergence.mjs` structure.
- `testing/classify-text-probe.test.mjs`

### Create — drivers
- `testing/compute-text-metrics.mjs` — walks `testing/probes/`,
  invokes helpers, writes `testing/text-metrics.json` + inlines into
  `manifest.json` under `perPlatformProbes`.
- `testing/probe-text-metrics.sh` — orchestrates capture → compute.
- `testing/web/capture-screenshots-hires.mjs` — puppeteer driver at
  `deviceScaleFactor: 4`.

### Create — committed test-fixture PNGs
- `testing/fixtures/text-metrics/b8_synthetic_baseline_{200,204}.png`
- `testing/fixtures/text-metrics/b9_{greyscale,subpixel}_45deg.png`
- `testing/fixtures/text-metrics/b10_{mono,kerned}_synthetic.png`

### Modify
- `testing/compare-screenshots.mjs` lines 176–189 — bump
  `manifestVersion: 2 → 3`, add three threshold keys.
- `testing/compare-screenshots-html.mjs` — "Typography probes" section
  (step 12); render only when `manifest.perPlatformProbes` is non-null.
- `testing/iOS/StyleConverterTest/Screenshot/ScreenshotManager.swift`
  ~line 50 — hires flag → `renderer.scale = 4.0` for `_metric_probes/`.
- `testing/Android/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`
  — capture probe components into 4×-bounds Bitmap.
- `test-all.sh` — exclude `examples/_metric_probes/**` from the
  standard regression-capture loop.
- `testing/package.json` — add `fft.js`. (sharp/pngjs/ssim.js already
  present.)

### Do NOT modify
- `testing/compare-screenshots-metrics.mjs` — text helpers go in a
  new file (Section 5 rationale).
- `testing/classify-divergence.mjs` — Section 5 (a). 7-label
  classifier + `CLASSIFIER_VERSION = 1` stay stable.
- `testing/baseline-stats.mjs` — walks `pairs.*` only.
- `testing/baseline/` — regression baseline unaffected.

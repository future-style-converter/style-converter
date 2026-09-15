# wave-50 skeptic S7 — independent PNG / scorer replays of every predicted flip

Lane S7 of wave 50. **No devices, no browsers, no build systems** — every number
here is PNG arithmetic on the frozen `tools/titan/runs/wave49-final/` captures
and the frozen refs at
`tools/wpt/refs/9b5435e55e0b54a6cd09c1c563861eb3c999cef1/white-black-ink-font-lh-imgpad-htmlpins/`,
scored with the campaign's OWN scorer entry points (`diffComposedVsRef` from
`tools/titan/inject-wpt-block.mjs`, which internally runs `diffWebVsRef` +
`checkFuzzyMatch` + `computePresenceFailed` + `computeColorFailed` +
`computeCoverageRatioFailed` + `computeNovelInkFailed` + `degenerateVetoFailed`
+ `computeWptPass`). Nothing here re-implements a metric.

## Harness validation (the precondition for trusting any replay)

`replay-lib.mjs`'s `validate()` re-scores the UNMODIFIED capture and asserts
every scoring field against the wave49-final manifest row. **Every cell replayed
below reproduced its manifest numbers exactly** — `ssim`,
`pixelMismatchedCount`, `fuzzyDifferingPixels`, `fuzzyMaxChannelDelta`,
`presenceFailed`, `colorFailed`, `coverageRatioFailed`, `novelInkFailed`,
`wptFuzzyMatch`, `wptPass`. The only field that ever differed is `wptPass` on
`css-counter-styles/counter-suffix` ios/android, where the manifest carries
`null` + `scoreExcluded:"native-font-parity"` (stamped downstream in
`buildResults`, not in the diff function) while the replay computes `false` —
the raw metrics match to the last digit.

The 327-pair side is validated too: `replay-B11-pairs.mjs` reproduces the six
`tools/visual/cross-platform-expectations.json` `observed` blocks EXACTLY
(SSIM 0.9138 / 0.8671 / 0.8990 / 0.8987 / 0.9137 / 0.8644, Δpx 7.457 / 8.052 /
4.036 / 4.045 / 8.015 / 8.755, ΔE95 49.707 / 13.955) from the committed
baselines with pixelmatch `{threshold:0.02, includeAA:false}` + ssim.js fast +
`computeLabDeltaE(stride 4)`.

## Files

| file | what it is |
|---|---|
| `replay-lib.mjs` | the harness: cell resolution, `validate()`, `scorePng()` through the gate's composed path, and pure PNG mutators (repaint / fillBox / shiftUp / frame / inkBBox / deltaOver). |
| `replay-B1.mjs` … `replay-B11-pairs.mjs` | one replay per builder-lane prediction (see the table below). |
| `replay-misc.mjs` | the brief's B8 item, the floor-does-not-bind baseline proxies, and the corpus-wide iOS-vs-Android score gap. |
| `replay-gaps.mjs` | the empirical native/web raster-gap distributions that calibrate every "the twin's picture is the prediction" claim. |
| `diag-line-profiles.mjs` | row-ink band profiles of a test's three captures + its ref; used to LOCATE the bands the replays erase or shift. |
| `run-all.mjs` → `replays.json` | re-runs everything and freezes the raw output. `S7_SCRATCH=<tmpdir> node tools/titan/results/wave50-S7/run-all.mjs`. |

## Verdicts

| lane / cell | before | replayed after | verdict |
|---|---|---|---|
| **B1** `css-values/angle-units-001` web / ios / android | f 0.9990 / f 0.9974 / f 0.9966, all `colorFailed`, novelPx 10000 | P 0.9990 / P 0.9974 / P 0.9967, all vetoes clear, novelPx 0 | **PASS-PLAUSIBLE (strongest in the wave).** The 10000 red px repainted rgb(0,128,0) makes the web capture **pixel-identical (0 differing pixels)** to the already-passing sibling `angle-units-002`, and all three post-repaint metric blocks equal the siblings' to 4 dp. 002/003/004/005 verified P on all three as B1 states. |
| **B7-1** `css-images/gradient/gradient-hue-direction` | f 0.6241 / f 0.6235 / f 0.6230, capture 390×894 | **shift only: web P 0.9521, iOS f 0.9491, Android f 0.9495** · shift + the 2px rule painted: P 1.0000 / P 0.9970 / P 0.9973 | **MIXED — B7's proof does not cover the decisive component.** Removing the three 98px displacements is NOT "the entire failure": without a painted separator rule both natives land BELOW the bar (0.9491 / 0.9495). B7's own "zero pixels >8, maxDelta ≤1" is true only over the gradient BANDS (verified: web bands 0 over 8, maxDelta 1); full-frame the shift-only web copy still differs on 2148 px at maxDelta 101 — exactly the 3 × 2 × 358 px of missing rule. Sensitivity band (`replay-B7a-sensitivity.mjs`): ANY painted rule passes — full-width 0.9952/0.9956, flat grey 0.9749/0.9753, one row only 0.9960/0.9963, pure black 0.9506/0.9510. So the flip hinges entirely on the baked `border:1px inset #eeeeee` box actually painting on Compose/SwiftUI. |
| **B7-2** `css-contain/contain-html-overflow-002` | f 0.8709 / f 0.8705 / f 0.8698 | P 1.0000 / P 0.9996 / P 0.9989 | **PASS-PLAUSIBLE.** The -001 sibling cell numbers B7 quotes are exact. The -002 capture differs from the -001 capture by **exactly 40000 pixels on every platform** (the 200×200 red square) and by nothing else; substituting -001's capture into the -002 cell scores 1.0000/0.9996/0.9989. The unpatched -002 per-test IR differs from -001's in exactly TWO tokens (`Height 400` vs `200`, `Contain PAINT` vs `LAYOUT`); the patch fixes the first, leaving the one token B7 claims. |
| **B7-2b** `CSS2/css21-errata/s-11-1-1b-005` | f 0.0001 ×3 | — | **CLAIM-HOLDS (measurement).** 234000 of 234000 pixels black on **all three** platforms (B7's note says "the web capture"; it is all three). No flip claimed, none replayed. |
| **B7-3** `css-backgrounds/animations/background-color-animation-with-table{1,3}` web + iOS | f 0.9884 / f 0.9904, `coverageRatioFailed` | twin substitution (Android's own duplicate-free capture): **P 0.9998** on both | **PASS-PLAUSIBLE.** |
| **B7-3** …`-table4`, all three | web f 0.9884 · iOS f 0.9904 · **Android f 0.9888** | column erasure → **P 0.9945 / P 0.9943 / P 0.9936** | **PASS-PLAUSIBLE, and B7 UNDER-claims by one cell.** B7's "Android already suppresses the duplicate" is false for table4: its Android ink box is x[16–110] y[20–73] / 300 px against table1/3's x[19–27] y[23–58] / 100 px — Android paints the duplicate there too. +7, not +6. (B7's protected cells are safe: erasing changes nothing on Android table1/3, still 0.9998.) Measured boxes: ref x[19–27] y[23–58] 109 px; web to x=133, iOS to x=39, Android(1,3) x[19–27]. |
| **B4** `broken-symbols` | web P 0.9749 · iOS f 0.9486 · Android f 0.9490 | erase the duplicate line (y55-71) → **web P 1.0000 · iOS P 0.9713 · Android P 0.9713** | **PASS-PLAUSIBLE, +2 cells B4 did not claim.** B4 only argued web "moves toward the ref". |
| **B4** `counter-suffix` | web f 0.8867 · iOS 0.8883 (excluded) · Android 0.8901 (excluded) | erase-in-place 0.8897 / 0.8914 / 0.8931 (all f); **reflow (optimistic bound) web P 0.9818 · iOS f 0.9292 · Android f 0.9044** | **MIXED.** B4's "natives do not reach 0.95" holds and is if anything generous — even the optimistic reflow bound leaves Android at 0.9044, below the 0.93–0.96 band the brief carries. Web reaching 0.9818 is an unclaimed +1. **Denominator warning:** retro R13 unexcluded this test, so wave 50 scores two cells that wave 49 did not — they will land as new FAILS, not as losses. |
| **B3** `counter-cjk-decimal` / `attr-notype-fallback` / `attr-style-sharing-4` / `counter-reset-reversed-pseudo-001` / `-003`, Android | f 0.9407 / 0.9584 / 0.9959 / 0.9884 / 0.9961 — **four of the five fail on the presence / coverage-ratio VETO, not on SSIM** (`aCoveragePct` 0 / 0 / 0 / 0.105 / 0.057) | the iOS picture scored as the Android cell: **P 0.9934 / 0.9993 / 1.0000 / 0.9999 / 0.9999** | **PASS-PLAUSIBLE ×4, PASS-PLAUSIBLE-BUT-THIN ×1.** Canvas height alone is FREE: extending the iOS picture to Android's 696 / 1032 canvas with a white tail leaves the score bit-identical (`overflowInkPx` 0), so the Android canvas heights cannot keep these failing. The thin one is `counter-cjk-decimal`: headroom over the bar is 0.0434, and the iOS→Android raster gap in the same `cjk-decimal/` directory measures 0.0171 on the passing sibling `css3-counter-styles-005` but 0.0614 on `css3-counter-styles-001`. |
| **B2** `css-position/position-relative-006` Android | f 0.9966, `colorFailed`, novelPx 10000 | green square over the red one at the ref's position → **P 0.9967**, all vetoes clear | **PASS-PLAUSIBLE.** The capture's red box is x[16,88]-[115,187] / 10000 px, coincident to the pixel with the ref's green box. |
| **B2** `css-masking/clip-path/clip-path-contentBox-1d` Android | f 0.8775 | erase the 24200 red px → **P 0.9585** — equal to the web and iOS cells (0.9585) | **PASS-PLAUSIBLE.** |
| **B2** `…-1e` Android | f 0.8875 | erase 23126 reddish px (red box x[124,24]-[365,123], 23078 px at tol 40) → **P 0.9601** vs web/iOS 0.9696 | **PASS-PLAUSIBLE, thin (0.010 over the bar)** — the residual is the right-hand `border-radius` cap B2 itself names. |
| **B9** `line-clamp-006` / `-007` Android | f 0.9446 / f 0.9409 | iOS picture as the Android cell → **P 0.9822** both | **PASS-PLAUSIBLE, moderate risk:** 0.0322 headroom against a corpus-wide iOS→Android \|Δssim\| whose p90 is 0.0312. |
| **B9** `block-ellipsis-032.tentative` Android | f 0.9451 (iOS P 0.9577, web P 0.9834) | web picture as the Android cell → **P 0.9834** | **PASS-PLAUSIBLE, thin:** 0.0334 headroom against a web→Android \|Δssim\| p75 of 0.0222 / p90 of 0.0520; today's web→Android gap on this very test is 0.0383. |
| **B8** `background-attachment-fixed-inside-transform-1` | web P 0.9961 · iOS P 0.9843 · Android P 0.9629 — **already passing on all three** | web picture under the iOS cell → 0.9961, `lowContentDensity: true` | **CLAIM-NOT-FOUND.** No wave-50 lane artifact mentions this test (`grep` over `tools/titan/results/wave50-B*/` and `docs/BACKLOG.md`: zero hits). B8's `_note.md` is about box-shadow σ and a deferred text-shadow patch. The brief's "iOS predicted 0.99+" has no source in the tree. |
| **B11** the three committed Android baselines | — | independently re-measured from scratch: **091 Android 115×26 · 094 200×28 · 105 115×26**, web 115×30 / 200×30 / 115×30 | **CLAIM-HOLDS** — reproduces `baseline-ink-boxes.json` exactly. **But the brief's "web/iOS twins' 115x30 / 200x30 / 115x30" is wrong for iOS:** iOS is **50×30** on 091 and 105 (and 50×30 on 090, 50×42 on 097) — a 65px WIDTH divergence from web and Android that no ledger line covers and that B11's height fix does not touch. |

## The gate-blocking consequence of B11 (see `mustFixBeforeGate`)

`placeholder-floor-band.patch` **is applied to the tree** (`git diff
runtimes/compose/.../StyleApplier.kt` shows `placeholderFloorInsets` +
the reworked `placeholderFloorMinSize`), the three Android baselines are NOT
re-captured, and the six `cross-platform-expectations.json` lines that excuse
the divergence were written against the OLD geometry. Measured consequences:

* Post-fix Android geometry == web's (390×62, ink 115×30 / 200×30 / 115×30).
  The floor-does-not-bind proxies say what the Android-web pair then scores:
  `090_Button_Primary` Android-web **0.9999 / Δpx 0.000**.
* Post-fix **iOS-Android == today's iOS-web**, which I measured:
  091 **ssim 0.9540 · Δpx 0.608% · ΔE95 0**, 094 **0.9998 · 0.000% · 0**,
  105 **0.9510 · 0.753% · 0** — inside all three gate thresholds
  (ssim 0.95 / pixel 2% / ΔE95 5).
* `compare-screenshots.mjs` **fails** on a ledger line whose divergence has
  gone (`if (xGate.stale.length > 0) … "now passing, delete the line"`), so the
  fixture net exits non-zero on six lines unless they are removed in the same
  change that re-captures the baselines.
* 105_Edge_DeepNesting iOS-Android post-fix is **0.9510** — 0.001 over the bar.
  If the re-captured Android differs from web by even a hair more than
  `090_Button_Primary` does, that pair flips to a real `unexpected` failure
  instead of a stale line. Re-measure, do not assume.

## Calibration published for later waves (`replay-gaps.mjs`)

Over the 1366 wave49-final cells scored on all three platforms, the raw-SSIM
agreement between rasterisers is:

| pair | p50 | p75 | p90 | p95 | max |
|---|---|---|---|---|---|
| \|iOS − Android\| | 0.0008 | 0.0092 | 0.0312 | 0.0479 | 0.6855 |
| \|web − Android\| | 0.0025 | 0.0222 | 0.0520 | 0.1043 | 0.6617 |
| \|web − iOS\| | 0.0016 | 0.0152 | 0.0494 | 0.0884 | 0.6855 |

**Rule of thumb this supports:** a "the twin's picture is the prediction" flip
claim whose headroom over 0.95 is under ~0.031 is a coin flip, and under ~0.022
for a web→Android twin. Every such claim in this wave is listed above with its
headroom.

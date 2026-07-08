# COMPARE_METRICS — multi-metric image comparison spec

**Status:** spec for B-2 / B-3 implementer agents.
**Target file:** `testing/compare-screenshots.mjs` (currently 1-metric: SSIM + pixelmatch).
**Goal:** upgrade `diffPair()` (lines 354–383) to emit 7 metrics per pair, add a divergence classifier, extend `manifest.json` and the HTML report — all backward compatible with the existing `BASELINE=1 ./test-all.sh` regression check used by `testing/smoke.sh`.

Out of scope: B8 (sub-pixel), B9 (AA strategy), B10 (text kerning) — deferred per the proposal.

---

## 1. Metric catalogue

For each metric: definition · npm package · output · perf estimate · failure modes.

### B1 — pixelmatch raw count (existing, keep)
- **What:** count of pixels whose RGBA differs above an AA-tolerant threshold.
- **Library:** `pixelmatch@^7.1.0` (already in `testing/package.json` line 13). Keep.
- **Output:** `{ pixelMismatchedCount: integer, pixelMismatchedPct: float (0–100) }`.
- **Perf:** ~5 ms for a 390×500 PNG on M-series Mac.
- **Identical:** count = 0, pct = 0. **Diverged:** count = W×H, pct = 100.
- **Why keep:** the regression check in `compareBaseline()` (lines 435–437) gates on `pixelMismatchedPct > pixelThreshold`. Removing it would break `smoke.sh`.

### B2 — Per-channel SSIM (R/G/B/A separately)
- **What:** four independent SSIM scores, one per channel, so we can answer "the blue channel is what diverged".
- **Library:** `ssim.js@^3.5.0` (already installed). Run four times by extracting each channel into a single-channel ImageData (set R=G=B=channel, A=255).
- **Output:** `{ r: 0..1, g: 0..1, b: 0..1, a: 0..1 }`.
- **Perf:** ~4× the existing SSIM cost (~30 ms total for a 390×500 pair). Acceptable.
- **Identical:** all 1.0. **Diverged:** approaches 0; alpha is usually 1.0 even when RGB diverges (good signal).
- **Failure mode:** `safeSsim` (lines 385–395) already swallows `ssim.js` exceptions and returns `null` — mirror that. If any channel returns null, the whole object is null.

### B3 — Edge-map SSIM (Sobel)
- **What:** apply 3×3 Sobel to both images, then SSIM the edge maps. Catches AA-only differences without conflating fill differences.
- **Library:** `sharp@^0.33.0` (already installed) for the convolution; `ssim.js` for the score.
- **Implementation:**
  ```js
  // Sobel-X kernel; |Gx| + |Gy| approximation via two convolves + add is
  // overkill — single-pass magnitude using the diagonal-aware Scharr-3x3
  // would be more accurate, but the Sobel-X alone has been shown to
  // correlate well enough for screenshot-diff use. Spec: 3x3 Sobel-X.
  await sharp(buf)
    .greyscale()
    .convolve({ width: 3, height: 3, kernel: [-1, 0, 1, -2, 0, 2, -1, 0, 1] })
    .raw()
    .toBuffer()
  ```
- **Output:** `edgeSsim: 0..1`.
- **Perf:** ~25 ms (sharp is fast, plus one SSIM run).
- **Identical:** 1.0. **Diverged:** drops sharply when edges are misaligned (≤0.5).
- **Failure mode:** if greyscale-then-convolve fails, return null. Don't fall back to RGB SSIM (would defeat the purpose).

### B4 — Color histogram KL divergence
- **What:** 256-bucket histogram per channel; compute `D_KL(P‖Q) = Σ P(i) log(P(i)/Q(i))`. Position-independent — catches "right colors, wrong layout" vs "right layout, wrong colors".
- **Library:** none; histogram is trivially computable from the existing PNG buffer (a single pass over `data` already in memory). Use a small inline helper. Adding `chroma.js` or similar would just bloat the install for no gain.
- **Output:** `{ r: float ≥ 0, g: float ≥ 0, b: float ≥ 0 }` (lower = more similar; 0 = identical distribution).
- **Perf:** ~3 ms (one buffer pass).
- **Identical:** 0 on all channels. **Diverged:** unbounded; in practice ≤ 4 for "very different".
- **Edge case:** zero-count buckets cause `log(0)` — clamp using Laplace smoothing (`P_smooth(i) = (count(i) + 1) / (total + 256)`) before the log. Spec this in code.

### B5 — Perceptual hash (pHash, 64-bit)
- **What:** 8×8 DCT-based fingerprint; Hamming distance ≤ 10 ≈ visually identical, ≤ 20 ≈ similar, > 20 ≈ different.
- **Library:** `sharp-phash@^2.2.0` (uses sharp directly, no native deps beyond what we already have, ~2 KB). Alternatives considered:
  - `imghash` — uses `jimp`, a 4 MB dependency tree. Rejected.
  - `phash-image` — abandoned, last publish 2017. Rejected.
- **Output:** `{ hex: '64-char hex string', hammingDistance: 0..64 }`.
- **Perf:** ~15 ms per pair (compute both hashes + Hamming).
- **Identical:** distance 0. **Diverged:** approaches 32 (random images average ~32 bits different).
- **Failure mode:** sharp-phash throws on tiny images (< 32×32). Catch and return `{ hex: null, hammingDistance: null }`.

### B6 — DSSIM
- **What:** `D_SSIM = (1 - SSIM) / 2`, but Kornel Lesinski's `dssim` uses an MS-SSIM variant that correlates better with perception than vanilla SSIM. Higher value = more different.
- **Library:** `dssim@^3.x` — but this is a Rust CLI binary, not a pure-JS lib. The pure-JS option is to **derive DSSIM from the existing ssim.js result** (`dssim = (1 - mssim) / 2`). This is mathematically the canonical DSSIM definition and avoids a native-binary install that would break CI on machines without Rust.
- **Recommendation:** **derive, don't install.** Compute `dssim = (1 - row.ssim) / 2` in the metric block. If a future need for true MS-DSSIM arises, swap in `dssim-cli` then.
- **Output:** `dssim: 0..0.5` (0 = identical, 0.5 = max).
- **Perf:** ~0 ms (free; arithmetic on existing SSIM).
- **Open question:** see Section 8.

### B7 — CIE LAB ΔE (CIEDE2000)
- **What:** perceptual color distance in CIE LAB space. ΔE < 1 = imperceptible, < 2 = barely perceptible, > 5 = clearly different.
- **Library:** `culori@^4.0.0`. Tree-shakable, MIT, actively maintained, used by Tailwind v4. Specific imports:
  ```js
  import { converter, differenceCiede2000 } from 'culori';
  const toLab = converter('lab65');
  const dE = differenceCiede2000();
  ```
  Alternatives considered:
  - `chroma.js` — supports ΔE but the whole bundle is 100 KB+ for one function. Rejected.
  - `color-diff` — last publish 2019, no CIEDE2000, only ΔE76 (less perceptual). Rejected.
- **Output:** `{ mean: float ≥ 0, max: float ≥ 0, p95: float ≥ 0 }`.
- **Perf:** ~120 ms for a 390×500 pair (the slowest metric). Mitigation: stride-sample 1-in-4 pixels — accuracy loss is < 1% for screenshots and brings cost to ~30 ms.
- **Identical:** all 0. **Diverged:** mean rises slowly; max can be huge. p95 is the most actionable (filters out outliers).
- **Failure mode:** transparent pixels (alpha 0) should be excluded from the mean (otherwise the bg colour dominates). Document this in the impl.

---

## 2. Canonical per-pair output shape

```ts
// testing/report/manifest.json — per `pairs.<pair-name>` block
type ImagePairMetrics = {
  // ── Existing fields (REQUIRED — don't break compat) ────────────────────
  ssim: number | null,                  // 0..1 or null on decode failure
  pixelMismatchedCount: number,         // integer ≥ 0
  pixelMismatchedPct: number,           // 0..100, 3 decimal places
  diffImage: string,                    // relative path to diff PNG

  // ── New B-section metrics (OPTIONAL during rollout) ────────────────────
  // Each independently nullable so partial rollout doesn't crash the
  // report renderer or the regression check. Missing field === metric
  // wasn't computed (impl gap). null === computed but failed (e.g.
  // sharp threw on a corrupt PNG). Both render as "—" in the HTML.

  dssim?:           number | null,                          // 0..0.5
  perChannelSsim?:  { r: number, g: number, b: number, a: number } | null,
  edgeSsim?:        number | null,                          // 0..1
  histogramKL?:     { r: number, g: number, b: number } | null,
  pHash?:           { hex: string | null, hammingDistance: number | null } | null,
  labDeltaE?:       { mean: number, max: number, p95: number } | null,

  // ── Derived classification (REQUIRED once any new metric ships) ─────────
  divergence?: 'identical' | 'sub-pixel-noise' | 'color-drift'
             | 'edge-shift' | 'structural-divergence' | 'mixed' | 'unknown',
};
```

**Backward-compat contract:** the HTML report and regression check must treat *all* new fields as optional and degrade to "metric unavailable" rather than throw. See Sections 5 + 6 for the rollout path.

---

## 3. Per-metric pass/fail thresholds

Threshold strategy per metric. **(a) hard-coded** = literal value in code; **(b) data-derived** = computed from a one-off baseline-stats run over the 327 current pairs, then committed as a constant.

| Metric        | Strategy | Threshold                              | Rationale |
|---------------|----------|----------------------------------------|-----------|
| `ssim`        | (a)      | regression if `< 0.95` (existing)      | Keep current behaviour; `--ssim-threshold` arg already exposes this. |
| `pixelMismatchedPct` | (a) | regression if `> 2%` (existing)        | Keep existing `--pixel-threshold` arg. |
| `dssim`       | (a)      | regression if `> 0.025`                | Equivalent to ssim < 0.95; redundant during rollout but trivial to compute. |
| `perChannelSsim` | (a)   | regression if `min(r,g,b) < 0.90`      | Slightly looser than overall SSIM because per-channel is noisier. Alpha excluded — alpha mismatches show up via `histogramKL.a` aren't useful here. |
| `edgeSsim`    | (b)      | regression if `< (p5 of baseline) − 0.05` | Edges are noisy; need data to set a sane floor. B-2 must run a baseline-stats pass first. Until then: hard-code `< 0.80` as a starter. |
| `histogramKL` | (b)      | regression if `max(r,g,b) > p95 × 1.5` | Distribution of KL values is unknown; need empirical p95. Until then: hard-code `> 0.5` per channel. |
| `pHash`       | (a)      | regression if `hammingDistance > 10`   | Industry-standard pHash threshold (Wang et al., 2008). |
| `labDeltaE.p95` | (a)    | regression if `> 5.0`                  | "Clearly different" boundary per CIE; p95 (not mean) so single-pixel outliers don't fail us. |
| `labDeltaE.mean` | (a)   | informational only (no gate)           | Surface in report but don't fail on it; mean is too forgiving. |

**Action item for B-2:** after wiring B3 + B4, run a one-shot `node testing/baseline-stats.mjs` script (B-2 to author) that re-scores the 327-pair baseline with the new metrics and prints percentiles. Replace `< 0.80` and `> 0.5` placeholders with the empirical values.

---

## 4. Divergence classifier (the headline value)

Each pair gets exactly **one** label from a fixed set. The decision tree below evaluates top-to-bottom and returns the first match — order matters.

```js
function classify(m) {
  // m has all the metric fields from Section 2.

  // 1. IDENTICAL — bitwise-identical or close enough that the pixel-diff
  //    reports zero mismatches. Highest bar; strictest gate.
  if ((m.ssim ?? 0) >= 0.99
      && m.pixelMismatchedCount === 0
      && (m.pHash?.hammingDistance ?? 99) <= 2) {
    return 'identical';
  }

  // 2. SUB-PIXEL-NOISE — perceptually identical but a few pixels jiggled.
  //    This is the dominant case for cross-platform AA differences.
  if ((m.ssim ?? 0) >= 0.95
      && m.pixelMismatchedPct < 1
      && (m.labDeltaE?.p95 ?? 0) < 2) {
    return 'sub-pixel-noise';
  }

  // 3. EDGE-SHIFT — colors are right but shapes / borders moved.
  //    Triggered by 1px layout drifts, font-baseline shifts, etc.
  //    Check BEFORE color-drift because "edges moved" implies labDeltaE
  //    is non-zero on the moved-pixel boundaries — color-drift would
  //    false-positive otherwise.
  if ((m.edgeSsim ?? 1) < 0.85
      && (m.labDeltaE?.mean ?? 0) < 2) {
    return 'edge-shift';
  }

  // 4. COLOR-DRIFT — shapes are right but colors shifted (e.g. sepia
  //    filter rendered with slightly different coefficients).
  if ((m.labDeltaE?.mean ?? 0) > 5
      && (m.edgeSsim ?? 0) >= 0.95) {
    return 'color-drift';
  }

  // 5. STRUCTURAL-DIVERGENCE — both edges and overall structure broken.
  //    A real rendering bug: missing element, wrong layout, swapped order.
  if ((m.ssim ?? 1) < 0.85 && (m.edgeSsim ?? 1) < 0.85) {
    return 'structural-divergence';
  }

  // 6. MIXED — multiple categories triggered weakly; needs human review.
  //    Default for everything that doesn't cleanly fit a single bucket.
  if ((m.ssim ?? 1) < 0.97 || (m.pixelMismatchedPct ?? 0) > 1) {
    return 'mixed';
  }

  // 7. UNKNOWN — metrics weren't computed (rollout in progress) AND
  //    we couldn't derive a label from existing fields. Distinct from
  //    'identical' so the report can surface "we don't know yet".
  return 'unknown';
}
```

**Rationale for the order:** `identical` first (cheapest, most-common test); `sub-pixel-noise` second (covers ~80% of remaining pairs in current 327-baseline); `edge-shift` before `color-drift` (color always changes when edges move, so the cheaper edge check has to win); `structural-divergence` last among the actual-divergence labels (it's the most catastrophic — anything that gets here needs a human); `mixed` as the catch-all; `unknown` only when metrics genuinely missing.

---

## 5. HTML report extension

Modify `renderHTML()` (lines 494–626), `renderRow()` (lines 628–679), and `renderPair()` (lines 700–718). All additive — old rows still render correctly when new fields are absent.

**Per-pair cell additions:**
1. **Divergence badge** at the top of each `.pair` div, color-coded:
   - `identical` → green
   - `sub-pixel-noise` → light green
   - `color-drift` → yellow (it's perceptual, not structural)
   - `edge-shift` → yellow
   - `structural-divergence` → red
   - `mixed` → orange
   - `unknown` → grey
2. **Expanded metrics row** under the existing `SSIM / Δpx` line:
   ```
   ΔE p95 ▸ 1.2 · pHash 4 · edge 0.94 · KL r:0.02 g:0.01 b:0.03
   ```
3. **`<details>` expand** for the per-channel SSIM breakdown (collapsed by default to keep the row compact).
4. **Backward compat:** wrap each new-field render in `?.` access; if any field is absent, show `—` and don't break.

**Per-row "headline divergence":** in the `<h2>` block (line 660), add a single badge for the worst-divergence-label across all three pairs (e.g. if `iOS-Android` is `structural-divergence` and the others are `identical`, the row badge is `structural-divergence`). Severity ordering: `structural > color-drift > edge-shift > mixed > sub-pixel-noise > identical > unknown`.

**Filter UI:** add a fifth dropdown to `.controls` (line 541): "filter by divergence" with one option per label + "all". Wire to existing client-side `apply()` function (line 576).

**Don't change:** the existing `data-min-ssim` sort attribute, the search box, the diff image rendering. Those keep working untouched.

---

## 6. Manifest.json shape extension

**Where the new metrics live:** flat inside the existing `pairs.<pair-name>` block (see shape in Section 2). Reasons not to nest under `metrics: {…}`:
- Existing CI / tooling reads `pair.ssim` directly — nesting would break it
- Flat is faster to inspect in the report
- All metric names are already namespaced by their type (`ssim`, `dssim`, `edgeSsim`, etc.) — no collision risk

**Manifest version field:**
```json
{
  "manifestVersion": 2,            // NEW — was implicitly 1
  "generatedAt": "...",
  "inputLabel": "...",
  "thresholds": {                  // EXTENDED
    "ssim": 0.95,
    "pixel": 2,
    "edgeSsim": 0.80,              // NEW
    "labDeltaEP95": 5.0,           // NEW
    "pHashHamming": 10             // NEW
  },
  "rows": [ ... ]
}
```

Bumping to v2 lets external tooling detect "this report has the new metrics" with one field check rather than probing each row.

**Regression-check rollout path** (for `compareBaseline()`, lines 397–443):

Phase A (B-2 ships first): keep the existing `regressed = ssim < threshold || pixelPct > threshold` gate. Add new metrics to the manifest but don't gate on them yet. **CI behavior unchanged.**

Phase B (after baseline-stats pass): expand the `regressed` predicate to OR in the new gates from Section 3 — but gate-on only the metrics that have empirically-validated thresholds (pHash, labDeltaE.p95). Keep edgeSsim / histogramKL informational until Section 3's data-derived thresholds are committed.

Phase C (post-rollout, B-3 territory): switch the headline regression signal from raw SSIM to `divergence === 'structural-divergence'`. The existing SSIM gate becomes a fallback for `unknown`-label rows.

`smoke.sh` doesn't need changes — it just inspects the script's exit code.

---

## 7. Implementation order for B-2 / B-3 agents

Ordered by risk (lowest first) and dependency. A B-2 agent can claim any contiguous prefix of this list and ship it in one PR.

| # | Metric | Why this order | Est. effort |
|---|--------|----------------|-------------|
| 1 | **B6 dssim** (derived) | Zero new dependency; trivial arithmetic on existing `ssim`. Safe canary for the manifest-shape change. | 30 min |
| 2 | **B4 histogramKL** | No new dep (inline helper). One buffer pass. Establishes the "new metric without new lib" pattern. | 1 hr |
| 3 | **B2 perChannelSsim** | Reuses already-installed `ssim.js`. Four invocations. No new lib. | 1 hr |
| 4 | **B5 pHash** (`sharp-phash`) | Single new dep, no native build (uses already-installed sharp). | 1 hr |
| 5 | **B3 edgeSsim** (sharp Sobel) | No new dep but the trickiest: must serialize sharp output back to a `PNG`-compatible buffer for ssim.js. | 2 hr |
| 6 | **B7 labDeltaE** (`culori`) | New dep + the slowest metric (~120 ms). Implement stride-sampling. | 3 hr |
| 7 | **Divergence classifier** | Pure function over the metric block. Trivially unit-testable. **Depends on 1–6 being present** (else mostly returns `unknown`). | 2 hr |
| 8 | **Baseline-stats pass** | One-off node script `testing/baseline-stats.mjs` that re-scores the 327 pairs and emits per-metric percentiles. Output replaces the placeholder thresholds in Section 3. | 1 hr |
| 9 | **HTML report extension** | Section 5. Touches `renderRow` + `renderPair` + the `<style>` block. | 3 hr |
| 10 | **Regression-check Phase B** | Extend `compareBaseline()` predicate. | 1 hr |

Total: ~15 hours. B-2 should claim 1–6 (~8 hr); B-3 picks up 7–10.

**Test plan per item:**
- For 1–6: add a unit test under `testing/` that reads two known PNGs (ship one as fixture: identical pair returns expected zero/perfect; clearly-different pair returns expected nonzero) and asserts the metric value is in the expected range.
- For 7: golden-test the classifier against a hand-labelled set of 10 pair-metric blocks.
- For 9: run `node compare-screenshots.mjs` against the existing captures, open `report/index.html`, confirm new badges + metrics render and old rows still work.

---

## 8. Open questions (need human decision before B-2 starts)

1. **DSSIM derivation vs install:** Section 1 / B6 recommends deriving `dssim = (1 - mssim) / 2` from existing `ssim.js` rather than installing the Rust `dssim` CLI. **Decision needed:** is the perceptual fidelity of true MS-DSSIM worth a native-binary install (and the CI fragility that comes with it)? Default = derive.

2. **Stride-sampling LAB ΔE:** computing CIEDE2000 on every pixel costs ~120 ms per pair × 327 pairs = ~40 s added to a baseline run. Stride-1-in-4 brings it to ~10 s with < 1% accuracy loss. **Decision needed:** ship stride-1-in-4 by default, or full-fidelity with a `--fast-lab` opt-in flag? Default = stride.

3. **`manifestVersion: 2` bump:** any external tool currently consuming `manifest.json`? If yes, they need a heads-up. **Action needed:** grep for consumers (the only one I found is `smoke.sh` reading the script's exit code, not the JSON directly). If clean, bump to v2 freely.

4. **Edge SSIM kernel:** Section 1 / B3 specifies a 3×3 Sobel-X. A 5×5 Scharr is more accurate but 4× slower. **Decision needed:** Sobel-3 (recommended, fast) or Scharr-5 (accurate, slow)? Default = Sobel-3.

5. **Per-channel SSIM threshold of `< 0.90`:** Section 3 calls this a starter. After the baseline-stats pass, should the regression check actually gate on per-channel SSIM, or keep it informational? Risk: noisy single-channel false-positives. **Recommendation:** informational-only at first; promote to gate only if it catches a real regression that overall-SSIM missed.

6. **`pHash` size — 64-bit vs 256-bit:** `sharp-phash` defaults to 64-bit (8×8 DCT). 256-bit (16×16 DCT) gives finer discrimination at the cost of a larger fingerprint. **Decision:** default 64-bit; bump only if 64-bit shows false-identicals in practice.

7. **Where does the divergence classifier live?** Inline in `compare-screenshots.mjs` (simple, one consumer) or as a separate `testing/classify-divergence.mjs` (testable in isolation)? **Recommendation:** separate file — Section 7 item 7 needs unit tests anyway.

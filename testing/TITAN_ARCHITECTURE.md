# TITAN — Web Platform Tests integration architecture

**Status:** spec for the TITAN-PHASE-N implementer agents.
**Target tree:** new `testing/wpt/` (gitignored corpus mirror), new
`testing/titan/` (orchestration code), additive top-level
`wpt:` block in `testing/report/manifest.json` (manifest bump
`3 → 4`), new `testing/report/titan.html` dashboard.
**Companion specs:** `testing/COMPARE_METRICS.md` (B1–B7),
`testing/COMPARE_METRICS_B8-B10.md` (typography probes),
`testing/RESEARCH.md` A1 (the original WPT proposal).
**Parallel work:** TITAN-PREP-A is making the existing capture
pipeline fully background — no Simulator.app GUI, no emulator
window, no focus steal. This spec **assumes** that work is in
place (see Section 6 for the dependencies it imposes on us).

This document is the build plan for **Project TITAN** —
integrating the Web Platform Tests (WPT) CSS reftest corpus into
the 3-platform comparison pipeline so that every CSS reftest in
the upstream corpus runs through Style-Converter
(CSS → IR → Compose / SwiftUI / CSS) and is compared against the
reference image on iOS, Android, and Web, producing
classifier-labelled output that funnels into the existing B1–B7
metric infrastructure.

Section numbering mirrors `COMPARE_METRICS.md` for parity.

---

## 1. Goals + non-goals

### 1.1 Goal

Every CSS reftest in WPT runs through Style-Converter and emits
the same per-pair record we already emit for the 327-pair
visual-test baseline:

```
WPT test.html (CSS source)
   │
   ▼
[ extractor ] — strip <style>+<body> → IR fixture JSON
   │
   ▼
examples/wpt/<spec-section>/<test-name>.json
   │
   ▼
[ test-all.sh-equivalent driver ]
   │
   ├──▶ iOS capture (background simulator)
   ├──▶ Android capture (background emulator)
   └──▶ web capture (headless puppeteer)
        │
        ▼
[ compare-screenshots.mjs ] — B1–B7 metrics + classifier
        │
        ▼
manifest.json:wpt.results.<test-path>
        │
        ├─ pairs: { iOS-Android, iOS-web, Android-web }   ← existing
        ├─ browserRef: { iOS-ref, Android-ref, web-ref }  ← NEW (Section 5)
        └─ divergence: <classifier-label>
```

The classifier output **is** the result. We do not pass/fail
individual tests against an absolute spec-conformance bar;
instead the histograms emitted per spec section
(`/css-flexbox/` vs `/css-grid/`) are the diagnostic signal —
they tell us **which CSS modules need engine work, on which
platform**.

### 1.2 Non-goals

- **Non-goal (initial):** WPT non-reftest categories — anything
  that depends on `testharness.js` JS execution (assertions,
  manual visual tests, performance timing). Per WPT's own docs
  ([web-platform-tests.org / writing-tests](https://web-platform-tests.org/writing-tests/index.html)),
  the corpus mixes `testharness.js` tests, reftests, and manual
  tests; only reftests have a deterministic image-comparable
  reference. Pure layout/style reftests only in v1.
- **Non-goal:** matching browser pixel-perfect on every test.
  Many WPT tests use AA-sensitive 1-px features; our renderers
  (Skia / CoreGraphics / Chromium) will never bitwise-match each
  other. The B1–B7 classifier already accepts that —
  `sub-pixel-noise` and `mixed` are the *expected* output for
  most well-converted tests. `structural-divergence` is the
  signal that there's a real engine bug.
- **Non-goal:** running WPT's own Python harness (`./wpt run`).
  That harness expects `wpt run firefox path/to/test`-style
  invocations with Selenium-driven browsers. We bypass it
  entirely (Section 3.3).
- **Non-goal:** gating CI regression on TITAN runs in v1. The
  327-pair regression baseline (`smoke.sh`) stays the gate;
  TITAN is exploration / coverage-mapping (Section 10 Q7).

---

## 2. WPT corpus characterization

### 2.1 Test counts (sourced + estimated)

The WPT repo at the recommended pinned commit (Section 3.2)
contains the following reftest counts. **These numbers must be
re-confirmed by the Phase 0 implementer** by running
`git ls-files 'css/**/*.html' | wc -l` and
`grep -lr 'rel="?match"?' css/ | wc -l` against the actual
checkout. The proposal in `RESEARCH.md:23` cites "30k tests" —
that is the total WPT count across all categories; the
CSS-reftest subset is smaller.

| Subtree                  | HTML files (approx) | Reftests (approx) | Source        |
|--------------------------|--------------------:|------------------:|---------------|
| `/css/` (all)            | ~24,000             | ~14,000           | wpt repo, recent |
| `/css/css-flexbox/`      | ~600                | ~500              | wpt repo |
| `/css/css-grid/`         | ~1,400              | ~1,200            | wpt repo |
| `/css/css-backgrounds/`  | ~700                | ~600              | wpt repo |
| `/css/css-text/`         | ~2,500              | ~2,000            | wpt repo |
| `/css/css-fonts/`        | ~600                | ~300              | wpt repo |
| `/css/css-transforms/`   | ~600                | ~500              | wpt repo |
| `/css/css-color/`        | ~200                | ~150              | wpt repo |
| `/css/CSS2/`             | ~6,000              | ~5,000            | wpt repo |

The "approx" disclaimer is load-bearing: **WPT moves fast** —
the repo gains ~50 tests/week
([WPT activity dashboard](https://wpt.fyi)). The Phase 0
implementer should treat these as order-of-magnitude figures
and write the actual numbers (per `git ls-files | wc -l` against
the pinned commit) into the manifest's `wpt.totalTests` field
(Section 7).

### 2.2 CSS-relevant subdirectories

These are the WPT directories that map to Style-Converter's IR
catalogue (per the category list in CLAUDE.md "Style-engine
architecture"):

```
/css/css-align/           → layout/flexbox + layout/grid alignment
/css/css-animations/      → animations/
/css/css-backgrounds/     → background/
/css/css-borders/         → borders/
/css/css-box/             → spacing/ + sizing/
/css/css-cascade/         → global/ (cascade layers, !important)
/css/css-color/           → color/
/css/css-color-adjust/    → color/ + appearance/
/css/css-color-hdr/       → color/ (skip in v1 — HDR not on-spec)
/css/css-conditional/     → @supports — partial; bucket-B
/css/css-contain/         → container/
/css/css-content/         → content/
/css/css-counter-styles/  → counters/ + lists/
/css/css-display/         → layout/
/css/css-easing/          → animations/ (timing functions)
/css/css-flexbox/         → layout/flexbox/
/css/css-fonts/           → typography/ font-family / font-feature-settings
/css/css-gaps/            → spacing/ gap
/css/css-grid/            → layout/grid/
/css/css-images/          → images/
/css/css-inline/          → typography/
/css/css-lists/           → lists/
/css/css-logical/         → spacing/ logical props
/css/css-masking/         → effects/mask/ + effects/clip/
/css/css-multicol/        → columns/
/css/css-overflow/        → layout/ + scrolling/
/css/css-position/        → layout/position/
/css/css-pseudo/          → effects/ + content/
/css/css-rhythm/          → rhythm/
/css/css-scroll-snap/     → scrolling/
/css/css-shadow/          → effects/shadow/
/css/css-shapes/          → effects/shapes/ + shapes/
/css/css-sizing/          → sizing/
/css/css-text/            → typography/ text-* (most)
/css/css-text-decor/      → typography/ text-decoration*
/css/css-transforms/      → transforms/
/css/css-transitions/     → animations/ (transitions)
/css/css-ui/              → interactions/ + appearance/
/css/css-values/          → primitive parsers (calc, var, units)
/css/css-variables/       → global/ (custom props) — bucket-B/C
/css/css-view-transitions/→ skip in v1 (not implementable in IR)
/css/css-writing-modes/   → typography/ writing-mode + direction
/css/CSS2/                → CSS Level 2 grab bag — many bucket-A
/css/cssom/               → CSSOM — JS-only, bucket-C entirely
/css/cssom-view/          → cssom-view — JS-only, bucket-C entirely
/css/filter-effects/      → effects/filter/
/css/mediaqueries/        → @media — bucket-B (size queries OK; UA-only ones not)
/css/motion/              → motion-path — partial
/css/selectors/           → selectors — bucket-C (we don't implement selector matching)
```

Subtrees explicitly **excluded** from v1 because the IR doesn't
model them: `cssom*`, `selectors`, `css-view-transitions`,
`css-color-hdr`, `css-cascade` (cascade layers — IR is
flat-property), `css-variables` (we don't resolve `var()` —
runtime-dependent per CLAUDE.md "IR Value Normalization").

### 2.3 Reftest format

Per [WPT reftest docs](https://web-platform-tests.org/writing-tests/reftests.html):

```html
<!-- /css/css-backgrounds/background-color-001.html -->
<link rel="match" href="background-color-001-ref.html">
<style>div { width:100px; height:100px; background:rgb(0,128,255); }</style>
<div></div>
```

```html
<!-- background-color-001-ref.html -->
<style>div { width:100px; height:100px; background:#0080FF; }</style>
<div></div>
```

The contract: a real browser renders both files; the diff is
expected to be 0 pixels (or within a `<meta name="fuzzy">`
tolerance — see Section 5.3). Both files render in an 800×600
window, top-left aligned, white background by spec.

For TITAN's purposes the two files together form a **triplet**:
the CSS-under-test (`*.html`), the simpler reference
(`*-ref.html`), and the optional `<meta name="fuzzy">` tolerance.
We consume all three.

### 2.4 What we ignore from WPT's harness

- `<script src="/resources/testharness.js">` — JS assertion
  framework. Reftests don't use it; if a test imports it, that's
  a heuristic for bucket-C.
- `<script src="/resources/reftest-wait.js">` — explicit
  `class="reftest-wait"` body class signals the renderer to wait
  until a JS event removes the class. Bucket-B (we'd have to
  evaluate the JS); bucket-C if the JS is non-trivial.
- `wpt manifest` (`MANIFEST.json`) — WPT ships a precomputed
  index of every test's metadata. We **use** this (Section 4.1)
  rather than re-walking the tree.
- `wpt run` Python harness — bypassed entirely.

---

## 3. WPT acquisition strategy

### 3.1 Cloning

```bash
# testing/titan/fetch-wpt.sh
git clone --depth 1 --branch <PINNED_REF> --filter=blob:none \
    https://github.com/web-platform-tests/wpt testing/wpt
```

Why these flags:
- `--depth 1`: we don't need history (~6 GB saved). Section 10
  Q1 covers re-pinning.
- `--filter=blob:none`: partial clone; only fetch blobs that are
  walked. WPT is ~3 GB packed but ~10 GB unpacked because of
  binary fixtures. Partial clone halves disk.
- `--branch <PINNED_REF>`: locks to a specific commit
  (Section 3.2).

`testing/wpt/` is gitignored (add to `.gitignore` in Phase 0
PR — see Section 8). Committing the corpus is a non-starter:
even sparse it's 1-2 GB of HTML/CSS that has its own upstream.

### 3.2 Pinning strategy

The corpus must be reproducible. Three pinning options:

| Strategy           | Pro                                  | Con                                       |
|--------------------|--------------------------------------|-------------------------------------------|
| HEAD               | Always-current spec coverage         | Daily flake; non-reproducible             |
| Latest "epochs"    | WPT's own monthly stable snapshot ([wpt.live/epochs](https://wpt.live/epochs/)) | Still updates; needs subscription |
| **Frozen commit** | Fully reproducible; matches `wpt-ref` env var | Manual re-pin every ~3 months             |

**Recommendation: frozen commit, re-pin quarterly.** Default to
the most recent `epochs/three_hourly` commit at the time of
Phase 0 PR (the implementer should record the exact SHA in
`testing/titan/WPT_REF` — a one-line file). Override via
`WPT_REF=<sha>` env var.

```bash
# testing/titan/fetch-wpt.sh
WPT_REF="${WPT_REF:-$(cat testing/titan/WPT_REF)}"
git -C testing/wpt fetch --depth 1 origin "$WPT_REF"
git -C testing/wpt checkout FETCH_HEAD
```

### 3.3 Sparse checkout (saves 80% of disk)

WPT's full tree is ~10 GB unpacked (we have all of `/html/`,
`/dom/`, `/svg/`, etc. that we don't use). Sparse-checkout to
just `/css/`, `/css-fonts/`, and `/resources/` (the latter for
shared CSS files referenced by tests):

```bash
git -C testing/wpt sparse-checkout init --cone
git -C testing/wpt sparse-checkout set css resources fonts
```

Disk after sparse: ~1.5 GB. Acceptable for local + CI.

### 3.4 Refresh cadence

Quarterly re-pin via PR:
1. Update `testing/titan/WPT_REF`
2. Re-run Phase 0 bucketer (Section 4); commit new
   `testing/wpt-buckets.json`
3. Diff the new buckets vs the old — any tests that moved from
   bucket-A to bucket-C deserve a comment in the PR

---

## 4. WPT-to-IR conversion

### 4.1 Pre-classification pass (the bucketer)

Before any captures, walk the corpus once and classify every
reftest into A/B/C. Output: `testing/wpt-buckets.json`
(committed; small, ~2 MB JSON).

```js
// testing/titan/bucket-wpt.mjs
{
  "wptRef": "<sha>",
  "generatedAt": "2026-05-11T12:00:00Z",
  "buckets": {
    "A": [ "css/css-backgrounds/background-color-001.html", ... ],
    "B": [ "css/css-fonts/font-variant-002.html", ... ],
    "C": [ "css/css-grid/script-driven-001.html", ... ]
  },
  "reasons": {
    "css/css-grid/script-driven-001.html": "imports /resources/testharness.js",
    "css/css-fonts/font-variant-002.html": "uses @font-face — fonts/ acquisition needed",
    ...
  }
}
```

Bucket assignment heuristics (per-file string-grep on the test
HTML — fast, no DOM parse needed for v1):

| Heuristic (regex)                                       | Bucket | Reason                                    |
|---------------------------------------------------------|:------:|-------------------------------------------|
| `<script src="/resources/testharness\.js">`             |   C    | testharness-driven, not a reftest         |
| `class="reftest-wait"`                                  |   C    | requires JS event to settle               |
| `<canvas` or `<iframe`                                  |   C    | IR doesn't model canvas/iframe            |
| `<svg` with `<script>` inside                           |   C    | scripted SVG                              |
| `<svg` without scripts                                  |   B    | static SVG — partial IR coverage          |
| `@font-face` referencing `/fonts/`                      |   B    | needs font asset; lossy without it        |
| `@supports`, `@container`                               |   B    | conditional rules — IR doesn't model      |
| `var(--`                                                |   B    | custom-prop fallback chains — lossy       |
| `calc(` with non-px units                               |   B    | resolves at runtime; mark lossy           |
| `vw`, `vh`, `vmin`, `vmax`, `dvw`, `dvh`                |   B    | viewport units — IR `null` per CLAUDE.md  |
| no `rel="match"` link in test                           |   C    | not a reftest                             |
| ref file missing on disk                                |   C    | broken reference                          |
| **everything else**                                     |   A    | cleanly convertible                       |

**Bucket A** = clean conversion possible; feed full pipeline.
**Bucket B** = partial conversion; include in pipeline but mark
`"lossy": true` in manifest entry so report can filter.
**Bucket C** = skip; record in `wpt-buckets.json` so the gap is
auditable, but don't try to render.

### 4.2 Extractor

Per bucket-A or bucket-B test:

```js
// testing/titan/extract-fixture.mjs
//
// Read test.html → parse <style> blocks + <body> tree → emit
// IR-friendly JSON in the shape of examples/properties/*.json.
//
// Strategy: use a real DOM parser (jsdom — already an indirect
// dep via puppeteer). For each top-level child of <body>:
//   - assign a component name "<test-stem>__<index>"
//   - flatten its CSS (inline + matching selectors from <style>)
//     into the existing IR property dict
//   - write to examples/wpt/<spec-section>/<test-stem>.json
```

Rules:
- One WPT test → one JSON fixture; each top-level body child
  becomes one IR component (mirrors `examples/properties/*.json`
  shape). Most reftests are 1–3 components, so the average
  fixture is small.
- Reference file (`*-ref.html`) is extracted the same way but
  written to `examples/wpt/<spec-section>/<test-stem>__ref.json`.
  The `__ref` suffix is the convention the comparison driver
  uses to recognize ref components.
- The original WPT relative path is preserved in a top-level
  `"_wpt"` block:
  ```json
  { "_wpt": { "test": "css/css-backgrounds/background-color-001.html",
              "ref":  "css/css-backgrounds/background-color-001-ref.html",
              "bucket": "A",
              "fuzzy":  { "maxDifference": 15, "totalPixels": 300 } },
    "components": { ... } }
  ```
- `<meta name="fuzzy">` tolerances are parsed and stored — used
  in Section 5.3 to relax the browser-ref comparison.

### 4.3 Lossy markers (bucket B)

When the extractor hits a value it can't fully convert (e.g.
`vw`, `var()`), it emits the IR property with `"_lossy": true`
on that property and a per-component `"_lossyReasons": [...]`
list. The comparison harness still runs the test, but the
manifest entry's `"lossy": true` flag tells the dashboard to
present results in a separate "expected gap" lane rather than as
a clean pass/fail.

### 4.4 Output layout

```
examples/wpt/
├── css-backgrounds/
│   ├── background-color-001.json
│   ├── background-color-001__ref.json
│   ├── background-color-002.json
│   ├── background-color-002__ref.json
│   └── ...
├── css-flexbox/
├── css-grid/
└── ...
```

Mirror of WPT's `/css/` directory layout — same spec sections,
same naming. Makes manual cross-reference trivial.

---

## 5. Reference image generation

### 5.1 Two ground truths

WPT reftests assume "test.html and ref.html render identically
in a real browser". For TITAN we have two complementary signals:

| Comparison                                  | Catches                                          |
|---------------------------------------------|--------------------------------------------------|
| **Inter-platform** (existing 3 pairs)       | "all three of our renderers agree" — engine consistency |
| **Browser-ref** (new — 3 platform-vs-browser pairs) | "our rendering matches what the spec says" — spec compliance |

**Recommendation: both.** Inter-platform gives us the same
diagnostic shape we already have for the 327-pair baseline;
browser-ref tells us when *all three* of our platforms agree on
something the spec disagrees with (an engine-wide bug we'd never
catch from inter-platform alone).

### 5.2 Browser-ref capture

Render the WPT `*-ref.html` directly in headless Chromium (the
`puppeteer` install we already use). Single browser, one
reference image per test:

```js
// testing/titan/capture-browser-ref.mjs
const browser = await puppeteer.launch({ headless: 'new' });
const page = await browser.newPage();
await page.setViewport({ width: 800, height: 600, deviceScaleFactor: 1 });
// 800×600 — WPT spec default; same canvas WPT itself uses.
for (const test of bucketA) {
  await page.goto(`file://${WPT}/${test.ref}`, { waitUntil: 'load' });
  await page.screenshot({ path: `testing/wpt/refs/${test.id}.png` });
}
```

Why headless Chromium and not Firefox/Safari/all-three:
- We already have puppeteer in the toolchain (no new dep)
- Chrome is what `wpt run firefox` validates against in CI
  upstream — using the same engine class minimises false
  divergence
- Three browsers × 14k tests × parallel runs = orchestration
  bloat we don't need in v1
- A future TITAN-2 could add `wpt run firefox`/`wpt run safari`
  as additional ref columns

The browser-ref images live in `testing/wpt/refs/<test-id>.png`
(separate directory from `testing/wpt/` source so a `git clean`
on the corpus doesn't nuke them; cached + reused across
TITAN runs that share the same `WPT_REF`).

### 5.3 Fuzzy tolerance handling

Per Section 2.3, WPT tests can carry a `<meta name="fuzzy">`
tolerance. The browser-ref pair uses these tolerances **as a
floor**, not a gate:

```js
// In compare-screenshots.mjs, per-pair, when pair.kind === 'browser-ref':
const fuzzyOK = test.fuzzy
  ? (pair.pixelMismatchedCount <= test.fuzzy.totalPixels.max
     && pair.maxChannelDelta   <= test.fuzzy.maxDifference.max)
  : pair.pixelMismatchedCount === 0;
pair.wptFuzzyMatch = fuzzyOK;   // surfaces in dashboard, doesn't gate
```

Fuzzy is informational — it tells the dashboard "this test
passes WPT's own tolerance for browser X" without changing the
classifier output. Inter-platform pairs ignore fuzzy entirely
(they have their own thresholds from B1–B7).

### 5.4 Caching

Browser-ref images are deterministic (same Chromium build, same
ref HTML → same PNG within AA noise). Cache them keyed on
`<wpt-ref-sha>__<test-path>`:

```
testing/wpt/refs/<wpt-sha>/css-backgrounds/background-color-001.png
```

Re-pinning WPT regenerates the cache; everything else hits cache
on subsequent runs. ~14k PNGs × ~5 KB avg = ~70 MB cached refs
per pinned WPT commit.

---

## 6. Capture pipeline scaling

### 6.1 The math

The existing pipeline does **109 components in ~3-5 min** end
to end (per `test-all.sh` timing, ~2s per component per
platform). At that rate:

- Bucket A (~10k tests, ~3 components avg = ~30k components):
  ~30k × 2s × 3 platforms = **~50 hours sequential per platform**
  — totally unacceptable.

We need 10–20× speedup. Three strategies, applied per platform:

### 6.2 Web (puppeteer)

Easiest to parallelize. Strategy: **N puppeteer browsers, each
captures a partition**.

```js
// testing/titan/capture-web-parallel.mjs
const N = os.cpus().length;          // typically 8–10 on M-series
const partitions = chunk(allTests, Math.ceil(allTests.length / N));
await Promise.all(partitions.map((p, i) => captureWorker(p, i)));
```

Per worker: one Chromium instance, one tab, iterate fixtures.
~8× speedup on M-series Mac. Memory cap: each Chromium ~300
MB → ~3 GB total — fine on 16 GB+.

Estimated wall time for bucket-A web pass: ~6 hours.

### 6.3 iOS (xcodebuild + simctl)

`xcrun simctl boot` is heavy (~5s) and the
`xcodebuild test` cycle is heavier (~30s for the install +
test-runner spin-up). We **cannot** run N simulators in
parallel — they fight for CoreSimulator and `xcodebuild`'s
DerivedData. Strategy: **one long-lived simulator + in-process
fixture iteration**.

This requires the iOS test runner (currently
`ScreenshotCaptureView.swift`) to support a **streaming-fixtures
mode**:

1. App boots once; loads an empty fixture list.
2. Test runner polls a directory in the simulator's `Documents/`
   for new IR JSON files (or listens on a UNIX socket).
3. Host script `testing/titan/feed-ios.mjs` writes one fixture
   at a time: `xcrun simctl simctl io <udid> push <ir.json>
   <docdir>/inbox/`.
4. App detects the file, renders, screenshots, writes PNG to
   `Documents/test_screenshots/`, deletes the inbox file.
5. Host pulls the PNG; loops to next fixture.

Per-fixture cost drops from ~30s → ~0.5s (just the SwiftUI
render + ImageRenderer). **TITAN-PREP-A's no-GUI work** is a
hard prerequisite here: the simulator must boot
`-CurrentDeviceUDID` + `simctl bootstatus -b -d` without ever
opening Simulator.app, and the app must launch via
`simctl launch --terminate-running-process` without focus
steal.

Estimated wall time for bucket-A iOS pass: ~6–8 hours.

### 6.4 Android (gradle + adb)

Same shape as iOS: one emulator, in-process fixture iteration.
The existing `ScreenshotManager.kt` already loops over the
fixture file's components on app launch — extend to support
**inbox-polling**:

1. Emulator boots once headless (`-no-window`, already TITAN-
   PREP-A-enabled).
2. App is installed once.
3. App polls `/sdcard/Android/data/com.styleconverter.test/files/inbox/`
   for new IR JSONs.
4. Host pushes via `adb push <ir.json>
   /sdcard/Android/data/.../files/inbox/`.
5. App renders + screenshots to `files/test_screenshots/`.
6. Host pulls via `adb pull`.

Per-fixture cost: ~0.3s (Compose render is fast).

Estimated wall time for bucket-A Android pass: ~5–7 hours.

### 6.5 Batching + crash recovery

Group bucket-A into **chunks of 200 tests**. After each chunk:

1. Write `testing/titan/runs/<run-id>/chunk-<NNN>.json` with
   per-test results
2. Update `testing/titan/runs/<run-id>/progress.json`:
   `{ totalChunks: 70, completedChunks: 47, lastChunkAt: "..." }`
3. On crash / kill / OOM, restart picks up at chunk 47 by
   reading `progress.json` — no re-doing work.

A run is "complete" when `completedChunks === totalChunks`. The
final aggregator reads all chunk files and writes the unified
manifest entry (Section 7).

### 6.6 Wall-time budget summary

| Pass                     | Sequential | Parallelized | Recovery point |
|--------------------------|-----------:|-------------:|----------------|
| Browser-ref (web)        | ~3 hr      | ~30 min (8x) | per chunk      |
| Inter-platform — web     | ~50 hr     | ~6 hr (8x)   | per chunk      |
| Inter-platform — iOS     | ~50 hr     | ~7 hr (in-proc) | per chunk   |
| Inter-platform — Android | ~50 hr     | ~6 hr (in-proc) | per chunk   |
| Comparison + classifier  | ~2 hr      | trivially parallel | per chunk |
| **Total wall (sequential platforms)** | — | **~20 hr** | — |
| **Total wall (platforms in parallel)** | — | **~7 hr** | — |

A full bucket-A TITAN run on a single M-series Mac fits in an
overnight window. Phase 1 smoke (100 tests) completes in
~5 min — fast iteration.

---

## 7. Output format

### 7.1 Manifest extension

Bump `manifestVersion: 3 → 4`. The new top-level `wpt` block is
sibling to the existing `rows[]` and `perPlatformProbes`:

```jsonc
{
  "manifestVersion": 4,
  "generatedAt": "...",
  "inputLabel": "...",
  "thresholds": { /* unchanged */ },
  "rows": [ /* existing 327 pairs unchanged */ ],
  "perPlatformProbes": { /* B8/B9/B10 unchanged */ },

  "wpt": {                                       // NEW (v4)
    "ref": "<wpt-git-sha>",
    "runId": "<iso-timestamp>",
    "totalTests": 14012,
    "buckets": { "A": 8200, "B": 1100, "C": 4712 },
    "duration": { "captureMs": 21600000, "compareMs": 7200000 },
    "results": {
      "css/css-backgrounds/background-color-001.html": {
        "bucket": "A",
        "lossy": false,
        "specSection": "css-backgrounds",
        "fuzzy": { "maxDifference": 15, "totalPixels": 300 },
        "components": ["background-color-001__0"],
        "pairs": {
          "iOS-Android": { /* full ImagePairMetrics — Section 2 of COMPARE_METRICS */ },
          "iOS-web":     { ... },
          "Android-web": { ... }
        },
        "browserRef": {
          "iOS-ref":     { /* same shape; pair.kind === 'browser-ref' */ "wptFuzzyMatch": true, ... },
          "Android-ref": { ... },
          "web-ref":     { ... }
        },
        "divergence": "sub-pixel-noise",
        "browserRefDivergence": { "iOS": "color-drift", "Android": "color-drift", "web": "identical" }
      },
      // ... ~8200 more bucket-A entries ...
      "css/css-fonts/font-variant-002.html": {
        "bucket": "B",
        "lossy": true,
        "lossyReasons": ["@font-face — font asset not bundled"],
        "specSection": "css-fonts",
        ...
      }
    },
    "skipped": {
      "css/css-grid/script-driven-001.html": "imports /resources/testharness.js",
      ...
    }
  }
}
```

### 7.2 Aggregator

`testing/titan-stats.mjs` walks `manifest.wpt.results` and emits
`testing/titan-stats.json`:

```jsonc
{
  "wptRef": "<sha>",
  "totals": { "A": 8200, "B": 1100, "C": 4712 },
  "bySpecSection": {
    "css-flexbox": {
      "tests": 480,
      "divergence": { "identical": 12, "sub-pixel-noise": 220, "mixed": 180,
                       "structural-divergence": 50, "edge-shift": 8, ... },
      "browserRefAgreement": {                              // % platforms matching browser
        "iOS":     { "match": 240, "fuzzyMatch": 80, "miss": 160 },
        "Android": { "match": 230, "fuzzyMatch": 85, "miss": 165 },
        "web":     { "match": 460, "fuzzyMatch": 15, "miss": 5 }
      }
    },
    "css-grid": { ... },
    ...
  },
  "topRegressions": [                                       // worst 50 tests by divergence
    { "test": "css/css-grid/grid-area-005.html",
      "divergence": "structural-divergence",
      "platforms": ["iOS", "Android"],
      "browserRefMiss": ["iOS", "Android"] },
    ...
  ]
}
```

The `topRegressions` list is what humans look at first — it
surfaces the engine bugs that need work.

### 7.3 HTML dashboard

**Separate dashboard** at `testing/report/titan.html` — do NOT
bloat the existing 327-pair `index.html` (it's already
700-line-rendered). Layout:

1. **Top-line stats** — total tests, bucket breakdown, run
   duration, WPT ref SHA + clickable link to upstream.
2. **Heatmap** — rows = spec sections, cols = divergence labels,
   cell = count + colour intensity. One glance shows "css-grid
   has tons of structural-divergence; css-color is mostly
   identical".
3. **Per-section drill-down** — click a heatmap cell → table of
   the matching tests with thumbnails (iOS / Android / web /
   browser-ref / 3-pair-diffs).
4. **Top regressions panel** — `topRegressions` from the
   aggregator, with one-click filter on each.
5. **Bucket-C explorer** — just the `skipped` map; useful when
   re-pinning to see what newly went off-rails.
6. **Platform-vs-browser scoreboard** — per platform: how many
   tests match the browser-ref exactly, fuzzy-match, miss. The
   single most actionable number for "which engine needs
   investment".

Same vanilla-JS / no-framework approach as the existing
`compare-screenshots-html.mjs` — the dashboard is a single
self-contained `.html` file, no build step.

---

## 8. Phased rollout

Each phase ships in its own PR. Phases 0–1 are blocking
prerequisites; Phases 2–4 can parallelize after Phase 1.

### Phase 0 — Acquisition + bucketer (no captures)

Deliverables:
- `testing/titan/fetch-wpt.sh` (Section 3)
- `testing/titan/WPT_REF` (one-line SHA file)
- `testing/titan/bucket-wpt.mjs` (Section 4.1)
- `testing/wpt-buckets.json` (committed; ~2 MB)
- `.gitignore` entries for `testing/wpt/` and
  `testing/wpt/refs/`
- Brief README at `testing/titan/README.md` summarising
  fetch + bucket workflow

Success criterion: `bash testing/titan/fetch-wpt.sh && node
testing/titan/bucket-wpt.mjs` runs end-to-end and produces a
`wpt-buckets.json` whose totals are within ±5% of Section 2.1's
estimates (numbers committed in the PR).

Effort: **0.5 day**, 1 agent.

### Phase 1 — End-to-end smoke (100 tests, all platforms)

Deliverables:
- `testing/titan/extract-fixture.mjs` (Section 4.2)
- `testing/titan/capture-browser-ref.mjs` (Section 5.2)
- `testing/titan/run-titan.sh` orchestrator (mirrors
  `test-all.sh` but for WPT fixtures, with chunking)
- iOS / Android / web inbox-polling capture mode (Section 6.3,
  6.4 — additive to existing capture entry points)
- `manifestVersion: 3 → 4` bump in `compare-screenshots.mjs`
  with the `wpt` block (Section 7.1)
- `testing/titan/SMOKE_TESTS` — hand-picked 100-test list
  spanning 5+ spec sections, 3+ buckets

Success criterion: `WPT_SMOKE=1 testing/titan/run-titan.sh`
produces a manifest with `manifest.wpt.totalTests === 100`,
`manifest.wpt.results` is fully populated for all 100 entries,
and at least one entry per spec section produces a non-`unknown`
classifier label. Wall-time target: ≤15 min on M-series Mac.

Effort: **3 days**, 1 agent (the work is fanning out across
4 capture entry points).

### Phase 2 — Bucket-A on web only

Deliverables:
- `testing/titan/capture-web-parallel.mjs` (Section 6.2 — 8-way
  partitioned worker pool)
- Browser-ref pass for all of bucket-A (~10k refs cached)
- `testing/titan-stats.mjs` aggregator (Section 7.2) — initial
  version, web-only

Success criterion: full bucket-A web pass completes in ≤8 hr,
manifest has `manifest.wpt.results.<every bucket-A test>` with
the `web-ref` browser-ref pair populated, and `titan-stats.json`
emits per-spec-section histograms.

Effort: **2 days**, 1 agent. Independent of Phase 3.

### Phase 3 — Add iOS + Android

Deliverables:
- iOS inbox-poll mode hardened (Section 6.3) — long-running
  simulator with crash recovery
- Android inbox-poll mode hardened (Section 6.4)
- Cross-platform run: `testing/titan/run-titan.sh --all-platforms`

Success criterion: full bucket-A pass on all three platforms
completes in ≤24 hr (split overnight + into early morning),
manifest's `pairs` block populated for every entry, no
crash-recovery loss > 1 chunk.

Effort: **3 days**, 1 agent. Can parallelize with Phase 2.

### Phase 4 — Browser-ref comparison columns

Deliverables:
- Extend `compare-screenshots.mjs` to compute
  `pair.kind === 'browser-ref'` against the cached browser-ref
  PNG (Section 5)
- Fuzzy-tolerance handling (Section 5.3)
- Manifest `browserRef` block + `browserRefDivergence`

Success criterion: every bucket-A entry has 6 pairs (3 inter-
platform + 3 browser-ref); `titan-stats.json` includes the
`browserRefAgreement` block.

Effort: **2 days**, 1 agent. Depends on Phase 2 + Phase 3.

### Phase 5 — Dashboard + bucket-B + housekeeping

Deliverables:
- `testing/report/titan.html` dashboard (Section 7.3)
- Bucket-B included in pipeline with `lossy: true` flag
- `baseline-stats.mjs` extended to emit per-WPT-section
  histograms (mirrors the existing 7-label distribution)
- `testing/titan/REFRESH.md` — runbook for quarterly WPT re-pin

Success criterion: dashboard renders the full bucket-A+B run,
heatmap is interactive, top-regressions list links to source
WPT path, and a quarterly re-pin takes ≤1 hour of human time.

Effort: **3 days**, 1 agent.

---

## 9. Estimated effort + agent dispatch plan

| Phase | Effort | Agents | Parallel with                        |
|------:|-------:|-------:|--------------------------------------|
| 0     | 0.5 d  | 1      | (none — must finish first)           |
| 1     | 3 d    | 1      | (none — depends on 0)                |
| 2     | 2 d    | 1      | Phase 3                              |
| 3     | 3 d    | 1      | Phase 2                              |
| 4     | 2 d    | 1      | (depends on 2 + 3)                   |
| 5     | 3 d    | 1      | (depends on 2/3/4)                   |
| **Total** | **~13.5 d sequential** | **2 in parallel after Phase 1** | — |

With 2 agents working post-Phase 1, end-to-end calendar is
**~9 days**. Single-agent serial: **~14 days**.

Dispatch:
- **TITAN-IMPL-0** (you, after this spec): execute Phase 0.
- **TITAN-IMPL-1**: execute Phase 1 (depends on 0).
- **TITAN-IMPL-2A**: execute Phase 2 (web).
- **TITAN-IMPL-2B**: execute Phase 3 (iOS + Android) in
  parallel with 2A.
- **TITAN-IMPL-3**: execute Phase 4 (depends on 2A + 2B).
- **TITAN-IMPL-4**: execute Phase 5 (depends on 3).

---

## 10. Open questions (with recommended answers)

### Q1. WPT pin choice — latest HEAD vs latest stable epoch vs frozen 6mo snapshot?

**Recommendation: latest `epochs/three_hourly` at Phase 0 PR
time, frozen-as-SHA in `testing/titan/WPT_REF`.** Re-pin
quarterly. Rationale: HEAD is daily-flake; a 6-mo-old snapshot
misses recent CSS spec work that's exactly what we need to
exercise. Quarterly re-pin balances reproducibility with
freshness.

### Q2. Generate browser-ref via headless Chrome vs `wpt run firefox`?

**Recommendation: headless Chromium via puppeteer (we already
have it).** Rationale: zero new toolchain (no Firefox/Safari
install, no Selenium); a single browser is sufficient for "spec
compliance signal" because all major engines pass WPT's own
fuzzy tolerances on bucket-A. A future TITAN-2 could add
multi-browser refs as additional columns if a platform's
browser-ref divergence turns out to be ambiguous.

### Q3. Bucket-C handling — silent skip or visible "wpt-unsupported" entry?

**Recommendation: visible — record every bucket-C test in
`manifest.wpt.skipped` with the reason.** Rationale: the
quarterly re-pin diff (Section 3.4) needs to surface tests that
moved A → C; silent skip would hide regressions in our
extractor's coverage. Cost is trivial (one string per test in
the manifest).

### Q4. TITAN run determinism — fully deterministic, or allowed to skip flakes?

**Recommendation: deterministic by default; explicit
`--allow-flakes` opt-in for development.** Rationale: same
WPT_REF + same Style-Converter commit + same OS image must
yield byte-identical `wpt-buckets.json` and bit-equivalent
manifest results (within AA noise). A non-deterministic TITAN
run is worse than no TITAN run because it makes regression
attribution impossible. Flakes are caught by re-running and
diffing — if a test toggles, file the flake.

### Q5. Storage — where do ~30k × 3 platforms × ~10 KB PNG = ~900 MB of captures live?

**Recommendation: gitignored under `testing/wpt/captures/<run-id>/`;
keep the latest 3 runs locally, archive older ones to S3 or
similar if/when CI integration arrives.** Rationale: 900 MB ×
N runs would balloon the repo; gitignored keeps it local. The
manifest.json itself (~5–10 MB) is the durable artifact —
captures are recreatable from `WPT_REF` + Style-Converter
commit. Section 8 Phase 0 PR adds the gitignore entries.

### Q6. WPT corpus refresh cadence — monthly, quarterly, semi-annually?

**Recommendation: quarterly (Section 3.4).** Rationale: monthly
is too noisy (3 PRs/year of "bucket diff churn"); semi-annual
risks falling behind on a new CSS module that justified TITAN
in the first place. Quarterly hits the "ship-with-spec" cadence
of the major browsers.

### Q7. Do bucket-A tests participate in the Phase A regression-check from `smoke.sh`?

**Recommendation: NO in v1.** Rationale: TITAN is an
exploration / coverage-mapping tool, not a regression gate. The
327-pair visual-test baseline already serves the
gate-on-regression purpose, and adding 30k more pairs to that
gate would (a) make `smoke.sh` take hours, (b) cause the
baseline to drift constantly (every WPT pin generates new
expected images), and (c) drown real regressions in WPT-baseline
churn. A future v2 could promote a small curated subset
("titan-smoke") to the gate, but only after we have ≥6 months
of stable per-pin distributions.

### Q8. Headless browser version pinning?

**Recommendation: pin Chromium via the `puppeteer` package
version in `testing/package.json` (already done — puppeteer
ships its own Chromium).** Rationale: TITAN's reproducibility
needs the browser to be locked the same way the WPT corpus is.
Bumping puppeteer is a deliberate PR (which it already is for
the existing pipeline).

### Q9. What about WPT tests that depend on remote resources (e.g. `<link href="https://...">`)?

**Recommendation: bucket-C — record in skipped with reason
"remote resource"**. Rationale: deterministic offline runs
forbid network fetches mid-test; less than 1% of CSS reftests
do this (per a sample of WPT spec-section dirs); easier to
exclude than to mock. The bucketer's heuristic table
(Section 4.1) gets one more rule: `https?://` in any `href` or
`src` attribute → bucket-C.

### Q10. How should TITAN runs interact with the `test-all.sh` single-run lock?

**Recommendation: TITAN's `run-titan.sh` acquires the same
`/tmp/style-converter-testall.lock` (lines 47–55 of
`test-all.sh`).** Rationale: TITAN uses the same iOS simulator,
the same Android emulator, the same vite port; running TITAN
and a `test-all.sh` invocation concurrently corrupts both. The
lock is already mkdir-atomic and macOS-portable; reuse it. The
existing `TESTALL_SKIP_LOCK=1` escape hatch lets sub-scripts
opt out the same way TITAN's own chunk drivers will.

### Q11. Should the bucketer also walk `*-ref.html` files independently?

**Recommendation: NO — refs are paired with tests via the
`rel="match"` link.** Rationale: ref files standalone aren't
useful (they're the "answer key", not the "test"). The
bucketer indexes test files only and resolves their refs by
following the link. Orphan refs (test deleted but ref kept) are
silently ignored; the WPT repo's own CI catches those.

---

## Files to create / modify

### New files (Phase 0)
- `testing/titan/fetch-wpt.sh` — clone + sparse-checkout driver
- `testing/titan/bucket-wpt.mjs` — A/B/C classifier
- `testing/titan/WPT_REF` — one-line pinned commit SHA
- `testing/titan/README.md` — fetch + bucket runbook
- `testing/wpt-buckets.json` — committed bucket index (~2 MB)

### New files (Phase 1)
- `testing/titan/extract-fixture.mjs` — WPT HTML → IR JSON
- `testing/titan/capture-browser-ref.mjs` — headless Chromium
  ref images
- `testing/titan/run-titan.sh` — chunked orchestrator
- `testing/titan/SMOKE_TESTS` — 100-test smoke list
- `examples/wpt/<spec-section>/<test-stem>{,__ref}.json` —
  generated, gitignored
- `testing/wpt/refs/<wpt-sha>/...` — cached browser-refs,
  gitignored

### New files (Phase 2)
- `testing/titan/capture-web-parallel.mjs` — N-worker pool
- `testing/titan-stats.mjs` — aggregator → titan-stats.json

### New files (Phase 4)
- (none — extends `compare-screenshots.mjs`)

### New files (Phase 5)
- `testing/report/titan.html` — dashboard (single self-
  contained file, no build)
- `testing/titan/REFRESH.md` — quarterly re-pin runbook

### Modified files
- `.gitignore` — add `testing/wpt/`, `testing/wpt/refs/`,
  `testing/wpt/captures/`, `examples/wpt/` (Phase 0)
- `testing/compare-screenshots.mjs` — `manifestVersion: 3 → 4`,
  recognize `wpt:` block, browser-ref pair handling (Phase 1 +
  Phase 4)
- `testing/iOS/StyleConverterTest/Screenshot/ScreenshotCaptureView.swift`
  + `ScreenshotManager.swift` — inbox-polling mode (Phase 1)
- `testing/Android/app/src/main/java/com/styleconverter/test/screenshot/ScreenshotCaptureScreen.kt`
  + `ScreenshotManager.kt` — inbox-polling mode (Phase 1)
- `testing/web/capture-screenshots.mjs` — inbox-polling mode
  (Phase 1)
- `testing/baseline-stats.mjs` — extend to walk `manifest.wpt`
  (Phase 5)

---

## First implementer dispatch prompt

Copy-paste-ready prompt for the parent to fire at TITAN-IMPL-0
(Phase 0 implementer):

````
You are TITAN-IMPL-0 on Style-Converter. Your single job: execute
Phase 0 of testing/TITAN_ARCHITECTURE.md — WPT acquisition +
pre-classification (bucketer). No captures, no comparisons; just
get the corpus on disk and a bucket-A/B/C index committed.

## Working tree
/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/jovial-shockley-b4adce

## Required reading
1. **testing/TITAN_ARCHITECTURE.md** Sections 1–4, 8 (Phase 0),
   10 (Q1, Q3, Q5, Q9, Q11)
2. **CLAUDE.md** "IR Value Normalization" — explains why `var()`,
   `vw`, `calc()` go to bucket-B
3. **testing/RESEARCH.md** A1 — the original WPT proposal for
   context

## Deliverables (one PR)
1. `testing/titan/fetch-wpt.sh` — partial-clone + sparse-checkout
   driver per Section 3.1–3.3. Reads `WPT_REF` env var; defaults
   to the contents of `testing/titan/WPT_REF`.
2. `testing/titan/WPT_REF` — pin to the most recent commit on
   `epochs/three_hourly` at the time you start (record how you
   chose it in the PR description).
3. `testing/titan/bucket-wpt.mjs` — Section 4.1 heuristics.
   String-grep based; reads HTML files from `testing/wpt/css/`,
   writes `testing/wpt-buckets.json`. Must handle the entire
   `/css/` tree without OOM (stream files; don't load all 24k
   at once).
4. `testing/wpt-buckets.json` — committed; spec shape in
   Section 4.1. Include `wptRef`, `generatedAt`, `buckets`
   (A/B/C lists), `reasons` (per-test bucket-C reason).
5. `.gitignore` adds: `testing/wpt/`, `examples/wpt/`,
   `testing/wpt/refs/`, `testing/wpt/captures/`.
6. `testing/titan/README.md` — short runbook: how to refresh the
   pin, how to re-bucket.

## Success criteria
- `bash testing/titan/fetch-wpt.sh` runs to green from a clean
  worktree (no pre-existing `testing/wpt/`); resulting checkout
  is ≤2 GB.
- `node testing/titan/bucket-wpt.mjs` runs in <60s on M-series
  Mac and produces a `wpt-buckets.json` with bucket totals
  within ±10% of Section 2.1's estimates.
- The PR description records:
  - the chosen WPT_REF SHA + how it was chosen
  - actual bucket totals (A / B / C counts, with `wc -l`-equivalent
    invocations against the checkout, citing them inline)
  - any heuristic from Section 4.1 that needed adjustment
    (and why)

## Hard rules
- Do NOT commit `testing/wpt/` (the corpus). The bucket index
  IS the artifact.
- Do NOT touch the existing 327-pair pipeline, `test-all.sh`,
  `compare-screenshots.mjs`, or the iOS/Android/web capture
  code. Phase 1 owns those edits.
- Bucket heuristics are per Section 4.1 — do NOT add new
  heuristics without flagging them in the PR description.
- Time budget: ≤4 hours.

## Output
Open a PR titled "TITAN Phase 0: WPT acquisition + bucketer"
with the deliverables above. Reference TITAN_ARCHITECTURE.md
section numbers in commit messages.
````

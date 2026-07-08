# TITAN — WPT Phase 0 runbook

This directory holds the orchestration code for **Project TITAN**: the
integration that funnels the Web Platform Tests (WPT) CSS reftest corpus
into Style-Converter's 3-platform comparison pipeline.

The design in two lines: fetch a pinned WPT snapshot, bucket every CSS
reftest by convertibility (A = full pipeline, B = lossy, C = skip), then
extract each A/B test into an IR fixture, render it on all three
platforms plus a browser reference, and compare with SSIM
(`run-titan.sh` / `section-runner.sh` drive the later phases).

## Layout

```
tools/titan/
├── README.md          (this file)
├── WPT_REF            one-line pin: SHA on epochs/three_hourly
├── fetch-wpt.sh       partial-clone + sparse-checkout driver
└── bucket-wpt.mjs     A/B/C classifier (string-grep)
```

The materialised corpus lives at `tools/wpt/` (gitignored — too big to
commit, ~1.5 GB after sparse-checkout).

The bucket index `tools/titan/wpt-buckets.json` is **not committed** —
it is fully derived from the corpus; regenerate it via
`node tools/titan/bucket-wpt.mjs` (<60s) after `fetch-wpt.sh` has
materialised the corpus at the `WPT_REF` pin.

## Usage

### First-time setup (or post-`git clean`)

```bash
bash tools/titan/fetch-wpt.sh
node tools/titan/bucket-wpt.mjs
```

`fetch-wpt.sh` materialises `tools/wpt/` at the SHA in
`tools/titan/WPT_REF` (override per-run with `WPT_REF=<sha>`). Expect
~1.5 GB on disk and ~3–6 minutes of network on a warm cache.

`bucket-wpt.mjs` walks `tools/wpt/css/` once and writes
`tools/titan/wpt-buckets.json`. <60s on M-series Mac. Re-running is
idempotent — the output is fully derived from the corpus.

### Quarterly re-pin

1. Resolve the current `epochs/three_hourly` tip:

   ```bash
   git ls-remote --heads https://github.com/web-platform-tests/wpt epochs/three_hourly
   ```

2. Update `tools/titan/WPT_REF` (replace the SHA, keep the comment
   header explaining how/when it was chosen).

3. Refresh the corpus + bucket index:

   ```bash
   bash tools/titan/fetch-wpt.sh
   node tools/titan/bucket-wpt.mjs
   ```

4. Diff the regenerated `tools/titan/wpt-buckets.json` against the
   previous run — any tests that moved A → C (or B → C) deserve a
   comment in the re-pin PR.

### Re-bucketing without re-cloning

Once `tools/wpt/` is materialised, the bucketer can be re-run on its
own as many times as you like:

```bash
node tools/titan/bucket-wpt.mjs
```

This is the inner-loop iteration when tweaking heuristics. The
heuristics live in the `RX` block at the top of `bucket-wpt.mjs`;
additions need to be flagged in the PR description.

## Buckets

| Bucket | Meaning                                           | Phase 1 action |
|:------:|---------------------------------------------------|----------------|
| **A**  | Cleanly convertible — extractor + 3-platform run  | full pipeline  |
| **B**  | Partial conversion (lossy)                        | pipeline + `lossy: true` flag |
| **C**  | Cannot model in IR (skip)                         | recorded only  |

The heuristic table lives as the `RX` block in `bucket-wpt.mjs`
(string-grep classification; remote-resource tests always bucket C).

## Hard rules (Phase 0)

- `tools/wpt/` is gitignored. **Do not commit the corpus.** The bucket
  index is likewise regenerated, never committed.
- `bucket-wpt.mjs` heuristics are exactly the `RX` table plus the
  remote-resource → C rule. Any additions must be flagged in the PR.
- This phase does NOT touch `test-all.sh`, `compare-screenshots.mjs`,
  or any platform capture code. Phase 1 owns those edits.

# TITAN — WPT Phase 0 runbook

This directory holds the orchestration code for **Project TITAN**: the
integration that funnels the Web Platform Tests (WPT) CSS reftest corpus
into Style-Converter's 3-platform comparison pipeline.

The full design lives in [`docs/reports/TITAN_ARCHITECTURE.md`](../TITAN_ARCHITECTURE.md).
This README only covers Phase 0 (acquisition + bucketer) — the work that
lands the corpus on disk and produces a committed bucket index. Phases
1–5 ship in follow-up PRs.

## Layout

```
tools/titan/
├── README.md          (this file)
├── WPT_REF            one-line pin: SHA on epochs/three_hourly. See §3.2
├── fetch-wpt.sh       partial-clone + sparse-checkout driver. See §3.1–3.3
└── bucket-wpt.mjs     A/B/C classifier (string-grep). See §4.1
```

The materialised corpus lives at `tools/wpt/` (gitignored — too big to
commit, ~1.5 GB after sparse-checkout).

The committed artifact is `tools/titan/wpt-buckets.json` — the durable
output of the bucketer (per `TITAN_ARCHITECTURE.md` §10 Q5).

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

### Quarterly re-pin (per `TITAN_ARCHITECTURE.md` §3.4)

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

4. Diff `tools/titan/wpt-buckets.json` against the previous commit — any
   tests that moved A → C (or B → C) deserve a comment in the re-pin
   PR (`TITAN_ARCHITECTURE.md` §3.4 step 3).

### Re-bucketing without re-cloning

Once `tools/wpt/` is materialised, the bucketer can be re-run on its
own as many times as you like:

```bash
node tools/titan/bucket-wpt.mjs
```

This is the inner-loop iteration when tweaking heuristics. The
heuristics live in the `RX` block at the top of `bucket-wpt.mjs`; per
the spec (§4.1), additions need to be flagged in the PR description.

## Buckets

| Bucket | Meaning                                           | Phase 1 action |
|:------:|---------------------------------------------------|----------------|
| **A**  | Cleanly convertible — extractor + 3-platform run  | full pipeline  |
| **B**  | Partial conversion (lossy)                        | pipeline + `lossy: true` flag |
| **C**  | Cannot model in IR (skip)                         | recorded only — visible per §10 Q3 |

See `TITAN_ARCHITECTURE.md` §4.1 for the heuristic table.

## Hard rules (Phase 0)

- `tools/wpt/` is gitignored. **Do not commit the corpus** — the
  bucket index IS the artifact (`TITAN_ARCHITECTURE.md` §10 Q5).
- `bucket-wpt.mjs` heuristics are exactly the §4.1 table plus §10 Q9
  (remote-resource → C). Any additions must be flagged in the PR.
- This phase does NOT touch `test-all.sh`, `compare-screenshots.mjs`,
  or any platform capture code. Phase 1 owns those edits.

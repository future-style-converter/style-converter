# SWARM-2B dispatch playbook

This doc is the **input** for `TITAN-SWARM-2B` — the agent that fans
30 parallel `section-runner.sh` invocations across the bucket-A spec
sections to drive Phase 2 to completion. It is pure documentation.
The infra it references (`section-runner.sh`, `aggregate-sections.mjs`,
`MANIFEST_OUT`, per-section locks) was shipped by `TITAN-IMPL-2A`.

## Top 30 spec sections (by bucket-A count)

Derived from `testing/wpt-buckets.json` `bySpecSection` block at SHA pinned
in `testing/titan/WPT_REF`. Wall-time estimate uses **3 s/test** per
end-to-end run (extract + browser-ref + gradle convert + vite + capture +
compare); first run of a fresh corpus will be slower because the browser-ref
cache is cold (puppeteer renders ~2 tests/s on a warm M-series Mac, so a
cold-cache run is roughly +0.5 s/test on top).

| # | section                | bucket-A | wall (cold) | wall (warm cache) |
|--:|------------------------|---------:|------------:|------------------:|
|  1 | css-break              | 962      | ~56 min     | ~48 min           |
|  2 | css-ui                 | 933      | ~55 min     | ~47 min           |
|  3 | css-grid               | 876      | ~52 min     | ~44 min           |
|  4 | css-flexbox            | 730      | ~43 min     | ~37 min           |
|  5 | css-backgrounds        | 531      | ~31 min     | ~27 min           |
|  6 | css-text               | 529      | ~31 min     | ~26 min           |
|  7 | css-sizing             | 413      | ~24 min     | ~21 min           |
|  8 | css-overflow           | 394      | ~23 min     | ~20 min           |
|  9 | css-transforms         | 376      | ~22 min     | ~19 min           |
| 10 | css-images             | 369      | ~22 min     | ~18 min           |
| 11 | CSS2                   | 292      | ~17 min     | ~15 min           |
| 12 | css-gaps               | 285      | ~17 min     | ~14 min           |
| 13 | css-contain            | 280      | ~16 min     | ~14 min           |
| 14 | css-multicol           | 279      | ~16 min     | ~14 min           |
| 15 | css-color              | 223      | ~13 min     | ~11 min           |
| 16 | css-text-decor         | 204      | ~12 min     | ~10 min           |
| 17 | css-counter-styles     | 201      | ~12 min     | ~10 min           |
| 18 | css-view-transitions   | 195      | ~11 min     | ~10 min           |
| 19 | css-writing-modes      | 191      | ~11 min     | ~10 min           |
| 20 | css-masking            | 177      | ~10 min     | ~9 min            |
| 21 | css-page               | 161      | ~9 min      | ~8 min            |
| 22 | css-lists              | 156      | ~9 min      | ~8 min            |
| 23 | selectors              | 155      | ~9 min      | ~8 min            |
| 24 | css-position           | 153      | ~9 min      | ~8 min            |
| 25 | css-pseudo             | 141      | ~8 min      | ~7 min            |
| 26 | filter-effects         | 140      | ~8 min      | ~7 min            |
| 27 | css-tables             | 128      | ~8 min      | ~6 min            |
| 28 | css-anchor-position    | 122      | ~7 min      | ~6 min            |
| 29 | css-values             | 115      | ~7 min      | ~6 min            |
| 30 | css-fonts              | 107      | ~6 min      | ~5 min            |
| **total** | —              | **9,818** | —          | —                 |

The top-30 covers **9,818 / 10,944 (89.7 %)** of bucket-A. The remaining
~1.1k tests are spread across 57 small sections (≤100 tests each); the
SWARM dispatcher should fold these into a long-tail "miscellaneous" bucket
or skip them in Phase 2.

## Concurrency cap

**Recommended ≤ 8 simultaneous agents** on a single M-series Mac.

Justification:
* **Memory ceiling.** Each section-runner spins up a full vite dev server
  (~250 MB RSS warm), one Puppeteer / Chromium process for capture (~500 MB
  RSS) and inherits a JVM for the gradle convert step (~600 MB peak; teardown
  after step 4). Steady-state per agent is ~1 GB. 8 agents × 1 GB = 8 GB,
  which fits in 16 GB of RAM with headroom for the OS + the user's editor.
  16 agents would push us to 16 GB and cause swap.
* **Port range.** Section-runner reserves ports `3100..3299` (200 slots)
  and falls back to scanning if the deterministic hash collides. 8 agents
  use ≤ 8 ports — way inside the range. The actual ceiling is set by
  Chromium's own port headroom (its devtools needs a free port too, so
  budget 2 ports / agent => 100 agents ceiling; we're nowhere near).
* **gradle daemon.** Each agent calls `./gradlew --no-daemon` to avoid
  the daemon-handshake race (test-all.sh already documents this — port
  exhaustion at >50k TIME_WAIT). With 8 parallel `--no-daemon` invocations,
  each spawns its own JVM; this is the steady-state RAM driver above.
  If the prebuilt classes path (`converter/build/classes/kotlin/main`) is up to
  date, section-runner falls through to direct `java -cp ...` and
  skips the daemon entirely — that drops the peak by ~400 MB / agent.
  **Pre-build with `./gradlew :converter:classes` once before dispatching SWARM-2B.**
* **Vite + esbuild.** Each vite uses ~4 worker threads for esbuild. 8
  agents × 4 threads = 32 esbuild workers, comfortable on an 8-core M2
  (it'll context-switch but won't stall). 16 would saturate.
* **Disk I/O.** browser-ref cache writes are section-keyed
  (`testing/wpt/refs/<sha>/<section>/<stem>.png`) so different sections
  write different paths — no fsync contention. The bottleneck is gradle's
  `out/` rewrites, but section-runner routes those through per-agent
  work dirs.

## Pre-flight (run once before dispatching SWARM-2B)

```bash
# 1. Make sure the WPT corpus is fetched at the pinned SHA.
ls testing/wpt/css/css-flexbox/abspos | head  # smoke check

# 2. Pre-build the JVM classes so section-runners skip the gradle daemon.
./gradlew :converter:classes

# 3. Pre-warm npm install in testing/web (section-runners symlink
#    node_modules from this dir).
( cd testing/web && npm install )

# 4. Verify the bucket index is fresh (regenerate if WPT_REF rotated).
node testing/titan/bucket-wpt.mjs
```

## Per-agent dispatch prompt template

Drop this into the agent spec for each of the 30 dispatched workers. Replace
`<SECTION>` with the section name and `<RUN_ID>` with the shared parent
runId (so `aggregate-sections.mjs` finds all 30 manifests under one root).

> You are TITAN-SWARM agent **<SECTION>** working in worktree
> `/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/<NAME>`.
>
> Run:
>
> ```bash
> testing/titan/section-runner.sh <SECTION> --run-id <RUN_ID>
> ```
>
> When the script exits 0, your job is done. The aggregator
> (`testing/titan/aggregate-sections.mjs <RUN_ID>`) runs after every agent
> completes — you do not run it yourself.
>
> Expected wall-time: see `testing/titan/swarm-dispatch.md` table row for
> your section. If you exceed 2× that, kill yourself and report the cause
> (likely vite startup hang or gradle OOM).
>
> Do **not** modify any file outside
> `testing/titan/runs/<RUN_ID>/sections/<SECTION>/` and the section-keyed
> subdirs of `testing/wpt/refs/`, `examples/wpt/<SECTION>/`. The other 29
> agents own their own subdirs.

## Aggregation

After all (≤ 30) agents finish (or time out), the dispatcher runs:

```bash
node testing/titan/aggregate-sections.mjs testing/titan/runs/<RUN_ID>
```

This emits `testing/titan/runs/<RUN_ID>/manifest.json` (unified v4),
plus a one-line summary + per-section table on stdout. The unified
manifest is the input for the Phase 2 dashboard
(`testing/titan/titan.html`, follow-up work).

## Open questions for SWARM-2B

* Do we run the long tail (57 small sections, ~1.1 k tests total) as a
  29-th + 30-th batch, or fold them into a "miscellaneous" pseudo-section?
  The current section-runner only handles real bySpecSection keys — a
  pseudo-section needs a one-line shim.
* Browser-ref cold cache: the first wave of 8 agents will pay puppeteer
  startup × 8 = ~8 s of pure browser warmup. Acceptable for an overnight
  run; matters if we ever push for sub-hour wall clocks.
* iOS / Android Phase 2: not in scope for SWARM-2B per spec hard rule B.
  Folded into a separate Phase 2.5 once `test-all.sh`'s simulator /
  emulator paths are taught the per-agent isolation tricks
  (`section-runner.sh` Step 5's pattern doesn't transfer 1:1 — they share
  the CoreSimulator / Android AVD singletons).

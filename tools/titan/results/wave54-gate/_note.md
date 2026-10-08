# Wave 54 — gate record

## Opening gate `wave54-open` (2026-10-08 13:12 UTC → ; `tools/titan/gate-driver.sh wave54-open --skip-fixture-net`, then `--skip-corpus`)

Tree: `campaign/applier-campaign` = `neworigin/dev` 7cce3b22 (the wave-53 squash; exactly corpus-v6.19's content). Host unchanged
since wave 53: macOS 27.0.1 (26A434), Xcode 27.0 (27A266a), iOS 26 simulator runtimes, Chrome for Testing 151.0.7922.47. Host after
stopping our processes: load1 2.82, free+inactive 3055 MB. Installed builds MATCH (`build-hashes.txt`, 13:16 UTC).

Obligation (BACKLOG "Next-wave obligations (wave 54 opens with these)" item 0): `score-gate.mjs wave53-final wave54-open --watch
tools/titan/results/wave53-plan/watchlist.txt` must print **0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now**
over 4096 cells and the fixture net exit 0 on all 9 gate fixtures. The first divergence is the first item of wave 54.

### Corpus (first launch, 13:12 → 14:31 UTC)

**30 / 30 sections OK on attempt 1, every column full** (css-cascade 43/43/43; css-view-transitions in 4 minutes this time — the
host was not swapping into the bake's CDP drives). `score-gate.mjs wave53-final wave54-open --watch …/wave53-plan/watchlist.txt
--movers 0.005` (`score-open.txt` / `.json`): **0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now over 4096
cells** — and **0 movers even at |Δ| ≥ 0.00005**: the pipeline reproduced `wave53-final` to the last scored digit on the same host.
Byte-identity control (`control-check.mjs wave53-final wave54-open`, wave 53's carrier sets — irrelevant here, the tree is the
same; `control-open.txt`): web 1435 compared · 1435 identical · 0 re-encoded · 0 changed (0 carriers) · iOS 1435 compared · 1435 identical · 0 re-encoded · 0 changed (0 carriers) · Android 1435 compared · 1435 identical · 0 re-encoded · 0 changed (0 carriers) · wire 1435 documents compared · 0 content-changed · 0 renumbered (ids only, explained by earlier carriers) · 0 outside the plan's wire carriers. The fifth consecutive
deterministic opening gate (wave52-open, wave53-open, …).

### Fixture net (second launch, `--skip-corpus`)

`BASELINE=1 ./test-all.sh --gate-set` (14:35 → 14:53 UTC, `gate-driver/fixture-net.log`): **exit 0 on all 9 gate fixtures** —
visual-test (390 baselines) and filter-sepia-amounts against their committed baselines, the seven gate-only fixtures (all-then-color
among them, its baselines withdrawn at the end of wave 53) through the cross-platform gate + spec oracle, 0 violations.
Installed builds: the corpus launch MATCH on both; on the net launch the iOS read raced test-all's own rebuild (built side missing
seconds after `provision rc=0`) and the re-read after the net MATCHES with the same digest as the wave-53 corpus build; the Android
re-read fired after teardown (vacuous) — `build-hashes.txt`, both annotated. **`wave54-open` is complete: 30 / 30 sections + fixture
net exit 0 ×9; the obligation "0 gained / 0 lost / 0 movers / 0 newly measured / 0 unmeasured-now against wave53-final" is MET,
with every capture and wire document byte-identical.** The reading of the net launch's hash is a known race: the wave skill's
"read right after provisioning" holds for the corpus launch only; on the net launch the record of value is the corpus launch's
(same tree) plus the post-net iOS re-read.

### Plan checks after the opening gate (PLAN.md §8 step 1; 2026-10-08)

- `watchlist-check.mjs` with the wave-54 watchlist over `wave54-open` → see `watchlist-check.wave54-open.txt` (expected `unmatched 0`).
- Per-test IR `wave53-final` → `wave54-open`: 1435 / 1435 byte-identical (the control above).
- `snapshot-cells.mjs wave54-open` → `wave54-plan/cells-wave54-open.json`: `cells` and `totals` equal to `cells-wave53-final.json`
  (only the `run` name differs) — every prediction's "from" value reads the same against `wave54-open`.
- `adjudicate.mjs` / `control-check.mjs` copied from `wave53-gate/` with only the expectations path changed. The four
  pre-registered `controlCalibrations`: **C2** `wave53-final → wave54-open` HOLDS, 0 changed / 0 wire / 0 renumbered
  (`control-calib-identity.json`); **C3** `wave53-open → wave53-final` under the wave-54 carriers FAILS — 25 capture + 1 wire
  leaks, wave 53's landed changes (`control-calib-wave53pair.json`); **C4** `--lane L7-label-chrome` (empty carrier set) HOLDS
  on the identity pair and FAILS on the wave-53 pair (28 + 1); **C1** the wave-53 original on its own pair reproduces its
  closing verdict (HOLDS; capture totals identical to the copy's — 5 / 10 / 13 changed, wire 1 + 10 — only the carrier
  attribution differs). The instrument is calibrated both ways before any lane lands.
- **No divergence from `wave53-final`, so PLAN.md §10 needs no restated prediction**; it records that reading.

## Landing and the single-writer sweep (2026-10-08)

- **Landed** (`wave54-plan/land-units.sh`, rehearsed first in a throwaway worktree): base 1cde1f48 → 18 commits — the lane
  records (fcf9fa9e), then 17 revert units in PLAN §4's order: L7 U1 · L1 P, M′ · L2 TB-android (+kt seam-1) · L6 RS (+tsx
  seam-1), W1 · L5 U1-android (+kt seam-1; GO-SMALL, U2-ios held) · L4 OOF-android, OOF-ios, CBB-android (+kt seam-1),
  GAP-android, GAP-ios · L3 U1, U2-android (+kt seam-1), U2-ios (+swift seam-2), U3 (+seam-3), U3b (+seam-3b). HEAD d64d4c6e.
  L1's patch replay reproduced the lane's three files byte-exact. Working tree clean after landing.
- **Pre-landing windows**: [W-L6] step 0 → BRANCH R (V1 26 / V3 46 / V5 46, V6 boxes 4/5 = 26); [W1] marker probe ALL PASS;
  [W2] converter hop ALL PASS (23 → 29); [W2b] css-counter-styles 32 identical · 15 renumbered · 1 content-changed, selectors
  48 identical, css-anchor-position only anchor-center-safe-rtl (`wave54-rtl-marker-bake/orchestrator-windows.out.txt`).
- **Sweep** (`sweep-landed.txt`, tree d64d4c6e, 21:06 → 21:11 UTC): tooling **2329** (2325 pass, 4 env-gated skips) ·
  conformance valid · web **1379** · web-harness **344** · converter **562** · compose **3491** · android-harness **169**
  (task executed) · swiftui **2212** · ios-harness **30/30** on the simulator. Doc tables restamped; `doc-staleness-check.sh`
  exit 0.
- **[W-L3]** the gate-flag wire differential (30 sections, pre 1cde1f48 vs post d64d4c6e, worktrees with the gitignored
  inputs symlinked): first attempt mis-invoked with a relative out dir (the script `cd`s into each tree) — re-run with an
  absolute one; result below. Noted for the lane's script: it prints `exit 0` with 30 one-sided sections, which should be a
  failure.

WL3_PLACEHOLDER


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


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

### [W-L3] result (`wl3-diff.out.txt`; the per-test IR trees of both sides parked, gitignored, at `tools/titan/runs/wave54-wl3-diff/`)

30 sections × 48 tests extracted with the gate's own flags (`POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1`), combined, converted and
split on BOTH trees: **1397 identical · 23 content-changed · 15 renumbered · 0 one-sided**. The 23 content-changed documents are
EXACTLY `expectations.unionWireCarriers` (set equality checked programmatically): L1's 4 (counter-suffix, bidi-lines-001/-002,
anchor-center-safe-rtl) and L3's 19 (hyphenate-character-001…005 — U1's value change inside the existing string variant —
plus the 18 U3 documents, four of them the same hyphenate-character files). The 15 renumbered are the css-counter-styles
`cssom/*` documents (+6 after counter-suffix: M′'s six root-owned runs). The three zero-padding selectors documents are
byte-identical. **R4b holds on the landed tree before any device run; no bisection needed.** (The lane's static differential
without the bake flags had predicted the same 19 for L3 — the flags did not widen the radius.)

## Stage 1 — `wave54-pre` (2026-10-09 07:11 → 07:25 UTC; CSS2, css-counter-styles, css-tables, css-text; tree 9d7f4250)

Host after stopping our processes: load1 4.92, free+inactive 4318 MB. Installed builds MATCH (`build-hashes.txt`). **4 / 4 sections
OK on attempt 1, every column full.** Read-out in `stage1/` (the pre-registered order: base first, Δ = pre − open):

- `score-gate --movers 0` → `probe-readout.mjs --stage1`: **rule 1 fired 0 · rule 2 fired 0 · rule 3 fired 0 · leak signals 0**
  over 548 compared cells (525 non-carrier cells read, 205 of them must-not-move lines, none moved); 17 rows of other lanes
  recorded for stage 2. At the record threshold: gained 11 · lost 0 · movers 9 in the four sections.
- Controls (union carriers, probed sections): web 185/192 identical + 7 carriers · iOS 186 + 6 · Android 183 + 9 · wire 8
  content-changed carriers + 15 renumbered · **0 leaks — HOLDS**.
- **S6 instrument check** (pre-registered in §10 item 6): the css-counter-styles per-test IR is byte-identical to the [W-L3]
  post tree 48/48 and counter-suffix carries no `marker-not-baked` stamp — the paint-chain reader did not decline anything,
  so the [M] rows below measure M′, not the instrument.
- **Geometry gate (L1, L2)**: 21 keys — PASS 18 · FAIL 0 · UNMEASURED 3 (the css-lists controls, not in these sections) ·
  gating 9 / 9 PASS. `[M] counter-suffix web / ios / android` PASS on `rtl-marker-bake.geometry.py` and on
  `lists-bakes.geometry.py` (with the x134 ±2 marker-left rule from the S1 fix pass); `[P] bidi-lines-001 / -002 android` PASS;
  `006 android` PASS on both table-body probes (square rows 56–75, x24–43, no stray ink).
- **Cells**: counter-suffix android P 0.9547 → **P 0.989** (predicted ≈0.989 with M′; the wave-53 attempt gave 0.9793 with the
  markers on the wrong rows), ios 0.9802 → **0.9873**, web 0.9818 → **1**; bidi-lines-001 android f 0.8934 → **P 0.9629**,
  bidi-lines-002 android 0.9534 → **0.9818**; s-11-1-1b-006 android P 0.9944 → **P 0.9983**.
- **Pictures, looked at** (`tools/titan/runs/wave54-pre/sections/…`): counter-suffix reads `foo .1 / bar .2 / foo .א / bar .ב` on
  Android, iOS and web — the root-owned marker runs land on their own rows on Compose this time (the Column no longer sizes
  them; the plan's §2 L1 mechanism held); iOS rows 3–4 still show the Hebrew period before the letter (the pre-registered
  "stays DEGENERATE rows 3–4"). The Android errata square is a single black square with no second cell.

**Decisions (expectations `stage1Decision`): P KEPT · M′ KEPT · TB-android KEPT. No `REVERTED` entry; `plan-build.py` unchanged.**
Stage 2 (`wave54-probe`, 26 sections) follows on the same tree.

## Stage 2 — `wave54-probe` (2026-10-09 07:27 → 08:4x UTC; 26 sections; tree 206d71d9)

Host after stopping our processes: load1 5.41, free+inactive 2937 MB. Installed builds MATCH. **26 / 26 sections OK on attempt 1.**
Read-out in `stage2/` (score, ab-diff, probe-readout, controls, geometry, prediction cells, review sheets, red-square census):
gained 31 · lost 0 · movers 58 · 0 downward movers · controls HOLD with 0 leaks (wire 23 + 15) · S6 instrument check holds.
Rules: rule 3 fired on two L3 iOS rows (restated — PLAN §10 item 7 ¶2); rule 4 fired on CBB-android's gating geometry row
(**reverted**, `8deddc19` — ¶1); one geometry control mis-registered on a base picture (restated — ¶3). Pictures looked at for
the three decisions: `stage2/sheets-gained/gained-03.png` rows 2 / 6, `gained-05.png` row 6, `stage2/sheets-movers/movers-02.png`
row 5. After regeneration the geometry gate reads the probe as gating 30 / 30 PASS · control 112 / 112 PASS, and
`probe-readout.mjs` fires no revert rule and reports 11 leak signals = CBB-android's withdrawn carriers on the probe tree
(`stage2/probe-readout.restated.txt`; the proof that the amended rules fail on the tree that had the unit). The remaining 13 review sheets are read at the closing gate.
Next: [W-L7] seeding on this tree, the single-writer sweep, the closing gate `wave54-final`.


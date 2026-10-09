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
  them; the plan's §2 L1 mechanism held); iOS rows 3–4 still show the Hebrew period before the letter (pre-registered as "stays DEGENERATE rows 3–6" in
  `expectations.stayDegenerateEvenIfPass`: rows 3–4 the Hebrew period, rows 5–6 the CJK marker offset; this look saw rows 3–4). The Android errata square is a single black square with no second cell.

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


## Closing gate — `wave54-final` (2026-10-09 08:59 UTC → ; tree ef20c977; `tools/titan/gate-driver.sh wave54-final --skip-fixture-net`, then `rm -f /tmp/titan-device-pool/provisioned-*` + `--skip-corpus`)

Pre-gate: the single-writer sweep on 2d068100 green on every suite (`sweep-landed.txt`; android-app re-run with `--rerun-tasks`
after the first line came back UP-TO-DATE in 1 s), doc tables restamped (compose 3492 → 3486 = the 6 tests the CBB-android revert
removed; tooling 2334 → 2340 = one tripwire test per seeded stem), `doc-staleness-check.sh` exit 0, committed as ef20c977 (the
tree the gate builds from; code-identical to the probe tree 206d71d9 minus CBB-android, plus the 18 seeded PNGs and doc lines).
Host before launch: load1 2.28, free+inactive 3065 MB; the driver stopped our two gradle daemons and read load1 2.06 / 5262 MB.
Provision rc=0 at 09:03:01 (emulator-5554 + the seed iPhone); installed-build hashes read at 09:03:43, right after `attempt 1
starting` (`build-hashes.txt`): **android MATCH** (`3e6173df…` — a NEW apk vs the probe's `fa7cd266…`, as it must be: CBB-android
left the Compose tree), **ios MATCH** (`38ea8fdc…` — byte-identical to the probe's .app digest, as it must be: no Swift source
changed between 206d71d9 and ef20c977).

**Corpus half: 30 / 30 sections OK on attempt 1** (css-cascade 43/43/43, every other section 48/48/48; 08:59 → 10:19 UTC).
**Read-out** (`final54-analysis.sh` = the pre-registered strings of `expectations.json` — `scoreOfRecord`, `scoreReadout`,
`geometryGate.closingCmd`, `adjudicate`; outputs `score-final.json` / `score-final.movers0.txt`, `score-final.movers0005.json` /
`score-final.txt`, `geometry-final.json` / `.out.txt`, `adjudicate-final.txt`, `control-final-union.*`, `final/`):
- **Score** wave54-open → wave54-final: web 1232/1372 → **1243/1372**, iOS 1125/1362 → **1131/1362**, Android 1115/1362 →
  **1128/1362** — **gained 30** (web 11 · iOS 6 · Android 13), **lost 0**, newly measured 0, unmeasured-now 0, **movers 51** at
  |Δ| ≥ 0.005 (none downward); the `--movers 0` record lists 4066 cells with any Δ (the natives' 1–2 px font drift class).
- **Geometry gate** rc 0: 185 keys — gating **30 / 30 PASS**, control **112 / 112 PASS**, report 27 / 36 (the 9 FAILs are the
  pre-registered "expected WRONG until the target is fixed" rows: `compose-wpt-content-box-cb` abspos-autopos-htb/vlr/vrl-rtl
  android, `ua-heading-face` text-decoration-inset-005/-006/-014 ios + android), withdrawn 7 (CBB-android's keys, FAIL: the unit
  is off the tree — as they must).
- **Adjudicate** rc 0 — **R1–R5 hold**: R1 lost 0 · R2 denominator unmoved · R3 no missing section / short column · R4 gating
  predictions 27, missed 0 · R5 must-not-move 669, moved 0 · R6 **retired 8** hyphenate-character cells from
  degenerateByConstruction (U1 + U2 + U3 on the tree and `hyphenate-character.geometry.py` "→ GEOMETRY OK" on each) — they are gains.
  The restated L3 iOS rows: `hyphenate-character-001 ios` P 0.9611 and `-003 ios` P 0.957 — equal to the probe to 4 decimals
  (the must-not-fall clause of §10 item 7 ¶2 holds; read by hand — `adjudicate.mjs` has no reader for it, a lesson).
- **probe-readout** over the full gate: rule 1 fired 0 · rule 2 fired 0 · rule 3 fired 0 · leak signals 0.
- **Control HOLDS** (`control-check.mjs`, union carriers minus CBB-android's 14 withdrawn): web 1435 compared · 1395 identical ·
  40 changed = 40 carriers; iOS 1416 identical · 19 carriers; Android 1404 identical · 31 carriers; wire 23 content-changed + 15
  renumbered, 0 outside the plan's carriers.
- **Probe → final differential** (`final/probe-vs-final.txt`, `ab-diff.mjs wave54-probe wave54-final --threshold 0.002`): 3527
  cells compared, **8 differ — all eight CBB-android carriers returning toward their base values** (nested-clip-3 android P 0.9668
  → f 0.9574 is the taken-back flip; nested-clip / -2 / -4, autopos-htb-rtl, the three semi-replaced-stretch rows), nothing else:
  the closing tree reproduced the probe byte-for-byte everywhere the revert did not touch.
- **Picture-correct tally** (`expectations.pictureCorrectTally`, regenerated at ship time so the revert's withdrawn gating keys
  leave the list: 31 full + 2 part listed, 6 withdrawn): on the closing run **28 full + 2 part print "→ GEOMETRY OK"**; the three
  abspos-autopos-rtl android rows stay listed with report-class keys that read WRONG until their target is fixed (not counted).
- **Watchlist** (`watchlist-check.wave54-final.txt`): unmatched 0.
- **Red-square census** (`final/red-square-census.txt`): PASSING red **156** (web 22 / ios 62 / android 72) over 86 tests, FAILING
  red 59 — from wave53-final's 164 / 62 (eight passing red-square cells fewer: the three css-position pairs, static-fixed-inside-abspos ×2).
- **Cell review** (`final/cell-review.json`; every row of the 5 gained + 9 mover sheets looked at, four rows at 2× crops, then an
  adversarial second reader on 29 cells — every non-FAITHFUL label and every pass under 0.975, `final/cell-review.second-reader.json`):
  gains **FAITHFUL 26 · DEGENERATE 4** (all four pre-registered in `stayDegenerateEvenIfPass`, not counted as earned:
  scope-pseudo-element web — bullets for the "M" markers and the B/Foo wrap; contain-content-011 android — "25" for "17";
  semi-replaced-stretch-other web — "abel"; hyphenate-character-002 ios — emergency breaks); movers **FAITHFUL 36** (eight
  DEGENERATE → faithful: the red squares under the green ones are gone on the three css-position pairs and static-fixed-inside-abspos ×2),
  **DEGENERATE 4** (pre-registered: counter-suffix android rows 5–6 / ios rows 3–6, block-ellipsis-002 ios, bidi-lines-002 android),
  **HONEST_FAIL 11** (still f, none moved down). Second readers: 28 agree, 1 disagreement — `hyphenate-character-004 ios` f 0.9477
  has a FAITHFUL picture (every break and "/-/" string as the ref; the iOS monospace U+002D glyph is narrower) — a prediction MISS
  (the plan said P ≈0.979), recorded as such. Two residues the readers found for the BACKLOG: backdrop-filter-clip-rect android's
  416 px of red-under-blue paint order (outside the test's condition), and clip-path-filter-order android's pass not exercising the
  clip/filter order (the gain was the text block).

**Fixture-net half** (10:21 → 10:44 UTC; `--skip-corpus`): installed-build read at 10:25:53 right after `provision rc=0` —
android MATCH on the same apk as the corpus launch, ios "built .app digest=MISSING" — the pre-registered race exactly (`test-all.sh`
had already rebuilt the .app; the installed digest is the corpus launch's `38ea8fdc…`); annotated in `build-hashes.txt`, the
record of value stays the corpus launch's block. Result: **exit 0 on all 9 gate fixtures** (`tools/titan/runs/wave54-final/gate-driver/fixture-net.log`): visual-test "no regressions vs
baseline (327 platform-comparisons ran)", filter-sepia-amounts exit 0, the six gate-only combination fixtures exit 0 with 0 oracle
violations (24 checks · 2 waived / 12 / 12 / 12 / 9 / 15), and **all-then-color against its committed baselines for the first time
at a closing gate: "no regressions vs baseline (18 platform-comparisons ran)", 12 oracle checks, 0 violations** — obligation 0(e)
discharged on device.

**A/B arms** (`ab-arms54.sh`: each arm = HEAD minus one unit on a throwaway branch, devices re-provisioned, one section as
`wave54-ab-<arm>`, installed-build read after `attempt 1 starting`, read ARM FIRST with `ab-diff.mjs <arm> wave54-final
--threshold 0.002 --platforms <arm.platforms>` so Δ = final − arm is the dropped unit's share): ARMS_RESULT

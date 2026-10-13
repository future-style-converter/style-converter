# Wave 55 — gate record

## Opening gate `wave55-open` (2026-10-11 10:37 UTC → ; tree 0d0d3a3d = the wave-54 squash; `tools/titan/gate-driver.sh wave55-open --skip-fixture-net`, then `rm -f /tmp/titan-device-pool/provisioned-*` + `--skip-corpus`)

Preflight (wave skill Phase 0): branch `campaign/applier-campaign` resynced to `neworigin/dev` = 0d0d3a3d (PR #153's squash,
merged 2026-10-10 14:52 UTC), tree clean, stash empty; provisioning intact (both `local.properties`, `tools/wpt/refs`, the
xcodeproj, `node_modules` a directory whose three workspace links point into this tree); disk 43 GB free; no emulator attached,
only the seed iPhone booted; tooling 2340 (2336 pass, 4 skipped); `doc-staleness-check.sh` exit 0; pilots default-ON; the 9 gate
fixtures present, 408 committed baselines, the ledger's 10 rows all with a real owner and a parseable `expires`;
`corpus-v6-20.json` names `wave54-final` in `artifact` and `reproduce`. Host at launch: load1 2.7, free+inactive 2820 MB,
**swap 9.4 / 10 GB used** (the wave-53 lesson: css-view-transitions may need `--resume` under swap); the driver's own read after
stopping our processes: load1 3.14, free+inactive 3487 MB. Provision rc=0 at 10:40:59 (emulator-5554 + the seed iPhone).

**Installed-build hashes** read at 10:41:27, right after `attempt 1 starting` (`build-hashes.txt`): **android MATCH**
(`62384c7e…`), **ios MATCH** (`38ea8fdc…` — the same digest as every wave-54 launch: no Swift source changed).

**The apk sha1 question settled (a correction to wave-54 lesson L19).** The apk on disk was NOT rebuilt by this provisioning —
`provision.103722.log` ran `:app:assembleDebug` but the file's mtime stays 2026-10-09 12:43:48 (the first wave-54 arm's build):
Gradle found the task up to date. The build embeds exactly one asset, `assets/tmpOutput.json` — the fixture IR that
`test-all.sh` copies into `apps/android-harness/app/src/main/assets/` (`test-all.sh:460`) — and that file was last written at
12:42:15 during the wave-54 fixture net (all-then-color, the gate set's last fixture, 6 components). So the wave-54 corpus
launch's `3e6173df…` and the arms' / today's `62384c7e…` differ by that ASSET (the net rewrote it between the two builds), not by
an irreproducible build: with unchanged sources and the same asset Gradle reuses the apk byte-for-byte, and a sha1 change does
signal an input change. For the WPT corpus the asset is irrelevant (titan feeds the per-test IR at run time), so across launches
the apk sha1 is comparable only when the last-net fixture is the same; built == installed MATCH remains the record's check.

**Corpus half: 30 / 30 sections OK on attempt 1** (10:41 → 11:52 UTC; css-cascade 43/43/43, every other section 48/48/48;
css-view-transitions on the first attempt despite the swap). **Read-out** (`open55-readout.sh` → `score-open.*`,
`control-open.*`, `watchlist-check.wave55-open.txt`, `sections-open.status.txt`): `score-gate.mjs wave54-final wave55-open` =
**0 gained / 0 lost / 0 movers (at ≥ 0.005 AND at any Δ) / 0 newly measured / 0 unmeasured-now over 4096 cells** — the totals are
corpus-v6.20's exactly (web 1243/1372 · iOS 1131/1362 · Android 1128/1362); the byte-identity control (`wave54-gate/control-check.mjs
wave54-final wave55-open`, no carriers): **web 1435 / 1435 identical · iOS 1435 / 1435 · Android 1435 / 1435 · wire 1435 / 1435**
(0 content-changed, 0 renumbered); watchlist unmatched 0. So the shipped tree — the wave-54 gate tree plus the post-gate regex
rewrite in `InertOutOfFlowWordJoin.ts` — reproduces the gate of record byte-for-byte on this host: the rewrite moved no pixel,
as its language-equivalence proof said it could not. The wave-55 opening obligation is DISCHARGED on the corpus; the fixture net
follows (its installed-build read is annotated as a net launch).

**Fixture-net half** (12:00 → 12:23 UTC; `--skip-corpus`; installed-build read at 12:04:38 annotated as a net-launch read — android
MATCH on the corpus launch's apk, ios "built .app digest=MISSING" = the race): **exit 0 on all 9 gate fixtures**
(`tools/titan/runs/wave55-open/gate-driver/fixture-net.log`): visual-test "no regressions vs baseline (327 platform-comparisons
ran)", filter-sepia-amounts 24 oracle checks · 2 waived · no regressions (24 comparisons), the five gate-only combinations 0 oracle
violations (12 / 12 / 12 / 9 / 15), composition-test gate-only, and all-then-color against its committed baselines "no regressions vs
baseline (18 platform-comparisons ran)", 12 oracle checks, 0 violations. **The wave-55 opening obligation is DISCHARGED** on the
corpus (0/0/0/0/0, 1435/1435 ×3 + wire) and on the net (exit 0 ×9). The net's only side effect on the tree is the regenerated
`tools/visual/baseline-stats.json` (same classification as committed; the timestamp-only churn was discarded).

## [W-L7a] — L7's Chromium + converter window (2026-10-12, before the landing, no gate on the host; records in `wl7a/`)

Run as the lane's note prescribes (`wave55-instruments/_note.md` "ORCHESTRATOR WINDOW REQUESTS"; `wl7a/window-run.txt`):
the two patches' sha256 equal the note's `patch:` lines (`wl7a/step1-patch-sha256.txt`); three throwaways at 611c9f93 with
U5 on `post` and U2 on `diff`; **step 3** `wl7a-marker-paint-probe.mjs` → `WL7A MARKER PAINT: every value none / normal`
and the wave-54 marker probe from the post tree → `PASS baked — 2 roots, 10 runs` / `MARKER PROBE: ALL PASS`; **step 4**
`wire-differential.sh` (U2) pre vs post over css-anchor-position, css-backgrounds, css-counter-styles, css-text,
css-writing-modes, selectors → 12 status rows `0 0 0 0 extracted=48 fail=0`, **identical 288 · content-changed 0 ·
renumbered 0 · one-sided 0 · renumbered-anomalous 0, verdict exit 0**; **step 5** the instrument fails when it must:
`--classify-only` against an expect file naming counter-suffix as CONTENT-CHANGED → exit 5, `MISSING CONTENT-CHANGED …
(expected, not observed)`. **Decision (PLAN §8 step 5): U5 lands at seq 9; no `--hold L7:U5`.** Throwaways removed.

## Landing (2026-10-12; `land-units.sh`, PLAN §4 / §8 step 6)

Prerequisites on the tree at b4f64963: the seven lanes COMPLETE (lane notes' last STATUS line; the pre-landing audit
`prelanding/` — 108 cell citations, 41 patch hashes, the manifest series applies with every revert chain, seams anchored on
611c9f93), the regeneration of the plan from the audit's items (55dd4cd9: expectations b773904f, 26 intended paths), the
landing tool's self-test green on the current bytes (144 / 0, b4f64963), [W-L7a] held (U5 lands at seq 9).
`land-units.sh --check --root <throwaway>` first: a clean checkout holds none of the UNTRACKED lane deliveries nor the
gitignored force-add files, so the first attempt reported 50 "MISSING" errors and touched nothing — the throwaway check is run
after rsyncing the seven lane dirs and the six wave-54 `out/` files into it (lesson for the recipe), then it printed
**0 errors, 0 warnings — landable**; `--check` on the shared tree: 0 / 0, `git status` byte-identical before and after.
**The landing: records commit fb94a502 (the seven lane dirs + the six force-added files) + 27 unit commits in manifest order
(L7 U1 optional, no patch, its files tracked), b4f64963..5bbc2a41, working tree clean.** Order as §4: L7 U2 → U3 → U4 →
U6-compose → U6-swift → U6-web → U6-tooling → U5; L6 U1 → U2 → U3; L4 U1-android → U1-ios → U2-android → U2-ios; L2 U1 → U2;
L3 U1 → U2; L5 U1-ios → U2o-ios → U2i-ios → U2o-android → U2i-android; L1 NB-ios; L5 U3-ios → D. Contingencies NB-ios-b and
U1-ios-LRM not landed (stage 1 decides). Next: the single-writer sweep on 5bbc2a41, S1, the worktree cleanup (§8 step 9½),
then stage 1 `wave55-pre` after `beforeEveryDeviceRun`.

## Stage 1 — `wave55-pre` (2026-10-12; PLAN §7 stage 1, §10 items 10-15; records in `stage1/wave55-pre/`)

**The run.** `beforeEveryDeviceRun` exit 0; `tools/titan/gate-driver.sh wave55-pre --sections css-counter-styles,css-flexbox,css-grid,
css-masking,css-text,css-text-decor,css-writing-modes,filter-effects,selectors --skip-fixture-net` on tree 0fd26479 (the landed tree
5bbc2a41 + records), 9 / 9 sections `OK attempt=1 web=48/48 android=48/48 ios=48/48`; the installed-build block (`build-hashes.txt`
`== wave55-pre corpus launch`): android f9c6bee5 MATCH, ios 0e4a30c2 MATCH, asset 05118ad1. Read-out (`stage-readout55.sh stage1
wave55-pre`): `score-stage1.json` (+ 0.005 movers), `control-stage1.json` (HOLDS), `geometry-stage1.json` (18 gating FAIL lines + 1
control FAIL, 27 gating PASS), every lane's own probe output, `prediction-cells.txt` (77 rows), the review sheets `sheets-{gained,lost,
movers}/`. Committed 53f11bba.

**The decisions, in the order the rules fired (every line looked at; the zooms were made from the ref / `wave55-open` / `wave55-pre`
captures at scale 2-6 and are described, not committed):**

- **L1 NB-ios → REVERT (rule 1).** `hyphenate-character-002 ios P 0.9534 → f 0.9165`, a lost carrier (rule 2 too; predicted P ≈0.969).
  The 3 gating rows `→ LINEBOX OK`, 001 / 003 / 004 up (004 f → P 0.975), the two byte-identical controls identical, no `anchor:`
  reason → not the R1 signature, no NB-ios-b. The lost sheet: 002's lines sit tighter than the ref's on NB-ios (the no-visible-hyphen
  host). Revert 22c51ee9. PLAN §10 item 13.
- **L4 U2-ios, U1-ios, U2-android, U1-android → REVERT (rule 3 + rule 4).** 005 / 006 ios f 0.93 / 0.9284 and android f 0.9147 ×2
  (floor 0.95; predicted ≈0.966); `[M] 005 / 006` on both natives `GEOMETRY WRONG` — the face IS present now (U2), and the whole
  heading sits 5 px high (`the` cap top y138 vs the ref's y143 on both; Android's super- / subscript offsets +8 / −8 besides, and no
  wrap). 014 flipped f → P on both (0.9936 / 0.9867) with `face ink:` WRONG — withdrawn with the units. The pre-registered probe-time
  arm `drop-L4-U2` ran first, at 53f11bba (`ab-arms55.sh --run --against wave55-pre`; arm tree 45473b32, MATCH / MATCH, css-text-decor
  48 / 48 / 48; `final/ab-drop-L4-U2.txt`): U1 alone = the expectation's arm M to the third decimal (014 0.9337 / 0.9328; 005 0.9167 /
  0.9121; 006 0.9164 / 0.9117). Reverts 131cd6e1, adc05081, db7b2417, 2b0e0b2b. PLAN §10 item 14.
- **L3 U1 → KEEP; U2 → KEEP after the recorded look.** dir-style-02a android rows 6 / 7 / 9 OK, f 0.9425 → P 0.9854; dynamic-offset
  android OK; `[S] bidi-tab-001 android rtl-rows` read `ink: band y106 2 blue components vs wave55-open 3`. Measured: the ref's band
  y106 = square x124-135 + '0' x114-122 (apart); `wave55-open` = the square + the '0' stranded at x210-219 OUTSIDE the box (ring +
  inner dot: 3); `wave55-pre` = the '0' at x115-123 touching its square (2). The rows read `■0` / `0■` as the ref's; the cell P 0.9805 →
  P 0.9929. The read's reference was the defect itself → the band is also read against the decided device picture (`INK_RUN`,
  additive); 8 device-picture fakes WRONG. PLAN §10 item 10.
- **L5 U1-ios, U2o / U2i ios + android, U3-ios, D → KEEP after the recorded looks.** `[C]` / `[I]` OK on both natives; counter-suffix
  ios 0.9873 → 0.992, android 0.989 → 0.9924; 005 ios 0.993 → 0.9971, android 0.9759 → 0.9741. `[T] ios`: the pre-registered twin-only
  `ink:` (26 px vs Android's tabular glyphs); rows 9-12 at scale 4 are the ref's → `TWIN_RUN_DEFAULT = 'wave55-pre'`. `[H] ios`: the
  round-2 letter-profile read (0.80 vs the ref's alef, 0.88 vs its bet) — a cross-font read the precedence did not name; rows 3-4 at
  scale 6 read `א.` / `ב.` with the period after the letter (the H-low defect gone; `wave55-open` reads `.א` / `.ב`); against the
  device's own letters 1.00 / 0.83 → the twin letters as a second reference + a same-capture duplicate read; four device-picture fakes
  WRONG. `[K] ios` (control): 4 px at x136 on rows 11-12 = U3-ios's period AA fringe, 1 px outside the Catalyst-measured run boxes →
  the zone starts at x137, the column read by the residual window. PLAN §10 items 11-12.
- **L2 U1 / U2 → KEEP.** Both clip-3 scripts `→ GEOMETRY OK`, clip-3 f 0.9574 → P 0.9999, clip / -2 / -4 at 1.000, web / iOS identical;
  the residual-only 20 px are the rounded corners' AA ring (the ref's picture) → recorded as windows. PLAN §10 item 12.
- **L6 U1 / U2 / U3 → KEEP.** `[X]` safe-001 / -002 `→ GEOMETRY OK` (0.9619 → 0.9943, 0.9635 → 0.9932; the black L-border now at the box
  edge as the ref's), residual 32 / 30 px at the corners where it meets the dotted outline → windows read by identity to the decided
  picture (the three-colour blends no class rule names). `[PO]` OK; may-move within the band. PLAN §10 item 12.
- **L7:** no device key.

**After the decisions.** `probe-decisions/reverted.json` 5 rows → `plan-build.py` (expectations b773904f → 2d2df890 → e65366b6 after the
text-only renumbering); `watchlist.txt` byte-identical; `stage1-regen.expectations-diff.txt`. The probe edits (`stage1-probe-edits.py`,
replayable byte-identically onto the pre-edit files), the residual windows (`fix-r6.zones.stage1.out.txt`: `--also` of the five device
pictures recorded as `WINDOWS_ALSO`, 46 fixed pictures 0 / 0 / 0, `--check` exit 0) and their proof (`stage1-mutations.out.txt`: 19
fakes, 0 violations). The regenerated plan read against `wave55-pre` itself: `regen/probe-readout.txt` names exactly the withdrawn
cells as rule-5 leaks (18 signals) and `regen/control.txt` the 28 withdrawn carriers — as it must on the tree that still carries the
units; `regen/geometry.txt` gating 27 / 27, control 135 / 135, residual 30 / 30, exit 0. The sweep on 2b0e0b2b (`sweep-stage1.txt`):
tooling 2351 (2347 pass, 4 skipped), conformance valid, web 1390, web-harness 344, converter 562, compose 3550, android-app 169, swiftui
2241, ios-harness 30; doc tables restamped, doc-staleness exit 0. Decisions committed 00201165 (+ the renumbering commit). Next: stage 2
`wave55-probe` (26 sections) after `beforeEveryDeviceRun`; the withdrawn cells must read identical to `wave55-open` there.

## Stage 2 — `wave55-probe` (2026-10-12; PLAN §7 stage 2, §10 item 16; records in `stage2/wave55-probe/`)

Tree aaf51b1b; `beforeEveryDeviceRun` exit 0 (load 3.4); `probeRun.cmd` over the 26 sections — 26 / 26 `OK attempt=1`, every column
48 / 48 (css-cascade 43 / 43 / 43); the installed-build block `== wave55-probe corpus launch` (android ecafaf0d MATCH, ios ff158a4e
MATCH, asset 05118ad1) read after `attempt 1 starting`. Read-out (`stage-readout55.sh stage2 wave55-probe`): geometry gating
28 / 28, control 159 / 159, residual 31 / 31, withdrawn 15 (`geometry-stage2.json`); `CONTROL HOLDS` — android 82 changed
captures = 49 carriers + 32 may-move + 1 ring-fenced, web / iOS identical outside L5's iOS carriers, wire identical
(`control-stage2.json`); the 28 withdrawn L1 / L4 carriers identical to `wave55-open`; rules 1-3 fired 0, every prediction met
(`score-stage2.json`, `probe-readout.txt`, `prediction-cells.txt`); `abdiff.txt` 22 / 3538 cells differ, all carriers or may-move
(flips: dir-style-02a android f 0.9425 → P 0.9854, clip-3 android f 0.9574 → P 0.9999). The three leak signals are L6 may-move
stems beyond the band, each looked at against `wave55-open` and the ref: descendant-static-position-001 / -002 android (the
grid's black side band now under its grey abspos children: 400 / 800 px black → grey) and break-inside-avoid-child android (the
bottom band under its child box: 300 px black → light blue on row y317) — `mayMoveRule`'s first picture → L6 U2 KEPT. **Every
remaining unit is kept; no decision row; no regeneration.** The readers' records after the regeneration: `readers-calibration.txt`
(every calibration as required on e65366b6), `probes-selfcheck.txt` (HOLDS, 28 / 28 gating keys able to fail),
`final55-analysis.sh` in sync. Next: the closing gate `wave55-final` (§8 step 12).

## Closing gate — `wave55-final` (2026-10-12 16:08 UTC → 2026-10-12 18:39 UTC; PLAN §8 steps 12-13, §10 item 17; records in `final/`)

**Launch 1, the corpus** (`tools/titan/gate-driver.sh wave55-final --skip-fixture-net`; `beforeEveryDeviceRun` exit 0, load 4.4;
tree 5a4ab5d4 at launch, HEAD 26cd6610 — a records-only commit — when the hash was read after `attempt 1 starting`): 30 / 30
sections `OK attempt=1 web=48/48 android=48/48 ios=48/48` (css-cascade 43 / 43 / 43). **The hash of record**
(`build-hashes.txt` `== wave55-final corpus launch`): android app-debug.apk ecafaf0d built == installed MATCH, ios .app ff158a4e
MATCH, asset 05118ad1 — the same digests as `wave55-probe`'s block: no runtime source changed between the probe and the gate
tree, so both witnesses are the probe's (§8 step 12.2).

**Launch 2, the fixture net** (`rm -f /tmp/titan-device-pool/provisioned-*` + `--skip-corpus`): refused once at load1 6.25 (the
corpus run's own teardown; never `--force`; `final/net-launch.refused.driver.txt`), then on a quiet host (load 3.88): eight
fixtures exit 0, composition-test.json's Android column EMPTY (0 of 32 captured after "Installed on 1 device", "count stuck at 0
for 20s — app may have crashed"; iOS 32 / web 32) → exit 7, `fixture-net rc=7` (`final/fixture-net.attempt1.txt`,
`driver-summary.attempt1.txt`). Attempt 2 (load 3.36), with a detached logcat tap on the pool emulator from `provision rc=0` to
the driver's exit: every fixture exit 0 — visual-test `no regressions vs baseline (327 platform-comparisons ran)`, filter-sepia
24, all-then-color 18, composition-test 32 / 32 / 32 and the five other gate-only fixtures exit 0 (`fixture-net rc=0`;
`final/fixture-net.attempt2.txt`, `driver-summary.txt`); the logcat holds no harness crash. **R8 holds on attempt 2; attempt 1's
empty column is a transient, recorded.** The net-launch hash blocks are ANNOTATED, never of record (attempt 2: android caa0fd42
MATCH — the net's own apk, a different asset; ios built MISSING at read time — test-all rebuilds the iOS harness per fixture, the
race by design); the post-net re-reads (devices torn down: MISSING / MISMATCH) likewise. `tools/visual/baseline-stats.json`
unchanged. Records committed before any arm window (935287c4, a80d8f6f, the attempt-2 commit).

**Read-out** (`bash tools/titan/results/wave55-gate/final55-analysis.sh`, generated; first run): R6 exit 0 (`geometry-final.json`:
gating 28 / 28, controls and residual all PASS, withdrawn 15); union control HOLDS (`control-final-union.txt`: web 1435 / 1435
identical; ios 1431 identical + 4 changed = the four L5 carriers; android 1353 identical + 82 changed = 49 carriers + 32 may-move +
1 ring-fenced; wire 1435 documents 0 / 0 / 0); probe → final differential rc 0 (`probe-to-final.txt`: nothing reverted after the
probe); scoreOfRecord gained 2 lost 0 (`score-final.json`), 14 movers ≥ 0.005 (`score-final.txt`); **adjudicate FAIL R5 —
must-not-move cells 4087, moved 3**: descendant-static-position-001 / -002 and break-inside-avoid-child android, the three L6
may-move stems whose looks the stage-2 section records (the border band now under its child, the ref's picture). Adjudicate reads
the complement and has no may-move class, so the rule's "stops the ship until its picture shows one of three pre-registered
pictures" had no mechanical reading there. Encoded as PLAN §10 item 17 says: `probe-decisions/looks.json` → three non-gating,
undirected L6 prediction rows out of the complement (regeneration 77a06f67; `stage2-regen.expectations-diff.txt`), `final55-analysis.sh`
re-rendered, `probe-readout.mjs` printing a recorded look as `LOOKED` (proof `final/probe-readout.looks-proof.txt`). **Re-read on
the same score / geometry records (`final/final55-analysis.out.txt`): R6 exit 0 · adjudicate R1–R5 hold · union control HOLDS ·
probe-readout rules 1-3 fired 0, 0 leak signals — FINAL READ-OUT HOLDS.** Review sheets 1 / 0 / 3 (`final/sheets-*`), red-square
census and every prediction row (`final/red-square-census.txt`, `final/prediction-cells.txt`).

**Cell review (§8 step 14; `final/cell-review.json`, `final/cell-review.second-reader.json`)** and **the closing arm `drop-L6-U2` (step 15)**: PLAN §10 item 18 — 57 cells read on the PNGs (FAITHFUL 21 · DEGENERATE 25 · HONEST_FAIL 11; the two gains and the claimed movers FAITHFUL on both reads; 51 adversarial second reads, one label change: counter-suffix ios → DEGENERATE on row 1's proportional '1', the pre-registered `[L]` report row); the arm 7 / 7 sections full columns; arm tree 8de866d8, android MATCH / ios MATCH; read ARM first (Δ = final − arm, `final/ab-drop-L6-U2.txt`): 5 of 309 android cells differ — safe-001 +0.0324, safe-002 +0.0297, descendant-static-position-002 +0.0123, -001 +0.0099 (the pictures the review reads above: the yellow abspos box over the container border, the band under the children) and the ring-fenced basic-blur +0.0036 (D2's band move, report only); every other U2 carrier within 0.002 — their claim is the paint order the `[X]` / `[PO]` keys and the crossings count read, score-flat as predicted; no P → f revealed; break-inside-avoid-child lies outside the arm's seven sections and is attributed by `mayMoveRule` (item 17).

# Wave 55 — generator stage: done note

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`. Work began at HEAD a2a37a8e (the briefs commit). During
the stage the orchestrator committed the opening-gate records as 640276a0; this stage made no commit. Nothing outside
`tools/titan/results/wave55-plan/` and `tools/titan/results/wave55-gate/` was edited. `git status` shows only new untracked
files from this stage, and no tracked file is modified (§3 records one incident, repaired).

**Records this note cites instead of copying their numbers:**
- `plan-build.out.txt`: the generator's own report;
- `probes-selfcheck.txt`;
- `readers-selfcheck.txt`;
- `../wave55-gate/readers-calibration.txt`;
- `watchlist-check.wave54-final.out.txt`.

## 0. Delivered

| path (under `tools/titan/results/`) | what |
|---|---|
| `wave55-plan/plan-build.py` | the generator, adapted from wave 54's: same structure, same checks, same dry-run options (`--out`, `--reverted`, `--restate`), plus `--contingency` (L1 NB-ios-b) and `--go-small` (L4) |
| `wave55-plan/expectations.json`, `wave55-plan/watchlist.txt` | GENERATED (indent 1); sha256 `cd3936d0…` |
| `wave55-plan/plan-build.out.txt` | the generator's report on the installed run |
| `wave55-plan/snapshot-cells.mjs`, `wave55-plan/cells-wave54-final.json` | a COPY of the wave-54 snapshot script; see the note below |
| `wave55-plan/watchlist-check.wave54-final.out.txt` | `RUN=wave54-final WATCH=…/watchlist.txt node …/wave52-plan/watchlist-check.mjs` → **unmatched 0** |
| `wave55-plan/geometry-gate.py` | adapted from wave 54: `--sections`, script token = first `*.py`, refuses a cmd off the pinned interpreter (exit 2), wave-55 defaults |
| `wave55-plan/probe-readout.mjs` | adapted from wave 54: `--sections`; `--stage1` lets every lane decide; the `down-preregistered` direction; may-move / ring-fence / complement classes; `floorNA` printed |
| `wave55-plan/probes-selfcheck.py` → `probes-selfcheck.txt` | every registered probe on wave54-final, plus every brief's fakes, replays and arms; one line per gating key |
| `wave55-plan/readers-selfcheck.sh` → `readers-selfcheck.txt` | the instruments can fail; the dry runs reproduce the installed outputs (in a throwaway worktree) |
| `wave55-gate/control-check.mjs`, `wave55-gate/adjudicate.mjs` | the wave-55 copies of the readers (beyond the listed deliverables; why in §6) |
| `wave55-gate/readers-calibration.sh` → `readers-calibration.txt` | their calibrations |
| `wave55-plan/generator.done.md` | this note |

**Why snapshot-cells.mjs is a copy.** The wave-54 `snapshot-cells.mjs` writes `cells-<run>.json` into ITS OWN directory
(`HERE`), not into the cwd. Running it "from wave55-plan/" would have written into `wave54-plan/`. The copy differs only in
its header and its default run.

**The probe scripts.** All seven family probes were already in `wave55-plan/` as the briefs drafted them:
`hyphen-ios-linebox`, `cbb-android`, `positioned-parent-slot`, `ua-heading-margin`, `counter-suffix-rows`,
`android-paint-order`, and L7's `flex-zero-gap-rules` copy. None was edited. The generator registers 13 probe commands:
- the seven family probes;
- the wave-54 `compose-wpt-content-box-cb`, `hyphenate-character`, `web-out-of-flow-hyphen-box`, `rtl-marker-bake` and
  `ua-heading-face`;
- the wave-53 `lists-bakes`.

## 1. The opening gate and the "from" values

`wave55-open` finished while this stage was running. Its committed record (`../wave55-gate/_note.md`, 640276a0) shows:
- score-gate wave54-final → wave55-open: 0 / 0 / 0 at any Δ over 4096 cells;
- byte-identity control: 1435/1435 on all three platforms and on the wire;
- fixture net: exit 0 ×9.

So every "from" that the generator asserts against `cells-wave54-final.json` also holds against wave55-open. No
restatement is needed, and `openingGate.recorded` says so. As the task asked, I never read the wave55-open run directory;
the committed record is the source.

## 2. Censuses re-derived with each brief's own script, and one under-reported radius

Each script was run read-only, niced, on wave54-final. Its output went to the scratchpad and was diffed against the
brief's recorded output.

| lane | script | result |
|---|---|---|
| L1 | `hyphen-ios-linebox.census.mjs --json` | out and json identical |
| L2 | `cbb-android.census.mjs` | identical (A = the wave-54 CBB-android 14) |
| L3 | `positioned-parent-slot.census.mjs --json` | identical |
| L4 | `ua-heading-margin.census.mjs --json` | identical |
| L5 | `counter-suffix-rows.census.py` | identical |
| L6 | `android-paint-order.census.mjs --json` and `crossings-scan.py` | identical (252 may-move, 25 carriers) |
| L7 | `instruments.census.mjs` | identical except the HEAD sha and the tracked/untracked state of its own probe copy |

The L7 script rewrites its `.out.txt` in place. The original was backed up and restored byte-exact (sha256 `03053bfd…`
before and after).

**Under-reported radius (fixed in the generator, decision D2).**
- `android-paint-order` U2 moves the band of census (2b)'s opacity-0.6 bordered box (`backdrop-filter-basic-blur`) inside
  the opacity group. That is §9 R3 of its own brief.
- The 252-document may-move scan only includes documents with "a bordered component with a non-`<br>` child or own
  text", and that box has `kids 0`.
- So basic-blur android would have read as a control-check LEAK, and rule 5 would have reverted U2 for a ring-fenced
  test.
- The generator therefore adds (2b) to L6 `mayMove` by rule. Because the test is ring-fenced it stays report-only: it is
  in no must-not-move list. Both readers apply the ring-fence first.

No other radius was found short. The other carrier sets equal what the briefs state, re-derived.

## 3. Probes: run on wave54-final, and every gating key proven able to fail (`probes-selfcheck.txt`)

**On wave54-final:**
- Every registered probe runs under the pinned interpreter, exits 0, and every ref row prints OK.
- `geometry-gate.py wave54-final --base wave54-final --self-test` HOLDS over all lanes: every gating key FAILs, every
  control PASSes, every report key FAILs. Per-lane counts are in §0 of the file.

**Every gating key can fail.**
- All 43 gating keys FAIL on today's device picture.
- Each was also run against its brief's fakes, replays or arms. The per-key table is the file's last section.
- 41 of the 43 also PASS on a predicted ("fixed") picture. The two exceptions:
  - the re-used wave-54 `compose-wpt-content-box-cb … clip-3 android` rule has no fixed picture in a run. The
    supplementary `cbb-android.geometry.py` key accepts the U1+U2 replay and rejects F3, F4 and F6;
  - `[X] flex-abspos-staticpos-align-self-safe-002 android`: its replay keeps 50 crossing px, a replay artifact the brief
    documents as the row's lower bound.

**C4 executed, not derived.** The restated wave-54 `rtl-marker-bake [M]` and `lists-bakes counter-suffix` keys were run
on L5's `ok-all` replay pixels, through a fake run under the scratchpad.
- All six verdicts stay OK.
- The `rows 0-207 identical` clause reads DIFFER on ios and android, by design. So it is dropped there and kept on web.

**Incident, repaired (records written only after the restore).**
- A first self-check run executed `android-paint-order.geometry.py --fakes` on the shared tree. That mode rewrites its
  12 fake PNGs beside the script.
- PIL 12.2 encoded them byte-differently from the committed ones, with identical decoded pixels.
- They were restored byte-exact from the throwaway worktree's HEAD copies. `git status` is clean for them.
- `probes-selfcheck.py` now runs that mode only inside the worktree, and compares the fresh fakes by pixels: 12/12
  identical.

## 4. The generator: what is new beyond wave 54 (all asserted in `plan-build.py`)

**Complement rule (C1).**
- Every scored cell lands in exactly one of four places: a carrier; a lane's named must-not-move list (each brief's
  radius controls, restated outside the union); `mustNotMoveComplement`; or the ring-fence.
- An assertion checks that these four partition the 4096 cells. The counts are in `plan-build.out.txt`.
- A cell inside another lane's carrier set is never a third lane's must-not-move. The exclusions are printed (C2, C3,
  C5, C6).

**L6 `mayMove` (C1, C2, D2).**
- The raw 252-stem scan plus (2b), minus every carrier.
- The 12 L2 / L3 carriers in the raw set are listed as `sharedStems` and attributed by the L6 drop-U2 arm.
- `unionMayMoveCaptures` is published for the readers.

**`keyCells` (C15).** Every expect key names the cell it reads. `geometry-gate.py --sections` and `probe-readout.mjs
--sections` use it.

**One interpreter (C14).**
- Every cmd starts with `/opt/homebrew/bin/python3.12 -B`. Its PIL lives in its own site-packages.
  `/usr/bin/python3` reaches PIL only through the user site, which is the likely source of cbb-android R7.
- `-B` keeps `__pycache__` out of `wave53-plan/`. None exists now.

**Floors.**
- Every gating floor is 0.95.
- An f→f row carries `floorNA` instead of a floor (C11). F2 → 0.95. F1 and F3 → `floorNA`. L3's "U1 only" row is
  `fallbackIfReverted` on dir-style-02a.
- New direction `down-preregistered` for css3-counter-styles-005 android (D5).
- Every row now carries a `directionWhy`.

**Stage 1 and stage 2.**
- One `stage1Probe` `wave55-pre` over the union of the briefs' §8 sections, with css-writing-modes from the audit's
  extension.
- An assertion checks that every native unit has a carrier in a stage-1 section (`unitCoverage`).
- `probeRun` `wave55-probe` = 26 sections. Not probed: css-color, css-pseudo, css-sizing, css-ui (reasons in
  `notProbed`).
- One key is first read at the closing gate, and is named there: `[C] composited-filters-under-opacity android`.

**Other wave-55 entries.**
- `pictureCorrectTally`: hand-listed P→P rows, 22 in full. A multi-key entry counts only if ALL its keys print OK. The
  withdrawn logic is kept.
- `degenerateByConstruction` and `degenerateRetirementCheck`: L4's inset-005 / -006. iOS gains retire on `[F]` OK; the
  Android ones stay DEGENERATE.
- `stayDegenerateEvenIfPass`: the briefs' list, plus D3 (the autopos *-rtl android rows).
- `ringFenced` is now an object: test, cells, captures, rule.
- The wire-renumbering class is kept, with no expected shadow (no wire carrier this wave).

**Every briefs-audit conflict is resolved.** C1–C15, plus D1–D7 where the plan goes against or beyond a brief, are in
`expectations.json` `planDecisions`. That is the source PLAN §9 should cite.

**Recorded default the orchestrator may flip: D1, L4 GO.** The brief recommends four units, with 005 / 006 android
labelled DEGENERATE. `plan-build.py --go-small` regenerates with U2-android held, and that path is exercised.

## 5. Readers adapted: `geometry-gate.py`, `probe-readout.mjs`

Both keep wave 54's exit codes, ORDER and `--movers 0` guards, key classes and `--self-test` contract. What `--sections`
does:
- On wave54-pre (4 sections), 33 gating keys go from UNMEASURED to NOT PROBED.
- A copy with the filter disabled brings back all 33 UNMEASURED (B2, B3).

`probe-readout --stage1 --sections` likewise drops the 7 MISSING sections (C5).

## 6. Why the two gate readers were written too (`wave55-gate/`), and their calibration

The expectations introduce three things the wave-54 readers cannot see:
- the may-move capture class;
- the ring-fence class;
- the R5 complement.

A plain copy with only the path changed would have two failures:
- control-check would call every legitimate L6 band move and the ring-fenced basic-blur a LEAK, which forces a
  bisect / revert of L6 U2;
- adjudicate would not read the complement.

The copies are the wave-54 files with minimal, header-documented changes (`readerChanges` in the JSON). They are
calibrated in `readers-calibration.txt`:
- **K1–K6:** the identity pair holds; the wave-54 pair fails; `--lane L7` both ways; a synthetic run reads may-move,
  RING-FENCED and LEAK correctly; a may-move-less mutant fails it.
- **J1–J5:** identity misses the 9 gating flips; all-met holds; a +0.003 complement move fails R5; a complement-less
  mutant misses it; ORDER and `--movers 0.005` are refused.

If the orchestrator prefers to re-copy them itself, `readerChanges` lists the exact deltas.

## 7. Every new instrument proven able to fail; dry runs in a throwaway worktree

Done in `git worktree add --detach <scratchpad>/wt55 HEAD`, the one git operation used. Its `tools/titan/runs`,
`tools/wpt` and `node_modules` were symlinked to this tree's. It was removed with `git worktree remove --force` after
the symlinks were unlinked; the targets were verified intact.

**`readers-selfcheck.txt`:**
- **A.** The worktree reproduces `cells-wave54-final.json`, `expectations.json` and `watchlist.txt` byte-for-byte.
  Mutations M1–M6 each stop the build (from-mismatch, off-interpreter, carrier in must-not-move, key without a section,
  replay floor, ring in the complement). The `--reverted` (the L2 pair), `--contingency` and `--go-small` hooks
  regenerate consistent JSON. On the reverted JSON the gate prints the 8 L2 keys WITHDRAWN and exits 0.
- **B1–B6.** geometry-gate: red on the pre-fix run; the section filter is load-bearing; off-interpreter and STALE inputs
  are refused; a broken control fails the self-test.
- **C1–C9.** probe-readout: ORDER and thresholded records are refused; red on identity and on the wave-54 pair. A
  synthetic record shows the may-move band, the ring-fence, the complement and the D5 bar. Two mutants (bar ignored,
  may-move removed) turn it red.
- **D1.** `probes-selfcheck.py` reports CANNOT FAIL when a never-failing control is promoted to gating.

## 8. Left for later stages (not this stage's deliverables)

- **PLAN.md** (§0–§10). It should cite `expectations.json` / `plan-build.out.txt` for every number (wave-54 lesson 11),
  §9 should be `planDecisions`, and §3 / §4 come from `seam-registry.md`.
- **The wave-55 `land-units.sh`.** It needs the lanes' unit sections, which do not exist yet.
- **Landing order.** The seam constraints the generator records:
  - L1 NB-ios before the wave-54 U3b on revert;
  - the L2 pair;
  - L3 U2 → U1;
  - L4 U2-p → U1-p;
  - L5 U2i → U2o;
  - L6 U2 → U1;
  - the C10 module lines landing L4 → L3 → L5.
- **Orchestrator windows.** L1's drop-NB-ios and L6's drop-U2 arms, and L4's probe-time drop-U2 arm (all in `abArms`);
  L7 [W-L7a]; L6 [W1].

STATUS: COMPLETE

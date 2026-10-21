# Wave 55 — plan-skeptic round 3: VERIFY (re-execution of every claim the fixed plan makes)

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0` (unchanged). Inputs at their fix-r2 bytes:
`expectations.json` 56dbcfc0…, `watchlist.txt` 4b1a9cd5…, `PLAN.md` 4326476a…, `plan-build.py` b10a6722…,
`landing-manifest.json` f6716e2f…, `land-units.sh` 817c9677…, `ab-arms55.sh` ef8e232d…, `adjudicate.mjs` 262d22c3…,
`control-check.mjs` cba40eb5…, `geometry-gate.py` 39b9a0d7…, `probe-readout.mjs` d627607d…, `final55-analysis.sh` 40692d56….

Everything ran read-only on the shared tree, or in ONE throwaway (`git worktree add --detach <scratch>/wave55-r3v-wt HEAD`,
`tools/titan/runs` and `tools/wpt` symlinked to the shared tree's, the two plan dirs mirrored in, removed at the end), or
writing only under the scratchpad. Calibration scripts that `tee` their record into the shared tree were run as SCRATCH
COPIES (only `SH=` and the tee target changed: `diff` printed in the session), so no committed record was rewritten.
`wave55-open` was never read. No gradle / xcodebuild / vite / puppeteer / emulator / simulator / adb / test-all / smoke.

## 1. Claims re-executed

| # | claim (PLAN / fix-r1 / fix-r2) | executed | result |
|---|---|---|---|
| 1 | the generator regenerates its outputs byte-for-byte | `python3.12 -B plan-build.py --out <scratch>` (0.24 s) | `expectations.json`, `watchlist.txt` and stdout vs `plan-build.out.txt`: **cmp identical** (sha256 56dbcfc0… / 4b1a9cd5… / 687a9306…); the 16 `generatedWith.inputs` hashes therefore match the files on disk; the JSON is the indent-1 rendering (`json.dumps(indent=1, ensure_ascii=False)` round-trips it) |
| 1b | `cells-wave54-final.json` is the scorer's own snapshot | a scratch copy of `snapshot-cells.mjs` (import repointed to the absolute `score-gate.mjs`, output to scratch) on wave54-final | 4096 cells, web 1243/1372 · ios 1131/1362 · android 1128/1362; **cmp identical** to the installed file |
| 2 | watchlist unmatched 0 | `RUN=wave54-final WATCH=…/watchlist.txt node wave52-plan/watchlist-check.mjs` | 746 lines, **unmatched 0**, output identical to `watchlist-check.wave54-final.out.txt` (wave55-open's not re-read: forbidden) |
| 3 | every probe runs on wave54-final and prints its registered keys with the registered expect strings | `geometry-gate.py wave54-final --base wave54-final --self-test --json <scratch>` | exit 0, **self-test HOLDS**: 230 keys, UNMEASURED 0, self-check failures 0; gating 43/43 FAIL, control 160/160 PASS (each line ends in its exact expect string, `contains` honoured), report 27/27 FAIL. Every expect literal (`LINEBOX OK`, `STACK OK`, `GEOMETRY OK`, the L2 RTL `GEOMETRY WRONG (green …)`) is printed by its probe. Over the 131 §T gate JSONs: 42 of the 43 gating keys PASS on a fixed picture; the exception is the known L6 `[X] …safe-002 android` (its replay keeps 50 crossing px) |
| 4 | every gating key (and every `pictureCorrectTally` key) fails on at least one recorded fake | `probes-selfcheck.py --scratch <s> --wt <throwaway> --out <scratch>` (2 min 34 s) | exit 0; the output is **byte-identical** to the committed `probes-selfcheck.txt` (§0 HOLDS; §T 130 cases, 0 violations, **48 / 48**). Independent tabulation of the 131 gate JSONs: every one of the 48 keys has ≥ 1 fake read FAIL |
| 5a | geometry-gate / probe-readout refusals (B1–B11, C1–C12, D1, E1–E5) | `readers-selfcheck.sh` as a scratch copy, throwaway + scratch | "every check behaved as required"; identical to the committed record except one scratch path in E4 |
| 5b | control-check / adjudicate / final55 refusals (K1–K15, J1–J7, F0–F4) | `readers-calibration.sh` as a scratch copy | 30 / 30 "as required"; **identical** to the committed record (header line aside) |
| 5c | further refusals not in those records | 14 probes (`plan-skeptic-round-3.verify.d/refusals.out.txt`) | as required except two nits (N1, N2 below): an extra positional is ignored by `control-check.mjs`; a missing input file crashes with exit 1 |
| 5d | toolkit proofs on the CURRENT reader bytes (the committed `toolkit-proofs.txt` cites older ones) | sections A, B, D of `toolkit-proofs.sh` as a scratch copy (section C needs `git init`, not run) | 42 checks "as required", exit 0, on adjudicate 262d22c3 / control-check cba40eb5 / make-snapshot55 df37730d / expectations 56dbcfc0: A0c's documented 14-line code delta holds; B's accounting CLOSES (79 capture + 23 wire LEAKs); D1b byte-identical to corpus-v6-20.json |
| 6 | `land-units.sh --check` is read-only | in the throwaway (`TMPDIR` in scratch), then on the shared tree | exit 1 (53 errors: no lane delivered yet — expected), **state unchanged** both times: HEAD, index sha256 AND mtime, `status --porcelain --untracked-files=all` hash; no file newer than a marker; the private export dir removed. Static read: every mutating call goes through `tree_mut` (exit 70 under `--check`); the only other git calls are reads or `apply --check / --numstat / --summary` |
| 6b | land-units self-test E / F / G on the current bytes | `LU_SELFTEST_ONLY="E F G" land-units.selftest.sh <scratch>` (they never commit) | **40 passed, 0 failed**, Z1 shared tree identical, Z2 no throwaway left; PASS/FAIL lines identical to `land-units.selftest.r2-EFG.out.txt` |
| 7 | `ab-arms55.sh` writes its records only after the last verified restore | static read of the current bytes (ef8e232d) + diff vs the self-tested bytes b8db42d8 (fix-r1's `orig/`) + the two git-free proofs | the `--run` section is byte-identical to the self-tested one except ONE added precondition (`code_dirty`): `write_records` / `verify_records` run only after the loop of `run_arm` → `restore_tree` (exit 6 on an unverified restore, nothing written in the tree). `ab-arms55.against-proof.sh` (throwaway, dry runs) and `ab-arms55.revert-check-proof.sh` (scratch): **identical** to their committed records (P1–P5, AB2–AB4; R1–R6) |
| 8 | PLAN §9 resolves C1–C15 | briefs-audit §6 / §8 vs §9 hand table vs `planDecisions` vs the JSON fields | every id has a hand row and a generated row, and each resolution is encoded: C1 `stage1Probe.sections` = the audit's 9-section union; C2 `sharedStems` 12, may-move ∩ union carriers = ∅; C3 L7 registers only flex-zero-gap + web-out-of-flow, no counter-suffix / L3 android cell in L7's must-not-move; C4 rtl-marker-bake / lists-bakes `contains` on web only, ua-heading-face holds only inset-011 web, flex 033 ×3 control, the 8 inset natives (005/006/011/014) neither named nor complement; C5 disclosure-styles ios = L5 U1-ios carrier, android = may-move, not in L1 / L5 lists; C6 `dropped`; C7 manifest row 28 D after 22 / 24; C8 / C9 strings; C10 the three anchors read :159 / :167 / :182 at HEAD; C11 floors 44 × 0.95, every gating cell P→0.95 / f→`floorNA`; C12 `dryRuns` + `revertUnits.U1-ios.dryRun`; C13 no `:702/:758/:786` in L2; C14 asserted; C15 `keyCells` + `--sections` |
| 9 | every number in PLAN cites a file | `plan-prose.py --check` on a copy; `plan-prose.selfcheck.sh` (scratch); a scan of hand prose outside the gen blocks | `--check` **HOLDS** (36 gen blocks · 39 decision ids · 46 citations · 28 units); the selfcheck reproduces its record (every mutation red). The scan finds brief-derived numbers cited by brief section, and a few uncited hand counts that are true today (N4) |
| 10 | standing rules | expectations scans | every floor is 0.95 or none; every native unit has a carrier section in the stage-1 union (carriers outside it are the audit's "left for the closing gate"); every probe cmd on the pinned interpreter; ring fence = basic-blur (never a carrier / named / complement cell; in may-move by D2, classified RING-FENCED first); `wireRenumbering` kept; decision tables empty (`reverted` [] · `restate` {} · `contingency` [] · goSmall false); seam files at HEAD = the registry's sha / line counts (8488 / 6291) and every quoted anchor line reads its text |

## 2. Defects

### Must-fix (a claim fails, or the plan's own rule is violated)

**M1 · `beforeEveryDeviceRun` is not an executable string.** PLAN §0 / §7 / D35 and `plan-build.py`'s comment call it "an
EXECUTABLE string, run from the shared tree's root". The generated string ends with a prose note
`   (never ./gradlew --stop; the /wave55- filter keeps other sessions' worktrees out, so no foreign process is stopped)`.
- `bash -n -c "<string>"` → `syntax error near unexpected token '('`, exit 2; `zsh -n -c` → `unmatched '` (the apostrophe in
  "sessions'"), exit 1 (`plan-skeptic-round-3.verify.d/guard-syntax.out.txt`). Cut before the note it parses (exit 0).
- So run verbatim (bash -c / eval) NOTHING runs — not clause 1, not the disk floor — and it exits 2, which reads exactly
  like a refusal; the operator then hand-strips a generated command. fix-r2's proof executed clause 1 alone, never the string.
- Fix: move the note to its own key (e.g. `beforeEveryDeviceRunNote`) or make it a trailing `# …` comment with no
  apostrophe; assert `bash -n` on it in `plan-build.py`; regenerate → `plan-prose.py --fill / --check` →
  `final55-analysis.gen.py` + `--check` → `readers-calibration.sh` (throwaway), one commit.

**M2 · A new fake class (added ink: the row double-painted) passes the three L3 dir-style gating keys.**
- `[S] dir-style-02a android row6` (→ U2), `row7` (→ U1), `row9` (→ U2) PASS on the U1U2 replay with each row's ink painted
  a second time 3 / 5 / 7 px lower inside its own band: gate exit 0, and `[C] … unaffected-rows` PASSes too
  (`verify.d/newfakes3.{py,out.txt}`). The whole-picture ghost +7y is accepted on all three rows and −12y on row 7
  (`verify.d/newfakes.out.txt`: 4 of 156 key reads; every other lane rejects every ghost).
- Why: `rows:` checks only WHERE the row profile peaks (0 ± 1), not how well it matches. Passing pictures read 0.99–1.00
  (ref, web, android today, the U1U2 / U1 / U2 replays); the ghosts read 0.91–0.94 (band) and 0.71–0.76 (whole). The
  extent rule reads x only (the ghost's ink bottom is +2: 131 → 133).
- Not caught elsewhere: the cell's floor is 0.95, and PLAIN already passed DEGENERATE at 0.9663 with rows 6 / 9 BLANK.
- PLAN §0 / D31: "every new fake a skeptic finds joins" the standing set, and §T exits 1 until the rule rejects it.
- Fix: in `positioned-parent-slot.geometry.py` `[S] dir rows`, (a) `rows:` peak ≥ 0.97 at 0 ± 1, (b) the ink bbox y-extent =
  the ref's ± 1. Add the band-ghost builder (+3 / +5 / +7 y, rows 6 / 7 / 9) to the standing set (`probes-teeth-r2.py` or an
  r3 module), plus one added-ink case per lane. Re-run `probes-selfcheck.py` and check that the U1U2 / U1 / U2 / PLAIN replay
  verdicts are unchanged.

### Should-fix

**S1 · Three committed self-test records still cite older bytes** (known; PLAN §8 step 6 makes their re-run the landing's
first sub-step):
- `ab-arms55.selftest.out.txt`: runner b8db42d8 vs ef8e232d;
- `land-units.selftest.out.txt` A–D: 06bb1f53 vs 817c9677;
- `toolkit-proofs.txt`: expectations cd3936d0, adjudicate 2c48748d, control-check e935cbac.

This pass narrows the gap. Toolkit A / B / D hold on the current bytes (row 5d), land-units E / F / G hold (row 6b), and
the runner's `--run` path differs from its self-tested bytes by one added precondition. Still unproven on the current
bytes: the runner's git-init T-cases and mutants M-L18 / M-per-arm / M-dry / M-onebyone, and land-units A–D.

### Nits

- **N1** `control-check.mjs` ignores an extra positional argument (`… wave54-final wave54-final extra` → exit 0 HOLDS).
  `geometry-gate.py` and `adjudicate.mjs` refuse an extra positional (exit 2).
- **N2** A missing input file crashes with exit 1:
  - `geometry-gate.py --exp /nonexistent` (FileNotFoundError), where exit 1 reads "a gating key FAILed";
  - `probe-readout.mjs /nonexistent` (ENOENT), where exit 1 reads "a rule fired";
  - `adjudicate.mjs` the same, which fails closed.

  The usage / refusal code is 2.
- **N3** The last section of `probes-selfcheck.txt` lists `compose-wpt-content-box-cb … clip-3 android → U2` as "fixed OK —"
  and says "with a fixed picture accepted 41". But §T's `L2 U2-fixed` case PASSes that key as pre-registered, so the true
  count is 42 of 43 (only safe-002 lacks one). The summary reads the §L replays only.
- **N4** Some hand counts outside the gen blocks are not caught by the STRAY list and carry no file citation:
  - §6 R3 and §8 step 12.3: "30 sections, every column 48/48/48 (css-cascade 43/43/43)" — corpus constants;
  - §9 hand rows: C2 "the 12 L2 / L3 carriers", D7 "stage 2 = 26 sections", D23 "the four grid `[X]` cells".

  All are true today (`sharedStems` 12, `probeRun.sections` 26, `paintOrderOnly` 4), and the generated `planDecisions`
  rows carry the same numbers.
- **N5** The L6 `[X] flex-…-safe-002 android` gating key has no picture that PASSes it (its replay keeps 50 crossing px).
  Its fakes fail for their own reasons (band 0, lost / extra > 4), so its teeth are shown. Whether a correct U2 device
  picture passes is first known at stage 1. This is already documented in §2 L6 and §7.
- **N6** `ab-arms55.revert-check-proof.sh <dir>` with a directory that does not exist sets `S=""`. Its `sed` gets an empty
  RE, and the script dies by SIGPIPE (exit 141) after the header line. It should `mkdir -p` the directory or refuse.
- (Recorded, not a defect.) The color class does not apply to these keys. Turning the dark ink grey passes 16 key reads
  (L2 / L3 / L6), but none of these units changes a color. Fading the whole picture to 50 % is rejected everywhere
  (`verify.d/newfakes2.out.txt`).

## 3. Not executed here, and why

- `ab-arms55.selftest.sh`, toolkit-proofs section C, and land-units self-test A–D: they `git init` or commit. This pass
  was allowed one git command (`worktree add` / `remove`). See S1.
- The wave55-open watchlist / hash reads: `wave55-open` is not to be read.

## 4. Tree

- **Before → after:**
  - HEAD `640276a0` → `640276a0`;
  - index sha256 `db6b5da3…` → `db6b5da3…`;
  - `status --porcelain --untracked-files=all` outside the two plan dirs: 0 → 0 lines;
  - the sha256 of all 725 files under `wave55-plan/` and `wave55-gate/`, taken before the first run and after the
    throwaway's removal: identical.
- **Incidental write.** One command ran `land-units.sh --emit-manifest` on the shared tree. It re-emitted
  `landing-manifest.json` byte-identically (sha256 f6716e2f… before and after; only the mtime changed, 19:49). This also
  confirms that the manifest regenerates from its two tables.
- **New files (written after the restore):**
  - this report;
  - `plan-skeptic-round-3.verify.done.md`;
  - `plan-skeptic-round-3.verify.d/` (the four new-fake scripts and their outputs, `refusals.out.txt`,
    `guard-syntax.out.txt`; scratch paths redacted).
- `git worktree list` holds no scratch tree.

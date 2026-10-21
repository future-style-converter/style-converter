# Wave 55 — gate toolkit stage: done note

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0` (the opening-gate records commit on top of
the briefs commit `a2a37a8e`, on `0d0d3a3d`). This stage made no commit. It changed no git state in this repo; the one git
operation was `git worktree add --detach <scratchpad>/wt55toolkit HEAD`, removed afterwards with `git worktree remove`.
Nothing outside `tools/titan/results/wave55-gate/` and `tools/titan/results/wave55-plan/` was written, and no tracked file is
modified (`git status`: untracked files only). No gate was running during the work: no `gate-driver` / `section-runner`
process, and the opening gate `wave55-open` and its fixture net are on record in `640276a0`. Every run was read-only and
niced; the `wave55-open` run dir was never read.

**Record of every number below:** `toolkit-proofs.txt`, written by `toolkit-proofs.sh`; the A/B runner's own record is
`ab-arms55.selftest.out.txt`. Where this note quotes a number, it comes from one of those two files.

## 0. Delivered (`tools/titan/results/wave55-gate/`)

| file | what |
|---|---|
| `adjudicate.mjs` | the wave-54 reader, repointed. Written by the generator stage. This stage verified it and fixed one comment string (see §1) |
| `control-check.mjs` | the same: the wave-54 reader, repointed. One usage-comment string fixed |
| `ab-arms55.sh` | NEW: the A/B arm runner (L7 U3, `instruments.md` §4 U3). Records are written only after a verified restore. `--dry-run` is the default |
| `ab-arms55.selftest.sh` → `ab-arms55.selftest.out.txt` | NEW: its self-test, run in throwaway `git init` repositories |
| `make-snapshot55.mjs` | NEW: the corpus-v6.21 snapshot generator, from `wave54-gate/make-snapshot54.mjs` |
| `toolkit-proofs.sh` → `toolkit-proofs.txt` | each instrument shown able to fail (A–D below). Result: every proof as required |
| `toolkit.done.md` | this note. `wave55-plan/toolkit.done.md` points here |

## 1. The two readers: verified, and two comment strings fixed

The generator stage had already copied both readers, and `readers-calibration.txt` holds their calibration (K1–K6, J1–J5).
This stage checked each item on the task's repointing list (`toolkit-proofs.txt` A0 and B0).

**Repointing (A0b):**
- the expectations path is `wave55-plan/expectations.json`;
- `--base` defaults to `wave55-open`;
- the usage names `score-gate.mjs wave55-open wave55-final --movers 0` → `wave55-gate/score-final.json` and
  `geometry-gate.py wave55-final --base wave55-open` → `wave55-gate/geometry-final.json`;
- the `probeDecisions` withdrawals are kept: `withdrawnPredictions`, `mustNotMoveAfter`, and in control-check
  `carriersWithdrawn`;
- the renumbered wire class is kept (`shift === runShift`, `wireRenumbered++`).

**No other code changed (A0c).** Against the wave-54 file, the code (non-comment) delta of `adjudicate.mjs` is exactly:
- the expectations path;
- the `--base` default;
- the usage string;
- the R5 complement walk.

**Remaining wave-54 strings.** `wave54` now survives only in each file's provenance header line (A0a, B0a). This stage fixed
two comment strings:
- `adjudicate.mjs:10` "Wave 54's closing-gate adjudication" → "Wave 55's … (wave 54's rules)";
- `control-check.mjs:33` usage example `--lane L1-lists-bakes` → `--lane L6-android-paint-order`.

Both edits are comment-only, so the generator's calibration still describes the code. The proofs below re-exercise both
readers on the edited files.

## 2. Proofs (`toolkit-proofs.txt`)

### A. `adjudicate.mjs` on synthetic `--movers 0` records

The records are built from the wave-54 record `wave54-gate/score-final.json`. The wave-55 base is that record's cur side,
because `wave55-open` equals `wave54-final` byte-for-byte (`_note.md`).

| proof | input | result |
|---|---|---|
| A1 | the record relabelled | red: R4 misses the 9 gating flips; R5 sees wave 54's moves |
| A2 | all 44 gating rows at their `to`, nothing else moved | exit 0 |
| A3 | A2 plus ONE lost cell | exit 1, **R1 only** (A3b) |
| A4 | prev `wave54-final` | exit 2 `ORDER` |
| A4b | the raw wave-54 record | exit 2 `ORDER` |
| A5 | the wave-54 `--movers 0.005` record, relabelled | exit 2 `NOT A --movers 0 RECORD: 81 cells listed against 4096` |
| A5b | the same record, raw | `ORDER` fires first |
| A6 | `--geometry wave54-gate/geometry-final.json` | exit 2 `GEOMETRY PAIR` |
| A7 | a scratch copy with R1 blinded, run on A3's record | exit 0: R1 is the rule that sees the lost cell |

The lost cell in A3 is a wave-55 capture carrier with no gating row, which is why only R1 can see it.
- Every carrier has a pre-registered row, so "no prediction at all" matches nothing.
- Carriers are in no must-not-move list.

### B. `control-check.mjs` on wave54-open → wave54-final, with the WAVE-55 carriers

Result: exit 1, `CONTROL FAILED: 81 capture(s) and 23 wire document(s)`.

**The proof is the count, and the accounting closes per platform (B2):**

| platform | changed | LEAK | wave-55 carriers | may-move |
|---|---|---|---|---|
| web | 40 | 40 | 0 | 0 |
| ios | 19 | 14 | 5 | 0 |
| android | 31 | 27 | 2 | 2 |

- No capture is ring-fenced on this pair.
- The changed counts are identical to the wave-54 reader's own record on the same pair (`wave54-gate/control-final-union.json`).
- The 7 carrier hits and the 2 may-move stems are named in the file.

**Wire:**
- 23 content-changed documents are all WIRE LEAKs, because `unionWireCarriers` is empty this wave.
- 15 documents read as renumbered, the same as the wave-54 reader. This shows the class is kept.

### C. `ab-arms55.sh`

**C1. The self-test.** Every check is as required (`ab-arms55.selftest.out.txt`). It uses throwaway `git init` repositories,
two arms, and stubbed driver, hash, ab-diff and own-processes scripts.
- **T1:** exit 0. HEAD is restored and the arm branches are gone. Each `== wave55-ab-<arm>` block appears exactly once, and
  the in-arm `tee -a` copies were discarded. Both read-outs and driver copies are written. Each driver ran on its reverted
  tree. The stub pool's markers were removed. The records appear in the log after the last restore.
- **T7:** the real `/tmp/titan-device-pool` and this repo's refs / worktrees / stash are unchanged.

**C1. The mutations.** All three are RED:
- **M-L18** (the wave-54 order) → exit 7, blocks 0, while the read-outs survive, exactly as in wave 54.
- **M-per-arm** (the brief's literal step 8) → the second arm is refused because the first block dirtied the tree (exit 6).
- **M-dry** (`--dry-run` falling through to `--run`) → the identity check sees the change.

**C1. Other cases:**
- The dry run, both as the default mode and as `--dry-run`, leaves refs and files byte-identical.
- 10 refusals (T5a, T5c–T5k), each with exit 2 and nothing touched; T5b shows that an untracked file does not block.
- A failing driver → exit 1, with the records still written.
- A driver that never reaches `attempt 1 starting` → an explicit NOT READ block, exit 1.

**C2. `--dry-run` in the throwaway worktree (first).** Result: exit 2. It refuses on the detached HEAD and on `6e1c2d5e`
(not an ancestor), and it still:
- checks the revert of the chain `640276a0 a2a37a8e` with `git apply -R --check`;
- prints gate-driver's own `--plan`.

The worktree and the refs were byte-identical afterwards.

**C3. `--dry-run` on the shared tree (then).** Result: `DRY RUN OK`. Refs, stash, HEAD, tracked status, `build-hashes.txt`
and `final/` were unchanged.

### D. `make-snapshot55.mjs`

| proof | what | result |
|---|---|---|
| D1 | the wave-54 inputs (via options), on `wave54-gate/score-final.movers0005.json` | totals equal corpus-v6-20's, and the whole file is **byte-identical** to `corpus-v6-20.json` |
| D2 | the same totals compared against `corpus-v6-19.json` | they differ, so the comparison can fail |
| D3a | the `--movers 0` record | `NOT THE 0.005 RECORD` (4015 of its 4066 movers have \|Δ\| < 0.005) |
| D3b | the wave-55 defaults on the wave-54 record | `ORDER` |
| D3c | `wave55-gate`, which has no `final/cell-review.json` yet | `NO CELL REVIEW` |
| D3d | a `build-hashes.txt` with no block for the run | `NO INSTALLED BUILD` |
| D3e | after every refusal | no output file written |
| D4 | an A/B arm block whose body names the run | not published as a launch (2 builds); the wave-54 `includes` match (mutation) publishes 3 |

The D4 case matters because `ab-arms55.sh` labels end in `read against wave55-final)`.

## 3. Decisions taken against or beyond the brief (`instruments.md` §4 U3), for PLAN §9

**R-a. Records are written after the LAST verified restore, not after each one.** The brief's step 8 appends per arm. With
two or more arms, the first arm's block sits in the tracked `build-hashes.txt` while the next arm runs, and that arm's
`git checkout -f` discards it (L18 again). So the loop only runs each arm and restores the tree. Steps 8 and 9 then run once,
after the loop. `ab-arms55.selftest.out.txt` T3 (M-per-arm) shows the brief's order fails.

**R-b. Refusals beyond the brief (exit 2, all listed at once by the dry run):**
- an arm name that is duplicated, or not `[A-Za-z0-9._-]`;
- an existing `tools/titan/runs/wave55-ab-<arm>`;
- an existing branch of that name (`checkout -B` would reset it);
- existing read-outs, or an existing `== wave55-ab-<arm>` label;
- shas that are not child-first;
- a section that is not in gate-driver's `DEFAULT_SECTIONS`;
- a platform that is not web / ios / android;
- a revert that does not apply (`git apply -R --check`, as one combined patch for a first-parent chain).

**R-c. `--against <run>`** (default `wave55-final`). L4's probe-time arm is read against `wave55-pre`, but `abRead.cmd`
hard-codes `wave55-final`.

**R-d. Failure handling:**
- a driver that never reaches `attempt 1 starting` gets an explicit NOT READ block and exit 1;
- an INT / TERM restores the tree and keeps the stage dir (exit 130).

**R-e. Exit codes:** 0 · 1 a driver failed · 2 refused · 3 a revert failed · 6 the restore was not verified · 7 the records
were not verified.

## 4. For the orchestrator: conflicts found, none fixed outside this stage's dirs

1. **L7 U3 ownership.** `ab-arms55.{sh,selftest.sh,selftest.out.txt}` is L7's revert unit U3 (`plan-build.py` L7 ownership;
   `land-units.sh` row 03 expects `wave55-instruments/unit-U3.patch`). These files now exist as untracked files here.
   - If they are committed with the toolkit / records, U3's patch will not apply, because the files exist; land with
     `--hold L7:U3` or drop row 03.
   - Recommended: commit them as the U3 unit commit, and tell L7 that U3 is delivered. L7 may review and amend it.
   - The U3 prediction in `expectations.json` (exit 0, HEAD restored, the block once, `final/ab-drop-U.*`, the real pool
     and the refs untouched, M-L18 red, M-dry byte-identical) is met by T1 / T2 / T4 / T7.
2. **Two arms named `drop-U2`** in `expectations.json` `abArms` (L4 at probe time, L6 at the closing gate) would share
   `wave55-ab-drop-U2`. The runner refuses the collision (T5f, T5i). Name them apart in the arms file: `drop-L4-U2`,
   `drop-L6-U2`.
3. **A stale reader name.** `expectations.json` `corpusSnapshotSource` / `closingGateFiles` (`plan-build.py` :1091 / :1098)
   name `wave52-gate/make-corpus.mjs` as the snapshot's reader. The wave-54 snapshot was in fact written by
   `make-snapshot54.mjs`, and the wave-55 one is `wave55-gate/make-snapshot55.mjs`. The fix is one string in
   `plan-build.py` plus a regeneration. It is left undone because `expectations.json` is generated and every calibration
   record cites its sha `cd3936d0…`.
4. **An inherited control-check property, not changed.** A post capture that is "absent before" is printed as `LEAK` but
   is not counted in the exit status (wave 53's code, kept verbatim). It cannot arise on `wave55-open → wave55-final`:
   the base is 30/30 with no short column. It is recorded here so that no one reads that line as a counted leak.

## 5. The closing-gate command lines these tools give PLAN §8 (cite them; do not copy numbers)

- `node tools/titan/results/wave55-gate/adjudicate.mjs tools/titan/results/wave55-gate/score-final.json --geometry tools/titan/results/wave55-gate/geometry-final.json`
  (`expectations.json` `adjudicate`).
- `node tools/titan/results/wave55-gate/control-check.mjs wave55-open wave55-final --json tools/titan/results/wave55-gate/control-final-union.json`
  (plus `--lane <id>` per lane).
- `bash tools/titan/results/wave55-gate/ab-arms55.sh <arms-file> --dry-run`, then `--run` in the orchestrator window.
  - Arms file lines: `<arm> <sections,> <platforms,> <sha…>`, shas child first.
  - Use `--against wave55-pre` for L4's probe-time arm.
- `DEV_SHA='0d0d3a3d+wave55 (gate tree <sha>)' node tools/titan/results/wave55-gate/make-snapshot55.mjs tools/titan/results/wave55-gate/score-final.movers0005.json <note.txt> tools/titan/results/corpus-v6-21.json --date <YYYY-MM-DD>`
  - The defaults are `corpus-v6-20.json`, `corpus-v6.21`, `wave55-open → wave55-final`, `wave55-plan/expectations.json` and
    `wave55-gate`.
  - It refuses until `wave55-gate/final/cell-review.json` and a `wave55-final …` installed-build block exist.

STATUS: COMPLETE — all four instruments delivered and each shown able to fail (`toolkit-proofs.txt`: every proof as required;
`ab-arms55.selftest.out.txt`: every check as required). The four conflicts in §4 are the orchestrator's: L7 U3 ownership,
the duplicate `drop-U2` arm names, the stale `make-corpus.mjs` pointer, and the inherited absent-before print.

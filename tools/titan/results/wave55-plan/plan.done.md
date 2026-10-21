# Wave 55 — PLAN stage: done note

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD 640276a0. This stage made no commit.

**What this stage touched.**
- Nothing outside `tools/titan/results/wave55-plan/` and `tools/titan/results/wave55-gate/` was written. `git status
  --short` lists only untracked files under those two directories, and no tracked file is modified.
- Nothing was built, captured or run on a device.
- The only git commands were `git worktree add --detach <scratch>/wt55plan HEAD` and `git worktree remove --force` of it
  (the incident below explains why it ran twice).
- No run directory was read beyond what the generator's records already cite. The `wave55-open` run dir was never read.

## 0. Delivered

| path (under `tools/titan/results/wave55-plan/`) | what |
|---|---|
| `PLAN.md` | the wave-55 plan: §0–§9 as asked, plus an empty §10 for decisions during the wave; sha256 `865649f1…` at completion |
| `plan-prose.py` | renders PLAN.md's machine-derived blocks (§ "How to read the numbers") and checks them; details below |
| `plan-prose.selfcheck.sh` → `plan-prose.out.txt` | `plan-prose.py --check` shown able to fail; result below |
| `plan-prose.worktree.out.txt` | the check reproduced on a clean checkout (`git worktree add --detach`, then removed) |
| `plan.done.md` | this note |

**Why `plan-prose.py`.** Wave-54 lesson 11: that wave's prose went stale the moment a hand-off regenerated
`expectations.json`. So every number PLAN.md takes from a generated file sits inside a marked `gen:` block (34 blocks),
rendered from four inputs:
- `expectations.json`;
- `plan-build.out.txt`;
- `cells-wave54-final.json`;
- `landing-manifest.json`.

`--check` exits 1 when:
1. a block differs from its rendering;
2. §9 misses a `planDecisions` id;
3. a hand-written `wave54-final <sec>/<test> <platform> <P|f> <ssim>` citation disagrees with the run of record (46
   citations checked);
4. the landing order misses a unit or breaks a dependency. The dependencies checked are:
   - revertOrder;
   - L7's landingOrder;
   - C10 (L4 → L3 → L5);
   - L1 before L5 U3-ios;
   - C7 (L5 D after U2o);
5. a lane heading is missing;
6. the landing manifest and `expectations.json` disagree on a unit or on a revert order.

`--fill` rewrites the blocks; it is the "restate in the same commit" step.

**Proof that `--check` can fail** (`plan-prose.out.txt`). Every mutation ran on SCRATCH COPIES of PLAN.md,
`expectations.json`, `cells-wave54-final.json` and `landing-manifest.json`, and 16 of 16 turned the check red:
- M2: a §9 row missing;
- M3 / M3b: a wrong score, and an unscored cell;
- M4 / M5 / M6 / M8 / M9: the landing-order constraints, through a mutated manifest filled into a PLAN copy, so only the
  independent checks can fire;
- M14 / M15: the manifest disagreeing with the JSON;
- M7: a hand edit inside a block;
- M12: a deleted block;
- M13: a renamed lane heading;
- M1: a regenerated prediction;
- M10: a disagreeing run of record;
- M11: a REAL regeneration, `plan-build.py --go-small --out <scratch>`, that PLAN.md has not followed.

The installed files' sha256 values are identical before and after the run (G0 / G1).

**It also fired for real.** While this stage was running, the landing stage regenerated `landing-manifest.json` (L7 U3
became `optional`). `--check` went red on exactly that block. `--fill` restated it, and the text was aligned (§1 below).

**Clean-checkout reproduction** (`plan-prose.worktree.out.txt`):
- `--check` HOLDS in a fresh worktree, run from another cwd;
- `--fill` there is byte-idempotent;
- the worktree's status shows only the six copied inputs;
- the worktree was removed.

## 1. Alignment with the two stages that ran beside this one

Two other planning stages wrote into these directories while this stage ran. PLAN.md cites their files instead of
restating them:
- the **landing stage**: `land-units.sh`, `land-units.selftest.*`, and the GENERATED `landing-manifest.json`;
- the **gate-toolkit stage**: `wave55-gate/ab-arms55.*`, `make-snapshot55.mjs`, `toolkit-proofs.*` and `toolkit.done.md`.

Where they changed the procedure, PLAN.md follows them and records the decision in §9:

| decision | what PLAN.md now says |
|---|---|
| **D18** | Lanes deliver `unit-<U>.patch` / `seam-<n>.patch` and never edit the shared tree. This is the landing stage's `deliveryContract`, replacing wave 54's shared-tree model. §0, §2 and §4 are written to it. |
| **D17** | `land-units.sh` exists at planning. Its `--check` (each patch alone, the cumulative series, the export-tree revert dry-runs) replaces wave 54's `integrate-seams.sh`. §4's commit list is RENDERED from the manifest, and `plan-prose.py` re-checks it independently. |
| **D19** | L7 U3 (`ab-arms55.*`) was delivered by the toolkit stage. It is committed with the gate records. U3 is optional: a patch is delivered only for amendments, and the landing checks the files are tracked at HEAD. This follows the landing stage's resolution, which changed while this stage ran. |
| **D8** | The two `drop-U2` arms collide on `wave55-ab-drop-U2`. They are renamed `drop-L4-U2` / `drop-L6-U2` (the toolkit's recommendation; its runner refuses the collision). |
| **D9** | The L4 probe-time arm uses the runner's `--against wave55-pre` (toolkit R-c). |
| **D12** | [W-L7a] runs BEFORE the landing, with pre = HEAD and post = HEAD + `unit-U5.patch`. U5 lands at manifest seq 9 only if it holds, otherwise under `--hold L7:U5`. |
| **D20** | `expectations.json` names `make-corpus.mjs` as the snapshot reader; the reader is `make-snapshot55.mjs`. |

## 2. For the orchestrator (not done here, by design)

1. **D8 and D20 are owed to the generator.** Apply them in one `plan-build.py` regeneration: the two `abArms` labels, and
   the `make-corpus.mjs` strings. Follow it with `plan-prose.py --fill` / `--check` and a re-run of the records that cite
   the JSON's sha256 `cd3936d0…` (`probes-selfcheck.txt`, `readers-selfcheck.txt`, `readers-calibration.txt`). This stage
   did not regenerate, because those records would then cite a stale sha.
2. **If `landing-manifest.json` changes again,** run `plan-prose.py --fill`, then `--check`. The check is red until then,
   by design. At completion it reads manifest sha256 `0b46b9b2…`.
3. **Comment nits.**
   - `wave55-gate/adjudicate.mjs` header line 27 still names `hyphenate-character.geometry.py`; the code reads
     `degenerateRetirementCheck.script`.
   - `land-units.sh` row 03's note names the arms "L4 drop-U2, L6 drop-U2", the pre-D8 labels. Arm names come from the
     arms file, so nothing breaks.
4. **Commit with named paths only** (§0). The landing stage's files were still changing during this stage (latest mtime
   15:16). Commit after its done note says COMPLETE.

## 3. Incident (repaired; no residue)

- The first clean-checkout run piped `git worktree add` into `head -1`. That closed the pipe and killed git mid-checkout,
  so the copies failed and that run's record was discarded.
- Afterwards I checked three things: no `wt55plan` entry in `git worktree list`, no admin dir under the common
  `.git/worktrees/`, and no directory. Nothing was left.
- The run was then repeated without the pipe (`plan-prose.worktree.out.txt`); add and remove both exit 0.
- No `git worktree prune` was needed, and none was run.

## 4. How the task's checklist maps onto PLAN.md

| task item | PLAN.md |
|---|---|
| §0, the contract | ring-fence (with D2's report-only path); honesty labels; single-writer sweep with `--rerun-tasks` on every Gradle suite; floors = 0.95; device-probe rule (`unitCoverage`); hash of record after `provision rc=0` of the corpus launch (plus the apk-asset correction); records after restore; named-path commits while instruments write; plus the throwaway-worktree / no-rewind rule and the patch-delivery model |
| §1 | `pictureCorrectTally` rendered (22 in full); `stayDegenerateEvenIfPass`, `degenerateByConstruction` with its retirement check, the ring-fence and the wall; expected movement computed with `plan-build.py`'s own tiering |
| §2 | per lane: ownership, units and revert order, carriers (counts rendered), predictions table (rendered), geometry keys (rendered), the stage-1 decision (rendered verbatim), pins, may-run, windows, risks |
| §3 | `seam-registry.md` in full, with C10 / C12 / C13 resolved |
| §4 | the commit list rendered from `landing-manifest.json`, re-checked against `expectations.json`; why the order holds; the sweep |
| §5 | every BACKLOG obligation 0, 0(a)–(o) mapped; C7 → L5 D, C8 → L2 U1, C9 → the docs pass; the not-staffed list |
| §6 | verbatim strings (`scoreOfRecord`, `closingCmd`, `adjudicate`, `scoreReadout`, `closingGateFiles`, `readerChanges`, calibrations); R1–R9 |
| §7 | stage 1 `wave55-pre` (9 sections) and stage 2 `wave55-probe` (26 sections), rendered; revert rules 1–8 from wave 54 verbatim with the wave-54 §10 amendments folded in and marked, then the machine form |
| §8 | the closing procedure step by step: two launches; the hash of record; read-out order scores → geometry → adjudicate --geometry → probe-readout → control → probe→final → sheets → census → predictions → tally; cell review with adversarial second readers; A/B arms with records after restore; snapshot via `make-snapshot55.mjs`; the truth check |
| §9 | C1–C15 and D1–D7 (each with brief / said / does / why, and the `planDecisions` table verbatim); D8–D20 this plan's own |

STATUS: COMPLETE — PLAN.md §0–§9 (+ an empty §10) is written. Its generated blocks HOLD against `expectations.json`
`cd3936d0…` and `landing-manifest.json` `0b46b9b2…`. Every planDecisions id C1–C15 / D1–D7 is resolved in §9. The new
check was proven able to fail (16 / 16 mutations red) and reproduced on a clean checkout. Two generator strings (D8, D20)
are left for the orchestrator's next regeneration, on purpose.

# Wave 55 — plan-skeptic round 1: process holes

**Target.** `PLAN.md` (expectations.json sha256 `cd3936d0…`, landing-manifest.json sha256 `0b46b9b2…`) on
`campaign/applier-campaign` at 640276a0, plus the planning-stage instruments: `plan-build.py`, `plan-prose.py`,
`land-units.sh`, `geometry-gate.py`, `probe-readout.mjs`, `../wave55-gate/{adjudicate,control-check}.mjs`, `../wave55-gate/ab-arms55.sh`.

**What I attacked.** The process, not the lanes' physics:
- PLAN §8, step by step, against wave skill Phase 5 / 6 and the wave-54 lessons. That covers the hash of record, records
  after restore, named-path commits, floors, device probes and the apk-asset finding in `../wave55-gate/_note.md`.
- Seam ordering against the registry (C12 / C13), with the landing order simulated on real bytes.
- The 15 audit conflicts against `expectations.json`.
- The lanes' mutual dependencies against revert rules 6–8.
- What a limit-killed builder leaves behind.
- Hand-written numbers in the prose (wave-54 lesson 11).

**How.** Every claim below was executed.
- The script is `plan-skeptic-round-1.process-holes.repro.sh <scratch>`, and its full output is
  `plan-skeptic-round-1.process-holes.repro.out.txt`. Both are beside this file; section tags E1–E11 refer to that output.
- The script is read-only on the shared tree:
  - `plan-build.py` runs with `--out <scratch>`;
  - `plan-prose.py` runs on a scratch copy of PLAN.md;
  - `land-units.sh` runs `--check` / `--print-order` only.
- The only state-changing git calls were one `git worktree add --detach <scratch>/wt-status HEAD` and its
  `git worktree remove`.
- Section Z of the output shows the shared tree's HEAD and status unchanged, apart from untracked files in the two plan
  directories. Nothing was built, captured or provisioned. No device process was running (`pgrep` for
  gate-driver / section-runner / test-all / ab-arms printed nothing).

**Verdict: DEFECT-CONFIRMED.**

| severity | count | ids |
|---|---|---|
| must-fix | 4 | M1–M4 |
| should-fix | 14 | S1–S14 |
| nit | 7 | N1–N7 |

M1 must be fixed before the builder Workflow starts, because it changes what three lanes cut. M3 must be fixed before
the landing. M2 and M4 must be fixed before stage 1.

---

## 1. Checks that HOLD (executed)

| # | check | output |
|---|---|---|
| E1 | `plan-prose.py --check` on the shared tree | `34 gen blocks · 22 decision ids · 46 wave54-final cell citations · 28 landing-order units (want 28)` → `HOLDS`, exit 0 |
| E2 | `plan-build.py --out <scratch>` reproduces its outputs | `expectations.json`, `watchlist.txt` and `plan-build.out.txt` are byte-identical to the committed copies (sha256 `cd3936d0…`) |
| — | `land-units.sh --print-order` (the table and constraint check, every mode) | exit 0; the 26 constraints hold |
| — | the C1–C15 resolutions encoded in `expectations.json` (one script; output below) | all 15 are encoded and consistent with §9 (C5's only "hit" is the WEB disclosure-styles row, which C5 does not cover) |
| — | the seam order against `seam-registry.md` §6 | `.kt`: L4 s1 (13) → L4 s3 (15) → L2 s1 (17) → L3 s1 (19) → L5 s4 (24) → L5 s5 (25). `.swift`: L4 s2 (14) → L4 s4 (16) → L5 s1 (21) → s2 (22) → s3 (23) → L1 s1 (26) → L5 s6 (27). This is the registry's sequence verbatim. The nearest cross-lane `.kt` hunks are :1165 → :1950 = 785 lines apart, so they are independent |
| — | the L5 `.swift` neighbourhood (C12), read at HEAD :4614-4654 | seam 2 (:4618) has post-context :4619-4621 and seam 1's :4626 hunk has pre-context :4623-4625, so they are disjoint. Seam 3 (:4646) and seam 1's :4650 hunk share context lines :4647-4649, but neither inserts inside the other's window. E5(c) shows that shape applies and reverts in either order. **No cross-lane `.kt` / `.swift` hunk pair fails in §4 order.** The one failing pair is in the shared TEST file (M1) |
| — | floors | all 44 gating rows floor at 0.95; all 15 f→f rows carry `floorNA`. The 17 rows with neither are all P→P MED/LOW rows with no gating key (the tier rule) |
| — | interpreter / keyCells (C14 / C15) | all 13 probe commands start with `/opt/homebrew/bin/python3.12 -B`; 0 expect keys lack `keyCells` |
| — | the apk-asset finding | `apps/android-harness/app/src/main/assets/tmpOutput.json` is gitignored (`.gitignore:90`). Every planned device run between `wave55-open`'s net and the `wave55-final` corpus launch is `--skip-fixture-net` (stage 1, its re-probes, stage 2, and the L4 arm through `gate-driver.sh --skip-fixture-net`). [W1] (test-all) is placed AFTER the gate (D11). So the asset the corpus launch embeds is all-then-color, the same as `wave55-open`'s, and §0's "comparable only when the last fixture-net asset is the same" holds. HOLDS (see N7 for a free second witness) |
| — | records after restore (arms) | `ab-arms55.sh` writes every record after the last verified restore. Its self-test cites runner sha256 `b8db42d8…`, which equals the current file's sha256 |
| — | gitignored evidence in the plan dirs | `git ls-files --others --ignored` over `wave55-plan/` and `wave55-gate/` is empty |

C1–C15 encoded-check output (executed against `expectations.json` + `landing-manifest.json`):
```
C1   OK  stage1 9 sections +css-writing-modes; complement 3352 cells
C2   OK  sharedStems 12; mayMove∩other carriers 0
C3   OK  L7 must-not-move holds none of the 5 excluded cells ([]); L7 probes: ['flex-zero-gap-rules.geometry.py', 'web-out-of-flow-hyphen-box.geometry.py']
C4   OK  L5 probes ['counter-suffix-rows.geometry.py', 'rtl-marker-bake.geometry.py', 'lists-bakes.geometry.py']; L4 dropped ['text-decoration-inset-011 ios', 'text-decoration-inset-011 android']
C5   (ok) disclosure-styles ios ∈ L5 U1-ios True; android ∈ L6 mayMove True; in L5 must-not-move only the WEB row
C6   OK  L3 dropped ['[C] anchor-center-safe-rtl android']
C7   OK  D needs ['U2o-ios', 'U2o-android']; D revertOrder ['D']; U2o-ios revertOrder ['U2i-ios', 'U2o-ios'] (D not in it → S1)
C8   OK  L2 U1 carries the breadcrumb row
C11  OK  gating 44 all floor 0.95: True; f→f all floorNA: True; dir-style-02a fallbackIfReverted: True
C12  OK  L4 dryRuns + L5 U1-ios dryRun present
C13  OK  L2 JSON cites StyleApplier by line: []
C14  OK  13 probe cmds on /opt/homebrew/bin/python3.12 -B
C15  OK  expect keys without keyCells: 0 []
```

---

## 2. MUST-FIX

### M1 — `SeamReachabilityTest.kt`: five module lines from three lanes, one insertion point. §4's order cannot apply, and earlier units cannot be reverted under it

**Where.** PLAN §3 "Shared test files that ride with seams" (:1517) and C10. `landing-manifest.json` `deliveryContract.cutOn`.
The briefs' insertion point: the `modules = listOf(…)` list ends at `"transforms/TransformInheritance.kt",` (:182) before `)`.
The writers are:
- L4 seam 1 (`UaPositionedHeadingMargin.kt`);
- L4 seam 3 (`UAHeadingFoldGate.kt`);
- L3 seam 1 (`PositionedParentSlotGate.kt`);
- L3 unit-U2 (`PositionedParentRtlOrigin.kt`);
- L5 seam 4 (`ListMarkerSuffixGap.kt`).

**Evidence (E5).** The test file's real bytes at HEAD, patched as a lane in its own worktree off HEAD would patch them
(D18):
```
(a) END, each cut on HEAD          land: L4s1 ok L3s1 FAIL L5s4 FAIL
(b) re-cut chain: git apply -R --check c_L4s3  on the full stack: FAIL
(b) re-cut chain: git apply -R --check c_L4s1  on the full stack: FAIL
(b) re-cut chain: git apply -R --check c_L3U2  on the full stack: FAIL
(b) re-cut chain: 3-way revert of L4 seam 3 (git revert's merge) on the full stack: CONFLICT
(c) pinned anchors, §4 order       land: A_L4 ok A_L3 ok A_L5 ok · revert latest-first: all ok
(c) pinned anchors, reversed order land: A_L5 ok A_L3 ok A_L4 ok · revert latest-first: all ok
```

**Why it matters.**
- (a) The lanes run in parallel and cut on HEAD, so L3's and L5's patches fail after L4's. The cumulative `--check`
  stops the landing.
- (b) The plan's resolution ("each re-cut on the tree it lands on") makes the five lines adjacent. That has three
  consequences:
  - Reversing an EARLIER unit fails on the landed series. The `--check` C12 dry-run for L4 goes red: the landing stage's
    own `land-units.selftest.out.txt` C11–C15 prove that instrument fires on exactly this shape.
  - A stage-1 rule-4 revert of L4 U2-<p> or U1-<p> (`git revert`) conflicts with L3's line, and so does an L3 revert
    with L5 landed.
  - The probe-time arm `drop-L4-U2` is REFUSED by `ab-arms55.sh`'s `git apply -R --check` revert check, because U2-android's
    seam 3 carries the `UAHeadingFoldGate.kt` line.
- So the plan, as written, guarantees one blocked landing, then one fix round that re-cutting alone cannot fix, then
  revert / arm failures at stage 1.

**Fix (exact).** Pin a distinct anchor per writer, ≥ 3 lines apart, each cut on HEAD.
- Add to PLAN §3's "Shared test files" table, to each lane's §2 seam paragraph, and to plan-build.py's L3 / L4 / L5
  `commit` strings:
  - L4 seams 1 → 3: after `"content/PseudoGeneratedBox.kt",` (:159). Seam 3's line goes directly under seam 1's line;
    this pair is intra-lane and stacked.
  - L5 seam 4: after `"images/ImageCandidateChain.kt",` (:167).
  - L3 seam 1 → unit-U2: after `"transforms/TransformInheritance.kt",` (:182).
- E5(c) proves these anchors land in §4 order and in reverse order, and revert latest-first and out of order.
- Change C10's text from "re-cut each on the tree it lands on" to "cut on HEAD at its pinned anchor".
- Add a `land-units.selftest.sh` case with the three writers at these anchors.

### M2 — the device probes record no installed-build hash, yet §8 step 12.2 relies on "the probe's digest"

**Where.** §7 stage 1 step 1 (:1931) "Run the stage1Probe.cmd"; D13 re-probes (:1952); stage 2 (:1965); §8 steps 10–11.

**Evidence (E11).**
- `installed-build-hash.sh` is named only at :162 (the rule, CORPUS launch) and :2148 (the closing gate).
- Wave 54 recorded both probes: `wave54-gate/build-hashes.txt:21` (`== wave54-pre …`) and `:27` (`== wave54-probe …`).
- `expectations.json` has no hash string for either probe.
- §8 step 12.2 says "the iOS witness is the probe's digest when no Swift source changed between `wave55-probe` and the
  gate tree". With no probe read there is no such digest.
- BACKLOG Standing constraints :105-113: "A run without that hash is a score, not evidence". The probes decide reverts
  (rules 1–8), and a D13 re-probe explicitly includes or excludes code (NB-ios-b, the LRM fallback).

**Fix.** Add one sub-step to §7 stage 1 step 1, to every `wave55-pre-r<n>`, and to stage 2. Mirror it into
`plan-build.py` `stage1Probe` / `probeRun` as `hashRead` strings so the prose block renders it:

`until grep -q 'attempt 1 starting' tools/titan/runs/<run>/gate-driver/driver.log; do sleep 15; done; tools/titan/results/wave55-gate/installed-build-hash.sh '<run> (stage-<n> probe; tree <sha> = …)'`

The read is one-shot, right after `provision rc=0`, and its block is committed with named paths before the next device
run (see S6). Its `HEAD <sha>` line is also what S5's against-run check reads.

### M3 — `land-units.sh` lands a limit-killed lane: no STATUS is silent, PARTIAL is a warning, and nothing ties a patch to its note

**Where.**
- `land-units.sh:419-420`:
  `[[ -z "$st" || "$st" == "STATUS: COMPLETE"* ]] || warn …`
- The landing aborts only on `ERRS` (:582).
- PLAN §0 "No scratchpad pointers" says that "the landing verifies that each commit's staged set equals the paths its
  patches name". That compares the patch with itself, never with the note.

**Evidence (E6).** A synthetic L6 lane dir in a throwaway worktree (three creation patches), `--check --only L6 --root <wt>`:
```
note with NO STATUS line, no TREES: line                        exit=0  SUMMARY  0 error(s), 0 warning(s) — landable
note ending STATUS: PARTIAL                                     exit=0  SUMMARY  0 error(s), 1 warning(s) — landable
note names 2 U1 paths + a sha; the patch carries 1, another sha exit=0  SUMMARY  0 error(s), 0 warning(s) — landable
```

**What a killed builder leaves behind.** The resume rule (§8 step 3, "resumable through `args.done` / `args.only`")
covers none of it:
- a `_note.md` with no `STATUS:` and often no `TREES:` line yet;
- `unit-*.patch` / `seam-*.patch` files from the dead attempt, possibly cut before its last fix or while a pin mutation
  was un-restored (wave skill Phase 2: "the dead attempt may have left partial edits or an un-restored mutation");
- a registered worktree holding the partial edits and live Gradle daemons. `beforeEveryDeviceRun` stops daemons only
  for trees named on `TREES:` lines (`own-processes.sh:130` serves "only builds inside the ROOTs").

**Fix.**
1. `land-units.sh`: make a missing or non-`STATUS: COMPLETE` note an `err` (not `warn`) for every selected lane.
2. Require each note's unit section to carry one line per delivered patch, `patch: <name> sha256 <64 hex>`, written
   after the final restore. `--check` errors when a lane-dir patch is missing from the note, or when its sha256
   differs. This also catches a stale dead-attempt patch.
3. Lanes write `TREES:` FIRST, before any build.
4. The resume rule in §8 step 3 becomes:
   - "a relaunched lane re-opens the worktree on its `TREES:` line (or `git worktree list` under its lane name);
   - runs `git status` / `git diff` there on its owned paths;
   - restores any mutation;
   - deletes every patch in its lane dir and re-cuts all of them before writing STATUS".
5. `beforeEveryDeviceRun` also passes every `git worktree list --porcelain` path whose name carries `wave55-`.
6. Add the three cases above to `land-units.selftest.sh`, so the guard is shown red before it is trusted.

### M4 — a probe revert of L6 U2 leaves its 214 may-move captures exempt from the leak control

**Where.**
- `plan-build.py` `probe_decisions()` (:1017-1062) withdraws carriers, predictions and keys, but not `mayMove`.
- `unionMayMoveCaptures` is built unconditionally (:1207).
- `control-check.mjs:74` `const mayMoveSrc = lane ? (lane.mayMove || {}) : (EXP.unionMayMoveCaptures || {});`

**Evidence (E4).** Regenerated with `--reverted` = L6 U2 at `wave55-pre`:
```
unionMayMoveCaptures.android 214 -> 214 (after L6 U2 reverted; want 0)
carriersWithdrawn.android    25
```

**Why it matters.**
- The may-move class exists only because L6 U2 moves the legacy band. Once U2 is reverted, those 214 Android captures
  must be decoded-pixel identical to `wave55-open`.
- Left as they are, any other lane's leak into them prints `may-move` (not `LEAK`) in `control-check.mjs` at stage 2 and
  at the closing R4. In `adjudicate` R5 it gets a 0.002 band, so a sub-0.002 leak is invisible on both reads.
- This is the wave-54 lesson "A probe revert must withdraw its carriers into must-not-move", applied to the pixel class
  this wave added.

**Fix.**
- In `probe_decisions()`: when the reverted unit is L6 `U2` (or `U1`, whose revert requires U2's first), set
  `lanes.L6.mayMove = {'android': []}`, record `mayMoveWithdrawn: <the 214 stems>` in the decision, and compute
  `unionMayMoveCaptures` AFTER `probe_decisions()`.
- Add a `readers-selfcheck.sh` mutation: regenerate with L6 U2 reverted plus a synthetic one-pixel nudge on one may-move
  capture. `control-check` must print `LEAK`, and a copy without the withdrawal must not.

---

## 3. SHOULD-FIX

**S1 — L5 unit D's text is true only after U2o, but no revert order says so.**
- Evidence:
  - C7's own resolution is "its corrected text is true only after U2o".
  - D carries the `counter-style-table.mjs` SUFFIX NOTE "a U+3001 suffix has no stand-in gap".
  - Yet `revertUnits.D.revertOrder = ['D']`, while U2o-ios is `['U2i-ios','U2o-ios']` and U2o-android is
    `['U2i-android','U2o-android']` (`plan-build.py:694`, `:699`; land-units rows 22 / 24).
  - A stage-1 U2o revert, which §7 allows ("a key → its unit"), therefore ships a false comment.
- Fix:
  - In `plan-build.py`: U2o-ios `revertOrder ['D','U2i-ios','U2o-ios']` and U2o-android
    `['D','U2i-android','U2o-android']`.
  - In land-units rows 22 / 24, the same orders; run `--emit-manifest`; add rule 6 "L5 D before U2o".
  - Alternatively, split the SUFFIX NOTE sentence out of D into U2o-android's commit.

**S2 — D13 re-probes overwrite the stage-1 record, and five of six read-outs have no record path (E7).**
- Substituting the run id in `stage1Probe.readOut` leaves `score` writing `wave55-gate/score-pre.json` /
  `score-pre.movers0.txt` (`PROBE_READOUT` keys the file on `run.split('-')[1]` = `pre`).
- `abDiff`, `floors`, `controls` and `geometry` write stdout only, while D13 says "records under `wave55-gate/stage1/`".
- Fix:
  - `PROBE_READOUT(run, …)` writes every read under `wave55-gate/<stage>/<run>/`: `score.json`, `score.movers0.txt`,
    `abdiff.txt`, `floors.txt`, `controls.txt`, and `geometry.json` / `.txt` (`--json`).
  - Key them on the full run id; render one block per pre-registered re-probe id (`wave55-pre-r1`, `-r2`).

**S3 — the regeneration inputs are unnamed, uncommitted, non-cumulative and unrecorded (E8).**
- §7 step 5 says only "`plan-build.py --reverted <file>`".
- Given only the stage-2 decision file, the stage-1 L6 U2 decision vanished: its 6 `[X]` keys gated again on a tree
  without U2. Nothing in `expectations.json` / `plan-build.out.txt` records which `--reverted` / `--restate` /
  `--contingency` / `--go-small` inputs produced it, so `readers-selfcheck` A's byte-reproduction cannot be re-run from
  the committed tree.
- Fix:
  - Fixed, committed paths: `wave55-plan/probe-decisions/{reverted,restate,contingency}.json`, append-only.
  - `plan-build.py` reads them by default (no flag) and asserts that every earlier `REVERTED` row is still present (a
    `.prev` copy, or a `run` ordering check).
  - It writes `generatedWith: {argv, inputs: {path: sha256}}` into `expectations.json` and prints it as the first line
    of `plan-build.out.txt`.
  - `--go-small` becomes a committed `holds.json` entry.

**S4 — one full sweep, placed before S1. Wave 54 needed three.**
- §4 step 5 / §8 step 7 runs the sweep and restamp, then §8 step 8's S1 fix lanes add `-S1` commits.
- Stage-1 / stage-2 reverts remove tests: wave 54's CBB revert took Compose 3492 → 3486.
- No step re-runs the sweep, restamp and `doc-staleness-check.sh` on the gate tree. Wave 54 needed
  `sweep-landed.pre-s1fix.txt`, `.pre-probe-decisions.txt` and `sweep-landed.txt`.
- Fix:
  - §8 step 8½: after the S1 fix commits, the full single-writer sweep plus restamp (`wave55-gate/sweep-landed.post-s1.txt`).
  - §8 step 11½: after the last probe decision, the sweep, restamp and doc-staleness on the tree the closing gate will
    run (`sweep-landed.txt`). The gate tree then equals the ship tree code-wise.

**S5 — the L4 probe-time arm is read against `wave55-pre`, but runs on a different tree.**
- §7 order: step 5 commits stage-1 reverts, step 6 lands swaps (NB-ios-b, the L5 LRM re-cut), and step 7 runs
  `drop-L4-U2 --against wave55-pre`.
- `ab-arms55.sh` builds the arm from HEAD minus the U2 shas, and never checks that `--against`'s tree equals HEAD
  (`AGAINST` is used only in the label and in the `ab-diff` call, :57 / :181 / :221).
- L1's carriers include the dotted ×2 iOS cells in css-text-decor (the shared stage-1 table), so an NB-ios-b swap or an
  L1 revert would appear in the arm's Δ and be read as L4 U2.
- Fix:
  - Run step 7 BEFORE any step-5 / step-6 commit, with HEAD == the `wave55-pre` tree. Prove it from the M2 hash block's
    `HEAD`.
  - Otherwise read the arm against the latest `wave55-pre-r<n>` whose tree is HEAD.
  - Add to `ab-arms55.sh` a refusal when the `== <against> …` block in `build-hashes.txt` names a HEAD whose
    `git diff --quiet <that> HEAD -- runtimes apps converter tools/titan` fails.

**S6 — tracked records are dirty before every arm, so the runner refuses.**
- `build-hashes.txt` is tracked (`git ls-files`).
- Every hash read appends to it: 12.2, the net read, and the M2 probe reads.
- The closing net regenerates the tracked `tools/visual/baseline-stats.json` (`../wave55-gate/_note.md` :45).
- `ab-arms55.sh:82` refuses on `git status --porcelain --untracked-files=no`.
- Neither §7 step 7 nor §8 step 15 says what to do, which invites an improvised `checkout -f`, or a Phase-6 `git add -A`
  that captures the churn.
- Fix:
  - Before each arm window, commit the gate records with named paths (`build-hashes.txt` + that window's read-outs).
  - Record `git diff --stat tools/visual/baseline-stats.json`, then restore that one path
    (`git restore --source=HEAD -- tools/visual/baseline-stats.json`) only when the diff is timestamp-only, as
    `wave55-open` did. Write both into §8 12.4.

**S7 — numbers outside the gen blocks drift on regeneration, and so do two literals inside the generator (E9, E10).**
- After a `--fill` with L6 U2 reverted, `plan-prose --check` HOLDS. Yet the tally block reads
  `**16 in full · 0 in part · 6 withdrawn**` while :330 still says `- L6: 7 — clip-rect [PO] and the six [X] rows`.
- The same applies to:
  - :325-330 "By lane";
  - :401 "28 revert units";
  - :1542 "13 registered patches";
  - :76 / :206 "41 of the 43" / "all 43";
  - :1867 "707 list entries over 665 distinct cells";
  - the §7 "required" table (`[M] ×8`, `the 6 gating keys … the 10 controls`, `[X] ×6`).
- Inside `plan-build.py:1119-1120` the strings `'the 9 gating flip rows'` are typed by hand. After an L2-pair revert the
  JSON has 8 gating f→P rows and still renders "9" into §6's gen block.
- Fix:
  - Render "By lane" and the §7 table from `pictureCorrectTally` / `stage1Decision` as gen blocks.
  - Replace the counts with "(gen:union)" / "(landing-manifest.json)" citations.
  - Compute the calibration count from the rows (`len(gating f→P)`).
  - Add a `plan-prose --check` rule: every number matching `\b\d+\b` in a list of regenerable phrases outside gen blocks
    is an error.
  - Add the E10 mutation to `plan-prose.selfcheck.sh`.

**S8 — the arms do not say what they drop, and a reverted unit's arm is never withdrawn (E4).**
- `abArms[drop-NB-ios].drops` is absent (`plan-build.py:1193`), and so is L6's.
- After NB-ios-b lands, `drop-NB-ios` must drop NB-ios-b first: NB-ios-b calls `NormalLineBox.verticalPads`, so the arm
  tree would not compile. It must also drop every `-S1` commit on the unit.
- After L6 U2 is reverted, `abArms` still lists the L6 closing arm.
- Fix:
  - `drops` = `[contingency units landed] + [the unit's -S1 commits] + [unit]`, child first, generated.
  - `probe_decisions()` moves an arm whose unit is reverted to `abArmsWithdrawn`.
  - §8 step 15 builds the arms file from `abArms[].drops`.

**S9 — S1 and the post-landing fix lanes have no working model, and device runs have no tree-clean precondition.**
- §8 step 8 has S1 run "two mutations per lane" and fix lanes "one `-S1` commit per touched unit". Neither says where
  S1 runs (the shared tree is now the probe tree) or how an `-S1` commit is cut and landed.
- `gate-driver.sh` has no dirty-tree check (`grep porcelain|diff-index|dirty` is empty).
- So a limit-killed S1 mutation on the shared tree would be built into `wave55-pre`.
- Fix:
  - S1 mutates only in a throwaway worktree off the landed HEAD.
  - Fix lanes deliver `fix-S1-<U>.patch` into their lane dir, and the orchestrator lands it with
    `git apply --index --check` + `git apply --index` + a named-path commit (or a `land-units.sh --s1` row).
  - `beforeEveryDeviceRun` gains
    `git diff --quiet HEAD -- runtimes apps converter tools/titan ':(exclude)*.md' && git diff --cached --quiet || exit 2`.

**S10 — lane worktrees are not provisioned.**
- D18 puts five Compose lanes in fresh worktrees.
- `apps/android-harness/local.properties` is gitignored (`.gitignore:98`), and `ANDROID_HOME` / `ANDROID_SDK_ROOT` are
  unset on this host. So `./gradlew :runtime:testDebugUnitTest` fails with "SDK location not found".
- `node --test` / vitest also need the root `node_modules`.
- A lane that "fixes" this by running on the shared tree breaks D18.
- Fix: add to the §0 D18 bullet:
  "provision: `cp <shared>/apps/android-harness/local.properties <wt>/apps/android-harness/`;
  `ln -s <shared>/node_modules <wt>/node_modules` (plus the workspace links); write `TREES:` first".

**S11 — `final55-analysis.sh` is written at gate time by copying a script that runs off-contract commands.**
- §8 step 13 copies `wave54-gate/final54-analysis.sh`. That script runs `python3 $P/geometry-gate.py …`, not
  `geometryGate.closingCmd`'s pinned interpreter, and reads `$G/stage2/prediction-terms.txt`, which no wave-55 step
  produces.
- An instrument first written on the closing tree is unreviewed.
- Fix:
  - Generate `wave55-gate/final55-analysis.sh` now, from `expectations.json` (`scoreOfRecord`, `scoreReadout`,
    `geometryGate.closingCmd`, `adjudicate`, …).
  - Emit `prediction-terms.txt` from the prediction rows.
  - Prove it red on the identity record (J1-shaped) before the plan commit.

**S12 — no pre-registered revert state is compiled.**
- Rules 5 and 6 bisect and revert by commit, and the C12 dry-runs prove only that the patches apply.
- No step compiles the trees that rules 6–8 can produce: L4 minus U2-<p>; L5 minus U2i; L3 minus U2; L6 minus U2; the
  L2 pair; NB-ios minus NB-ios-b.
- A revert that does not build fails at provision, which costs a device window.
- Fix: each lane's delivery includes a focused compile + test per state in its `revertOrder` chains, on its worktree,
  recorded in the note. S1 re-runs it on the landed tree.

**S13 — [W-L7a] runs an unlanded instrument and is never re-run.**
- `wire-differential.sh` is NEW in L7 `unit-U2.patch`, so at §8 step 5 it is not on HEAD, and the plan does not say
  which copy runs.
- An S1 `-S1` commit on L7 U2 or U5 would leave the verdict describing other bytes.
- Fix:
  - Run the tool from a third throwaway at HEAD + `unit-U2.patch`, and record both patches' sha256 in the [W-L7a]
    record.
  - Any later change to U2 / U5 bytes re-runs [W-L7a] before stage 1.

**S14 — the owed D8 / D20 regeneration has no step.**
- §5 / §9 defer the arm renames and the snapshot-reader string "to `plan-build.py`'s next regeneration". §8 has no
  such step.
- Until it runs, `abRead.runId` `wave55-ab-<arm>` names two arms `wave55-ab-drop-U2`.
- Fix: make it §8 step 2's first sub-step, before the plan commit:
  - regenerate;
  - `--fill` / `--check`;
  - re-run `probes-selfcheck`, `readers-selfcheck` and `readers-calibration` (they cite the sha);
  - commit all of it together.

---

## 4. NITS

- **N1.** §8 step 6, "`land-units.sh --check` first with `--root` on a throwaway worktree". The lane dirs are untracked
  files of the shared tree, and the script reads them relative to `--root`, so the throwaway reports every lane dir
  MISSING (E6 had to plant one). Say "copy the seven lane dirs into the throwaway first".
- **N2.** Wave-54 lesson "a scripted multi-file doc edit … asserts each replacement applied; a commit message names only
  what its own `git show --stat` shows" is not carried into §8 steps 7 / 18. `grep scripted|replacement` in PLAN finds
  only unrelated hits: :341-342 "scripted inset" and :1970 "its replacement".
- **N3.** Phase 5's "set `ANDROID_SERIAL` when more than one device is attached" appears nowhere in §7 / §8. The memory
  notes emulator contention on this host.
- **N4.** R8 (the fixture net) first meets L6 U2's band change at the closing gate (D11 keeps test-all off the probes).
  `android-paint-order.gate-fixtures-scan.out.txt` shows 9 bordered gate components, 1 with a child. State this as an
  accepted risk in §8 risks, with what a red net costs (re-gate).
- **N5.** The L4 probe-time arm's read-out goes to `final/ab-drop-L4-U2.txt`. It is a stage-1 record, so use
  `stage1/ab-drop-L4-U2.txt`.
- **N6.** `TREES:` lines will name scratchpad worktree paths inside committed lane notes, while §8 step 18 requires
  "grep for a scratchpad path returns nothing". Either exempt `TREES:` lines explicitly, or put lane worktrees outside
  the scratchpad.
- **N7.** A free second Android witness: with the asset unchanged (see §1) and no Compose change between `wave55-probe`
  and the gate tree, the corpus launch's apk sha1 must equal the probe's. Add it beside the iOS witness in 12.2 (needs
  M2).

---

## 5. Checks run (by execution)

- E1 `plan-prose.py --check` → HOLDS.
- E2 `plan-build.py --out` → byte-identical ×3.
- `land-units.sh --print-order` → exit 0.
- The C1–C15 encoded-check script (output in §1).
- Floors / floorNA / interpreter / keyCells census over `expectations.json`.
- The seam-order comparison, manifest vs `seam-registry.md` §6, plus the `.swift` :4614-4654 context-window read.
- E4 `--reverted` L6 U2 regeneration → may-move 214 → 214 and the arm still listed.
- E5 SeamReachabilityTest landing / revert simulation in three shapes on HEAD's bytes (`git apply` and
  `git merge-file` as plain patch tools outside the repo).
- E6 `land-units.sh --check` on a synthetic killed lane in one throwaway worktree (created, then removed).
- E7 D13 substitution of the stage-1 read-out strings.
- E8 non-cumulative `--reverted` regeneration.
- E9 calibration-literal drift.
- E10 `plan-prose --fill` / `--check` on a regenerated copy → HOLDS with stale prose.
- E11 grep for hash reads, plus the wave-54 record labels.
- Greps:
  - `kill_own_gradle_daemons` roots;
  - the gate-driver dirty-tree check (none);
  - `.gitignore` for `local.properties` / the apk asset;
  - `ANDROID_HOME`;
  - `final54-analysis.sh` commands;
  - tracked `build-hashes.txt`;
  - `ab-arms55.sh:82`;
  - hidden source pins on the seam text (only value pins on `ListMarkerRow.gapPt` / `gapDp`, which no lane changes).
- Z: the shared tree's HEAD and status are unchanged; `git worktree list` holds no scratch tree; the stash is empty.

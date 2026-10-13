# Wave 55 — plan-skeptic round 2: process holes

**Target.** `PLAN.md` (sha256 `ddaa6955…`), `expectations.json` (sha256 `a1fdca71…`), `landing-manifest.json` (`90f077b0…`) on
`campaign/applier-campaign` at 640276a0, plus the instruments the closing procedure runs: `plan-build.py` (`dacc884a…`),
`land-units.sh` (`98b0b07b…`), `geometry-gate.py`, `probe-readout.mjs`, `../wave55-gate/{adjudicate,control-check}.mjs`,
`../wave55-gate/ab-arms55.sh` (`b5e1e7b1…`), `../wave55-gate/final55-analysis.{gen.py,sh}`, `../wave55-gate/installed-build-hash.sh`.
Round 1 (`plan-skeptic-round-1.process-holes.md`) and its fix pass (`fix-r1.done.md`) are the baseline: every defect marked
fixed is re-checked in §1.

**What I attacked.** The process, not the lanes' physics:
- PLAN §8 step by step against wave skill Phase 5 / 6 and the wave-54 lessons: the hash of record, records after restore,
  named-path commits, floors, device probes, and the apk-asset finding of `../wave55-gate/_note.md`.
- The seam order against `seam-registry.md` (C12 / C13), simulated on paper from the anchors at HEAD.
- The 15 audit conflicts: each one resolved in §9 and encoded in `expectations.json`.
- The lanes' mutual dependencies (the L2 pair, the L4 pairs, the L5 order) against revert rules 6–8.
- What a limit-killed builder (or skeptic, or fix lane) leaves behind, and whether the resume rule covers it.
- Numbers in the prose that no file backs (wave-54 lesson 11).

**How.** Every claim below was executed.
- The script is `plan-skeptic-round-2.process-holes.repro.sh <scratch>`. Its redacted output is
  `plan-skeptic-round-2.process-holes.repro.out.txt` (script sha256 `9cf37f5c…`). Both sit beside this file, and the tags
  H, E1–E25 and Z refer to that output.
- The script is read-only on the shared tree:
  - `plan-build.py` runs only with `--out <scratch>`;
  - `final55-analysis.gen.py` runs only with `--check --exp <scratch json>`;
  - `land-units.sh` runs `--check` only, through `--root` on a throwaway;
  - every other git call is a read, or a `git diff --no-index` / `git apply --check` used as a plain patch tool on
    scratch files.
- The only state-changing git calls were `git worktree add --detach <scratch>/wt-* HEAD` (wt-untracked, wt-fixs1,
  wt-armdry) and `git worktree remove --force` of the same three.
- Section Z shows HEAD and status unchanged outside the two plan dirs, and no scratch worktree left registered. Nothing was
  built, captured or provisioned. No gate process was running at any point (`pgrep` for gate-driver, section-runner,
  test-all, ab-arms, gradle and xcodebuild printed nothing). `tools/titan/runs/wave55-open/` was never read.

**Verdict: DEFECT-CONFIRMED.**

| severity | count | ids |
|---|---|---|
| must-fix | 1 | M1 |
| should-fix | 12 | S1–S12 |
| nit | 11 | N1–N11 |

M1 must be fixed before stage 1: the L4 arm runs there, and the S1 fix commits it would drop land before it. S2, S3, S6
and S7 must be fixed before stage 1, because each one breaks a pre-registered stage-1 path. S1, S4, S5 and S12 must be
fixed before the first device run. The rest can be fixed before the closing gate.

---

## 1. Round-1 defects marked fixed: re-checked

| round-1 id | fixed as | re-check (executed) | status |
|---|---|---|---|
| M1 SeamReachabilityTest single insertion point | pinned anchors :159 / :167 / :182, each cut on HEAD | anchors read at HEAD: `"content/PseudoGeneratedBox.kt",` :159, `"images/ImageCandidateChain.kt",` :167, `"transforms/TransformInheritance.kt",` :182. The test asserts no list order. `land-units.selftest.r1-EF.out.txt` F1–F6 cite the current `land-units.sh` (`98b0b07b…`). E3 C10: the anchors are in the commit strings and in `deliveryContract.cutOn`. E19: windows 157-162 / 164-170 / 179-185, disjoint | HOLDS |
| M2 probes read no hash | `hashRead` on stage 1, both re-probes and stage 2 | present (E11 of `fix-r1.process-holes.rerun.out.txt`). A **new hole**: the wait is not one-shot per launch (E9, **S10**) | HOLDS, with S10 |
| M3 a killed lane lands | note guards in `land-units.sh` | E0–E12 cite the current sha. A **new conflict**: the S1 fix-patch naming of §8 step 8 trips the same guard (E11, **S3**), and fix lanes have no PARTIAL-first rule (**S12**) | HOLDS, with S3 / S12 |
| M4 may-move not withdrawn | `probe_decisions()` empties L6 `mayMove` | E4: 240 → **0**, `drop-L6-U2` → `abArmsWithdrawn` | HOLDS |
| S1 D vs U2o | D in both U2o chains | E3 C7: `['D','U2i-ios','U2o-ios']` / `['D','U2i-android','U2o-android']`; manifest constraints U2o-ios < D and U2o-android < D | HOLDS |
| S2 re-probe records overwrite | `records` per full run id | `stage1/<run>/` for wave55-pre, -r1, -r2 and `stage2/wave55-probe/`. The regenerated re-read (`*.regen.*`) is still hand-named (**N2**) | HOLDS, with N2 |
| S3 regeneration inputs | `probe-decisions/*.json`, append-only, `generatedWith` | the files exist (`[]`, `{}`, `[]`, goSmall held false); `plan-build.out.txt` line 1 is `generatedWith`. Two decision shapes the plan names crash the generator: an `-S1` row (E24, **N1**) and GO-SMALL after landing (E5, **S7**) | HOLDS, with S7 / N1 |
| S4 one sweep | §8 8½ and 11½ | present (PLAN :2315, :2323) | HOLDS |
| S5 L4 arm on another tree | arm before any revert; runner refusal | `ab-arms55.against-proof.out.txt` P1–P5 cite the current runner. But the dry-run command PLAN writes omits `--against` (E25, **N3**), and the runner's own `--run` self-test is not re-run on these bytes (E12, **S6**) | HOLDS, with S6 / N3 |
| S6 dirty tracked records | named-path commits before arm windows | §7 step 5, §8 12.5 | HOLDS |
| S7 prose numbers drift | gen blocks plus the STRAY rule | E1 HOLDS. Two stray counts remain (E21, **N8**). §6's "Two caveats" went false at the round-1 regeneration (E14, **S11**) | PARTIAL |
| S8 arms' drops | `drops` generated, arms withdrawn | E4 holds. But the runner cannot execute those drops once an `-S1` or a contingency is in them (E22, **M1**), and GO-SMALL does not narrow the L4 arm (E5, S7) | **BROKEN by M1** |
| S9 dirty device runs, S1 model | `beforeEveryDeviceRun` clause 1; S1 in a throwaway; `fix-S1-<U>.patch` | clause 1 refuses a tracked edit (E8 control). It misses untracked sources (E8, **S1**). The fix-patch name conflicts with M3 (E11, S3) | PARTIAL |
| S10 lane worktrees unprovisioned | `local.properties` copy, `node_modules` link, TREES first | the link resolves `@style-converter/web` to the SHARED tree (E17, **N5**). The `wave55-` daemon grep has no naming rule behind it (E10, **S4**) | PARTIAL |
| S11 final55 copied at gate time | `final55-analysis.gen.py` → `.sh`, `--check` | it drifts on every regeneration, and §7 step 6 does not regenerate it (E7, **S8**) | PARTIAL |
| S12 revert states not compiled | `revertStatesCompiled:` | the §0 enumeration omits L5 minus U1-ios and L5 minus U3-ios (E20, **N7**) | HOLDS, with N7 |
| S13 [W-L7a] tool copy | a third throwaway at HEAD + `unit-U2.patch` | PLAN §8 step 5 | HOLDS |
| S14 owed regeneration | done in fix-r1 | E2: byte-identical reproduction | HOLDS |
| N1 lane dirs into the throwaway | step 6 | PLAN :2292 | HOLDS |
| N2 scripted-edit lesson | step 18 | present | HOLDS |
| N3 `ANDROID_SERIAL` | §7 step 0 | present at stage 1 only; absent from the closing gate and the arms (**N11**) | PARTIAL |
| N4 the net meets L6 U2 at the gate | risk 8 | present | HOLDS |
| N5 the L4 arm read-out path | queued | — | queued |
| N6 TREES: exempt from the step-18 grep | §0 | present. But 10 more record files carry scratch paths (E18, **N6**) | HOLDS, with N6 |
| N7 Android witness | 12.2 | present. The asset caveat is missing for iOS (E13, **S9**) | HOLDS, with S9 |

## 2. Checks that HOLD (executed)

| # | check | output |
|---|---|---|
| E1 | `plan-prose.py --check` | 36 gen blocks · 30 decision ids · 46 wave54-final citations · 28 landing-order units → HOLDS, exit 0 |
| E2 | `plan-build.py --out <scratch>` | `expectations.json`, `watchlist.txt` and `plan-build.out.txt` byte-identical |
| E3 | C1–C15 encoded in `expectations.json` / `landing-manifest.json`, plus a §9 row per C-id | all 15 OK. C1: 9 sections incl. css-writing-modes, complement 3352. C2: 12 shared stems, mayMove ∩ other carriers 0. C3: no counter-suffix native in L7's must-not-move, no rtl-marker probe 2. C4: L5 carries rtl-marker-bake / lists-bakes. C5: disclosure-styles ios ∈ L5 U1-ios, android ∈ L6 mayMove, ∉ L5 must-not-move. C6: dropped anchor-center-safe-rtl. C7: D in both U2o chains, U2o < D on both natives. C8: breadcrumb row in L2 U1. C9: no L2 unit edits ContentBoxRelativeInflationTest. C10: pinned anchors. C11: 44 gating rows all floor 0.95, 12 f→f rows all floorNA, `fallbackIfReverted`. C12: dry-runs. C13: no StyleApplier line pointers. C14: 13 probe cmds on the pinned interpreter. C15: 0 keys without `keyCells`. §9: rows for all 15 C-ids |
| E4 | an L6 U2 revert | may-move android 0; `drop-L6-U2` moved to `abArmsWithdrawn` |
| E19 | the landing order on paper, from the anchors at HEAD (each hunk = anchor ± 3 lines of context) | **no hunk pair fails in §4 order or under any rule-6 revert.** `.kt` cross-lane gaps 785 (L4 s1 :1165 → L2 s1 :1950), 1682 and 821. `.swift` cross-lane gaps 3617, 405 and 202. `SeamReachabilityTest.kt` gaps 8 and 15 (disjoint windows). Two pairs are intra-lane STACKED and cut in order: L4 s1/s3 (:1157-1165 ∋ :1161) and s2/s4 (:340-356 ∋ :352-356). One pair shares context only: L5 s3 :4646 vs s1 :4650, the C12 dry-run case. **Caveat:** where U1-ios binds `let painted` is unregistered. If it lands inside :4643-4649 it moves into seam 3's window, so the L5 C12 export-tree dry-run is the check of record |
| E23 | every native unit in `stage1Probe.decides` has a carrier in a stage-1 section | none missing |
| — | seam anchors and counts at HEAD | sha256 `2445d9ea` / `417853ce` / `c780ac1c` / `6ab6f2b8`. Lines 8488 / 6291 / 1479 / 12268. `0d0d3a3d..HEAD` has no code change. Every §0 `wc -l` figure is exact (14 files) |
| — | hidden source pins on seam text | only value pins on `ListMarkerRow.gapDp` / `gapPt`, plus L4's own `UAElementFontRuleSeamWiringTest.kt`. `ListMarkerOutsideHangRasterTests.swift` renders through `markerPlacement` (a raster pin, not a text pin: L5's focused suite must run it) |
| — | the apk-asset finding, for wave 55's own witnesses | only `test-all.sh` writes the harness IR (`test-all.sh:459-461`). Every device run between `wave55-open`'s net and the `wave55-final` corpus launch is `--skip-fixture-net`, and [W1] comes after the gate, so the 12.2 witnesses compare like with like. See S9 for iOS and for wave 56 |
| — | records after restore | `ab-arms55.sh` writes after the last verified restore. `land-units.sh` stdout goes to `$TMPDIR` and is copied afterwards. Hash reads on the shared tree need no checkout |
| — | the S5 check parses build-hashes labels | `^== wave55-pre ` does not match `== wave55-pre-r1 …`. With `tail -1`, an `--against wave55-final` read takes the net-launch block, whose HEAD differs from the gate tree only by results commits. The test-all IR targets are gitignored (`.gitignore:90`, `:92`), so a net block never says "+ uncommitted" |

---

## 3. MUST-FIX

### M1 — `ab-arms55.sh` refuses the arms the plan designs: its non-chain revert check tests each commit's reverse alone, on the current tree

**Where.**
- `../wave55-gate/ab-arms55.sh:142-147`, `revert_check()`:
  `else for c in "${s[@]}"; do gitr diff --binary "$c^" "$c" | gitr apply -R --check … || return 1; done`.
- PLAN §7 step 5 and §8 step 15. The `expectations.json` `abArms[].drops` are all child first, with the `-S1` fixes or a
  landed contingency ahead of the unit (E22):
  - `drop-NB-ios`: `NB-ios-S1* → NB-ios` (`NB-ios-b → NB-ios` once the contingency lands);
  - `drop-L4-U2`: `U2-ios-S1* → U2-android-S1* → U2-ios → U2-android`;
  - `drop-L6-U2`: `U2-S1* → U2`.

**Evidence (E22).** The runner's check, emulated with plain patch files: `git diff c^ c | git apply -R --check`, one
commit at a time on the current tree.
```
(a) a modified line: arms file 'drop-X … <W> <U>' (child first), current tree = U + W
    runner, one by one on the current tree:  W ok · U REFUSED   → the arm is REFUSED (exit 2)
    cumulative (what git revert W U does): W ok · U ok
(b) a created file (NB-ios) later extended (NB-ios-b, or any -S1 on a NEW file): drop-NB-ios 'NB-ios-b NB-ios'
    runner, one by one on the current tree:  NB-ios-b ok · NB-ios REFUSED   → REFUSED
    cumulative: NB-ios-b ok · NB-ios ok
```

**Why the combined-diff branch never rescues it.**
- That branch runs only when each sha is the parent of the one before it.
- The `-S1` commits land after every unit and after the 8½ sweep (§8 step 8). NB-ios-b lands at stage 1, after
  units 27 and 28. So no arm's list is a first-parent chain.

**What follows:**
- **`drop-NB-ios` with NB-ios-b landed is refused with certainty.** NB-ios-b adds `NormalLineBox.verticalPads` to
  `NormalLineBox.swift`, a file NB-ios creates (`plan-build.py:318`; `hyphen-ios-linebox.md:326`). The arm expect
  explicitly pre-registers "With NB-ios-b landed the arm drops NB-ios-b, then NB-ios".
- **`drop-L4-U2` is refused at stage 1** whenever S1 lands a `U2-<p>-S1` fix that touches U2's own lines or files (most
  S1 fixes on a unit edit the files that unit created). S1 runs before stage 1.
- The runner's self-test case T5j ("a revert that does not apply to HEAD (W rewrote U's line)") pins the refusal for an
  arm that omits W. No case covers an arm that INCLUDES W.

**Fix (exact).**
1. Replace the non-chain branch of `revert_check()` with a cumulative reverse-apply in a private export dir outside the
   repo. This is `land-units.sh`'s C12 pattern: reads only, writes only the mktemp dir.
   ```bash
   else
     local X f; X="$(mktemp -d "${TMPDIR:-/tmp}/ab-arms55-rc.XXXXXX")" || return 1
     for c in "${s[@]}"; do while IFS= read -r f; do
         [[ -e "$X/$f" || ! -e "$AB_ROOT/$f" ]] || { mkdir -p "$X/$(dirname "$f")"; cp -p "$AB_ROOT/$f" "$X/$f"; }
       done < <(gitr diff --name-only "$c^" "$c"); done
     for c in "${s[@]}"; do gitr diff --binary "$c^" "$c" | (cd "$X" && GIT_CEILING_DIRECTORIES="$X" git apply -R 2>&1) \
       || { rm -rf "$X"; return 1; }; done
     rm -rf "$X"
   fi
   ```
2. Add two cases to `ab-arms55.selftest.sh`:
   - **T5l:** commits U → an unrelated X → W, where W rewrites U's line. `drop-U <W> <U>` must dry-run with exit 0, and
     T5j (`<U>` alone) must stay exit 2.
   - **T5m:** a file created by N and extended by B. `drop-N <B> <N>` must exit 0.

   Re-run the whole self-test on the new sha256 and record it (see S6).
3. Change PLAN §7 step 5 and §8 step 15 to say: "the arms file lists the resolved shas in `git log` order (newest
   first)". The `drops` list gives only the set: the relative order of `U2-ios-S1` and `U2-android-S1` depends on how S1
   landed them, and the runner refuses any sha that is "not older than the sha before it".

---

## 4. SHOULD-FIX

**S1 — `beforeEveryDeviceRun`, the hash block and the arm runner are all blind to an UNTRACKED source file (E8).**
- **Evidence.** In a throwaway, I planted two untracked runtime sources (`StrayLaneFile.kt` and `StrayLaneFile.swift`):
  - clause 1 exits 0, so the device run proceeds and Gradle / SwiftPM compile both;
  - the `installed-build-hash.sh` "+ uncommitted" test (`installed-build-hash.sh:37`, `git diff --quiet -- …`) exits 0;
  - `ab-arms55.sh` `tracked_dirty` (`:80`, `--untracked-files=no`) prints nothing.

  The control (a tracked edit) is refused as designed. Under D18 almost every unit CREATES files, so a lane, fix or S1
  write that lands on the shared tree by mistake is most likely a NEW, untracked file. Root `Package.swift`, which the
  iOS harness builds (`apps/ios-harness/project.yml:19-21`, `path: ../..`), is outside every code-path list.
- **Fix.**
  - Make clause 1:
    `[[ -z "$(git status --porcelain --untracked-files=all -- runtimes apps converter tools/titan Package.swift ':(exclude)tools/titan/results' ':(exclude)*.md')" ]] || { echo "…"; exit 2; }`.
    On today's shared tree it lists nothing (E8), so it never refuses a clean tree.
  - Use the same status test for the hash block's "+ uncommitted" (the wave55-gate copy of `installed-build-hash.sh`).
  - Use it in `ab-arms55.sh` for the code paths only; untracked results stay allowed.
  - Regenerate `beforeEveryDeviceRun` from `plan-build.py`.
  - The planted-file fixture of E8 is the mutation that proves the new clause red.

**S2 — The L5 U1 "LRM fallback re-cut" (a pre-registered stage-1 swap) has no generator encoding and no landing tool (E6).**
- **Evidence.**
  - §7 step 7 says each swap "lands as its own commit (a contingency: an appended `probe-decisions/contingency.json`
    row)".
  - An L5 row crashes the generator with `KeyError: 'contingencyUnits'`. L5 has no `contingencyUnits`, and the loop at
    `plan-build.py:937-940` handles only units whose revertOrder ends in `['NB-ios']`.
  - `land-units.sh` has no ORDER row for the re-cut, and its `--contingency` mode lands "L1 NB-ios-b only".
  - The control (the L1 NB-ios-b row) regenerates cleanly, with NB-ios revertOrder `[NB-ios-b, NB-ios]`.
  - So the one path between "keep U1-ios" and "revert U1-ios" in L5's `stage1Decision` would be improvised at the probe:
    a hand landing plus a generator edit.
- **Fix.** Pre-register the re-cut as NB-ios-b is pre-registered:
  - `plan-build.py`: L5 gains `contingencyUnits: {'U1-ios-LRM': {commit, captures (= U1-ios's), revertOrder ['U1-ios-LRM'], onLand}}`.
    The contingency loop prepends the landed unit to every revertOrder that contains its base unit, not a hard-coded
    `['NB-ios']`.
  - `land-units.sh`: add ORDER row 30
    `L5|counter-suffix-rows|U1-ios-LRM|unit-U1-ios-LRM.patch|-|U1-ios|U1-ios-LRM|contingency|…`, add the constraint
    `L5:U1-ios < L5:U1-ios-LRM`, and change `--contingency` to take the unit (`--contingency L5:U1-ios-LRM`).
  - Add a self-test case for the new row.
  - Alternatively, define the fallback as "revert U1-ios, land U1-ios′" and encode THAT (a `reverted.json` row plus an
    ORDER row). Either way the stage-1 decision must have a tool before stage 1.

**S3 — S1's fix-patch naming (§8 step 8) is refused by the round-1 M3 guard (E11).**
- **Evidence.**
  - `land-units.sh:436-438` accepts only ORDER-table patch names in a lane dir.
  - A COMPLETE L1 note with `unit-NB-ios.patch` and `seam-1.patch` passes `--check` (exit 0).
  - Adding `fix-S1-NB-ios.patch`, named on its `patch:` line exactly as §8 step 8 prescribes, gives exit 1: "is not a
    patch the ORDER table names for this lane (a stale dead-attempt patch? delete it)".
  - The records loop has no `CONT` guard (only the force-add line mentions it), so `land-units.sh --contingency` (NB-ios-b
    at stage 1) refuses whenever S1 delivered any fix patch into the L1 dir. The orchestrator is then pushed to delete a
    record or edit the script mid-probe.
- **Fix.**
  - `known` (`land-units.sh:436`) also accepts `fix-S1-<U>.patch` for every U of the lane, still requiring its `patch:`
    sha line.
  - Add E-section cases: named → exit 0, unnamed → exit 1.

**S4 — `beforeEveryDeviceRun` cannot find the lane worktrees whose Gradle daemons it means to stop (E10).**
- **Evidence.**
  - The `grep -E 'wave55-'` over `git worktree list` matches 0 worktrees today.
  - Nothing in PLAN names a lane, skeptic, fix or S1 worktree with `wave55-`.
  - Workflow-isolated worktrees are named `wf_<run>-<n>`. Four are still registered from 2026-09-25, for example
    `…/worktrees/wf_c3a87fe8-936-12`.
  - The `TREES:` part of `beforeEveryDeviceRun` is a placeholder (`<every tree on a lane note's TREES: line>`), not a
    command.
  - `kill_own_gradle_daemons` stops a daemon only when EVERY directory it served lies under one of the roots given
    (`own-processes.sh:133-150`). So one missing lane root keeps alive every daemon that served it and any other lane,
    not just its own.
- **Fix.**
  - §0 D18: every lane, skeptic, fix, S1 and [W-L7a] worktree is created as
    `git worktree add --detach <scratchpad>/wave55-<role>-<lane> HEAD`, never as a Workflow-isolated worktree.
  - Render `beforeEveryDeviceRun` as an executable string:
    `kill_own_gradle_daemons "$PROJECT_ROOT" $(sed -n 's/^TREES: *\([^ ]*\).*/\1/p' tools/titan/results/wave55-*/_note.md) $(git worktree list --porcelain | sed -n 's/^worktree //p' | grep -E '/wave55-')`.
    The `/wave55-` filter keeps other sessions' worktrees out, so no foreign process is stopped.

**S5 — No disk budget and no worktree cleanup (E16).**
- **Evidence.**
  - Free space is 38 GiB.
  - The Catalyst DerivedData `StyleConverterRuntime-dhqlkcum…` is **4.0 GB**, and is keyed per checkout path
    (`WorkspacePath` = the shared tree), so every Swift-building worktree gets its own.
  - The Swift-building worktrees are four lanes (L1, L4, L5, L7 U6-swift), their skeptics' throwaways and the S1
    throwaway: about 8 × 4 GB, plus Gradle outputs.
  - `section-runner.sh`'s watchdog trips under 8 GB (wave skill Phase 5).
  - PLAN's only "worktree remove" hit is the planning-stage throwaway (:47). Four stale worktrees from an earlier wave are
    still registered.
- **Fix.**
  - Swift lanes, skeptics and S1 pass `-derivedDataPath <wt>/.dd` to `xcodebuild`.
  - Add §8 step 9½: after the landing's records commit and S1, `git worktree remove --force` every `wave55-*` throwaway
    and delete its DerivedData. The `TREES:` lines stay as history.
  - `beforeEveryDeviceRun` gains `(( $(df -g . | awk 'NR==2{print $4}') >= 20 )) || { echo "disk < 20 GiB"; exit 2; }`.

**S6 — The destructive-path self-tests are recorded against older bytes, and §8 never re-runs them (E12).**
- **Evidence.**
  - `land-units.selftest.out.txt` covers sections A–D: the REAL landing path, commits, the fault-knob reverse-apply,
    `--from` and the records commit. It cites `land-units.sh` sha256 `06bb1f53…`; the script is now `98b0b07b…` (only
    E/F were re-run, `r1-EF`).
  - `ab-arms55.selftest.out.txt` covers `--run`, `checkout -B`, `revert`, `checkout -f`, `branch -D` and records after
    the restore. It cites runner `b8db42d8…`; the runner is now `b5e1e7b1…`, and only the dry-run against-proof cites it.
  - `fix-r1.done.md` defers both to "the orchestrator … before the landing (§8 step 6)", but §8 steps 2–15 name neither
    (0 hits).
  - The standing rule is that a dry-run / destructive path is first exercised in a throwaway, on the bytes that will run.
- **Fix.**
  - Add as §8 step 6's first sub-step:
    `bash tools/titan/results/wave55-plan/land-units.selftest.sh <scratch>` (A–F) and
    `bash tools/titan/results/wave55-gate/ab-arms55.selftest.sh <scratch>`, on the CURRENT sha256s (after M1's change).
  - Commit both records before the landing.
  - The stage-1 L4 arm does not run on a runner whose `--run` self-test cites other bytes.

**S7 — GO-SMALL cannot be taken once U2-android has landed, and it does not narrow the L4 arm (E5).**
- **Evidence.**
  - `plan-build.py --go-small` keeps `drop-L4-U2` units `['U2-ios','U2-android']` with
    `drops [… 'U2-android']`, a commit that never lands.
  - With `goSmall.held` set plus a `reverted.json` row for U2-android (the shape `holds.json`'s "with the run" invites
    after a probe), the generator crashes: `KeyError: 'U2-android'`.
  - `land-units.sh` does not read `holds.json`. Its plan-agreement check does refuse a landing without
    `--hold L4:U2-android`, which fails closed.
- **Fix.**
  - Rule 8, D1 and the `holds.json` why say: "GO-SMALL is decided BEFORE the landing only; after the landing the same
    outcome is a rule-4 revert row of U2-android, never `holds.json`".
  - `plan-build.py` asserts `not (goSmall.held and a U2-android reverted row)` with that message.
  - It narrows `drop-L4-U2` to U2-ios when held.

**S8 — §7 step 6's regeneration chain is shorter than §8 step 2's, and `final55-analysis.sh` drifts silently (E7).**
- **Evidence.**
  - After the L2-pair revert regenerates, `final55-analysis.gen.py --check --exp <regenerated>` prints DRIFT and exits 1:
    the header sha and the prediction terms both change.
  - §7 step 6 names `plan-build.py` and `plan-prose.py` only (0 hits for final55).
  - `final55-analysis.sh` never compares its embedded sha with the live `expectations.json` (0 hits), although PLAN §8
    step 2 requires `final55-analysis.gen.py --check` to exit 0 on the committed tree.
  - `plan-build.py:1443` itself says `readers-calibration.sh` is re-run on every new sha.
- **Fix.**
  - §7 step 6, and stage 2, add: "`final55-analysis.gen.py` then `--check`; `readers-calibration.sh` in a throwaway; the
    three records committed with the decision".
  - `final55-analysis.gen.py` emits a first command that does
    `[[ "$(shasum -a 256 tools/titan/results/wave55-plan/expectations.json | cut -c1-16)" == "<embedded>" ]] || { echo "STALE: regenerate final55-analysis.sh"; exit 2; }`.

**S9 — The apk-asset finding applies to the iOS .app too, and [W1] carries it into wave 56 (E13).**
- **Evidence.**
  - `test-all.sh:459-460` copies the fixture IR into `apps/ios-harness/StyleConverterTest/Resources/tmpOutput.json` AND
    the Android assets. The copy is unconditional; SKIP_IOS does not guard it (0 hits).
  - `project.yml:49` bundles `StyleConverterTest/Resources`.
  - Both files carry sha1 `05118ad1…` today (all-then-color, `wave55-open`'s net).
  - PLAN §0 states the asset caveat for Android only (ios 0 hits), and treats the iOS digest as a pure Swift-source
    witness.
  - [W1] (`SKIP_IOS=1 ./test-all.sh fixtures/properties/effects/filter-functions.json`, D11) rewrites BOTH embedded
    files after the gate. Wave 56's opening witnesses would then mismatch with unchanged sources.
- **Fix.**
  - §0 and 12.2: "both bundles embed the last test-all IR; a digest or sha1 is comparable across launches only with the
    same IR sha1".
  - `installed-build-hash.sh` (the wave55-gate copy) prints `asset sha1 ios=<…> android=<…>` in every block.
  - §8 step 16: record both asset sha1s after [W1] in `../wave55-gate/_note.md`, and carry them into BACKLOG's wave-56
    obligation 0.

**S10 — The "one-shot" hash wait is not one-shot per launch (E9).**
- **Evidence.**
  - `gate-driver.sh` appends to `driver.log` (`:99`) and provisions at the start of EVERY launch, `--resume` included
    (`:239`).
  - On a driver.log that already holds `attempt 1 starting`, the plan's `until grep` returned after **0 s**, so the read
    lands before the new launch's provisioning. This happens:
    - on a `--resume` (risk 6 expects one for css-view-transitions under swap);
    - on the `--skip-corpus` net launch of the same run id.
  - PLAN names no hash read for a resumed launch (0 hits).
  - The wait also has no upper bound; `ab-arms55.sh` has `AB_WAIT_MAX_SEC=1800`.
- **Fix.**
  - `HASH_READ` captures `n=$(grep -c 'attempt [0-9]* starting' "$LOG" 2>/dev/null || echo 0)` BEFORE the launch.
  - It then waits `until (( $(grep -c 'attempt [0-9]* starting' "$LOG") > n ))`, for at most 1800 s.
  - A resumed launch reads under its own label: `<run> (… launch k)`.

**S11 — §6 "Two caveats about the strings above" are false since the round-1 regeneration (E14).**
- **Evidence.**
  - `corpusSnapshotSource` / `closingGateFiles` name `make-snapshot55.mjs`, not `make-corpus.mjs` (D20 applied by D28).
  - `control-check.mjs:125` now COUNTS "absent before" as a leak.
  - The second caveat (PLAN :1986-1988) tells a closing-gate reader that such a line is "never … a counted leak", which
    invites dismissing a real one.
- **Fix.** Delete both bullets (:1981-1988). D20, D28 and `readerChanges` already carry the facts.

**S12 — The resume rule covers a killed BUILDER only. A killed fix lane, and a mutation killed before its record, are
uncovered.**
- **Evidence.**
  - `land-units.sh` reads the LAST `STATUS:` line of the note.
  - A fix lane appends a "## Fix pass" under the builder's `STATUS: COMPLETE`. If it is killed after re-cutting a patch
    and rewriting its `patch:` line, but before its focused suites and `revertStatesCompiled`, the note still says
    COMPLETE and the sha matches, so the landing passes.
  - The resume rule restores mutations "sha256 against the note's record". §0 says that record is written red → restore
    → green AFTER the mutation, so a lane killed mid-mutation leaves nothing to restore against. The mandatory re-cut
    would then capture the mutated byte.
- **Fix.**
  - Fix lanes write `STATUS: PARTIAL — fix-r<n> in progress` before touching the lane dir, and `STATUS: COMPLETE` last,
    after their `patch:` lines and focused suites.
  - Every pin mutation writes `MUTATING <file> sha256 <pre>` to the note BEFORE the edit, and `RESTORED <file> sha256 <post>`
    after it.
  - A resumed lane:
    - restores every `MUTATING` line that has no `RESTORED` pair, before it re-cuts;
    - re-runs its focused suites and every revert state on the re-cut patches.

---

## 5. NITS

- **N1.** No rule says how a rule-6 `-S1` revert is recorded. A `reverted.json` row naming `U2-S1` crashes the generator
  (`KeyError: 'U2-S1'`, E24). Say: "one row per UNIT; its `-S1` shas go in that row's `commit`".
- **N2.** The regenerated re-read's `*.regen.*` names are hand-made (0 hits in `plan-build.py` / `expectations.json`,
  E24), so two stage-1 decisions overwrite one proof-by-failure. Generate them as `PROBE_READOUT(run, …, suffix=f'regen{len(REVERTED)}')`.
- **N3.** §7 step 5 writes `ab-arms55.sh <arms-file> --dry-run`, which leaves `--against` at its default `wave55-final`.
  The dry run therefore never runs the S5 tree check that `--run --against wave55-pre` applies (E25: the same synthetic
  block is noted by the first and refused by the second). Write `--dry-run --against wave55-pre`.
- **N4.** Stale arm names survive the D8 rename (E15):
  - L4 `stage1Decision` "the probe-time A/B arm drop-U2" (`plan-build.py:636-639`, rendered in §7);
  - `land-units.sh` ORDER row 03 note "L4 drop-U2, L6 drop-U2" (it goes into the U3 commit body).
- **N5.** The S10 provisioning link makes `node_modules/@style-converter/web` an ABSOLUTE link to the shared
  `runtimes/web` (E17). A `web-harness` suite run in a lane worktree therefore tests the shared runtime, not the lane's
  edits. This affects only L7 U4 and U6-web. Run their web tests from `runtimes/web` (relative imports), or give the
  worktree its own `node_modules/@style-converter/web -> ../../runtimes/web`.
- **N6.** Ten files under `plan-skeptic-round-1.instrument-teeth.d/` carry scratch paths, besides the two gate files
  fix-r1 queued (E18). Redact them before the plan commit (§8 step 2), not at ship.
- **N7.** §0's `revertStatesCompiled` list ("L4 minus U2-<p>; L5 minus D, U2i, U2o; L3 minus U2; L6 minus U2; the L2 pair;
  L1 minus NB-ios-b") omits L5 minus U1-ios and L5 minus U3-ios. Both are single-unit chains (E20). Say "every chain of
  `lanes.*.revertUnits[].revertOrder`".
- **N8.** Two regenerable counts are still typed outside the gen blocks (E21), and the STRAY list misses both:
  - "asserts all 26 of its own" (§4 :1742; manifest `orderConstraints`, which grows with S2's row);
  - "probeRun's 26 sections" (§8 :2358).
- **N9.** Only `wave55-pre-r1` and `-r2` are pre-registered, and no `wave55-probe-r<n>` exists. Rule 6 re-probes
  "between steps", while NB-ios-b, the LRM re-cut and any stepwise revert can already use more than two. Pre-register an
  id template.
- **N10.** §0 says every hash block "is committed with named paths before the next device run". §8 step 12 commits the
  corpus block only at 12.5, after the net launch. Align the two, so 12.2's block is committed before 12.4.
- **N11.** `ANDROID_SERIAL` (round-1 N3) appears only in §7 step 0. Add it to §8 step 12 and to the arm windows (wave skill
  Phase 5: "the run refuses with exit 2" otherwise).

---

## 6. Checks run (by execution; output in `plan-skeptic-round-2.process-holes.repro.out.txt`)

| tag | check |
|---|---|
| H | input sha256s |
| E1 | `plan-prose --check` |
| E2 | generator reproduction |
| E3 | C1–C15 encodings plus the §9 rows |
| E4 | L6 U2 revert (M4 / S8) |
| E5 | `--go-small`, and `--go-small` with a U2-android revert row |
| E6 | L1 and L5 contingency rows |
| E7 | `final55-analysis.gen.py --check` after a regeneration |
| E8 | untracked sources in a throwaway, run against `beforeEveryDeviceRun`, the hash-block test, `tracked_dirty` and the proposed fix |
| E9 | `gate-driver` log append and provision-per-launch; the `until` wait on a pre-seeded driver.log |
| E10 | `git worktree list` against the `wave55-` grep; the placeholder |
| E11 | `land-units.sh --check` on a synthetic L1 lane with and without `fix-S1-NB-ios.patch` (throwaway) |
| E12 | self-test record sha256s against the current scripts |
| E13 | `test-all.sh` sync lines, `project.yml`, the embedded IR sha1s |
| E14 | §6 caveats against the JSON and `control-check.mjs` |
| E15 | stale arm names |
| E16 | `df`, DerivedData size and WorkspacePath, PLAN cleanup rules |
| E17 | the `node_modules` workspace link |
| E18 | scratch paths in the plan / gate records |
| E19 | paper landing simulation from the anchors at HEAD |
| E20 | revert chains against the §0 list |
| E21 | stray counts |
| E22 | the runner's `revert_check` against cumulative reverse-apply (plain patch tool) |
| E23 | stage-1 unit coverage |
| E24 | an `-S1` revert row; regen record names |
| E25 | the arm dry run with and without `--against` (throwaway) |
| Z | shared tree unchanged; no throwaway left registered |

Also run by hand (read-only):
- the anchor lines and the `wc -l` counts at HEAD;
- the grep for hidden source pins;
- the gitignore status of the three test-all IR targets;
- `gate-driver.sh` and `own-processes.sh` reads;
- `pgrep` for gate, device and build processes (none).

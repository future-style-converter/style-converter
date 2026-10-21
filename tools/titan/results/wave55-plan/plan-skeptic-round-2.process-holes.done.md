# plan-skeptic round 2 — process holes: done

Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`. Nothing was committed, checked out,
stashed or reset.

**Edits.** Only these, all under `tools/titan/results/wave55-plan/`:
- `plan-skeptic-round-2.process-holes.md` — the report: every check with its output, and the defects ranked with their
  exact fixes;
- `plan-skeptic-round-2.process-holes.repro.sh` — the executed checks H, E1–E25 and Z, read-only on the shared tree;
- `plan-skeptic-round-2.process-holes.repro.out.txt` — its redacted output (script sha256 `9cf37f5c…`, repro exit 0);
- this file.

**Not touched.** No runtime, harness, converter, `tools/titan/*.{sh,mjs}` or `tools/visual/` file was edited. No gradle,
xcodebuild, vite, puppeteer, emulator, simulator, adb, test-all or smoke run was started.

**State-changing git calls.** Only `git worktree add --detach <scratch>/wt-{untracked,fixs1,armdry} HEAD` and
`git worktree remove --force` of the same three. Section Z of the output shows HEAD and status unchanged outside the two
plan dirs, and no scratch worktree left registered. `tools/titan/runs/wave55-open/` was never read.

**Verdict: DEFECT-CONFIRMED — 1 must-fix, 12 should-fix, 11 nits.**
- **M1.** `ab-arms55.sh`'s non-chain revert check tests each commit's reverse alone on the current tree. So it refuses
  the drops the plan generates:
  - `drop-NB-ios` once NB-ios-b lands (certain);
  - `drop-L4-U2` once an `-S1` touches U2 (stage 1).

  E22 shows this by plain-patch emulation.
- **Before stage 1:**
  - S2: the L5 LRM re-cut has no generator or landing path;
  - S3: S1's `fix-S1-<U>.patch` is refused by the M3 guard, which blocks `--contingency`;
  - S6: the land-units A–D and ab-arms `--run` self-tests cite older bytes;
  - S7: GO-SMALL after landing crashes the generator.
- **Before the first device run:**
  - S1: untracked sources pass `beforeEveryDeviceRun`;
  - S4: the `wave55-` daemon grep matches no worktree, and the TREES: part is a placeholder;
  - S5: no disk floor or cleanup, with 38 GiB free against 4.0 GB of DerivedData per Swift worktree;
  - S12: fix lanes and un-recorded mutations are outside the resume rule.
- **Before the closing gate:**
  - S8: `final55-analysis` drifts on every regeneration;
  - S9: the iOS .app embeds the test-all IR too;
  - S10: the hash wait is not one-shot per launch;
  - S11: §6's two caveats are false.
- **Held.** The 15 audit conflicts are each encoded and resolved in §9. The seam landing order on paper has no failing
  hunk pair: the closest cross-lane `.kt` hunks are 785 lines apart, and `SeamReachabilityTest`'s windows are disjoint.
  Every round-1 must-fix held, but M3, S5, S8, S9, S10 and S11 have new holes beside them (§1 of the report).

STATUS: COMPLETE — every check the brief asked for was executed and recorded. Three items are by emulation or inspection
only, because each would need a commit, a build or a device:
- the runner's `revert_check` against real commits;
- a compiled DerivedData estimate per lane worktree (measured on the shared tree);
- the U1-ios `let painted` placement, which no patch delivers yet.

# Wave 55 — plan-skeptic round 1 (process holes): done note

**Delivered** (all in `tools/titan/results/wave55-plan/`):

| file | what it is |
|---|---|
| `plan-skeptic-round-1.process-holes.md` | the report: the checks that hold, 4 must-fix (M1–M4), 14 should-fix (S1–S14), 7 nits (N1–N7), each with its exact fix |
| `plan-skeptic-round-1.process-holes.repro.sh` | re-runs every executed check (E1–E11, Z) against a scratch dir. Fix lanes can turn each one green, and S1 can re-check them |
| `plan-skeptic-round-1.process-holes.repro.out.txt` | its output on 640276a0 + the untracked plan files (expectations.json sha256 `cd3936d0…`) |

**Headline.** DEFECT-CONFIRMED. The landing order holds for every cross-lane `.kt` / `.swift` hunk pair. Four process
holes are must-fix:
- **M1.** `SeamReachabilityTest.kt`'s five module lines share one anchor. Cut on HEAD, L3 and L5 fail after L4.
  Re-cut on the tree they land on, L4 / L3 reverts and the `drop-L4-U2` arm fail. The fix (pinned anchors) is proven to
  apply and revert in any order.
- **M2.** No installed-build hash is read at `wave55-pre`, its re-probes or `wave55-probe`, yet §8 12.2 relies on "the
  probe's digest".
- **M3.** `land-units.sh` lands a limit-killed lane: no STATUS gives 0 errors and 0 warnings, PARTIAL is a warning only,
  and nothing ties a patch to its note.
- **M4.** A probe revert of L6 U2 leaves its 214 may-move captures exempt from the leak control.

**Discipline.**
- Read-only on every runtime, harness and converter source, on `tools/titan/*.sh` / `*.mjs` and on `tools/visual/`.
- Writes only in `wave55-plan/`, and only the three files above plus this note. Scratch work went in the session
  scratchpad (`ph-r1/`).
- One throwaway worktree (`git worktree add --detach … HEAD`), removed. No other git state change: no commit, checkout,
  stash or reset.
- Nothing built, captured or provisioned. `pgrep` showed no gate, probe or arm process.
- The shared tree's HEAD and status are unchanged (repro Z), the stash is empty, and `git worktree list` holds no scratch
  tree.

STATUS: COMPLETE — every check was executed and recorded (`plan-skeptic-round-1.process-holes.repro.out.txt`). Defects are ranked with exact fixes; none were applied, because fixing the generator, the landing script and the plan is the fix lanes' and orchestrator's job.

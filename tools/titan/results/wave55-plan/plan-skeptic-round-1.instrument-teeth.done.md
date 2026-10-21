# plan-skeptic round 1 — instrument-teeth: done

- **Report:** `plan-skeptic-round-1.instrument-teeth.md`.
- **Scripts, outputs and look PNGs:** `plan-skeptic-round-1.instrument-teeth.d/`. Copy that directory to a scratch dir
  before re-running it: its `lib.py` refuses to run inside the repository.

**Verdict: DEFECT-CONFIRMED.**

- 41 of the 43 gating geometry keys PASS a fake built from their own fixed picture; 39 of them outside their declared
  tolerance.
- With one wrong picture under every gating key of a lane, `geometry-gate.py` exits 0 in all six lanes.
- The grid `[X]` tally entries #19–#22 claim picture-correctness that the lane's own U2 replay contradicts: the abspos
  children sit 3–22 px off the ref, with their containers in place.
- The refusal paths that exist hold: geometry-gate 2 / 2 / 2 / 3 / 3, probe-readout 2 / 2 / 2 / 3, adjudicate
  2 / 2 / 2 / 2, control-check 2 / 2.
- Vacuous passes on typos and missing data:
  - geometry-gate `--sections` / `--lanes`;
  - probe-readout `--lanes`;
  - control-check `--sections`, a missing section / platform dir / capture / wire document.
- `land-units.sh --check` is read-only. Proven in a throwaway worktree with snapshots, a find-newer sweep (0 files) and
  three byte-identical canaries; the real path refused on the canary tree with "nothing was touched".
- `ab-arms55.sh` writes its records only after the last verified restore (code order L239–246; dry-run refusals executed).

**Defects:** 9 must-fix (M1–M9), 4 should-fix (S1–S4), 3 nits (N1–N3), each with its exact fix. Every proposed fix was
executed as a measurement: the fixed picture OK, every accepted fake WRONG.

**Scope kept:**
- Edits only under `tools/titan/results/wave55-plan/`.
- No runtime, harness, converter, `tools/titan/*.sh` / `*.mjs` or `tools/visual/` file touched.
- No device, Gradle, xcodebuild, vite or test-all.
- One throwaway `git worktree add --detach`, then `git worktree remove`.
- HEAD `640276a0` unchanged.
- No `wave55-open` capture read.

STATUS: COMPLETE — every gating key faked and read through the real gate; every reader's refusal path executed;
`land-units --check` proven read-only with canaries; the `ab-arms55` records order read and its refusals executed. Not run:
the `ab-arms55` `git init` self-test, which is outside this stage's one permitted git command; its recorded output is
cited instead.

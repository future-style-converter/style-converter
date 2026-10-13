# plan-skeptic round 2 — instrument-teeth: done

- **Report:** `plan-skeptic-round-2.instrument-teeth.md`.
- **Scripts and outputs:** `plan-skeptic-round-2.instrument-teeth.d/`. Scratch paths are redacted; copy the dir to a
  scratch dir before re-running.
- **Tree:** HEAD `640276a0`, unchanged. Shared-tree status / index / HEAD snapshot taken around the test window:
  identical.
- **New files:** only the two above, plus this file, all under `tools/titan/results/wave55-plan/`.
- **Git:** the only state commands were `git worktree add --detach <scratch>/wt-lu HEAD` and its `git worktree remove`;
  `git worktree list` holds no scratch tree.
- **Captures:** no `wave55-open` capture was read. No device, gradle, xcodebuild, vite, puppeteer, test-all or smoke.

**Verdict: DEFECT-CONFIRMED.**
- The round-1 fixes hold against the round-1 fakes: the standing set re-runs at 84 cases, 0 violations, 43 / 43.
- New fakes of the same four classes are accepted by 37 / 43 gating keys. `geometry-gate.py` exits 0 with one wrong
  picture per gating key on L2, L3, L4 and L5.
- **must-fix:** M1–M5 (the L3 / L2 / L5 / L4 / L6 rule additions, each validated as a measurement) and M6 (the standing
  set must carry the round-2 fakes and every tally key; the PLAN §0 claim is false at `a1fdca71`).
- **should-fix:** S1–S5. In short: the L4 ±12 tolerance was never measured on a device face; `--base` equal to the run
  disables the base-relative reads; control-check HOLDS when a carrier vanishes; the `ab-arms55.sh --against` check
  reads the last block and ignores MISMATCH; four value-less flags exit 1.
- **nit:** N1–N4, including the round-1 `cur` guards, which are still open.
- **Confirmed sound:** `land-units.sh --check` is read-only (7 runs, canaries byte-identical, real path refuses).
  `ab-arms55.sh` writes its records after the last verified restore.

STATUS: COMPLETE — every requested check executed: fakes for every gating key, all reader refusal paths, `--sections`,
the land-units read-only proof and the ab-arms record order. One fake (bidi `rtl-runs+2y`) is withdrawn as inconclusive,
with the reason recorded. The ab-arms `git init` self-test was not re-run: it is outside this stage's one permitted git
command; its recorded output is cited instead.

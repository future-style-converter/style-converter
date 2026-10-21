# plan-skeptic round 5 — verify: done

- **Report:** `plan-skeptic-round-5.verify.md`.
- **Evidence:** `plan-skeptic-round-5.verify.d/`.
  - `fakes5.py --scratch <dir> --groups Lx --out … --json …` re-runs the sweep.
  - `adjudicate5.py fakes5.L*.json` re-derives the per-key table.
  - `residual.py` re-measures the fix basis.
  - `checks.out.txt` holds §1.
- **Tree:** HEAD `611c9f93`, unchanged. The index sha256, `git stash list` and the status outside `tools/titan/results/wave55-*`
  are the same before and after; no throwaway is left registered.
- **What this pass wrote:** only these three new entries, each installed atomically.
- **Changed by someone else during the pass:** `PLAN.md` (an appended "[W-L7a]" paragraph), `../wave55-gate/_note.md`, and
  the new `../wave55-gate/wl7a/`. This pass did not write them. `plan-prose --check` still HOLDS on the new PLAN.md.
- **Not run:** no device, gradle, xcodebuild, vite, puppeteer, test-all or smoke.

**fix-r4's claims re-execute:**
- `geometry-gate --self-test` HOLDS;
- `plan-prose --check` HOLDS;
- the watchlist reads unmatched 0;
- `plan-build --out` is byte-identical;
- `probes-selfcheck.txt` is byte-identical;
- the `fix-r4.teeth-before-after` body is byte-identical (round-3 bytes 145 / 0 / 0; current bytes 0 violations);
- `teeth-r4-equiv` is identical;
- `fix-r3` and `fix-r4` verdicts-preserved are identical;
- every fixed picture prints OK, except the known by-design cases (`[X] safe-002`, and `[F]` under U1 alone).

**The round-5 independent strictly-additive sweep FAILS.** It ran 901 fake runs (2388 key reads) of specks, thin lines,
faint 50 % copies and duplicated glyphs inside each key's read zone. That gave 704 acceptances adding > 2 px, on 33 of 43
gating keys and 17 tally entries:

| | where | keys | what passes |
|---|---|---|---|
| M1 | L2 | 8 | only strict green and the fail ink are read |
| M2 | L3 bidi, stack, dyn-001, dyn-002 | 4 gating, + the vrl-rtl-001 tally entry | only their colour masks are read |
| M3 | L4 | 12 | ink lighter than the dark threshold, outside the band rows |
| M4 | L1 | 3 | faint ink outside the line crops |
| M5 | L6 `[X]` | 5 | the fill is colour-exact and dark is bounded below only |
| M6 | L6 `[PO]` | | blue is bounded below only; pink is counted inside one box |

- **Hold:** L5 (all six keys, both tally cells) and the L3 dir rows 6 / 7 / 9.
- **The 2-px floor reproduces as named:** 85 acceptances.
- **Fix basis (`residual.py`):** a "rest of the capture = the base's" read is satisfied at 0 px by the fixed pictures of every
  M1 / M2 / M5 / M6 key except clip-3 U2 (573 px) and bidi (93 px), which need a wider allowed region.

STATUS: PARTIAL — the fix-r4 claims all hold on re-execution, but the round-5 sweep's 704 must-fix acceptances (M1–M6) stand
open.

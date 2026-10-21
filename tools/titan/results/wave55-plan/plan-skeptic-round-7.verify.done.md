# plan-skeptic round 7 — verify: done

- **Report:** `plan-skeptic-round-7.verify.md`.
- **Evidence:** `plan-skeptic-round-7.verify.d/`. Run its scripts from a scratch copy of the directory.
  - `fakes7.py --scratch <dir> --out <jsonl>` re-runs the sweep through the real gate (`--base wave55-open`).
  - `adj7.py sweep7.records.tsv` re-derives `adj7.out.txt` byte-for-byte.
  - `classify7.py` groups the acceptances; `basis7*.py` / `propose7.py` measure the fix bases.
  - `checks7.out.txt` holds §1.
- **Tree:** HEAD `4e789976` → `7566fb03`. That is the orchestrator's 09:28 pre-landing commit, not this pass. The index, `git stash
  list` and the status outside `tools/titan/results/wave55-*` are as recorded in §5. The throwaway `wave55-plan-r6-verify7` was
  removed.
- **What this pass wrote:** only these three new entries, each installed atomically.
- **Not run:** no device, gradle, xcodebuild, vite, puppeteer, test-all or smoke.

**fix-r6's claims re-execute:**
- `geometry-gate --self-test` HOLDS;
- `plan-prose --check` HOLDS;
- the watchlist reads unmatched 0;
- `plan-build --out` is byte-identical;
- `probes-selfcheck.txt` re-runs identical except its HEAD line (611 standing cases, 0 violations; §T5 0 must-fix);
- `teeth-r5-equiv` is byte-identical;
- both verdicts-preserved records reproduce;
- every fixed picture prints OK with residual 0 / 0 / 0, except the by-design `[X] safe-002` and `[F]` under U1 alone;
- the residual reads 0 on 174 of 181 replay / arm pictures, and the 7 non-zero are exactly the named wrong pictures;
- every O-family fake in a non-class colour (5140) is FAILed by the residual read on every key.

**The round-7 independent sweep FAILS.** It ran 471 families of every round-1-5 class over the 41 fixed pictures: 710 real-gate
fake runs, 9933 cell fakes, 12769 owning gating-key reads. It found 1973 acceptances adding > 2 px, on 35 of 43 gating keys and
16 of 21 testable tally cells, by seven mechanisms:
- **MF1:** faint in-zone ink (min ≥ 200) on the neutral / ramp zones — L1 ×3, L4 ×12, L5 ×6, dir rows ×3.
- **MF2:** bidi blue outside the RTL bands.
- **MF3:** palette paint grown from a recorded window — clip, clip-2, clip-4, bidi.
- **MF4:** the L4 face-present heading moved ±2-3 px in x — 10 keys.
- **MF5:** `[X]` child fill over the parent band — 5 keys.
- **MF6:** `[PO]` overlap revert plus red elsewhere.
- **MF7:** stack, an "other" square relocated.

Each has a measured fix basis, except MF7, whose other squares have no tolerance-0 reference. The floor (≤ 2 px, 32) reproduces
as named.

STATUS: PARTIAL — fix-r6's claims re-execute and the round-5 class is closed. The round-7 sweep still finds 1973 acceptances > 2 px on 35 / 43 gating keys and 16 tally cells (MF1-MF7), each a must-fix; MF1-MF6 carry a measured fix basis.

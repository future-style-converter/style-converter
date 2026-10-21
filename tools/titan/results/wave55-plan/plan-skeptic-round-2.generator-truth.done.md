# plan-skeptic round 2 — generator-truth: done

**Report.** `plan-skeptic-round-2.generator-truth.md`. My scripts, read-outs and the 13 probe outputs are in
`plan-skeptic-round-2.generator-truth.d/`.

**Inputs** (unchanged throughout):

| file | sha256 |
|---|---|
| `expectations.json` | `a1fdca71…` |
| `watchlist.txt` | `4b1a9cd5…` |
| `PLAN.md` | `ddaa6955…` |
| `cells-wave54-final.json` | `c9c148e7…` |

The tree is at HEAD `640276a0`. `wave55-open` was never read.

## Executed

**Reproduction.**
- The generator reproduces byte-for-byte on the shared tree (`--out`, nothing written in the tree).
- It also reproduces in a throwaway worktree at HEAD, with `plan-prose.py --check` HOLDS there and on the shared tree.

**Snapshot and froms.**
- The snapshot equals `cells.mjs` over all 4096 cells.
- All 76 "from" values equal `cells.mjs` on wave54-final.

**Carrier censuses.** Each one is re-derived with my own scripts over the per-test IR, the captures and the frozen refs:
- **L1:** 60 / 19 (40 / 20) reproduced, plus pixel pitch checks on the MOVES and SAME claims;
- **L2:** U1 33 boxes / 14 docs with the same box ids; U2 122 axes, 2 carriers;
- **L3:** 53 / 65 / 17, STACK 3, U2 11 / 7 (7 / 4);
- **L4:** 4 + 12; exactly 4 heading hosts;
- **L5:** 36 / 4; FontVariantNumeric 6; 2 RTL markers;
- **L6 may-move:** my `BorderSideExtractor` mirror gives 279 raw, and the set-equal 240 after the rule's subtractions;
  `sharedStems` is exact;
- **L6 U1:** 10 self-paint, 2 carriers;
- **L6 U2:** 25 / 25 carriers corroborated Android-only against web by two independent pixel metrics;
- **L7:** 17 bidi-baked.

**Structure.**
- The partition is 76 + 665 + 3352 + 3 = 4096, with no overlap and no hole.
- No named cell is a carrier.
- The floors are 44 × 0.95.
- Stage 1 = the union of the lanes' sections, and every native unit is device-probed there.

**Keys.**
- All 13 probes ran on wave54-final.
- 230 keys, each matching one line; 0 wrong-class outcomes; every expect string is printable.
- `geometry-gate.py --self-test` HOLDS.
- The U1-state strings are exact on wave54-probe.

**Watchlist.** Official check `unmatched 0`, and my matcher agrees.

**A/B arms.** Coverage and order checked, plus two `plan-build.py` dry runs: a contingency, and an L6 U2 revert.

**Tally and expected movement.** Checked against the rows.

**adjudicate R6.** A synthetic record shows the Android inset retirement (S2).

**My checkers can fail.** Mutations turn them red: `gtchecks` 9 / 9, `keys` 3 / 3, plus census rule mutations.

## Defects

**Must-fix: none.**

**Should-fix:**
- **S1:** three `stage1Decision` strings attribute section-level moves to their own lane on the union tree, so L1's KEEP
  depends on Android css-text pixels L3 U1 can move. This contradicts `revertRule` 5 and `probe-readout`'s bisect.
- **S2:** `degenerateRetirementCheck.units.android` lets R6 print "RETIRED … gains" for inset-005 / -006 android, against
  `stayDegenerateEvenIfPass`.

**Nits N1–N6:**
- **N1:** the complement-rule sentence;
- **N2:** the L4 -006 `<p>`, round-1 N3 regressed;
- **N3:** `all: initial` strips all-prop-001's line-height, so the L1 radius is 58 / 18;
- **N4:** non-whitespace "own text";
- **N5:** a `KeyError` on a reverted row without `rule`;
- **N6:** disclosure-styles' 12 vertical runs.

**Round-1 re-checks.** M1, M2, S1–S4 and N2 are fixed; N1 is partly fixed; N3 regressed (round-1 N3 was a false
positive); N4 is accepted.

## Git and tree

- The one state-changing git command was `git worktree add --detach <scratchpad>/gt2-wt HEAD`, followed by its
  `git worktree remove`; it is gone from `git worktree list`.
- No commit, checkout, stash or reset.
- No edit outside `plan-skeptic-round-2.generator-truth*`.
- `git status` differs from the start only by the parallel round-2 skeptics' own files.

STATUS: COMPLETE — every check the brief listed was executed and recorded with its output; no must-fix; the two should-fix
defects and six nits carry an exact fix.

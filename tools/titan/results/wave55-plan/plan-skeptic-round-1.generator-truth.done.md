# Wave 55 — plan-skeptic round 1, generator-truth: done note

**Scope.** Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`. I made no commit and no git
state change. I wrote only these, all under `tools/titan/results/wave55-plan/`:
- `plan-skeptic-round-1.generator-truth.md`: the report, with every check and its output, and the defects ranked with
  their fixes;
- `plan-skeptic-round-1.generator-truth.d/`: my own scripts, `out/`, and the 13 probe read-outs on wave54-final;
- this note.

I did not read `wave55-open`. No device, gate, build or worktree was used.

**Result.** The generator is byte-reproducible (`plan-build.py --out` gives identical outputs). These all hold:
- every "from" (76 / 76);
- the partition (4096 = 76 + 665 + 3352 + 3; no hole, no overlap);
- every geometry key (230; 0 wrong-class on wave54-final; 13 probes exit 0, nothing written);
- the watchlist (746 lines, each exactly one cell);
- floors (44 gating rows at 0.95);
- the tally, stage-1 coverage, and the manifest against revertUnits.

My own censuses reproduce the published carrier sets for L2, L3 U2, L4, L5, L6 U1 / U2 and L7.

**Two must-fix defects in the transcribed censuses:**
- **M1.** L1 `hyphens-vertical-001 ios` is not "1 line": the census asserts its line count with a filename regex. Its box 1
  is a space-less U+00AD run in VERTICAL_RL. The runtime declines the pre-break there, so the `singleLineText` proxy
  calls it exact with 1 line, while TextKit draws 3. NB-ios would pin it to 19 px, which breaks the stage-1 byte-identity
  KEEP clause and R5.
- **M2.** L6 `mayMove` misses 24 documents whose Compose border is the `medium` 3 px default (`applyMediumDefault`).
  The brief's explicit-px rule reproduces the published 252 exactly; the medium-aware rule gives 276. My crossing scan
  finds no new carrier.

**Should-fix:**
- **S1.** drop-NB-ios says "exactly 4 differ" but its own dotted +0.004 / +0.005 make 6.
- **S2.** The L6 arm asserts clip-rect, but filter-effects is not in its sections.
- **S3.** The D8 duplicate arm name and the D9 missing `against`.
- **S4.** The D20 stale `make-corpus.mjs` strings.

There are also four nits. All fixes fold into one `plan-build.py` regeneration, followed by `plan-prose.py --fill` /
`--check`, after the L1 brief adds its vertical guard and pin P8.

**My instruments can fail.**
- `gt-checks.py`: 6 / 6 mutations red.
- `keys-check.py`: 2 / 2 mutations red.
- The may-move census has a control arm that reproduces 252.
- The crossing counter gives 0 on a clean fake and 80 on a banded fake.
- The installed files' sha256 values are unchanged.

**Two caveats.**
- L3 U1's third carrier (multicol-004) is reproduced only through the brief's multicol host channel, which I did not
  model.
- M1's render path (PlaceholderLabel for that leaf) is read from source, not executed. Pin P8 in the fix settles it.

STATUS: COMPLETE — every requested check executed and recorded. 2 must-fix, 4 should-fix and 4 nits are in the report.

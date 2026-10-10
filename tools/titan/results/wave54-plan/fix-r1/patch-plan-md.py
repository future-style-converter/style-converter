# fix r1: restate PLAN.md where the round-1 plan-skeptic defects changed a claim (asserted exact replacements).
P = 'PLAN.md'
s = open(P).read()
def rep(tag, old, new):
    global s
    n = s.count(old)
    assert n == 1, f'{tag}: expected 1 occurrence, found {n}'
    s = s.replace(old, new)

rep('written', """- `plan-build.out.txt` and `watchlist-check.wave53-final.out.txt`: the executed outputs of items 2 and 3 below.""",
"""- `plan-build.out.txt` and `watchlist-check.wave53-final.out.txt`: the executed outputs of items 2 and 3 below.
- `block-ellipsis-br.geometry.py` (fix round 1, S3) and `fix-r1/`: the round-1 fix pass's scripts, fakes and executed
  outputs (§11).""")
rep('probes', """`soft-hyphen.geometry.py`). `land-units.sh`""",
"""`soft-hyphen.geometry.py`), plus `block-ellipsis-br.geometry.py` (fix round 1). `land-units.sh`""")
rep('item2', """2. `python3 plan-build.py` → 773 watch lines,""", """2. `python3 plan-build.py` → 775 watch lines (773 at planning; +2 at fix round 1, S6),""")
rep('item3', """   `watch-lines=773 matched-cells(distinct)=779 … unmatched 0`.
4.""", """   `watch-lines=775 matched-cells(distinct)=781 … unmatched 0` (the same on `wave54-open`).
4.""")
rep('item4', """4. `python3 geometry-gate.py wave53-final --base wave53-open` → 140 keys: PASS 82 · FAIL 58 (gating 33) · 0 probe
   self-check failures, exit 1. Every gating row fails on today's pictures and every control passes, so the rules can fail.""",
"""4. `python3 geometry-gate.py wave53-final --base wave53-open` → 185 keys: **gating 37, all FAIL · control 112, all PASS ·
   report 36, all FAIL** (MED / LOW targets) · 0 probe self-check failures, exit 1; with `--self-test` it HOLDS (exit 0).
   The rules can fail and the controls hold. (140 keys, 33 gating, at planning; fix round 1 added 45 keys and the
   control / report split, §11; the 140 planning keys keep their verdict and line byte-for-byte.)""")
rep('item6', """6. The `plan-build.py` probe-decision hook, self-tested on a scratch copy with `Mprime` and `TB-android` reverted:
   - it withdraws counter-suffix web/ios and 006 android, as predictions and as carriers;""",
"""6. The `plan-build.py` probe-decision hook, self-tested (`--out` / `--reverted`, fix round 1) with `Mprime` and `TB-android` reverted:
   - it withdraws counter-suffix web/ios and 006 android, as predictions and as carriers;
   - it withdraws their 7 gating geometry keys (`[M]` ×3, lists-bakes ×2, 006 ×2), which `geometry-gate.py` then prints
     `WITHDRAWN` and never gates on (fix round 1, M3);""")

rep('§0 rule', """  - **New:** every lane also runs `geometry-gate.py wave53-final --base wave53-open --lanes <id>` and confirms that its
    gating rows FAIL today and its controls PASS. A geometry rule that cannot fail is not a gate.""",
"""  - **New:** every lane also runs `geometry-gate.py wave53-final --base wave53-open --lanes <id> --self-test`, which exits 0
    only when its GATING keys FAIL today, its CONTROL keys PASS and its REPORT keys (MED / LOW targets, `report` in each
    probe) FAIL. A geometry rule that cannot fail is not a gate (fix round 1, S5: "every control passes" was false for the
    25 report keys).""")

rep('§1 tally', """**Passing cells made picture-correct with no change of verdict: 38 in full and 2 in part.**
- L1: `counter-suffix` web in full; the counter-suffix natives in part.
- L2: 006 android.
- L3: `block-ellipsis-002/-004/-005/-006` web and `-002` ios.
- L4: 8 OOF cells, 6 autopos and 3 nested clips.
- L6: 13 box-sizing captures and `semi-replaced-stretch-input`.""",
"""**Passing cells made picture-correct with no change of verdict: 37 in full and 2 in part** (fix round 1, S3; 38 before).
Each is claimed ONLY when its own geometry key prints `→ GEOMETRY OK` on the closing run (`expectations.json`
`pictureCorrectTally`, asserted by `plan-build.py`: every entry has a key in its lane's probes).
- L1: `counter-suffix` web in full; the counter-suffix natives in part.
- L2: 006 android.
- L3: `block-ellipsis-002/-004/-005/-006` web (the new `block-ellipsis-br.geometry.py`, gating U3). **`block-ellipsis-002`
  ios is OUT**: looked at, its capture paints Line 4 and no "…" (the iOS clamp is not applied) and U3 only removes the
  stray blank lines (replay B keeps 4 bands vs the ref's 3). It moves up and stays DEGENERATE (`stayDegenerateEvenIfPass`).
- L4: 8 OOF cells, 6 autopos and 3 nested clips.
- L6: 13 box-sizing captures (one key each now: 010, 011, 013, 014-019, 020, 021, 024, 025) and `semi-replaced-stretch-input`.""")
rep('§1 stayDeg', """Three passing cells stay DEGENERATE even when their lane is right (`expectations.json` `stayDegenerateEvenIfPass`):
`bidi-lines-002` android (the orange `!` on the left) and `counter-suffix` ios / android (rows 3–6 / 5–6).""",
"""Four passing cells stay DEGENERATE even when their lane is right (`expectations.json` `stayDegenerateEvenIfPass`):
`bidi-lines-002` android (the orange `!` on the left), `counter-suffix` ios / android (rows 3–6 / 5–6) and
`block-ellipsis-002` ios (no clamp).""")

rep('L3 U3b', """  - **U3b, probe-decided (seam-3b, a separate patch and commit).** A line-start br in a host whose cascade declares
    `line-height` gets that line box (`normal` → 1.2 × font-size) instead of 20. Radius: 12 brs in 4 documents
    (hyphenate-character-001…004).""",
"""  - **U3b, probe-decided (seam-3b, a separate patch and commit).** A line-start br whose HOST (the br's parent element)
    declares `line-height` **on itself** (its own declared cascade; the `font` shorthand counts) gets that line box
    (`normal` → 1.2 × font-size) instead of 20. Radius: 12 brs in 4 documents (hyphenate-character-001…004). An INHERITED
    line-height does not trigger U3b: under that reading 23 more 20-px brs in 4 documents would move, two of them not
    carriers (`css-multicol/baseline-002`, `baseline-007`; `skeptic-r1/br-census-all.out.txt`). Fix round 1, S4.""")
rep('L3 pins', """    - U3b: 001 → `[0,19.2,0,19.2,0,19.2]`.

    Mutation: drop the re-arm.""",
"""    - U3b: 001 → `[0,19.2,0,19.2,0,19.2]`;
    - U3b negative (fix round 1, S4): the verbatim `css-multicol/baseline-007` brs (line-height 40px declared on an
      ANCESTOR, not the host) stay 20, and `baseline-002`'s (2em on an ancestor) stay 20. Mutation: read the nearest
      ancestor's line-height (the inherited reading) → red.

    Mutation: drop the re-arm.""")
rep('L3 002 row', """  | `hyphenate-character-002` web / ios / android | f 0.92 / 0.9261 / 0.9223 → ≈0.96 / mover / stays f (Minikin dictionary hyphen: a logged wall) | LOW-MED / LOW / LOW | report | U1, U2-ios, U3 |
  | `hyphenate-limit-chars-001` ios | f 0.8919 → mover (U+2010 → U+002D in the dictionary fold) | LOW | report | U2-ios |""",
"""  | `hyphenate-character-002` web / ios / android | f 0.92 / 0.9261 / 0.9223 → ≈0.96 / mover (**undirected**) / stays f (Minikin dictionary hyphen: a logged wall; replay B of its only unit U3: 0.923) | LOW-MED / LOW / LOW | report | U1, U2-ios, U3 |
  | `hyphenate-limit-chars-001` ios | f 0.8919 → mover (U+2010 → U+002D in the dictionary fold; **undirected**) | LOW | report | U2-ios |""")
rep('L3 be rows', """  | `block-ellipsis-002` web | P 0.9868 → P ≈1.0 (B replay 1) | MED-HIGH | 0.995 | U3 |
  | `block-ellipsis-004/-005/-006` web | P 0.9737 / 0.9735 / 0.9735 → P ≈0.998 (B replay) | MED-HIGH | 0.99 | U3 |
  | `block-ellipsis-002` ios / android | P 0.9813 → ≈0.993 / P 0.9884 → identical or up | MED / LOW | report | U3 |""",
"""  | `block-ellipsis-002` web | P 0.9868 → P ≈1.0 (B replay 1) | MED-HIGH | 0.995 · gating (`block-ellipsis-br`) | U3 |
  | `block-ellipsis-004/-005/-006` web | P 0.9737 / 0.9735 / 0.9735 → P ≈0.998 (B replay) | MED-HIGH | 0.99 · gating (`block-ellipsis-br`) | U3 |
  | `block-ellipsis-002` ios / android | P 0.9813 DEGENERATE → ≈0.993, stays DEGENERATE (Line 4, no "…") / P 0.9884 → identical or up | MED / LOW | report (control lines `4 bands vs ref 3` / `2 bands vs ref 3`) | U3 |""")
rep('L3 mnm', """- **Must not move (72 cells):**""", """- **Must not move (74 cells):**""")
rep('L3 mnm pop', """  - the U2 default-argument population on both natives: 16 css-text/hyphens tests + `block-ellipsis-014/-028`;""",
"""  - the U2 default-argument population on both natives: 17 css-text/hyphens tests + `block-ellipsis-014/-028` (fix round 1,
    S6, added `hyphens-none-shy-on-2nd-line-001` android P 0.998 / ios P 0.9988: it carries U+00AD on the wire);""")
rep('L3 geo', """  - `wave53-plan/soft-hyphen.geometry.py <run>`: control. The wave-53 soft-hyphen pictures must survive U2's defaulted
    parameter.""",
"""  - `wave53-plan/soft-hyphen.geometry.py <run>`: control. The wave-53 soft-hyphen pictures must survive U2's defaulted
    parameter.
  - `block-ellipsis-br.geometry.py <run>` (fix round 1, S3): band count, tops / bottoms ±3 px and right edges ±4 px of the
    ref in the top 200 rows. The four web rows are gating (U3): WRONG on wave53-final (002 band top y79 vs y59; 004-006 3
    bands vs 2), OK on the brief's U3 replay pictures, WRONG on white and on the ref shifted 6 px
    (`fix-r1/post-S3.block-ellipsis-fakes.out.txt`). The native rows are controls holding today's WRONG lines verbatim.
  - The hyphenate gating keys `requires` U1 + U2-ios + U3 (fix round 1, M3): a probe revert of any of them withdraws the key.""")

rep('L4 catalyst', """  - Catalyst `-only-testing:` `OutOfFlowContainingBlockTests`, `FixedHoistTests`, `TransformContainingBlockTests`,
    `GapDecorationLinesTests`, `GapDecorationSegmentsTests`;""",
"""  - Catalyst `-only-testing:` `OutOfFlowContainingBlockTests` (NEW, the lane creates it), `FixedHoistTests`,
    `TransformContainingBlockTests`, and for the Swift `GapDecorationLines` / `-Segments` edits the EXISTING classes that
    exercise them: `GapDecorationSegmentsTests`, `GapDecorationBandsTests`, `GapDecorationGrammarTests`. There is no
    `GapDecorationLinesTests` class (fix round 1, S7); a lane that wants one creates it in its own list. Every
    `-only-testing:` run records xcodebuild's `Executed N tests` line with N > 0: a filter on a missing class runs zero
    tests and is not a green run (`build-workflow.js` rule 4);""")
rep('L4 004 row', """  | `contain-content-004` android | f 0.8286 → moves, stays f (the table renders wrong on both natives) | MED | report | OOF-android |""",
"""  | `contain-content-004` android | f 0.8286 → moves, stays f (the table renders wrong on both natives); **undirected** (brief §10 R1: its in-flow `FAIL` span), exempt from rule 2 | MED | report | OOF-android |""")
rep('L4 006 row', """  | `flex-gap-decorations-006` android | f 0.8223 → may move, stays f | LOW | report | CBB-android |""",
"""  | `flex-gap-decorations-006` android | f 0.8223 → may move, stays f; **undirected**, exempt from rule 2 | LOW | report | CBB-android |""")
rep('L4 geo', """  - Executed on wave53-final: every one of these native rows prints WRONG and every control passes.""",
"""  - Executed on wave53-final: every gating row prints WRONG, every control passes, and the report rows
    (backdrop-filter-containing-block ×2, position-relative-003 ×2, autopos rtl android ×3) print WRONG (`--self-test`).""")

rep('L5 movers', """  | movers (android): text-decoration-color f 0.6164, counter-list-item f 0.7307, counter-reset-increment-overflow-underflow f 0.8699, subelements-003 f 0.9084, backdrop-filter-border-radius-change f 0.5977, -corner-shape-change f 0.4904 | move, stay f — look, never count | LOW | report | U1-android |""",
"""  | movers (android): text-decoration-color f 0.6164, counter-list-item f 0.7307, counter-reset-increment-overflow-underflow f 0.8699, subelements-003 f 0.9084, backdrop-filter-border-radius-change f 0.5977, -corner-shape-change f 0.4904 | move, stay f — look, never count. text-decoration-color is directed (≈0.67, the iOS anchor); the other five are **undirected** and exempt from rule 2 (fix round 1, S1) | LOW | report | U1-android |""")
rep('L5 geo', """- **Geometry verdicts:** `ua-heading-face.geometry.py <run>`: every row OK. block-in-inline android is gating; inset-011's
  rows print OK only while its native bands EQUAL the recorded ones.""",
"""- **Geometry verdicts:** `ua-heading-face.geometry.py <run>`: every row OK. block-in-inline android is gating; inset-011's
  rows print OK only while its native bands EQUAL the recorded ones. Fix round 1 (M2): block-in-inline also checks every
  band TOP within ±3 px of the ref, so a line-pitch / line-height error fails it (the skeptic's +2 px/line fake scored
  0.9709 ≥ the 0.97 floor and printed OK before; it prints `band top y107 vs ref y103` now). The iOS anchor (tops
  30/66/105/144 vs 29/65/103/142) stays OK, and every wave53-final line is byte-identical.""")

rep('L6 movers', """  | `scope-pseudo-element`, `display-flow-root-list-item-001` | f 0.9353 / 0.8003 → movers | LOW | report | RS |""",
"""  | `scope-pseudo-element`, `display-flow-root-list-item-001` | f 0.9353 / 0.8003 → up / mover (**undirected**, exempt from rule 2) | LOW | report | RS |""")
rep('L6 geo', """  - `web-root-separator.geometry.py <run>`: every web row OK; box-sizing-007/-008/-022 gating. The native rows are controls:
    box-sizing-013 and the semi-replaced natives keep today's WRONG lines verbatim.""",
"""  - `web-root-separator.geometry.py <run>`: every web row OK; box-sizing-007/-008/-022 gating. The native rows are controls:
    box-sizing-013 and the semi-replaced natives keep today's WRONG lines verbatim. Fix round 1: after the recorded row the
    probe checks the second-atom edge on EVERY ref atom row band (S2: a fix on the probed band only — 1 of 10 on 007, 1 of 3
    on 008 — prints WRONG), and it carries one key for each of box-sizing-011, 014-019, 020, 021, 024, 025 (S3; web
    report rows WRONG today, natives controls OK).""")

rep('§6 score', """**Score of record:**
`node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0.005 --json tools/titan/results/wave54-gate/score-final.json`.""",
"""**Score of record** (restated at fix round 1, M1): the adjudicated JSON is produced with **`--movers 0`**:
`node tools/titan/score-gate.mjs wave54-open wave54-final --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 --json tools/titan/results/wave54-gate/score-final.json > tools/titan/results/wave54-gate/score-final.movers0.txt`,
then `node tools/titan/results/wave54-gate/adjudicate.mjs tools/titan/results/wave54-gate/score-final.json`.
- Why: `score-gate.mjs:125` lists a same-verdict cell only when |Δ| ≥ the threshold, and `adjudicate.mjs` R4 reads a cell
  absent from every list as "did not move". With 0.005 it failed a closing gate in which every prediction came true: the
  four P→P gating rows predicted below 0.005 (autopos-{htb,vlr,vrl}-ltr android Δ0.0001, s-11-1-1b-006 android Δ0.0039)
  and counter-suffix iOS landing on its floor (Δ0.0048). R5 could not see a must-not-move move in (0.002, 0.005).
- With `--movers 0` every scored cell is in a list, so R4 reads each gating cell's measured score against its floor and R5
  applies its own 0.002 rule to every must-not-move cell.
- The three autopos-ltr android floors (0.995) sit below today's 0.9966, so R4 is vacuous on them: **their gate is R6
  alone** (the CBB-android geometry keys), said here rather than implied.
- The human-readable mover list for the PR is the same command with `--movers 0.005` and no `--json` (`score-final.txt`);
  it adjudicates nothing.
- Calibrated (`expectations.json` `adjudicateCalibrations`, §11): all predictions met → R1–R5 hold; counter-suffix iOS at
  exactly 0.985 → hold; one must-not-move cell +0.003 → R5 FAIL (with the old threshold the same picture shows R5 moved 0);
  the identity pair `wave53-final → wave54-open` → R4 FAIL, missed 31 (34 minus the 3 vacuous autopos floors).""")
rep('§6 R6', """| **R6 picture-correctness** | `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open` exits 0: every gating key PASS, no probe self-check failure. Every report key is read and labelled (it gates nothing). | A target whose gating key is not PASS stays with its old picture: DEGENERATE, as before. Say so; do not count it. |""",
"""| **R6 picture-correctness** | `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open` exits 0: **every gating key of a unit still on the tree** PASS, no probe self-check failure. Keys of a unit reverted at a probe print `WITHDRAWN` (`plan-build.py` `REVERTED` demotes them; the gate exits 2 on a stale JSON that still gates them). Every report key is read and labelled; every control key is expected PASS (a FAIL is diagnosed through R4/R7). A §1 tally cell is counted only when its `pictureCorrectTally` key is OK. | A target whose gating key is not PASS stays with its old picture: DEGENERATE, as before. Say so; do not count it. |""")
rep('§6 R7', """| **R7 must-not-move** | Every must-not-move line of `watchlist.txt` prints `prev == cur` (`adjudicate` R5: within 0.002, never lost/gained). R4 implies it for every capture outside the carrier sets. |""",
"""| **R7 must-not-move** | Every must-not-move line of `watchlist.txt` prints `prev == cur` (`adjudicate` R5: within 0.002, never lost/gained; with the `--movers 0` JSON R5 reads every one of the 651 cells). R4 implies it for every capture outside the carrier sets. |""")
rep('§6 stage1', """- A unit reverted here is entered in `plan-build.py` `REVERTED` (the JSON is regenerated, never edited) before stage 2.""",
"""- A unit reverted here is entered in `plan-build.py` `REVERTED` (the JSON is regenerated, never edited) before stage 2. The
  regeneration withdraws its predictions, its carriers AND its gating geometry keys (`probeDecisions.reverted[].withdrawnGeometryKeys`).""")
rep('§6 lane rule', """**What each lane must show at its probe** (a miss reverts the commit: revert rules 1-8). Every row also requires rules 1, 2 and
5 for every carrier cell of the lane: no P → f, no prediction row down by ≥ 0.002, no leak.""",
"""**What each lane must show at its probe** (a miss reverts the commit: revert rules 1-8). Every row also requires rules 1, 2 and
5 for every carrier cell of the lane: no P → f, no "up-or-stay" prediction row down by ≥ 0.002, no leak.""")
rep('§6 rule2', """2. **Moved down.** Δ ≤ −0.002 on any row of a lane's predictions, at any tier. No prediction in this plan forecasts a fall.
   Same-host noise is 0 on every platform (BACKLOG "Wave 53 lessons").""",
"""2. **Moved down.** Δ ≤ −0.002 on any prediction row whose `direction` is `up-or-stay`, at any tier. No prediction in this
   plan forecasts a fall. **Ten rows predict a move whose sign is not pre-registered** (`direction: "undirected"`, each with
   its `directionWhy`): hyphenate-character-002 ios, hyphenate-limit-chars-001 ios, contain-content-004 android,
   flex-gap-decorations-006 android, counter-list-item / counter-reset-increment-overflow-underflow /
   text-decoration-subelements-003 / backdrop-filter-border-radius-change / -corner-shape-change android,
   display-flow-root-list-item-001 web. They are **exempt from rule 2**, so a LOW coin flip cannot revert a commit that
   carries HIGH flips (OOF-android, U1-android, RS, U2-ios). A fall on one is looked at against the ref and named with its
   cause in the probe read-out and the PR; it reverts nothing by itself. Rules 1 and 5 still bind them (all ten are f
   today, so rule 1 cannot fire on them; what they gate is nothing — said plainly). Same-host noise is 0 on every platform
   (BACKLOG "Wave 53 lessons").""")
rep('§6 tier', """predicted, or not at all, is read and labelled, never a trigger. Rules 1, 2 and 5 apply to every tier, so "read and
labelled" never covers a P → f or a fall.""",
"""predicted, or not at all, is read and labelled, never a trigger. Rules 1 and 5 apply to every row of every tier and rule 2
to every `up-or-stay` row of every tier, so "read and labelled" never covers a P → f, and covers a fall only on the ten
undirected rows.""")

rep('§7 count', """- **773 watch lines covering 779 distinct cells**, generated""", """- **775 watch lines covering 781 distinct cells** (fix round 1: +2, S6), generated""")
rep('§7 check', """  tools/titan/results/wave52-plan/watchlist-check.mjs` → `watch-lines=773 matched-cells(distinct)=779 … unmatched 0`.""",
"""  tools/titan/results/wave52-plan/watchlist-check.mjs` → `watch-lines=775 matched-cells(distinct)=781 … unmatched 0`.""")
open(P, 'w').write(s)
print('PLAN.md restated')

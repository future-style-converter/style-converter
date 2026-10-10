#!/usr/bin/env python3
# fix r2: the PLAN.md edits of the second fix pass. Reads ../PLAN.md, writes argv[1]; every replacement matches once.
import sys, os
HERE = os.path.dirname(os.path.abspath(__file__))
src = open(os.path.join(HERE, '..', 'PLAN.md')).read()
def rep(a, b):
    global src
    assert src.count(a) == 1, (src.count(a), a[:100]); src = src.replace(a, b)

# ── §1 (R2-N1 PLAN:189; R2-S4 totals) ──
rep("""| +2 ios (MED-HIGH); +1 ios, +2 web at MED / MED-LOW; 5 P→P picture-correct (block-ellipsis ×4 web, -002 ios) |""",
    """| +2 ios (MED-HIGH); +2 ios, +4 web at MED / MED-LOW (incl. clip-path-filter-order web / iOS and balance-grid-container web on replay B, fix round 2); 4 P→P picture-correct (block-ellipsis ×4 web; -002 ios is OUT and stays DEGENERATE, fix round 1 S3) |""")
rep("""- With the MED and MED-LOW flips: web +3 · iOS +5 · Android +4 more, a ceiling of **web 1239 · iOS 1133 · Android 1125**.
- LOW / LOW-MED possibles, never counted: web `hyphenate-character-002/-004`; Android `hyphenate-character-001/-003`,
  `backdrop-filter-clip-rect` / `-edge-clipping` / `-paint-order`.""",
    """- With the MED and MED-LOW flips: web +5 · iOS +6 · Android +4 more, a ceiling of **web 1241 · iOS 1134 · Android 1125**
  (fix round 2, R2-S4: replay B on their own pixels adds `clip-path-filter-order` web / iOS and `balance-grid-container`
  web at MED; it was web +3 · iOS +5, 1239 / 1133).
- LOW / LOW-MED possibles, never counted: web `hyphenate-character-002/-004`; Android `hyphenate-character-001/-003` and
  `clip-path-filter-order` (replay B 0.9549). The three `backdrop-filter-*` Android rows are undirected movers now (not
  replayable: R2-S4).""")

# ── §2 L1 own (N1) ──
rep("""    +95 / −4 over today's 1172 lines (`git diff --stat a8ffd1c6^ b0edb788^`); the lane keeps every NEW function except""",
    """    +91 / −4 over today's 1172 lines (`git diff --numstat a8ffd1c6^ b0edb788^ -- tools/titan/bidi-bake.mjs`; the 95 written
    here before fix round 2 was the `--stat` line total); the lane keeps every NEW function except""")
rep("""  - `tools/titan/bidi-bake.test.mjs`, with the pins split per unit.""",
    """  - `tools/titan/bidi-bake.test.mjs`, with the pins split per unit (758 lines at HEAD, already past the ~300-line split
    line: the lane adds its pins in per-unit `describe` blocks and reports the final count, like the 0(d) exception, §9 D8).""")

# ── §2 L3 predictions header + table (R2-N1, R2-S4) ──
rep("""- **Predictions** (46 rows; `expectations.json`). Floors on the six MED-HIGH rows (001 / 003 ios, block-ellipsis-002 /
  -004 / -005 / -006 web); every other row is read and labelled, but rules 1/2 bind it.""",
    """- **Predictions** (46 rows; `expectations.json`). Floors on the six MED-HIGH rows (001 / 003 ios, block-ellipsis-002 /
  -004 / -005 / -006 web); every other row is read and labelled. Rules 1 and 5 bind every row; rule 2 binds every row
  except the **16 undirected** ones (002 ios, limit-chars-001 ios, the nine backdrop-filter rows, balance-grid-container
  ios / android, column-height-009 ×3: fix round 2, R2-S4). U3b is ADDITIVE in every L3 row's `requires` (its revert keeps
  the U3-only value the row gives first), so a U3b revert demotes nothing; a U1 / U2-<p> / U3 revert demotes the rows
  that need it (fix round 2, R2-S1).""")
rep("""  | `backdrop-filter-clip-rect / -edge-clipping / -paint-order` web, ios | P 0.959-0.9595 / 0.956-0.9578 → up | MED | report | U3 |
  | the same three android | f 0.9315 / 0.929 / 0.929 → up, a flip is possible | LOW | report | U3 |""",
    """  | `backdrop-filter-clip-rect / -edge-clipping / -paint-order` web, ios | P 0.959-0.9595 / 0.956-0.9578 → moves, expected up; **undirected** (not replayable: the stray line sits under abspos boxes that do not move with the flow and text shows through their backdrop filter; fix round 2 extends R2-S4 to these six) | MED | report | U3 |
  | the same three android | f 0.9315 / 0.929 / 0.929 → moves, expected up; **undirected** (same reason, R2-S4) | LOW | report | U3 |""")
rep("""  | `clip-path-filter-order`, `balance-grid-container`, `column-height-009` ×3 | f → up or unchanged | LOW | report | U3 |""",
    """  | `clip-path-filter-order` web / ios / android | f 0.8586 / 0.8578 / 0.8406 → **P ≈1.0 / P ≈0.998 / ≈0.955** (replay B on their own pixels 1 / 0.9978 / 0.9549, `hyphenate-character.replay-b-u3f-score.out.txt`; LOOKED at: `fix-r2/look-clip-path-filter-order-ref-Bweb-Bios-Bandroid.png`) | MED / MED / LOW-MED | report | U3 |
  | `balance-grid-container` web | f 0.9207 → **P ≈1.0** (replay B 1: the two stray blank lines removed give the ref, `fix-r2/look-balance-grid-container-ref-Bweb.png`) | MED | report | U3 |
  | `balance-grid-container` ios / android, `column-height-009` ×3 | f → moves; **undirected** (not replayable: the natives paint the address on one line; column-height-009's 15 brs feed a multicol balance) | LOW | report | U3 |""")

# ── §2 L5 own (R2-N8) ──
rep("""  - NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` and `UAHeadingFoldGate.kt`,
    with their JVM tests under `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/`;""",
    """  - NEW `runtimes/compose/src/main/java/com/styleconverter/runtime/typography/UAElementFontRule.kt` and `UAHeadingFoldGate.kt`,
    with their JVM tests NEW `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/UAElementFontRuleTest.kt`
    and `UAHeadingFoldGateTest.kt` — these two files only, not the directory (it holds earlier waves' tests, and L3's
    `typography/wrapping/PreBreakPipelineTest.kt` sits under it; fix round 2, R2-N8);""")

# ── §2 L6 (N4 range; R2-S1 step 0) ──
rep("""  - seam-1 lifts the WWS branch of `renderChildSeparator` (:1105-1158) into an exported pure `wsAfterSeparator(prev, next,""",
    """  - seam-1 lifts the WWS branch of `renderChildSeparator` (:1108-1153; the widget rule at :1105-1107 stays in place) into an exported pure `wsAfterSeparator(prev, next,""")
rep("""  - **V3 ≠ 46 stops W1:** its row is withdrawn and RS proceeds alone.""",
    """  - **V3 ≠ 46 stops W1:** its row is withdrawn and RS proceeds alone. The withdrawal is MECHANICAL (fix round 2, R2-S1):
    the orchestrator enters `{'lane': 'L6-web-tail', 'unit': 'W1', 'run': 'step0', 'rule': 'step 0: V3 != 46, W1 not built'}`
    in `plan-build.py` `REVERTED` and regenerates, which withdraws the -002 web row (gating 34 → 33), its geometry key and its
    carrier (dry run: `fix-r2/hooks/w1/`).""")

# ── §2 L7 own (N3 + N7) ──
rep("""    - optionally, NEW `tools/visual/label-chrome-check.mjs` (the pure checker, so replays import it without registering
      node:test cases).""",
    """    - optionally, NEW `tools/visual/label-chrome-check.mjs` (the pure checker, so replays import it without registering
      node:test cases). Size: the tripwire is 209 lines at HEAD, so U1's new logic belongs in this file whenever adding it
      inline would take the test past the ~300-line split line (fix round 2, N7).""")
rep("""  `tools/visual/label-chrome-check.mjs` (U1); the 18 `tools/visual/baseline/{Android,iOS,web}__00{0..5}_*.png` (U2-seed);""",
    """  `tools/visual/label-chrome-check.mjs` (U1); the 18 `tools/visual/baseline/{Android,iOS,web}__{000_ATC_AllThenProps,
  001_ATC_PropsThenAll_InGreenParent,002_reset,003_ATC_InitialUnderRedParent,004_span,005_ATC_DirectionSurvives}.png` —
  exactly these names (`label-chrome-all-reset.seeded-77fe41e8/`); the glob `__00{0..5}_*.png` also matches 66 committed
  baselines of four other fixtures (fix round 2, N3) — (U2-seed);""")

# ── §3 registry (N4) ──
rep("""| `apps/web-harness/src/sdui/ComponentRenderer.tsx` | `:1099-1158` lift the WWS branch""",
    """| `apps/web-harness/src/sdui/ComponentRenderer.tsx` | `:1099-1154` (the WWS branch is :1108-1153) lift the WWS branch""")

# ── §6 score of record (R2-N1 numbering, R2-S3) ──
rep("""  and counter-suffix iOS landing on its floor (Δ0.0048). R5 could not see a must-not-move move in (0.002, 0.005).
- With `--movers 0` every scored cell is in a list, so R4 reads each gating cell's measured score against its floor and R5
  applies its own 0.002 rule to every must-not-move cell.
- The three autopos-ltr android floors (0.995) sit below today's 0.9966, so R4 is vacuous on them: **their gate is R6
  alone** (the CBB-android geometry keys), said here rather than implied.
- The human-readable mover list for the PR is the same command with `--movers 0.005` and no `--json` (`score-final.txt`);
  it adjudicates nothing.
- Calibrated (`expectations.json` `adjudicateCalibrations`, "Fix pass (round 1)"): all predictions met → R1–R5 hold; counter-suffix iOS at
  exactly 0.985 → hold; one must-not-move cell +0.003 → R5 FAIL (with the old threshold the same picture shows R5 moved 0);
  the identity pair `wave53-final → wave54-open` → R4 FAIL, missed 31 (34 minus the 3 vacuous autopos floors).""",
    """  and counter-suffix iOS landing on its floor (Δ0.0048). adjudicate's R5 could not see a must-not-move move in (0.002, 0.005).
- With `--movers 0` every scored cell is in a list, so adjudicate's R4 reads each gating cell's measured score against its
  floor and adjudicate's R5 applies its own 0.002 rule to every must-not-move cell. (adjudicate.mjs numbers its rules R1–R6:
  its R4 is this table's R-floor read and its R5 this table's R7; the table below keeps the plan's own R1–R9.)
- The three autopos-ltr android floors (0.995) sit below today's 0.9966, so adjudicate's R4 is vacuous on them: **their
  gate is R6 alone** (the CBB-android geometry keys), said here rather than implied.
- The human-readable mover list for the PR is the same command with `--movers 0.005` and
  `--json tools/titan/results/wave54-gate/score-final.movers0005.json` (`score-final.txt`, `expectations.json`
  `scoreReadout`); it adjudicates nothing. **It is the corpus snapshot's source** (§8 step 13, `corpusSnapshotSource`):
  `make-corpus.mjs` publishes `movers: record.movers.length` under a hardcoded `--movers 0.005` scorer string, and the
  `--movers 0` record lists every same-verdict cell (4096 "movers" on the identity pair) (fix round 2, R2-S3).
- Calibrated (`expectations.json` `adjudicateCalibrations`, "Fix pass (round 1)"; re-run at fix round 2 on the regenerated
  JSON with the string-fixed adjudicate.mjs): all predictions met → adjudicate R1–R5 hold; counter-suffix iOS at exactly
  0.985 → hold; one must-not-move cell +0.003 → adjudicate R5 FAIL (with the old threshold the same picture shows R5
  moved 0); the identity pair `wave53-final → wave54-open` → adjudicate R4 FAIL, missed 31 (34 minus the 3 vacuous
  autopos floors).""")

# ── §6 R6 row (R2-S2) ──
rep("""| **R6 picture-correctness** | `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open` exits 0: **every gating key of a unit still on the tree** PASS, no probe self-check failure.""",
    """| **R6 picture-correctness** | `python3 tools/titan/results/wave54-plan/geometry-gate.py wave54-final --base wave54-open` exits 0: **every gating key of a unit still on the tree** PASS, no probe self-check failure. A gating key that is UNMEASURED (capture MISSING or no line) is not a pass: the gate exits 3 (fix round 2, R2-S2), so "exit 0" is "every gating key PASS, UNMEASURED-gating 0".""")

# ── §6 stage 1 / stage 2 read-outs (R2-M2, R2-S1) ──
rep("""  - `python3 geometry-gate.py wave54-pre --base wave54-open --lanes L1,L2`;
  - `node tools/titan/results/wave54-gate/control-check.mjs wave54-open wave54-pre --sections CSS2,css-counter-styles,css-tables,css-text`;
  - `node tools/titan/results/wave52-gate/cells.mjs 'counter-suffix|bidi-lines|s-11-1-1b-006' wave54-open wave54-pre`;
  - OPEN counter-suffix ×3 and 006 android.""",
    """  - `python3 geometry-gate.py wave54-pre --base wave54-open --lanes L1,L2` (exit 0 = every L1 / L2 gating key PASS; 1 a
    FAIL; 3 a gating key UNMEASURED — on a stage-1-shaped run every L1 / L2 gating key is measured, `fix-r2/post-S2.geogate-stage1-shaped.out.txt`);
  - `node tools/titan/score-gate.mjs wave54-open wave54-pre --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 --json tools/titan/results/wave54-gate/score-pre.json`,
    then **`node tools/titan/results/wave54-plan/probe-readout.mjs tools/titan/results/wave54-gate/score-pre.json --stage1`**
    (revert rules 1-3 for L1 / L2, every other lane's rows recorded; fix round 2, R2-M2);
  - `node tools/titan/results/wave54-gate/control-check.mjs wave54-open wave54-pre --sections CSS2,css-counter-styles,css-tables,css-text`;
  - `node tools/titan/results/wave52-gate/cells.mjs 'counter-suffix|bidi-lines|s-11-1-1b-006' wave54-open wave54-pre`;
  - OPEN counter-suffix ×3 and 006 android.""")
rep("""- A unit reverted here is entered in `plan-build.py` `REVERTED` (the JSON is regenerated, never edited) before stage 2. The
  regeneration withdraws its predictions, its carriers AND its gating geometry keys (`probeDecisions.reverted[].withdrawnGeometryKeys`).""",
    """- A unit reverted here is entered in `plan-build.py` `REVERTED` (the JSON is regenerated, never edited) before stage 2. The
  regeneration withdraws its predictions, its carriers AND its gating geometry keys (`probeDecisions.reverted[].withdrawnGeometryKeys`),
  and DEMOTES every prediction that keeps other units but `requires` the reverted one (`demotedPredictions`: gating off,
  floor none, undirected; fix round 2, R2-S1). `plan-build.py` asserts that no gating prediction needs a reverted unit
  unless `RESTATE` re-registers it with its own `gating`, `floor` and `why`. The same mechanism carries L6's step-0
  decision: W1-not-built is entered as `{'unit': 'W1', 'run': 'step0'}` (§2 L6, §8 step 3).""")
rep("""- Then `ab-diff.mjs wave54-probe wave54-open --threshold 0.002`, the R4 / R4b controls restricted to those sections, and
  `geometry-gate.py wave54-probe --base wave54-open`.""",
    """- Then, **base first in every command** (fix round 2, R2-M2 — `ab-diff.mjs` is `<excludeRun> <includeRun>` and prints
  Δ = include − exclude, so the order written here before printed Δ = open − probe and labelled every probe gain
  `flip P→f`: wave 53's own pair printed `background-attachment-margin-root-002` web `P 1 → f 0.339 Δ-0.661`,
  `fix-r2/pre-M2.abdiff-plan-order.wave53-probe.out.txt`; `expectations.json` `probeRun.readOut`):
  - `node tools/titan/results/wave52-gate/ab-diff.mjs wave54-open wave54-probe --threshold 0.002` (Δ = probe − open; the
    same pair of wave 53 prints that cell `flip f→P f 0.339 → P 1 Δ+0.661`, `fix-r2/post-M2.abdiff-fixed-order.wave53-probe.out.txt`);
  - **the floor reader:** `node tools/titan/score-gate.mjs wave54-open wave54-probe --watch tools/titan/results/wave54-plan/watchlist.txt --movers 0 --json tools/titan/results/wave54-gate/score-probe.json`,
    then `node tools/titan/results/wave54-plan/probe-readout.mjs tools/titan/results/wave54-gate/score-probe.json`. It reads
    revert rules 1, 2 and 3 over every carrier, every prediction row and every non-carrier cell of the probed sections,
    names each unit with its `revertOrder`, REFUSES a record whose `prev` is not `wave54-open` (exit 2) or that was written
    with a mover threshold (exit 2: an unlisted gating cell would read as "unchanged", the M1 hole), and exits 3 when a
    probed section is missing;
  - the R4 / R4b controls restricted to those sections (`control-check.mjs wave54-open wave54-probe --sections …`: rule 5);
  - `geometry-gate.py wave54-probe --base wave54-open` (rule 4; exit 3 on an UNMEASURED gating key).""")

# ── §6 revert rules 2 / 3 / 4 + tier (R2-S4 count, R2-M2 reader, R2-S2) ──
rep("""   plan forecasts a fall. **Ten rows predict a move whose sign is not pre-registered** (`direction: "undirected"`, each with
   its `directionWhy`): hyphenate-character-002 ios, hyphenate-limit-chars-001 ios, contain-content-004 android,
   flex-gap-decorations-006 android, counter-list-item / counter-reset-increment-overflow-underflow /
   text-decoration-subelements-003 / backdrop-filter-border-radius-change / -corner-shape-change android,
   display-flow-root-list-item-001 web. They are **exempt from rule 2**, so a LOW coin flip cannot revert a commit that
   carries HIGH flips (OOF-android, U1-android, RS, U2-ios). A fall on one is looked at against the ref and named with its
   cause in the probe read-out and the PR; it reverts nothing by itself. Rules 1 and 5 still bind them (all ten are f
   today, so rule 1 cannot fire on them; what they gate is nothing — said plainly). Same-host noise is 0 on every platform
   (BACKLOG "Wave 53 lessons").""",
    """   plan forecasts a fall. **Twenty-four rows predict a move whose sign is not pre-registered** (`direction: "undirected"`,
   each with its `directionWhy`): hyphenate-character-002 ios, hyphenate-limit-chars-001 ios, contain-content-004 android,
   flex-gap-decorations-006 android, counter-list-item / counter-reset-increment-overflow-underflow /
   text-decoration-subelements-003 / backdrop-filter-border-radius-change / -corner-shape-change android,
   display-flow-root-list-item-001 web, and (fix round 2, R2-S4: no replay is possible on them) backdrop-filter-clip-rect /
   -edge-clipping / -paint-order ×3, balance-grid-container ios / android, column-height-009 ×3. They are **exempt from
   rule 2**, so a coin flip cannot revert a commit that carries HIGH flips (OOF-android, U1-android, RS, U2-ios, U3). A
   fall on one is looked at against the ref and named with its cause in the probe read-out and the PR (`probe-readout.mjs`
   prints it as `READ`); it reverts nothing by itself. Rules 1 and 5 still bind them: eighteen are f today, so rule 1
   cannot fire on them; the six backdrop-filter web / iOS rows are P today, so a P → f there still reverts U3. The four
   U3-radius rows with a replay (clip-path-filter-order ×3, balance-grid-container web) stay directed. A row DEMOTED by a
   probe decision (R2-S1) becomes undirected too. Same-host noise is 0 on every platform (BACKLOG "Wave 53 lessons").""")
rep("""3. **Below floor.** A gating prediction (every HIGH / MED-HIGH row; 34) scores below its `floor`.
4. **Wrong geometry.** A gating key of `geometry-gate.py` is not PASS. The runner names the unit.""",
    """3. **Below floor.** A gating prediction (every HIGH / MED-HIGH row; 34) scores below its `floor`. Read by
   `probe-readout.mjs` over the `--movers 0` probe record (a gating cell that did not move is READ and fails; fix round 2,
   R2-M2). A row demoted by a probe decision has no floor (R2-S1).
4. **Wrong geometry.** A gating key of `geometry-gate.py` is not PASS (FAIL: exit 1, the runner names the unit;
   UNMEASURED: exit 3, re-run its section — never a pass; fix round 2, R2-S2).""")
rep("""to every `up-or-stay` row of every tier, so "read and labelled" never covers a P → f, and covers a fall only on the ten
undirected rows.""",
    """to every `up-or-stay` row of every tier, so "read and labelled" never covers a P → f, and covers a fall only on the
twenty-four undirected rows (and on rows a probe decision demoted).""")
rep("""- The ceiling with MED and MED-LOW: 1239 / 1133 / 1125.""",
    """- The ceiling with MED and MED-LOW: 1241 / 1134 / 1125 (fix round 2, R2-S4; was 1239 / 1133 / 1125).""")

# ── §8 steps 3, 8, 9, 13 ──
rep("""3. **[W-L6] step 0** can run as soon as L6 has written its probe script (Chromium, ~1 min, no device). L6's branch follows it.""",
    """3. **[W-L6] step 0** can run as soon as L6 has written its probe script (Chromium, ~1 min, no device). L6's branch follows it.
   If step 0 says V3 ≠ 46, W1 is not built: enter `{'lane': 'L6-web-tail', 'unit': 'W1', 'run': 'step0', …}` in
   `plan-build.py` `REVERTED` and regenerate (R2-S1; it withdraws the -002 web row, its geometry key and its carrier).""")
rep("""8. **Stage 1 `wave54-pre`** (4 sections, ~10 min), decided per §6 for L1 and L2. Reverts go into `plan-build.py`
   `REVERTED`, the JSON is regenerated, and the reverted sections are re-probed if anything else in them is to be read.
9. **Stage 2 `wave54-probe`** (26 sections, ~65 min), read per §6: `ab-diff`, the controls, `geometry-gate.py`. Every""",
    """8. **Stage 1 `wave54-pre`** (4 sections, ~10 min), decided per §6 for L1 and L2 (`stage1Probe.readOut`: base first;
   `probe-readout.mjs … --stage1`). Reverts go into `plan-build.py` `REVERTED`, the JSON is regenerated (withdrawing and
   demoting what the revert touches), and the reverted sections are re-probed if anything else in them is to be read.
9. **Stage 2 `wave54-probe`** (26 sections, ~65 min), read per §6 (`probeRun.readOut`, base first): `ab-diff.mjs
   wave54-open wave54-probe`, the `--movers 0` score + `probe-readout.mjs` (rules 1-3), the controls, `geometry-gate.py`. Every""")
rep("""13. **Ship** per wave skill Phase 6. The corpus snapshot gets `artifact` / `reproduce --run-id` = `wave54-final`. BACKLOG""",
    """13. **Ship** per wave skill Phase 6. The corpus snapshot gets `artifact` / `reproduce --run-id` = `wave54-final`, and its
    `<record.json>` (`make-corpus.mjs`) is **`tools/titan/results/wave54-gate/score-final.movers0005.json`** (the
    `scoreReadout` JSON, `corpusSnapshotSource`), never the `--movers 0` `score-final.json` (R2-S3). BACKLOG""")
open(sys.argv[1], 'w').write(src)
print('patched ->', sys.argv[1])

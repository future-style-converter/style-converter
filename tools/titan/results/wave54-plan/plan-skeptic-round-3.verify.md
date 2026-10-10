# Wave 54 plan, fix pass round 3: verification

**Verdict: CLAIM HOLDS for R3-S1, R3-S2 and R3-S3. Must-fix = no.**
- **R3-S1 is closed.**
  - `adjudicate.mjs` now refuses every thresholded record with exit 2. That includes the skeptic's hole picture, which
    the HEAD copy still passes as `R1–R5 hold`, exit 0.
  - Every `--movers 0` twin reads exactly as before.
- **R3-S2 is closed.** On the synthetic correct tree with the all-OK geometry JSON, adjudicate prints the plan's own
  iOS gains RETIRED, not DEGENERATE. Every non-retiring path prints DEGENERATE with its reason.
- **R3-S3 is closed for the class it names: a column-rule (red) segment's vertical extent.**
  - Every vertical fake is now WRONG ×3: the skeptic's two, fix-r3's two and two new ones of mine.
  - The ref, the web captures and a ref-as-run picture stay OK.
- **plan-build.py is byte-stable.** Two `--out` runs give `c9d73e56…` / `2557d870…`, the same as the installed files.
- **One new should-fix (R3V-S1).** The same hole remains in the transposed direction:
  - a row-rule (blue) segment whose **horizontal** extent is 4–5 px short prints `GEOMETRY OK` ×3;
  - it scores 0.999 / 0.9993, above the GAP rows' 0.99 floor;
  - a one-read fix is executed below as a stand-alone proposal.
- Two nits follow.

**Scope and hygiene.**
- **What I wrote:** only this file and `tools/titan/results/wave54-plan/verify-r3/`.
- **What I left untouched:** `adjudicate.mjs`, `control-check.mjs`, PLAN, expectations, probes, sources, tests and lanes.
- **What I did not run or commit:** nothing committed; no Gradle, xcodebuild, emulator, simulator or Chromium.
- **How the runs were made:** every probe and score run used `nice -n 19` and read run dirs and the frozen refs only.
- **Regenerable JSONs deleted after use.** These are the synthetic and real score records, the hook / `--out` expectations, and the geometry JSONs. Their sha256s are in `verify-r3/regenerable.sha.out.txt`.
  - The deleted geometry JSONs were byte-identical to `fix-r3/geogate-wave53-final.json` and `fix-r3/geometry-{allmet,u3only,u2ios,otherpair}.json`.
  - To regenerate them, use the commands in PLAN "Fix pass (round 3)", with `verify-r3/` as the output dir.
- **The tree.** HEAD is `31b76944`. The fix-r3 edits are uncommitted:
  - modified: `adjudicate.mjs`, PLAN, expectations, `plan-build.py`, the flex probe and its record, and the geometry-gate record;
  - untracked: `fix-r3/`.
- **Untouched files, re-checked.** `control-check.mjs` is byte-equal to `fix-r3/control-check.mjs.pre`, and `build-workflow.js` has no diff.

---

## R3-S1: `adjudicate.mjs` refuses a thresholded or misordered record

**Inputs.** I regenerated the inputs as follows:
- The hooks: `plan-build.py --out verify-r3/hooks/{abc,u2ios} --reverted fix-r3/hooks/<h>.reverted.json`. Both exit 0, and their stdout is byte-equal to fix-r3's.
- The closing records: `fix-r3/synth-closing-r3.mjs` (installed + abc), built from the real identity record. The four `.m0005` twins come out byte-identical to the kept ones.
- **The 0.005 twins are byte-identical to each other.** `abc-allmet.m0005` and `abc-mnm3.m0005` share sha256 `a266efc8…`, and the two installed ones share `d8bf2dae…`. A thresholded record cannot express the −0.003 must-not-move move at all.
- **Fresh real records**, so the guard is tested on score-gate's own output and not only on fix-r3's synthesis:
  - `score-gate.mjs wave53-final wave54-open --movers 0` / `0.005`;
  - `score-gate.mjs wave53-open wave53-probe --movers 0` / `0.005`, a partial run with 1427 unmeasured-now cells.
  - The real identity `--movers 0` record equals `fix-r1/score-identity-movers0.json` once `generated` is ignored.

**Adjudications.** Outputs are in `verify-r3/adj/*.out.txt`.

| input | expectations | result | exit |
|---|---|---|---|
| installed all-met `.m0` | installed | `R4 34, missed 0` · `R5 651, moved 0` · hold | 0 |
| installed mnm3 `.m0` | installed | `FAIL R5 … moved 1` | 1 |
| installed all-met / mnm3 `.m0005` | installed | `NOT A --movers 0 RECORD: 30 cells listed against 4096` | **2** |
| abc all-met `.m0` | abc hook | `R4 24, missed 0` · hold | 0 |
| abc mnm3 `.m0` | abc hook | `FAIL R5 … moved 1` | 1 |
| abc all-met / **mnm3** `.m0005` | abc hook | `NOT A --movers 0 RECORD: 24 … vs 4096` | **2** |
| abc mnm3 `.m0005`, the **HEAD** copy (`skeptic-r3/adjudicate-exp-arg.mjs`) | abc hook | **`R1–R5 hold`**, the move unseen (the R3-S1 hole, reproduced) | **0** |
| installed all-met `.m0005`, the HEAD copy | installed | `FAIL R4 … missed 4`. It prints 88 rows (4 of them gating) as "scored on neither side"; all 88 are scored on both sides | 1 |
| real identity `.m0` (prev `wave53-final`) | installed | `ORDER: … needs wave54-open FIRST` | **2** |
| same, `--base wave53-final` | installed | `FAIL R4 … missed 31` · `R5 moved 0` (unchanged) | 1 |
| real identity `.m0005`, `--base wave53-final` | installed | `NOT A --movers 0: 0 listed vs 4096` | **2** |
| real `wave53-open → wave53-probe` `.m0`, `--base wave53-open` | installed | the guard passes: 14 + 0 + 2655 + 1427 = 4096; the rules run | 1 |
| the same pair `.m0005`, `--base wave53-open` | installed | `NOT A --movers 0: 1458 listed vs 4096 (31 vs 2669 on cur)` | **2** |
| the same pair `.m0`, default base | installed | `ORDER` | **2** |
| identity with prev / cur swapped, `--base wave53-final` | installed | `ORDER` | **2** |
| `skeptic-r1/synth-score.json`, default base / its own base | installed | `ORDER` / `NOT A --movers 0 (30 vs 0)` | **2** / **2** |
| fix-r1 all-met / floor / mnm3 / mnm3-old, `--base wave53-final` | installed | hold / hold / `FAIL R5 moved 1` / `NOT A --movers 0 (30 vs 4096)` | 0 / 0 / 1 / **2** |
| a stray extra argument | — | usage | **2** |

**Comparison with fix-r3's records.** Every row that fix-r3 also recorded is byte-identical to its `fix-r3/post-S1.*` / `post-cal.*` file, apart from that file's trailing `exit=` line. That covers 18 files.

**Checks on the guard itself:**
- **The arithmetic.** I read score-gate `diffRuns` (`score-gate.mjs:115-126`). At `--movers 0`, every two-sided cell is in gained / lost / movers, because `|Δ| >= 0` always holds. So `gained + lost + movers + unmeasuredNow = Σ totals.prev.measured`, and the `cur` side balances the same way. The real partial-run record confirms it.
- **The fail direction.** A NaN `ssim` would make the guard refuse, not pass, so it fails safe.

**Other readers of the score records:**
- `probe-readout.mjs` on the identity record (`--base wave53-final`) still prints `rule 3 fired 31`, exit 1, byte-equal to `fix-r3/post-readout-identity.out.txt`.
- `closingGateFiles`, PLAN §6 (the which-reader-reads-which-JSON table) and §8 step 11 agree with one another. No PLAN or `build-workflow.js` line feeds the 0.005 JSON to adjudicate.

## R3-S2: retiring a degenerate gain when degenerateRetirement holds

**Inputs:**
- I ran `geometry-gate.py wave53-final --base wave53-open --json` in this pass. Its JSON is byte-equal to `fix-r3/geogate-wave53-final.json`. Its stdout is byte-equal to the refreshed installed record `geometry-gate.wave53-final.out.txt` (R3-N1).
- I ran a copy of `fix-r3/synth-geometry.py` (output dir changed only). It re-ran `hyphenate-character.geometry.py --replay GB / B`, and produced all four geometry JSONs byte-identical to fix-r3's kept ones. Its stdout is identical too.

| record + geometry | R6 read-out | R1–R5 | exit |
|---|---|---|---|
| installed all-met + all-OK geometry | **`RETIRED … 2`**: 001 ios 0.9287 → 0.979, 003 ios 0.9337 → 0.977, "units U1, U2-ios, U3 on the tree; … PASS (→ GEOMETRY OK)"; `gained 13: web 4 ios 3 android 6` with no DEGENERATE suffix | hold | 0 |
| same, no `--geometry` | DEGENERATE 2, "not read: no --geometry JSON" | hold | 0 |
| same + the U3-only geometry (replay B) | DEGENERATE 2: the probe's `glyph:` / `offset:` WRONG line quoted | hold | 0 |
| same + a geometry JSON of another pair | `GEOMETRY PAIR … wave53-final --base wave53-open` | — | **2** |
| U2-ios closing (`synth-u2ios-r3.mjs`) + its own geometry / + all-OK | DEGENERATE 1: 003 ios "unit U2-ios reverted (probeDecisions)" in both. The units are read before the picture | `R4 32, missed 0` hold | 0 |
| abc all-met + all-OK geometry | RETIRED 2 | `R4 24` hold | 0 |
| **adversarial**: all-met + 001 android and 001 web made gains, all-OK geometry | RETIRED 4 (001 ios / 003 ios / 001 android / 001 web) | hold | 0 |
| **adversarial**: the same, with a new hook reverting **U2-android** (`verify-r3/hooks/u2and.reverted.json`; plan-build demotes 001 / 003 / 004 android) | 001 android DEGENERATE "unit U2-android reverted"; 001 ios, 003 ios and 001 web RETIRED | hold | 0 |

**Comparison with fix-r3's records.** Every row also in fix-r3 is byte-identical to `fix-r3/post-S2.*`. The one difference is the GEOMETRY PAIR message, which quotes the path it was given.

**Checks on the retirement inputs:**
- The retirement keys exist in the probe: `plan-build.py` asserts `degenerateRetirementCheck` against `degenerateByConstruction`, the L3 units, every platform, and the probe's own expect strings.
- geometry-gate's JSON carries the `run` / `base` / `rows[].{script,key,verdict,line}` that adjudicate reads (`geometry-gate.py:118,143`).

**R6 still never changes the exit.**

## R3-S3: the flex-gap probe reads each column rule's vertical extent

I ran `bash fix-r3/run-gap-probe.sh flex-zero-gap-rules.geometry.py` over all 11 runs and fakes (`verify-r3/gap/run-gap-probe.out.txt`). The output is **byte-identical** to `fix-r3/post-S3.probe.out.txt`.
- **Real runs.** wave53-final and wave54-open: ref OK (self-check, exit 0), web OK, iOS / Android WRONG (`redPx 0 vs ref 2200`). A re-run on wave53-final is byte-equal to the installed `flex-zero-gap-rules.geometry.out.txt`.
- **The earlier fakes keep their verdicts:**
  - `skeptic-r2/fake-gap`: row 95;
  - fix-r2's `fake-gap-blue`: col 90;
  - fix-r2's `fake-gap-line3`: row 145;
  - `short6` / `line1-short4`: redPx.
- **The vertical fakes print WRONG ×3**, each naming the flex line:
  - `skeptic-r3/fake-gap-vshift5`: col 115, line 2;
  - `line2-short4`: col 115, line 2;
  - `fix-r3/fake-gap-vshift3-up`: col 115, line 2;
  - `fix-r3/fake-gap-line3-short3`: col 65, line 3.
- **The HEAD probe printed OK on those four.** It is byte-equal to `fix-r3/…geometry.py.pre`. Run over the same set, it prints `GEOMETRY OK` ×3 on all four (`verify-r3/gap/run-gap-probe.HEAD.out.txt`, byte-equal to `fix-r3/pre-S3.probe.out.txt`).
- **New fakes of mine** (`verify-r3/gap/make-fakes.py`, built in the ref's own colours with the background taken from the no-rule iOS capture; `extra-fakes.probe.out.txt`):
  - `ref-as-run`, the ref itself as all three captures: **OK ×3**, SSIM 1. The probe does not fail a correct picture.
  - `red-l1r-down2`, line 1's RIGHT segment 2 px down: WRONG ×3, col 115, line 1.
  - `red-l3r-up2`, line 3's RIGHT segment starting 2 px early: WRONG ×3, col 115, line 3.
  - The fix-r3 fakes and these two cover all five column-rule segments.
- **The gate.** `geometry-gate.py <fake> --lanes L4` on vshift5, vshift3-up, line3-short3 and the r2 fake prints `revert rule 4 names: GAP-android, GAP-ios` and exits 1. The three fix-r3 outputs are byte-equal to `fix-r3/post-S3.geogate-*`.
- **The rest re-holds:**
  - `geometry-gate.py wave53-final --base wave53-open --self-test` reports **HOLDS, 0 violations** (gating 37 FAIL · control 112 PASS · report 36 FAIL);
  - watchlist-check reports `unmatched 0` on wave53-final and on wave54-open.

## plan-build.py, run twice

- **Byte-stable.** `plan-build.py --out verify-r3/pb1` and `--out verify-r3/pb2` both exit 0 with identical stdout, equal to `plan-build.out.txt` (`fdb6f47b…`).
  - `expectations.json` is `c9d73e56…` in both runs and installed.
  - `watchlist.txt` is `2557d870…` in both runs, installed, and unchanged from round 2.
- **The diff against HEAD (`8f870f8e…`) is the intended keys only:**
  - top level: `adjudicate`, `adjudicateCalibrations`, `closingGateFiles`, `abRead`, `degenerateRetirementCheck`, `geometryGate`, `revertRule`;
  - lane level: L3 `revertUnits`, which is U3b `probeDecided`, R3-N2;
  - lane level: L4 `geometryProbes`, which is `reads` only. `cmd` / `expect` / `gating` / `report` / `requires` are identical.
- **Unchanged:**
  - all 124 prediction tuples (cell, gating, floor, direction, from, to, units, requires) are identical;
  - every `mustNotMove` list is identical;
  - the totals are unchanged: gating 34 · undirected 24 · geometry keys 185.
- **The text nits** are present as described:
  - R3-N2: U3b keep rule on the two gating iOS rows;
  - R3-N4: the nine carrier controls in `geometryGate.what` and §6 R6;
  - R3-N5: §6 "Revert rule 2 is a probe rule" and `revertRule[1]`;
  - R3-N6: `abRead`, with `ab-diff.mjs` being `<excludeRun> <includeRun>`, so the arm goes first.
- **Syntax checks.** `node --check adjudicate.mjs` passes. `adjudicate.mjs` has 197 lines and no `wave53` string.

---

## Defects

### MUST-FIX
None.

### SHOULD-FIX

**R3V-S1. The flex-gap probe still passes a row-rule (blue) segment with the wrong HORIZONTAL extent.** This is R3-S3 transposed.
- **How the blue rules are pinned today:**
  - their **vertical** position, by the blue runs on columns x40 / x90 / x140;
  - their **horizontal** extent, only by the blue count (±2 % of 3000 = ±60 px) and the overall blue bbox. Each of the two row rules spans x16-165, so either rule alone keeps the bbox.
- **Executed** (`verify-r3/gap/extra-fakes.probe.out.txt`, `score-fakes.out.txt`, `geogate-L4.verify-r3_gap_fake-blue-r1-short5.out.txt`):

  | fake | probe | gate GAP keys | SSIM (`diffWebVsRef`) | floor 0.99 |
  |---|---|---|---|---|
  | `blue-r1-short5`: row rule 1 ends 5 px early (x16-160), −50 px = 1.7 % | **GEOMETRY OK ×3** | PASS / PASS | **0.999** | cleared |
  | `blue-r2-lshort4`: row rule 2 starts 4 px late (x20-165), −40 px | **GEOMETRY OK ×3** | — | **0.9993** | cleared |

- **Why it is should-fix, not a nit.** It is the same class and tier as R3-S3: the only gate of the HIGH GAP-ios / GAP-android rows passes a wrong picture above the floor.
  - Today the natives paint **neither** rule colour: wave53-final iOS / Android show `redPx 0` and `bluePx 0`. The lane must therefore build the row-rule geometry as newly as the column rules.
  - An end-of-rule inset at the container edge (an outset or inset mis-applied where there is no intersection) is the transposed twin of the vertical-extent error.
  - The lane's unit test pins "2 full-width row-rule segments" (PLAN §2 L4 GAP). That covers the model, not the device picture.
- **Fix, executed as a stand-alone proposal.** The script is `verify-r3/gap/proposal-bluerow.geometry.py`: the installed probe plus 5 replaced lines. Output is in `proposal-bluerow.out.txt`.
  - **The change:** also read the blue runs on a ROW through each row-rule gap (y65 / y115) against the ref, ±1 px. The ref gives `[(16, 165)]` for each; the ref self-check gains `blueRow {65: 1, 115: 1}`.
  - **Results over all 16 runs and fakes (exit 0 ×16):**
    - wave53-final / wave54-open: ref and web OK, natives WRONG (unchanged);
    - `ref-as-run`: OK ×3;
    - every earlier fake keeps its verdict and reason;
    - both blue fakes: WRONG ×3 (`blue runs row 65 [(16, 160)] vs ref [(16, 165)]`, `row 115 [(20, 165)]`).
  - **To install it** (orchestrator's call):
    - update the probe header, `reads` in plan-build.py, and §2 L4;
    - re-run plan-build twice and `geometry-gate.py --self-test`.

### NITS

- **R3V-N1. The calibration entry for the swapped identity record does not name the base it was run with.**
  - In `adjudicateCalibrations[5]`, "the same record with prev / cur swapped exits 2 ORDER" is true only with `--base wave53-final`, which is how fix-r3 ran it (`post-S1.reversed-identity` says "needs wave53-final FIRST").
  - With the default base, the swapped record is `wave54-open → wave53-final` and is legitimately adjudicated: `FAIL R4 missed 31`, exit 1 (`verify-r3/adj/S1-swapped-default.out.txt`).
  - Add "(with `--base wave53-final`)".
- **R3V-N2. The DEGENERATE header gives the wrong reason when no geometry JSON was passed.**
  - Without `--geometry`, the header reads "DEGENERATE-BY-CONSTRUCTION gains (not fixes): 2 — degenerateRetirement **does not hold** for them". In fact it was **not read**.
  - The per-cell reason says so correctly. A header such as "is not established for them" would match both cases.
  - This is string only and does not affect the exit.
- **Note, not a defect.** "Units on the tree" is read from `probeDecisions.reverted` only.
  - A unit that never landed, or one reverted after the probe, is not seen there.
  - Retirement also requires the cell's own geometry row PASS on the same run pair, so a retired cell is picture-correct whatever the unit bookkeeping says.

---

## Executed checks: index (`verify-r3/`)

| check | output |
|---|---|
| plan-build twice + hashes | `pb1/out.txt`, `pb2/out.txt`, `regenerable.sha.out.txt` |
| plan-build hooks abc / u2ios / u2and | `hooks/*/plan-build.out.txt`, `hooks/u2and.reverted.json` |
| adjudicate over m0 / m0005 / real / swapped / calibrations / HEAD copy | `adj/S1-*`, `adj/cal-*`, `adj/PRE-*` |
| adjudicate R6 retirement + adversarial U2-android | `adj/S2-*` |
| probe-readout on identity | `readout-identity.out.txt` |
| geometry-gate wave53-final + self-test | `geo/geogate-wave53-final.out.txt`, `geo/selftest.out.txt`, `geo/synth-geometry.out.txt` |
| watchlist-check ×2 | `watchlist-check.{wave53-final,wave54-open}.out.txt` |
| flex probe: installed / HEAD / extra fakes / proposal / gate L4 / SSIM | `gap/run-gap-probe.out.txt`, `gap/run-gap-probe.HEAD.out.txt`, `gap/extra-fakes.probe.out.txt`, `gap/proposal-bluerow.out.txt`, `gap/geogate-L4.*`, `gap/score-fakes.out.txt`, `gap/flex-probe.wave53-final.out.txt` |

STATUS: COMPLETE

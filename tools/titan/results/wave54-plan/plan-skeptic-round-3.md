# Wave 54 plan skeptic, round 3

**Verdict: CLAIM HOLDS. Must-fix = no.**
- Every round-2 item that the fix pass took on is closed, and each closure was checked by running it: R2-M1, R2-M2, R2-S1…S4, and the nits R2-N1, N3, N7 (round-1 N1, N3–N7) and N8.
- The fix pass's own record re-holds byte for byte:
  - `plan-build.py` is byte-stable (two runs give the installed sha256 `8f870f8e…` / `2557d870…`);
  - `geometry-gate.py --self-test` HOLDS;
  - the watchlist matches with `unmatched 0`.
- A new pass over §6 found no closing rule that cannot fail and none that fails on a correct tree's exit code.
- It found three should-fix gaps next to those rules:
  - **R3-S1:** `adjudicate.mjs` accepts a thresholded score record without complaint. With three units reverted, that record ships a must-not-move move.
  - **R3-S2:** adjudicate's DEGENERATE line labels the plan's own iOS gains "not fixes" on a fully correct tree.
  - **R3-S3:** the fixed flex-gap probe still passes two wrong pictures, both scoring ≥ 0.9995.
- Seven nits follow.

**Scope and hygiene.**
- **What I wrote:** only this file and `tools/titan/results/wave54-plan/skeptic-r3/` (scripts, `.out.txt` outputs, four small fake run dirs `fake-gap-*`, hook `reverted` JSONs).
- **What I left untouched:**
  - `adjudicate.mjs`: no string needed to change for R2-N3.
  - Every source, test, doc, lane and plan file.
- **What I did not run or commit:** nothing committed; no Gradle, xcodebuild, emulator, simulator or Chromium.
- **How the runs were made:** every probe and score run used `nice -n 19` and read run dirs only.
- **Regenerable JSONs deleted after use:** the `--movers 0` / `0.005` score records, the synthetic closing records and the hooks' `expectations.json`. To regenerate:
  - the score records: `node tools/titan/score-gate.mjs wave53-final wave54-open --movers 0 --json …` (and `wave53-open wave53-probe`, `--movers 0` / `0.005` / reversed);
  - the synthetic closing records: `node …/skeptic-r3/synth-closing.mjs <exp> <identity.json> <outdir> <tag>`;
  - the hooks' expectations: `python3 …/plan-build.py --out …/skeptic-r3/hooks/<h> --reverted …/fix-r2/hooks/<h>.reverted.json`. For the new hook, use `…/skeptic-r3/hooks/abc.reverted.json`.
- **The tree.** HEAD is `db6e8aa0`. The fix-r2 pass is **uncommitted**: `probe-readout.mjs`, the `replay-b-u3f` pair and `fix-r2/` are untracked, and PLAN / expectations / plan-build / geometry-gate are modified. Seven lanes are writing on this tree (see R3-N7).

---

## Check 1: R2-M1, the flex-gap probe

`flex-zero-gap-rules.geometry.py` (output: `skeptic-r3/r2m1-gap-probe.out.txt`).

| run | ref | web | ios | android | exit |
|---|---|---|---|---|---|
| wave53-final | OK | OK | WRONG (`redPx 0 vs ref 2200`) | WRONG (same) | 0 |
| wave54-open | OK | OK | WRONG | WRONG | 0 |
| `skeptic-r2/fake-gap` | OK | **WRONG** (`red runs row 95 [(61, 70)] vs ref [(111, 120)]`) | **WRONG** | **WRONG** | 0 |

**The fix pass's own two fakes** (`skeptic-r3/r2m1-fixr2-fakes.out.txt`):
- `fake-gap-blue` prints `WRONG (blue runs col 90 …)` ×3;
- `fake-gap-line3` prints `WRONG (red runs row 145 …)` ×3.

**The gate on the fake.** `geometry-gate.py ../results/wave54-plan/skeptic-r2/fake-gap --lanes L4` prints `revert rule 4 names: GAP-android, GAP-ios` and exits 1 (`skeptic-r3/r2m1-geogate-fake-gap-L4.out.txt`).

**The whole gate on wave53-final** (`skeptic-r3/geogate-final.out.txt`):
- 185 keys: gating 37 all FAIL · control 112 all PASS · report 36 all FAIL; exit 1.
- `--self-test` gives **HOLDS, 0 violations**, exit 0 (`skeptic-r3/geogate-selftest.out.txt`).
- The output differs from the plan's record `geometry-gate.wave53-final.out.txt` in exactly one line. That line is the 006 android `line:` text, which lacks the `→` added by N5 after the record was written (R3-N1).

**R2-M1 is closed for the picture it named.** New fakes that keep every count and run are R3-S3.

## Check 2: R2-M2, the order of the probe read-out

**ab-diff in the pre-registered order.** I ran `ab-diff.mjs wave53-open wave53-probe --threshold 0.002`, base first as in §6, `probeRun.readOut.abDiff` and `stage1Probe.readOut.abDiff` (`skeptic-r3/r2m2-abdiff-preregistered-order.wave53.out.txt`). The signs come out right:
```
flip f→P  web      f 0.339   → P 1       Δ+0.661   css/css-backgrounds/background-attachment-margin-root-002.html
flip f→P  android  f 0.8934  → P 0.9629  Δ+0.0695  css/css-text/bidi/bidi-lines-001.html
mover     web      P 0.9506  → P 1       Δ+0.0494  css/css-lists/counter-reset-reversed-nested.html
mover     android  P 0.9944  → P 0.9906  Δ-0.0038  css/CSS2/css21-errata/s-11-1-1b-006.html
```
- Gains print `+` and wave 53's reverted TB-android fall prints `−`.
- No inverted `ab-diff` invocation is left in PLAN.md, `expectations.json`, `plan-build.py` or `build-workflow.js`.

**`probe-readout.mjs`, executed** (`skeptic-r3/readout-*.out.txt`):

| record | flags | result | exit |
|---|---|---|---|
| `score-gate wave53-open wave53-probe --movers 0` | `--stage1` | rule 3 + rule 2 on 006 android (0.9906 < 0.996, Δ-0.0038). It names **only `L2 TB-android`**. 7 leak signals are wave 53's own changes | 1 |
| same record | none | 8 probed sections missing, all named. Rules fire on wave-54 rows that are not on that tree | 1 |
| the reversed record (`wave53-probe → wave53-open`) | either | `ORDER: … Refusing.` | **2** |
| a `--movers 0.005` record | either | `NOT A --movers 0 RECORD: 1458 cells listed … vs 4096` | **2** |
| identity `wave53-final → wave54-open --movers 0` | none | rule 3 fires on **31** rows (34 − 3 vacuous autopos floors) | 1 |
| same identity record | `--stage1` | rule 3 fires on **6** rows | 1 |

**R2-M2 is closed:** the sign is fixed by the scorer's own `prev → cur`, and rule 3 has a reader that refuses the M1 hole.

## Check 3: R2-S1, partial reverts (plan-build.py hooks)

`skeptic-r3/hooks/*/plan-build.out.txt`, re-run from `fix-r2/hooks/*.reverted.json`:

| reverted | exit | gating rows | geometry keys withdrawn |
|---|---|---|---|
| Mprime + TB-android (`a`) | 0 | 31 | 7 |
| U1 | 0 | 32 | 2 |
| U2-ios | 0 | 32 | 2 |
| U3b | 0 | 34 (nothing demoted) | 0 |
| W1 at step0 | 0 | 33 | 1 |
| U2-ios + bad RESTATE | **1** | `AssertionError: … gating, but its required unit(s) ['U2-ios'] are reverted — demote it or RESTATE it with a why` | — |
| U2-ios + good RESTATE | 0 | 33 | — |

**The skeptic-r2 U2-ios closing score**, rebuilt on the hook JSON (`skeptic-r3/synth/u2ios-closing.*`):
- adjudicate prints `R4 gating predictions 32, missed 0` and `R1–R5 hold`; 001 / 003 ios are printed `demoted at wave54-probe: unit U2-ios reverted`;
- `probe-readout` gives `rule 3 fired 0`, exit 0.

The cascade into U3b / U3 is gone. **Closed.**

## Check 4: R2-S2, an UNMEASURED gating key

I ran `geometry-gate.py wave53-probe --base wave53-open --lanes L5` (`skeptic-r3/r2s2-geogate-probe-L5.out.txt`). The summary reads:
```
geometry-gate wave53-probe (base wave53-open): 15 keys — … UNMEASURED 15 (gating 1) …
UNMEASURED GATING KEYS (1) — not a pass: …
   L5-ua-heading-face ua-heading-face.geometry.py block-in-inline-015-print android [unit U1-android]
exit=3
```
The round-2 run exited 0. §6 R6, revert rule 4 and `geometryGate.what` all say "exit 3". **Closed.**

## Check 5: R2-S3, the corpus snapshot source

- `scoreReadout` now writes `--json tools/titan/results/wave54-gate/score-final.movers0005.json`.
- `corpusSnapshotSource` names that file, and so does §8 step 13.
- `make-corpus.mjs` publishes `movers: record.movers.length` under a hardcoded `--movers 0.005` scorer string (re-read).

**Closed.** The side effect, a 0.005 JSON next to the adjudicated one, is what R3-S1 is about.

## Check 6: R2-S4, the twelve rows' direction fields

Printed from `expectations.json`; the scores were re-run with the replay scorer.

**Directed, carrying their replay values.** `replay-b-u3f-score.mjs` re-run is byte-identical to its record (`skeptic-r3/r2s4-replay-score.out.txt`):

| row | direction | from → to | replay B |
|---|---|---|---|
| clip-path-filter-order web | `up-or-stay` | f 0.8586 → P ≈1.0 | 1 |
| clip-path-filter-order ios | `up-or-stay` | f 0.8578 → P ≈0.998 | 0.9978 |
| clip-path-filter-order android | `up-or-stay` | f 0.8406 → "up, ≈0.955 at best" | 0.9549 |
| balance-grid-container web | `up-or-stay` | f 0.9207 → P ≈1.0 | 1 |

**Undirected (8), each with a `directionWhy`:**
- column-height-009 ×3: multicol re-balance;
- balance-grid-container ios / android: the address sits on one line, so there is no blank band to cut;
- backdrop-filter-clip-rect / -edge-clipping / -paint-order android: abspos boxes over the stray line.

**The total.** The six backdrop-filter web / iOS rows were made undirected too, which brings the plan's list to **24 undirected rows** (`plan-build.out.txt` lists them).
- Every corpus row carries a `direction`, and every undirected row has a `directionWhy`.
- **6 are P today** (the backdrop-filter web / iOS rows at 0.956–0.9595) **and 18 are f**, exactly as §6 rule 2 states (`skeptic-r3/undirected-today.out.txt`).

**Closed.**

## Check 7: R2-N3, adjudicate.mjs's strings

**Diff against `wave53-gate/adjudicate.mjs`** (re-read). The changes are:
- the header path, "Wave 54's" and the usage line;
- the absent-key comments;
- "back at wave54-open";
- `WITHDRAWN at ${d.run} (unit ${d.unit})` via a new message-only map;
- the `demotedFrom` read-out suffix.

The rule bodies of R1–R6 are unchanged. `node --check` passes, and `grep wave53` over the file finds 0 matches. Run on the identity `--movers 0` record it prints `FAIL R4 gating predictions 34, missed 31` and `ok R5 … 651, moved 0`. Its only `wave53` text comes from data: the record header and prediction texts (`skeptic-r3/r2n3-adjudicate-identity.out.txt`).

**My synthetic closing records** (`skeptic-r3/synth-closing.mjs`, built from the real identity record), with every gating row at its prediction:

| record | result | exit |
|---|---|---|
| all-met | `R1–R5 hold` | 0 |
| all-met + one must-not-move cell −0.003 | `FAIL R5 … moved 1` | 1 |

**Closed** (but see R3-S1 and R3-S2).

## Check 8: plan-build.py, run twice

`skeptic-r3/regen.sha.out.txt`: two `--out` runs exit 0 with identical stdout, which equals `plan-build.out.txt`.
```
8f870f8e9b66…f128  expectations.json   (installed = regen1 = regen2)
2557d870289e…acf1  watchlist.txt       (installed = regen1 = regen2)
```

**Assertions read** (`grep -n assert plan-build.py`, 50 sites), on top of round 2's list:
- `requires ⊆ units`;
- `direction ∈ {up-or-stay, undirected}`;
- "a gating row must have a direction";
- after the probe decisions, "no gating row needs a reverted unit unless RESTATE gives `gating` + `why` + a floor";
- every HIGH / MED-HIGH corpus row gating unless demoted;
- every gating floor numeric;
- the tally keys exist and are P→P rows;
- report ⊆ expect, report ∩ gating = ∅, gating ⊆ expect;
- `requires` keys gating and naming known units;
- carrier disjointness across lanes;
- units cover the carriers exactly;
- no must-not-move line is a carrier;
- every carrier sits in a probed section.

**The watchlist** (`skeptic-r3/watchlist-check.out.txt`): `watch-lines=775 matched-cells(distinct)=781 … unmatched 0` on both wave53-final and wave54-open.

## Check 9: §6 closing rules, as adjudicate.mjs + geometry-gate.py + control-check.mjs read them

| rule | can it fail? | does it fail on a correct tree? | executed |
|---|---|---|---|
| R1 lost | yes (any P→f) | no: no row predicts a fall. The 6 P undirected backdrop rows are bound on purpose | — |
| R2 denominator | yes | **checked for render-dependent stamps:** `scoreExcluded='absence-only'` depends on the verdict (`isAbsenceOnly`), so a carrier on a blank ref could leave the denominator when it flips. **None can:** min ref coverage over all 150 carrier captures is 0.425 % against the floor 0.02 %, and 0 carriers are absence-only today. The 4 unscored carriers are tag-excluded `true` (static). The wire carriers' NA tags are capability tags, not delivery ones | `r2-render-dependent-exclusion.out.txt`, `r2-carrier-scored.out.txt`, `wire-carriers-na.out.txt` |
| R3 completeness | yes | no | — |
| R4 / R4b control | yes; withdrawn carriers are subtracted (`carriersWithdrawn`) | no. A nit on "absent before" captures (R3-N3) | read |
| R5 gains (DEGENERATE clause) | — | **mislabels** 001 / 003 ios as "not fixes" on the all-met tree: **R3-S2** | `synth/installed-allmet.m0.adjudicate.out.txt` |
| R6 geometry | yes (self-test: 37/37 gating FAIL on wave53-final) | no gating key fails a ref or a replay. Control FAILs never change the exit; 9 controls sit on carriers (R3-N4) | `control-on-carriers.out.txt` |
| R7 must-not-move (adjudicate R5) | yes on the `--movers 0` record; **blind on a thresholded one**: **R3-S1** | no | `synth/abc-mnm3.*` |
| R8 / R9 | external commands | — | (round 2 covered R8's reach) |

**Other facts checked along the way:**
- **Prediction units vs carrier units** (`units-vs-carriers.out.txt`): every corpus prediction's `units` equals the units that carry its cell, except hyphenate-character-002 ×3, which omit U3b. That is harmless: U3's `revertOrder` is [U3b, U3] and U3b's revert demotes nothing.
- **Carriers with no prediction row** are 4, all unscored. A withdrawn row is therefore never still carried by a kept unit, so the "held to must-not-move" mechanism cannot fail on a correct tree.
- **Gating floors** (`gating-floors.out.txt`): only the 3 autopos-ltr floors are vacuous, as §6 says. Each of the 34 rows has a gating geometry key.

---

## Round-2 findings: closure

| r2 | status | executed evidence (skeptic-r3/) |
|---|---|---|
| R2-M1 flex probe row coverage | **closed** | the r2 fake → WRONG ×3; wave53-final / wave54-open ref OK, web OK, natives WRONG; gate on the fake names GAP-android, GAP-ios |
| R2-M2 ab-diff order; no floor reader | **closed** | base-first ab-diff prints margin-root-002 `f→P Δ+0.661`; probe-readout: ORDER exit 2, 0.005 exit 2, identity rule 3 fires 31 / 6, wave-53 stage-1 names TB-android only |
| R2-S1 partial revert → unmeetable floors | **closed** | hooks 31 / 32 / 32 / 34 / 33 gating; bad RESTATE asserts; u2ios closing holds, R4 32 missed 0 |
| R2-S2 UNMEASURED gating exits 0 | **closed** | exit 3, the key and its unit named |
| R2-S3 corpus snapshot source | **closed** | `scoreReadout --json …movers0005.json`, `corpusSnapshotSource`, §8 step 13 |
| R2-S4 unreplayed "up" rows bind rule 2 | **closed** | 4 directed with replay values (re-scored byte-identical); 8 + 6 undirected with a `directionWhy`; 6 P / 18 f |
| R2-N1, N3, N7 (r1 N1, N3–N7), N8 | **closed** | N1: `91 4`. N3: 18 names. N4 at HEAD: :1099 / :1105-1107 / :1108-1153 / :1154. N5: arrow. N6: header, `bash -n`. N7: 758 / 209 / 274 at HEAD. N8: the two files named |
| R2-N2, N4, N5, N6 | **open** (declared out of scope by the fix pass) | R2-N2 now interacts with R3-N4 |

---

## Defects, ranked

### MUST-FIX
None.

### SHOULD-FIX

**R3-S1. `adjudicate.mjs` has no `--movers 0` guard, and the plan now writes a thresholded JSON beside the adjudicated one.**
- **The asymmetry.** `probe-readout.mjs` refuses a thresholded record: `listed !== Σ totals.prev` exits 2. adjudicate does not. Its R5 (the table's R7) reads an absent cell as "scored on neither side", so on a 0.005 record it cannot see a move in (0.002, 0.005).
- **The setup.** Fix round 2 (R2-S3) made `tools/titan/results/wave54-gate/score-final.movers0005.json` a pre-registered file next to `score-final.json`.
- **Executed** (`skeptic-r3/synth/`, adjudicate copied with an expectations argument, `skeptic-r3/adjudicate-exp-arg.mjs`). One must-not-move cell (`css-cascade/all-prop-001.html ios`) was moved by −0.003:

  | expectations | record | result | exit |
  |---|---|---|---|
  | installed | `--movers 0` | `FAIL R5 … moved 1` | 1 |
  | installed | `0.005` | `FAIL R4 … missed 4` plus a **false** read-out: autopos ×3 / 006 android `measured not in any list (scored on neither side)`. They are scored on both sides | 1 |
  | Mprime + TB-android + CBB-android reverted (`hooks/abc.reverted.json`) | `--movers 0` | `FAIL R5 … moved 1` | 1 |
  | same | `0.005` | **`R1–R5 hold`**, the move unseen | **0** |

  So the second row fails only because R4 is accidentally fail-safe (the four rows predicted below 0.005), with a wrong explanation printed, and the last row ships the move.
- **Fix** (the orchestrator's file; this is logic, so outside R2-N3's string scope):
  - copy probe-readout's two guards into adjudicate: exit 2 when `gained + lost + movers + unmeasuredNow !== Σ totals.prev.measured`, and when `score.prev !== 'wave54-open'`;
  - re-run the four `adjudicateCalibrations`.

**R3-S2. adjudicate's DEGENERATE line ignores `degenerateRetirement`, so on a fully correct tree it labels the plan's own gains "not fixes".**
- **The rule.** §6 R5: "a gain on hyphenate-character-001/-003/-004 is DEGENERATE by construction **unless `degenerateRetirement` holds**". Retirement holds when U1, U2-p and U3 are on the tree and the `hyphenate-character.geometry.py` row is OK.
- **What adjudicate prints.** On the all-met record, which is a tree where retirement holds (the two iOS keys are gating and PASS by R6), it prints `DEGENERATE-BY-CONSTRUCTION gains (not fixes): 2 — hyphenate-character-001.html ios, -003.html ios` (`skeptic-r3/synth/installed-allmet.m0.adjudicate.out.txt`).
- **The contradiction.** §6's expected totals count both rows in iOS +3 (001, 003, 033), as R4 does.
- **The partial case is right.** After a U2-ios revert it correctly prints only 003 ios (`u2ios-closing`).
- **Fix** (string only):
  - print `DEGENERATE unless degenerateRetirement holds (EXP.degenerateRetirement): read geometry-gate.py's hyphenate-character.geometry.py <cell> key and the units on the tree`;
  - or move the line under R6's read-out. No rule logic changes.

**R3-S3. The fixed flex-gap probe still passes wrong pictures whose error is in a column-rule segment's VERTICAL extent.**
- **Why it passes them.** It pins each segment's horizontal position (red runs on rows y20 / 95 / 145). The vertical extent is pinned only through the red count (±2 % = ±44 px) and the overall bbox.
- **Executed.** `skeptic-r3/gap-fakes-r3.py` builds fakes in the ref's own colours, with the true background taken from the no-rule iOS capture; output in `gap-fakes-r3.out.txt` and `gap-fakes-r3-score.out.txt`:

  | fake | probe | SSIM | floor 0.99 |
  |---|---|---|---|
  | `vshift5`: line 2's segment moved 5 px down (y76-115, painted over the row rule) | **GEOMETRY OK ×3** | **0.9995** | cleared |
  | `line2-short4`: line 2's segment ends 4 px early (y71-106) | **GEOMETRY OK ×3** | **0.9996** | cleared |
  | `short6` (control) | WRONG | — | — |
  | `line1-short4` (control) | WRONG | — | — |

  The control fakes calibrate the ±2 % edge.
- **How it compares with R2-M1.** It is the same class as R2-M1 (the only gate of the GAP-ios / GAP-android HIGH rows passes a wrong picture above the floor). It is less plausible than a gap-index error, so it is should-fix rather than must-fix. A ≤ 5-px error is still well past the ±1 px positional tolerance the probe applies everywhere else.
- **Fix, executed as a stand-alone proposal** (`skeptic-r3/gap-colread-proposal.py` → `.out.txt`):
  - also read the red runs on a COLUMN through each column-rule gap (x65 / x115) against the ref, ±1 px. The ref gives x65 `[(16,60),(121,165)]` and x115 `[(16,60),(71,110),(121,165)]`;
  - on wave53-final and wave54-open: ref and web OK, natives WRONG;
  - `vshift5`, `line2-short4`, the r2 fake and fix-r2's `line3` fake: WRONG;
  - fix-r2's `blue` fake is caught by the existing blue column read.

### NITS

- **R3-N1. The geometry-gate record predates the N5 arrow.**
  - `geometry-gate.wave53-final.out.txt` (19:12) predates the N5 edit to `compose-table-body-cell.geometry.py` (19:23).
  - A re-run differs in exactly one line: 006 android `line:` now carries `→ GEOMETRY WRONG`.
  - Refresh the record.
- **R3-N2. The U3b keep rule reads report rows.**
  - The rule is "no `offset:`/`lines:` reason on **any** iOS row". It therefore reads the report-class `hyphenate-character-004 ios` (MED).
  - The brief's named failure mode for that row is a `lines:` re-wrap from a missing spent-hyphen hunk. `reasonToUnit`'s own parenthetical assigns that to U2-ios ("a glyph-width line count goes to U2-<platform>").
  - So the rule can revert U3b for U2-ios's fault, which also contradicts §6 "Tier" (MED never triggers).
  - Restrict it to the gating 001 / 003 ios rows, or apply the parenthetical.
- **R3-N3. Two silent cases in `control-check.mjs`.**
  - A capture "absent before" is printed `LEAK` but not pushed to `out.leaks`, so it exits 0.
  - A capture present before and missing after is never iterated.
  - R3's column counts make both unreachable in practice.
- **R3-N4. Controls on carriers are gated by nothing at the closing gate.**
  - `geometry-gate.py`'s exit ignores control FAILs ("diagnosed through R4/R7").
  - Nine control keys sit on CARRIERS, which R4 and R7 cannot see (`skeptic-r3/control-on-carriers.out.txt`):
    - the 8 block-ellipsis native "freeze" keys of U3;
    - `block-in-inline-015-print web` (RS).
  - At the probe, rules 1 / 2 bind their scores. At the closing gate, a geometry change that does not lower the score passes everything.
  - Together with R2-N2 (002 android's control expects today's WRONG verbatim), either:
    - say so in R6; or
    - add an exit code (e.g. 4) for a control FAIL on a carrier, after making R2-N2's key a report.
- **R3-N5. The closing gate does not re-read revert rule 2.**
  - Directed non-gating rows (for example block-ellipsis-004/-005/-006 ios/android "byte-identical expected") can fall at the closing gate unflagged.
  - The probe is rule 2's only reader. That is defensible on a deterministic host, but §6 should say it.
- **R3-N6. The A/B arms carry no command.**
  - `abArms` and §8 step 12 give no `ab-diff` order.
  - After R2-M2, pre-register `ab-diff.mjs <arm-run> wave54-final`: exclude first, so Δ = final − arm = the mechanism's share.
- **R3-N7. The fix-r2 pass is uncommitted while seven builder lanes write on the same tree.**
  - Unversioned: `probe-readout.mjs`, `fix-r2/`, the replay pair, and the edits to PLAN / expectations / plan-build / geometry-gate / adjudicate.
  - The memory rule is to commit plan artefacts immediately. That is the orchestrator's call (this round must not commit).

STATUS: COMPLETE

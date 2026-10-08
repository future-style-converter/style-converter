# Wave 54 plan skeptic, round 2

**Verdict: MIXED. Must-fix = yes (2).**
- Every round-1 defect that the fix pass took on is closed by execution: M1, M2, M3, S1–S7, and N2 answered.
- The facts re-hold: cited cells, all seven censuses (re-derived with new scripts, several from the wire rather than the extractor), file-level ownership, and byte-identical regeneration.
- Two new must-fix items:
  - **R2-M1:** the GAP geometry probe passes a wrong picture that also clears its 0.99 floor.
  - **R2-M2:** the plan's stage-2 read-out `ab-diff.mjs wave54-probe wave54-open` prints every change with its sign inverted. Applied as written, revert rules 1 and 2 would revert the commits that fixed cells and keep the ones that broke them.
- Four should-fix items (R2-S1…S4) and eight nits follow.

**Scope and hygiene.**
- I wrote only this file and `tools/titan/results/wave54-plan/skeptic-r2/`: scripts, their `.out.txt` outputs, three 033 fake PNGs, and five composite look PNGs.
- I committed nothing. I ran no Gradle, xcodebuild, emulator, simulator or Chromium.
- `git status --short` lists only `tools/titan/results/wave54-plan/`. HEAD is `f798b255`, which is `7cce3b22` plus the orchestrator's two `wave54-gate/` commits.
- Every node / python run was read-only over run dirs and sources, under `nice -n 19` for the probe runs.
- One read-only extra: I ran `node tools/visual/coverage-audit.mjs` (registered 558/558/558, real android 15 · ios 70 · web 516). `tools/visual/COVERAGE.md` was not rewritten: its mtime is still Sep 14 and the tree is clean.
- Large regenerable JSONs (the `--movers 0` identity score, the synthetic closing scores, the hook expectations copies) were deleted after use. To regenerate them:
  - the identity score: `node tools/titan/score-gate.mjs wave53-final wave54-open --movers 0 --json tools/titan/results/wave54-plan/skeptic-r2/score-identity-movers0.json`;
  - the hook expectations: `python3 tools/titan/results/wave54-plan/plan-build.py --out tools/titan/results/wave54-plan/skeptic-r2/<hook> --reverted tools/titan/results/wave54-plan/skeptic-r2/<hook>/reverted.json`;
  - the synthetic scores: then the `synth*.mjs` scripts.

**Evidence base.**
- `tools/titan/runs/wave53-final` (= `wave54-open`: cells identical, control C2 1435/1435 identical ×3, wire 0 changed).
- The frozen refs `tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`.
- `tools/titan/runs/wave53-probe`.
- The scorer idiom: a cell is scored iff `typeof ssim==='number' && !scoreExcluded`, and passes iff `wptPass===true`.

---

## Check 1: every cited cell, re-derived

**`skeptic-r2/cite-check.mjs`: an independent manifest reader** (the scorer idiom re-typed, no import of score-gate).
- `wave53-final`: **4096 cells, 0 differences from `cells-wave53-final.json`**.
- The strict `<test> <platform> <P|f> <score>` form over PLAN.md and all 12 briefs: **74 OK, 0 mismatches** (`skeptic-r2/cite-check.out.txt`).

**`skeptic-r2/cite-check2.mjs`: a general extractor.** It expands `X/-NNN/-MMM`, `ios / android` and `a / b / c` value lists to cells.
- Result: **212 cells OK, 6 non-equal** (`skeptic-r2/cite-check2.out.txt`). All 6 are parse artifacts, resolved by hand:
  - **PLAN:191** `text-decoration-inset-005/-006/-014 ios/android f 0.8987–0.9238` is a range. The six cells are ios 0.8995 / 0.8987 / 0.9238 and android 0.9 / 0.8993 / 0.923, so min 0.8987 and max 0.9238: it holds.
  - **PLAN:616** `block-ellipsis-002 ios / android | P 0.9813 … / P 0.9884` has its second value after the arrow. ios P 0.9813 and android P 0.9884: it holds.

**`cells.mjs` over `wave53-open → wave53-final → wave53-probe → wave54-open`** (`skeptic-r2/cells-targets.out.txt`).
- `wave53-probe` values, all exact:
  - `css-text/bidi/bidi-lines-001` android P 0.9629, `-002` android P 0.9818;
  - `css-counter-styles/counter-suffix` web P 1 · ios P 0.9873 · android P 0.9793;
  - `CSS2/css21-errata/s-11-1-1b-006` android P 0.9906.
- Every `wave53-final` value equals `wave54-open`.
- The new fix-pass cells hold:
  - `wave53-final css-text/hyphens/hyphens-none-shy-on-2nd-line-001 android P 0.998` / `ios P 0.9988`;
  - `css-overflow/line-clamp/block-ellipsis-002 ios P 0.9813`.

**`plan-build.py` asserts every prediction `from`** against the snapshot. Its rerun exits 0 (check 5).

**Every cited cell holds.**

## Check 2: censuses replayed with my own scripts

Every census below is my own script. Most read the WIRE (`per-test-ir`) rather than the extractor source, so they are independent of round 1's simulations.

| unit | plan | my census (script → output) | holds? |
|---|---|---|---|
| L1 P / M′ | 6 roots in 4 docs; M′ = 4 marker comps; +6 × 15 shadow | `meta.markerText` + ABSOLUTE\|FIXED = **4, all counter-suffix** (`skeptic-r2/l1-census.out.txt`). counter-suffix has 23 components today. `css-counter-styles/tests.list` puts counter-suffix at line 33/48 and the **15 documents after it are all `cssom/*`**, which is the id-shadow population (ids are section-counter suffixes, e.g. `…-632`). P's padded roots are not visible on the per-test wire (no lossy reasons there). I re-verified that the probe tree's bake equals `b0edb788^` (`git diff 7843a523 b0edb788^ -- tools/titan/bidi-bake.mjs tools/titan/bidi-marker-bake.mjs` is empty), so round 1's device wire diff (content 4, renumbered 15) is the census of P+M | yes |
| L2 TB-android | 1 body-root table doc | a body-root whose last `Display` is TABLE/INLINE_TABLE: **1** (`s-11-1-1b-006`) (`skeptic-r2/l7-l2-census.out.txt`). `276757ea` applies clean on HEAD | yes |
| L3 U3 | 54 brs in 18 docs | wire-side: a 20-px `<br>` whose parent's `meta.runs` holds non-whitespace text since the previous child gives **54 brs in 18 docs, the same 18** (`skeptic-r2/br-census-ir.out.txt`) | yes |
| L3 U3b | 12 brs in 4 docs, host-declared | 20-px brs whose parent carries LineHeight/Font: 20 in 4 docs before U3, **12 after U3** (hyphenate-character-001…004). Ancestor-only (inherited reading): **23 brs in 4 docs** (balance-grid-container, baseline-002, baseline-007, column-height-009), as the S4 fix says (`skeptic-r2/br-u3b-all.out.txt`) | yes |
| L3 U1 | 4 docs | the wire's hyphenate-character values: `""` Generic ×2 (001, 002), `\\2022` (003), `\\00a0\\0640` (005), `/-/` (004), `-` ×9 (limit-chars-001) (`skeptic-r2/shy-hc-census.out.txt` (b)) | yes |
| L3 U2 default population | 19 members | **17 documents carry U+00AD; all 17 are an L3 carrier or L3 must-not-move**, 0 outside (`skeptic-r2/shy-hc-census.out.txt` (a)) | yes, S6 closed |
| L4 OOF | android 9, iOS 6, hostFlips 7 | a re-typed `shouldHoistToCanvasRoot` / `rendersInFlowAsStaticPosition` / `anyOutOfFlowBox` plus the `ComponentRenderer.kt:3471` Box branch, with F1–F3: **android 9 · iOS 6 · hostFlips 7 · Box-branch flips 6 (all carriers) · FIXED under a positioned establisher 0**. The document sets are identical to the plan (`skeptic-r2/oof-census.out.txt`) | yes |
| L4 CBB | 10 + cross-fade control (+006 side reader) | a padded content-box element with a definite W/H, and a child that reads the block through a % on a Margin/Padding/inset/size family (following %-sized children): **11 docs = the 10 carriers + `cross-fade-target-alpha` (must-not-move; its child is `Width/Height 100%`, layout constraints); 0 outside L4's lists** (`skeptic-r2/cbb-census.out.txt`) | yes |
| L5 U1-android | 10 android carriers | every h1–h6/sub/sup on the wire: **24 docs, 12 reached** = 10 carriers + inset-011 (fold bails; the control) + subelements-002 (fold members, must-not-move by brief §192) (`skeptic-r2/ua-census.out.txt`) | yes |
| L6 RS | 27 docs | adjacent ROOT pairs, earlier one `ws-after`, both inline-level (the WWS predicate re-typed from `ComponentRenderer.tsx` :1108-1153 / :327): **73 pairs in 27 docs, 0 extra, 0 missing** (`skeptic-r2/rs-census.out.txt`) | yes |
| L7 | 11 `All` docs | **11** (`skeptic-r2/l7-l2-census.out.txt`) | yes |
| fixture net (R8) | — | the 9 gate fixtures carry no h1–h6/sub/sup tag, no establisher with an out-of-flow child, no out-of-flow box under an establisher, no all-auto-inset FIXED box, and no U+00AD. L4/L5/L3-U2 cannot reach the fixture net | yes |

**No radius is under-reported.**

## Check 3: ownership disjointness

- `skeptic-r2/ownership.mjs` parses every `own:` block of §2, brace-expanded (`skeptic-r2/ownership.out.txt`): **0 basename collisions across lanes**.
- Seam anchors re-read at HEAD (`skeptic-r2/anchors.out.txt`):
  - kt :1141 / :1934 / :2653 / :2670 / :7072;
  - swift :343 / :356 / :5011 / :5026;
  - tsx :991 / :1099;
  - extract :11457 / :11459 / :11706 / :11733.
- All 9 registered hunks are ≥ 250 lines apart within each seam file, so they do not overlap.
- The Compose consumers of the predicate are `ComponentRenderer.kt` :2014 and :3473, plus the locals at :887 / :2107. All of them read `CanvasRootHoist.establishesTransformContainingBlock`, so no seam is missing.
- Swift :309 sits in `strippingFixedDescendants` (FIXED only), which is consistent with iOS contain-content-003/-004/-011 being must-not-move.
- The Swift test classes named exist, except the two marked NEW (`OutOfFlowContainingBlockTests`, `UAHeadingFoldGateTests`) (`skeptic-r2/test-names.out.txt`).
- Shared files are none. One wording nit is listed as R2-N8.

## Check 4: geometry probes on wave53-final

**The whole gate on wave53-final** (`skeptic-r2/geogate-final.out.txt`, byte-identical to the plan's `geometry-gate.wave53-final.out.txt` once blank lines are dropped):
- **185 keys: gating 37 all FAIL · control 112 all PASS · report 36 all FAIL**;
- 0 self-check failures, exit 1.

**`--self-test`:** **HOLDS** on the whole run, and lane by lane L1–L7 (`skeptic-r2/geogate-selftest*.out.txt`).

**`wave53-probe --lanes L1,L2`:** 21 keys, gating FAIL 4, names exactly `Mprime, TB-android`, exit 1 (`skeptic-r2/geogate-probe-L1L2.out.txt`).

**Re-checks of the round-1 fixes:**
- **M2:** on round 1's pitch fakes, +1 prints OK and **+2 / +3 / +4 print `band top … (line pitch / line-height)` WRONG**. The wave53-final output is byte-identical to the record (`skeptic-r2/m2-recheck.out.txt`, `skeptic-r2/ua-final.out.txt`).
- **S2:** round 1's partial fake gives **007 WRONG `band rows 222-321`, 008 WRONG `band rows 272-421`** (`skeptic-r2/s2-recheck.out.txt`).
- **S3:** `block-ellipsis-br.geometry.py` gives web 4/4 WRONG on wave53-final, OK on replay B, WRONG on white and on the ref moved down 6 px. I looked at the 002 PNGs (`skeptic-r2/look-be002-ref-web-ios-android.png`):
  - ref: `Line 1 / Line 2 / Line 3…`;
  - web: a stray blank line before `Line 3…`;
  - iOS: `Line 3` with no "…" and a `Line 4`;
  - Android: only two lines.
- **L7 seed probe:** 18 OK on the seeded copy, exit 0. `--naive` gives 9 WRONG, exit 1. A missing directory exits 1.

**New adversarial pictures:**
- **R2-M1** (below): the GAP probe passes a mis-indexed rule segment at SSIM 0.9919.
- `compose-wpt-content-box-cb` and `oof-containing-block` hold. Their green bounding box is ±1, which leaves room for at most a 1-px red rim, and that is raster tolerance.

## Check 5: generator, keys, adjudicator

**Regeneration** (`skeptic-r2/regen.sha.out.txt`):
- `python3 plan-build.py --out <copy>` exits 0, with stdout identical to `plan-build.out.txt`.
- **`expectations.json` sha256 `50906759…ae` and `watchlist.txt` `2557d870…f1` are byte-identical.**

**Watchlist:** `watchlist-check` on wave53-final and wave54-open both give `watch-lines=775 matched-cells(distinct)=781 … unmatched 0` (`skeptic-r2/watchlist-check.out.txt`).

**Keys:**
- Every key `adjudicate.mjs` and `control-check.mjs` read is present, with the right types, in the planning JSON and in two hook JSONs (`skeptic-r2/exp-accesses.out.txt`).
- `skeptic-r2/key-resolve.mjs`: in a `--movers 0` score, **all 651 must-not-move cells and all 118 prediction cells resolve to adjudicate keys, with 0 key collisions**. R5 therefore never silently skips a cell (`skeptic-r2/key-resolve.out.txt`).

**Adjudicator, re-executed:**

| input | result |
|---|---|
| real `score-gate wave53-final wave54-open --movers 0` | FAIL R4 missed 31, R5 651 moved 0 (`skeptic-r2/adjudicate-identity.out.txt`) |
| my all-predicted synthetic | R1–R5 hold, exit 0 |
| every gating row exactly at its floor | R1–R5 hold, exit 0 |
| one row at floor − 0.0001 | FAIL R4 missed 1 |

The synthetic scores come from `skeptic-r2/synth.mjs` and their outputs are `skeptic-r2/synth-*.adjudicate.out.txt`. M1 is closed.

**Hook with Mprime and TB-android reverted** (`skeptic-r2/hook-a/`):
- `geometry-gate.py --exp` on wave53-probe: 7 keys WITHDRAWN, exit 0. On wave53-final it names only P.
- A stale JSON exits 2 with `STALE … needs Mprime`.
- `skeptic-r2/adjudicate-exp-arg.mjs` (the copy with the JSON path as an argument) on a closing score with the withdrawn cells at wave54-open: R1–R5 hold. With counter-suffix web moved +0.0032: `FAIL R5 … MOVED (probe-revert:Mprime)`.
- M3 is closed for geometry. Its prediction-side analogue is R2-S1.

**Control check:** C2 re-run (`control-check wave53-final wave54-open`) gives **HOLDS, 1435 identical ×3, wire 0/0/0**. Its JSON is byte-identical to `wave54-gate/control-calib-identity.json` (`skeptic-r2/control-C2-identity.out.txt`).

## Check 6: predictions

**Gating rows:**
- 34 gating rows, each with a numeric floor and a `to` starting with P. There are no undirected gating rows (both asserted).
- No floor exceeds its prediction (`skeptic-r2/synth.out.txt`).
- The narrowest margins are:
  - counter-suffix ios 0.987 vs 0.985;
  - 006 android 0.9983 vs 0.996.
- The three autopos-ltr floors are vacuous (0.995 < 0.9966), as §6 now says.

**Must-not-move cells per lane:** L1 80 · L2 97 · L3 74 · L4 253 · L5 46 · L6 68 · L7 33.

**L1:**
- It has a device probe before the gate: stage 1 `wave54-pre` (css-counter-styles + css-text, read with `lists-bakes.geometry.py`), which satisfies BACKLOG 0(a)(1).
- P is its own unit, with `revertOrder` P = [Mprime, P].
- No css-tables carrier exists, so the "48/48 identical" stage-1 rule cannot misfire.

**Expected flips:** HIGH/MED-HIGH f→P web 4 · iOS 3 · Android 6 (box-sizing-007/-008/-022, hyphens-out-of-flow-002; hyphenate-character-001/-003 ios, 033 ios; bidi-lines-001, contain-content-003/-011, clip-3, 033, block-in-inline android).

## Check 7: honesty

- "engine" appears only in the rule text, PLAN:172 and `build-workflow.js:39`.
- There are no scratchpad pointers. The only `/tmp` is the device-pool lock, PLAN:1457.
- PNG claims are tied to probe outputs. The two stale sentences are listed as R2-N1.

---

## Round-1 findings: closure

| r1 | status | executed evidence |
|---|---|---|
| M1 movers threshold | **closed** | identity → R4 missed 31; all-predicted / at-floor → hold; floor−0.0001 → FAIL |
| M2 b015 tops | **closed** | pitch +2/+3/+4 WRONG; wave53-final byte-identical |
| M3 probe decisions | **closed (geometry)** | hook-a exit 0, 7 WITHDRAWN; stale exit 2. The prediction side remains open (R2-S1) |
| S1 undirected rows | **closed for the 10 named rows** | `plan-build.out.txt` lists 10; the residue is R2-S4 |
| S2 RS single row | **closed** | partial fake WRONG on 007/008 |
| S3 tally | **closed** | `pictureCorrectTally` 37+2 asserted key by key; 002 ios OUT (PNG agrees) |
| S4 U3b reading | **closed** | text + pin; census 12/4 host, 23/4 inherited |
| S5 control/report | **closed** | self-test HOLDS overall and per lane |
| S6 shy population | **closed** | 17/17 U+00AD docs covered |
| S7 class names | **closed** | every named existing class exists; NEW ones are marked, with the N>0 rule |
| N1, N3–N7 | **open** (nits, untouched) | R2-N7 |

---

## Defects, ranked

### MUST-FIX

**R2-M1. `flex-zero-gap-rules.geometry.py` passes a wrong picture that also clears the 0.99 floor.** It is the only gate of GAP-ios / GAP-android (033 ios/android HIGH).
- **The gap.** The probe compares red/blue pixel COUNTS, red/blue bounding boxes, and red runs on ONE row (y20, flex line 1).
- **Executed** (`skeptic-r2/gap-fake.mjs` → `skeptic-r2/gap-fake.out.txt`). Take the ref and move line 2's single column-rule segment to the other gap: x111-120 → x61-70, rows 71-120. Then:
  - red 2200 px, same bbox; blue 3000 px, same bbox; row-20 runs identical;
  - **`flex-zero-gap-rules.geometry.py ../results/wave54-plan/skeptic-r2/fake-gap` prints GEOMETRY OK on ios and android**;
  - scorer SSIM (`ssim.js` fast) **0.9919 ≥ floor 0.99**.
- **The pictures** (`skeptic-r2/look-033-ref-fake-android.png`): the ref's middle line has its rule at the RIGHT gap, the fake at the LEFT. wave53-final android paints no rules at all.
- **Why it matters.** A gap-index error on a wrapped line is exactly what a "keep zero-extent intervals as positions" change can produce.
- **Fix.**
  - Check the red runs on a row through EVERY flex line against the ref. The ref gives row 20 `[(61,70),(111,120)]`, row ~95 `[(111,120)]`, row ~145 `[(61,70),(111,120)]`.
  - Re-run wave53-final (ios/android WRONG, web OK), the ref self-check, and this fake (it must print WRONG).

**R2-M2. The stage-2 read-out of revert rules 1–3 is inverted, and rule 3 has no reader.**
- **The inversion.** PLAN §6 (:1323) pre-registers `ab-diff.mjs wave54-probe wave54-open --threshold 0.002`. `ab-diff.mjs` is `<excludeRun> <includeRun>` and prints Δ = include − exclude, so this order prints **Δ = open − probe and labels a probe gain `flip P→f`**.
- **Executed on wave 53's own pair** (`skeptic-r2/abdiff-plan-order.wave53.out.txt` vs `skeptic-r2/abdiff-fixed-order.wave53.out.txt`):
  - `css-backgrounds/background-attachment-margin-root-002`, a real f→P gain of wave 53 (`wave53-final css-backgrounds/background-attachment-margin-root-002 web P 1`), prints `flip P→f … P 1 → f 0.339 Δ-0.661` in the plan's order;
  - `css-lists/counter-reset-reversed-nested` ×3 (+0.0494 / +0.0481 / +0.0343) prints Δ−0.05.
- **Consequence.** Rule 1 ("P on wave54-open, f on the probe") and rule 2 ("Δ ≤ −0.002"), applied literally to that output, revert the commits that fixed cells and keep the ones that broke them.
- **The missing reader.** Stage 2 names no executable reader for rule 3 (floors). A gating cell that did not move is absent from ab-diff's list: the M1 hole again, at the probe.
- **Inherited.** Wave 53 carried the same line (`wave53-plan/PLAN.md:1041`) but read its probe with `score-gate.mjs wave53-open wave53-probe` plus a cells list (`wave53-gate/_note.md` "Probe").
- **Fix.**
  - Swap the arguments: `ab-diff.mjs wave54-open wave54-probe --threshold 0.002`.
  - Pre-register the floor read: `score-gate.mjs wave54-open wave54-probe --movers 0 --json …`, then `cells.mjs` over every prediction row (or adjudicate R4 restricted to the probed sections).
  - Add both to `expectations.json` `probeRun`.

### SHOULD-FIX

**R2-S1. A legitimate partial revert leaves gating floors that cannot be met, and rule 3 then cascades into U3.**
- **The hook.** `probe_decisions()` withdraws a prediction only when ALL of its units are reverted. It withdraws geometry keys by `requires`, but not predictions.
- **U2-ios revert** (`skeptic-r2/hook-u2ios/`):
  - `hyphenate-character-001/-003 ios` stay **gating at 0.965** while their geometry keys are WITHDRAWN.
  - The U3-only pictures are replay B: 001 ios 0.9304 f, 003 ios **0.9566 P** (`hyphenate-character.replay.out.txt`). I looked at the 003 picture: hyphens where the ref paints bullets (`skeptic-r2/look-hc003-ref-ios-iosB.png`).
  - On a closing score built from them, adjudicate prints **`FAIL R4 gating predictions 34, missed 2` → do not ship** (`skeptic-r2/hook-u2ios/adjudicate.out.txt`).
- **U1 revert.** The same holds (`skeptic-r2/hook-u1/plan-build.out.txt`: geometry keys withdrawn, 0 predictions).
- **The cascade.** At the re-probe, rule 3 on 003 ios plus rule 6 (latest first) reverts U3b and then U3. That contradicts §2 L3 ("a later revert of U1 or U2-<p> keeps that platform's 001/003/004 cells under `degenerateByConstruction`").
- **Same class.** L6 step-0 `V3 ≠ 46` ("the -002 web row is withdrawn") has no mechanism either. The gating W1 row (floor 0.995) stays unless it goes through `REVERTED`.
- **Fix.**
  - Demote (or require a `RESTATE` entry for) every gating prediction one of whose `requires` / units is reverted, asserted in `plan-build.py`.
  - Say in §6 / §8 that W1-not-built is entered in `REVERTED` (run `step0`).

**R2-S2. `geometry-gate.py` exits 0 when a GATING key is UNMEASURED.**
- **Executed:** `geometry-gate.py wave53-probe --base wave53-open --lanes L5` prints 15 keys UNMEASURED, including `block-in-inline-015-print android [GATING -> revert U1-android]` (`MISSING`), and **exits 0** (`skeptic-r2/geogate-probe-L5.out.txt`).
- R6 reads "exits 0: every gating key … PASS". R3 covers missing captures at the closing gate, but a key whose line is absent also lands in UNMEASURED.
- **Fix.** Exit non-zero on any UNMEASURED gating key outside `--self-test`, or restate R6 as "exit 0 and UNMEASURED-gating 0".

**R2-S3. The `--movers 0` score of record breaks the corpus snapshot.**
- `score-final.json` now lists every same-verdict cell: **4096 movers** on the identity pair (`skeptic-r2/score-identity-movers0.head.txt`).
- `tools/titan/results/wave52-gate/make-corpus.mjs` publishes `movers: record.movers.length` under a hardcoded `--movers 0.005` scorer string. `corpus-v6-19.json` `perCellDiff` = `{movers: 13, scorer: … --movers 0.005}`.
- `scoreReadout` writes no JSON. A v6-20 built the usual way from `score-final.json` would publish ≈4000 "movers" under a 0.005 label.
- **Fix.** `scoreReadout` also writes `--json tools/titan/results/wave54-gate/score-final.movers0005.json`, and §8 step 13 names it as the snapshot's source.

**R2-S4. Rows marked "up or unchanged" without a replay still bind rule 2, and through it they can revert U3 (S1 residue).**
- **The rows.** `css-multicol/column-height-009`, `balance-grid-container` and `css-masking/clip-path/clip-path-filter-order` ×3 (LOW, "f → up or unchanged"), plus `backdrop-filter-clip-rect / -edge-clipping / -paint-order` android (LOW).
  - None has a replay: hyphenate brief §9 says only "f movers, LOW", and `hyphenate-character.replay-b-radius.out.txt` covers only block-ellipsis.
  - All 12 rows are `up-or-stay`. U3, which carries 6 gating rows, is reverted on any Δ ≤ −0.002.
- **Looked at** (`skeptic-r2/look-column-height-009-ref-web-ios-android.png`):
  - The ref is a two-row column-height layout.
  - Web paints 3 balanced columns with a 20-px blank line between letters.
  - The natives paint one column.
  - Removing 15 brs re-balances those columns. Its sign is not knowable without a replay.
- **Same doubt elsewhere.** The plan's own B arm shows U3 alone FALLING on `hyphenate-character-004` ios (0.9134 → 0.9012) and android (0.905 → 0.8986).
- **Fix.** Replay B on their own pixels for these 12 cells (as done for block-ellipsis), or mark them `undirected` with a `directionWhy`.

### NITS

- **R2-N1. Stale sentences.**
  - PLAN:189 (L3 lane row): "5 P→P picture-correct (block-ellipsis ×4 web, -002 ios)" contradicts §1, where 002 ios is OUT and the count is 4.
  - The L3 predictions header (PLAN:601-602): "every other row … rules 1/2 bind it" is false for the two undirected L3 rows.
  - §6's M1 text says "R4 reads … R5 applies" with adjudicate's numbering, beside a table whose R4/R5 are the capture control and gains. Write "adjudicate R4/R5".
- **R2-N2. A control that fails loudly on an improvement.** The control `block-ellipsis-002 android` expects today's `GEOMETRY WRONG (2 bands vs ref 3)` verbatim, but it is a U3 carrier predicted "identical or up", so a fix prints a loud `!c` CONTROL FAIL. That cell is also a DEGENERATE pass today: `Line 3…` is missing in `skeptic-r2/look-be002-ref-web-ios-android.png`. It is not in `stayDegenerateEvenIfPass`.
- **R2-N3. Stale wave-53 strings in `wave54-gate/adjudicate.mjs`.**
  - The header says "Wave 53's closing-gate adjudication" and the usage says `wave53-open → <final>`.
  - It prints "WITHDRAWN at wave53-probe" (`skeptic-r2/hook-a/adjudicate.out.txt`).
  - The "absent key = unchanged" comment is out of date.
  - §6 promised that "run-id strings in messages" would change. This is the orchestrator's file.
- **R2-N4. No A/B arm behind a stated claim.** L4's invariance on its shared cells (`backdrop-filter-clip-rect` / `-edge-clipping`, `anchor-center-safe-rtl`) "rests on … the closing A/B" (§2 L4, §4), but `abArms` has no L4 arm.
- **R2-N5. Gradle filters can silently match nothing.** The N>0 rule (`build-workflow.js` rule 4) covers xcodebuild only. A Gradle multi-`--tests` run whose NEW class (`TableCellHugTest`, `OutOfFlowContainingBlockTest`, `UAElementFontRuleTest`, `UAHeadingFoldGateTest`, `HyphenateCharacterExtractorTest`) is absent matches nothing for that pattern and stays green.
- **R2-N6. "Identity by construction" is not literal for U2-android.**
  - U2-android moves soft-hyphen detection to `SoftHyphenCuts.took` for EVERY run, not only non-default ones (hyphenate brief §U2-Compose). The 17 U+00AD documents are all carriers or must-not-move, so drift is caught as a leak; add an equivalence pin over the 13 non-carrier runs.
  - `hyphenate-limit-chars-001`'s 9 `"-"` runs reach U2-android's Extractor. The cell is must-not-move, with no pin.
- **R2-N7. Round-1 nits still open.**
  - N1: PLAN:291 "+95 / −4" (91 / 4).
  - N3: PLAN:1068 and `plan-build.py:634` glob `__00{0..5}_*.png` (66 matches today, 0 seeds; the 18 names are in `label-chrome-all-reset.seeded-77fe41e8/`).
  - N4: tsx :1105-1158 (the WWS branch is :1108-1153).
  - N5: the missing "→" in `compose-table-body-cell.geometry.py`.
  - N6: the `integrate-seams.sh` header.
  - N7: file sizes, plus `tools/titan/bidi-bake.test.mjs` 758 lines, which L1 extends.
- **R2-N8. A directory where file names belong.** L5's `own:` names a directory, `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/`. That directory contains L3's `typography/wrapping/PreBreakPipelineTest.kt`. Name the files.

STATUS: COMPLETE

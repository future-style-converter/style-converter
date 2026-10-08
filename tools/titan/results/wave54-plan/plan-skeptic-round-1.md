# Wave 54 plan skeptic, round 1

**Verdict: MIXED.** The plan's facts hold:
- every cited cell;
- every census radius, re-derived with my own scripts;
- file-level ownership;
- the generator's byte-identity;
- every geometry probe's ability to fail on today's pictures.

Three tooling defects make the pre-registered closing rules unsatisfiable or unsound, so **must-fix = yes** (§Defects M1–M3).

**Scope and hygiene.**
- I wrote only this file and `tools/titan/results/wave54-plan/skeptic-r1/`: scripts, their `.out.txt` outputs, and four composite PNGs I looked at.
- I ran no Gradle, xcodebuild, emulator, simulator or Chromium, and committed nothing.
- `git diff --name-only 7cce3b22 HEAD` touches only `tools/titan/results/wave54-gate/`. Those are the orchestrator's two commits made during this audit: `3db12aff` scored the opening gate, and `f798b255` added the post-opening checks.
- HEAD is now `f798b255`. The opening gate reproduced `wave53-final` to the last digit (`wave54-gate/_note.md:17-18`), so every check below against `wave53-final` is also a check against `wave54-open`.

**Evidence base.**
- The gate run `tools/titan/runs/wave53-final`, scored with the scorer idiom: a cell counts iff `typeof ssim==='number' && !scoreExcluded`, and passes iff `wptPass===true`.
- The frozen refs `tools/wpt/refs/9b5435e…/white-black-ink-font-lh-imgpad-htmlpins-rootbg-uamargin/`.
- The probe run `tools/titan/runs/wave53-probe`, which holds the pictures of the two reverted wave-53 units.

---

## Check 1: cited cells, re-derived

**Method.**
- `skeptic-r1/cellidx.mjs` is an independent manifest reader; it does NOT import score-gate.
- It gives `wave53-final` 4096 cells: web 1232/1372, iOS 1125/1362, Android 1115/1362. That is 0 differences from `cells-wave53-final.json`.
- `wave53-open` gives 1229 · 1119 · 1111, also 0 differences.
- 1435 per-test IR documents.

**Citation extractors over PLAN.md and every brief.**
- `cite-check.mjs` (strict `<test> <platform> <P|f> <ssim>` form): **255 OK**.
- `cite-check-slash.mjs` (`<tests> ios / android | P a / b` form): **39 OK, 0 mismatch**.
- The run-prefixed canonical form: **3 OK** (`cite-canonical.out.txt`).

**The 30 strict-extractor "mismatches", each resolved by hand with `cells.mjs`** (`cells-targets.out.txt`, `cells-misc.out.txt`):
- Most are slash rows the strict parser mis-attributes. Examples:
  - PLAN:591 `001 / -003 web | f 0.935 / 0.8812`;
  - PLAN:752-754 `ios / android | P 0.9594 / 0.9589`;
  - PLAN:600 `P 0.959-0.9595 / 0.956-0.9578`, which is web 0.959 / 0.9595 / 0.9595 and iOS 0.956 / 0.9578 / 0.9578.
- The `wave53-open` column of `hyphenate-character-compose.md:41-43` reads android 0.9136 / 0.9168 / 0.9041. Those are exactly the `wave53-open` values.
- Range claims:
  - `line-clamp-001…007 android P 0.9701–0.982` (min 0.9701, max 0.982);
  - `inset-005/-006/-014 ios/android f 0.8987–0.9238`.

**Every cited cell holds.** That includes the §5 singletons, the L5 movers, the L6 at-risk passes and the box-sizing P→P classes.

**`wave53-probe` citations** (`cells-targets.out.txt`), all exact:
- `bidi-lines-001` android P 0.9629;
- `-002` android P 0.9818;
- `counter-suffix` web P 1, iOS P 0.9873, android P 0.9793;
- `s-11-1-1b-006` android P 0.9906.

**`expectations.json`:**
- all 118 prediction `from` values equal `wave53-final`;
- all 649 must-not-move lines are scored cells;
- no must-not-move line is a carrier.

## Check 2: censuses replayed with my own scripts

| lane · unit | plan's radius | my census (script → output) | holds? |
|---|---|---|---|
| L1 P + M′ | P: 6 roots in 4 docs, no id shadow. M′: +6 components, so 15 cssom documents renumbered | `wire-diff.mjs wave53-final wave53-probe` (the probe tree carried hunks P and M): **content 4** (counter-suffix 23→29, bidi-lines-001/-002, anchor-center-safe-rtl), **renumbered 15** (all `cssom/*`), 936 identical. `tests.list` puts exactly those 15 after counter-suffix | yes, device-measured |
| L2 TB-android | 1 body-root table; 22 declared + 38 ua-fold table documents | `table-census.mjs`: 1 (`s-11-1-1b-006`); 22 declared | yes. `276757ea` touches only L2-owned files; `git apply --check` is clean on HEAD |
| L3 U3 | 54 brs in 18 docs | `br-census.mjs`, my own simulation of `buildNode`'s BR-LINE-CONTEXT (`extract-fixture.mjs` :11457-11466 / :11510-11534 / :11706): **54 brs in 18 docs**, the same 18 | yes |
| L3 U3b | 12 brs in 4 docs | the same 12 brs in 4 docs (`hyphenate-character-001…004`, 20 → 19.2) under the HOST-DECLARED reading; see S4 for the inherited reading | yes, see S4 |
| L3 U1 | 4 docs (001, 002, 003, 005) | `hc-census.mjs`: `""` ×2 (Generic `_unmapped`), `\2022`, `\00a0\0640`. 004 `/-/` and limit-chars `-` are unchanged | yes; wire 18 ∪ {005} = 19 |
| L3 U2 default-argument population | 16 css-text/hyphens + 2 block-ellipsis | `shy-census.out.txt`: 17 documents carry U+00AD on the wire | one omission (S6) |
| L4 OOF | android 9 captures, iOS 6, hostFlips 7 | `oof-census.mjs`, with the PLAN rule table exactly (the scout's `otherCB` also counted container-type): **android 9, iOS 6, hostFlips 7** (same documents). 0 FIXED boxes under a self-positioned establisher | yes |
| L4 CBB | 10 cbborder + flex-gap-decorations-006 | `cbb-census.mjs`, WITH propagation through %-sized descendants and every `LocalContainingBlock` reader: **12 documents** = the 11 plus `cross-fade-target-alpha`, which the plan holds must-not-move as the in-flow control (in-flow % = layout constraints). A first broader pass listed Transform / BackgroundImage % readers; reading `DynamicValueResolver.needsResolution` (:260) excluded them | yes |
| L4 GAP | 033 only; 17 gapzero containers | `gap-census.mjs`: 10 FLEX containers with a zero gap on a decorated axis, all in css-gaps; multicol 0 | yes; css-gaps is must-not-move |
| L5 U1-android | 10 android carriers | `ua-census.mjs`: 12 docs restyled. The other 2 are inset-011 (fold bails, the control) and subelements-002 (fold members), both must-not-move by the brief's argument | yes |
| L6 RS | 27 docs | `rs-census.mjs`: 73 pairs in **27 docs**. Pairs from the widget branch alone: **0** | yes |
| L6 W1 | 4 hosts in 1 doc; 12 inert members | `w1-census.mjs`: 21 out-of-flow run members (the brief's 24 adds 3 floats), **12 inert**, **4 joins in 1 doc** | yes |
| L7 | 11 `All` documents ×3 | `all-census.out.txt`: 11 | yes |

## Check 3: ownership disjointness

- `ownership.mjs` parses every "own:" block of PLAN §2 (brace-expanded): **0 files shared between lanes** outside the four seam files.
- I listed basenames by hand to confirm: no duplicate basename across lanes.
- The seam registry holds 9 patches:
  - kt: L2 :2653/:2670, L3 :7072, L4 :1934, L5 :1141;
  - swift: L3 :5011/:5026, L5 :343-356;
  - tsx: L6 :1099;
  - extract: L3 :11706 and :11457.
- Every anchor re-read at HEAD matches (`sed -n` reads in this session). So do the L4 call-site anchors: `CanvasRootHoist.kt` :222 / :340 / :389 and `FixedHoist.swift` :309 / :347 / :115.
- The Compose consumers of the OR at :222 are `ComponentRenderer.kt` :2014 and :3473. Both call `CanvasRootHoist.establishesTransformContainingBlock`, so no seam is needed.
- `a8ffd1c6` touched `ListMarkerOutsideHang.swift`; D18 hands that comment to the orchestrator.

## Check 4: geometry probes on wave53-final

**`geometry-gate.py wave53-final --base wave53-open`** (`geogate-final.out.txt`) is byte-identical to the plan's record:
- 140 keys: PASS 82, FAIL 58;
- **all 33 gating keys FAIL**;
- 0 self-check failures.

**Each of the 12 probe commands run alone** (`probe-selfcheck.out.txt`):
- every one exits 0;
- every one has a ref self-check that exits 1 on a bad ref row;
- every ref row is OK.

**`wave53-probe --lanes L1,L2`** is identical to the record (`geogate-probe-L1L2.out.txt`). It names exactly Mprime and TB-android. `rtl-geometry.wave53-probe.out.txt` shows the `[P]` rows OK on device.

**Adversarial pictures** (`adversarial-geometry.py` → `adversarial-geometry.out.txt`). I built synthetic captures and ran every probe on them through the gate's own key matching:
- **white:** every gating key FAILs.
- **the ref itself:** every gating key PASSes. The exception is the `[M]` / lists-bakes keys, which FAIL by design on their `rows 0-207 identical to <base>` clause; the `[M]` verdict itself prints OK.
- **ref shifted down 6 px:** 6 gating keys PASS:
  - `[P] bidi-lines-002` android;
  - `block-in-inline-015-print` android;
  - `box-sizing-007` / `-008` / `-022` web;
  - `hyphens-out-of-flow-002` web.

**Does the SSIM floor catch those pictures?** Scored with the scorer's call (`ssim.js { ssim:'fast' }`; `shift-ssim.out.txt`):
- for 5 of the 6, it does from a 2-3 px shift;
- for `block-in-inline-015-print` it does not: a wrong line pitch passes both the floor and the probe (M2).

I looked at the pictures (`skeptic-r1/look-*.png`, crops of the ref and the captures). Each defect the plan names is what the pixels show:
- **counter-suffix:** web markers on the left, no native markers, Android text +48 px; at `wave53-probe`, Android markers one run low with the digits missing.
- **006:** Android square at rows 51-70; at `wave53-probe`, an empty 1-px outline plus a solid square at x44-63.
- **block-in-inline-015-print:** Android at the 16-px regular face.
- **contain-content-003:** Android bare red, with green at the canvas top-right.
- **033:** Android rules missing.
- **box-sizing-007:** web.
- **hyphens-out-of-flow-002:** web box 4 reads `highway` on one line.

## Check 5: the generator and the keys the adjudicator reads

**`plan-build.py` regenerates byte-identically.** I ran it in a copy directory (`skeptic-r1/regen/`, ROOT patched):
- `expectations.json` sha256 `46c339a5…`;
- `watchlist.txt` sha256 `8ef1b789…`;
- stdout is identical to `plan-build.out.txt` (`regen.out.txt`).

**`watchlist-check` against `wave53-final`:** `watch-lines=773 matched-cells(distinct)=779 … unmatched 0`, identical to the record.

**Keys** (`exp-keys.mjs`). The JSON parses and carries every key that `adjudicate.mjs` and `control-check.mjs` read:
- `expected.{lost,unmeasuredNow,newlyMeasured}` = [];
- `degenerateByConstruction` (3);
- `lanes[*].{predictions,mustNotMove,captureCarriers,wireCarriers}`;
- `unionCaptureCarriers` = web 44 · iOS 27 · Android 51, equal to the per-lane union;
- `unionWireCarriers` 23;
- 34 gating rows, each with a numeric floor and a `to` starting with `P`.

`probeDecisions` is absent at planning. Both readers default it with `|| []`, so that is fine.

**The REVERTED hook** (my rerun with Mprime, TB-android and U3b reverted) withdraws:
- counter-suffix web/iOS;
- 006 android;
- nothing for U3b.

But it leaves `geometryProbes` untouched (M3).

**The committed `wave54-gate/adjudicate.mjs` on an all-predictions-met synthetic score** (`synth-score.mjs`; `synth-score.adjudicate.out.txt`): **R4 FAIL, "do not ship"** (M1).

## Check 6: predictions

- 34 gating rows; every HIGH and MED-HIGH row is gating with a floor.
- Every lane has must-not-move cells: 80 · 97 · 72 · 253 · 46 · 68 · 33.
- L1 has a pre-gate device probe (stage 1 `wave54-pre`: css-counter-styles + css-text, `stage1Decision`), and P is its own unit with `revertOrder` P = [Mprime, P].

**Expected movement checks out:**
- f→P HIGH / MED-HIGH flips: web 4 · iOS 3 · Android 6, giving 1236 · 1128 · 1121;
- ceiling with MED and MED-LOW: 1239 · 1133 · 1125.

**Floors against today's scores** (`floors.out.txt`): three floors sit at or below today's value, the `abspos-autopos-{htb,vlr,vrl}-ltr` android rows (N2).

## Check 7: honesty

- "engine" appears only in the real path `runtimes/web/src/engine/…` and in the rule text.
- There are no scratchpad pointers. The one `/tmp` string is the device-pool lock command, PLAN:1398.
- PNG claims are tied to probe outputs and cells.
- The picture-correct tally overreaches (S3).

---

## Defects, ranked

### MUST-FIX

**M1. The closing adjudicator cannot pass when the plan is exactly right.**
- **The mechanism.** The score of record (§6; `expectations.scoreOfRecord`) runs with `--movers 0.005`. `score-gate.mjs:125` lists a same-verdict cell only when |Δ| ≥ the threshold. `adjudicate.mjs` R4 (committed `wave54-gate/adjudicate.mjs`) treats an absent gating cell as "did not move" and sets `ok = false`.
- **The four P→P gating rows predicted below 0.005:**
  - `abspos-autopos-{htb,vlr,vrl}-ltr` android: P 0.9966 → 0.9967, Δ 0.0001 (HIGH);
  - `s-11-1-1b-006` android: P 0.9944 → 0.9983, Δ 0.0039 (MED-HIGH).
- **Executed.** With every gating row set to its predicted value: `FAIL R4 gating predictions 34, missed 4 … ADJUDICATION: 1 rule(s) broken — do not ship` (`synth-score.adjudicate.out.txt`).
- **Two related holes:**
  - `counter-suffix` iOS landing at its own floor 0.985 (Δ 0.0048) would also be "unchanged".
  - R5's "within 0.002" cannot see a must-not-move move in (0.002, 0.005).
- **Fix.** Produce the adjudicated JSON with `--movers 0` (R5 keeps its own 0.002 rule). Restate §6 and `scoreOfRecord`. Re-run the four `controlCalibrations` before trusting the change.

**M2. `ua-heading-face.geometry.py`, the only geometry gate of the HIGH row `block-in-inline-015-print` android, passes a wrong picture that also clears the floor.**
- **The gap.** The probe checks band count, heights and right edges, and never band tops (the ref shifted down 6 px prints OK).
- **Executed** (`b015-pitch.mjs`): the ref with a 2-px-per-line pitch error (line 4 is 6 px low) scores **0.9709 ≥ floor 0.97** and prints `→ GEOMETRY OK` (`b015-pitch.out.txt`).
- **Why it matters.** A line-height or face mismatch on Android is exactly the plausible failure of U1-android.
- **Fix.** Add a top check per band, within ±3 px of the ref. The iOS anchor's tops 30/66/105/144 against the ref's 29/65/103/142 still pass. Then re-run the self-check, the `wave53-final` WRONG line and the pitch fakes; pitch ≥ +2 must print WRONG.

**M3. `geometry-gate.py` ignores probe decisions, so R6 "exits 0" is unsatisfiable after any legitimate revert.**
- **Executed** (`regen.out.txt`): `plan-build.py` with `REVERTED=[Mprime, TB-android, U3b]` leaves every lane's `geometryProbes` byte-identical. The gate (copy reading those expectations) still marks `[M]` ×3 / lists-bakes ×2 → Mprime and 006 ×2 → TB-android as GATING.
- **Result.** On the pictures those cells return to, it exits 1 and "names" units that are already gone. Those pictures are `wave53-final`'s, where these keys FAIL (`geogate-final.out.txt`).
- **Why it matters.** M′ and TB-android are the two units with the most likely stage-1 revert.
- **Fix.** `probe_decisions()` demotes a reverted unit's gating keys to report (or `geometry-gate.py` reads `probeDecisions.reverted` and prints WITHDRAWN). R6 then reads "every gating key of a unit still on the tree". For counter-suffix android, the key stays P's floor only.

### SHOULD-FIX

**S1. Rows that predict a move with no direction can revert HIGH units through rule 2.**
- Rule 2 reverts the carrying commit on Δ ≤ −0.002 on any row, and PLAN §6 says "No prediction in this plan forecasts a fall". But these rows predict a move of unknown sign (`movers.out.txt`):

  | row | unit at stake |
  |---|---|
  | `contain-content-004` android ("moves") | OOF-android |
  | `flex-gap-decorations-006` android ("may move") | CBB-android |
  | `counter-list-item`, `subelements-003`, `backdrop-filter-border-radius-change` / `-corner-shape-change` android ("moves") | U1-android |
  | `hyphenate-character-002` iOS, `hyphenate-limit-chars-001` iOS ("mover") | U2-ios |
  | `display-flow-root-list-item-001` web ("mover") | RS |

- The OOF brief §10 R1 names 004's in-flow `<span>FAIL</span>` as the risk that would push it.
- **Fix.** Pre-register a direction with its evidence, or exempt undirected LOW movers from rule 2 and keep rules 1 and 5.

**S2. `web-root-separator.geometry.py` reads one pixel row, so it passes a partial fix.**
- With a separator only on the probed atom band, it prints `edge x151 → GEOMETRY OK` for 007 and 008 (`rs-partial.out.txt`).
- The floor catches both (0.9118 < 0.975; 0.9209 < 0.965), so the pair holds today.
- **Fix.** Check the edge on every atom row band.

**S3. The picture-correct tally (§1: 38 cells) counts cells no geometry key gates.**
- `block-ellipsis-002` iOS is a NATIVE cell. The BACKLOG "Wave 53 lessons" forbid a native picture-correct label without the family's probe.
- `block-ellipsis-002/-004/-005/-006` web are gating rows with geometry `null`.
- `box-sizing-020/021/024/025` web have no representative key. `010` stands in for the 011/014-019 class.
- **Fix.** Add the keys, or take those cells out of the tally.

**S4. U3b's "a host whose cascade declares `line-height`" is ambiguous.**
- Host-declared: 12 brs in 4 docs, which is the plan's number.
- Inherited: 23 more 20-px brs in 4 docs (`br-census-all.out.txt`), two of them NOT carriers: `css-multicol/baseline-002` (2em) and `baseline-007` (40px).
- [W-L3] would catch it, but no pin does.
- **Fix.** Say "declared on the host itself", and add a pin that baseline-007's brs stay 20.

**S5. `geometry-gate.py` labels every non-gating key "control/report", and 25 of them FAIL today.**
- They are MED targets (`geogate-map.out.txt`).
- `build-workflow.js` rule (5) and PLAN §0 tell every lane to "confirm … every control PASSES".
- **Fix.** Tag keys `control` (expected OK today) or `report` (expected WRONG today), and restate rule (5).

**S6. The L3 must-not-move population misses one document.**
- It omits `css-text/hyphens/hyphens-none-shy-on-2nd-line-001` android P 0.998 / iOS P 0.9988.
- That document carries U+00AD; the brief's table lists its sibling `hyphens-none-011`.
- R4 covers it, but the enumeration is incomplete. **Fix:** add the two cells via `plan-build.py`.

**S7. L4's Catalyst filter names a test class that does not exist.**
- `-only-testing:` `GapDecorationLinesTests` is not in `runtimes/swiftui/Tests/StyleConverterRuntimeTests/`, so the filter can run zero tests and report green.
- **Fix.** Name the existing classes, or have the lane create the class and assert the executed test count is above 0.

### NITS

- **N1. The bidi-bake delta.** PLAN §2 L1's "+95 / −4" is wrong. `git diff --numstat a8ffd1c6^ b0edb788^ -- tools/titan/bidi-bake.mjs` gives 91 / 4; 95 is the `--stat` total.
- **N2. Three vacuous floors.** The autopos-ltr ×3 android floors (0.995) sit below today's 0.9966. Geometry is the whole gate there; say so.
- **N3. The L7 baseline glob.** `tools/visual/baseline/{Android,iOS,web}__00{0..5}_*.png` matches 66 committed baselines of four other fixtures, while the 18 seed names exist 0 times today. List the 18 files exactly.
- **N4. The RS lift range.** It is written as :1105-1158, which includes the widget branch at :1105-1107. The WWS branch is :1108-1158. That branch adds 0 root pairs, so the radius does not change.
- **N5. A missing separator.** `compose-table-body-cell.geometry.py` prints its WRONG line without the "→".
- **N6. `integrate-seams.sh`'s header.** It says "nothing lands half-applied", but a mid-run failure leaves the earlier patches applied.
- **N7. File sizes.**
  - `label-chrome-tripwire.test.mjs` is 209 lines. Make `label-chrome-check.mjs` mandatory for U1's new logic.
  - `UAElementFontRule.swift` is 274 lines; the doc edits approach the 300-line split line.

STATUS: COMPLETE

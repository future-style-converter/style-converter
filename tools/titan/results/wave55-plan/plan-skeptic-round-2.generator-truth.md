# Wave 55 — plan-skeptic round 2, generator-truth

**Target.** The generator (`plan-build.py`) and everything it emits (`expectations.json`, `watchlist.txt`,
`plan-build.out.txt`), attacked by execution after the round-1 fix pass (`fix-r1.done.md`). Every round-1 generator-truth
defect marked fixed is re-checked (§11).

**State.** Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`.

**Inputs as read (sha256, unchanged from the first read to the last):**

| file | sha256 |
|---|---|
| `expectations.json` | `a1fdca71…` |
| `watchlist.txt` | `4b1a9cd5…` |
| `cells-wave54-final.json` | `c9c148e7…` |
| `PLAN.md` | `ddaa6955…` |
| `landing-manifest.json` | `90f077b0…` |
| `plan-build.py` | `dacc884a…` |

**Run of record.** `tools/titan/runs/wave54-final`. I never read `wave55-open`.

**My own scripts and their outputs** are in `plan-skeptic-round-2.generator-truth.d/`:
- `scripts/`: the scripts;
- `out/`: their outputs;
- `probes-wave54-final/`: the 13 probe read-outs.

Every census is my own code over `sections/*/per-test-ir`, the captures and the frozen refs. No brief's census script was
run. Scratch paths are redacted to `<scratch>`.

**Verdict.** The generator is faithful and reproducible:
- **no must-fix**;
- every carrier set re-derives;
- every "from" matches;
- the partition has no hole;
- every key prints its class;
- the watchlist is exact;
- floors are 0.95;
- the round-1 fixes are in.

**Two should-fix defects.** Both are consistency defects in generated decision text and in what it feeds a reader:
- **S1:** stage-1 decisions attribute other lanes' moves by section;
- **S2:** `degenerateRetirementCheck` lets `adjudicate.mjs` R6 retire the two Android inset gains that
  `stayDegenerateEvenIfPass` forbids.

**Six nits**, N1–N6.

---

## 1. Reproduction (executed)

| check | command | result |
|---|---|---|
| snapshot = scorer | `node tools/titan/results/wave52-gate/cells.mjs 'html' wave54-final` vs `cells-wave54-final.json` | 4096 cells, 0 differences; totals web 1243/1372 · ios 1131/1362 · android 1128/1362 equal |
| stray wave-54 snapshot | `ls tools/titan/results/wave54-plan/cells-*` | only wave53-final / wave53-open / wave54-open, no `cells-wave54-final.json` there (the wave-55 copy writes beside itself) |
| regeneration, shared tree | `python3.12 -B plan-build.py --out <scratch>/regen` | exit 0; `expectations.json`, `watchlist.txt` **byte-identical**, report == `plan-build.out.txt`; `git status --porcelain` identical before/after, `find tools/titan/results -newer <marker>` empty (`out/regen.out.txt`) |
| regeneration, clean checkout | `git worktree add --detach <scratchpad>/gt2-wt HEAD` + rsync of `wave55-plan/` + symlink `runs/wave54-final`; `plan-build.py --out`, `plan-prose.py --check`; `git worktree remove` | exit 0; all three outputs **byte-identical**; `plan-prose --check: HOLDS`; worktree removed (`out/wt-regen.out.txt`, `out/wt-prose.out.txt`) |
| format | `json.dumps(e, indent=1, ensure_ascii=False) == file` | True (generated, indent 1) |
| prose | `plan-prose.py --check` (shared tree) | HOLDS: 36 gen blocks · 30 decision ids · 46 wave54-final citations · 28 landing-order units; PLAN.md sha unchanged |
| manifest | `landing-manifest.json` units vs `revertUnits` + `contingencyUnits` | 29 = 29, every `revertOrder` equal |

## 2. Each lane's carrier census, re-derived with my own scripts

| lane | published | mine | verdict |
|---|---|---|---|
| L1 NB-ios | 60 text comps / 19 docs (40 own, 20 inherited); 14 iOS carriers; 5 SAME | IR walk with `InheritedText`'s merge (slot.parent chain, the iOS inherited-type set): **60 / 19, 40 / 20**, same 19 docs (`out/l1.out.txt`). Pixel check of the MOVES model on wave54-final: boundary-shaping-001 iOS pitch 42 vs web 41 (42 → 41); dotted-002 iOS pitch 158 vs web 151 (box 112 vs Arial 105; NB-ios → Inter 111); boundary-shaping-010 iOS pitch 44 (SAME holds: ceil 44 = Blink 44); anonymous-table-ws-001 iOS bands == web bands | **holds**. Two refinements, N3 / N6: (a) with `GlobalExtractor.applyingAllReset` applied, all-prop-001's two runs are not declared-normal → the runtime radius is **58 / 18** (`out/l1-allreset.out.txt`); (b) 12 of disclosure-styles' 18 runs are VERTICAL_LR / RL, declined by the D22 guard too |
| L2 U1 | 33 boxes / 14 docs | CB threaded twice (frozen subtracts bands; WPT subtracts only for BORDER_BOX), readers oof / spc / vtext / dyn: **33 / 14, identical box ids** (`out/l2.out.txt`) | **holds** (mutation: counting nested PERCENT, e.g. Transform translate, gives 51 / 18; `needsResolution` reads top-level shapes only) |
| L2 U2 | 122 % axes, 2 carriers | 122 (Width 76 + Height 46); non-zero own band: exactly clip-3 `0-172` W / H, band 20 (`out/l2u2.out.txt`) | **holds** |
| L3 | U1 53 parents / 65 oof children / 17 docs, STACK 3 docs; U2 11 RTL parents / 7 docs (7 inactive, 4 active) | my host model (`hostActivates` = any hoisted or static-position oof box; positioned / transformed / clipped flags): **53 / 65 / 17**; STACK dir-style-02a, multicol-004, multicol-nested-001; U2 **11 / 7 (7 / 4)**; 16 LTR-reached docs = 3 STACK + 13 controls (`out/l3.out.txt`) | **holds** (mutations: no host gate → 107 / 61 docs / 17 STACK; RELATIVE as column-loop → 647 / 204 / 47: `out/l3-mut.out.txt`) |
| L4 | U1 fires on 7 comps / 6 docs, heading scope 4; 12 identity docs; U2 exactly 4 hosts | 4 abspos h1 with an undeclared block edge (005 / 006 / 011 / 014); 12 identity docs; abspos `<p>` 4 comps / 3 docs (my walk does not separate hoisted roots); headings with children or runs: **exactly 4** (`out/l4.out.txt`) | **holds**; see N2 on the `<p>` list |
| L5 | U+3001 markers 36 / 4 docs; FontVariantNumeric 6; 2 RTL baked markers | `meta/markerText` U+3001: 36 (001: 9, 004: 23, 005: 2, counter-suffix: 2); U+3001 in `text` (005, 203, 206: 6, not markers, not reached); FontVariantNumeric 6, all counter-suffix; strong-RTL markers: `א.` `ב.` (35 others are AN digits) (`out/l5.out.txt`) | **holds** |
| L6 may-move | 240 android stems (252 + D21 26 + (2b) 1 − 12 shared − 27 L6 carriers) | My mirror of `BorderSideExtractor` (logical sides, shorthands, keywords, `applyMediumDefault`) and `hasBorder`: **1973 bordered comps, 279 docs** (`out/l6mm.out.txt`). Mine − contain-animation-001 + basic-blur − shared − L6 carriers = **240, set-equal** to `unionMayMoveCaptures.android`; `sharedStems` == raw ∩ other lanes' android carriers (12) | **holds** (N4: the generator's "own text" is non-whitespace; contain-animation-001's only text is U+00A0) |
| L6 U1 | 10 self-paint; carriers clip-rect, clip-rect-zoom | `selfPaintsWithoutClip` with StyleApplier's real inputs (`backgroundClipInsets` = a non-border-box BackgroundClip): **the same 10**; only clip-rect / -zoom have children reaching the band (Top −34); clip-path-contentBox-1e's child is clipped by its own clip-path (`out/l6u1.out.txt`) | **holds** |
| L6 U2 carriers | 25 (brief crossing scan) | Two independent pixel metrics vs the frozen refs: "band over ref ink" (`l6x.py`) and "band sandwiched through ink" (`l6s.py`). **25 / 25** are Android-only against web (excess ≥ 20, web ≤ 5) under one or both. The one non-carrier both-natives-clean hit is anchor-center-overflow-003 (unscored, D21 may-move): a position artefact (`out/aco3-zoom.png`: the abspos box is offset and recoloured, not overpainted) | **holds** |
| L7 | 0 carriers; 17 bidi-baked | manifests `bidiBaked` = **17** | **holds** |

## 3. Every "from" (executed)

- **Command.** `node tools/titan/results/wave52-gate/cells.mjs '<the 76 cell rows>' wave54-open wave54-final`
  (`out/cells-pred.out.txt`).
- **Result.** 76 rows, **0 mismatches** against wave54-final.
- **Rows that moved inside wave 54.** Seven rows move between wave54-open and wave54-final, as wave 54 explains:
  hyphenate-character-001…004 ios, counter-suffix ios / android, and clip-rect android.
- **Non-cell rows.** The 11 non-cell rows are tooling rows or unscored claims, and the claims hold:
  - css3-counter-styles-001 / -004 are scored on web only;
  - clip-rect-zoom is not scored.

## 4. Partition, must-not-move, ring-fence, may-move, floors (executed: `scripts/gtchecks.py` → `out/gtchecks.out.txt`, exit 0)

**Partition.** `scored 4096 = carrier 76 + named 665 + complement 3352 + ring 3`:
- no class overlaps another;
- there is no hole;
- every named / complement / ring cell is scored;
- no named cell is a carrier of any lane on its platform.

**Units and predictions.**
- The union of each lane's unit captures equals its `captureCarriers`.
- `unionCaptureCarriers` equals the union of the lanes.
- Every cell-prediction row is a capture carrier of its lane.
- Every unit it names carries it.
- Every `requires` unit exists.

**Gating keys.**
- All 43 name existing units.
- Each key's cell is a capture of a required unit.
- Each key has a prediction row that is gating or `floorNA`.

**Floors.**
- 44 gating rows, every floor **0.95**.
- No non-gating row carries a floor.
- No f→f row carries a floor.

**May-move.**
- 240 Android stems, none an Android carrier of any lane.
- Web / iOS are empty.
- basic-blur is present (D2).
- Scored: 215 = complement 180 + named 34 + ring 1. The named 34 are L1 27, L3 4, L7 4 and L6 3, with overlaps.
- Unscored: 25.

**The ring-fence** (`filter-effects/backdrop-filter-basic-blur.html`) is referenced only by:
- `ringFenced`;
- `lanes.L6.mayMove` and `unionMayMoveCaptures` (D2);
- `readerChanges`;
- `planDecisions` D2;
- `abArms[2].expect` (report only).

It is in no carrier set, no named / complement list, no prediction and no key. It is watched by 3 lines.
`control-check.mjs:133` / `:147` and `probe-readout.mjs:163` test the ring BEFORE may-move.

**Stage 1.**
- `stage1Probe.sections` equals the union of the lanes' `stage1Sections`.
- Every native unit has at least one carrier in a stage-1 section: NB-ios 14, L2 U1 10 / U2 1, L3 U1 1 / U2 7, L4 4 / 4 / 3 / 3, L5 2 / 1 / 3 / 1 / 3 / 1, L6 U1 2 / U2 22.
- Every gating key and gating row sits in its lane's probed sections.

**The checker can fail** (`scripts/gtchecks.py`, `out/mutations.out.txt`). The installed sha256 values were unchanged
after the run.
- Identity: GREEN.
- 9 / 9 mutations RED:
  - `from` +0.0001;
  - a complement hole;
  - a carrier cell in L7's named list;
  - floor 0.9574;
  - a watch line matching 5 cells;
  - an Android carrier in may-move;
  - a key whose `requires` does not carry its cell;
  - a complement cell in the ring;
  - a prediction naming unit U9.

## 5. Geometry keys (executed on wave54-final, my own matcher)

**The run.**
- All 13 registered probe commands, with `<run>` = `<base>` = `wave54-final`, under `nice -n 19` on the pinned
  interpreter.
- All exited 0, as the gate's self-test expects.
- `git status --porcelain` was identical before and after, and no file under `results/` or the run dir is newer.
- Read-outs are in `probes-wave54-final/`.

**My matcher** is `scripts/keys.py` → `out/keys.out.txt`, written independently of `geometry-gate.py`. Over 230 keys
(gating 43 / report 27 / control 160):
- every key matches **exactly one** line, at token 0;
- **0 wrong-class outcomes**: every control prints its expect today, and no gating or report key does;
- every gating and report expect string is printed verbatim somewhere: today, or in `probes-selfcheck.txt`'s replays.

**The reader agrees.** `geometry-gate.py wave54-final --base wave54-final --self-test` gives HOLDS: 230 keys, gating 43
FAIL, control 160 PASS, report 27 FAIL (`out/gg-selftest.out.txt`).

**The U1-state strings are exact.** `compose-wpt-content-box-cb.geometry.py wave54-probe` (U1 on the device) prints:
- the three ltr / two clip / clip-4 gating lines `→ GEOMETRY OK`, with their `contains` strings;
- the three rtl report lines exactly `GEOMETRY WRONG (green (16, 88, 105, 187) vs ref (16, 88, 115, 187))`;
- clip-3 WRONG (U2 absent) (`probes-wave54-final/03.wave54-probe.out.txt`).

**My matcher can fail.**
- A control expect mutated: RED.
- A gating key demoted to control: RED.
- A control promoted to report: RED.
- Identity: GREEN.

## 6. Watchlist (executed)

- `RUN=wave54-final WATCH=…/watchlist.txt node tools/titan/results/wave52-plan/watchlist-check.mjs` gives
  **unmatched 0**: 746 / 746 lines `ok 1`, with 746 distinct cells (`out/watchlist-check.out.txt`).
- My re-implementation of `watchCells` (`${sec}/${test}`.includes(pat), same platform) gives the same, with no duplicate
  line.
- Coverage: every carrier, named and ring cell is watched. There are 2 extra complement lines: multicol-007 android (the
  recorded wall) and dynamic-offset-rtl-001 ios (L3 R7).

## 7. A/B arms (executed: `out/dry-cont.out.txt`, `out/dry-rev.out.txt`)

**Coverage and identity.**
- Each arm's sections ⊇ the sections of its dropped units' carriers.
- Its platforms equal the platforms where those units have captures.
- The names `drop-NB-ios` / `drop-L4-U2` / `drop-L6-U2` are unique.
- `abRead.cmd` uses `<arm.against>`.
- No kept unit depends on a dropped one: U1's revertOrder lists U2 first, so dropping U2 alone is legal.

**The drop-NB-ios expect, recomputed from the rows.** It holds:

| cell | Δ (final − arm) | in the expect |
|---|---|---|
| hyphenate-character-001 ios | +0.0169 | listed |
| hyphenate-character-002 ios | +0.0156 | listed |
| hyphenate-character-003 ios | +0.019 | listed |
| hyphenate-character-004 ios | +0.0273 | listed |
| text-decoration-dotted-001 ios | +0.0037 | listed |
| text-decoration-dotted-002 ios | +0.0046 | listed |
| boundary-shaping-001…008 ios | ±0.0001 | not listed |

**Dry run 1: `plan-build.py --out <scratch> --contingency {NB-ios-b}`.**
- `drop-NB-ios` drops `[NB-ios-b-S1*, NB-ios-b, NB-ios-S1*, NB-ios]`.
- NB-ios's revertOrder becomes `[NB-ios-b, NB-ios]`.

**Dry run 2: `plan-build.py --reverted {L6 U2, rule 4}`.**
- may-move becomes **0**, and `mayMoveWithdrawn` holds the 240.
- `drop-L6-U2` moves to `abArmsWithdrawn`.
- The 25 withdrawn carriers are all in `mustNotMoveAfter`, which adjudicate and probe-readout hold.
- The 6 `[X]` gating keys are withdrawn.
- Neither dry run wrote anything in the tree.

## 8. The picture-correct tally (read and checked)

- **Counts.** 18 entries in full and 4 paint-order-only.
- **Every entry:**
  - is a prediction row whose `to` is P;
  - has keys that are all registered;
  - has keys keyed to that cell;
  - has keys that expect OK.
- **Against the briefs.** The list matches the briefs' P→P claims:
  - L1 -001 / -003 (hyphen-ios-linebox §6);
  - L2's 6 (cbb-android §6);
  - L3's 3 (positioned-parent-slot §6);
  - L5's 4 (counter-suffix-rows §6);
  - L6 clip-rect + the six `[X]`, split by D23.
- **Withdrawal.** The withdrawn logic is present: `pictureCorrectTally.withdrawn`, plus the dry run above.

## 9. Expected movement (read and checked)

The f→P rows are exactly the `gen:movement` table:
- **iOS, MED / MED-LOW:** hyphenate-character-004 and inset-005 / -006 / -014.
- **Android, HIGH / MED-HIGH:** clip-3 and dir-style-02a.
- **Android, MED / MED-LOW:** inset-005 / -006 / -014.

`expected.lost`, `unmeasuredNow` and `newlyMeasured` are all empty.

---

## 10. Defects (ranked)

### S1 (should-fix) — stage-1 decisions revert a lane for a move made by another lane's unit

**What the generated text says.** At `wave55-pre` the tree carries EVERY lane's units (the union probe), but three
`stage1Decision` strings turn a section-level identity clause into this lane's revert:
- **L1** (`plan-build.py:322-323`): "KEEP NB-ios iff … **every web and Android css-text capture** outside the union
  carriers … is decoded-pixel identical to wave55-open …". NB-ios is a Swift-only seam.
- **L3** (`:534`): "row 7 not OK, or **any other selectors capture moved** → revert U2, then U1". This is platform-blind for a
  Compose-only lane.
- **L6** (`:870`): "U2 is reverted on … **an Android mover outside the union carriers ∪ mayMove**", while L2 / L3's Compose
  units are on the same tree.

**The concrete path (from my L3 census, §2).**
- L3 U1 mounts LTR host-inactive parents in `css-text/bidi/bidi-lines-002` and `css-text/boundary-shaping/boundary-shaping-009`
  android. These are stage-1 css-text captures, and L3 predicts identity for them (its own `[C]` controls).
- If that prediction fails, L1's KEEP conjunction is false, so NB-ios (iOS) is reverted.
- L3's own decision reverts nothing for a css-text move.

**The conflict.**
- `revertRule` 5 says a leak "reverts the commit it is **bisected** to".
- `probe-readout.mjs` prints "leaks: bisect" and names no unit for a non-carrier move.
- The generated text and the readers disagree. Rule 7 makes the text decide.

**Fix** (`plan-build.py`, then regenerate and run `plan-prose.py --fill` / `--check`):
- **L1:** keep only the iOS clauses in the KEEP conjunction, and append: "web / Android css-text captures outside the
  union carriers (L6 may-move stems: |Δ| ≤ 0.002) are rule-5 must-not-move for every lane: a move there is bisected to its
  commit (probe-readout names none), never an NB-ios revert by itself."
- **L3:** "… or an **Android** selectors capture outside the union carriers moved and the rule-5 bisect lands on U1 / U2 →
  revert U2, then U1; a web / iOS selectors move is never L3's."
- **L6:** "… an Android mover outside the union carriers ∪ mayMove **that the rule-5 bisect lands on U2**".

### S2 (should-fix) — `degenerateRetirementCheck.units.android` lets R6 retire gains `stayDegenerateEvenIfPass` forbids

**Evidence (executed: `scripts/r6-synth.py`, `out/r6-synthetic.adjudicate.out.txt`).**
- I built a synthetic `--movers 0` record: every wave54-final cell unchanged, except inset-005 ios / android f → P 0.97.
- I built a geometry JSON in which `[F] text-decoration-inset-005 ios` and `… android` PASS.
- `node tools/titan/results/wave55-gate/adjudicate.mjs <score> --geometry <geo>` prints:

```text
RETIRED from degenerateByConstruction (degenerateRetirement holds — gains): 2
    css-text-decor/text-decoration-inset-005.html ios ...
    css-text-decor/text-decoration-inset-005.html android  f 0.9 → P 0.97  — units U1-android, U2-android on the tree; ... PASS
```

**What the plan says instead.**
- `degenerateRetirement`: "… the android rows stay DEGENERATE this wave".
- PLAN §1: "Android stays DEGENERATE this wave … also on stayDegenerateEvenIfPass".
- `stayDegenerateEvenIfPass` lists inset-005 / -006 android: "a P on these cells is never written as a fix".

**Why it happens.**
- The generator emits `units.android: [U1-android, U2-android]`; `plan-build.py:1350` asserts that every platform is named.
- `adjudicate.mjs` R6 (`:174-189`) never reads `stayDegenerateEvenIfPass`.

**Probability.** Today the `[F]` android rows are REPORT keys, expected WRONG, so this needs an unexpected OK. But the
closing read-out would then tell the PR writer the opposite of the plan's honesty list.

**Fix, either of:**
- (a) `adjudicate.mjs` `notRetired()` returns "stays degenerate (stayDegenerateEvenIfPass)" first for any listed cell, with
  calibration row J6 being this synthetic (android stays, ios retires);
- (b) `plan-build.py` emits `units.android: null` with a `why`, relaxes the `:1436` assert to allow null, and adjudicate
  treats null as "not retirable this wave".

### Nits

**N1.** `mustNotMoveComplement.rule` still says "L6 may-move cells are here: their 0.002 band IS this rule". The same text
is rendered in PLAN `gen:union`. Of the 215 scored may-move cells, 34 are in NAMED lists (§4). Round-1 N1 fixed
`mayMoveRule` only. Fix the string the same way: "of mustNotMoveComplement or of a lane's named list".

**N2.** L4 `carrierRule.U1` now says the out-of-scope abspos `<p>` documents "(s-11-1-1b-005, **s-11-1-1b-006**,
anchor-center-safe-rtl) are logged once".
- The brief's own census (`ua-heading-margin.census.out.txt:7-8`) shows -006's `<p>` is a hoisted ROOT handled by T6
  ("already has the UA margin"), which the rule does not fire on.
- So "7 components in 6 documents" (4 headings + -005 + anchor-center-safe-rtl ×2) was right.
- Round-1 N3, my predecessor's, was a false positive (my own walk does not separate hoisted roots either: 4 `<p>` comps /
  3 docs).
- Fix: drop -006 from the list, or say "-006's hoisted root is T6's, not U1's".

**N3.** The L1 census counts all-prop-001 as two declared-normal "dejavu@20, 1 line" runs. At runtime they are not:
- `All: INITIAL` is the component's last property, and `GlobalExtractor.applyingAllReset` (iOS) keeps only
  Direction / UnicodeBidi before it.
- So `lineHeightIsNormal` is false, and the runs are calibrated: the iOS capture's two lines sit at 276 / 296 (pitch 20),
  in Inter-sized glyphs.
- NB-ios's real radius is therefore 58 components / 18 documents. all-prop-001 ios stays a correct must-not-move cell
  (out of radius), and the Catalyst log row for it is moot.

Fix: correct the census line, and optionally pin that `applyingAllReset` strips the LineHeight on the verbatim
`__1-002` properties.

**N4.** The L6 may-move rule's "own text" is implemented as non-whitespace: Python `str.strip()`, which strips U+00A0.
- contain-animation-001's 50-px green-bordered box (whose only text is NBSP) is therefore excluded.
- No ink means no possible move, so this is harmless.
- Say "non-whitespace own text" in `mayMoveRule` / `carrierRule.U2`, so a reader re-deriving it gets 278, not 279.

**N5.** A `probe-decisions/reverted.json` row without `rule` makes `plan-build.py` die with a `KeyError` traceback
(`:1097`) instead of a named refusal. PLAN §7 documents the field (`out/dry-rev.out.txt` first attempt). Add one assert
naming the required keys `{lane, unit, run, rule}`.

**N6.** The L1 census line for disclosure-styles ("inter@16, 1 line") hides that 12 of its 18 runs are vertical, which
the D22 guard now declines. It is harmless: disclosure-styles ios is an L5 carrier (C5). Fold it into N3's census
correction.

## 11. Round-1 generator-truth defects marked fixed: re-check

| id | re-check | result |
|---|---|---|
| M1 hyphens-vertical-001 | `horizontalWritingMode` guard in `carrierRule`, the NB-ios commit text, brief §4 and the census line (`census.out.txt:58`); the HEAD anchors exist (`ComponentRenderer.swift:4868` stored flag; `:3220` / `:1746` / `:3510` / `:3568` / `:4313` all set it from the merged list); P8 / M8 in the commit text | **fixed** (the guard also declines disclosure-styles' 12 vertical runs: N6) |
| M2 medium default | my own Compose-rule census, §2 | **fixed**: 278 + basic-blur, 240 set-equal |
| S1 drop-NB-ios count | §7 | **fixed** (6 cells, Δs consistent with the rows) |
| S2 clip-rect section | `abArms[2].sections` ∋ filter-effects | **fixed** |
| S3 arm identity / against | names unique, per-arm `against`, `abRead.cmd` `<arm.against>`, generator assert | **fixed** |
| S4 snapshot reader | `corpusSnapshotSource` / `closingGateFiles` name `wave55-gate/make-snapshot55.mjs` | **fixed** |
| N1 | `mayMoveRule` fixed; `mustNotMoveComplement.rule` not | **partly** → N1 |
| N2 contingency drops | dry run, §7 | **fixed** |
| N3 `<p>` docs | the addition is itself wrong | **regressed** → N2 |
| N4 naming | kept by decision | accepted |

## 12. What I did not do

- No gate, device, gradle, xcodebuild, vite, puppeteer or emulator.
- No edit outside `plan-skeptic-round-2.generator-truth*`.
- **Git use.**
  - The only state-changing git commands were `git worktree add --detach <scratchpad>/gt2-wt HEAD` and its
    `git worktree remove`; `git worktree list` no longer shows it.
  - Otherwise git was read-only: `status`, `ls-files`, `check-ignore`, `log`, `--no-optional-locks show 5b333c8a`.
- **Tree state.** `git status --porcelain` at the end differs from the start only by the parallel round-2 skeptics'
  untracked files: `plan-skeptic-round-2.instrument-teeth.d/` and `…process-holes.repro.*`.
- **Not done.**
  - No device truth for the L1 SAME raster claims (half-leading < 0.5 px rounds away at 1×). That is stage 1's job.
  - L3's multicol host channel is modelled only through the hoist walk. It still reproduces the published STACK set.

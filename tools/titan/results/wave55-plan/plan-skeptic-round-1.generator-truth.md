# Wave 55 — plan-skeptic round 1, generator-truth

**Target.** The generator and what it generated, attacked by execution:
- `plan-build.py` → `expectations.json` and `watchlist.txt`;
- the readers that consume them: `geometry-gate.py`, `probe-readout.mjs`, `../wave55-gate/adjudicate.mjs`,
  `../wave55-gate/control-check.mjs` and `../wave55-gate/ab-arms55.sh`.

**State of the tree.** Tree `…/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`, HEAD `640276a0`.

**Inputs, as read (sha256):**

| file | sha256 |
|---|---|
| `expectations.json` | `cd3936d0…` |
| `watchlist.txt` | `4b1a9cd5…` |
| `cells-wave54-final.json` | `c9c148e7…` |
| `PLAN.md` | `865649f1…` |
| `landing-manifest.json` | `0b46b9b2…` |

**Run of record.** `tools/titan/runs/wave54-final`. I never read the `wave55-open` run directory.

**My own scripts and their outputs** are under `plan-skeptic-round-1.generator-truth.d/`:
- `*.py` / `*.mjs`: the scripts;
- `out/`: their outputs;
- `probes-wave54-final/`: the 13 probe read-outs.

Every census below is my own code over `sections/*/per-test-ir` and the captures. None of it calls or copies a brief's
script. All of it was run from the scratchpad and is read-only.

**Verdict.** The generator transcribes faithfully:
- it is byte-reproducible;
- every "from" is right;
- the partition has no hole;
- every key prints what it should;
- the watchlist is exact;
- the floors are right.

**Two of the carrier censuses it transcribes are wrong**, and both are must-fix before build:
- **M1:** the L1 control `hyphens-vertical-001 ios` is not "1 line". By the runtime's own predicate, NB-ios moves it.
- **M2:** L6's may-move set misses 24 documents. Their Compose border is the 3 px `medium` default.

There are also four should-fix items (abArms text and identity) and four nits.

---

## 1. The generator reproduces its outputs (executed)

| check | command | result |
|---|---|---|
| cells snapshot | `node …d/cells-own.mjs wave54-final <scratch>` (the scorer's `loadRun`, my own writer) vs `cells-wave54-final.json` | 4096 cells, **equal** |
| regeneration | `/opt/homebrew/bin/python3.12 -B plan-build.py --out <scratch>/regen` | exit 0; `expectations.json` **byte-identical**, `watchlist.txt` **byte-identical**, report identical to `plan-build.out.txt` (`out/plan-build.regen.out.txt`) |
| side effects | `find tools/titan/results -newer <marker>` after the run | nothing |
| stray file | the wave-54 `snapshot-cells.mjs` writes beside itself | no `wave54-plan/cells-wave54-final.json` exists; `git status` shows only wave-55 untracked files |

## 2. Each lane's carrier census, re-derived with my own scripts

| lane | published (expectations.json) | mine | verdict |
|---|---|---|---|
| L1 NB-ios | 60 text comps / 19 docs (40 own, 20 inherited); 25 `<br>`; ios carriers 14 (4 TARGET + 10 MOVES), 5 SAME | 60 / 19 (40 / 20); 25 `<br>`; faces from my own hhea parse: DejaVu 16 18.625→19, DejaVu 20 23.2812→24, Inter 16 19.3594→20, Inter 36 43.5586→44, Inter 92 111.3164→**111** (ceil 112), Libertine 36 41.04→**41** (ceil 42) | the 14 carriers match. **hyphens-vertical-001 is NOT "1 line"** (see M1) |
| L2 U1 | 33 boxes / 14 docs (oof / vtext), android | 33 boxes / 14 docs, the same box ids (`out/l2-census.out.txt`) | **holds** |
| L2 U2 | 122 % axes, 2 in-flow carriers (clip-3 `0-172` W/H, band 20) | 122 axes; exactly those 2 (`out/l2-u2-percent-axes.out.txt`) | **holds** |
| L3 U2 | 11 RTL Column-loop parents / 7 docs | 11 / 7, the same 7 docs. A positioned (ABSOLUTE/FIXED) parent is needed: RELATIVE takes the Box branch, `ComponentRenderer.kt` `isPositionedContainer` (`out/l3-u2.out.txt`) | **holds** |
| L3 U1 | STACK carriers dir-style-02a, multicol-nested-001, multicol-004 | dir-style-02a and multicol-nested-001. multicol-004 is host-active under my approximation, because I do not model the multicol host channel (`out/l3-u1-stack.out.txt`) | holds within my approximation |
| L4 | 4 heading docs (inset-005/-006/-011/-014); 12 declared-margin abspos `<h1>` docs identity | 4 / 12, identical (`out/l4.out.txt`) | **holds** |
| L5 | U+3001 markers 36 in 4 docs (2 outside + 34 inside); FontVariantNumeric 6 (counter-suffix); 2 RTL baked markers | 36 / 4 (9 + 23 + 2 inside, 2 outside); 6; `א.` `ב.` (`out/l5.out.txt`) | **holds** |
| L6 U1 | 10 self-paint components; 2 carrier parents | 10, the same list (`out/l6-u1-selfpaint.out.txt`) | **holds** |
| L6 U2 carriers | 25 (crossing scan, android excess ≥ 20, web / iOS within 5) | 25: my own crossing counter over **276** docs gives exactly the same 25 stems (`out/l6-crossings.out.txt`) | **holds** |
| L6 may-move | 252 docs + (2b) basic-blur | **276**: the brief's rule plus Compose's `applyMediumDefault`. The brief's explicit-px rule reproduces 252 == 252 exactly (`out/l6-maymove-*.out.txt`) | **under-reported by 24** (M2) |
| L7 | 0 carriers; U5 over 17 bidi-baked docs | manifest `bidiBaked` = 17 | **holds** |

## 3. Every prediction "from" against `cells.mjs` (executed)

- **Command:** `node tools/titan/results/wave52-gate/cells.mjs '<76 cells>' wave54-open wave54-final`
  (`out/cells-pred.out.txt`). Each row was compared with its `from` (`out/check-from.out.txt`).
- **Result:** 76 cell rows, **0 mismatches**. Three `from`s carry a `DEGENERATE` suffix (L2 autopos *-ltr), and the
  number before it matches.
- **The 11 non-cell rows:**
  - the "unscored" claims hold. css3-counter-styles-001 / -004 are unscored on iOS and Android (web P 0.9933 / 0.9878);
    backdrop-filter-clip-rect-zoom is `scoreEligible: false`;
  - the other 8 rows are tooling rows, not cells.

## 4. Must-not-move outside the union; the complement rule; the ring-fence (executed: `…d/gt-checks.py`)

**`gt-checks.py` HOLDS:**
- F 76 rows;
- the partition: 4096 cells = carriers 76 + named 665 + complement 3352 + ring 3;
- W 746 lines;
- G 44 gating rows.

**The partition:**
- No pair of the four classes overlaps, and there is no hole.
- Every named / complement / ring cell is a scored cell.
- No lane's must-not-move cell is any lane's carrier.
- The union of each lane's unit captures equals that lane's `captureCarriers`.

**May-move:**
- 214 android stems; 196 are scored cells, 18 are unscored captures.
- Of the 196 scored cells: ∩ complement 167, ∩ ring 1 (basic-blur android, D2), ∩ named lists **28** (L1 21, L3 3,
  L6 3, L7 1).
- R5 (`adjudicate.mjs`) and `probe-readout.mjs` give named and complement cells the same 0.002 band, and may-move is
  read before must-not-move. So the behaviour is right. Only `mayMoveRule`'s sentence "the cells are in
  mustNotMoveComplement" is wrong (N1).

**The ring-fence** (`filter-effects/backdrop-filter-basic-blur.html`):
- It appears only in:
  - `ringFenced`;
  - `unionMayMoveCaptures.android` and `lanes.L6.mayMove`;
  - D2's resolution;
  - the `readerChanges` prose.
- It is in no carrier set, no named or complement list, and no prediction or key.
- Both `control-check.mjs` and `probe-readout.mjs:155` test the ring BEFORE may-move.
- It is watched: 3 watch lines.

## 5. Every geometry key against the probe's own print (executed on wave54-final)

**Commands.**
- All 13 registered probe commands were run myself: `<run>` = `wave54-final`, `<base>` = `wave54-final`, pinned
  interpreter, `nice -n 19`.
- All exit 0.
- `git status --porcelain` was identical before and after, and no file under the tree is newer than the marker.
- Read-outs are in `probes-wave54-final/`.

**`keys-check.py` (my matcher: key tokens within the first 3 tokens, last hit, `endswith(expect)` + `contains`)** gives
230 keys, **0 wrong-class outcomes**:
- every one of the 160 controls prints its expect string today;
- every gating key and every report key does not.

**Printability.** Each expect string appears verbatim on some line of the same probe's wave54-final output, with one
exception: the 10 L1 `STACK OK` keys. No line is OK today. `STACK OK` is printed on the replays
(`probes-selfcheck.txt:118-127`).

**The three gating keys on f→f rows** are by design:
- `[W] anchor-position-multicol-nested-001 android stack`;
- `[M] text-decoration-inset-011 ios` and `[M] text-decoration-inset-011 android`.

These rows carry `floorNA` (C11). Every other gating key's cell has a gating prediction floored at 0.95.

**Unit names.** Every gating key's `requires` / spec names units that exist, and its cell is a capture of one of them.

**The retirement keys** `[F] text-decoration-inset-005/-006 ios|android` exist. They print `… → GEOMETRY WRONG (1 heading
line band(s) vs ref 2 …)` today.

## 6. The watchlist (executed)

- `RUN=wave54-final WATCH=…/watchlist.txt node …/wave52-plan/watchlist-check.mjs` → **unmatched 0**: all 746 lines
  `ok 1`.
- My own matcher, which re-implements `watchCells`' substring rule, gives the same: every line matches exactly one
  scored cell, with no duplicate line.
- **Coverage:**
  - every carrier cell (76), every named must-not-move cell (665) and the 3 ring cells are watched;
  - 2 more lines are complement cells: `anchor-position-multicol-007 android` and `dynamic-offset-rtl-001 ios`.

## 7. Floors, tally, stage 1, units (executed)

**Gating rows.** 44 gating rows, every one floor **0.95** and predicting P. No non-gating row carries a floor. Every f→f
cell row carries `floorNA`.

**`pictureCorrectTally`.**
- It has 22 entries. Every one is a P→P prediction row whose keys are registered, keyed to the same cell, and expect an
  OK string.
- counter-suffix ios / android also list their `[K]` CONTROL key. That is stricter, and consistent with the "every key OK"
  rule.

**Stage 1.**
- `stage1Probe.sections` equals the union of the lanes' `stage1Sections`: the briefs' §8 sections plus L3's own
  css-text / css-writing-modes extension.
- Every native unit has at least one carrier in a stage-1 section. L2 U1's css-position / css-gaps carriers, and L3 U1's
  css-anchor-position carriers, are stage-2-only, as `unitCoverage` says.

**Landing manifest.** `landing-manifest.json` `units[]` equals `expectations.json` `revertUnits` + `contingencyUnits`,
with every `revertOrder` equal.

## 8. abArms against units (read)

**Coverage.** Each arm's sections cover the sections of the carriers of the units it drops, and each arm's platforms
match:
- NB-ios: css-text, css-text-decor;
- L4 U2-ios / U2-android: css-text-decor;
- L6 U2: css-flexbox / css-grid / css-images.

**Order.** Each dropped unit is the first in its lane's revert order: NB-ios before U3b; L4 U2 before U1; L6 U2 before
U1.

The defects are S1–S3.

## 9. My instruments can fail (executed on scratch copies)

Installed `expectations.json` / `watchlist.txt` sha256 values are unchanged (`shasum -c` OK). The record is
`out/mutations.out.txt`.

**`gt-checks.py`: 6 / 6 mutations red:**
- a `from` +0.0001;
- a complement cell removed (hole);
- an L1 carrier added to L7's must-not-move;
- a floor set to 0.9574;
- a complement cell put into the ring;
- a watch line matching 7 cells.

**`keys-check.py`:**
- a control's expect mutated → red;
- a gating key demoted to control → red.

**The L6 may-move census:**
- the brief's rule reproduces the published 252 exactly (control arm);
- the medium-aware rule gives 276 (the finding).

**The crossing counter:** 0 on a clean synthetic teal square, 80 on the same square with a 2-px band across it.

**L2 and L3 censuses** are shown sensitive:
- L2 with medium-default bands adds 12 docs. They do not apply, because `DynamicValueResolver.childContainingBlock`
  reads raw px (`:235-246`).
- L3 with the broad host predicate gives 0 STACK docs; with the narrow one, 2.

---

## 10. Defects (ranked)

### M1 (must-fix) — L1: `css-text/hyphens/hyphens-vertical-001.html ios` is classified SAME ("1 line"), but NB-ios as specified moves it

**Evidence (all executed or read at HEAD):**

1. **The census asserts the line count; it does not derive it.**
   - `hyphen-ios-linebox.census.mjs:99-101`: `multiLine: /hyphenate-character-00[1-4]/.test(f)`, with the comment
     "every other radius run is one line".
   - Its output prints `SAME … hyphens-vertical-001 … 1 line: box ceil(18.625) = 19 → 19`.
2. **The wave54-final iOS capture draws both boxes on THREE lines.** They read `hyph / ena- / tion` and `hyph / en- /
   ation` (`…d/out/l1-census.out.txt`, and a 2× crop I looked at).
3. **Box 1's IR** is `__2-320`:
   - text `'hyphen\xadation'`, no whitespace;
   - `WritingMode VERTICAL_RL`, `Hyphens MANUAL`, no `lang`;
   - Width 3em, LineHeight `{original: normal}`.
4. **The runtime's own predicates at HEAD give it an "exact" count of 1:**
   - `SoftHyphenPolicy.admitsPreBreak` returns false for a space-less U+00AD run in a vertical mode
     (`SoftHyphenPolicy.swift:159-167`), so `preBroken` is false and `displayText` is the raw string.
   - `singleLineText = !displayText.contains { $0.isWhitespace }` is true, because U+00AD is not whitespace
     (`ComponentRenderer.swift:5082`).
   - So `lineCountIsExact` is true (`:5151`), and `renderedLineCount` = 1 (`LineBoxMetrics.swift:114`).
5. **Today and after NB-ios:**
   - Today `pinnedBoxHeight` returns nil only because a declared `normal` has `lineHeightPx == nil`
     (`LineBoxMetrics.swift:179`).
   - NB-ios's `usedLineHeight` returns Blink's 19 exactly when `declaredNormal && wptCapture && lineCountIsExact`
     (brief §4).
   - So box 1 is pinned to 1 × 19 px while TextKit lays out 3 lines. That is a large picture change, not byte-identity.
6. **The plan bets on byte-identity:**
   - `expectations.json` L1 `stage1Decision`: "iOS boundary-shaping-010 and hyphens-vertical-001 byte-identical" is a
     KEEP condition, so failing it reverts NB-ios;
   - the cell is in L1's `mustNotMove`, and R5 fails at the closing gate if NB-ios ships;
   - the brief names it an R1 tripwire.

**Fix, before L1 builds:**
- (a, preferred) NB-ios declines a run in a vertical writing mode. Pass `horizontalWritingMode` (`:3220`'s expression)
  into `usedLineHeight` and return nil when it is false. Add pin **P8** with mutation M8 "drop the guard": hyphens-vertical-001 `__2-320`'s properties verbatim
  from the wave54-final per-test IR, `usedLineHeight(…) == nil`.
- Keep the cell as a control, and correct the census line ("1 line" → "pinned by the singleLineText proxy, 3 drawn
  lines; declined by the vertical guard").
- (b) Otherwise, make it an L1 ios carrier with an undirected prediction, drop it from the stage-1 byte-identity clause,
  and regenerate.
- Either way, the L1 builder should log `lineCountIsExact` / `renderedLineCount` for the five SAME controls in the
  Catalyst pin. The census asserts them; it does not derive them. all-prop-001's `'321'` in a 0.5em box is the other
  space-less one.
- Then `plan-build.py` regenerates, followed by `plan-prose.py --fill` / `--check`.

### M2 (must-fix) — L6: `mayMove` misses 24 documents whose Compose border is the 3 px `medium` default

**Evidence:**
1. **The brief's rule counts only an explicit width.** `android-paint-order.crossings-scan.py:44-45`
   (`structural_docs`, the may-move source) counts a band only when `Border*Width.px > 0`.
2. **Compose paints a style-only side at 3 dp.** `BorderSideExtractor.kt:158-171` `applyMediumDefault`: "A side declared
   with a visible style but NO width must therefore paint 3px". This is on the painting path (`BordersFacade.kt:72`,
   `StyleApplier.kt:879`).
3. **The legacy route is under-counted:**
   - the corpus has **533 style-only bordered components in 32 documents** beyond the brief's 1438;
   - **24** of those documents have a non-`<br>` child or own text: the brief's own may-move condition.
   - The explicit-px variant of my census reproduces the published 252 exactly; the medium-aware one gives 276
     (`out/l6-maymove-*.out.txt`).
4. **The 24 documents** (all have Android captures; 17 are scored):
   - floats-clear-multicol-000…003 and -balancing-000…003 (CSS2);
   - anchor-center-overflow-001…004 (unscored);
   - attachment-local-positioning-2/-3/-4 (unscored);
   - all-prop-001;
   - bidi-lines-001/-002, hyphens-out-of-flow-001/-002, hyphens-span-001/-002 (css-text);
   - direction-upright-001/-002.
5. **Consequences.** A U2 pixel move on any of them is a LEAK under:
   - `control-check.mjs` (not may-move);
   - `probe-readout.mjs` (any Δ ≠ 0 of a non-may-move cell);
   - revert rule 5, which bisects and reverts L6 U2 for a generic, legitimate move.
   - The six css-text ones also sit in L1's (iOS-only) `stage1Decision` Android-identity clause at `wave55-pre`.
6. **No new carriers.** My crossing count on the 24 finds no carrier: ref == android on every one. So the carrier list
   stands. Only the may-move set is short.

**Fix:**
- `plan-build.py`'s may-move rule (and the brief's census line "1438 bordered components") honour the medium default:
  visible style and no `Border*Width` means 3 px.
- Add the 24 Android stems to `lanes.L6.mayMove` / `unionMayMoveCaptures` by rule, as D2 did for (2b). Record it as
  decision D21.
- Regenerate. The complement and the R5 band are unchanged.

### S1 (should-fix) — abArms `drop-NB-ios`: "exactly 4 cells differ" contradicts its own numbers

`ab-diff.mjs` counts a row when it is a flip or `|Δ| ≥ --threshold 0.002`. The same expect string gives dotted-001 /
-002 Δ ≈ +0.004 / +0.005 (predictions 0.5318 → ≈0.5355, 0.5426 → ≈0.5472). So 6 cells differ, and "any other
differing iOS cell is a leak" would call the two dotted rows leaks.

**Fix:** "exactly 6 cells differ: hyphenate-character-001 / -002 / -003 / -004 ios (+0.017 / +0.016 / +0.019 / +0.027,
-004 a flip) and text-decoration-dotted-001 / -002 ios (+0.004 / +0.005); boundary-shaping within 0.0002". Restate it
after M1, which may add hyphens-vertical-001. If NB-ios-b lands, the arm drops NB-ios-b + NB-ios (N2).

### S2 (should-fix) — abArms L6 `drop-U2`: the expect asserts what its sections cannot see

- **The defect.** The expect says "U1's clip-rect stays", but `filter-effects` is not in the arm's sections, so ab-diff
  never compares clip-rect.
- **The fix.** Add `filter-effects`. That also observes D2's ring-fenced basic-blur band move: report-only. Or drop the
  clause.
- **After M2.** State that may-move moves outside the six sections are attributed by rule, not by this arm.

### S3 (should-fix) — abArms identity (D8 / D9 still owed to the generator)

**Defects.**
- Two arms are both named `drop-U2` (L4 probe-time, L6 closing), so `abRead.runId` `wave55-ab-<arm>` collides.
  `ab-arms55.sh` refuses it.
- The L4 arm has no `against`, and `abRead.cmd` hard-codes `wave55-final`, although PLAN §9 D9 reads it against
  `wave55-pre`.
- The generated file disagrees with PLAN §9 D8 / D9.

**Fix** in the same regeneration as M1 / M2:
- `arm: drop-L4-U2` / `drop-L6-U2`;
- a per-arm `against`: wave55-pre for L4, wave55-final otherwise;
- `abRead.cmd` uses `<arm.against>`;
- an assertion that arm names are unique.

### S4 (should-fix) — D20: stale snapshot-reader strings

`corpusSnapshotSource` and `closingGateFiles` name `wave52-gate/make-corpus.mjs`. Wave 55's reader is
`../wave55-gate/make-snapshot55.mjs` (PLAN §9 D20). Regenerate with the new string.

### Nits

- **N1.** `lanes.L6.mayMoveRule` says may-move cells "are in mustNotMoveComplement". 28 are in named lists (L1 21, L3 3,
  L6 3, L7 1). The behaviour is right, because R5 and probe-readout give both the same 0.002 band. Fix the sentence.
- **N2.** `drop-NB-ios` does not say what happens if the contingency NB-ios-b lands: drop both, in revertOrder
  [NB-ios-b, NB-ios].
- **N3.** The L4 `carrierRule` text says "fires on 7 components in 6 documents … s-11-1-1b-005 and anchor-center-safe-rtl
  (`<p>`)". My census finds three out-of-scope abspos `<p>` docs: s-11-1-1b-005, **s-11-1-1b-006** and
  anchor-center-safe-rtl. Out of scope either way.
- **N4.** counter-suffix ios / android sit in both `pictureCorrectTally.full` and `stayDegenerateEvenIfPass`, with an
  "until … print OK" condition. This is consistent with the tally rule, but the list's name contradicts the condition.
  Move the condition to a `degenerateRetirement`-style field.

**Observations, not defects:**
- Compose paints a style-only border at 3 dp, but `childContainingBlock` subtracts only raw px bands. This is a
  pre-existing paint / layout disagreement. L2 is not affected, because its census mirrors the layout read.
- L3 U1's multicol-004 rests on the brief's port of the multicol host channel, which I did not model.

## 11. What I did not do

- I ran no gate, device, gradle, xcodebuild, vite or puppeteer.
- I made no git state change.
- Git commands were read-only: `status` / `log` once each at the start, `status --porcelain` around the probe run, and
  `--no-optional-locks show 5b333c8a` to read the wave-54 `ContainingBlockBands.kt`.
- I created no throwaway worktree. My new instruments are read-only scripts that ran from the scratchpad. The one
  existing dry-run I exercised, `plan-build.py --out`, was already proven in a worktree by the generator stage, and here
  it wrote nothing in the tree (§1).
- I edited no file outside `tools/titan/results/wave55-plan/plan-skeptic-round-1.generator-truth*`.

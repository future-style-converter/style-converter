# Wave 54 · builder lane L7 · label-chrome — lane note

Contract: `tools/titan/results/wave54-plan/PLAN.md` "### L7 · label-chrome"; brief `label-chrome-all-reset.md`;
`expectations.json` `lanes["L7-label-chrome"]`. Shared tree, branch `campaign/applier-campaign`, HEAD `db6e8aa0`.
No commit, no checkout/stash/reset, no seam file touched, no device / Chromium / emulator / simulator, no full suite.
Focused runs only: `node --test tools/visual/label-chrome-tripwire.test.mjs`, the brief's replay and geometry probe, and
this lane's own scripts below.

## 1. What changed and why

**The defect is in the ORACLE, not the chrome** (brief §0, re-seen here): the 18 PNGs of 77fe41e8 show the label on
000 / 002 / 004 only, on all three platforms; 001 and 003 are containers and 005 is a text root, which
`docs/DYNAMIC_CAPTURE.md` §5 "When" says get NO label. I LOOKED at all 18 (`look-seed.py` montage; the band of
001/003/005 is empty ground ×3, the green fills sit at (16,16), 005's "123" at the right edge, web serif / native sans).
`tools/visual/label-chrome-tripwire.test.mjs` derived P from the file name for every stem and had no clause for the "iff".

U1 (device-free tooling) — three files:
- NEW `tools/visual/label-chrome-check.mjs` (199 lines) — the PURE checker (no node:test cases, so replays import it):
  - `normalize`, `truncatedCount`, `glyphPixels`, `isGround`, `checkTriplet`: moved from the test file, **code verbatim**
    (proven: `verdict-dump.*` identical to HEAD on all 136 triplets, see §5 R3);
  - `checkExempt(pngs, name)` — U1 (b): `checkTriplet(pngs, '')` (P = ∅, so (ii) band byte-identity binds when the band
    is ground ×3) plus NEW clause **(iv)**: per platform, red when EVERY pixel of the name's would-be label is non-ground;
  - `nodeKind` / `nodesNamed` / `fixtureSeeded` / `readFixture` / `parseExemptManifest` / `verifyExemptEntry` — U1 (a):
    structural verification of each manifest entry against its fixture, independent of pixels and runtimes;
  - `labelAbsentEverywhere` / `listFixtures` / `exemptHint` — U1 (d): the HINT on a red stem whose label is absent ×3.
- NEW `tools/visual/label-chrome-exempt.json` — `{ "_comment": [...], "exempt": { "001_ATC_PropsThenAll_InGreenParent":
  container, "003_ATC_InitialUnderRedParent": container, "005_ATC_DirectionSurvives": text } }`, all with fixture
  `fixtures/combinations/all-then-color.json` and a per-entry `_comment`.
- `tools/visual/label-chrome-tripwire.test.mjs` (209 → 207 lines) — imports the checker and RE-EXPORTS `normalize`,
  `truncatedCount`, `glyphPixels`, `checkTriplet`, `checkExempt` (so `wave54-plan/label-chrome-all-reset.replay.mjs`, which
  imports from the test file, still runs byte-identically: `brief-replay.head.txt` = `brief-replay.u1.txt`); the stem loop
  `label chrome <stem>` runs `checkExempt` iff `VERIFIED.get(stem)?.status === 'verified'`, else `checkTriplet` UNCHANGED,
  and prints the (d) HINT when an unexempted red stem's label is absent ×3; NEW tests: 8 synthetic controls (c), the
  manifest shape test, one `label chrome exempt-entry <stem>` test per entry (a). Header: clause (iv) documented, the
  stale "(iii) … agree within ±1" wording corrected to the code (counted, never asserted), the wave-51 negative-control
  record kept verbatim, a wave-54 control line added. Counts in comments made count-free ("the committed captures").

### Where the brief / plan and the tree disagree (the tree wins)

1. **Stale-entry rule → `pending` while the fixture is unseeded.** Brief §6 (a) says an entry whose stem has no committed
   triplet is red ("stale entry"). But PLAN §4 lands U1 at step 3.1, the sweep (§4 step 5, the tooling suite included)
   runs BEFORE the seeding (§8 step 10), and brief §9 R1 itself requires "U1 without PNGs is inert but green"; an
   unconditional stale rule would make U1 alone red. So: no PNG for the stem AND no committed PNG names any component of
   the entry's fixture (`fixtureSeeded` = `test-all.sh` `_gate_fixture_has_baselines`, re-typed) → `pending` (printed
   loudly, exempts nothing); no PNG while the fixture IS seeded → red "stale entry"; 1–2 platforms → red "partial
   triplet". So `pending` holds exactly when the fixture net runs the fixture gate-only. m5 stays red pre- and post-seed
   (its node does not exist); the pure-stale half is pinned by the synthetic control + MUT-4.
2. **Manifest shape** is `{ _comment, exempt: { stem: { fixture, why, _comment? } } }`, not the brief's flat
   `{ stem: entry }` — JSON has no comments, and a flat map would make `_comment` look like a stem. Unknown keys are red.
3. **No tripwire-header edit at U2-seed.** Brief U2.5 lists "the tripwire header (130 → 136 stems / 390 → 408 PNGs)"
   among the U2-seed doc lines; U1 made the live comments count-free, and the 130/136/390 numbers left in the header are
   the dated wave-51 negative-control record, which stays. U2-seed therefore touches no U1 file.
4. **`geometry-gate.py --lanes L7` has 0 keys** (L7 has no corpus geometry probe; `expectations.json` `geometryProbes: []`),
   so its `--self-test` HOLDS vacuously (exit 0, `geometry-gate.selftest.out.txt`). The lane's picture probe is the
   brief's `label-chrome-all-reset.geometry.py` (`geometrySeed`), whose teeth I re-ran (§5 G).

## 2. REVERT UNIT U1 (tooling; lands first, §4 step 3.1)

Commit holds exactly these paths:
- `tools/visual/label-chrome-tripwire.test.mjs` (modified)
- `tools/visual/label-chrome-check.mjs` (new)
- `tools/visual/label-chrome-exempt.json` (new)

Final sha256 (`sha256.final.txt`): check `fad37378…7b65`, test `9d8f2b22…23ed5`, manifest `5d5ea832…98753`.
Not executed or built by any gate, capture, converter or runtime path (census (A)). Alone it is green: 148/148, the
three entries `pending`. `revertOrder`: U2-seed is reverted before U1, never after (PNGs without U1 → the wave-53 red,
proven by MUT-5 on the post-seed mirror). The lane records `tools/titan/results/wave54-label-chrome/` are records, not
part of U1's revertible code — commit them with the lane records, not inside U1.

## 3. REVERT UNIT U2-seed (device step on the CLOSING tree, [W-L7], PLAN §8 step 10)

Commit holds exactly these 18 PNGs (the names of `label-chrome-all-reset.seeded-77fe41e8/`; PLAN's
`__00{0..5}_*.png` glob also matches 66 committed baselines of other fixtures — skeptic N3 — so these are listed):
- `tools/visual/baseline/Android__000_ATC_AllThenProps.png`, `iOS__000_ATC_AllThenProps.png`, `web__000_ATC_AllThenProps.png`
- `tools/visual/baseline/Android__001_ATC_PropsThenAll_InGreenParent.png`, `iOS__001_…`, `web__001_ATC_PropsThenAll_InGreenParent.png`
- `tools/visual/baseline/Android__002_reset.png`, `iOS__002_reset.png`, `web__002_reset.png`
- `tools/visual/baseline/Android__003_ATC_InitialUnderRedParent.png`, `iOS__003_…`, `web__003_ATC_InitialUnderRedParent.png`
- `tools/visual/baseline/Android__004_span.png`, `iOS__004_span.png`, `web__004_span.png`
- `tools/visual/baseline/Android__005_ATC_DirectionSurvives.png`, `iOS__005_…`, `web__005_ATC_DirectionSurvives.png`

plus the orchestrator's doc lines (§7 hand-offs). No U1 file. Seeding is NOT done by this lane (window request [W-L7]).

## 4. Census (method: `census.mjs` → `census.out.txt`; `seed-name-collision.out.txt`)

- **(A) code reach.** `git grep` for the three U1 file names outside `tools/titan/results/` and `docs/`: 2 tracked files —
  the tripwire itself and a COMMENT in `apps/web-harness/tests/ui/LabelChrome.raster.test.tsx:22`. On a capture /
  extraction / converter / runtime path: **0**. Consumers of `tools/visual/*.test.mjs`: CI `test-tooling`
  (`.github/workflows/ci.yml:101`), `doc-staleness-check.sh:85` (the COUNT), `smoke.sh:241`. → **capture carriers ∅ ×3,
  wire carriers ∅** = `expectations.json` (`captureCarriers` all [], `wireCarriers` []). The lane's carrier set for
  `control-check.mjs` is empty (C4 calibration already holds/fails as pre-registered, PLAN §10.2).
- **(B) the `all` corpus.** Scanning all **1435** per-test IR documents of `wave53-final` for an IR property of type
  `All`: **11** documents, **33** scored cells, **33 P** (css-cascade all-prop ×7, css-display display-contents-button /
  -details / -fieldset, css-ui appearance-revert-001.tentative). My set equals `expectations.json` `mustNotMove`
  exactly (only-mine 0, only-plan 0). Per-test IR `wave53-final` vs `wave54-open`: 1435 compared, 0 differ.
- **(C) U2-seed radius (brief §9 R4).** test-all switches a gate fixture to BASELINE mode if ANY PNG names one of its
  components. Of the 446 tracked fixtures other than all-then-color, **0** declare `ATC_*`, `reset` or `span` (9 gate
  fixtures: 0). The seed can only be read by the all-then-color child.
- **(D) Opening-gate net.** `wave54-open` fixture-net.log:1247 `exit 0  fixtures/combinations/all-then-color.json
  (gate-only: no committed baseline)`, :1224 `18 pair(s) · 0 unexpected`, :1225 `12 … check(s) · 0 violation(s)` (brief
  R6 reading holds). The gitignored `apps/{android,ios,web}-harness/screenshots/` still hold that net's last child's
  captures (mtime 16:52:47–16:53:05, fixture-net.log mtime 16:53:06): **byte-identical to the 77fe41e8 copies on 18/18**
  (Android `5f8379d7 52d6121e 860e284b df83f9d6 8edbe69b 9053c091`, iOS `f28193cf 941991fa d067925e f0ea4761 32daf8d0
  c6557d89`, web `2223f4cb 02b59b58 7a534f7e 721b776a 6787ee01 002f0941`) — the opening tree reproduces the seed bytes.

## 5. Pins and executed mutations

Pins (all re-run on the FINAL bytes):
- **SUITE** `node --test tools/visual/label-chrome-tripwire.test.mjs` → `# tests 148 # pass 148 # fail 0`
  (`suite.final.out.txt`; 130 stem lines all `ok`, `suite.final.stems.txt`; 3 `exempt-entry … pending` lines).
- **SIM** `bash sim-post-seed.sh` — the same three files in a mktemp mirror whose `baseline/` = 390 committed + the 18
  77fe41e8 PNGs (408) and the tracked fixtures → `# tests 154 # pass 154 # fail 0`, 3 entries `verified`,
  `001/003/005 [exempt band-identical, 0 glyph px] ok`, `000/002/004 [band-identical, 224/80/65 glyph px] ok`
  (`sim-post-seed.out.txt`). This is the U2-seed tripwire run, executed on the 77fe41e8 bytes.
- **R0–R3 through the committed code** `node replay-committed.mjs` → `REPLAY OK` (`replay-committed.out.txt`):
  R0 reproduces 001 / 003 / 005 RED (i) **437/437, 369/369, 293/293** ×3 (the record's "437/437 for all three" is
  corrected); R1 3 entries verified against the post-seed listing, 6/6 green; R2 m1–m5 RED; R3 130 stems green,
  **verdict objects identical to HEAD 130/130**, exempted 0. Its R0/R1/R2 stem and mutation lines are identical to the
  brief's prototype output (only the two section headings' wording differs).
- **DUMP** `verdict-dump.mjs` raw `checkTriplet` JSON for all 136 triplets: `verdict-dump.head.txt` (dumped from HEAD's
  test file BEFORE the edit, sha256 `2292046a…a9ce`) = `verdict-dump.u1.label-chrome-check.txt` =
  `verdict-dump.u1.label-chrome-tripwire.test.txt` (via the re-export).
- **Brief replay** `wave54-plan/label-chrome-all-reset.replay.mjs` (imports from the test file): output identical at
  HEAD and on U1 (`brief-replay.head.txt` = `brief-replay.u1.txt`, = the brief's recorded `.replay.out.txt`).

Executed mutations (`mutations.py` → `mutations.out.txt`): each a single-occurrence replacement in the working-tree file,
pin(s) RED, restore from the pre-edit bytes, sha256 == pre, pin(s) GREEN. Pre/post sha256 = `sha256.final.txt` for all 9.

| id | file · mechanism | pin → red (executed) | restored |
|---|---|---|---|
| MUT-1 | check · clause (iv) deleted | SUITE fail 1 (exempt-mutation (iv) test) | `fad37378…` == pre, green |
| MUT-2 | check · `checkExempt` keeps P = name | SUITE fail 3; SIM fail 6 incl. 001/003/005 RED (i) 437/369/293 ×3 | == pre, both green |
| MUT-3 | check · `nodeKind` leaf → container | SUITE fail 2 (manifest mutations, hint) — brief m4 | == pre, green |
| MUT-4 | check · `fixtureSeeded` always false | SUITE fail 1 (stale entry hides as pending) — brief m5 stale half | == pre, green |
| MUT-5 | test · stem loop ignores the manifest (the wave-53 oracle) | SIM fail 3: 001/003/005 RED (i) ×3 (inert pre-seed: PLAN R1 "PNGs without U1" reproduced) | `9d8f2b22…` == pre, green |
| MUT-6 | manifest · 005 `why` text → container | SUITE fail 1 (exempt-entry 005); SIM fail 2 (entry + stem) | `5d5ea832…` == pre, both green |
| MUT-7 | check · `labelAbsentEverywhere` → false | SUITE fail 1 (hint test) | == pre, green |
| MUT-8 | check · moved `MASK_TOL` 1 → 0 | DUMP 9 verdict lines differ from HEAD (R3 can fail) | == pre, 0 differ |
| MUT-9 | check · unseeded entry → `verified` | SUITE fail 1 (pending control) | == pre, green |

Plus the HINT on real data (`sim-post-seed.hint-demo.out.txt`, mirror-only mutation forcing every entry `pending`):
3 HINT lines, each naming exactly the committed manifest line (001/003 container, 005 text), and the stems stay red.

**G — geometry.** `label-chrome-all-reset.geometry.py` (`geometry.rerun.out.txt`): default (77fe41e8 copies) 18 × OK,
6 × band-identical True, 18/18 vs 3/18, exit 0; `--naive` 9 × WRONG (001/003/005 ×3), exit 1 — both byte-identical to
the brief's record; `baseline` today exits 1 (`missing capture`: the 18 names are not committed yet), so the post-seed
read can fail. `geometry-gate.py wave53-final --base wave53-open --lanes L7 --self-test`: 0 keys, HOLDS, exit 0 (vacuous;
§1 item 4).

## 6. Predictions (non-corpus, `expectations.json` notes) and must-not-move

- T1–T3 RED → green: **HIGH** — executed on the 77fe41e8 bytes through the real test file (SIM) and the committed code
  (R1); the red half is R0 and MUT-5.
- T4 000/002/004 green with 224 / 80 / 65 glyph px: **HIGH** (SIM, R1).
- T5 130/130 verdict objects identical: **HIGH** (DUMP + R3, executed).
- m1–m5 red: **HIGH** (R2 executed through the committed code; MUT-1/3/4 pin the mechanisms).
- Tripwire file: 148 tests on U1 alone (130 stems + 18), **154** after U2-seed (136 stems + 18): **HIGH**.
- T6 fixture net `exit 0` with `✓ no regressions vs baseline (18 platform-comparisons ran)`: **MED-HIGH** (registered),
  floor exit 0. Evidence: the wave54-open net's own all-then-color captures equal the 77fe41e8 bytes 18/18 (§4 D).
- Fresh seed byte-identical to 77fe41e8 on 18/18: registered **MED**; the evidence (§4 D, plus skeptic round 2's "the 9
  gate fixtures carry nothing L3/L4/L5 reach" and §4 C) supports MED-HIGH, but a landed lane may still legitimately move
  a pixel — then it is named and looked at.
- Corpus: **0 gained / 0 lost / 0 movers / 0 newly measured** attributable to L7: **HIGH** (census (A): no capture-path
  reader; WPT mode draws no chrome).
- **Must not move (33 cells, all P at wave53-final = wave54-open):** css-cascade `all-prop-001` web 0.9689 / ios 0.9641 /
  android 0.9642; `all-prop-002` 0.999 / 0.9709 / 0.9702; `all-prop-inherit-color` 1 / 0.9993 / 0.9992;
  `all-prop-initial-color` 0.9787 / 0.9993 / 0.9992; `all-prop-initial-visited` 0.9839 / 0.999 / 0.9995;
  `all-prop-revert-color` 1 / 0.9993 / 0.9992; `all-prop-unset-color` 1 / 0.9993 / 0.9992; css-display
  `display-contents-button` 0.9772 / 0.9692 / 0.9705, `-details` 0.9724 / 0.968 / 0.9693, `-fieldset` 0.9708 / 0.968 /
  0.9693; css-ui `appearance-revert-001.tentative` 1 / 0.9897 / 0.9895. And every other cell.

## 7. Hand-offs

- **Seam patches: none** (PLAN §3 registers none for L7). No hunk for another lane.
- **`hunk-for-orchestrator-1.patch`** (docs, for the U2-seed commit; base db6e8aa0, `docs/DYNAMIC_CAPTURE.md` sha256
  `4258766a…fdc57`, anchor §5 tripwire paragraph :297-303; `git apply --check` clean): describes the exempt manifest,
  `pending`, clause (iv), the HINT and the two rejected inference routes, and corrects the stale "(iii) P-mask bytes agree
  within ±1" sentence (`checkTriplet` never asserts it).
- **Tooling count** (doc-staleness derives it live, `doc-staleness-check.sh:85`): this file's tests **+12 at U1**
  (136 → 148) and **+6 at U2-seed** (→ 154). The README / CLAUDE.md / STATUS suite tables are restamped by the
  orchestrator at the §4 step-5 sweep (and again after the seeding if the sweep precedes it).
- **Orchestrator doc lines for U2-seed** (brief U2.5, unchanged except item 3 of §1): `docs/STATUS.md:19` (390 / 130 →
  408 / 136); the trailing all-then-color comment in `tools/visual/gate-fixtures.txt`; the "Gate: … Baselines: NOT
  captured" sentences of the fixture `_comment` (the converter reads only `components` — `CssParsing.kt:59` — so a
  `_comment` edit does not change the IR; still re-run the conversion and diff to be sure); BACKLOG 0(e) discharged
  with the corrected cause (the `all` reset never reaches the chrome; the oracle lacked the "iff"); a correction line
  (not a rewrite) under `wave53-gate/_note.md` "Post-net correction" and `docs/STATUS.md:2678` (437 / 369 / 293, not
  "437/437 for all three"; "its `all` reset reaches the chrome" is false).

## ORCHESTRATOR WINDOW REQUESTS

**[W-L7] the seeding** — on the CLOSING tree, after the stage-2 probe decisions (PLAN §8 step 10), quiet host, after
§8 7a `kill_own_gradle_daemons` (L7 ran no Gradle; its TREES line is the shared tree only).
1. `UPDATE_BASELINE=1 ./test-all.sh fixtures/combinations/all-then-color.json` → exit 0 (exit 7 = short column: fix the
   capture, never `SKIP_<P>`). Then `git status --short tools/visual/baseline/` must list EXACTLY the 18 files of §3 as
   new and nothing modified.
2. LOOK: `python3 tools/titan/results/wave54-label-chrome/look-seed.py baseline <out.png>` → the label in the band on
   000 / 002 / 004 only; 001 / 003 / 005 band empty; green fills at (16,16); 005 "123" at the right edge.
3. `python3 tools/titan/results/wave54-plan/label-chrome-all-reset.geometry.py baseline` → 18 × `→ GEOMETRY OK`,
   6 × `band rows 0..15 byte-identical ×3: True`, `18/18`, exit 0.
4. `for p in Android iOS web; do for f in tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8/${p}__*.png; do cmp "$f" "tools/visual/baseline/$(basename "$f")" || echo "DIFF $(basename "$f")"; done; done`
   → expected no output (identical 18/18, MED). Any DIFF is named and looked at against the step-2 montage before
   committing, never waved through.
5. `node --test tools/visual/label-chrome-tripwire.test.mjs` → `# tests 154`, `# pass 154`, three `exempt-entry …
   verified` lines, three `[exempt band-identical, 0 glyph px] ok` lines (`sim-post-seed.out.txt` is the dry run).
6. `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` → `# fail 0` (R9: the full tooling suite on the ship tree,
   after the seeding — the step wave 53 skipped).
7. R8 at the closing net (or `BASELINE=1 ./test-all.sh fixtures/combinations/all-then-color.json`) → `exit 0`,
   `✓ no regressions vs baseline (18 platform-comparisons ran)`, `18 pair(s) · 0 unexpected`, `12 … · 0 violation(s)`.

Decision: steps 1–6 hold → commit U2-seed (18 PNGs + §7 doc lines). Step 3 WRONG, step 5 or 6 red, or a step-4 DIFF
whose picture is wrong → do NOT commit the PNGs: U1 stays (green alone, entries `pending`), all-then-color stays
gate-only, and the cause is found in the picture before anything else.

## What I could NOT verify

- The seeding itself, the post-seed geometry read and the T6 fixture-net comparison against committed baselines (device;
  [W-L7]). The SIM and R1 runs use the 77fe41e8 bytes, which the opening net reproduces 18/18, not the closing tree's.
- The full tooling suite (`tools/visual/*.test.mjs tools/titan/*.test.mjs`) and the doc-staleness job: not run (focused
  runs only); the +12 / +6 deltas are this file's own counts.
- Node: run under v22.21.0 on this host (CI uses Node 24); nothing version-specific is used beyond `??=`.
- A known limit of clause (iv), kept as the brief designed it (§9 R2: "every pixel", to avoid false reds from a
  component's own band paint): on an exempt stem, a label drawn PARTIALLY or mis-positioned (not every would-be pixel
  lit) is not red per stem when the band is not ground ×3 (glyph-mask mode). The same chrome code draws every label-due
  stem, whose clause (i) would catch a mis-positioned label suite-wide (133 label-due stems post-seed); not pre-built.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (no export tree; no Gradle / xcodebuild run by this lane)
STATUS: COMPLETE

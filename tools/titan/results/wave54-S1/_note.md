# Wave 54 · S1 — combined-tree skeptic

S1 is the combined-tree skeptic of wave 54 (wave skill Phase 3; `wave54-plan/s1-workflow.js`). It covers:
- the shared tree `campaign/applier-campaign`;
- base `1cde1f48` and the landed range `1cde1f48..d64d4c6e`: the lane-records commit `fcf9fa9e` plus the 17 revert units in
  PLAN §4 order (U2-seed and L5 U2-ios are not on the tree);
- the orchestrator commits after landing, which were also read: `094bd918` (s1-workflow.js), `c7e37409` (sweep + doc
  restamp) and `8b8bff58` (the [W-L3] record). HEAD at the end of this audit is `8b8bff58`.

**What S1 did and did not touch.**
- The shared tree was not modified, except for this directory.
- Every mutation ran in a throwaway `git archive d64d4c6e` export in the session scratchpad, with the gitignored inputs
  (`node_modules`, `tools/wpt`, `fixtures/wpt`, `tools/titan/runs`, `wpt-buckets.json`, `local.properties`) symlinked.
- Gradle always ran with `--no-daemon`, and Catalyst used a private `-derivedDataPath`.
- No device, emulator, simulator or Chromium was used. There was no git commit, checkout, stash or reset.
- No daemon is left behind. The three JVMs alive on the host date from 19:11 / 19:19 and are not S1's; they were left
  alone.

Every script and output named below is beside this note.

## Verdict: MIXED. The combined-tree claim holds. There is no must-fix; there are nine should-fix items and ten nits.

What holds, by execution:
- **Seams.** All 8 landed seam patches reproduce their unit commit's bytes exactly, and no seam hunk is unregistered.
- **Suites.** The single-writer sweep is green on every suite, and each count delta equals the tests the lanes added.
  The doc tables are restamped and `doc-staleness-check.sh` exits 0.
- **Git.** Every landed path sits in its own lane's unit commit.
- **Carriers.** S1's own static census reproduces every lane's carrier set. No radius is under-reported.
- **Mutations.** 17 lane mutations go red, restore byte-exact and go green again. That covers all seven lanes, the four
  natives / web / tooling / converter suites, and two Swift mutations.
- **Wire.** The [W-L3] gate-flag differential gives content-changed = `unionWireCarriers` (23) exactly and renumbered =
  15 (`cssom/*`). Every one of the 23 content changes is a registered wire class.

What does not hold: open pin gaps and silent counters from the lane skeptics, which no fix pass addressed (re-executed
here); one gate-instrument hole that stage 1 will lean on; evidence logs that will not land; and PLAN.md prose that is
stale against the regenerated `expectations.json`.

## Executed checks

### 1. Seam-hunk audit
**How it was run.** `seam-replay.sh`, output in `seam-replay.out.txt`. For each unit commit that touches a seam, every
file of that lane's patch was applied to the commit's PARENT bytes (in a scratch git dir), and the result was compared
with the commit's bytes.

**Patch replay: 14 / 14 files EQUAL, 0 DIFF.**

| commit | unit | patch (all files) | result |
|---|---|---|---|
| `e86d3dd2` | L2 TB-android | `wave54-table-body-cell/seam-1.patch` | ComponentRenderer.kt + TableCellHugSeamWiringTest.kt EQUAL |
| `110f4b4e` | L6 RS | `wave54-web-tail/seam-1.patch` | ComponentRenderer.tsx + ComposedCaptureGallery.tsx + ComposedRootSeparatorWire.test.tsx EQUAL |
| `a5708b7e` | L5 U1-android | `wave54-ua-heading-face/seam-1.patch` | ComponentRenderer.kt + UAElementFontRuleSeamWiringTest.kt EQUAL |
| `5b333c8a` | L4 CBB-android | `wave54-oof-layout/seam-1.patch` | ComponentRenderer.kt + ChildContainingBlockSeamWiringTest.kt EQUAL |
| `b5b721fd` | L3 U2-android | `wave54-hyphenate-character/seam-1.patch` | ComponentRenderer.kt EQUAL |
| `20b65b57` | L3 U2-ios | `…/seam-2.patch` | ComponentRenderer.swift EQUAL |
| `1d8fe364` | L3 U3 | `…/seam-3.patch` | extract-fixture.mjs + extract-fixture-br-line-context.test.mjs EQUAL |
| `d64d4c6e` | L3 U3b | `…/seam-3b.patch` | extract-fixture.mjs EQUAL |

**Combined diff `1cde1f48..HEAD` per seam file.** Every hunk is comment-headed (read in full). No hunk falls outside these
rows.
- **`ComponentRenderer.kt`: 6 hunks → 4 §3 rows**, with disjoint base ranges:
  - @@1138 → L5 :1141;
  - @@1930 → L4 :1934;
  - @@2650, @@2666 and @@2691 → L2 :2653-2697;
  - @@7101 → L3 :7072 (inserted after :7101, as the patch header says).
- **`.swift`: 2 hunks → 1 row** (L3 :5011 / :5026). L5's row is HELD and has no hunk, which is correct.
- **`.tsx`: 2 hunks → 1 row** (L6 RS: the exported `wsAfterSeparator` after `isInlineLevelSibling`, and the lift at
  :1112). W1 is branch R, so there is no seam-2.
- **`extract-fixture.mjs`: 3 hunks → 2 rows.**
  - U3 has the re-arm in the children loop.
  - U3b has the `brHostLineBoxPx` helper (@@10141), the br rule (@@11456) and the `childLineCtx` seed line (in the
    @@11703 region).
  - The two patches touch different lines, and both orders apply (the lane verified this).
- **Header check.** All 8 patch headers name the base tree, the seam file's sha256 at base and the anchors.

### 2. Sweep (`wave54-gate/sweep-landed.txt`, tree d64d4c6e)

**The log.** Every suite is rc=0:

| suite | count |
|---|---|
| tooling | 2329 tests: 2325 pass, 4 env-gated skips |
| conformance | valid |
| web runtime | 1379 |
| web-harness | 344 |
| converter | 562 |
| compose | 3491 |
| android-harness | 169 |
| swiftui | 2212 |
| ios-harness | 30 / 30 |

**Every delta is accounted for.** Each count delta against the pre-landing doc tables equals the tests added in the
landed range. I counted `@Test`, `func test…`, and `it(` / `test(` declarations at d64d4c6e against 1cde1f48:

| suite | delta | where it comes from |
|---|---:|---|
| compose | +63 | 12 test files (`L2` 17, `L3` 12, `L4` 22, `L5` 12) |
| android-app | +1 | ComposedCanvasTableBodyTest |
| converter | +11 | HyphenateCharacterPropertyParserTest |
| swiftui | +20 | FixedHoist 2, GapDecorationSegments 4, GreedyLineBreaker 6, OutOfFlowContainingBlock 8 |
| web | +10 | InertOutOfFlowWordJoin.test.tsx |
| web-harness | +14 | 7 + 7 |
| tooling | +38 | 19 static in bidi-bake, 7 in br-line-context, 12 in the tripwire (incl. loop-generated) |

**Re-run in the export** (to check counts, not as the record):
- tooling: `# tests 2329 # pass 2325 # skipped 4 # fail 0` (`tooling-export.summary.txt`);
- web runtime: 1379 / 1379;
- web-harness: 344 / 344;
- conformance: "all documents valid".

**Doc rows.** The orchestrator restamped them in `c7e37409`, which ran during this audit. Each value was checked against
the sweep:
- README.md, CLAUDE.md and docs/STATUS.md suite tables, 7 rows each:
  - converter 551 → 562;
  - web 1369 → 1379;
  - compose 3428 → 3491;
  - android-harness 168 → 169;
  - swiftui 2192 → 2212;
  - web-harness 330 → 344;
  - tooling 2291 → 2329.
- The real-applier floor, Android 15 → 16 / 558 (L3's HyphenateCharacterApplier.kt), and the applier-file count
  44 → 45, in CLAUDE.md, README.md and STATUS.md.
- The tree READMEs: android-harness ×2, web-harness, compose, swiftui, web.
- `tools/visual/COVERAGE.md`, regenerated.

`bash tools/visual/doc-staleness-check.sh` at HEAD: exit 0, "✓ all doc claims match live source-of-truth output"
(`doc-staleness.out.txt`). `coverage-audit.mjs` (stdout mode) prints `real: android=16 ios=70 web=516`, `applier files
android=45`.

**Rows that still wait for later steps (not restamp defects):**
- after U2-seed: tooling +6 (154 tripwire tests), `docs/STATUS.md:19` (390 / 130 → 408 / 136), the
  `gate-fixtures.txt` comment, and the fixture `_comment`;
- L7's `DYNAMIC_CAPTURE.md` §5 hunk (see S9);
- L1's `ListMarkerOutsideHang.swift` comment, only if M′ survives stage 1. `git apply --check` is clean for both hunks.

### 3. Git hygiene
- **`git status --short`:**
  - At S1 start it was NOT empty. The orchestrator's doc restamp (8 files) and the W-L3 outputs were in flight; they were
    committed later as `c7e37409` / `8b8bff58`.
  - Now the only entry is `?? tools/titan/results/wave54-S1/` (this report).
- **`git stash list`:** empty.
- **`tools/titan/runs/wave54-lock/`:** exists and holds no lock.
- **Per-commit file lists** (`git show --name-status` for each of the 18):
  - The records commit `fcf9fa9e` holds only the 7 lane dirs (1000 files). That includes L4's two
    `census-*.base.txt`, so either OOF unit reverts alone; L4 skeptic must-fix 2 is resolved this way.
  - Every unit commit holds exactly the paths of its lane's `own:` list or its seam patch. No path sits in another
    lane's commit.
  - L5's commit carries `UAElementFontRule.swift`, a docs-only edit owned by L5 by the plan.
- **No probe files in the source trees:** the added files outside `results/` contain no `probe` / `tmp` / `scratch` /
  `.bak` / `.orig` / `census` name.
- **No build artefacts committed:** no `.class` / `.jar` / `build/` / `node_modules` / `.DS_Store` / `DerivedData`.
- **Vitest cannot pick up the lane records:** the 6 `*.census.test.tsx` / `sk*.test.tsx` files under
  `results/wave54-web-tail/` sit outside both vitest roots (`runtimes/web`, `apps/web-harness`, default include). The
  sweep's 1379 / 344 confirm it.
- **Evidence that does not land.** 76 gitignored `*.log` files sit in lane dirs, several of them cited as evidence
  (defect S2):
  - L3: 5;
  - L4: 5;
  - L1: 1;
  - L5: 58;
  - L6: 7.

### 4. Pointer audit
**Paths.** `pointers.py` and its outputs `pointers.out.txt` / `pointers.reresolve.out.txt` cover every backticked
path-like token in the 7 `_note.md` and 7 `skeptic.md`:
- 839 resolve and are committed as written;
- 99 more resolve repo-wide or inside the lane dir (bare basenames of source files).

23 do not resolve, and every one is expected:
- L7's 18 U2-seed PNGs (not seeded yet);
- `wave54-pre` captures (stage 1 not run yet);
- L5's held files, which exist as `held/*.txt` (`shasum -c held/SHA256SUMS.txt`: 12 / 12 OK);
- transient lock paths;
- L6's PLAN-path `src/ui/ComposedRootSeparator.test.ts` (the note's D1 says the tree has `tests/ui/`);
- `step0-harness-markup.json` (the note says it never existed).

42 resolve to files that exist but are gitignored. The input corpora (`runs/`, `tools/wpt`, `fixtures/wpt`, `build/`) are
expected; the lane `*.log` records are defect S2.

**Test names.** 44 cited test identifiers were found in the tree with `git grep` (`testnames.out.txt`). The one apparent
miss is L2's abbreviated `::declaredBodyRoot`, which is `declaredTableBodyRoot_keepsTodaysStroke_noHug`.

**Cells.** `pred-cells-check.py` (output `pred-cells-check.out.txt`) checks all 115 `expectations.json` prediction rows
that name a cell. Their "from" value equals `cells.mjs` on **wave54-open AND wave53-final** for **115 / 115**.

### 5. Census replay (S1's own static models; `census.py`)
**Method.** The script works over all 1435 `tools/titan/runs/wave54-open/sections/*/per-test-ir` documents plus the
section manifests. The models are written from the landed code, not from the lanes' scripts:
- **L1 P:** the outermost bake root with non-zero padding and no guard.
- **L1 M′:** `markerText` together with ABSOLUTE / FIXED.
- **L2:** a body-root whose last Display is a table.
- **L3 U1:** a string that decodes differently.
- **L3 U3 / U3b:** buildNode's br state machine, replayed per runs-host.
- **L4 OOF:** an establisher with an out-of-flow descendant, or FIXED with no declared inset; Swift counts FIXED
  descendants only.
- **L4 CBB:** a non-border-box parent with bands, plus a reader. The reader is found transitively through `%`-sized
  in-flow boxes.
- **L4 GAP:** a ruled zero-gap flex axis with no spacing distribution.
- **L5:** a leaf h1-h6, or a sub / sup.
- **L6 RS:** root pairs under the ws-after inline-level predicate.
- **L6 W1:** an inert member that splits a mid-word `auto` host.

**Results** (outputs: `census.wave54-open.out.txt`, identical on wave53-final in `census.wave53-final.out.txt`; vs
`expectations.json` `revertUnits`):

| unit | mine | plan | |
|---|---|---|---|
| L1 P | 6 roots in 4 docs (android + wire) | 4 | **EQUAL** |
| L1 M′ | counter-suffix ×3 + wire | same | **EQUAL** |
| L2 TB-android | 006 android | same | **EQUAL** |
| L3 U1 wire | 4 docs (001/002/003/005) | 4 | **EQUAL** |
| L3 U3 wire | **54 brs in 18 docs**; the base replay reproduces every br height on the wire (0 mismatches) | 18 | **EQUAL** |
| L3 U3b wire | 12 brs in 4 docs | 4 | **EQUAL** |
| L3 U2 reach | ⊆ the 6 docs that carry the 14 `hyphenate-character` declarations: iOS 5 (005's host has no text), Android 3 (002 and limit-chars are dictionary runs) | same | consistent |
| L4 OOF-android / OOF-ios | 9 / 6 | 9 / 6 | **EQUAL** |
| L4 CBB-android | 13 ⊂ plan 14 | 14 | the missing one is `flex-gap-decorations-006`, the plan's watched vtext side reader, which S1's model omits |
| L4 GAP | 033 ×2 | same | **EQUAL** |
| L5 U1-android | 10 ⊃ plan 7 | 7 | the extras are inset-005 / -006 and subelements-002, whose sup / sub are consumed by an engaged host fold. S1's model cannot see the fold; the lane's executed fold census and the re-verifier say `Folded`, and the natives of all three are must-not-move, so R4 / R7 would catch a leak |
| L6 RS | **73 separators in 27 docs** | 27 | **EQUAL** |
| L6 W1 | 4 joins in 1 doc | 1 | **EQUAL** |
| L7 | 11 `All` documents (must-not-move only) | | |

On the 2 non-equal rows, the plan's set ⊇ S1's set: **no under-reported radius.**

**Cross-lane check on the LANDED wire** (`census.post-landed.out.txt`, the [W-L3] post trees): the L2 / L4 / L5 / L6
reaches are unchanged by L1's and L3's wire edits. The L3 rows differ by exactly the 66 landed brs (54 + 12), as they
must, and the L1 roots no longer match because their padding is now 0.

### [W-L3] the gate-flag wire differential (`wave54-gate/wl3-diff.out.txt`; trees parked at `runs/wave54-wl3-diff/`)
**Counts:** `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0`, `exit 0`.

**Set check** (`wl3-setcheck.out.txt`):
- content-changed == `expectations.unionWireCarriers` (23): **True**;
- renumbered == `wireRenumbering.expectedShadow.documents` (15, all `cssom/*`): **True**;
- the three zero-padding selectors documents are identical;
- the ring-fenced `backdrop-filter-basic-blur` did not change.

**Kind-of-change audit** (`wirekind.py`, output `wirekind.out.txt`, which diffs pre and post per component name).
Every change is a registered class:

| change | count |
|---|---|
| `Padding*` → `{px:0}` | anchor-center-safe-rtl ×8, bidi-lines-001 ×2, -002 ×2, counter-suffix ×4 |
| br Height 20 → 0 | 54 (the U3 count) |
| br Height 20 → 19.2 | 12 (the U3b count) |
| `Generic` → `HyphenateCharacter {string,""}` | 001, 002 |
| `"\2022"` → `"•"` | 003 |
| `"\00a0\0640"` → `" ـ"` | 005 |
| counter-suffix 23 → 29 components | 6 added runs; 4 items `ListStyleType none` and `−markerText` |

The tally prints `unclassified 4`. Those 4 are the M′ `ListStyleType "none"`, which the classifier expected in upper
case; it is a registered class. **R4b holds on the landed tree.**

### 6. Mutations (17 lane pins, two or more per lane; plus 2 skeptic survivors re-confirmed)
**How they ran.** The runner is `mutate.py`, with literals in `lit/` and the log in `mutations.out.txt`. Each mutation:
- asserts its anchor occurs exactly once;
- applies the mutation;
- runs the focused suite;
- restores the file from its saved bytes;
- checks sha256 before == after;
- re-runs the suite.

Compose runs use `gradle-rerun.sh` (`--rerun`, fresh JUnit XML) and Catalyst runs use `xc.sh`. **Every restore was
BYTE-EXACT and every re-run GREEN.**

| lane | mutation (file) | red? | evidence |
|---|---|---|---|
| L7 | MUT-1 clause (iv) deleted (label-chrome-check.mjs) | RED | 147 / 148: `exempt mutation … (iv)` |
| L7 | MUT-3 `nodeKind` leaf → container | RED | 2 fail (manifest mutations, hint) |
| L1 | P-3 drop the non-zero guard (bidi-bake.mjs) | RED | V3b; mutated sha `98324f59` = the lane's |
| L1 | M-12 owner = li | RED | V1, V6, V7, V4; mutated sha `a9aed43a` = the lane's |
| L3 | U3-a drop the re-arm (extract-fixture.mjs) | RED | 3 not ok (001, revert-val-001, ws-only) |
| L3 | U3b-a the inherited reading | RED | baseline-007 / -002 negative |
| L3 | U1-a drop hex decoding (CssStringParser.kt, Gradle) | RED | "11 tests completed, 4 failed"; sha `af42bd37` = the lane's |
| L3 | U2I-a SpentHyphen keeps the hyphen (Catalyst) | RED | GreedyLineBreakerTests "Executed 19 tests, with 3 failures" |
| L2 | T1 drop `!anonymous` (TableCellHug.kt) | RED | 145 tests, 2 failed |
| L4 | M-a `"Contain" -> false` (OutOfFlowContainingBlock.kt) | RED | 145 tests, 6 failed |
| L4 | M-gap1 restore `!gap.isEmpty` (GapDecorationLines.kt) | RED | 47 tests, 2 failed |
| L4 | S-Ma Swift `"Contain" → false` (Catalyst) | RED | "Executed 62 tests, with 8 failures"; P3 corpus pin names the 2 missing carriers |
| L5 | G2 `headingStandsDown` → true | RED | 12 tests, 3 failed; sha `5be9d073` = the lane's |
| L5 | M2 em base without MonospaceUAFontSize | RED | 12 tests, 1 failed; sha `0579136a` = the lane's |
| L6 | RS-m2 table decline removed (ComposedRootSeparator.ts) | RED | 2 tests: table / tableBody declines |
| L6 | W1-m4 `auto` gate dropped (InertOutOfFlowWordJoin.ts) | RED | (d) -001 box 4 |
| **L2** | **X3 (L2 skeptic S1) `widthAtMaxIntrinsic` → `widthAtMinIntrinsic`** | **SURVIVED** | fresh XML: TableBodyForest 7 + TableCellHug 8 + SeamWiring 2 executed, 0 failures → defect S5 |
| **L6** | **SK6-w7 (L6 skeptic D1) NodeRenderer `{ hyphensAuto: true }`** | **SURVIVED** | the whole web runtime suite, `vitest run`, rc 0 → defect S3 |

**Baselines in the export:**
- focused Compose: 145 / 0;
- Catalyst, 4 classes: 62 / 0;
- tripwire: 148 / 148;
- bidi-bake: 69 / 69;
- br-line-context: 7 / 7;
- harness RS: 14 / 14;
- web renderer: 138 / 138.

**Re-executed lane-skeptic check.** I replayed the L1 skeptic's R13 fakes (`skeptic/geom-fakes.py` into a scratch root;
output in `geom-fakes.replay.out.txt`). A counter-suffix capture with the `.` run whitened prints `→ GEOMETRY OK` on:
- web, in `rtl-marker-bake.geometry.py`;
- web and iOS, in `lists-bakes.geometry.py`.

So that defect stands (S1).

**Geometry gate.** `geometry-gate.py wave53-final --base wave53-open --self-test` on the regenerated expectations
(output `geometry-gate.selftest.out.txt`):
- 185 keys;
- gating 37, all FAIL;
- control 112, all PASS;
- report 36, all FAIL;
- `self-test: HOLDS`.

### 7. Honesty
- **"runtime", not "engine".** Two added lines use "engine":
  - `ComposedRootSeparator.ts:25` "The engine entry point" is new (N4);
  - the lifted WWS comment "RAW engine output" is pre-existing text that moved.
- **Size.**
  - Every NEW source file is ≤ 200 lines; the largest are 199 (UAElementFontRule.kt, bidi-marker-bake.mjs,
    label-chrome-check.mjs).
  - Two new test files are larger, both disclosed: br-line-context 264, UAElementFontRuleTest 243.
  - The oversized files got only call sites, defaulted parameters and conditions. CanvasRootHoist's RC1 `when` and
    FixedHoist's `switch` are disclosed. `bidi-bake.mjs` is +105 / −4 → 1273 (the L1 note says +101; N5).
- **Comments.** `comments.py` is a heuristic over the added code lines of the source files (output `comments.out.txt`):
  - it counts 305 of 1060 lines as having no comment on or directly above them, most of them argument continuations;
  - block-level commenting holds on every new file I opened;
  - the outlier is `bidi-marker-bake.mjs` with 89, wave-53 code restored verbatim (L1 skeptic nit 6, still open; N6).
- **DEGENERATE labels.** `stayDegenerateEvenIfPass` has 8 entries: bidi-lines-002 android, counter-suffix ios / android,
  block-ellipsis-002 ios, contain-content-011 android, and hyphenate-character-002 ×3. L4 should-fix 3 and L3 S1 are
  therefore applied. `degenerateByConstruction` (hyphenate 001 / 003 / 004) and the retirement check are in place.
  - Missing: L6 skeptic D4. `semi-replaced-stretch-other` web is predicted P 0.967 with its "abel" label residue
    unlabelled, and `scope-pseudo-element` web (a LOW mover, "B/Foo wrap remains") would be DEGENERATE if it passes.
    Neither is registered (S8).
- **"Never silent".** `InertOutOfFlowWordJoin.ts:27-29` and `InlineRuns.ts:50-54` say every join is "counted, never
  silent", but `joinedOutOfFlowMembers` is read nowhere. `grep` finds the definition and the producer only (S4).

## Defects (ranked)

**should-fix**
1. **S1 · plan instrument (L1 M′, before stage 1).** `rtl-marker-bake.geometry.py` `[M]` and `wave53-plan/lists-bakes.geometry.py`
   print GEOMETRY OK on a counter-suffix capture whose `.` marker run is missing: web on both probes, and iOS on
   lists-bakes (re-executed). Stage 1 decides M′ with these probes, and Android has no M′ SSIM floor (its 0.970 floor is
   P's).
   - Fix: also require each RTL row's marker-ink LEFT edge at the ref's x133 ±2.
   - Then re-run `--self-test`: the no-dot fake must turn WRONG, while the ref and wave53-probe web / iOS stay OK.
2. **S2 · evidence of record that will not land (L1, L3, L4, L5, L6).** 76 `*.log` files in lane dirs match
   `.gitignore:21 *.log`, so they are not committed:
   - L3 `mutations.log` (528 lines) and `seam-verify.log`, which the L3 note calls "the record of value for every claim";
   - L6 `verify-*.log` ×7, cited for every RS / W1 mutation;
   - L1 `mutations.log`, its "full log";
   - L5's 58 run logs;
   - L4's 5.

   The squash merge and worktree recycling lose them. Fix: `git add -f` them (or rename to `.txt`) in a records commit
   before the closing gate.
3. **S3 · L6 W1's production gate is unpinned.** With `NodeRenderer.ts:339` set to `{ hyphensAuto: true }`, the whole web
   runtime suite stays green (re-executed). The L6 skeptic's census showed `hyphens-out-of-flow-001` web (P 1,
   must-not-move) changing under it. Fix: an (f)-style pin that renders -001 box 4 through NodeRenderer and asserts
   `>high<…>­way<` stays split.
4. **S4 · L6 W1 "counted, never silent" is false at runtime.** `RunsPlacement.joinedOutOfFlowMembers` is produced
   (`InlineRuns.ts:170`) and read nowhere. Fix: one breadcrumb where `joined > 0` (beside resolveRuns' rule-5 warn), or
   reword the two comments.
5. **S5 · L2 D2 quantity unpinned.** `hugColumn`'s `widthAtMaxIntrinsic` → `widthAtMinIntrinsic` survives: fresh XML, 17
   tests executed, 0 failures. Fix: a source pin in TableCellHugTest that the enabled branch calls
   `widthAtMaxIntrinsic(` with `CELL_HUG_REFUSAL` and never `widthAtMinIntrinsic`. Also bound the vacuous
   `.then(cellModifier)` order assertion (skeptic nit 2).
6. **S6 · L1 M′ root ownership is a silent fallthrough** (L1 skeptic should-fix 1, unaddressed). Re-parenting the marker
   runs to the root drops the item's opacity, visibility, transform, filter and clip effects, and any between-box ones,
   with no decline, stamp or TODO (`grep` of `bidi-marker-bake.mjs` and the bidi-bake diff). Corpus reach is 0. Fix: at
   minimum a TODO plus a `marker-*` stamp, in a new ≤ 200-line file.
7. **S7 · PLAN.md prose is stale against the regenerated expectations** (`25cbd1c2` applied the L4 / L5 / L3 hand-offs to
   `plan-build.py` only):

   | where | PLAN.md says | now |
   |---|---|---|
   | §6 R4 | "web 44 · iOS 27 · Android 51" | iOS 24 |
   | §1 | "19 revert units" | 18 |
   | §1 ceiling | "iOS 1134 · Android 1125" | 1131 / 1122 |
   | §1 | "Four passing cells stay DEGENERATE" | 8 |
   | §3 L5 rows | `UAHeadingFoldGate.standsDown` / seam-2 | `headingStandsDown` / HELD |
   | §3 totals | 9 patches, `.swift` 2 hunks | 8 patches, 1 hunk |
   | §2 L4 | 26 predictions, CBB 11 | 29, 14 |
   | §2 L5 carriers | android 10 / iOS 3 | 7 / 0 |

   §10 records none of this. The machine checks read `expectations.json` and are right. Fix: a §10 item restating these
   before the stage-1 read-out cites §6.
8. **S8 · L6 honesty labels** (L6 skeptic D4, unaddressed). `semi-replaced-stretch-other` web (MED flip; the label paints
   "abel") and `scope-pseudo-element` web (LOW; B/Foo wrap) are not in `stayDegenerateEvenIfPass`, and the
   semi-replaced row's `kind` names no residue. Fix: add both via `plan-build.py`, or annotate the rows.
9. **S9 · L7 docs** (L7 skeptic S5). The `DYNAMIC_CAPTURE.md` §5 hunk (`wave54-label-chrome/hunk-for-orchestrator-1.patch`,
   `--check` clean) is not applied, although U1 is landed. The normative doc describes the pre-U1 tripwire. Fix: land it in
   the docs pass whatever the seeding decides.

**nits**
1. **N1.** `wave54-table-body-cell/seam1-files/…/ComponentRenderer.kt` is a full 8462-line (518 KB) copy of the seam,
   committed in the lane records (L2 skeptic nit 4). It is regenerable by `seam1-hunk.py` and pollutes `git grep`.
2. **N2.** `wave54-hyphenate-character/wl3-wire-differential.sh` exits 0 regardless of content-changed or one-sided
   counts. The orchestrator noticed on the mis-invoked first run; the instrument cannot fail by its exit code.
3. **N3.** The §3 U3b registry row names only the br-height rule. Its patch also adds the exported `brHostLineBoxPx` and
   edits the `childLineCtx` seed in U3's registered region. The patch header is complete.
4. **N4.** `ComposedRootSeparator.ts:25` says "engine" (vocabulary).
5. **N5.** L1 note: the size line says "+101 / −4" where numstat gives +105 / −4, and "29/29 OK" where 30 were executed.
6. **N6.** `bidi-marker-bake.mjs` comment density (L1 skeptic nit 6), still open.
7. **N7.** Orchestrator commit hygiene. `c7e37409` committed 379 partial pre-side W-L3 per-test-ir files and a 0-byte
   `wl3-diff.out.txt`; `8b8bff58` deleted them (net zero after the squash). The doc restamp also ran during this audit,
   although s1-workflow.js says the restamp comes after the S1 report. The tree is clean now.
8. **N8.** Two new test files are over 200 lines: br-line-context 264 and UAElementFontRuleTest 243 (both disclosed).
9. **N9.** L7 note §3 elides three U2-seed names (`iOS__001_…`, `iOS__003_…`, `iOS__005_…`). [W-L7] must use the full
   names (L7 skeptic S1).
10. **N10.** Open lane-skeptic test gaps that reach no corpus document were not re-run here:
    - L3 SK-X1 / X2 / X3 / U2I-g;
    - L3 N1 (`hyphenate-character` not inherited on the natives, no TODO);
    - L4 K-X4 / S-X7 and the unpinned breadcrumbs;
    - L6 D3;
    - L7 S2-S4 and X1 / X4 / X8.

**Resolved since the lane skeptics** (checked):
- L4 must-fix 1: CBB-android captures 14, union android 51 with L5's −3.
- L4 must-fix 2: records-first commit, base files outside the units.
- L4 should-fix 3: 011 android degenerate-even-if-pass.
- L5 must-fix: GO-SMALL; the inset rows withdrawn to must-not-move.
- L3 S1: 002 ×3 degenerate-even-if-pass.

## What S1 could NOT check
- **No device, Chromium or simulator.** Every picture claim (stage 1, stage 2, closing gate) is still the probes'. The
  census is a static model of each trigger, not a capture: the L3 per-platform capture subsets were not re-derived, the
  L5 fold consumption is not modelled, and the CBB vtext reader is not modelled.
- **Full native suites.** I did not re-run the full Compose (3491), android-app (169), Catalyst (2212), converter (562) or
  ios-harness (30) suites. Their counts come from the sweep log; I ran focused re-checks of them instead. The sweep log
  does not show `--rerun-tasks` / EXECUTED for compose; my export re-run of 145 focused tests with fresh XML is green.
- **[W1] / [W2] / [W2b] / [W-L6] step 0.** I did not re-run them; I read their recorded outputs. The [W-L3] trees were
  read directly.
- **`integrate-seams.sh --dry-run`.** Not run, because it creates a worktree. The per-commit byte replay above is the
  stronger check on the landed tree.
- **Not re-executed:** the L4 CBB and GAP-ios mutations, and the L6 W1 mutations other than m4.

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (read; only
`tools/titan/results/wave54-S1/` written). Export: `git archive d64d4c6e` in the session scratchpad (`s1/exp`, plus
private DerivedData `s1/dd`), throwaway.

STATUS: COMPLETE

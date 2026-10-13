# L7-instruments: wave-55 lane brief (BACKLOG "Next-wave obligations" 0(l), 0(m), 0(o))

**Evidence run: `tools/titan/runs/wave54-final`** (HEAD 0d0d3a3d, the wave-54 merge, has exactly its content).
- Every score below comes from `node tools/titan/results/wave52-gate/cells.mjs '<terms>' wave54-open wave54-final`; the output is
  `instruments.cells.wave54-final.txt`, 49 cells.
- Every PNG named below was opened: the ref next to the three captures, and every fake next to the ref.

**Nothing was built, run or captured.** The opening gate `wave55-open` is live on this tree. Every script written for this brief only
reads files, at `nice -n 19`. Everything it wrote is in `tools/titan/results/wave55-plan/`:
- `flex-zero-gap-rules.geometry.py`: the **wave-55 copy** of the flex-gap probe, which adds the horizontal-extent read (§7).
- `flex-zero-gap-rules.fakes-run.sh`, which produces `flex-zero-gap-rules.geometry.out.txt`: the wave-54 probe and the wave-55 copy,
  side by side, over 3 runs, 14 recorded fakes and 6 new ones.
- `instruments-flex-gap-fakes/make-fakes.py` (the 6 new fakes), and `score-fakes.mjs`, which produces `score-fakes.out.txt` (the
  gate metric on those fakes).
- `instruments.census.mjs`, which produces `instruments.census.out.txt`: the carrier census (§5).
- `instruments.wl3-classifier-replay.sh`, which produces `.out.txt`: the wave-54 [W-L3] classifier, extracted verbatim and run on the
  parked trees and on a mis-invocation.
- `instruments.renumber-rule.mjs`, which produces `.out.txt`: the wave-53 RENUMBERED rule, measured on the parked wave-54 trees.
- `instruments.wl3-expect.wave54.txt`: the wave-54 pre-registered wire set, used as a calibration input for U2.
- `instruments.cells.wave54-final.txt`: the must-not-move cells (§6).

**Summary.** This is an instruments-and-hygiene lane. **No corpus picture changes, and every target is a must-not-move.** The
wave-54 record left four instruments that cannot fail where they should, plus one spec hole and one batch of pins that were never written:
1. **The flex-gap geometry probe has a blind axis.**
   - It reads the blue row-rule runs on columns only (`wave54-plan/flex-zero-gap-rules.geometry.py:89`).
   - So a row rule 5 px short, or one starting 4 px late, prints GEOMETRY OK ×3 at SSIM 0.999 / 0.9993 (R3V-S1).
   - Measured here: even a picture with **both row rules missing scores 0.9644**, above the 0.95 pass threshold. On this test only the
     geometry key can see a missing row rule.
   - The wave-55 copy is written and run. It turns the 2 recorded blue fakes and 3 new ones WRONG, and keeps every other verdict.
   - On `wave54-final` it prints **GEOMETRY OK on all four rows**: the shipped GAP-ios / GAP-android pictures hold on the new axis too.
2. **`wl3-wire-differential.sh` cannot fail.**
   - It exits 0 with 30 one-sided sections (replayed here: `one-sided 30`, `classifier exit=0`).
   - It walks only the sections that exist on the PRE side.
   - Its RENUMBERED class is weaker than the wave-53 rule it claims to apply.
   - A new tracked, general instrument replaces it (U2). Its classifier is calibrated on the parked wave-54 trees: 1397 / 23 / 15 / 0,
     and the strict renumber rule holds on 15 of 15 documents.
3. **The A/B arm runner wrote its records before `git checkout -f`**, and lost three hash blocks (lesson "An A/B arm runner that
   switches branches…"). U3 writes a tracked wave-55 runner that writes records only after a verified restore. It is self-tested in a
   throwaway `git init` repository.
4. **The spec and code citations (0(l)).**
   - Spec 03 §4.1 lacks the renderer-latitude paragraph that W1 relies on.
   - The proposed text would **exclude Compose's real behaviour**: Compose drops ANY inert member of a fold, not only a member that
     splits a word.
   - Four web sites cite "rule 1". Rule 1 exists ("Order is the payload"); the rule W1 departs from is **rule 2** (skeptic N5).
   - This is prose and comments only. Not a freeze event (§2.4).
5. **0(o): the queued pins.**
   - 21 pins in 8 test files, each with an executed mutation.
   - The `mask-image` / `mix-blend-mode` marker decline is unit U5. It is on the extract path, so it ships only behind a wire
     differential that comes back identical.
   - 7 items touch other source and are deferred, or handed to the lane that owns that source (§4 U6, "not pins").

**Corrections to the computed task, from the tree:**
- `tools/titan/wl3-wire-differential.sh` does not exist. The tracked file is
  `tools/titan/results/wave54-hyphenate-character/wl3-wire-differential.sh`, a wave-54 record. It stays byte-identical
  (sha256 `54b71953…`), and U2 is a NEW `tools/titan/wire-differential.sh`.
- "rule 1 … does not exist" is wrong: spec 03 §4.1 rule 1 is "Order is the payload". The citation names the wrong rule; it does not
  name a missing one.

## 1. Targets (all must-not-move) and what the pictures show

The lane changes no runtime layout path, so its targets are the cells its five units could reach. Each row below is
`wave54-final <sec>/<test> <platform> <verdict> <score>`, with the full set in `instruments.cells.wave54-final.txt`.

| reach | cells (wave54-final) | why this unit |
|---|---|---|
| U1, the probe's own cells | `css-gaps/flex/flex-gap-decorations-033 web P 1` · `ios P 0.9993` · `android P 1` | the GAP control keys move onto the wave-55 copy |
| U5, marker bake with root-owned runs | `css-counter-styles/counter-suffix web P 1` · `ios P 0.9873` · `android P 0.989` | the only bake root with marker runs (6: `. 1 . 2 א. ב.`) |
| U5, bake root list items, no runs | `css3-counter-styles-101 web P 0.9932 / ios P 0.9943 / android P 0.9736`; `-102 web P 0.9791`; `-103 web P 0.9986 / ios P 0.9978 / android P 0.9913` | their list items are measured by the bake |
| U5, the other 13 bidi-baked documents | 30 more scored cells (10 documents ×3: bidi-lines-001/-002, bidi-tab-001, boundary-shaping-009, hyphenate-character-005, bidi-plaintext-br-001, the four `selectors/dir-*`; anchor-center-safe-rtl and attachment-local-positioning-3/-4 are unscored) | `bidi-bake.mjs` imports `bidi-marker-paint.mjs` for every bake |
| U4, the console string | `css-text/hyphens/hyphens-out-of-flow-002 web P 1` (4 hosts warn) · `ios P 0.9971` · `android P 0.9943`; `hyphens-out-of-flow-001 web P 1` | `InlineRuns.ts:174` is a warn string on web only |

**What the pictures show.**
- **flex-gap-decorations-033** (ref plus the three `wave54-final` captures, crop 190×190):
  - The ref and all three captures show the same structure:
    - two blue row rules across x16-165 at y61-70 / y111-120;
    - red column-rule segments interrupted by the row rules: two on flex lines 1 and 3, one (the right gap) on line 2.
  - iOS paints the two colours saturated (255,0,0 / 0,0,255), which the probe's strict predicates still accept.
  - **No picture defect.**
- **The fakes** (the ref next to `blue-r1-short5` / `blue-r2-lshort4` / `blue-r1-lshort2` / `blue-r1-split1` / `blue-r1-moved5` /
  `blue-none`):
  - each is exactly what its name says: a row rule cut short at one end, cut at x100, or its end block moved 10 px down;
  - `blue-none` keeps the five red segments and has no blue.
  - The wave-54 probe prints OK on the first five of these six.
- **counter-suffix** (ref, web, iOS, Android):
  - web equals the ref;
  - iOS rows 3-4 read `.א foo` / `.ב bar`, where the ref reads `א. foo`;
  - both natives keep the CJK marker slightly left on rows 5-6.
  - These are 0(b)/(j) residues and belong to the lists lane (L5), not to L7. L7 only requires that U5 does not change these bytes.
- **hyphens-out-of-flow-002** (ref and the three captures): every orange box reads `high-` / `way` on all three platforms, as in the ref.

## 2. Mechanism (spec citations and arithmetic)

### 2.1 The flex-gap probe's blind axis (0(m)(1), R3V-S1)

**The test.** `tools/wpt/css/css-gaps/flex/flex-gap-decorations-033.html`, `rel=help` css-gaps-1 (Editor's Draft; its section
numbering is not pinned here). A gap decoration is painted even in a 0-px gap, centred on it.
- The container is 150 px wide (x16-165 in the frozen ref). The items are 50×50, except `#four`, which is 100 wide. `flex-wrap: wrap`.
- Lines 1 and 3 hold three items: column rules at x61-70 and x111-120. Line 2 holds 100 + 50: one rule, at x111-120.
- The row rules are 10 px tall, at y61-70 and y111-120, and span the full 150 px.
- Counts:
  - red: 2·45·10 + 40·10 + 2·45·10 = **2200 px**;
  - blue: 2·150·10 = **3000 px**.
  - Both equal the probe's ref read (`redPx 2200`, `bluePx 3000`).

**What the wave-54 probe reads.**
- Counts ±2 % (blue ±60 px), and both bboxes ±1 px.
- The red runs on rows y20 / y95 / y145.
- The blue runs on **columns** x40 / x90 / x140 (`:89`).
- The red runs on columns x65 / x115 (fix r3).

**Why a short row rule passes.**
- A rule cut 5 px short at the right end loses 50 px (1.67 % < 2 %).
- The union bbox is kept by the other rule, which still spans x16-165.
- None of x40 / x90 / x140 crosses x161-165 or x16-19.
- So `blue-r1-short5` (x16-160) and `blue-r2-lshort4` (x20-165) print OK ×3 at 0.999 / 0.9993.

**The fix: the transpose of fix r3.**
- Read the blue runs on a **row** through each row-rule gap: y65 (inside y61-70) and y115 (inside y111-120).
- Each must equal the ref's `[(16, 165)]` ±1 px. The ref self-check gains `blueRow {65: 1, 115: 1}`.
- The logic is byte-equal to the verify-r3 proposal (`wave54-plan/verify-r3/gap/proposal-bluerow.geometry.py`; the diff is comments only).
- A 2-px error is caught. A 1-px error is inside the tolerance, by design (fake `blue-r1-short1` prints OK on both probes).

**Why it matters more than R3V-S1 said.** On this test the score cannot see a row rule at all:
- `blue-none` scores **0.9644**, above the 0.95 pass threshold (`instruments-flex-gap-fakes/score-fakes.out.txt`);
- every other new fake scores 0.998–0.9999.

### 2.2 The wire differential that cannot fail (0(m)(2), wave-54 lesson h)

These defects were read in `wl3-wire-differential.sh`:
- **(a) No verdict.** The script ends with the node classifier, which only `console.log`s. Its exit is therefore 0 whatever it counts
  (`:46-64`), although its header calls any other changed document a LEAK (`:26`).
  - Replayed: the classifier, extracted by line range (`:47-63`), prints `identical 0 · content-changed 0 · renumbered 0 · one-sided 30`,
    `classifier exit=0`, on the mis-invocation shape.
  - It prints `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0` on the parked wave-54 trees
    (`instruments.wl3-classifier-replay.out.txt`).
- **(b) A relative out dir.** `W="$OUT/$tag/$sec"` is used inside `( cd "$TREE" … )` (`:37-42`).
  - With a relative OUT, every log and per-test-ir lands inside each tree, while the closing node reads `$OUT` from the caller's cwd.
  - Result: 30 ONE-SIDED, exit 0. This is the record's first [W-L3] attempt (`wave54-gate/_note.md:65-69`).
- **(c) A swallowed pipeline failure.**
  - The extract result is ignored (`; node build-combined…`, `:39-40`).
  - A failed combine / convert / split only prints `WARN` (`:43`).
  - A section with no `tests.list` is skipped silently (`:35`).
- **(d) Sections read from PRE only.** The classifier iterates `fs.readdirSync(path.join(OUT,'pre'))` (`:50`), so a section present
  only on POST is never seen.
- **(e) RENUMBERED is weaker than the rule it applies.**
  - It strips every `-<n>` suffix from `id` / `parent` and compares (`:48`).
  - The wave-53 rule (`wave54-plan/expectations.json` `wireRenumbering.rule`) also requires every id to shift by ONE amount, equal to
    the running component-count delta of the content-changed documents before it.
  - Measured on the parked trees in tests.list order (`instruments.renumber-rule.out.txt`): 1435 / 1435 documents visited, and
    **15 renumbered, all 15 uniform-shift = running delta (+6 after counter-suffix)**. The stricter rule accepts exactly the record.
- **(f) Symmetric extract FAILs exist on real data.**
  - `css/css-pseudo/first-letter-of-html-root-refcrash.html` FAILs on both sides
    (`Cannot read properties of null (reading 'appendChild')`).
  - It becomes the same `missing`-bucket placeholder document on both sides (the `wave54-final` manifest gives `bucket: "missing"`).
  - So "any FAIL → failure" would be wrong. The rule is: **the FAIL sets differ between sides → failure**.

### 2.3 The A/B arm runner (lesson "An A/B arm runner that switches branches must write its records after the tree is restored")

**What happened in wave 54.**
- `ab-arms54.sh:32` runs `installed-build-hash.sh` on the throwaway arm branch.
- That script `tee -a`s its block into `$HERE/build-hashes.txt` (`wave54-gate/installed-build-hash.sh:50`), a TRACKED file.
- `:35` `git checkout -q -f "$BRANCH"` then discards uncommitted changes (git-checkout(1) `-f`). The three blocks went, and
  `build-hashes.txt:45-56` is marked RECONSTRUCTED.
- The read-outs survived only because `ab-diff.mjs` ran after the restore (`:36`).

**Other defects in the same runner.**
- The shebang is on line 5 under a 4-line record header, so a direct `./ab-arms54.sh` runs under `/bin/sh`.
- The driver log goes to `/tmp/$run.driver.out`, which is wiped and not of record.
- The clean-tree check uses `git status --short`, which counts untracked files.
- A failing arm driver is printed (`driver rc=$?`) but never sets the exit code.
- The final check only prints `WARNING` (`:39`).

### 2.4 Spec 03 §4.1 renderer latitude and the citations (0(l))

**What W1 and Compose do.**
- **W1 (web, `InertOutOfFlowWordJoin.ts`)** rewrites `[P, M, N]` → `[P+N₁, M, N₂]`.
  - This applies only under the host's own `hyphens: auto`, and only for a paint-inert out-of-flow `span|time|data` member that splits
    a word.
  - So it paints a referenced child somewhere other than its `runs` position.
- **Compose `InlineRunFold`** goes further (`InlineRunFold.kt:460-467`).
  - It DROPS every member that `InertOutOfFlowMember.admits`, in a folded paragraph, whatever `hyphens` is, and **whether or not it
    splits a word**.
  - It counts them in `Folded.droppedOutOfFlowMembers`, printed in the fold breadcrumb at `ComponentRenderer.kt:3905-3906`.

**Which rule this departs from.** Both depart from **rule 2** ("A reader that honours `runs` paints the entries in order",
`03-children.md:191`), not from rule 1, which is about the wire ("Order is the payload"). The wire still carries the member at its
document position.

**Why the departure is faithful.**
- CSS 2.1 §9.3.1 takes an `absolute | fixed` box out of flow.
- css-text-3 §5.1 gives it no soft wrap opportunity, so the text on both sides of it is one word.
- css-text-3 §5.4 is where `auto` hyphenates.
- The cost is the static position (CSS 2.1 §10.3.7 / §10.6.4) and the accessible-text order.

**The proposed paragraph is too narrow.** Its "and it splits a word" (`wave54-web-tail/_note.md:321-327`) would make Compose's
any-position drop non-conforming. The corrected text is in §4 U4.

**Not a freeze event.**
- `schema/spec/05-versioning.md` "Change process" (`:85-97`) classifies changes by EMITTED BYTES:
  - a new omit-when-absent key → a minor revision (schema, spec and golden);
  - a change to an existing emitted byte shape → a new major version.
- A reader-side MAY with no byte change is neither. It is the same class as the v2 freeze's "reader-only fix (wire bytes unchanged)"
  (`:66-69`).
- No schema, no golden and no converter file changes. `node schema/conformance/run.mjs` must stay 39/39 valid, byte-identical.

**Numbering must not move.** The tree carries 12 other citations of spec 03 §4.1 rules 3-6, for example `NodeRenderer.ts:342` rule 4,
`IRDocumentDecoder.kt:528` rule 5 and `extract-fixture.mjs:3702` rule 6 (`instruments.census.out.txt` (4); `extract-fixture.mjs:3719` is CSS Text
§4.1, not spec 03). So the paragraph is
**unnumbered** and goes after rule 7 (`:210-214`), before `## 5.` (`:216`).

### 2.5 The marker paint decline (0(o), `bidi-marker-paint.mjs:32`)

**The rule.** A `::marker` belongs to its item (css-lists-3 §3.1), so a group effect on the item → root chain also applies to the
marker. A run that the bake owns at the ROOT escapes that effect. Two such effects are not read yet:
- `mask-image` (css-masking-1 #the-mask-image, which masks the element and its descendants as a group);
- `mix-blend-mode` (compositing-1 #mix-blend-mode, which blends the element's group).

**The census radius** (`instruments.census.out.txt` (2)):
- 17 bidi-baked documents, of which 4 have a list item: counter-suffix (6 root-owned runs) and css3-counter-styles-101/-102/-103
  (0 runs).
- **0** of the 17 sources mention `mask` or `mix-blend-mode`.
- 0 `marker-not-baked` stamps at `wave54-final`.

**The only risk is the spelling.**
- `markerPaintLoss` compares each key's computed value by exact string (`:150-152`).
- So if Chromium does not expose `maskImage` / `mixBlendMode` on `getComputedStyle(…, '::marker')`, or spells their initial values
  other than `'none'` / `'normal'`, every chain reads "lost".
- Then counter-suffix's markers DECLINE: 29 → 23 components, and web P 1 → the wave-53 0.9818 picture.
- That is exactly why the wave-54 TODO deferred it. The unit lands only after a CDP read plus a wire differential that comes back
  identical ([W-L7a], §4 U5).

## 3. Ownership (disjoint; by file)

**Lane-owned:**
- **U1:**
  - `tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py` (written)
  - `flex-zero-gap-rules.fakes-run.sh` and `flex-zero-gap-rules.geometry.out.txt`
  - `instruments-flex-gap-fakes/`: `make-fakes.py`, `score-fakes.mjs`, `score-fakes.out.txt`, 6 fake dirs × 3 PNG
- **U2:** NEW `tools/titan/wire-differential.sh`, NEW `tools/titan/wire-differential.mjs`, NEW `tools/titan/wire-differential.test.mjs`.
- **U3:** NEW `tools/titan/results/wave55-gate/ab-arms55.sh`, NEW `ab-arms55.selftest.sh`, and its `ab-arms55.selftest.out.txt`. These
  are the only L7 paths inside the orchestrator's `wave55-gate/`. `installed-build-hash.sh` there is the orchestrator's and stays
  untouched.
- **U4:**
  - `schema/spec/03-children.md` (one paragraph in §4.1)
  - `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts` (`:32`, a comment)
  - `runtimes/web/src/renderer/InlineRuns.ts` (`:52` / `:167` comments, `:174` warn string)
  - NEW `runtimes/web/tests/renderer/InlineRunsLatitudeCitation.test.ts`
  - Optional U4c: `apps/web-harness/src/ui/ComposedRootSeparator.ts` gets one TODO comment line for D5 near `:13` (0(l): the
    separator-font limit is not recorded in code). Taken only if the orchestrator agrees; it is a harness file.
- **U5:** `tools/titan/bidi-marker-paint.mjs`, and `tools/titan/bidi-bake.test.mjs` (S6-2 `effects` table +2 entries, +1–2 lines on
  1402, an already-disclosed 0(d) size exception).
- **U6 (tests only):**
  - U6-compose:
    - `runtimes/compose/src/test/java/com/styleconverter/runtime/typography/wrapping/HyphenateCharacterExtractorTest.kt` (101 lines, +1 test)
    - NEW `…/typography/wrapping/PreBreakPipelineClampHyphenTest.kt`
    - NEW `…/typography/wrapping/SoftHyphenCutsTest.kt`
    - NEW `…/layout/position/OutOfFlowContainingBlockBranchesTest.kt`
  - U6-swift: NEW `runtimes/swiftui/Tests/StyleConverterRuntimeTests/HyphenateCharacterCascadeTests.swift`, NEW
    `OutOfFlowContainingBlockBranchesTests.swift`.
  - U6-web: NEW `runtimes/web/tests/renderer/InertOutOfFlowWordJoin.branches.test.tsx`. The existing file is exactly 200 lines.
  - U6-tooling: NEW `tools/visual/label-chrome-check.test.mjs`.
- **Lane note:** `tools/titan/results/wave55-instruments/_note.md` (one section per revert unit, `STATUS:` and `TREES:` lines), plus the
  mutation logs as `.txt`, never `.log`.

**Seams: none.** No hunk in any of the three ComponentRenderers or in `tools/titan/extract-fixture.mjs`.
- U5 reaches extraction only through `extract-fixture.mjs` → (dynamic `import`) `bidi-bake.mjs` → `bidi-marker-paint.mjs`
  (`instruments.census.out.txt` (1)).
- `bidi-marker-paint.mjs` is not a seam file.

**Read-only.** These files are mutated only for the pin proofs, **in a throwaway worktree, never on the shared tree**, because other
lanes own or may edit some of them:
- `HyphenateCharacterExtractor.kt` / `.swift`
- `PreBreakPipeline.kt`, `SoftHyphenCuts.kt`
- `OutOfFlowContainingBlock.kt` / `.swift`
- `PerformanceExtractor.kt`
- `InertOutOfFlowWordJoin.ts`
- `tools/visual/label-chrome-check.mjs`

Every wave-54 record stays byte-identical:
- `wave54-plan/flex-zero-gap-rules.geometry.py` `f71565f1…`
- `wave54-hyphenate-character/wl3-wire-differential.sh` `54b71953…`
- `wave54-gate/ab-arms54.sh` `e8478700…`

**Overlap check against the six other lanes:**

| lane | overlap with L7 |
|---|---|
| L1 (Swift typography line boxes) | none: the Swift pins are in NEW files, not `TypographyTests.swift` / `GreedyLineBreakerTests.swift` |
| L2 (Compose ContainingBlockBands / DynamicValueResolver) | none. The `ContainingBlock[dark-stage-content-box-bands]` crumb pin is L2's (its source left the tree with revert 8deddc19) |
| L3 (Compose PositionedParentFlowSlot / CanvasRootHoist plus a ComponentRenderer.kt seam) | none: L3 may re-cut `OutOfFlowContainingBlockTest.kt` P3 or `census-*.base.txt` (0(o) note); L7's OOF pins are in NEW files |
| L4 (UA heading rules and spacing) | none |
| **L5 (lists marker, lists-bakes probe)** | **possible**: `tools/titan/bidi-bake.test.mjs` / `bidi-marker-bake.mjs` and the counter-suffix cells. If L5 claims either file, U5 lands AFTER L5 (rebased) or moves to L5. If L5 carries counter-suffix, U5's claim is wire-only (§6) |
| L6 (Compose borders paint order, effects drop-shadow / clip) | none |

**Docs and records (orchestrator, not L7):**
- Test counts move: tooling, web vitest, compose, swiftui. `CLAUDE.md` / `docs/STATUS.md` need a restamp, or `doc-staleness-check.sh`
  goes red.
- The wave-54 web-tail lane note `:77` gets an appended dated erratum ("rule 1 → rule 2, skeptic N5"); the record line itself is not
  rewritten (wave-54 lesson "A count restamp rewrote a dated historical sentence").
- The six gitignored L4 dry-run `out/` files (`wave54-S1/fix/ignored-evidence.out-dirs.txt`; all six exist, untracked) are force-added
  in the records commit.
- The BACKLOG 0(l) / 0(m) / 0(o) entries are rewritten at the ship.

## 4. Units (each one commit, separately revertible; landing order U1 → U2 → U3 → U4 → U6-* → U5)

**U1 — the flex-gap probe's wave-55 copy** (0(m)(1)).
- **Change.** Already written and executed at planning (§7). The lane re-executes `flex-zero-gap-rules.fakes-run.sh`, checks that the
  output equals `flex-zero-gap-rules.geometry.out.txt` (except the timestamp line), and lands the probe, fakes and outputs.
- **Gate wiring (orchestrator).** The wave-55 `expectations.json` names this copy, not the wave-54 file, for the
  `flex-gap-decorations-033` keys. Their class changes from GATING (wave 54: GAP-ios / GAP-android) to **CONTROL**.
- **Predicts.** No cell moves. On `wave55-open` the probe prints the same four OK rows as on `wave54-final`.
- **Revert.** Records only.

**U2 — `tools/titan/wire-differential.{sh,mjs}` + pins** (0(m)(2)). NEW and general (no per-wave copy). Each file is ≤ 200 lines and
every line is commented.

- **Usage.** `wire-differential.sh <pre-tree> <post-tree> <out-dir> --expect <file> [--run <id>=wave55-open] [--sections a,b] [--dry-run | --classify-only]`.
- **Paths and refusals.** Before any `cd`, PRE / POST / OUT / EXPECT resolve to absolute, physical paths (`cd -P … && pwd -P`; OUT is
  created first). It **refuses with exit 2** on:
  - an unresolvable path;
  - PRE == POST;
  - either tree being this script's own checkout (`git rev-parse --show-toplevel` equality). Extraction writes
    `fixtures/wpt/<sec>/` inside the tree, and the shared tree is where a gate runs. The trees under test are throwaway worktrees, as in
    the wave-54 [W-L3].
  - a missing `$RUN/sections`;
  - a requested section without `tests.list`. There is no silent `continue`.
- **Pipeline.** Per tree and section it runs the gate's four steps verbatim (`section-runner.sh:375`, `:416`, `:435`, `:620`).
  - It writes `status.tsv` rows: `<tag> <sec> rc_extract rc_combine rc_convert rc_split extracted=<n> fail=<m>`.
  - The extract `FAIL` lines go to `fail-<tag>-<sec>.txt`.
  - Bulky intermediates go under the gitignored `tools/titan/runs/<id>/` (lesson "A broad orchestrator commit captured a running
    instrument's partial output"). Logs are `.txt`.
- **Classifier.** `wire-differential.mjs classify --pre <dir> --post <dir> --expect <file> --tests-root <run>/sections [--status <tsv> | --no-status]`.
  - It walks the **union** of the PRE and POST section dirs, and the documents in `tests.list` order.
  - Classes:
    - IDENTICAL;
    - RENUMBERED: equal after the suffix strip AND a uniform shift equal to the running content-changed component delta (§2.2 e);
    - RENUMBERED-ANOMALOUS: strip-equal but not uniform, counted as a leak;
    - CONTENT-CHANGED;
    - ONE-SIDED (a document or a whole section).
  - `--no-status` prints `pipeline status NOT READ` (classify-only calibration). It is never silent.
- **Exit codes**, highest wins:

  | exit | meaning |
  |---|---|
  | 2 | usage or refusal |
  | 3 | pipeline: any combine / convert / split rc ≠ 0, or extract FAIL sets that differ between sides. A symmetric FAIL, as with the refcrash test, is printed as a NOTE and accepted |
  | 4 | ONE-SIDED |
  | 5 | changed set ≠ expected: an `UNEXPECTED CONTENT-CHANGED` / `RENUMBERED`, a `MISSING` expected one, or any RENUMBERED-ANOMALOUS |
  | 0 | none of the above |

- **Expect file.** Lines `CONTENT-CHANGED <sec>/<file>` / `RENUMBERED <sec>/<file>`, plus `#` comments. An empty file means "no change".
- **Pins** (`wire-differential.test.mjs`; synthetic per-test-ir trees under `os.tmpdir()`; no Chromium, no gradle):

  | pin | case | expected |
  |---|---|---|
  | P1 | identical trees, empty expect | 0 |
  | P2 | one change, expected | 0 |
  | P2 | one change, unexpected | 5 `UNEXPECTED` |
  | P2 | one change expected but absent | 5 `MISSING` |
  | P3 | uniform +2 shift after a +2 content change | RENUMBERED, 0 |
  | P3 | non-uniform shift | RENUMBERED-ANOMALOUS, 5 |
  | P4 | a one-sided document | 4 |
  | P4 | a section present only in POST | 4 |
  | P5 | `rc_convert=1` | 3 |
  | P5 | asymmetric FAIL | 3 |
  | P5 | symmetric FAIL | 0 + NOTE |
  | P6 | `wire-differential.sh --dry-run` run from another cwd with a relative OUT | prints an absolute OUT equal to its realpath |
  | P6 | PRE == POST | 2 |
  | P6 | PRE = own checkout | 2 |

- **Mutations** (each executed red → restore → green, sha256 recorded):

  | mutation | turns red |
  |---|---|
  | M1: the exit code is always 0 | P2 / P4 / P5 |
  | M2: sections read from PRE only | P4, the post-only section |
  | M3: the uniform-shift check dropped | P3 anomalous |
  | M4: absolutization dropped | P6 |
  | M5: own-checkout refusal dropped | P6 |

- **Calibration on real data** (lane; read-only; Chromium-free). Run `wire-differential.mjs classify --pre tools/titan/runs/wave54-wl3-diff/pre
  --post …/post --tests-root tools/titan/runs/wave54-open/sections --expect tools/titan/results/wave55-plan/instruments.wl3-expect.wave54.txt --no-status`:
  - it must print `identical 1397 · content-changed 23 · renumbered 15 · one-sided 0` and exit 0;
  - with the counter-suffix line deleted from a copy of the expect file it must exit 5, naming counter-suffix UNEXPECTED;
  - on the §2.2 mis-invocation shape it must exit 4.
- **The first live run is [W-L7a]** (U5 below).
- **Predicts.** No cell. Tooling suite +N (the lane reports N).

**U3 — `ab-arms55.sh` + self-test** (lesson "An A/B arm runner that switches branches…").

**The runner** is derived from `ab-arms54.sh`:
- **Header.** The shebang is on line 1.
- **Inputs and mode.** `ab-arms55.sh <arms-file> --dry-run|--run`. An explicit mode flag is required; the default is the dry run. The
  arms file has one arm per line: `<arm> <sections,> <platforms,> <sha…>`, child first, as `ab-arms54.sh:16-21`.
- **Environment.** `AB_ROOT` (default: the checkout holding the script) and `TITAN_DEVICE_POOL_DIR` (default `/tmp/titan-device-pool`).
- **Refusals (exit 2):**
  - `git status --porcelain --untracked-files=no` is not empty: tracked changes only, so the untracked `wave55-gate/` does not block it;
  - a detached HEAD;
  - an arm sha that is not an ancestor of HEAD;
  - a stage dir (`mktemp -d`, OUTSIDE the tree) that cannot be created.
- **Per arm (`--run`):**
  1. Record the sha256 and size of `build-hashes.txt`.
  2. Run `checkout -B` and `revert`.
  3. Start the driver. Its output goes to `$STAGE/<arm>.driver.txt`, not `/tmp`, not `.log`.
  4. After `attempt 1 starting`, run `installed-build-hash.sh` with its stdout to `$STAGE/<arm>.hash.txt`. Its own in-tree `tee -a` is
     allowed and discarded.
  5. Wait for the driver and record its rc.
  6. **Restore:** `git checkout -q -f "$BRANCH"`, then `git branch -D`.
  7. **Verify the restore (exit 6 otherwise, records left in `$STAGE`, path printed):**
     - HEAD == BASE;
     - tracked-clean;
     - `build-hashes.txt` sha256 == the pre-arm sha. An untracked file that kept the in-arm append is first truncated to the pre-arm size.
  8. **Only then write the records:**
     - append the staged block to `build-hashes.txt`;
     - copy the driver output to `final/ab-<arm>.driver.txt`;
     - run `ab-diff.mjs wave55-ab-<arm> wave55-final --threshold 0.002 --platforms <p>` → `final/ab-<arm>.txt`.
  9. Verify (exit 7 otherwise) that the block's `== <label>` line appears exactly once and the read-out is non-empty.
- **Exit status.** A driver rc ≠ 0 sets the final exit status; the runner still runs the remaining arms. The last line asserts the tree
  is at BASE on BRANCH.

**The self-test** (`ab-arms55.selftest.sh`): device-free, gradle-free, Chromium-free.
- **Setup:**
  - a fresh `git init` repository under `mktemp -d`. It is NOT a worktree of this repo, so no branch or stash is shared.
  - stubs for `tools/titan/gate-driver.sh` (prints `attempt 1 starting` … `OK`), `own-processes.sh` (`kill_own_gradle_daemons(){ :; }`),
    `results/wave55-gate/installed-build-hash.sh` (appends and tees a `== <label>` block beside itself, like the real one) and
    `results/wave52-gate/ab-diff.mjs`;
  - a tracked `build-hashes.txt`;
  - commits base → U → head, and one arm "drop-U".
- **It asserts:**
  - exit 0;
  - HEAD restored;
  - the block present exactly once;
  - `final/ab-drop-U.txt` and `.driver.txt` written;
  - the stub pool dir used, and the real `/tmp/titan-device-pool` untouched;
  - this repo's `git for-each-ref | shasum` unchanged before and after.
- **Mutations:**
  - **M-L18:** move step 8 before step 6, the wave-54 order → the block is missing → RED.
  - **M-dry:** `--dry-run` must leave the throwaway repo's refs and files byte-identical.
- **The lane runs it.** Only the arms themselves are an orchestrator window, after the closing gate.
- **Predicts.** No cell. The wave-55 A/B arms keep their installed-build blocks.

**U4 — spec 03 §4.1 latitude paragraph + the four citations** (0(l)).

The paragraph, inserted unnumbered after rule 7:

> **Renderer latitude — a reader-side exception to rule 2 (no wire change).** A reader MAY paint a `{child}` member away from
> its position in `runs` — or, in a paragraph it folds into one text layout, not mount it at all — only when the member is out of
> flow (`position: absolute | fixed`) AND paints nothing: an alpha-0 `color`, no declaration besides `position`, `color` and
> `hyphens`, no `children`, `runs` or `decorations` of its own, and a text tag with no UA ink (`span`, `time`, `data`). CSS 2.1
> §9.3.1 takes such a box out of the flow and css-text-3 §5.1 gives it no soft wrap opportunity, so the text on both sides of it is
> one word, which a reader that breaks the paragraph at the member cannot lay out faithfully. The stated cost is the member's static
> position (CSS 2.1 §10.3.7 / §10.6.4) and its place in the accessible-text order. Every use is counted where it happens: web
> `RunsPlacement.joinedOutOfFlowMembers` (the member moved to the end of the word it splits, only under the host's own computed
> `hyphens: auto`, warned on the `[InlineRuns]` console channel); Compose `InlineRunFold.Folded.droppedOutOfFlowMembers` (the member
> dropped from a folded paragraph, whatever `hyphens` is, printed in the fold breadcrumb). The wire is unchanged: `runs` keeps the
> member at its document position (rule 1), and a reader that does not use this latitude paints it there (rule 2).

**Every clause was checked against code:**
- the web predicate: `InertOutOfFlowWordJoin.ts:98-119`;
- the Compose predicate: `InertOutOfFlowMember.kt:89-114`;
- the member tags: `TEXT_MEMBER_TAGS` at `InlineRunFold.kt:177`;
- the Compose drop: `:460-467`;
- the Compose breadcrumb: `ComponentRenderer.kt:3905-3906`;
- the web warn: `InlineRuns.ts:172-175`.

SwiftUI has no twin (grep: no `InertOutOfFlow*` under `runtimes/swiftui`).

**The citations:**
- `InertOutOfFlowWordJoin.ts:32`, `InlineRuns.ts:52` and `:167`: "rule 1" → "rule 2, under its renderer-latitude paragraph".
- `InlineRuns.ts:174` (the warn STRING): "… reader deviation from spec 03 §4.1 rule 1" → "… rule 2 (renderer latitude)".
  - Existing test (i) (`InertOutOfFlowWordJoin.test.tsx:167-177`) matches a prefix with `toContain`, so it stays green.

**Pin** (NEW `InlineRunsLatitudeCitation.test.ts`):
- (a) The breadcrumb for an inert member that splits a word ends with `spec 03 §4.1 rule 2 (renderer latitude)`. Mutation: restore
  "rule 1" → red.
- (b) It reads `schema/spec/03-children.md` and asserts:
  - §4.1 has exactly seven numbered rules;
  - the latitude paragraph names both `joinedOutOfFlowMembers` and `droppedOutOfFlowMembers`.
  - Mutations: insert the paragraph as a numbered rule between 2 and 3 → red; rename a counter in the spec → red.

**Other runs and predictions:**
- `node schema/conformance/run.mjs` gives 39/39 valid, unchanged. No converter, schema or golden file is touched.
- Predicts no capture change: comments plus a console string. Web capture is identical by construction, because the manifest records
  no console output.

**U5 — `mask-image` / `mix-blend-mode` in the marker paint decline** (0(o)).
- **Change.**
  - Add `maskImage: 'none'` and `mixBlendMode: 'normal'` to `MARKER_PAINT_INITIAL`, each with its spec comment (css-masking-1
    #the-mask-image; compositing-1 #mix-blend-mode).
  - Replace the `TODO(S6 residue)` paragraph (`:32-36`) with the census sentence: 0 of 17 bidi-baked sources carry either.
  - Extend the S6-2 `effects` table in `bidi-bake.test.mjs` (`:1307-1308`) with `maskImage: 'linear-gradient(rgb(0, 0, 0), rgba(0, 0, 0, 0))'`
    and `mixBlendMode: 'multiply'`. The existing key-equality line (`:1310`) then forces both into every decline row (`::marker` and
    `li`).
- **Mutation:** `markerPaintLoss` skips the two keys → the S6-2 rows for both go red.
- **[W-L7a], an orchestrator window (Chromium + converter; no device):**
  1. A CDP read on counter-suffix, using the S1 `marker-probe` copy or a one-off `page.evaluate`, of
     `getComputedStyle(li, '::marker')` and each chain entry's `maskImage` / `mixBlendMode`.
     **Expect:** `'none'` / `'normal'` on every entry of the four RTL chains.
  2. `wire-differential.sh <tree@U4> <tree@U5> tools/titan/runs/wave55-wl7a --expect <empty> --sections css-anchor-position,css-backgrounds,css-counter-styles,css-text,css-writing-modes,selectors`
     (the six sections that hold the 17 bidi-baked documents).
     **Expect:** exit 0 and `content-changed 0 · renumbered 0 · one-sided 0`.
  3. `--classify-only` on the same out dir with counter-suffix pre-registered as changed → exit 5. This proves on a real run that the
     instrument can fail.
  - Any other result on (1) or (2) means **U5 does not land**.
- **Predicts.** Wire byte-identical; no cell moves.

**U6 — the queued pins** (0(o)). Every mutation is executed red → restore → green, sha256-verified, in a throwaway worktree.

| pin | test file (owner L7) | input → expected | mutation that turns it red | wave-54 item |
|---|---|---|---|---|
| C1 | `HyphenateCharacterExtractorTest.kt` | `[HyphenateCharacter {string "/-/"}, HyphenateCharacter {string "•"}]` → `"•"` (css-cascade-4 §6.1 cascade sorting order: order of appearance, the last declaration wins) | `lastOrNull` → `firstOrNull` (`HyphenateCharacterExtractor.kt:36`) | L3 SK-X1 |
| C2 | NEW `PreBreakPipelineClampHyphenTest.kt` | a fired run under `clampLines = 1` with `hyphenChar = "•"`, cut at a U+00AD → the clamp head ends `•` + marker; the lane derives the exact string by running it | drop `hyphenChar = hyphenChar` from the `clampLines` call (`PreBreakPipeline.kt:222`) | L3 SK-X2 |
| C3 | NEW `SoftHyphenCutsTest.kt` | `took("im­ple", ["im‐ple","x"], "‐") == true` AND `walkAligns(…) == false`: the fallback, not the walk, answers. Traced: the walk returns null at `:77`, and `legacyCount` 1 > 0 | `?: legacyCount(…)` → `?: false` (`SoftHyphenCuts.kt:38`) | L3 SK-X3 |
| S1 | NEW `HyphenateCharacterCascadeTests.swift` | two declarations → the second's value | first-wins (`if cfg == nil`) in `HyphenateCharacterExtractor.swift` | L3 SK-U2I-g |
| C4 | NEW `OutOfFlowContainingBlockBranchesTest.kt` | `Filter` object leaf `{"url":"#f"}` → `establishes == true` (filter-effects-1 §5) | `is JsonObject -> false` (`filterListIsUsed`) | L4 K-X4 |
| C5 | same | `Contain` primitive `"paint"` → true (css-contain-2 §3.4; the Compose twin of S-X7) | the string branch of `PerformanceExtractor.extractContainConfig` returns the empty set | L4 S-X7 twin |
| C6 | same | `Contain [{"bad":1}]` → false AND `PropertyTracker.isUnhandled(CONTAIN_DECODE_BREADCRUMB)` | delete the `markUnhandled` at the catch | L4 crumb |
| C7 | same | `[ContentVisibility …]` → `isUnhandled(IMPLIED_CONTAINMENT_BREADCRUMB)` | delete that `markUnhandled` | L4 crumb |
| S2 | NEW `OutOfFlowContainingBlockBranchesTests.swift` | `Contain` string `"paint"` → true | `case .string: return false` | L4 S-X7 |
| S3 | same | `Filter` object leaf → true | the Swift object branch → false | K-X4 twin |
| S4–S6 | same | after `PropertyTracker._resetForTests()`: the decode-failed, implied-containment and positioned-establisher crumbs → `XCTAssertFalse(PropertyTracker.logOnce(key: <crumb>, message: ""))` (already seen) | delete each `logOnce` call | L4 crumbs (Swift's positioned one was never asserted) |
| W1 | NEW `InertOutOfFlowWordJoin.branches.test.tsx` | a member with a child / with `meta.runs` / with `meta.decorations` → `joined 0` | delete the structural refusal (`InertOutOfFlowWordJoin.ts:102`) | L6 D3 SK6-w1 |
| W2 | same | a `FIXED` inert member joins (`joined 1`) | drop `FIXED` from `OUT_OF_FLOW_POSITIONS` (`:45`) | SK6-w2 |
| W3 | same | `[text, M, text, text]` → the member lands at the word's end | disable the `pushText` continuation (`:154`) | SK6-w5 |
| W4 | same | alpha 0.3 → `joined 0` | the `a <= 0` test (`:89`) loosened to `a < 0.5` | alpha threshold |
| W5 | same | `srgb.a: "0"` (decimal string) → joins | drop the string branch of `alphaIsZero` | SK6-w9 |
| W6 | same | `Position {"keyword":"ABSOLUTE"}` → joins | `keywordOf` ignores objects | SK6-w10 |
| T1 | NEW `tools/visual/label-chrome-check.test.mjs` | the fixture `{P1:{children:{Box:{children:{Kid:{}}}}}, P2:{children:{Box:{}}}}` with `verify('001_Box','container',…)` → red, `/is a leaf, entry says container/` | X1 `hits.slice(0, 1)` (`label-chrome-check.mjs:163`) | wave-54 L7 S4 / X1 |
| T2 | same | `checkExempt` with the component's paint on SOME (not all) would-be glyph pixels of one platform → no `(iv)` problem | X8 `lit === wouldBe.length` → `lit > 0` (`:110`) | wave-54 L7 X8 / N2 |

**Totals:** 21 pins (C1–C7, S1–S6, W1–W6, T1–T2) and 21 mutations.

**Commits:** U6-compose (C1–C7), U6-swift (S1–S6), U6-web (W1–W6) and U6-tooling (T1–T2) land as four commits, each revertible alone.

**Not pins.** These touch other source and are DEFERRED, or belong to another lane:
- **L3 N1** (`hyphenate-character` not inherited on either native; css-text-4 §6.3 says it inherits).
  - The fix is one entry in each inherited list: Compose `ComponentRenderer.kt:214`, which is a SEAM, and Swift `InheritedText.swift:77`.
  - It is a behaviour change on twin files. Defer both halves together; corpus reach is 0 (all 14 declarations sit on their text host).
- **The SK-X3 residue** (an unalignable run under `""` declines with no breadcrumb). This is Compose source (`SoftHyphenCuts.kt`), owned
  by no wave-55 lane. Defer.
- **L4 `ContainingBlock[dark-stage-content-box-bands]`**: its source (`ContainingBlockBands.kt`) is not on HEAD. The pin rides with L2's
  CBB unit.
- **L6 note-D3** (`ComposedRootSeparator.ts:60` "TODO(wave-55+): measure, then drop"). It needs a CDP measure and is behaviour, not a
  test gap. Defer.
- **Wave-54 L7 S3** (red when the band is in a MIXED state on an exempt stem). This is a `tools/visual/label-chrome-check.mjs` behaviour
  change. The skeptic could not rule out false reds. No lane owns it. Defer.
- **Wave-54 L7 X4 / N3** (the stem-loop HINT call). Pinning it means refactoring the tripwire loop into the checker, which is source.
  It is message-only and "can never turn red into green". Defer.
- **Wave-54 L7 S2.** It is a census under-report, not a test gap: the executed 40 / 40 green with and without the seed
  (`wave54-label-chrome/skeptic/seed-readers.out.txt`) closes it in the records.

## 5. Carrier census (`instruments.census.mjs` → `instruments.census.out.txt`)

**Gate reach** (closure of `gate-driver.sh`, `section-runner.sh` and `test-all.sh` over static and dynamic imports: 43 scripts):
- Executed by the gate:
  - `bidi-marker-paint.mjs`, by the chain `extract-fixture.mjs → bidi-bake.mjs → bidi-marker-paint.mjs` (U5);
  - the two web renderer files, as runtime source (U4).
- Not executed by the gate:
  - the probe copy, which reads PNGs only;
  - `wire-differential.*` and `ab-arms55.*`: no gate script names them;
  - the spec prose. No tool parses `03-children.md`: every reference is a comment.
  - every test file.

**Captures: web {} · ios {} · android {}.**
- U4 changes comments plus one console string. The warn fires on 4 hosts of 1 document (`hyphens-out-of-flow-002__3-287 / __4-289 /
  __5-291 / __6-293`), the **same host set** as the recorded `wave54-web-tail/w1-joins.wave53-final.out.txt`.
- U5's capture radius is empty when its wire is identical.

**Wire: {}.**
- U5 is the only owned path on the extract route. Its radius is the 17 bidi-baked documents:
  - 4 with a list item: counter-suffix, with 6 root-owned runs `. 1 . 2 א. ב.`, and css3-counter-styles-101/-102/-103, with 0 runs;
  - 0 with `mask` or `mix-blend-mode` in their source;
  - 0 `marker-not-baked` stamps today.
- Predicted diff: none. It is proven before landing by [W-L7a]. U2 is the instrument that would show a leak.

**No stem is a carrier of L7 on any platform. Every cell below is a must-not-move of L7.**

## 6. Predictions (`wave54-final` → the wave-55 closing gate, read against `wave55-open`, which must equal `wave54-final`)

Every row is a must-not-move with direction **none**. "Identical" means the capture and per-test IR are byte-identical to `wave55-open`
(|Δ| = 0; the rule-2 −0.002 band is the alarm, not the expectation). Geometry-controlled rows carry the floor 0.95.

| cell (wave54-final) | → predicted | tier | floor · geometry | unit |
|---|---|---|---|---|
| `css-gaps/flex/flex-gap-decorations-033 web P 1` | identical | HIGH | 0.95 · `flex-zero-gap-rules` (wave-55 copy) control | U1 |
| `… ios P 0.9993` | identical | HIGH | 0.95 · control | U1 |
| `… android P 1` | identical | HIGH | 0.95 · control | U1 |
| `css-counter-styles/counter-suffix web P 1` / `ios P 0.9873` / `android P 0.989` | identical under U5 alone | HIGH (conditional on [W-L7a]) | 0.95 · `[M]` control, **only if no other lane carries counter-suffix**; otherwise the rows are L5's and U5's claim is wire-only | U5 |
| css3-counter-styles-101 `web P 0.9932` / `ios P 0.9943` / `android P 0.9736`; -102 `web P 0.9791`; -103 `web P 0.9986` / `ios P 0.9978` / `android P 0.9913` | identical | HIGH | — | U5 |
| the other 30 scored cells of the 13 remaining bidi-baked documents (`instruments.cells.wave54-final.txt`) | identical | HIGH | — | U5 |
| `css-text/hyphens/hyphens-out-of-flow-002 web P 1` / `ios P 0.9971` / `android P 0.9943`; `-001 web P 1` | identical | HIGH | 0.95 · `web-out-of-flow-hyphen-box` -002 web control | U4 |
| every other scored cell | identical (no L7 path reaches it) | HIGH | — | all |

**Totals.** Flips: 0. Lost: 0. Newly measured / unmeasured-now: 0. L7 adds nothing to the pre-registered lost list.

## 7. Geometry probes (classes and exact expect strings)

**Probe 1: `python3 tools/titan/results/wave55-plan/flex-zero-gap-rules.geometry.py <run>`**
- **Reads:** red / blue counts ±2 % and bboxes ±1 px; ±1 px against the ref on:
  - the red runs on rows y20 / y95 / y145;
  - the blue runs on columns x40 / x90 / x140;
  - the red runs on columns x65 / x115;
  - **the blue runs on rows y65 / y115** (new).
- **Self-check:** the ref row must print OK (exit 1 otherwise).

| key | class | expect (line ends) |
|---|---|---|
| `flex/flex-gap-decorations-033 web` | control | `→ GEOMETRY OK` |
| `flex/flex-gap-decorations-033 ios` | control | `→ GEOMETRY OK` |
| `flex/flex-gap-decorations-033 android` | control | `→ GEOMETRY OK` |

- Gating: none. L7 has no picture unit.
- Report: none.
- Executed values:
  - `wave54-final`: ref / web / ios / android OK ×4;
  - `wave53-final` and `wave54-open`: ref / web OK, ios / android `GEOMETRY WRONG (redPx 0 vs ref 2200)`, unchanged from the wave-54 probe.

**The fakes it must reject.** All were executed (`flex-zero-gap-rules.geometry.out.txt`). Each line is printed ×3 (web / ios / android),
with the ref OK, and SSIM is the gate metric (`diffWebVsRef`).

| fake | wave-54 probe | wave-55 copy | SSIM |
|---|---|---|---|
| `wave54-plan/verify-r3/gap/fake-blue-r1-short5` | OK | `GEOMETRY WRONG (blue runs row 65 [(16, 160)] vs ref [(16, 165)]: row-rule horizontal extent)` | 0.999 |
| `…/fake-blue-r2-lshort4` | OK | `GEOMETRY WRONG (blue runs row 115 [(20, 165)] vs ref [(16, 165)]: row-rule horizontal extent)` | 0.9993 |
| `wave55-plan/instruments-flex-gap-fakes/fake-blue-r1-lshort2` | OK | `GEOMETRY WRONG (blue runs row 65 [(18, 165)] vs ref [(16, 165)]: row-rule horizontal extent)` | 0.9997 |
| `…/fake-blue-r1-split1` | OK | `GEOMETRY WRONG (blue runs row 65 [(16, 99), (101, 165)] vs ref [(16, 165)]: row-rule horizontal extent)` | 0.9999 |
| `…/fake-blue-r1-moved5` (count-preserving) | OK | `GEOMETRY WRONG (blue runs row 65 [(16, 160)] vs ref [(16, 165)]: row-rule horizontal extent)` | 0.998 |
| `…/fake-blue-r2-hshift2` (control) | WRONG | `GEOMETRY WRONG (blue (16, 61, 167, 120) vs ref (16, 61, 165, 120))` | 0.9993 |
| `…/fake-blue-none` (target ink removed) | WRONG | `GEOMETRY WRONG (bluePx 0 vs ref 3000)` | **0.9644** |
| `…/fake-blue-r1-short1` (tolerance control, 1 px) | OK | `GEOMETRY OK`, by design | 0.9998 |
| the 11 other recorded fakes (skeptic-r2, fix-r2 ×2 (one is the blue VERTICAL-position fake `fake-gap-blue`), skeptic-r3 ×4, fix-r3 ×2, verify-r3 red ×2), plus `fake-ref-as-run` OK ×3 | as recorded | the same verdict and reason, byte for byte | as recorded |

The rule set covers the lesson's fake classes:
- target ink removed;
- shifted ±2 (`hshift2`, and `red-l1r-down2` / `red-l3r-up2`);
- shortened at each end (`short5`, `lshort4`, `lshort2`);
- split (`split1`);
- count-preserving rearrangement (`moved5`).

**Probe 2 (U5 control, conditional): `python3 tools/titan/results/wave54-plan/rtl-marker-bake.geometry.py <run> wave55-open`**
- The keys `[M] counter-suffix web`, `[M] counter-suffix ios` and `[M] counter-suffix android`:
  - class: control;
  - expect: `→ GEOMETRY OK`;
  - contains: `rows 0-207 identical to wave55-open`.
- The executed `wave54-final` lines (base `wave54-open`) print `… | rows 0-207 identical to wave54-open → GEOMETRY OK` ×3.
- **Registered only if L5 does not carry counter-suffix.** Otherwise U5 is held to the wire differential alone.

**Probe 3 (U4 control): `python3 tools/titan/results/wave54-plan/web-out-of-flow-hyphen-box.geometry.py <run>`**
- The key `hyphens/hyphens-out-of-flow-002 web`:
  - class: control;
  - expect: `→ GEOMETRY OK`. At `wave54-final` it reads `heights [46, 46, 46, 46, 46, 46, 46] … → GEOMETRY OK`.

**Self-test rule.** `geometry-gate.py <run> --lanes L7 --self-test` must HOLD:
- the lane has no GATING or REPORT keys;
- its CONTROL keys PASS on `wave54-final`.
- The ability to fail is proven by the fake table above, not by today's picture, because every target is already right.

## 8. Device probe section

**None required.** No L7 unit positions runs or boxes on a native:
- U1, U2 and U3 are instruments outside the gate path;
- U4 is prose, comments and one web console string;
- U5 changes extraction. Its pre-gate proof is the Chromium-plus-converter wire differential [W-L7a] over css-anchor-position,
  css-backgrounds, css-counter-styles, css-text, css-writing-modes and selectors. An identical wire makes every capture identical by
  the pipeline's determinism (wave-54 lesson "On an unchanged host the whole pipeline is byte-deterministic on all three platforms").
- U6 is tests only.

If the wave's union probe includes css-counter-styles and css-text, read the §6 rows there with the union carrier set. L7 requests no
device window of its own.

## 9. Risks, and what could not be determined

1. **U5's spelling (MED impact, LOW-MED likelihood).** The `::marker` and chain spellings of `maskImage` / `mixBlendMode` were not
   measured; Chromium cannot run under the live gate.
   - A non-initial or absent value declines every marker. counter-suffix would go 29 → 23 components and its pictures back to wave 53.
   - [W-L7a] steps (1) and (2) gate the landing. The existing eight keys already read initial spellings on `::marker`, since
     counter-suffix bakes at `wave54-final`, so the same is expected here.
2. **L5 collision (MED).** `tools/titan/bidi-bake.test.mjs`, `bidi-marker-bake.mjs` and the counter-suffix rows may be L5's. The
   orchestrator resolves this before landing (§3). It is unknowable from this brief.
3. **U2's stricter RENUMBERED rule** could reject a legitimate shift pattern not seen in wave 54. It is calibrated on 15 / 15 real
   documents. An anomaly prints the per-id shifts and the running delta, so a false alarm can be read off the output.
4. **U3 has been run only against stubs.** The real arms run once, after the closing gate. Every destructive step (`checkout -B`,
   `revert`, `checkout -f`, `branch -D`, `rm` of pool markers) sits behind `--run`. The dry run is exercised in the self-test's
   throwaway repo, and the runner refuses on tracked changes (wave-54 lesson "A `--check` mode…").
5. **Contract perception (LOW).** A MAY added to a frozen, normative section may be read as a contract change.
   - Mitigation: it is reader-side, unnumbered and counted.
   - `05-versioning.md:85-97` is cited.
   - Goldens and schema are byte-identical. The U4 pin (b) guards the rule numbering.
6. **Doc drift (certain, LOW).** Test counts move on four suites, so `doc-staleness-check.sh` fails until the orchestrator's restamp.
7. **Pin brittleness.** C4–C7 and S2–S6 pin today's spec-correct OOF behaviour in NEW files. If L3 changes `OutOfFlowContainingBlock.*`
   on purpose, these pins go red as designed and must be re-cut in the same unit.
8. **Not determined:**
   - css-gaps-1 section numbering: the Editor's Draft, offline.
   - The exact C2 clamp-head string: the lane derives it by running, then mutates.
   - Whether the wave-55 plan will register any A/B arms: U3 reads an arms file and hard-codes none.
   - Whether `getComputedStyle(…,'::marker')` exposes `maskImage` at all on the CfT build of record.

STATUS: COMPLETE

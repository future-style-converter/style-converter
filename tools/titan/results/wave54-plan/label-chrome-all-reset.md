# Wave 54 brief — family `label-chrome-all-reset`

Read-only brief, written 2026-10-08 while `wave54-open` ran on the devices. Nothing was built, captured, run on a
device or edited outside this directory; the tree at 7cce3b22 is byte-identical outside `tools/titan/results/wave54-plan/`.
BACKLOG item covered: "Next-wave obligations (wave 54 opens with these)" **0(e)** ("all-then-color's baselines are NOT
seeded — the label-chrome tripwire refused them"), plus one stale clause in obligation 0's preamble (§9 R6).

Evidence, every item executed or opened for this brief:
- the 18 PNGs the wave-53 seeding commit **77fe41e8** (branch `campaign/wave53`) committed and the ship reverted, read with
  `git show 77fe41e8:tools/visual/baseline/<file>` into `label-chrome-all-reset.seeded-77fe41e8/` (sha256 prefixes in §2).
  Opened as images: Android 000–005, iOS 001/002/003/005, web 000/001/003/004/005;
- `label-chrome-all-reset.replay.mjs` → `.replay.out.txt`: the tripwire's OWN exported `checkTriplet` / `glyphPixels`
  (`tools/visual/label-chrome-tripwire.test.mjs`) replayed on those PNGs and on the 130 committed stems;
- `label-chrome-all-reset.geometry.py` → `.geometry.out.txt` (both modes);
- `label-chrome-all-reset.census.mjs` → `.census.json` / `.census.out.txt` (fixtures, baselines, tracked IR, corpus);
- `label-chrome-all-reset.cells.txt`: `node tools/titan/results/wave52-gate/cells.mjs '<11 tests>' wave53-open wave53-final`;
- the fixture nets `tools/titan/runs/wave53-open/gate-driver/fixture-net.log:1223` and
  `tools/titan/runs/wave53-final/gate-driver/fixture-net.log:1155-1246`;
- the code on all three platforms (§4), `docs/DYNAMIC_CAPTURE.md` §5 "Harness label chrome",
  `tools/titan/results/wave51-A/design-record.md` §1–§2 and `docs/STATUS.md:2425-2445` (the wave-51 PR-3 record).

## 0. Verdict first — the BACKLOG premise is falsified

0(e) says "the fixture's `all` reset reaches the capture-frame label chrome". **It does not, on any platform.** The three
canvases without a label are exactly the canvases the chrome contract says get none: `docs/DYNAMIC_CAPTURE.md` §5
"When": *"exactly one label per capture, iff the COMPOSED root of that capture … has zero composed children AND no
non-empty `text` … Containers and text-bearing roots get none"*. `001_ATC_PropsThenAll_InGreenParent` and
`003_ATC_InitialUnderRedParent` are containers (children `reset`, `span`); `005_ATC_DirectionSurvives` has `_text` "123".
The roots of 001 and 003 do not even declare `all` — the `all` is on their CHILD, and the child's own standalone
capture (002 `reset`, 004 `span`) DOES carry its label (§2). Label drawn ⇔ label-due by fixture structure: **18/18**;
label drawn ⇔ root declares no `all` (the premise): **3/18** (`geometry.out.txt`, last two lines).

The defect is in the **oracle**: the tripwire derives the expected glyph set P from the file name for EVERY committed
stem and has no clause for the contract's "iff". It never fired before because every committed stem was childless
and textless (`wave51-A/design-record.md` §1: "all childless and textless"; census (B): 130 stems, all owned by flat
fixtures). all-then-color is the first fixture with a container or a text root ever offered to `tools/visual/baseline/`.
So the fix is a tooling change plus the seeding, no runtime and no harness edit. **Timing:** the tripwire is not
executed or built by the gate (CI `test-tooling` / the tooling suite only), so U1 can be written and landed while a gate
runs; U2 (seeding) needs the devices → after the opening gate frees the host, or as part of the closing fixture net.

## 1. Targets

**No corpus cell.** WPT / TITAN-inbox / composed captures are label-free on all three platforms (the WPT term of each
predicate, §4), so no WPT cell can carry, lose or move chrome. The targets are tripwire stems and one fixture-net line:

| # | target | today (executed) | wanted |
|---|---|---|---|
| T1 | `001_ATC_PropsThenAll_InGreenParent` tripwire, on the 77fe41e8 bytes | RED `(i) Android: 437/437 glyph px are ground; (i) iOS: 437/437 …; (i) web: 437/437 …` (replay R0) | green: exempt (container), band rows 0..15 byte-identical ×3 |
| T2 | `003_ATC_InitialUnderRedParent` | RED (i) **369/369** ×3 (R0) | green: exempt (container) |
| T3 | `005_ATC_DirectionSurvives` | RED (i) **293/293** ×3 (R0) | green: exempt (text root) |
| T4 | `000_ATC_AllThenProps` / `002_reset` / `004_span` | green — 224 / 80 / 65 glyph px, band-identical (R0) | green, unchanged |
| T5 | the 130 committed stems | green 130/130 (R3) | green, verdict objects identical 130/130 |
| T6 | gate-set child `fixtures/combinations/all-then-color.json` | `exit 0 … (gate-only: no committed baseline)` (`wave53-open` fixture-net.log:1223) | `exit 0` against committed baselines, `✓ no regressions vs baseline (18 platform-comparisons ran)` |

Record correction: BACKLOG 0(e), `wave53-gate/_note.md:232` and `docs/STATUS.md:2678` quote "437/437" for all three
stems; the executed replay gives 437 / 369 / 293 (437 is 001's count only).

Must-not-move cells (the corpus carriers of `All`, census (D); every one `wave53-open → wave53-final`, `cells.txt`):
css-cascade `all-prop-001` android P 0.9642 / ios P 0.9641 / web P 0.9689; `all-prop-002` P 0.9702 / P 0.9709 / P 0.999;
`all-prop-inherit-color` P 0.9992 / P 0.9993 / P 1; `all-prop-initial-color` P 0.9992 / P 0.9993 / P 0.9787;
`all-prop-initial-visited` P 0.9995 / P 0.999 / P 0.9839; `all-prop-revert-color` P 0.9992 / P 0.9993 / P 1;
`all-prop-unset-color` P 0.9992 / P 0.9993 / P 1; css-display `display-contents-button` P 0.9705 / P 0.9692 / P 0.9772;
`display-contents-details` P 0.9693 / P 0.968 / P 0.9724; `display-contents-fieldset` P 0.9693 / P 0.968 / P 0.9708;
css-ui `appearance-revert-001.tentative` P 0.9895 / P 0.9897 / P 1 — 33 cells, 33 P, unchanged across the two runs.
And every other cell.

## 2. The defect as SEEN in the pictures

All 18 PNGs are 390 px wide on the `#1A1A2E` ground (390×92 except `002_reset` 390×62 and `005` 390×72).
- `000_ATC_AllThenProps` (Android, web opened): "ATC ALLTHENPROPS" in the band at the top-left, a 160×60 green block at
  (16,16). Label present.
- `001_ATC_PropsThenAll_InGreenParent` (Android, iOS, web opened): the parent's green 160×60 block only; the child's red
  is gone, as the fixture asks. **Band empty — no label on any platform.**
- `002_reset` (Android, iOS opened): nothing but "RESET" in the band — the `all: revert` child, captured standalone,
  paints no box and **keeps its label**.
- `003_ATC_InitialUnderRedParent` (Android, iOS, web opened): green 200×60 covering the blue parent. **No label.**
- `004_span` (Android, web opened): "SPAN" in the band over the green 200×60 block — the `all: initial` child,
  standalone, **keeps its label**.
- `005_ATC_DirectionSurvives` (Android, iOS, web opened): green 200×40, white "123" at the right edge (rtl survives);
  the web digits are a serif face, the natives' sans (the face split the fixture `_comment` records). **No label.**

Measured by `label-chrome-all-reset.geometry.py` (default source = the 77fe41e8 copies), every line `→ GEOMETRY OK`:
label lit 224/224, 0/437, 80/80, 0/369, 65/65, 0/293 on each of Android / iOS / web; fill boxes (16,16)-(176,76),
(16,16)-(176,76), none, (16,16)-(216,76), (16,16)-(216,76), (16,16)-(216,56); band rows 0..15 byte-identical ×3 on all
six stems. The three platforms agree pixel-for-pixel in the band: this is one decision taken identically ×3, not a
platform defect. Fixture-net cross-check (`wave53-final` fixture-net.log:1213-1218): every pair 1.00 except
005 iOS-web / Android-web 0.97 (the face split).
sha256 prefixes of the copies: Android 000 `5f8379d7`, 001 `52d6121e`, 002 `860e284b`, 003 `df83f9d6`, 004 `8edbe69b`,
005 `9053c091`; iOS `f28193cf` `941991fa` `d067925e` `f0ea4761` `32daf8d0` `c6557d89`; web `2223f4cb` `02b59b58`
`7a534f7e` `721b776a` `6787ee01` `002f0941`.

## 3. The wire

Tracked IR of the current fixture (`tools/titan/results/wave52-all-reset-postload-colour/convert-out/all-then-color/
tmpOutput.json`, `irVersion 2`, 6 components; same component count as the wave53-final net, fixture-net.log:1166).
Walked with the device flatten rules (`tools/visual/expected-captures.mjs` `parentCreatesContext` / `dependsOnBackdrop`;
census (C)):

| capture | root `All` (data @ position) | composed children | text | label-due |
|---|---|---|---|---|
| 000_ATC_AllThenProps | `INITIAL` @1/5 | — | — | yes |
| 001_ATC_PropsThenAll_InGreenParent | none | `reset` (slot.parent) | — | **no** |
| 002_reset | `REVERT` @4/4 | — | — | yes |
| 003_ATC_InitialUnderRedParent | none | `span` | — | **no** |
| 004_span | `INITIAL` @1/5 | — | — | yes |
| 005_ATC_DirectionSurvives | `INITIAL` @2/8 | — | `"123"` | **no** |

Neither parent creates a paint context, so both children are also captured standalone (002, 004) — 6 captures, the
`6 / 6 canvases` of the net.

## 4. Mechanism (traced, three platforms)

Each capture app flattens the composed tree pre-order and hands each node to its canvas: the parent node WITH its
composed children, then each child standalone (Android `flattenComponents`, `ScreenshotCaptureScreen.kt:2515`; web
`flatten`, `CaptureGallery.tsx:354`; iOS `flatten`, `ScreenshotCaptureView.swift:407`). The label decision reads only
that node's structure and the WPT flag:
- **web** — `CaptureGallery.tsx` `CaptureCanvas` (`:453`): `showLabel = !WPT_MODE && node.children.length === 0 &&
  !(typeof component.text === 'string' && component.text.length > 0)`; twin in `FixtureCanvas.tsx:96`. `LabelChrome.tsx`
  only draws: an absolutely positioned `<svg>` mounted as a SIBLING after `<ComponentRenderer>` inside
  `[data-capture-canvas]` (`:502`). The runtime's `all` handling (`applyAllReset`, `runtimes/web/src/engine/global/
  _dispatch.ts:39`, called at `runtimes/web/src/core/renderer/StyleBuilder.ts:141`) filters the component's own property
  list; CSS inheritance flows to descendants only, and the svg is not one.
- **Android** — `ScreenshotCaptureScreen.kt` `CaptureCanvas` (`:953`): `showsLabel = !LocalWptCaptureMode.current &&
  component.children.isNullOrEmpty() && component._text.isNullOrEmpty()`; drawn in the canvas Box's `drawWithContent {
  drawContent(); … }` (`:1018-1030`), outside `.padding` and `ComponentHost`. `global/AllReset.kt` (`object AllReset`,
  applied at `core/renderer/ComponentRenderer.kt:1127`) runs inside the component subtree.
- **iOS** — `HarnessLabelChrome.swift` `showsLabel(_:wptCaptureMode:backdropPass:)` (`:91-102`): `guard
  component.children?.isEmpty ?? true` / `return component.text?.isEmpty ?? true`; mounted by `CaptureCanvas.swift`
  `.overlay(alignment: .topLeading)` after `.background(canvasBackground)` on both root branches (`:193`, `:253`),
  outside `ComponentHost`. `GlobalExtractor.applyingAllReset` runs at `Renderer/ComponentRenderer.swift:338`.

None of the three predicates reads a property, a style or a reset; there is no path from `all` to the chrome. The
contract is pinned on two platforms: web `apps/web-harness/tests/ui/LabelChrome.test.tsx` ("a root WITH text gets no
chrome", "a root WITH composed children gets no chrome; its flattened leaf keeps one", "a text root or a container gets
none"), iOS `HarnessLabelChromeRasterTests.swift` `testRootWithChildrenPaintsNoLabel` / `testShowsLabelPredicate`.
Android has no unit pin of `showsLabel` (`HarnessLabelChromeTest.kt` pins geometry only) — §6 optional U3.

**The defect** — `tools/visual/label-chrome-tripwire.test.mjs`: the per-stem loop (`:200-208`) calls
`checkTriplet(pngs, name)` for every `<platform>__NNN_<name>.png` triplet; `glyphPixels(name, width)` (`:65-80`) is a
function of the name and width only, and clause (i) (`checkTriplet`, `:87`; the push at `:95`) demands every p ∈ P be non-ground. The doc paragraph
(`DYNAMIC_CAPTURE.md` §5, "re-derives the expected glyph pixel set P per committed baseline stem") inherits the same
omission. Replay R0 reproduces the wave-53 reds byte-for-byte on the withdrawn PNGs.

## 5. Census of the radius (`label-chrome-all-reset.census.out.txt`)

- **`all` in fixtures** — 9 declarations in 2 of 445 tracked fixture JSONs: all-then-color ×4 (`ATC_AllThenProps`
  initial @1/5; `…/reset` revert @4/4; `…/span` initial @1/5; `ATC_DirectionSurvives` initial @2/8 — the only one whose
  own capture is label-exempt, by its `_text`) and `fixtures/properties/global/longtail.json` ×5 (`All_Initial`,
  `All_Inherit`, `All_Unset`, `All_Revert`, `All_RevertLayer`, each `all` LAST @4/4, childless and textless → label-due,
  not gate-listed, no baseline). No other fixture declares `all`.
- **Label-exempt nodes** (containers or text roots; an upper bound on exempt captures, since a child under a
  context-creating parent is not captured standalone) — 1212 of 5330 nodes in 121 of 445 fixtures. In the 9 gate
  fixtures: all-then-color 3 of 6 (2 containers, 1 text root), composition-test 18 of 42, blend-isolation 6 of 9,
  opacity-blend 5 of 10, nested-transforms 4 of 8, radius-overflow-transform 4 of 8, transform-abspos-pivot 4 of 12,
  visual-test 0 of 109, filter-sepia-amounts 0 of 8 — every future seeding of the six other gate-only fixtures hits
  the same tripwire red (44 exempt nodes in all) and needs this fix too.
- **Committed baselines** — 130 stems / 390 PNGs; owners by (index, name): visual-test 109, aspect-ratio 9,
  filter-sepia-amounts 8, filter-grayscale-basis 4; none unowned. One stem is claimed twice: `000_Sizing_Fixed` is a
  leaf in `visual-test.json` (its real owner; tripwire green with a label) and a text root in
  `visual-test-controls.json` at the same index. This decides the design (§6 R-c).
- **WPT corpus** (`wave53-final`) — 11 of 1435 per-test IR documents carry `All` (7 css-cascade all-prop, 3
  css-display display-contents-button/details/fieldset, css-ui appearance-revert-001) = the 33 must-not-move cells of
  §1, all P. Cells currently P that could move: **none** (no capture-path byte changes; WPT mode draws no chrome).

## 6. The fix (smallest), ownership, seams

**U1 — the tripwire learns the contract's "iff" (GO; tooling, device-free, landable any time).**
- NEW `tools/visual/label-chrome-exempt.json`: `{ "<stem>": { "fixture": "<path>", "why": "container" | "text" } }`
  with the three entries 001 / 003 (container) and 005 (text), fixture `fixtures/combinations/all-then-color.json`.
- `tools/visual/label-chrome-tripwire.test.mjs`:
  (a) verify every manifest entry structurally, independent of every pixel and runtime: the stem has a committed
      triplet (else "stale entry"); the named fixture declares ≥ 1 node of that name (any depth); every such node is
      a container (non-empty `children`) or a text root (non-empty `_text`) and `why` matches — a childless textless
      node is "a leaf, which IS labelled" → red;
  (b) a verified exempt stem runs `checkTriplet(pngs, '')` (P = ∅: clause (ii) band byte-identity applies when the band
      is ground ×3) plus NEW clause **(iv)**: per platform, NOT every pixel of the name's would-be label is non-ground
      (positional, no colour — review C39). An unlisted stem runs today's path unchanged;
  (c) synthetic controls for the new path: label drawn ×3 on an exempt stem → (iv) ×3; label on one platform only →
      (iv) on that platform; manifest lists a leaf → red; stale entry → red;
  (d) on a red (i) stem whose P is fully ground on all three, print a HINT naming the fixture node(s) of that name that
      are containers / text roots and the manifest line to add — a message, never a pass.
  Executed prototype of (a)/(b): replay R1 (6/6 green, 3 entries verified), R2 (m1–m5 red), R3 (130/130 verdict objects
  identical, 0 listed).
- Optional, same lane: move the pure checker (`normalize`, `truncatedCount`, `glyphPixels`, `checkTriplet`, the manifest
  verifier) to a new `tools/visual/label-chrome-check.mjs` so replays import it without registering the 136 node:test
  cases (the replay needed `--test-name-pattern`); keeps the test file under ~300 lines (209 today).

**U2 — seed, then prove (device step, after U1, same squash).**
1. On the closing tree, quiet host: `UPDATE_BASELINE=1 ./test-all.sh fixtures/combinations/all-then-color.json` (refuses a
   short column, exit 7). LOOK at the six canvases ×3 (labels on 000 / 002 / 004 only).
2. `python3 tools/titan/results/wave54-plan/label-chrome-all-reset.geometry.py baseline` → 18 × `GEOMETRY OK`, then
   `cmp` each new PNG against `label-chrome-all-reset.seeded-77fe41e8/` (expected identical; any difference is named and
   looked at before committing, not waved through).
3. `node --test tools/visual/label-chrome-tripwire.test.mjs` → 136 stem tests + synthetic all green; then the whole
   tooling suite (the CI glob) green on the ship tree — the step wave 53 skipped.
4. `BASELINE=1 ./test-all.sh fixtures/combinations/all-then-color.json` (or the closing net) → `exit 0`, §1 T6 lines.
5. Docs, orchestrator-owned, same commit: `DYNAMIC_CAPTURE.md` §5 tripwire paragraph (exempt manifest + clause (iv));
   the tripwire header (130 → 136 stems / 390 → 408 PNGs; the negative-control record stays); `docs/STATUS.md:19`
   (390 / 130 → 408 / 136); the live tooling count in `CLAUDE.md` and the `docs/STATUS.md` suite table
   (`doc-staleness-check.sh:85-104` derives it — +6 stem tests + U1's new synthetic tests); the trailing
   all-then-color comment in `tools/visual/gate-fixtures.txt` and the "Gate: … Baselines: NOT captured" sentences of
   the fixture `_comment`; BACKLOG 0(e) discharged with the corrected cause, and a correction line (not a rewrite) under
   `wave53-gate/_note.md` "Post-net correction" and `docs/STATUS.md:2678`.

**Ownership (disjoint):** U1 — `tools/visual/label-chrome-tripwire.test.mjs`, `tools/visual/label-chrome-exempt.json`
(new), optionally `tools/visual/label-chrome-check.mjs` (new). U2 — the 18 new PNGs under `tools/visual/baseline/`.
Read-only for this family: the three drawers and predicates, the runtimes' `all` code, the fixture's components.
**Seams: none** — no ComponentRenderer (×2), no `extract-fixture.mjs`, no web-harness `ComponentRenderer.tsx`, no
capture-path file. Optional **U3** (default OUT): a pure Android `harnessShowsLabel(component, wpt)` + JVM pin mirroring
iOS `testShowsLabelPredicate` — it edits `ScreenshotCaptureScreen.kt`, already over its size budget (BACKLOG 0(d)),
and buys no cell; take it only if a lane owns that file anyway.

**Rejected:**
- (R-a) "Fix the chrome's isolation from the cascade" (0(e)'s wording): there is nothing to isolate (§4). A lane that
  makes labels appear on these canvases has changed the contract, not fixed a leak.
- (R-b) Change the contract to label containers and text roots: edits all three predicates and their web/iOS pins and
  §5, and changes every container / text-root capture of every non-WPT fixture (≤ 1212 nodes; 44 in the gate-only gate
  fixtures). A user-level design decision, not a wave-54 lane — NO-GO here.
- (R-c) Infer exemption by matching stems to fixtures by (index, name): ambiguous today (`000_Sizing_Fixed`, §5), and
  the nested numbering needs a fifth copy of the flatten rules over CSS property names. The manifest names the fixture.
- (R-d) Infer exemption from the pixels (band ground ×3 ⇒ exempt): circular — it would pass a label lost on all three
  platforms by a predicate regression, the failure the tripwire exists to catch.

## 7. Geometry probe design (written and executed: `label-chrome-all-reset.geometry.py`)

One line per (stem, platform), ending in an exact verdict:
`<stem> <platform> <w>x<h> due=<bool> rootAll=<bool> label <lit>/<|P|> band-ground=<bool> fill=<bbox> → GEOMETRY OK`
or `→ GEOMETRY WRONG (<why>)`, why ∈ {`label absent: k/n glyph px ground`, `label on an exempt capture: k/n glyph px
lit`, `band rows 0..15 carry paint on an exempt capture`, `fill box … vs expected … at (16,16)`, `missing capture`};
`due` comes from the FIXTURE (no children, no `_text`), P from `tools/visual/block-font.json` exactly as the tripwire
derives it. Then one `band rows 0..15 byte-identical ×3: <bool>` line per stem and two summary lines (label ⇔ due;
label ⇔ root has no `all`). Exit 1 on any WRONG.
- Executed on the 77fe41e8 copies: 18 × `→ GEOMETRY OK`, 6 × band-identical True, `18/18` vs `3/18`, exit 0.
- Teeth (`--naive` = today's tripwire assumption): 001/003/005 × 3 platforms print `→ GEOMETRY WRONG (label absent:
  437/437 | 369/369 | 293/293 glyph px ground)`, exit 1.
- Post-seed reading (U2.2): `… geometry.py baseline` must print 18 OK. **The tripwire run itself is the oracle of
  record** (replay R0–R3 is its executed dry run); the probe is the picture check behind it.

## 8. Predictions

- T1–T3 RED → green (exempt, band-identical ×3). **HIGH**; floor: all three green with band byte-identity on all
  three platforms — R1 executed on the same bytes. Falsified by: a seeded PNG whose band is not ground ×3.
- T4 green → green, glyph counts 224 / 80 / 65. **HIGH** (R0/R1).
- T5 130/130 green with identical verdicts. **HIGH** (R3 executed).
- U1 mutations m1–m5 all red. **HIGH** (R2 executed).
- T6 gate-only → `exit 0` with `· cross-platform gate: 18 pair(s) · 0 known divergence(s) · 0 unexpected`,
  `· spec oracle: 12 platform-component check(s) · 0 violation(s)`, `✓ no regressions vs baseline (18 platform-comparisons
  ran)`. **MED-HIGH**; floor: exit 0 — the identical comparison ran green against the 77fe41e8 bytes at `wave53-final`
  (fixture-net.log:1222-1246). Risk: a wave-54 runtime lane changing these boxes between seed and net (seed on the
  closing tree).
- Fresh seed byte-identical to 77fe41e8 on 18/18. **MED** (same host stack as wave 53; a wave-54 lane may legitimately
  move a pixel — then it is named, not assumed).
- Corpus: **0 gained / 0 lost / 0 movers / 0 newly measured** attributable to this family. **HIGH** — no capture-path
  byte changes, chrome is off in WPT mode. Must-not-move: the 33 cells of §1 and every other cell.

## 9. Risks

- R1 **Order of landing.** PNGs without U1 re-create the wave-53 red (CI `test-tooling`); U1 without PNGs is inert but
  green. Same squash, and run the full tooling glob on the ship tree after the seeding (wave 53 ran it only after the net).
- R2 **Clause (iv) false red** on a future exempt stem whose own paint (shadow, outline, negative offset) covers every
  glyph pixel of its name in rows 6..12. None of the three entries does (band ground ×3). If a future seeding hits it,
  refine (iv) then (e.g. P pixels equal to the ink-over-underpaint byte); do not pre-build it.
- R3 **Manifest drift** — a renamed component or moved fixture turns the verification red: wanted, loud.
- R4 **Name-based baseline detection.** `test-all.sh` `_gate_fixture_has_baselines` (`:132-145`) switches a gate fixture
  to BASELINE mode if ANY PNG names one of its components. After seeding, a gate fixture declaring `reset`, `span` or an
  `ATC_*` name would be compared against these PNGs (and could exit 2 on 0 comparisons). Census: no collision in the 9
  gate fixtures today. Generic names are a latent hazard; renaming the fixture's children is NOT proposed (the wave-52
  fixture oracles pin them).
- R5 **Doc-staleness.** The tooling count is derived live (`doc-staleness-check.sh:85`) — CLAUDE.md and STATUS.md must
  carry the new number in the same commit, or CI's doc-staleness job goes red.
- R6 **Stale clause in obligation 0.** "fixture net exit 0 on all 9 gate fixtures (all-then-color now has committed
  baselines, so it can go stale)" is false since the withdrawal: at `wave54-open` the all-then-color child must read
  `(gate-only: no committed baseline)` exactly as at `wave53-open` (fixture-net.log:1223) — the orchestrator should
  read the opening net that way, not as a missing baseline.
- R7 **The record's wording.** Leaving "its `all` reset reaches the chrome" in BACKLOG / STATUS / the wave-53 note
  invites an R-a lane; U2.5 corrects it.

## 10. Recommendation — GO-SMALL

One tooling file, one new data file, 18 PNGs and doc lines; no runtime, harness or seam edit; zero WPT radius. It
discharges 0(e), turns all-then-color into a baselined fixture, and unblocks seeding the six other gate-only fixtures
(44 exempt nodes) later. It is small, but not free: U2 needs a device run and a look at the pictures.

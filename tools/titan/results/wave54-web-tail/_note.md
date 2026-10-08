# wave 54 · L6 web-tail — lane note

Builder lane L6 of wave 54 (PLAN §2 "### L6 · web-tail"). Shared tree
`/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf`, branch `campaign/applier-campaign`,
HEAD `db6e8aa0`. Built 2026-10-08 19:05–19:40. Nothing committed; no worktree, checkout, stash or reset. No device,
emulator, simulator, Chromium or full suite was run by the lane. The step-0 Chromium probe was run by the orchestrator
(19:10, `step0-probe.out.txt`).

**Result in one paragraph.** Both units are built, pinned on verbatim wire, and every pin was shown able to fail by an
executed mutation (red → restored byte-exact → green). Step 0 printed **`BRANCH R`**, exactly as predicted, so W1 lands as
the runtime files. The real-code DOM census over all 1435 per-test IR documents measures the lane's radius. It **equals
the pre-registered carrier sets exactly**: RS 27 documents, W1 1, landed 28 = `expectations.json` web carriers; 0 native, 0
wire. All 13 web must-not-move cells keep an identical DOM. The pure lift alone changes **0 / 1435** documents. The
geometry-gate self-test holds. The pixels are the probes' to measure.

## What changed and why

### Unit RS — one collapsed space between inline-level ROOTS on the composed web canvas

The defect. The extractor stamps `meta.role: "ws-after"` on the earlier of two siblings that source white space
separated (`tools/titan/extract-fixture.mjs` `WS_AFTER_ROLE`). The harness replayed it only between CHILDREN
(`ComponentRenderer.tsx` `HARNESS_OPTIONS.renderChildSeparator`, wave-26 WWS). The body's element children are sibling
ROOTS of the flat v2 forest, and `ComposedCaptureGallery.tsx` rendered them flush (`flowRoots.map` / `canvasRoots.map`).
I looked at the pictures (ref │ web │ iOS crops of box-sizing-007, -022 and semi-replaced-stretch-other): the web's second
atom column sits at x146 against the ref's x151 (box-sizing-022 shows one merged run), and the natives sit on x151.
CSS 2.1 §16.6.1 and css-text-3 §4.1.1 say a collapsible white-space run between inline-level boxes renders as one space;
CSS 2.1 §9.2.2.1 says none renders between blocks.

The change:
- **NEW `apps/web-harness/src/ui/ComposedRootSeparator.ts`** (109 lines).
  - `interleaveRootSeparators(roots, render, container, separator)` is the root-level twin of NodeRenderer's
    child-walk separator slot. The first root gets no slot, a null answer keeps the pair flush, and a forest with no
    accepted pair returns exactly the bare `.map` array.
  - It **declines** for the table-body plan's `table` / `inline-table` wrapper (`TABLE_BOX_DISPLAYS`), as
    pre-registered. See hand-off D3: this decline is conservative.
  - `rootSeparatorContainer(doc, tableBody)` reads `display` (the table plan's box, else `flow-root`, which is what
    both the ICB div and the margin wrapper are) and the body-root's declared `white-space` (via `buildStyles`, the
    same CSS spelling `renderChildSeparator` reads).
  - Rule 2b: the predicate is a PARAMETER, and this file never imports `wsAfterSeparator`.
- **seam-1.patch** (`ComponentRenderer.tsx`, a pure lift).
  - The WWS branch of `renderChildSeparator` (:1115-1153, after the kept `WPT_MODE` gate, widget rule and
    `WPT_COMPOSED_MODE` gate) moves verbatim into the exported `wsAfterSeparator(prev, next, container)`, inserted
    after `isInlineLevelSibling` (:332).
  - `renderChildSeparator` now ends `return wsAfterSeparator(prev, next, ctx.styles);`.
- **Patch-borne, inside seam-1.patch** (rule 2b: they consume the lifted export):
  - `ComposedCaptureGallery.tsx` changes only the import and the two root `.map` sites, which become
    `interleaveRootSeparators(…, rootSeparatorContainer(doc, canvasTableBody), wsAfterSeparator)`.
  - NEW `apps/web-harness/tests/ui/ComposedRootSeparatorWire.test.tsx`: the verbatim-wire and call-equivalence pins.

### Unit W1 — a paint-inert out-of-flow member no longer splits an `auto` word on web (branch R)

The defect. On `hyphens-out-of-flow-002` web (f 0.9411 on all 13 runs since wave 35), boxes 4/5
`high<span abspos>abspos</span>way` are one 26-px line. Step 0 settled who owns that split:

- V0 is the raw markup and V6 is the raw WPT page. Both are 26 on boxes 4/5, so **host Chromium-on-macOS fails the WPT
  test itself**: the out-of-flow element inside the word hides the `high|way` dictionary point.
- V3, the W1 shape `highway` + member, is 46.
- V4 is two plain spans `<span>high</span><span>way</span>` and is also 26. So on this host any element boundary inside
  an `auto` word blocks the dictionary point (hand-off D6).

css-text-3 §5.1: out-of-flow elements introduce no soft wrap opportunity. §5.4: `auto` hyphenates at the dictionary
point. CSS 2.1 §10.3.7 / §10.6.4: only the member's static position moves.

The change:
- **NEW `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts`** (187 lines).
  - `isInertOutOfFlowMember` is a byte-for-byte restatement of Compose `InertOutOfFlowMember.admits`: no
    children / runs / decorations; Position ABSOLUTE|FIXED required; Color with `srgb.a` ≤ 0 required; Hyphens tolerated;
    anything else refuses. `keywordOf` / `alphaIsZero` restate `ValueExtractors.extractKeyword` and `floatOrNull`.
  - `joinsAcrossWord` adds Compose's `InlineRunFold.TEXT_MEMBER_TAGS` ring (span / time / data), which the fold
    applies before the predicate. It keeps an abspos `<img>` / `<input>` (which paint whatever their colour) out.
  - `joinWordsAroundInertOutOfFlow(entries, children, hyphensAuto)` rewrites `[P, M, N]` → `[P+N₁, M, N₂]` (N₁ = N up
    to its first CSS white space). Several members in one word land after it in order, and the function returns the
    caller's own array when nothing joins.
- **`runtimes/web/src/renderer/InlineRuns.ts`**:
  - `resolveRuns` takes an optional 4th argument `opts: { hyphensAuto?: boolean } = {}` and applies the join after
    validation. The member stays `claimed`, so rule 4 never paints it twice.
  - `RunsPlacement.joinedOutOfFlowMembers` counts the reader deviation from spec 03 §4.1 rule 1. It is the web twin of
    Compose `Folded.droppedOutOfFlowMembers`.
- **`runtimes/web/src/renderer/NodeRenderer.ts:339`** changes one line: it passes
  `{ hyphensAuto: styles.hyphens === 'auto' }`, the host's OWN computed value.
- **NEW `runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx`**: pins (a)-(g) plus three mechanics pins.

## Revert unit RS — the commit's exact paths

- `apps/web-harness/src/ui/ComposedRootSeparator.ts` (new, untracked; sha256 `1df076a6…cdafc1`)
- `apps/web-harness/tests/ui/ComposedRootSeparator.test.ts` (new, untracked; sha256 `a72f9ba0…ed13748`)
- then `git apply tools/titan/results/wave54-web-tail/seam-1.patch` (sha256 `b741d2c0…fab70d`), which brings:
  - `apps/web-harness/src/sdui/ComponentRenderer.tsx` (the seam; base sha256 `cc39ab6e…a4d8e`, patched `c780ac1c…8028d2`);
  - `apps/web-harness/src/ui/ComposedCaptureGallery.tsx` (base `87ac9ca4…47b9`, patched `badb03dc…893d2`);
  - `apps/web-harness/tests/ui/ComposedRootSeparatorWire.test.tsx` (new).

  `git apply --check` is clean on db6e8aa0 (a temporary index from `git read-tree db6e8aa0`) and on the working tree.
- Revert alone: yes. W1 shares no file with RS.

## Revert unit W1 — the commit's exact paths (branch R, decided by step 0)

- `runtimes/web/src/renderer/InertOutOfFlowWordJoin.ts` (new, untracked; sha256 `21389d5a…a0512e`)
- `runtimes/web/tests/renderer/InertOutOfFlowWordJoin.test.tsx` (new, untracked; sha256 `34d069ed…123bdf`)
- `runtimes/web/src/renderer/InlineRuns.ts` (modified; sha256 `eb7dcd38…d3eb6a99`)
- `runtimes/web/src/renderer/NodeRenderer.ts` (modified, one line :339; sha256 `69dd160f…afae8b7b`)
- No seam. **No `seam-2.patch` exists at the registry path.** The branch-H alternative was built and verified before
  the step-0 result was read. It is kept as a record only, in `branchH-not-landed/` (seam-2.patch with a "NOT LANDED"
  header, its verify log, its mutations, its census). `integrate-seams.sh` skips the slot ("the lane delivered none").

## [W-L6] step 0 — SERVED (orchestrator, 2026-10-08 19:10)

- `step0-probe.out.txt`: `V0 26 · V1 26 · V2 26 · V3 46 · V4 26 · V5 46 · V6 [46,46,46,26,26,46,46]` → **`BRANCH R`**, exit 0.
- Every pre-registered value held: V1 = 26, V3 = 46, V5 = 46. The MED prediction (V0 = 26, V6 boxes 4/5 = 26 → R) came
  true.
- The run used the 19:09 copy of `step0-probe.mjs`. At 19:24 I removed an optional "extras" block (V7/V8 read from a
  `step0-harness-markup.json` that never existed), so the V0-V6 code that ran is the current file's
  (sha256 `b8ac9716…ea0508`). A re-run is not needed.

## Census (the lane's carrier set, re-derived with the REAL code)

### Method

- `dom-census.census.test.tsx`, run through `vitest.census.config.mts` (the harness vite config; it includes only this
  directory's `*.census.test.tsx`).
  - It renders every per-test IR document of `wave53-final` (1435) through the real `ComposedCaptureGallery`, with
    `window.location.search = '?wpt=1&wptComposed=1'` (the capture URL's two module constants).
  - It writes one sha1 of the static markup per document. `dom-census-diff.py` diffs two tables.
- `carrier-crosscheck.py` compares a diff with `expectations.json` `lanes["L6-web-tail"]`.
- `mnm-crosscheck.py` checks the 68 must-not-move cells.
- Same DOM ⇒ same Chromium paint (web capture is byte-deterministic on this host, BACKLOG "Wave 53 lessons"), so this is
  the DOM-level twin of `control-check.mjs`.

### Results

| state of the tree | changed / 1435 | verdict vs pre-registered |
|---|---|---|
| base (`dom-census.base-db6e8aa0.json`, before any lane edit) | — | — |
| lift only (`seam-1.liftonly.patch`: the ComponentRenderer.tsx hunk without the call sites) | **0** | the pure lift is byte-identical corpus-wide: call-equivalence over the corpus |
| seam-1 (RS) | **27** | EQUAL to `revertUnits.RS.captures.web` (27) |
| W1 runtime, no seam | **1** (`hyphens-out-of-flow-002`) | EQUAL to `revertUnits.W1.captures.web` |
| branch H (seam-2, W1 runtime set aside; not landed) | 1 (`hyphens-out-of-flow-002`) | EQUAL |
| **as landed, branch R** (seam-1 + W1 runtime) | **28** | **EQUAL to `captureCarriers.web` (28)**; ios 0 / android 0 / wire 0 pre-registered, and no Swift or Kotlin file is touched |

- The as-landed census on `wave54-open` IR equals the one on `wave53-final` IR: 0 / 1435 differ
  (`dom-census.landed-R.wave53-final-vs-wave54-open.out.txt`).
- The 13 web must-not-move cells all keep an identical DOM. The 55 native must-not-move cells are untouched by
  construction (`mnm-crosscheck.py`).

### Pair census (`rs-pairs.census.test.tsx`, real `wsAfterSeparator`, under the seam lock)

- **73 separators in 27 documents.** This is the brief's and skeptic r2's count, document by document.
- Table bodies: 1 (`s-11-1-1b-006`, which has 0 inline-level pairs). Bodies declaring white-space: 0. Separators
  withheld by any decline: **0**.

### W1 join census (`w1-joins.census.test.tsx`, real `resolveRuns` as NodeRenderer calls it)

- 264 runs hosts, 7 of them with their own `hyphens: auto`.
- 21 abspos/fixed run members, 12 of them inert. The brief's 24 adds 3 floats.
- **4 hosts joined, all in -002** (boxes 3-6).

### How many pass today (wave53-final, web)

- RS carriers: 20 P (14 of them DEGENERATE with packed atoms) · 6 f · 1 unscored (`overlay-button-appearance`).
- W1: f 0.9411.

## Pins and executed mutations

Mutation runs use `mutate.py` (`<file> sha256 before` → mutate → RED → restore → `sha256 == before` → GREEN). The seam
side runs under `seam-verify.sh`. That script holds `tools/titan/runs/wave54-lock/ComponentRenderer.tsx`, logs the seam
files' sha256 before and after, and restores with `git show HEAD:<path> > <path>`. After every run the seam files read
`cc39ab6e…a4d8e` and `87ac9ca4…47b9` again, the patch-created test file was removed, and the lock was released.

### RS (logs `verify-seam1.log`, `verify-rs-mutations.log`, `verify-landed-R.log`)

Pins:
- **Unseamed** (`tests/ui/ComposedRootSeparator.test.ts`, 7 tests):
  - first root never gets a slot;
  - forest order;
  - null keeps the array identical;
  - `(prev, next, container)` passed in order;
  - table / inline-table decline;
  - container read: flow-root, table, the body's `white-space` in CSS spelling.
- **Seam-borne** (`ComposedRootSeparatorWire.test.tsx`, verbatim box-sizing-007, 22 components, sha256 `8f7b0da0…`):
  - **19 separators, none after the body-root or the `<p>`**;
  - the real gallery's markup carries 19 root gaps among 20 `<img>`s;
  - the flow-wrapper call site (body margin) also gives 19;
  - an unmarked pair stays flush (18);
  - a table body → 0;
  - a `white-space: pre` body → 0;
  - call-equivalence: 224 marker × level × container combinations, child walk ≡ `wsAfterSeparator`, 8 spaced.

With the seam applied: 15 harness files, 120 tests green, plus `tsc --noEmit` OK. As landed: 16 files, 133 tests.

Mutations — every one RED → restored → GREEN:

| mutation | red tests | file sha256 before = after |
|---|---|---|
| RS-m1: `isWsAfterMarked` gate removed (`wsAfterSeparator`) | flush_*, call-equivalence (16 ≠ 8), interInlineWs "keeps flush siblings flush" | `c780ac1c…8028d2` (patched ComponentRenderer.tsx) |
| RS-m2: table-box decline removed | table_* (unseamed), tableBody_* (wire) | `1df076a6…cdafc1` |
| RS-m3: first-root guard removed | firstRoot_*, predicate order, bs007_* ×4, flush, preBody | `1df076a6…cdafc1` |
| RS-m4: flow-wrapper call site handed a never-separating predicate | bs007_gallery_flowWrapperSite | `badb03dc…893d2` (patched gallery) |
| RS-m4b: ICB call site handed a never-separating predicate | bs007_gallery_markup, flush (gallery half) | `badb03dc…893d2` |

### W1 (log `verify-w1-mutations.log`, re-run on the final files)

Pins (`InertOutOfFlowWordJoin.test.tsx`, verbatim -002 (sha256 `0f1d9251…`), -001 box 4 (`dec51fc7…`) and the 24 census
members):
- (a) box 4 → `[text "highway", child]`, joined 1, nothing unreferenced;
- (b) boxes 3 / 5 / 6 the same;
- (c) word-edge boxes 2 / 7 and SPACED splits (`"high "`/M/`"way"`, `"high"`/M/`" way"`) unchanged, 0 joined;
- (d) -001 box 4 (`manual`) unchanged;
- (e) alpha 1, no alpha, a background, a border colour, relative, no Color and an `<img>` tag are all refused, while a
  tolerated `Hyphens` still joins;
- (f) package-default `renderToStaticMarkup` of box 4: `>highway<span`, no `</span>way`, one member element;
- (g) the predicate over the 24 census members = the census's 12 Compose trues, and the tag ring agrees;
- mechanics: two members in one word move after it in order (joined 2); a continuation with white space lands the
  member at the WORD end; no join gives the pre-W1 plan.

`tests/renderer/` (12 files, 138 tests) and `tsc --noEmit` are green.

Mutations — every one RED → restored → GREEN:

| mutation | red | sha256 before = after |
|---|---|---|
| W1-m1: the join never armed (InlineRuns) | (a) (b) (e) (f) + mechanics | `eb7dcd38…d3eb6a99` |
| W1-m2: over-narrow — both fragments ≥ 3 chars | (b), two members | `21389d5a…a0512e` |
| W1-m3: mid-word test dropped | (c) | `21389d5a…a0512e` |
| W1-m4: `auto` gate dropped | (d) | `21389d5a…a0512e` |
| W1-m5: Color requirement dropped (`outOfFlow \|\| transparentInk \|\| true`) | (e) "no Color" | `21389d5a…a0512e` |
| W1-m5b: widened to any abspos | (e), (g) | `21389d5a…a0512e` |
| W1-m6: NodeRenderer paints at the wire slot (no `hyphensAuto` passed) | (f) | `69dd160f…afae8b7b` |
| W1-m7: predicate drift — alpha read from `srgb.alpha` | (a) (b) (e) (f) (g) + mechanics | `21389d5a…a0512e` |

### Branch H record (not landed)

`branchH-not-landed/verify-branchH.log` holds the record. With the W1 runtime set aside and seam-2 applied:
- 46 tests and `tsc` were green;
- the DOM census changed 1 / 1435;
- H-m1…H-m5 were all RED → GREEN;
- the W1 runtime files were put back byte-exact (sha256 logged).

## Geometry gate (rule-5 self-test)

`python3 tools/titan/results/wave54-plan/geometry-gate.py wave53-final --base wave53-open --lanes L6 --self-test` exits
**0** and HOLDS (`geometry-gate.L6.self-test.wave53-final.out.txt`):

| class | keys | today |
|---|---|---|
| gating (box-sizing-007/-008/-022 web, `hyphens-out-of-flow-002` web) | 4 | all FAIL |
| control | 44 | all PASS |
| report | 15 | all FAIL |

0 UNMEASURED and 0 probe self-check failures. Rule 4 names RS and W1.

## Pictures looked at

These crops are scratch copies, not committed; their sources are the frozen refs and the wave53-final captures.

| test | what the picture shows |
|---|---|
| box-sizing-007 | ref │ web │ iOS: second column x151 │ x146 │ x151. The ref's bold `<strong>` is the corpus residue on every platform. |
| box-sizing-022 | the ref has two rectangles with a 5-px gap; web has one merged run. |
| semi-replaced-stretch-other | the second-column border is at x192 in the ref and x187 on web. The "abel" vs "label" difference is a separate defect. |
| scope-pseudo-element | the ref's boxes are at x16/123/229; web's are flush at x16/118/220. |
| display-flow-root-list-item-001 | the picture is wrong on web for other reasons. |
| the at-risk passes | `appearance-auto-input-non-widget-001` (`defChoose File` vs `def Choose File`), `attr-style-sharing-1` (`greennot green` vs `green not green`) and `baseline-with-orthogonal-flow-001` (`bbbccc` vs `bbb ccc`) all pass today with visibly PACKED atoms, which RS moves toward the ref. `static-inside-inline-block` and `box-sizing-009` show nothing the separator moves. |

## Predictions (wave53-final = wave54-open → closing gate; web only; 27 rows of `expectations.json`)

The pre-registered rows stand unchanged. The lane's evidence per row:

| cell (web) | from → predicted | conf. | floor | lane evidence |
|---|---|---|---|---|
| css-ui/box-sizing-007 | f 0.9036 → P ≈0.985 | HIGH | 0.975 | DOM: exactly 19 root gaps; brief replay 0.9852 |
| css-ui/box-sizing-008 | f 0.8943 → P ≈0.974 | HIGH | 0.965 | DOM: 5 gaps |
| css-ui/box-sizing-022 | f 0.9442 → P ≈0.970 | MED-HIGH | 0.96 | DOM: 1 gap |
| css-position/…-semi-replaced-stretch-other | f 0.941 → P ≈0.967 | MED | — | DOM: 6 gaps |
| box-sizing-010/011/014…019 · 020/021/024/025 · 013 · semi-replaced-stretch-input | P DEGENERATE → ≈0.982 / 0.982 / 0.970 / 0.966 | MED | — | DOM: 1/1/1/8 gaps; picture-correctness only if each key prints GEOMETRY OK |
| css-cascade/scope-pseudo-element | f 0.9353 → up | LOW | — | **my replay `scope-pseudo.replay.out.txt`: 0.9507–0.9628 for ±1-px shifts**; a P there is **DEGENERATE** (the boxes' contents stay wrong: "B/Foo" wrap, missing markers) — never a fix |
| css-display/display-flow-root-list-item-001 | f 0.8003 → mover (undirected) | LOW | — | body is monospace 16 px: the separator measures in the canvas font (reach limit D5) |
| at-risk passes (static-inside-inline-block, block-in-inline-015-print, appearance-auto-input-non-widget-001, attr-style-sharing-1, baseline-with-orthogonal-flow-001, box-sizing-009) | stay or rise | MED | — | three of them visibly packed today (above); block-in-inline-015-print: the source has the same white space between the inline wrappers of blocks, which collapses (expected byte-identical) |
| css-text/hyphens/hyphens-out-of-flow-002 | f 0.9411 → **P 1** | **HIGH (branch R confirmed)** | 0.995 | step-0 V3 = 46 (box 7's shape); DOM: boxes 3-6 become `<span>highway</span><span …>abspos</span>` |

**Expected from L6:** web +4 at HIGH / MED-HIGH (007, 008, 022, -002), +1 at MED (semi-replaced-other), 14
DEGENERATE → faithful at MED. A possible LOW P on scope-pseudo-element is listed but DEGENERATE. Lost 0. Natives:
nothing.

## Must not move (68 cells: 13 web, 55 native)

- Every native cell of the 26 scored RS documents, except block-in-inline android (L5's carrier). No Swift or Kotlin file
  is touched.
- Web: `hyphens-out-of-flow-001`, `hyphens-span-002`, `hyphens-auto-control` and `hyphens-auto-inline-010`.
- Web: the 9 other tests whose out-of-flow run members W1 refuses (between-float-and-text, hypothetical-inline-alone,
  static-inside-inline-001/-002/-003, all-prop-001, first-letter-list-item-dynamic-001,
  abspos-container-change-dynamic-001, ch-unit-001). `semi-replaced-stretch-other` is RS's carrier; W1's refusal of its
  member is pinned by (g).
- Natives of `hyphens-out-of-flow-001` and `-002`.
- Executed check: the 13 web cells keep an identical composed DOM, as landed and under branch H alike.

## Hand-offs

### Seam patches

- **`seam-1.patch`** (RS) — deliver as registered in PLAN §3. Apply right before the RS commit. It needs
  `ComposedRootSeparator.ts` (untracked) in the same commit, because `integrate-seams.sh --dry-run` carries only
  `git diff HEAD`, and that excludes untracked files.
- **`branchH-not-landed/seam-2.patch`** — NOT for landing (step 0 = R).

### Hunks for other lanes

None.

### ORCHESTRATOR WINDOW REQUESTS

[W-L6] step 0 has been served (above). The lane has no outstanding request. The stage-2 probe and the closing gate read L6
per PLAN §6:
- RS: box-sizing-007 ≥ 0.975, -008 ≥ 0.965, -022 ≥ 0.96, each `GEOMETRY OK`; every native cell of the 26 documents
  identical.
- W1: -002 web ≥ 0.995 with `heights [46, 46, 46, 46, 46, 46, 46] … → GEOMETRY OK`; every other hyphens web cell
  identical.

### Docs hand-offs (orchestrator's docs pass; not owned by L6)

1. **`schema/spec/03-children.md` §4.1, a renderer-latitude paragraph.** Proposed text:

   > A reader MAY render a `{child}` run member away from its wire slot — or, in a folded paragraph, not mount it — only
   > when the member is out of flow (`position: absolute | fixed`) AND paints nothing (alpha-0 `color`, no declaration
   > but `hyphens`, no children / runs / decorations of its own, a no-UA-ink text tag) and it splits a word. CSS Text 3
   > §5.1 gives such an element no soft wrap opportunity, so keeping the word whole keeps the reader's line breaking
   > faithful. The member's static position (and accessible-text order) is the stated cost. Every such move is counted:
   > web `RunsPlacement.joinedOutOfFlowMembers` (moved to the word's end, only under the host's own `hyphens: auto`);
   > Compose `InlineRunFold.Folded.droppedOutOfFlowMembers` (dropped, any `hyphens`).

   No wire byte changes, so this is not a freeze event.
2. **BACKLOG (platform behaviour, step 0 measured on CfT 151 / macOS 27).** Chromium-on-macOS loses the `auto`
   dictionary point at ANY element boundary inside a word: V0 (raw, out-of-flow member) = 26, V4 (two plain spans) = 26.
   The WPT test `hyphens-out-of-flow-002` fails in host Chromium itself. W1 is a product-level workaround with precedent
   (wave-52 `bakedMarkerPlan`).
   - Latent hazard (the W2 question, still not taken): the harness's per-piece `<span>` (`renderText`) would block
     hyphenation for any `auto` word split across two `{text}` entries. The corpus has 0 such words.
3. **BACKLOG 0(c) / 5.** The -002 web cell is addressed by W1, and the box-sizing web tail by RS (pending the probe).
4. **Reach limits recorded in code** (TODOs):
   - W1 reads only the host's OWN `hyphens` (0 inherited members in the corpus);
   - inherited paint the predicate cannot see (an ancestor `text-shadow` / `-webkit-text-stroke` on transparent glyphs),
     a limit Compose shares (0 members);
   - the table-body decline (D3);
   - the separator font (D5).

### Where the brief / plan and the tree disagree (the tree wins; listed for the skeptic)

- **D1 — test location.** PLAN's own-list names `apps/web-harness/src/ui/ComposedRootSeparator.test.ts`. The harness
  tests live under `apps/web-harness/tests/ui/` (`tsc` includes `src` only), so the test is
  `apps/web-harness/tests/ui/ComposedRootSeparator.test.ts`, plus the patch-borne `…/tests/ui/ComposedRootSeparatorWire.test.tsx`.
- **D2 — size.** `InertOutOfFlowWordJoin.ts` is 187 lines, against the plan's "≤ 100". The house rule (≤ 200) holds. The
  extra lines come from the word-end split and the why-comments.
- **D3 — the table-body decline is conservative, not spec-forced.**
  - CSS 2.1 §17.2.1 rule 1 drops a white-space run only between table-internal boxes or captions, and the inline-level
    gate already excludes those.
  - Between two inline-level non-table roots, rule 2 wraps both and the space into one anonymous cell, where the space
    survives.
  - Kept because it is pre-registered and pinned. Its corpus cost is 0 (the census withheld 0 separators).
  - TODO: measure in Chromium, then drop.
- **D4 — `[P, M, N] → [P+N, M]` is implemented as `[P+N₁, M, N₂]`** (the member lands at the WORD end, not the run end).
  It is identical on all 4 corpus hosts (N₂ = ∅) and pinned by the mechanics test.
- **D5 — the separator's advance is measured in the canvas font** (Inter, inherited by the ICB), not in the body's
  declared font. Only `display-flow-root-list-item-001` (body `monospace`) is affected, an undirected LOW mover.
- **D6 — the tag ring.** W1's join also requires Compose's `TEXT_MEMBER_TAGS`, which the Compose fold applies before
  `admits`. All 12 inert corpus members are `<span>`, so the reach is unchanged.
- **D7 — `scope-pseudo-element` web.** PLAN §2 L6's table calls it "undirected, exempt from rule 2", but `expectations.json`
  (generated by `plan-build.py`) has `direction: up-or-stay`, and §6's list of ten undirected rows omits it. Rule 2
  therefore binds it, so a Δ ≤ −0.002 would revert RS. My replay says up (0.951–0.963), so the binding is probably safe.
  The orchestrator should pick one wording.
- **D8 — the anchors.** The lift is :1108-1153 in today's numbering (the WWS branch body is :1115-1153). The widget
  rule :1105-1107 stays in place, matching the fix-round-2 restatement of §3.

## What I could NOT verify

- **No pixels.** Every score and geometry line is the probes' to measure; nothing here is a picture claim.
  - The 4.5-px separator advance landing on x151 / x192 is the brief's replay and the wave-26 WWS measurement.
  - The -002 box heights after W1 rest on step-0 V3 = 46, a probe page, not the harness page.
- That `block-in-inline-015-print` web stays byte-identical (MED), and how far `scope-pseudo-element` /
  `display-flow-root-list-item-001` move.
- The DOM census renders each per-test document alone. The capture renders the combined section document, grouped by
  the same `groupByTest`. I assume per-canvas DOM equality; R4 at the probe checks it.
- Full suites were not run (single-writer rule): web runtime `tests/renderer/` only; harness separator / gallery /
  parity / span suites only. The orchestrator's sweep is the count of record.
- The other branch's Chromium behaviour on the harness page itself (not needed: R decided).

## Files in this directory

| file | role |
|---|---|
| `step0-probe.mjs` (+ `.out.txt`) | [W-L6] |
| `seam-1.patch`, `seam-1.liftonly.patch` | the landing seam; the lift-only probe input |
| `seam-verify.sh`, `mutate.py`, `rs-mutations.json`, `w1-mutations.json` | lock and mutation harness |
| `dom-census.census.test.tsx`, `vitest.census.config.mts`, `dom-census-diff.py`, `dom-census.*.json` / `*.out.txt` | DOM census |
| `rs-pairs.census.test.tsx` (+ out), `w1-joins.census.test.tsx` (+ out) | pair / join census |
| `carrier-crosscheck.py`, `mnm-crosscheck.py` | cross-checks |
| `scope-pseudo.replay.mjs` (+ out) | replay |
| `geometry-gate.L6.self-test.wave53-final.out.txt` | geometry self-test |
| `verify-*.log` | run records |
| `branchH-not-landed/` | the unused alternative |

TREES: /Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf (no export tree; no Gradle run by this lane)

STATUS: COMPLETE

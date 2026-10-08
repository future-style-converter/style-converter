# web-root-separator — wave-54 family brief (BACKLOG 5 web tail; untracked web-only fails, natives PASS)

Evidence run: `tools/titan/runs/wave53-final` (dev tip 7cce3b22). Scores are `node tools/titan/results/wave52-gate/cells.mjs
'<terms>' wave53-final`. Every PNG named below was opened (ref, web, iOS and Android side by side).

The measurements come from three scripts in this directory:
- **Atom edges:** a run scan of non-white pixels on one row, written as `web-root-separator.geometry.py` →
  `web-root-separator.geometry.out.txt`.
- **SSIM replays:** `web-root-separator.replay.mjs` → `web-root-separator.replay.out.txt`. It uses the scorer's own call,
  `ssim.js` `{ssim:'fast'}`. Its "shipped" column reproduces every gate score to 4 decimals, which is the check that the
  replay is the scorer.
- **Census:** `web-root-separator.census.mjs` → `web-root-separator.census.out.txt`, over all 1435 per-test IR documents.

Pixel identity of the box-sizing captures was checked by sha1 over the decoded RGB. Nothing was built, run or captured.

**Summary.** The web composed canvas renders the document's ROOT components as flush siblings. Two inline-level root
atoms that the source separated with whitespace (the extractor's `meta.role: "ws-after"`) therefore abut. The ref and both
natives put one collapsed-space advance between them: 5 px in the ref, the natives' fixed 4.5 px `atomGapPx`.

The web harness already replays that space one level DOWN (`renderChildSeparator`, wave-26 WWS). Nothing replays it
between ROOTS.

Results:
- 4 web cells fail on this alone, or on this plus a small residue.
- 14 more web cells pass with the atoms visibly packed (DEGENERATE: 13 box-sizing captures + semi-replaced-stretch-input;
  box-sizing-009 is a carrier whose sampled rows already match the ref).
- The natives pass the same tests (box-sizing-007 0.9845 / 0.9844).

## 1. Target cells (wave53-final)

| cell | web | ios | android |
|---|---|---|---|
| css-ui/box-sizing-007 | **f 0.9036** | P 0.9845 | P 0.9844 |
| css-ui/box-sizing-008 | **f 0.8943** | P 0.9726 | P 0.9712 |
| css-ui/box-sizing-022 | **f 0.9442** | f 0.8788 | f 0.8773 |
| css-position/position-absolute-semi-replaced-stretch-other | **f 0.941** | f 0.4623 | f 0.4396 |

**DEGENERATE web passes (picture-correctness targets; natives pass with the gap):**

| cells | web | natives (ios / android) |
|---|---|---|
| box-sizing-010, -011, -014…-019 (×8; web captures pixel-identical, sha1 857e5d3751) | P 0.9713 | 0.982 / 0.9811 |
| box-sizing-020, -021, -024, -025 (×4; sha1 e1705010ed) | P 0.9599 | 0.982 / 0.9811 |
| box-sizing-013 | P 0.9538 | f 0.945 / f 0.9436 |
| position-absolute-semi-replaced-stretch-input | P 0.9596 | f 0.4425 / f 0.4117 |
| box-sizing-009 | P 0.9811 | — |

## 2. The picture

All boxes are green on white. The edges below are the first ink column of the second atom on one row.

| test | row | ref | web | iOS / Android |
|---|---|---|---|---|
| box-sizing-007 (390×1344) | y150 | columns x26–125 and **x151**–250 | second column at **x146**–245, every one of the 10 rows (`… GEOMETRY WRONG (second atom edge x146 vs ref x151 on row 150)`) | x151 (OK) |
| box-sizing-008 | y150 | x151 | x146 | x151 |
| box-sizing-010 / -013 (2 atoms) | y140 / y150 | two runs x16–85 and **x91**–160 | ONE merged run x16–155: the second atom starts at x86, flush | 010: x91 (013 natives have their own height defect: no second edge on row 150) |
| box-sizing-020 / -022 | y110–200 | x16–145 and x151–280 | one run x16–275 | — |
| position-absolute-semi-replaced-stretch-other / -input | y60 | second column border at **x192** | **x187** | the natives fail these on a different, larger defect |

Every gap is 5 px: the Inter 16 px space advance the wave-26 WWS banner measured as 4.5 px.

**Residue that is NOT this defect.** Every box-sizing paragraph loses its `<strong>` runs (lossy `inline-run-merged`), and
the ref shows "filled green squares" in bold. This is the same on all three platforms, and it caps the web score below 1.

## 3. The wire

`wave53-final/sections/css-ui/per-test-ir/wpt__css-ui__box-sizing-007.json`:
- 22 components;
- the body-root, then the `<p>` (`role: ws-after`), then 20 ROOT `<img>` components (no `slot`), each
  `{"sourceTag":"img","role":"ws-after","attrs":{"src":…}}` with `BoxSizing BORDER_BOX`, `Width/Height auto` and margins
  10 (or −10 + padding 20).

The body-roots of all 27 census documents declare no `Display` and no `WhiteSpace` (executed scan), so no carrier hits
the predicate's flex/grid/preserving declines.

## 4. Mechanism (traced; file:line under 7cce3b22)

The path, in order:

1. **Extractor.** `tools/titan/extract-fixture.mjs` `WS_AFTER_ROLE = 'ws-after'` (:4883) is stamped as `cmp._role`
   (:4905) on the EARLIER of two siblings that source whitespace separated. The converter forwards it as `meta.role`.
2. **Web consumer, one level down only.**
   - `apps/web-harness/src/sdui/ComponentRenderer.tsx` `HARNESS_OPTIONS.renderChildSeparator` (:1099).
   - Its gates: `isWsAfterMarked` :260, `INLINE_LEVEL_SOURCE_TAGS` :273, `isInlineLevelSibling` :327, `WPT_COMPOSED_MODE`,
     not flex/grid, not white-space-preserving.
   - It returns `' '` between (prev, next) CHILDREN.
   - `runtimes/web/src/renderer/NodeRenderer.ts` (:366-376) invokes it inside a component's child walk.
3. **Roots.**
   - `apps/web-harness/src/ui/ComposedCaptureGallery.tsx` renders the root forest as `flowRoots.map` (:1070) /
     `canvasRoots.map` (:1079). Each root is `<RootErrorBoundary><ComponentRenderer node={root}/></RootErrorBoundary>`
     with NO separator between consecutive roots.
   - Two root `<img>`s therefore abut in the flow-root div. That is exactly the 5 px (one space advance, CSS 2.1 §16.6.1)
     the ref keeps.
4. **Natives (right).**
   - iOS `InlineBlockAtom.rootAtomGapPx = UAWidgetIntrinsics.atomGapPx` 4.5 (`InlineBlockAtom.swift:577`,
     `UAWidgetIntrinsics.swift:29`).
   - Compose `InlineBlockAtom.ROOT_ATOM_GAP_PX` (`InlineBlockAtom.kt:604`).
   - Both pack root atoms with the space advance, which is why the native columns land on x151 / x91.

## 5. The fix (smallest)

- **New `apps/web-harness/src/ui/ComposedRootSeparator.ts`** (≤ 120 lines): `interleaveRootSeparators(roots, render,
  container)`. It returns the rendered roots with `' '` between each adjacent pair the WWS predicate accepts.
  - The container is the canvas wrapper: `display` = `flow-root`, or the table-body plan's `table` → DECLINE (CSS 2.1
    §17.2.1 drops whitespace between table-internal boxes). `white-space` = the body-root's declared value.
  - The body-root itself (first root, never `ws-after`) pairs with nothing.
- **Seam hunk `seam-1.patch`** (`ComponentRenderer.tsx`): lift the WWS branch of `renderChildSeparator` (:1105-1158) into
  an exported pure `wsAfterSeparator(prev, next, container: {display?, whiteSpace?})`, which `HARNESS_OPTIONS` calls.
  - The child-level DOM stays byte-identical. The pin is the existing separator tests plus a new call-equivalence test.
- **Call sites:** `ComposedCaptureGallery.tsx` (1267 lines, a size-rule exception per BACKLOG 0(d)), the two `.map` sites
  only.
- **Spec:**
  - CSS 2.1 §16.6.1 and css-text-3 §4.1.1: collapsible whitespace between inline-level boxes renders as one space.
  - §9.2.2.1: none between blocks. The predicate's inline-level gate already encodes this.

## 6. Corpus radius (`web-root-separator.census.out.txt`)

**27 documents carry ≥ 1 adjacent ROOT pair (`ws-after` on the earlier sibling, both inline-level).** Web cells: 20 P
(at risk, expected to rise or stay), 6 f (candidates), 1 unscored (`overlay-button-appearance`).

**f candidates:**

| document | pairs | web | role |
|---|---|---|---|
| box-sizing-007 | 19 | f 0.9036 | target |
| box-sizing-008 | 5 | f 0.8943 | target |
| box-sizing-022 | 1 | f 0.9442 | target |
| position-absolute-semi-replaced-stretch-other | 6 | f 0.941 | target |
| css-cascade/scope-pseudo-element | 2 | f 0.9353 | mover: ref box lefts x16/123/229 vs web x16/118/220; its B/Foo wrap defect remains |
| css-display/display-flow-root-list-item-001 | 1 | f 0.8003 | mover |

**P at risk:**
- box-sizing-009 … -025 (except 012 / 023, which have no pair).
- position-absolute-semi-replaced-stretch-input.
- `CSS2/abspos/static-inside-inline-block` P 0.9803.
- `css-break/block-in-inline-015-print` P 1.
- `css-ui/appearance-auto-input-non-widget-001` P 0.9873.
- `css-values/attr-style-sharing-1` P 0.9939.
- `css-writing-modes/baseline-with-orthogonal-flow-001` P 0.9854.

**Carrier set:**
- **web only:** the 26 scored stems above.
- **ios / android: none.** No native file is touched. The natives already pack root atoms.

**Shared stems.**
- `block-in-inline-015-print` is also an ANDROID carrier of `ua-heading-face`: a different capture, so no conflict.
- `position-absolute-semi-replaced-stretch-other` appears in `web-out-of-flow-hyphen-box.census.out.txt` as an
  `inert=false` abspos member, outside that lane's reach. plan-build must still assign the web stem to ONE lane.

## 7. Ownership (disjoint) and seams

**Owned:**
- New `apps/web-harness/src/ui/ComposedRootSeparator.ts` and `apps/web-harness/src/ui/ComposedRootSeparator.test.ts`
  (vitest, verbatim box-sizing-007 IR: 19 separators, none after the body-root or the `<p>`).
- `apps/web-harness/src/ui/ComposedCaptureGallery.tsx`: the two call sites.
- `tools/titan/results/wave54-<lane>/`.

**Seam:** one hunk in `apps/web-harness/src/sdui/ComponentRenderer.tsx` (pure lift plus export).

**Not touched:**
- `runtimes/web/**`;
- every native file;
- `extract-fixture.mjs`;
- `LabelChrome.tsx` (the label-chrome family's ground);
- the web hyphenation path (web-out-of-flow-hyphen-box's ground).

## 8. Geometry probe (`web-root-separator.geometry.py`, executed on wave53-final)

**Rule:** on the probe row, the first inked run starting at or after the split column begins within ±1 px of the ref's.

**Today:**
- the ref rows print OK (self-check, exit 0);
- every web row prints `GEOMETRY WRONG (second atom edge x146|xNone|x187 vs ref x151|x91|x192 …)`;
- the iOS / Android rows are controls (box-sizing-007/008/010/022 natives already OK).

**After the lane:** every web row "→ GEOMETRY OK". Native rows byte-identical.

**Pins:**
- the vitest above;
- mutation: drop the `isWsAfterMarked` gate → a flush-authored pair test gets a separator → red;
- mutation: drop the table-body decline → the table-body test red.

**Probe sections:** css-ui, css-position (+ css-cascade, css-display, CSS2, css-break, css-values, css-writing-modes for
the at-risk passes).

## 9. Predictions (wave53-final → closing gate; web only)

Each prediction is the replayed SSIM of the web capture with the moved column shifted right by the measured 5 px. That is
an upper bound on the geometry half; no colour is added, so the colour and coverage vetoes are unchanged.

**Target cells:**

| cell | from → to | confidence | floor |
|---|---|---|---|
| box-sizing-007 | f 0.9036 → **P 0.985** | HIGH | 0.975 |
| box-sizing-008 | f 0.8943 → **P 0.974** | HIGH | 0.965 |
| box-sizing-022 | f 0.9442 → **P 0.970** | MED-HIGH | 0.96 |
| position-absolute-semi-replaced-stretch-other | f 0.941 → **P 0.967** | MED | — |

**P → P (DEGENERATE → faithful):**

| cells | from → to |
|---|---|
| box-sizing-010/011/014…019 | 0.9713 → ≈ 0.982 |
| box-sizing-020/021/024/025 | 0.9599 → ≈ 0.982 |
| box-sizing-013 | 0.9538 → ≈ 0.970 |
| semi-replaced-stretch-input | 0.9596 → ≈ 0.966 |

**Movers (f):** scope-pseudo-element f 0.9353 (up, LOW) and display-flow-root-list-item-001 f 0.8003 (LOW).

**Must not move or fall:**
- block-in-inline-015-print web P 1 (whitespace between inline divs that wrap blocks collapses: expect byte-identical);
- static-inside-inline-block P 0.9803;
- appearance-auto-input-non-widget-001 P 0.9873;
- attr-style-sharing-1 P 0.9939;
- baseline-with-orthogonal-flow-001 P 0.9854;
- box-sizing-009 P 0.9811;
- every native cell;
- the 327-pair fixture net (the separator is `WPT_COMPOSED_MODE`-only).

**Expected:** web +2 HIGH, +1 MED-HIGH, +1 MED; 14 picture-correct passes; lost 0.

## 10. Risks

1. **An at-risk pass falls (LOW).**
   - The separator replays a space the SOURCE had, into the same Chromium that rendered the ref, so a fall means the
     composed DOM diverges from the ref structurally around that pair.
   - The stop rule: any web P cell dropping > 0.002 is looked at against the ref before the ship. The pre-registered
     lost list stays empty.
2. **Abspos atoms (MED).** For semi-replaced-stretch-*, the space sits between abspos inline-blocks, so it moves the next
   box's static position. The ref does the same; the replay was +0.026 / +0.007.
3. **Table-body canvas.** wave-53 L3's `canvasTableBody` wrapper must decline: whitespace between table-internal boxes is
   dropped. The census has 0 such carriers. The pin guards it.
4. **File size.** `ComposedCaptureGallery.tsx` is 1267 lines (0(d)). New logic goes in the new file; the gallery gets two
   call sites.
5. **Ring-fence:** none of the 27 documents is `backdrop-filter-basic-blur`.

## 11. Recommendation

**GO (one lane, effort S; web harness only, one seam hunk).**

**GO-SMALL:** the same change with the semi-replaced pair excluded from the claim. It cannot be excluded from the code,
because it is the same predicate; it is only reported as a mover.

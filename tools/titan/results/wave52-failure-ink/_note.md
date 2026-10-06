# L3 · failure-ink — lane note (wave 52; first pass 2026-09-25, resume pass 2026-10-05, fix pass 2026-10-05 18:20–19:10)

Brief: `tools/titan/results/wave52-plan/failure-ink.md` §3 T1–T3 / §4 F1–F3; plan: `PLAN.md` §2 "### L3 ·". Evidence run `wave51-fix`.
Effort spent: M (F1, F2, F3 all built; F4–F6 not touched, as budgeted). No device was booted; every check below is JVM / Catalyst / node.
**Resume pass (2026-10-05)** — the first pass was killed by a usage limit before its seam-2 result and before seam-1 could run its pins (other
lanes' test files did not compile). Everything from the first pass was re-read and kept; the resume pass (a) re-ran every pin on the current
shared tree, (b) verified seam-1 WITH its pins (now possible) and re-verified seam-2, (c) found that the brief's F1 census recipe read the
container's OWN writing mode while both real seams read the USED (inherited) one, re-ran the census both ways and added a pin for the one
passing cell the old guard used to protect, and (d) corrected carrier counts in comments (8 → 9 baked carrier docs / 12 containers).
**Fix pass (2026-10-05, after `skeptic.md`)** — the ONE must-fix is fixed: `hunk-for-orchestrator-1.patch` is re-cut WITH its pins and is now
verified (§11). Executing it found a SECOND red pin the skeptic's run did not include (`Wave12BoxSizingBasisTests` raster row) — both rows ride
in the hunk now, plus a new verbatim -017 pin file, H1/H2/H3 executed. Should-fixes also done: seam-1/seam-2 re-cut with source "wiring" pins
(should-fix 2, negative controls executed under the locks), the T3 self-certification caveat (should-fix 3, §7), the used-mode carrier count in
both seam comments (nit 4), the F3 outer-positioned-CB breadcrumb + pin (nit 8), the raster pin's "VERBATIM" wording (nit 6), and the false
"can never disagree" comment in `MulticolSpannerFlow.kt`. Census gained `f1swiftUsed` (the hunk's call-site gates): 0 movers.

## 1. What changed, and why

**F1 — the baked-layout guard reads the post-load extractor's real signature.** Both natives' vertical seams (Compose block-flow seam
`ComponentRenderer.kt` `anyBakedChild`, its multicol twin `MulticolSpannerFlow.ChildSpec.bakedPhysicalSize` → `VerticalMulticolMeasure`; SwiftUI
`verticalBlockFlowZ2()`) declined any container whose in-flow child declared BOTH `Width` and `Height` — the wave-47 heuristic. An authored
`.square { width:50px; height:50px }` trips it, so on `css-writing-modes/flexbox_align-items-stretch-writing-modes` the two vertical-writing-mode
flex items kept the frozen Column/VStack, stacked their squares vertically, came out 50 wide, and the test's 250×100 red `z-index:-1` probe
showed through the right 100 px (wave51-fix ios f 0.999 cF · android P 0.998, same picture). The extractor (`tools/titan/post-load-extract.mjs`
POST_LOAD_PROPERTIES :160-181) writes `width`+`height`+`box-sizing`+margins+paddings+border-{width,style,color} on EVERY box it bakes, so the
new shared predicate is `bakedPhysicalBox = Width ∧ Height ∧ BoxSizing ∧ (PaddingTop ∨ BorderTopStyle)`:
- new `runtimes/compose/src/main/java/com/styleconverter/runtime/core/renderer/BakedLayoutSignature.kt` (88 lines) + twin
  `runtimes/swiftui/Sources/StyleConverterRuntime/Renderer/BakedLayoutSignature.swift` (82 lines);
- `MulticolSpannerFlow.kt` `specsFor` reads it for `ChildSpec.bakedPhysicalSize` (doc updated); `VerticalMulticolMeasure.kt` (owned) needed no change —
  it consumes the flag;
- seam patches `seam-1.patch` (`ComponentRenderer.kt:2774-2785` — the guard expression only) and `seam-2.patch` (`ComponentRenderer.swift:4354-4361`);
- `hunk-for-orchestrator-1.patch` for the FOURTH site the brief did not list: `runtimes/swiftui/…/StyleEngine/columns/VerticalMulticolPlan.swift:52-60`
  (the Swift twin of `ChildSpec.bakedPhysicalSize`'s consumer). The file has no owner in PLAN.md §2, so it is a hunk, never an edit. Its single
  corpus carrier (`anchor-position-multicol-017`, baked, 9-longhand signature) reads true under both predicates → zero movement.
  **Fix pass**: the hunk now carries its pins (all four paths unowned): the source change (+ doc; `hasBakedWidth` renamed `hasPhysicalWidth` for its
  remaining block-extent job), `VerticalFragmentGeometryTests.testVerticalPlanDeclinesBakedLayout`'s row on the real signature (+BoxSizing,
  +PaddingTop 0), `Wave12BoxSizingBasisTests.testInheritedVerticalModeRendersUnfragmented`'s TallChild on the same two longhands (zero geometric
  effect — keeps that wave-12 pin on the bail path it was written for), and NEW `VerticalMulticolBakeGateTests.swift` (146 lines, 3 tests).

**F2 — `display: contents` unboxes when floated.** css-display-3 §2.7's blockification sentence ends "…has no effect on display types that
generate no box at all (none, contents)"; the wave-18 Float clause (`ContentsUnboxing.kt:71-74`, `.swift:65-66`) misread it and kept the box, whose
`background:red` painted a full-width 358×20 bar behind "PASS" on `css-display/display-contents-float-001` (ios f 0.9417 · android f 0.941; web P 1.0000).
The clause is deleted on both natives; the position clause (and its containing-block-threading reason) stays.

**F3 — the spanner containing-block chain, on iOS.** css-multicol-1 §6.1 lays a `column-span:all` box out as a block of the multicol container, so
CSS 2.1 §10.1's nearest-positioned-ancestor search for its abspos descendants skips the `position:relative` column item. Compose has had
`MulticolSpannerContainingBlock.kt` since wave 49 (android P 0.999); iOS had no twin (ios f 0.9553 cF, 20 000 red px at (16,16)).
- new `runtimes/swiftui/…/StyleEngine/columns/MulticolSpannerContainingBlock.swift` (180 lines): the Kotlin functions byte-parallel
  (`isSpanner`, `isMulticolContainer`, `childPositionedAncestor`, `childPositionedAtMulticol`, `transformChainRestartUnimplemented`), plus
  `establishesContainingBlock` (twin of `CanvasRootHoist.establishesContainingBlock`) and the ONE consumer predicate
  `hoistsToInitialContainingBlock(child, underSpanner:, hasPositionedAncestor:, hasTransformedAncestor:)`;
- `FixedHoist.swift` `strippingFixedDescendants` threads `hasPositionedAncestor` / `positionedAtMulticol` / `underSpanner` (all defaulted to the root
  context — every pre-wave-52 call site byte-identical) and hoists an INSET ABSOLUTE descendant of a spanner whose RESTARTED chain has no
  positioned box to the canvas-root `FixedHoistOverlay` — the ICB (§10.1 rule 4), exactly the mount a fixed descendant gets. A no-inset abspos stays
  at its static position (the `rendersInFlowAsStaticPosition` exemption); a positioned box OUTSIDE the multicol keeps the child in the tree
  (rendered by its direct parent's overlay — the pre-wave-52 approximation, no corpus witness).

## 2. Design deviations (honest list)

1. **No iOS overlay-partition seam patch.** PLAN §3 reserved `ComponentRenderer.swift :765 / ~:2050` for L3's `positionedAtMulticol`. The
   partition never needed it: iOS partitions each renderer's OWN direct children, so the only whole-tree chain walk is `FixedHoist.split`'s
   descendant strip — the Compose `CanvasRootHoist.collectCanvasHoisted` twin — and the stripped probes never reach the spanner's renderer.
   Consequence for **L7**: there is no L3-applied tree for the `positionedChildren` region; L7 cuts its `:2098-2112` patch against HEAD (seam-2 touches
   `:4354-4361` only, far from that function). `FixedHoist.swift` IS changed (owned by L3, §5) — L7's `AbsposStaticPositionTests` do not consume it.
2. **Two new files not in the §2 own: list**: `Renderer/BakedLayoutSignature.swift` (the "+ Swift twin" §2's Fix text names) and
   `Tests/…/MulticolSpannerHoistRasterTests.swift` (the S4 raster pin, split from `MulticolSpannerContainingBlockTests.swift` so both stay ≤ 200 lines).
3. **The ICB hoist is spanner-scoped, not the BACKLOG 6(e) ICB clause.** `hoistsToInitialContainingBlock` requires the §6.1 restart to have fired
   on the path (`underSpanner`); `FixedHoistTests.testEXPECTED_DIVERGENCE_…` and `testSplitKeepsNestedAbsoluteChildren` stay green and unchanged
   (14/14 with M6 applied and without) — 6(e) remains unstaffed, as §5 says.
4. Lane tools in this dir (they edit no project file): `gradle-xml-redirect.init.gradle.kts` — twelve lanes share
   `runtimes/compose/build/test-results/`, and Gradle wipes it per run, so a Test task's XML is redirected to `_jvm-xml/<run>/` at task-graph-ready
   time; `seam-verify.sh` — the lock dance of lane rule (2) as one script whose lock RELEASE is a trap armed only after its own `mkdir` succeeded
   (written after the incident in §8).
5. `VerticalMulticolMeasure.kt` (owned) is unchanged — the flag it reads moved to the shared predicate one level up.

## 3. Census (`census.mjs` → `census.json`, 1435 docs; re-derivable: `node tools/titan/results/wave52-failure-ink/census.mjs`)

- **F1** carriers (brief recipe = container's OWN WritingMode, per CONTAINER): 20 containers in **14 docs** (the brief's 13 + `css-gaps/flex/flex-gap-decorations-006`,
  a FLEX container). Old guard true 20/20; new guard true 12/20. **Baked stay guarded 12/12 containers in 9 docs (slip-through 0)**:
  anchor-position-multicol-007/008/013/014/015/016/017, -colspan-003, input-range-zero-inline-size (×4 containers). **Authored unguarded 8/8 containers in 5 docs
  (still guarded 0)**: the target (×2), abs-pos-border-offset-003 (×3), flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse (FLEX — the block seam never runs),
  flex-gap-decorations-006 (FLEX).
- **F1-used (resume pass, `census.json → f1Used`)**: both real seams read the USED writing mode (Compose `extractWritingModeConfig` over the merged
  inherited view; iOS `WritingModeExtractor.extract(from: resolvedProperties)`), so the pass walks `slot.parent` for the nearest WritingMode and mirrors the
  seams' gates exactly (4-keyword inline-level set, own-WritingMode orthogonal child, text-only leaves, no own text). **39 old-guarded containers**
  (19 own-mode, 20 inherited-only); new guard true **23** (all baked — every inherited baked container stays guarded); **7 movers** in 3 docs (block arm, seam
  now engages): the target ×2, abs-pos-border-offset-003 ×3 (both known) and **one NEW: `css-tables/baseline-vertical` ×2 `<td>` cells** (inherited vertical
  mode, one authored 50×100 child each; both natives f today — ios 0.6209 · android 0.5727 — a mover, direction unknown, cannot be LOST; added to
  `watchlist-additions.txt`). **7 `<tr>` containers** (td-different-subpixel-padding-in-same-row-vertical-rl ×1, collapsed-border-{sideways-rl-rtl,vertical-lr-rtl,
  vertical-rtl}-overflow ×2 each) lose the old guard but are **consumed by the table path on both natives** — iOS `tableTrackPlan` gives role `.row` an
  `.inlineRow` track BEFORE the Z2 branch (`ComponentRenderer.swift:1638`); Compose folds the undeclared `<table>` to `DisplayType.TABLE` when
  `TableBoxTree.consumableRowList` holds (`ComponentRenderer.kt:1658-1664`) and `RenderTableContent` renders rows as `TableRow`, never `RenderComponent`
  (`:3016-3058`). The census re-implements that fold (`tableFolds`); the only PASSING one of the seven (td-different-subpixel, ios P 0.9638 · android P 0.9609 —
  the plan's at-risk cell) is now pinned on both natives (§4, P1-used). `float-vlr-014` (plan at-risk): its vertical container has an INLINE_BLOCK child → the
  seam's inline-level gate declines it either way — not a carrier.
- Signature separation corpus-wide over **5353 Width+Height components**: in postLoadExtracted docs 1478 true / 113 false (the 113 are un-baked
  components of baked docs — authored parts); in authored docs **39 true / 3723 false**. The 39 (17 docs, listed in `census.json → signature.authoredNewTrue`:
  anchor-center-overflow-005 ×8, counter-suffix ×6, bidi-plaintext-br-001 ×6, backdrop-filter-clip-rect-2 ×3, …) are authored boxes declaring
  `box-sizing` + a padding/border-style; NONE sits under a vertical-writing-mode block container (own-mode: authoredStillGuarded 0; used-mode: every new-guard-true
  container is baked), so they are the residual false-positive potential, not a live effect. Every authored Width+Height child of every old-guarded container
  (own or used mode) lacks BoxSizing — the table cells carry PaddingTop (+BorderTopStyle on collapsed-border), so BoxSizing is the load-bearing conjunct.
- **F2**: 106 `Display=CONTENTS` components; with a non-none `Float`: **exactly 1** (the target).
- **F3**: 28 `ColumnSpan=ALL` components; with an ABSOLUTE/FIXED descendant: **exactly 1** (the target, 2 descendants).
- **F1'** (Swift `VerticalMulticolPlan` sole-child gate): 1 carrier (`anchor-position-multicol-017`, baked, new=true) → the hunk moves nothing.
- **F1'-used (fix pass, `census.json → f1swiftUsed`)**: the brief-style F1' read the container's OWN WritingMode; the real call site
  (`ComponentRenderer.swift` fragmentPlan, inside the per-child loop) reads the USED mode, counts FLOW siblings under capture
  (`MulticolSpannerFlow.flowCount`) and evaluates the gate for every non-spanner child. Mirrored exactly (multicol = numeric ColumnCount or px
  ColumnWidth): **1 old-guarded child in 1435 docs** (`anchor-position-multicol-017__1__0-744`, flow, baked), new guard true → **0 movers** — the
  hunk is parity, not a flip, under the call site's own gates too. Every other census number is byte-identical to the resume pass (diffed).

## 4. Pins (verbatim wave51-fix IR) and executed mutations

| pin | file | rows | mutation (executed, restored byte-exact, sha-verified) | result |
|---|---|---|---|---|
| P1 | `VerticalBlockFlowSeamGuardTest.kt` (new, 6 tests) | 012 `.square` (Height 50, Width 50) → false; abs-pos-border-offset-003 `__0__0__0-321` (10×20) → false; 013 `__1__0-707` (29 longhands) → true; BoxSizing-alone / band-alone → false, basis+band → true; `specsFor(...).bakedPhysicalSize` authored false / baked true | M1: `bakedPhysicalBox` → `WIDTH in types && HEIGHT in types` | 4/5 FAIL (both authored rows, BoxSizing row, ChildSpec row) → restored sha `0a66c181…1133`, 5/5 green |
| P1 (Swift) | `VerticalBlockFlowSeamGuardTests.swift` (new, 5 tests) | same rows | M5: `types.contains(width) && types.contains(height)` | 4/4 FAIL → restored sha `fdbb04a7…48fe`, 4/4 green |
| P1-used (resume) | both files above, +1 test each | VERBATIM td-different-subpixel `__0__0__{0..4}-304..308` (Width 20, Height 20, padding 2 / 1…2): (1) every cell `bakedPhysicalBox` false; (2) DEPENDENCY: Compose `uaRoleOf("table") == TABLE` ∧ `consumableRowList([tr])`; iOS `arrangement(roleOf([], sourceTag:"tr")) == .inlineRow`; control: tag-less row not consumable / `.none` | M7: `BOX_SIZING`/`boxSizing` conjunct replaced by `true` (Compose `_compose-mutation-M7.log` + `_jvm-xml/mutation-M7/`; Swift `_swift-mutation-M7.log`, `_swift-mutation-runC.log`) | Compose 2/6 FAIL (the new row + the band-alone row) → restored sha `0a66c181…1133`; Swift 6 failures in 2 tests (5 cells + band row) → restored sha `fdbb04a7…48fe`. (2) is not mutation-proven by an edit — its production code is `TableBoxTree.{kt,swift}`, L8's files this wave, never touched; its control row runs every time and proves the assertion can fail |
| P2 | `ContentsUnboxingTest.kt` (11 tests: float row flipped to TRUE + new verbatim `__1-218` row: unboxable, self-strip drops Display/Float/BackgroundColor, text "PASS", `Float NONE` unboxable) | | M2: Float clause re-added | 2/11 FAIL (the two rows) → restored sha `3b3578f9…8968`, 11/11 green |
| P2 (Swift) | `ContentsUnboxingTests.swift` (11 tests; lines 34-48 edited in place with the SAME line count, new test appended after line 192 — `:175-183 testAllResetDropsEveryOtherDeclaration` untouched for L11) | | M4: Float clause re-added | 3 failures in 2 tests → restored sha `f581b573…5a7c`, 11/11 green |
| P3 | `MulticolSpannerContainingBlockTests.swift` (new, 14 tests = Kotlin S1–S3 byte-for-byte + S4 over `FixedHoist.split`: both probes hoist, spanner children nil; no-spanner / no-container / outer-relpos / no-inset negative controls) | | M3: spanner branch of `childPositionedAncestor` dropped | S2 row 1 + S4 row 1 FAIL → restored sha `83289165…4476`, 14/14 green |
| P3 hoist | same S4 + `MulticolSpannerHoistRasterTests.swift` (new, 3 tests: TL green at (66,66)/(20,20)/(110,110); BR green at ICB (w−16−dx, h−16−dy); red px count == 0 over the whole raster) | | M6: `hoistsToInitialContainingBlock` → `return false` | S4 row 1 (2 asserts) + all 3 raster tests (7 asserts) FAIL; `FixedHoistTests` 14/14 stay green (rule is spanner-scoped) → restored, 3/3 + 14/14 green |
| P1′ hunk (fix pass) | NEW `VerticalMulticolBakeGateTests.swift` (in the hunk): VERBATIM -017 sole child (30 longhands) on its container geometry (count 5, gap 0, 100×100, rl) → nil; authored `{Width 450, Height 20}` → plan, translations [-350,-250,-150,-50,50], slot 100×20; authored + `BoxSizing BORDER_BOX` (no band) → 5 fragments. Rewritten rows: `VerticalFragmentGeometryTests:145-159` (+BoxSizing +PaddingTop), `Wave12BoxSizingBasisTests` TallChild (same) | | H1: gate → wave-47 heuristic (= HEAD blob `f95f7d1d…`); H2: gate → `if false`; H3: Wave12 file at HEAD with the source hunk | H1: the 2 authored rows FAIL, everything else green; H2: the -017 row + `testVerticalPlanDeclinesBakedLayout` + the Wave12 raster row (2 asserts) FAIL; H3: Wave12 `:394`/`:397` FAIL (why its fixture edit ships in the hunk). Restored sha `b8f37ca7…` equal each time (`_swift-hunk1-mutations.log`) |
| P-wire (fix pass) | NEW `VerticalBlockFlowSeamWiringTest.kt` (in seam-1, 3 tests) / `VerticalBlockFlowSeamWiringTests.swift` (in seam-2, 2 tests): source pins — the guard calls `BakedLayoutSignature.bakedPhysicalBox(child.properties)`, the Width∧Height heuristic is gone, (Compose) the engage condition still reads `!anyBakedChild` | | NC1: seam file at HEAD bytes, pin kept; NC2 (Compose): seam applied, `!anyBakedChild &&` line deleted | Compose NC1 2/3 FAIL, NC2 1/3 FAIL (`_seam1-verify.log`); Swift NC1 2/2 FAIL (shared tree `_seam2-verify.log` + private copy `_seam2-private-verify.log`) |
| P3-log (fix pass) | `MulticolSpannerContainingBlockTests.testS4PositionedAncestorOutsideTheMulticolStillVetoes` + breadcrumb assert (`multicol-spanner-abspos-outer-cb`) | | N8: the new `PropertyTracker.logOnce` call deleted | that row FAILS (`:177`), raster 3/3 green; restored sha `927b1475…` equal (`_swift-fixpass-F3log.log`) |

M7 ran before the resume pass's comment-only edit of the two `BakedLayoutSignature` headers (§0 (d)); the predicate lines are byte-identical
(current shas `3aa0f815…` .kt / `a0386a70…` .swift), and the post-edit green runs below cover the final files.

Green runs, first pass (2026-09-25): Compose run3 `_compose-focused-run3.log` + `_jvm-xml/run3/` 78/0; Swift run1 `_swift-focused-run1.log` 72/0.
**Green runs, resume pass (2026-10-05, current shared tree)**: Compose run4 `_compose-focused-run4.log` + `_jvm-xml/run4/` — MulticolSpannerContainingBlockTest 14,
MulticolSpannerFlowTest 23, ContentsUnboxingTest 11, VerticalBlockFlowSeamGuardTest 6 — **54/0**; Swift run2 `_swift-focused-run2.log` — ContentsUnboxingTests 11,
FixedHoistTests 14, MulticolSpannerContainingBlockTests 14, MulticolSpannerFlowTests 26, MulticolSpannerHoistRasterTests 3, VerticalBlockFlowSeamGuardTests 5 — **73/0**.
(The first resume attempt at 17:03 could not compile the Compose test source set: `VerticalRunIntrinsicsTest.kt:98-220` "No value passed for parameter
'onDecline'" — L8's in-flight `VerticalRunIntrinsics.kt`; L8's 17:10:45 edit cleared it on the next retry, so it is not a standing block.)

## 5. Seam patches (never left applied; per-file lock taken; restored via `git show HEAD:` and sha-compared)

- `seam-1.patch` — `ComponentRenderer.kt` `anyBakedChild` → `BakedLayoutSignature.bakedPhysicalBox(child.properties)` (+ comment). `git apply --check` clean on HEAD.
  **VERIFIED WITH PINS (resume pass, `_seam1-verify.log` 17:15:40 via `seam-verify.sh`)**: lock taken, patch applied, `:runtime:compileDebugKotlin` re-ran,
  ContentsUnboxingTest 11 · VerticalBlockFlowSeamGuardTest 6 · MulticolSpannerContainingBlockTest 14 · MulticolSpannerFlowTest 23 · CanvasRootHoistTest 25 =
  **79/0** (`_compose-seam1-run2.log`, `_jvm-xml/seam1/`); restored, sha `c4369165…e652` before = after; lock released 17:15:43. (17:13:26 was the same run on the
  previous patch text; the patch then changed one COMMENT line — the carrier count — and was re-verified at 17:15:40.)
- `seam-2.patch` — `ComponentRenderer.swift` `verticalBlockFlowZ2()` guard → `BakedLayoutSignature.bakedPhysicalBox(child.properties)` (+ comment).
  `git apply --check` clean on HEAD (checked against the HEAD blob while another lane held the file). First pass: verified 2026-09-25 20:16 — 32/0, sha
  `d2afc70d…ee63` equal. Resume-pass re-verification (corrected comment line + the new P1-used test): see §10.
- No patch for the iOS overlay partition (§2 deviation 1). No other seam touched.
- **Fix pass re-cut (supersedes the shas above)**: `seam-1.patch` sha `3e3c446e…`, `seam-2.patch` sha `43809f52…` — each = the same guard change +
  the census comment on the USED mode (23 baked containers / 9 docs; was the own-mode 12) + a NEW source-pin test file (§4 P-wire), with a header.
  Both `git apply --cached --check` clean on a HEAD index. Verified via `seam-verify-pinned.sh` (lock dance as `seam-verify.sh`, plus the new file
  removed on restore and the negative controls run while the lock is held; restore only if THIS run applied): seam-1 18:53:07–18:53:21, 26 Compose
  suites **226/0**, sha `c4369165…` before = after, clean, lock released; seam-2 18:59:25–19:02:06 (shared tree, private `-derivedDataPath`
  prewarmed OUTSIDE the lock), 11 suites **117/0**, sha `d2afc70d…` before = after, clean, lock released. seam-2 also verified in the private
  package copy with the hunk applied: 17 suites **168/0** (`_seam2-private-verify.log`).
  Interop (19:12): every other lane's `seam-*.patch` was `git apply --cached --check`ed on a HEAD index with L3's FIRST-pass seams applied and
  again with the RE-CUT seams: identical outcome for all 25 (19 apply, 6 do not at either). The non-appliers checked further: the ones probed
  on a PURE HEAD index (counters-and-lists seam-2, all-reset seam-3, extractor-cascade seam-2/seam-6) fail there too — they are cut on earlier
  patches (counters seam-2's header: "HEAD + seam-4, apply seam-4 FIRST"); none of their failing hunks is in L3's Z2/anyBakedChild region. The
  re-cut moves no other lane's landing.

## 6. PNG replay (`png-replay.mjs` → `png-replay.json`; pngjs, channel delta > 24 = mismatch)

| cell | ref | wave51-fix ios | wave51-fix android | web (= predicted post-fix native picture) | residual web over ref |
|---|---|---|---|---|---|
| T2 display-contents-float-001 | red 0 | red 6 941 px bbox (16,88)-(373,107); 8 167 px ≠ ref | red 6 958 px, same bar; 8 396 px ≠ ref | red 0 | **0 px** |
| T1 flexbox_align-items-stretch-writing-modes | red 0, green 25 000 | red 10 000 px bbox (166,88)-(265,187), green 15 000; 11 179 px ≠ ref | same shape; 11 476 px ≠ ref | red 0, green 25 000 | **0 px** |
| T3 abspos-containing-block-outside-spanner | red 0, green 20 000 | red 20 000 px bbox (16,16)-(373,583), green 10 000; 30 481 px ≠ ref | red 0, green 20 000; 609 px ≠ ref (text AA) | red 0, green 20 000 | **0 px** |

The raster pin (§4, P3 hoist) is the iOS-specific replay for T3: the composed-canvas replica paints both green squares at the ICB corners and 0 red px.
The plan's "PNG-check" for td-different-subpixel / float-vlr-014 is replaced by code-path proof: neither reaches the seam (§3 F1-used, pinned in P1-used).

## 7. Predicted flips (against wave51-fix)

- `css-display/display-contents-float-001` ios f 0.9417 → **P**, android f 0.941 → **P** — **HIGH**. The red bar is the only divergence (web residual 0 px);
  the root self-strip leaves a text-only pass-through.
- `css-multicol/abspos-containing-block-outside-spanner` ios f 0.9553 → **P** — **MED-HIGH** (raised from MED: the brief's falsifier — `bottom:0;right:0`
  resolving against the padded canvas — is refuted device-free by the raster pin, which asserts green at (w−16−5, h−16−5) and (w−16−95, h−16−95) on the
  390×600 replica; what remains unverified is the real harness canvas vs the replica). android stays P 0.999.
  **Caveat (skeptic should-fix 3, accepted): this cell's gate verdict is NOT self-certifying.** The scorer's own `diffWebVsRef`/`computeWptPass`
  pass a FALSIFIED picture (bottom-right green 16 px off, ≈2 900 red px visible) at P 0.9712, so a `P` here must be PNG-checked for ZERO red
  (the raster pin's own assertion) before it is written up as a fix — a P with red ink showing is a degenerate pass, not the flip.
- `css-writing-modes/flexbox_align-items-stretch-writing-modes` ios f 0.999 → **P** — **MED** (unchanged): the guard now lets the seam re-stack the
  squares horizontally; the flip still needs iOS `CSSFlexLayout` to stretch the re-stacked 100×50 auto item to the 100-px line. android P 0.998 stays P
  (shape changes; `FlexCrossStretch.kt:60-63` stands down under an auto container cross size — not touched).
- Movers, direction unknown (all f today, cannot be LOST): `css-writing-modes/abs-pos-border-offset-003` ios f 0.9342 / android f 0.9262 (VERTICAL_LR block
  container, single in-flow 10×20 child ⇒ same slot as the Column); **`css-tables/baseline-vertical` ios f 0.6209 / android f 0.5727** (resume-pass find:
  two `<td>` cells with one authored 50×100 child each now take the seam; far below any pass threshold).
- At risk, must NOT move: `css-anchor-position/anchor-position-multicol-007/008/013/014/015/016/017`, `-colspan-003`, `css-writing-modes/forms/input-range-zero-inline-size`
  (baked: stay guarded 12/12 containers own-mode and every inherited baked container used-mode — pinned by the 013 row);
  `css-tables/height-distribution/td-different-subpixel-padding-in-same-row-vertical-rl` ios P 0.9638 / android P 0.9609 (unguarded now, but consumed by the table
  path on both natives — pinned, P1-used); `css-tables/collapsed-border-*-overflow` ×3 (same table-path protection; f today). Expected-not-to-move:
  `css-gaps/flex/flex-gap-decorations-006` and `flex-align-baseline-column-vert-{lr,rl}-rtl-wrap-reverse` (FLEX containers — the block seam never runs);
  `css-writing-modes/float-vlr-014` (INLINE_BLOCK child gate); `css-display/display-contents-oof-001/002` (the abspos clause is kept).

## 8. Not verified (honest)

- The three flips themselves (no device run — the gate decides). T1's iOS stretch precondition; T3's real harness canvas vs the 390×600 replica.
- `baseline-vertical`'s and `abs-pos-border-offset-003`'s direction (not PNG-replayed; both far from a pass).
- The P1-used DEPENDENCY half rests on `TableBoxTree.{kt,swift}` / `TableSeparatedTracks.swift` (L8's / unowned) — pinned with a control row, not mutated.
- The 39 authored `box-sizing`-carrying Width+Height components (17 docs) never sit under a vertical container TODAY; a future authored doc could.
- ~~`hunk-for-orchestrator-1.patch` … not compiled/run~~ — **superseded by the fix pass**: compiled and run, A/D green, H1/H2/H3 executed (§11).
  What stays unverified about it: it was run in a PRIVATE copy of `Package.swift` + `runtimes/swiftui` (snapshot of the shared tree at 18:39:11,
  renderer reset to the HEAD blob), not in the shared tree — the four files are unowned, so applying them in the shared tree would have been a
  foreign edit; the copy holds the same bytes as the tree for every other Swift file at snapshot time. Its render-level behaviour for an AUTHORED
  Width+Height sole child of a vertical multicol (now planned, not bailed — e.g. an inline-overflowing child is clipped to colH by the wave-47
  V-table) is unmeasured against a browser: zero corpus carriers, parity with Compose's in-tree twin.
- Remaining nits accepted, not fixed: `ContentsUnboxing.{kt,swift}` / their tests and `FixedHoist.swift` grew past the 200-line target (nit 5;
  splitting them is a refactor of files other lanes may diff against); the results dir is uncommitted (nit 7 — the orchestrator commits).
- **Scratchpad collision (fix pass, honest record)**: the session scratchpad is SHARED by every lane. From 18:31 to 18:39 this lane used the generic
  names `…/scratchpad/{hk,pkg,dd}`; `dd` already existed (born 2026-09-25, used by another lane's prewarm at 18:19, and by the flex-nowrap-gaps
  seam verification launched 18:38:48), so one L3 xcodebuild attempt at ~18:39:30 failed on a locked build DB (nothing ran; the other lane's
  build was not disturbed beyond sharing the derived-data cache). `hk`/`pkg` were born by L3 at 18:32/18:33 (no other lane's data removed). All L3
  scratch moved to `…/scratchpad/l3-failure-ink/` at 18:40; `…/scratchpad/dd` was left in place for its owner. The same snapshot also caught lane
  L10's seam transiently applied to the shared `ComponentRenderer.swift` (it was verifying under its lock) — every hunk run was then REPEATED on
  the HEAD renderer (the RE-RUN section of `_swift-hunk1-mutations.log`); the logs named `_swift-hunk1-run{A,D…}.log` are the HEAD-renderer runs.
- **Lock incident (2026-10-05 17:11:58, honest record)**: an inline `mkdir LOCK && {…}; rmdir LOCK` found `wave52-lock/ComponentRenderer.swift` HELD by another
  lane (mkdir failed, so nothing was applied), but the trailing `rmdir` removed that lane's lock directory; it was recreated with `mkdir` within ~1 s (17:11:59 —
  the `mkdir` succeeded, so nobody took the lock in the gap). The holder's patch on the seam file was never touched; its lock directory now carries a 17:11
  mtime instead of its original one (so a stale-lock age check on it reads ~1 s younger than the truth). Every later lock operation goes through
  `seam-verify.sh`, which arms its release only after its own `mkdir` succeeds.

## 9. Hand-offs

- **Orchestrator (fix pass — supersedes the next bullet where they differ)**: apply the RE-CUT `seam-1.patch` (`3e3c446e…`) and `seam-2.patch`
  (`43809f52…`) — each now also CREATES a test file (`VerticalBlockFlowSeamWiringTest.kt` / `VerticalBlockFlowSeamWiringTests.swift`), so a
  dropped or mis-merged seam turns a pin red instead of silently losing T1. Apply `hunk-for-orchestrator-1.patch` (`4f64d533…`): it touches four
  unowned paths (`VerticalMulticolPlan.swift`, `VerticalFragmentGeometryTests.swift`, `Wave12BoxSizingBasisTests.swift`, NEW
  `VerticalMulticolBakeGateTests.swift`), is order-independent of the seams, and is verified alone and with seam-2 (§11). Apply it WHOLE — the
  source hunk without its two test-row edits turns two Catalyst pins red (H3, and the skeptic's run).
- **Orchestrator (resume pass)**: apply `seam-1.patch`, `seam-2.patch` in the §4 order; decide on `hunk-for-orchestrator-1.patch` (parity, zero corpus movement);
  paste `watchlist-additions.txt` into the plan watchlist (5 lines — `WATCH=… watchlist-check.mjs` → `unmatched 0`; it now adds `css-tables/baseline-vertical ios|android`).
  BACKLOG paragraph for 0(k): "F1/F2/F3 shipped (wave 52, L3); the seven sampled 0(k) cells all PASS and are correctness work — F4 (§7.2 sequential fill, 046),
  F5 (table-box clip, 098), F6 (§7.3.1 orthogonal available size, 031/068) remain queued with the carrier counts in failure-ink.md §5." Please tell the
  holder of `wave52-lock/ComponentRenderer.swift` at ~17:11 about the §8 lock incident (its lock mtime was reset).
- **L7**: no L3 partition patch exists — cut `:2098-2112` against HEAD (seam-2 is at `:4354-4361`). `FixedHoist.swift` changed (defaulted parameters only).
- **L8**: M-F is folded — the baked guard is `BakedLayoutSignature` on both natives. NEW FYI: the P1-used pins call `TableBoxTree.uaRoleOf` /
  `consumableRowList` (Compose) and `TableBoxTree.roleOf(_:sourceTag:)` + `TableSeparatedTracks.arrangement` (Swift) — a signature change there (your M-E) must
  keep them compiling; the td-different-subpixel P cells' protection from the vertical seam now rests on that table path.
- **L11**: `ContentsUnboxingTests.swift:175-183` is byte-identical; my in-place edit kept lines 34-48 at the same count, the new test is appended after
  the last existing function.

## 10. Seam-2 re-verification (resume pass)

`_seam2-verify.log` 2026-10-05 17:16:34 via `seam-verify.sh` (queued behind another lane's lock, taken when it freed): sha before `d2afc70d…ee63`,
patch applied, xcodebuild recompiled `ComponentRenderer.swift` and `BakedLayoutSignature.swift` (`_swift-seam2-run2.log`), ContentsUnboxingTests 11 ·
FixedHoistTests 14 · MulticolSpannerContainingBlockTests 14 · MulticolSpannerFlowTests 26 · MulticolSpannerHoistRasterTests 3 ·
VerticalBlockFlowSeamGuardTests 5 = **73/0, TEST SUCCEEDED**; restored, sha after `d2afc70d…ee63` (equal), `git diff --quiet` clean; lock released 17:17:40.
Both seam patches are therefore verified on their FINAL text with this lane's FINAL files (patch shas `848bccea…` seam-1, `83defc39…` seam-2).
(Superseded by §11: the fix pass re-cut both seams — new shas `3e3c446e…` / `43809f52…` — and re-verified them.)

## 11. Fix pass (2026-10-05, after `skeptic.md`) — what was executed

**Must-fix (the hand-off hunk reddened Catalyst pins) — FIXED, not withdrawn.** Reproduced first: with the source hunk alone,
`VerticalFragmentGeometryTests.testVerticalPlanDeclinesBakedLayout` (the skeptic's row) AND `Wave12BoxSizingBasisTests.testInheritedVerticalModeRendersUnfragmented`
(`:394`/`:397`, a raster row the skeptic's suite list did not include) go red: both fixtures were "baked" only by the wave-47 heuristic (bare
Width + Height). Both rows now carry the real signature (+BoxSizing CONTENT_BOX, +PaddingTop 0 — zero geometric effect), and a NEW
`VerticalMulticolBakeGateTests.swift` pins the verbatim -017 child and two authored shapes (§4 P1′). To find every pin the hunk can reach, the
Swift test tree was grepped for multicol × vertical-writing-mode fixtures and for `fragmentPlan`/`ColumnsApplier`/`BakedLayoutSignature`
callers: 7 suites (+ the L3 and multicol suites) — all 16 run below.
Runs (Mac Catalyst, PRIVATE copy `…/scratchpad/l3-failure-ink/pkg` = `Package.swift` + `runtimes/swiftui` snapshot 18:39:11 + the final hunk
`git apply`d, `ComponentRenderer.swift` reset to the HEAD blob `d2afc70d…`, harness font copied so `TableColumnWidthsTests` can register it):
- **A** hunk alone, 16 suites: **166/0** (`_swift-hunk1-runA.log`).
- **D** hunk + seam-2 (first-pass text): **166/0** (`_swift-hunk1-runD-with-seam2.log`); hunk + RE-CUT seam-2: **168/0** (`_seam2-private-verify.log`).
- **H1/H2/H3** mutations and restores: §4 P1′ row, `_swift-hunk1-mutations.log` (first section ran with L10's transient seam in the snapshot;
  the RE-RUN section repeats A/H1/H2/D on the HEAD renderer — identical outcomes).
- Census `f1swiftUsed`: 0 movers (§3). `git apply --cached --check` of the final file (header + body) on a HEAD index: clean.

**Should-fix 2 (seam wiring unpinned) — FIXED**: §4 P-wire, §5 re-cut. **Should-fix 3 (T3 not self-certifying) — recorded** in §7.
**Nit 4** (used-mode count 23 in both seam comments) and the `MulticolSpannerFlow.kt` "can never disagree" sentence — fixed (that comment now
quotes the f1swiftUsed census and no longer claims parity it cannot see). **Nit 6** — the raster pin's header says what is verbatim (payloads)
and what is not (ids, `meta`). **Nit 8** — `hoistsToInitialContainingBlock` now logs `multicol-spanner-abspos-outer-cb` once on the
outer-positioned-CB approximation (same boolean result; restructured guards), pinned in S4 and mutation-proven (N8). Nits 5 and 7: §8.

**Final in-tree state** (seams NOT applied, shared tree at 19:08–19:10): Swift 6 L3 suites **73/0** (`_swift-focused-run3-fixpass.log`, renderer
at HEAD bytes before and after); Compose 25 suites **223/0** (`_compose-focused-run6-fixpass-final.log`, `_jvm-xml/fixpass-final/`). With seam-1:
226/0 (+3 wiring); with seam-2: 117/0 on its 11 suites. No foreign file edited: the hunk's four paths were only ever applied in the private copy;
both seam files were restored to HEAD bytes (sha-equal, `git diff --quiet` clean) and their new test files removed before each lock release.

STATUS: COMPLETE

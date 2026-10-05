# wave-52 lane L10 · flex-nowrap-gaps — executed-repro skeptic

Tree `campaign/wave52` @ HEAD 7d9c22a7 (shared, other lanes' uncommitted work present). Session 2026-10-05 ~19:00–19:30 CEST.
Read: PLAN.md §2 L10 / §3 / §5 / §9, brief `runtime-bugs-and-gaps.md` §4 + §7 pins, the lane's `_note.md` (ends `STATUS: COMPLETE`),
`flex-nowrap-gaps.census.mjs/.json`, `png-replay.mjs/.json`, `mutate.py`, `mutations.log`, `seam-1/2.patch`, `watchlist-additions.txt`,
`head-shas.txt`, `logs/`. Scripts I wrote live in the session scratchpad (not committed; their verbatim logic is summarised below).

## Verdict

The lane is complete and its headline mechanics hold under independent execution: every re-run pin is green, every re-executed
mutation goes RED by assertion with byte-exact restore, both seam patches apply clean on HEAD and compose with every other lane's
seam patch in either order, my own census reproduces every class count, and the wave51-fix PNGs make all three predicted flips
plausible (002 iOS: the raster model reproduces the device defect to the pixel — 8 900 red / 1 100 green). One must-fix: the
Compose `FlexNowrapLine.Line` places with `place` (not `placeRelative`) and the gate never looks at direction, so an RTL nowrap row
the gate admits would be drawn on the wrong side where `Row` mirrored it — a regression on admitted inputs (0 corpus carriers, so
the fix is gate-neutral). Several should-fix items: an iOS reader that takes the bare-number PERCENT margin/padding wire as px
(contradicting its own doc and its Kotlin twin), the `Line` composable being unexecuted by any pin (my mutation placing every child
at the origin stays GREEN), and a colour-filtered snap-mover oracle whose "integral rects cannot move" claim is false for 8 decorated
tests (measured here: immaterial, ≤ 0.0014, no P→f).

## Executed repros

| # | what | result |
|---|---|---|
| R1 | Kotlin pins, no seam: `:runtime:testDebugUnitTest --rerun --tests …layout.flexbox.FlexNowrapLineTest --tests …columns.*` (XML copied at once — the shared build dir is overwritten by concurrent runs) | FlexNowrapLineTest 7/7, GapNowrapShiftAndSnapTest 4/4, GapDecorationBandsTest 11/11, all of `columns.*` green |
| R2 | SK-K1 = lane KT-G2: `return anyNegative \|\| line > main` → `return true` in FlexNowrapLine.kt | RED: `a fitting line without negative margin is refused` AssertionError :94; sha 5dd1d0e0… before = after |
| R3 | SK-K2 = lane KT-H1: `GapItemSink.placedItems` shift → `(0f to 0f)` in GapDecorationHook.kt | RED: `027 rules sit in the outer-size gaps…` AssertionError :53; sha ab386830… before = after |
| R4 | SK-K3 (mine): drop the gate's child cross-size px requirement | GREEN (7/7) — clause unpinned; ablation below shows 0 corpus containers depend on it |
| R5 | SK-K4 (mine): `Line` places every child at (0,0) | GREEN across `layout.flexbox.*` + `columns.*` (compileDebugKotlin executed) — the composable is unpinned; sha restored |
| R6 | seam-2 under the lock: `mkdir …/wave52-lock/ComponentRenderer.kt`; sha c4369165…03e652 (= HEAD); `git apply`; `--tests layout.flexbox.* columns.* *SeamReachabilityTest`; `git show HEAD:<path> > <path>`; sha equal; `git diff --quiet`; `rmdir` | compileDebugKotlin executed, 413 tests 0 failures; restored SHA_OK, clean |
| R7 | `git apply --check --cached` of seam-1 / seam-2 against the HEAD index | both clean |
| R8 | seam composition, scratch repo seeded with the two HEAD seam files: all other lanes' 17 seam patches then L10's, and L10's first then the 17 | L10 seam-1/-2 apply in BOTH orders. (`wave52-counters-and-lists/seam-2.patch` fails at `ComponentRenderer.swift:3781` in both orders — L6's, independent of L10.) |
| R9 | Swift pins with seam-1, on a PRIVATE COPY of `Package.swift` + `runtimes/swiftui/` (same bytes as the shared tree: ComponentRenderer.swift d2afc70d = HEAD; L10 files a2fd40ce / 993db5fc / 1e237113 = the lane's final shas), private `-derivedDataPath`, Catalyst | FlexNowrapOuterTests 3/3, FlexNowrapRasterTests 3/3 (002, 003, 027), + EmptyFlexContainer, FidelityWave2, FlowLayoutDistribution, Placement, GapDecorationHook/Raster/Segments/Bands, FlexWrapStretch: 80/80 |
| R10 | SK-S1 = lane SW-M1 (copy, seam-1 applied): `cursor += s + outer(i) + gap + between` → `cursor += s + gap + between` | RED: testNegativeMarginAdvancesByOuterSize `[0,60,…,300] ≠ [0,−90,…,150]`; testRaster027 (no rule at −100…140, Six overflows); testRaster002 red 500; sha restored |
| R11 | SK-S2 = lane SW-B1 (copy): `percentBasis` → `return nil` | RED: testPercentBasis `[nil,nil]`; **testRaster002 red 8900 / green 1100 — exactly the wave51-fix iOS capture's ink (green x 16–26 = 11 border columns, red 27–115)** |
| R11b | full Swift suite on the copy with seam-1 (2133 tests) | 45 failures, ALL copy-environment (files outside `runtimes/swiftui/` absent: schema goldens, wpt per-test docs, harness fonts — ConformanceTests ×22, IRDecodeSeam, ChUnitInlineAxis, TableColumnWidths, …); none touches flex / gap / placement |
| R12 | full Compose suite, no seam (3364 tests) | 3 failures, all L9's (`LineClampUnderPreWave39Test`, `PlaceholderOverflowMarkerTest` ×2 — need L9's seam); none L10 |
| R13 | my census (Python, written from the Swift/Kotlin SOURCE, contents-unboxed children, own readers) over 1435 docs | see "Census" — every class equal to the lane's |
| R14 | gate ablation (each clause removed alone) over 387 flex containers | only `shrink` (+1: view-transitions far-away-capture) and `trigger` (+11) are load-bearing; both pinned (R2, lane KT-G1) |
| R15 | any-hue snap oracle (Node, repo `ssim.js` `fast` + `pngjs`, same call as the lane) over the 46 decorated tests | reproduces the lane's warm-oracle numbers exactly where they overlap (008 android 0.9824, 046 android 0.9976, 036 0.9561 …); finds the non-warm movers below |
| R16 | `node wave52-plan/watchlist-check.mjs` with `WATCH=…/watchlist-additions.txt` | 14 lines, `unmatched 0` |
| R17 | `:converter:run` on `fixtures/properties/effects/box-shadow-reach.json` | converts: 4 components × 5 props, BoxShadow spreads −8/0/+8/−1, no generic |
| R18 | PNG review (ref = `tools/wpt/refs/9b5435e5…/white-black-ink-font-lh-imgpad-htmlpins/…`, captures `runs/wave51-fix/sections/<sec>/{screenshots,ios-screenshots,android-screenshots}/`) | see "Predicted flips" |

Every shared-tree mutation was restored byte-exact (sha256 before = after printed per run); no lock was left held; the Swift seam was
never applied to the shared tree (copy instead — nothing to lock).

## Census (mine vs lane)

| class | mine | lane |
|---|---|---|
| percent basis (bare-number FlexBasis) anywhere | 8 components / 4 tests (all under flex parents) | 4 tests |
| … resolves on the iOS nowrap arm (nowrap, in-flow, px main) | 2 items / 1 test (background-clip-content-box-002) | 1 |
| … untouched | abspos-child-002 ×3 (abspos — `inFlowChildren` filters them, ComponentRenderer.swift:772, so `flexMainPlan` never sees them), 025 ×2 (wrap), calc-size-flex-007 ×1 (wrap) | 3 |
| M2 negative main-axis px margin, in-flow, nowrap | 2 (027 −150 L; dynamic-relayout-003 −100 L) | 2 |
| bare-number (percent) margin on a nowrap flex child | 0 | — |
| Compose gate admits | 2 (008, 027) — and with BOTH axes tried on every parent of any display, only 3 non-flex parents more (unset-val-001, two multicol blocks), unreachable because the route lives inside the flex Row/Column branch | 2 |
| decorated flex containers | 46 | 46 |
| decorated + negative-margin child (anchor shift) | 1 (027) | 1 |
| decorated with a half-pixel-centred rule by IR geometry ((gap − width)/2 non-integral) | **8: 024, 029, 030, 034, 035, 036, 037, 050** (+ 045/046 fractional bands) | not counted ("integral rects — every other css-gaps test — come back unchanged") |

## Predicted flips — PNG judgement

- **background-clip-content-box-002 iOS** (f 0.9992 cF): ref green 100×100 at x 16–115 / y 68–167; web and Android identical; iOS green
  x 16–26 then red 27–115 (8 900 px). R11 reproduces that exact ink in the Catalyst raster with the percent arm removed and R9 paints the
  green square with it. Plausible → P (HIGH stands).
- **flex-gap-decorations-027 iOS / Android** (f 0.903 / 0.9085): ref = container border box from page x 216 (body margin 200), items One…Six
  at 68/128/188/248/308/368, five red rules at 118…358. iOS today: container at 16, One pulled off-canvas (white hole), Two…Six at 60-px pitch
  overflowing by 150. Android: Four/Five/Six squeezed at 183/193/193. With L2's M1 (+200) the iOS slots `[0,−90,…,150]` + MarginApplier's −150
  pull give the ref geometry (R9/R10 raster pins it under ImageRenderer, which is also the harness's capture path, scale 1); Android needs the
  unexecuted `Line` (R5) — MED is the right confidence. Plausible.
- **008 Android** (P 0.9646, no flip): iOS (0.9996) already paints the ref's 60-px pitch; `Line` + offsets `[0,60,…,300]` would match.
- The ring-fenced `filter-effects/backdrop-filter-basic-blur` is block-only (no flex, no rule, margins `{px:0}`/auto) — no L10 mechanism reaches it.

## Defects

1. **must-fix — Compose RTL.** `FlexNowrapLine.Line` places with `p.place(at[i], 0)`; `engages` never reads `Direction` and cannot see an
   inherited one. `RenderComponent` provides `LocalLayoutDirection` (ComponentRenderer.kt:2117) and `Row`'s `spacedBy` arrangement mirrors in
   RTL, so an admitted RTL line (shrink-0 overflow or a negative margin) moves from the right edge (Row) to the left edge (Line) — CSS RTL
   main-start is the right. 0 corpus carriers (14 RTL flex containers / 7 tests, none admitted), so the fix is gate-neutral: `placeRelative`
   (or refuse when `LocalLayoutDirection.current == Rtl`) + a pin.
2. **should-fix — iOS percent wire read as px.** `FlexOuterFacts.px` uses `ValueExtractors.extractPx`, which returns `.double/.int` bare
   numbers as px; on the Margin*/Padding* wire a bare number is a PERCENTAGE (`MarginExtractor` → `extractLengthPercentDefault` →
   `.relative(unit: .percent)`, LengthValue.swift:120-126; corpus: `abspos-auto-sizing-fit-content-percentage-001/002` `MarginLeft/Right −50`).
   The doc says "0 for a side that is auto, a percentage or absent"; the Kotlin twin's object-only `px` refuses them. Effect: a nowrap flex
   item with `margin-right:-50%` in a 40-px container advances by −50 (CSS −20); positive percent margins / padding on a percent-basis item
   size the frame wrong; `paintShift` anchors at neither slot nor painted box. 0 corpus carriers (census row "bare-number margin on a nowrap
   flex child" = 0).
3. **should-fix — `Line` is unpinned.** R5 (all children at the origin) stays GREEN; brief pin 4 asked for a layout-level test (offsets AND
   widths, Row fallback). No JVM Compose layout harness exists (verified: no Robolectric / ui-test; no test drives a MeasurePolicy), so
   extract the measure body into a pure function (sizes, deltas, gap, constraints → w, h, positions) and pin that; until then Android 027/008
   rest on compile-only code (disclosed in note §7).
4. **should-fix — snap blast radius colour-filtered.** `png-replay.mjs` `ruleish` admits warm pixels only; blue/green/grey/pink rules are
   invisible to it, and `GapDecorationsPainter.snapped`'s doc ("Integral rects — every other css-gaps test — come back unchanged, so they
   cannot move") is false for the 8 half-pixel tests above. Measured with an any-hue oracle (R15): 024 ios/android +0.0001 (an **L5-watched**
   target, now moved by L10 undeclared — attribution confound, negligible), 034 −0.0008, 035 −0.0014 (both stay f), 036 0.9565→0.9557 and
   037 0.9576→0.9568 (stay P, margin ≈ 0.006), 004/005 small rises. Immaterial, but the claim and the watch attribution should be corrected
   (add 024 / 029 / 030 / 034–037 / 050 to L10's attribution; the gate's `--movers 0.005` would not flag any of them).
5. **nit — unpinned gate clauses.** Only `shrink` and `trigger` are individually pinned (R2, KT-G1); R4 shows the cross-size clause can be
   dropped GREEN. R14: no single unpinned clause changes the corpus admission set, so no blast radius today.
6. **nit — gate fit test ignores box-sizing.** `main = px(Width)` is compared with the outer line; under `box-sizing: border-box` with a
   border/padding band the comparison is off by the band (008/027 are content-box).
7. **nit — silent fallthrough.** Non-px (em / auto / calc) main-axis margins contribute 0 to the iOS `outer` and the iOS/Compose paint shift
   with no PropertyTracker breadcrumb, while MarginApplier still paints them.
8. **nit — file size / note drift.** CSSFlexLayout.swift is 429 lines (note says 414; 343 at HEAD; CLAUDE.md "split past ~300");
   GapDecorationsPainter.swift 233 → 302; GapDecorationHook.kt 208 and GapDecorationBands.kt 224 (> 200 target). New files are all ≤ 200
   (FlexNowrapLine.kt 197) and block-commented.
9. **nit — ownership edges (no foreign edit).** Every changed path is in L10's own list or is an L10 test, except
   `runtimes/compose/src/test/…/columns/GapDecorationBandsTest.kt` — an UNOWNED existing test of L10's `GapDecorationBands.kt`, edited for the
   fractional share and flagged in the note. `MulticolSpannerFlow.kt` (modified in the tree) is L3's — its diff carries no L10 hunk.
10. **nit — 6(c) is a recorded expectation, not a test.** The fixture's v1 `_expect` pins fill + box (v1 `box` is the bbox of FILL-coloured
    pixels — it cannot see a shadow row), so brief pin 7's "bottom-most shadow row" is in `note` only. Spec arithmetic re-checked (σ = 5,
    1 − Φ(d/σ) ≥ 0.5/255 ⇒ d ≤ 14.4 → rows 56/64/72/63). Disclosed.

No test-name carve-outs (L10's source and patches compare no ids / names). No device A/B staged, so no hash sentence is owed.

## Re-verify

Session 2026-10-05 ~19:40–19:47 CEST, same tree (HEAD 7d9c22a7, other lanes' uncommitted work present). The note still ends
`STATUS: COMPLETE`; §10 records the fix pass. Lane files at the shas §10 states: FlexNowrapLine.kt a4d6d16b…, GapDecorationHook.kt
6212059b…, GapDecorationPainter.kt 49cdf61e…, GapDecorationsPainter.swift 93c87337…, FlexNowrapLineTest a50bfbac…,
GapNowrapShiftAndSnapTest 73b3e9b6…, seam-2.patch e2c0dbea… (`head-shas.txt`'s unprefixed top block is the HEAD shas, checked with
`git show HEAD:<path> | shasum`).

### Must-fix #1 (Compose RTL): FIXED, confirmed by execution

The fix is a refusal: `engages(…, rtl)` returns false under Rtl, so every RTL row or column stays on `Row`/`Column`, byte-identical to
HEAD. `Line` now uses `placeRelative`, which equals `place` on every admitted (LTR) line. The hook's paint shift mirrors x under Rtl.

How the signal reaches the gate, read from source: `RenderComponent` provides `LocalLayoutDirection` only when the component's own
`Direction` is RTL (ComponentRenderer.kt:2116-2121). That provider wraps `selfAlignedContent` → `inheritanceWrappedContent` → `content`
→ `RenderComponentContent` (:1807/:1810), which holds both the engine Row branch (the seam's `RenderNowrapLine` call) and
`rememberGapDecorationSink` (:2146). So the gate sees the container's own `direction: rtl` and any inherited one. `Row` lays out from
the same local in the same scope, so the gate and `Row` read one signal. The only other provider in the runtime is RootPseudoBox.kt:107
(Ltr, around the root pseudo shim only). An explicit `direction: ltr` under an RTL ancestor pushes no provider, so it stays Rtl and is
refused. That is the conservative side: it is HEAD behaviour.

| # | executed check | result |
|---|---|---|
| V1 | pins without the seam: `:runtime:testDebugUnitTest --rerun --tests layout.flexbox.* --tests columns.*`, XML copied at once (timestamps 17:41:40Z) | 413 tests, 0 failures. FlexNowrapLineTest 9/9 (7 + the 2 RTL pins), GapNowrapShiftAndSnapTest 5/5 |
| V2 | RV-D1 = the lane's KT-D1, re-run independently (my `mut.py`: exactly one site, restore from memory, sha256 before vs after): delete `if (rtl) return false` | **RED** `an RTL line is refused - Row mirrors it to main-start at the right edge` by AssertionError; compileDebugKotlin ran; sha a4d6d16b… before = after |
| V3 | RV-H2 = the lane's KT-H2: `paintShiftPx(it.properties, rtl)` → `paintShiftPx(it.properties, false)` in `paintShiftsFor` | **RED** `paint shifts follow the container direction - RTL mirrors One's pull` by AssertionError; sha 6212059b… before = after |
| V4 | seam-2 (re-cut): `git apply --check` on the worktree and `--cached` on the HEAD index | both clean. The diff against `logs/seam-2.pre-fix-pass.patch.txt` is only the `rtl` read and argument, the header, and hunk context |
| V5 | seam-2 under the lock: `mkdir tools/titan/runs/wave52-lock/ComponentRenderer.kt` 19:43:36; sha c4369165…03e652 (= `git show HEAD:`); `git apply`; `--tests layout.flexbox.* columns.* *SeamReachabilityTest *BlockBoxAlignItemsTest` | compileDebugKotlin **executed**; 418 tests, 0 failures (SeamReachability 3/3, BlockBoxAlignItems 2/2, FlexNowrapLine 9/9) |
| V6 | RV-S1 (mine), still under the same lock: in the applied seam, `rtl = rtl)) return false` → `rtl = false)) return false` (the pre-fix wiring) | **GREEN** 418/0 with compileDebugKotlin executed. The seam's direction wiring, the part that delivers the fix on device, has no pin. Then `git show HEAD:<path> > <path>`; sha c4369165… equal; `git diff --quiet` clean; `rmdir` 19:43:45 |
| V7 | RV-W1 (mine): in the hook, `val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl` → `val rtl = false` | **GREEN** 413/0 (compiled). The hook's direction read has no pin; restored, sha equal |
| V8 | RV-P1 (mine): `Line` `placeRelative` → `place` | **GREEN** 413/0, as expected: under the refusal the two are the same on every admitted line, and `Line` still has no pin (should-fix #3 from the first pass); restored, sha equal |
| V9 | my census (`rtl_census.py`, written from the FlexNowrapLine.kt source, not the lane's JSON): 1435 docs; Rtl = own or any ancestor `Direction "RTL"`; the gate run with real `rtl` and with `rtl` forced false, on raw and on `display: contents`-unboxed children, plus the other axis for every RTL container | 387 flex containers. RTL **14 containers / 7 tests**: abspos-autopos-{htb,vlr,vrl}-rtl, flex-align-baseline-column-rtl-direction, …-vert-{lr,rl}-rtl-wrap-reverse, direction-upright-002. Admitted before and after = **{008, 027}**. RTL ∩ admitted = **0** on either axis. Equal to the lane's R / G / G_rtlRefused |
| V10 | S_rtl superset: every flex child (with ≥ 2 siblings) whose x paint shift is nonzero, then those mirrored (container RTL or the item's own RTL), whether or not the container is decorated | 2 children have a shift, **0** of them mirrored. The hook change moves no corpus cell |
| V11 | seam composition: scratch repo seeded with HEAD's ComponentRenderer.kt; apply all 9 other Compose seam patches (L-all-reset, counters ×2, failure-ink, inline-run-wall, small-fixes, static-position, vertical-wedges ×2) with L10 first, then with L10 last | **10/10 apply in both orders**; the `rtl = rtl` wiring is present once in both results |
| V12 | Swift side of the fix pass: diffed against my pre-fix private copy (`swc/`, sha 1e237113…) | the only change is the `///` comment at :115-117; `swiftc -parse` OK; `git apply --check seam-1.patch` clean (seam-1 unchanged) |
| V13 | PNG re-look (wave51-fix android/ios + the frozen `…/white-black-ink-font-lh-imgpad-htmlpins/` ref) for 027 and 008 | unchanged from the first pass. Both admitted carriers are LTR (`rtl = false`), so the fix pass changes neither prediction. Android still shows the 183/193/193 squeeze that `Line` removes |
| V14 | ownership: `git status` plus files tagged "lane L10" | every L10-tagged path is in L10's own list (plus the flagged `GapDecorationBandsTest.kt` and the new Swift test files under `Tests/`). The fix pass touched only FlexNowrapLine.kt, GapDecorationHook.kt, the two painters (comments), the two Compose tests and seam-2. `MarginApplier.kt` (L4) is modified in the tree, but only by L4's hunks (mtime 18:21, "lane L4" tags, no L10 hunk) |

No test-name carve-outs (no id or name literals in FlexNowrapLine.kt, GapDecorationHook.kt or seam-2). No device A/B is staged, so no
hash sentence is owed. The ring-fenced `backdrop-filter-basic-blur` is not a flex container, so nothing in the fix pass reaches it.
Every mutation was restored byte-exact; the lock was released and the lock dir is empty; ComponentRenderer.kt is at HEAD
(`git diff --quiet`).

### Regressions: none found

The LTR path is unchanged. `rtl` is false whenever `LocalLayoutDirection` is Ltr. `paintShiftPx(…, false)` equals the pre-fix
formula for any item without its own `Direction RTL`. The new `remember` key is constant under Ltr. `engages` has no callers besides
the tests and seam-2 (grep). The admitted set and every census class are unchanged.

### Remaining defects after the fix pass

- **should-fix, NEW: the fix's wiring has no pin.** V6 and V7 go GREEN when the seam's or the hook's `LocalLayoutDirection` read is
  replaced by `false`. The guarantee "an RTL line is refused on device" therefore rests on two source lines that no test reads. This
  module already pins seams at source level (SeamReachabilityTest, FragmentGeometryTest). A source pin for
  `engages(…, rtl = LocalLayoutDirection.current == LayoutDirection.Rtl)` in `RenderNowrapLine`, and for the hook's `rtl` feeding
  `paintShiftsFor`, would close it. Not must-fix: the pure refusal is pinned and RED under mutation (V2, V3), the wiring is correct as
  read (provider chain above), and 0 corpus containers are RTL ∩ admitted (V9).
- **should-fix, carried from the first pass:** #2 (iOS bare-number percent margin/padding read as px) and #3 (`Line` measure body
  unpinned; V8 shows it again). Both are unaddressed and disclosed in note §7; 0 corpus carriers.
- **nit:** GapDecorationHook.kt is now 222 lines (190 at HEAD, 208 at the first pass), past the ≤ 200 target for an existing file.
  New files stay ≤ 200 (FlexNowrapLine.kt 198).
- **nit, observation:** iOS M2 (ungated) was not re-examined for RTL (note §7). V10's superset finds 0 RTL carriers, so no blast
  radius today.

Verdict: the must-fix is closed, and the fix is gate-neutral by an independent census. Nothing must be fixed before the gate.

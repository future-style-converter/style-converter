# Phase 12 Audit — Category: spacing

Fixture: `examples/properties/spacing/audit-phase12.json` (18 edge-case
components, rendering as 46 captured PNGs once children are split out).

Run: `SKIP_IOS=1 NO_OPEN=1 ./test-all.sh …` under a `/tmp/sc-testall.lockfile`
mutex (mirrors the lock scheme the other parallel audits use — an earlier
`perl -MFcntl flock` on `/tmp/sc-testall.lock` did NOT hold off siblings and
resulted in `out/tmpOutput.json` being overwritten mid-run).

Snapshot: `testing/audit/spacing/snapshot/` (contains the spacing rows plus
trailing speech-audit rows that raced after our run completed; see "Snapshot
hygiene" below). Worst-offender diff/render PNGs copied to
`testing/audit/spacing/images/`.

## Scope

IR properties covered by this category and exercised in the fixture:

- Physical margins: `margin-top/right/bottom/left` (+`margin` shorthand).
- Logical margins: `margin-block-start/end`, `margin-inline-start/end`.
- Physical padding: `padding-top/right/bottom/left` (+`padding` shorthand).
- Logical padding: `padding-block-start/end`, `padding-inline-start/end`.
- Gap family: `gap`, `row-gap`, `column-gap`.
- Margin trim: `margin-trim`.
- Scroll-\*: `scroll-margin*` + `scroll-padding*` longhands & shorthands.
- Edge-case value flavors: `auto`, negatives, `%`, `calc()`, `em`, `9999px`,
  and the CSS `normal` keyword on `column-gap`.

## Totals

- iOS: skipped (xcodebuild "disk I/O error" on the build DB — environmental,
  not a code issue; retried after `rm -rf testing/iOS/build`, failed again).
- Android: 46/46 screenshots captured after uninstall + cache wipe.
- Web: 46/46 captured.
- **iOS↔Android, iOS↔web pairs: N/A** (no iOS captures).
- **Android↔web SSIM**:
  - passing (≥ 0.95): **3 / 46** (just three flexbox children whose layout
    survived intact: `003_a`, `004_b`, `006_a`).
  - failing (< 0.95): **43 / 46**.
- Decode errors: 0.
- Size mismatches: 23 rows (pixel-for-pixel Android and web canvases differ
  in height by 16–160 px on almost every padding/margin/gap case).

## Failing cases (Android ↔ web, sorted by SSIM)

| SSIM | name | root-cause hypothesis |
|---|---|---|
| 0.357 | `011_inner` (child of Padding_ExceedsParent) | Android clamps padding at box edges; web renders `padding: 220px` on a 40px child (total ~480px). |
| 0.459 | `010_Padding_ExceedsParent` | Same as above — parent height differs by 40 px. |
| 0.628 | `031_Gap_Grid_AutoGap` | Grid `gap: 0` renders different column widths on Android vs web (grid track resolution). |
| 0.666 | `024_Gap_Zero_vs_Normal` | `column-gap: normal` — Android GapExtractor treats as `Unknown` (`extractLength` on the string `"normal"`), web emits the literal keyword. |
| 0.683 | `016_Padding_Calc_Dynamic` | `padding: calc(20px + 10px)` → Android `SpacingResolve.resolveToDp` returns `0.dp` for `LengthValue.Calc` (see TODO at line 56); web emits literal `calc(20px+10px)` which CSS evaluates correctly. |
| 0.697 | `023_inner` (Margin_LogicalMixed) | Android `MarginApplier` uses `Modifier.offset` so logical sides are folded via `SpacingResolve` but the Compose container doesn't reflow — overlap instead of reflow. Web lets flow layout reflow. |
| 0.708 | `021_inner` (Padding_Logical_Mixed) | Logical → physical resolution in Android goes through `PaddingConfig.resolve()`, but the child's intrinsic size differs from web's flow box. |
| 0.708 | `009_inner` (AutoMargin_Block_Center) | Android centers via `wrapContentWidth` — works, but vertical positioning of the centered child differs from web's block-flow centering. |
| 0.718 | `028_Gap_NonFlex_Context` | `display: block` with `gap: 40px`: web ignores gap (CSS spec). Android's ComponentRenderer may still pass the gap to a Row/Column if the dispatch logic treats any `gap` as flex. Gap should be ignored when not in flex/grid/multicol. |
| 0.720 | `020_Padding_Logical_Mixed` | Parent container height diverges (107 vs 82 px) — logical padding on the *parent* not fully resolved consistently. |
| 0.728 | `039_Nested_PaddingMargin_Collapse` | Block margin collapsing (parent padding-top + child margin-top) — CSS collapses adjacent vertical margins; Compose's `offset` does not. Android adds the full 30+30 = 60 px, web collapses to 30. |
| 0.729/0.730 | `042_ScrollMargin_Basic` / `044_ScrollMargin_Sides` | scroll-margin/scroll-padding not in the `spacing/` triplet (lives under `testing/web/src/style/engine/scrolling/Scroll*`). Android likely has no visual effect either. Sizes still mismatch because the generic renderer falls back to default layout. |
| 0.734–0.787 | most margin/padding children | Height mismatches caused by block-box margin collapsing and by MarginApplier's `offset()` choice (affects position, not size, so parent height is wrong). |
| 0.825 | `005_AutoMargin_Flex_PushRight` | `margin-left: auto` on a flex item should push the item to the end. Android's `MarginApplier` handles it via `wrapContentWidth(Alignment.End)` but that only aligns within the child's own bounds, not within the flex row. |
| 0.848 | `002_NegMargin_Top_Overlap` | Negative `margin-top: -20px` — Android offsets but still reserves 60 px of parent height; web overlaps correctly. |
| 0.854 | `000_NegMargin_Sides_Overflow` | Negative horizontal margins widen the child past parent (web); Android `Modifier.offset` shifts position but doesn't expand width. |
| 0.886 | `012_Padding_Huge_9999px` | 9999 px padding: Android height 10071 px, web 10031 px — 40 px drift consistent across huge paddings (likely default border-box vs content-box discrepancy × 2 sides). |

## Passing cases (3)

- `003_a`, `004_b`, `006_a` — children of `AutoMargin_Flex_PushRight` whose
  size and position happen to coincide because the first item has no margin
  and no auto.

## Parser / style-engine gaps uncovered

1. **`margin-trim: block inline` (two-keyword form) is not parsed.** Log:
   `[CSS Parser] No parser for 'margin-trim', using GenericProperty`. The
   parser at
   `src/main/kotlin/app/parsing/css/properties/longhands/spacing/MarginTrimPropertyParser.kt`
   only matches single keywords (`none|block|inline|block-start|…`). Spec
   accepts a space-separated list — add `block-and-inline` / tokenised parse
   or expand to multi-value enum.
2. **`column-gap: normal` is dropped.** `GapExtractor.extract` sends the
   string through `extractLength`, which returns `LengthValue.Unknown` and
   skips the slot. Result: Android renders 0 gap; web renders the browser
   default (~16 px in flex). Needs a `GapValue.Normal` variant or an explicit
   string branch in `GapExtractor`.
3. **`calc(…)` is silently zeroed on Android.** `SpacingResolve.resolveToDp`
   hard-codes `LengthValue.Calc -> 0.dp` with a Phase-3 TODO (line 56). This
   makes every Android padding/margin with calc() render as 0 while web
   evaluates natively. Wire `CalcExpressionEvaluator` or at least fall back
   to `pxFallback` when the converter pre-computes one.
4. **`margin-trim` applier is a documented no-op on Android.**
   `MarginTrimApplier.apply()` returns the modifier unchanged. Every
   `MarginTrim_*` case will fail the cross-platform parity bar until a
   custom Layout is written.
5. **`gap` in non-flex/non-grid context is not filtered.** Spec says `gap`
   applies only to flex/grid/multicol. `028_Gap_NonFlex_Context` shows web
   correctly ignoring gap on `display: block`. Verify Android's
   `ComponentRenderer.buildDisplayConfig` guards against this.
6. **Margin collapsing is not implemented on Android.** `MarginApplier`
   emits `Modifier.offset` for the y-axis, which does not collapse with the
   parent's `padding-top` or a sibling's `margin-bottom`. The fixture's
   `Nested_PaddingMargin_Collapse` case exposes this (SSIM 0.728). Compose
   has no built-in equivalent; would need a custom Layout or to bake
   collapse into the IR at extraction time.
7. **Negative margins widen intrinsic bounds on web only.** `MarginApplier`
   on Android uses `offset(x = left - right)` — that shifts the child but
   doesn't change its measured width. On web, negative horizontal margins
   grow the effective box. Cases 000 & 002 show this.
8. **Auto margin on a flex child doesn't push to the axis end.** Android's
   `wrapContentWidth(Alignment.End)` aligns WITHIN the child's own layout
   box, not the flex container. Compose flex emulation needs a parent-aware
   alignment (likely via `LayoutWeight` / `Arrangement.SpaceBetween`
   heuristic, or a Compose FlexBox library).
9. **`scroll-margin-*` / `scroll-padding-*` are not in the `spacing/`
   triplet at all.** They live under `testing/web/src/style/engine/scrolling/`
   (web only), with no Android / iOS counterparts. Per CLAUDE.md these
   properties are listed under the spacing category (26 IR properties).
   Either reclassify under `scrolling/` officially or port the extractors
   into the spacing triplet trees.
10. **Percentage padding/margin uses viewport width as fallback parent
    width.** `SpacingContext.parentWidthPx` defaults to null →
    `resolveRelative` falls back to `viewportWidthPx` (390 px). On nested
    components the parent is 200 px, so `padding: 25%` resolves to 97.5 px on
    Android instead of 50 px. `014_Padding_Percent_Parent` fails at 0.833.

## Snapshot hygiene

Because the test-all harness is shared across parallel audits and writes to
the same `testing/report/` directory, the snapshot we captured contains 46
spacing rows (0–45 prefixed correctly) AND 45 later speech-audit rows
(30–44). The compare script merges captures by filename; analysis scripts
should filter on the expected component-name prefixes before computing
totals. A deduplicating run that writes each audit's `report/` into its own
directory before triggering comparison would eliminate this.

## Recommendations (priority order)

1. Fix `calc()` on Android padding/margin — trivial win, unblocks any
   fixture using responsive padding. (Triplet file:
   `testing/Android/app/src/main/java/com/styleconverter/test/style/spacing/SpacingResolve.kt`
   line 56.)
2. Add `column-gap: normal` + parent-aware `%` resolution to
   `SpacingContext`. Both are small, localised fixes.
3. Extend `MarginTrimPropertyParser` to handle multi-keyword values, and
   move `MarginTrimApplier` from no-op to a proper custom-Layout-based
   implementation. (Known parser gap: space-separated `block inline`.)
4. Decide whether `scroll-margin-*` / `scroll-padding-*` belong in
   `spacing/` or `scrolling/`. Current layout only has web implementations
   under `scrolling/`, creating a silent coverage hole in the spacing audit.
5. Replace `MarginApplier`'s `Modifier.offset` approach with a wrapping
   custom Layout so (a) negative margins expand bounds, (b) auto margins
   push within the flex container, and (c) block-level margin collapsing
   becomes possible. This is the single change that would flip ~15 failing
   rows.
6. Guard `gap` at the renderer: if `display ∉ {flex, grid, multicol}`,
   drop the gap value before invoking `GapApplier`.
7. Get the iOS build unstuck before the next run — the disk I/O error on
   `testing/iOS/build/XCBuildData/build.db` reproduces after a clean; look
   at sandbox / worktree-path interactions (the build path is
   `.claude/worktrees/…` which is unusually deep).

package com.styleconverter.runtime.lists

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.renderer.composedDefaultLineHeightPx
import com.styleconverter.runtime.typography.LineHeightNormal

/**
 * The LINE BOX a synthesized `::marker` box occupies — wave 29, lane MP.
 * Two halves of one rule: WHICH box (css), and MAKING Compose report it.
 *
 * ## The defect, measured on-device (emulator-5558, composed WPT capture)
 * Per-row ink pitch of `wpt/css-counter-styles/armenian/
 * css3-counter-styles-007` — 24 single-`<li>` `<ol>`s at `font-size: 25px`:
 *
 * | platform | pitch | capture height |
 * |----------|-------|----------------|
 * | web      | 31–32 | 885 |
 * | iOS      | 31    | 855 |
 * | Android  | **34**| **920** |
 *
 * Re-feeding the SAME document with every `meta.markerText` stripped and
 * `list-style-type: none` forced — the identical `<ol>`/`<li>` stack with
 * no marker Row — gives **31**. The whole +2.75px/row is the marker box,
 * and it accumulates down the document.
 *
 * Instrumenting the Row's two children (`Modifier.layout`, logged) named
 * the term exactly:
 * ```
 *   marker  lh=31.25sp fs=25sp  h=34  baseline=27
 *   item                        h=31  baseline=26
 * ```
 * Both resolve the same 31.25px CSS line box and the marker still measures
 * 34, because **Compose floors a single line at the resolved face's natural
 * glyph box** — a smaller `lineHeight` cannot shrink it and `Trim` cannot
 * either. These markers resolve through a system FALLBACK face (Inter has
 * no Armenian/Bengali/Khmer) whose natural line is ~1.36em. The row is
 * baseline-aligned, so its height is `max(baseline) + max(height −
 * baseline)` = 27 + 7 = **34**; with the marker snapped to 31/baseline 26
 * it is 26 + 5 = **31**, the item's own height — which is what css-lists-3
 * §3.2 requires of a marker that IS the item's first inline box.
 *
 * ## Why this is a REPAIR the runtime already had, not a new invention
 * `ComponentRenderer`'s `composedLineBoxSnap` does exactly this for every
 * ITEM text run (its own comment: "Compose FLOORS a single line at the
 * font's natural glyph box … that +1px/line makes a stacked 10-bar test
 * drift ~1px/bar off the ref"). The marker `Text` was the one text run in
 * the runtime that never went through it, because it is a raw `Text` and
 * not a `PlaceholderContent`. Both halves below are byte-identical rules
 * to the item's, deliberately: marker and item must not be able to
 * disagree about the line they share.
 *
 * Pure and JVM-pinned (ListMarkerLineBoxTest) except [snap] itself, which
 * is the `Modifier` wrapper around the pinned [snappedHeightPx].
 */
object ListMarkerLineBox {

    /**
     * The browser's inherited body default font size, in sp — the same
     * bottom-out `PlaceholderContent` applies before multiplying by the
     * calibration ratio, so a marker whose font size never resolved can
     * never scale a *missing* number.
     */
    const val DEFAULT_FONT_SIZE_SP: Float = 16f

    /**
     * WHICH box: the `lineHeight` the marker's `TextStyle` must carry.
     * Mirrors `ComponentRenderer`'s `effectiveLineHeight` three-state
     * resolution argument-for-argument.
     *
     * @param declaredLineHeight the container's inheritance-merged
     *   `line-height` (`inherited.lineHeight`) — `Unspecified` when
     *   nothing declared one anywhere up the chain, which is every
     *   document in the counter-styles corpus.
     * @param fontSize the marker's resolved font size
     *   (`inherited.fontSize`); `Unspecified` falls back to
     *   [DEFAULT_FONT_SIZE_SP].
     * @param declaredNormal did the cascade DECLARE the `normal` keyword
     *   (css-fonts-4 §4.3's `font`-shorthand reset)? Then the face's own
     *   metrics ARE the CSS answer and no pin may override them.
     * @param wptCapture value of `LocalWptCaptureMode` at the call site —
     *   gates the NATURAL row exactly as it does for the item.
     * @param composedWpt value of `LocalWptComposedMode` at the call site;
     *   picks the ref's 1.25 box over the native 1.2 one.
     */
    fun resolve(
        declaredLineHeight: TextUnit,
        fontSize: TextUnit,
        declaredNormal: Boolean,
        wptCapture: Boolean,
        composedWpt: Boolean
    ): TextUnit =
        when (LineHeightNormal.lineBoxSource(
            // "Declared" is exactly what survived extraction into the
            // TextStyle — the same test the item's branch makes.
            hasDeclaredValue = declaredLineHeight != TextUnit.Unspecified,
            declaredNormal = declaredNormal,
            wptCapture = wptCapture
        )) {
            // An author value wins outright (author > us), verbatim.
            LineHeightNormal.LineBoxSource.DECLARED -> declaredLineHeight
            // Declared `normal` → stay Unspecified so Compose uses the
            // resolved face's ascent+descent, the CSS `normal` model.
            LineHeightNormal.LineBoxSource.NATURAL -> TextUnit.Unspecified
            // Nothing declared → the item's calibration, so the marker's
            // box and the item's box are the SAME box.
            LineHeightNormal.LineBoxSource.CALIBRATED ->
                composedDefaultLineHeightPx(
                    composedWpt,
                    if (fontSize != TextUnit.Unspecified) fontSize.value
                    else DEFAULT_FONT_SIZE_SP
                ).sp
        }

    /**
     * MAKING Compose report it: the height a marker run of [lineCount]
     * lines must claim, or `null` to pass its natural height through.
     *
     * `null` on a non-positive [lineCount] is the pre-settle frame — the
     * count arrives from the previous layout pass's `TextLayoutResult`, so
     * before the first one there is nothing to multiply and the natural
     * box is reported verbatim (the capture waits for layout to settle;
     * the item's snap has the identical settle behaviour).
     *
     * @param lineCount lines the marker actually laid out — READ from the
     *   layout result, never assumed to be 1: a marker that ever wraps
     *   must snap to N boxes, not one.
     * @param refLineBoxPx the resolved CSS line box in px ([resolve]'s
     *   value converted at the call site's density).
     */
    fun snappedHeightPx(lineCount: Int, refLineBoxPx: Float): Int? =
        snappedHeightPx(prefixExactPx = 0f, lineCount = lineCount,
            refLineBoxPx = refLineBoxPx)

    /**
     * The same height, but ROUNDED AGAINST THE ACCUMULATED POSITION
     * instead of in isolation — wave 30, lane 3 (fix B3). Compose-only:
     * iOS already reproduces the reference pitch.
     *
     * ## The defect (measured on the LIVE wave29-final section)
     * Every composed line box is quantised independently, so a CSS box of
     * 31.25px reports 31 on EVERY row and the error accumulates down the
     * document. Row groups of `wpt__css-counter-styles__armenian__
     * css3-counter-styles-007` (24 single-`<li>` `<ol>`s at `font-size:
     * 25px`, i.e. a 31.25px line box), first ink row of each:
     *
     * | row | web | Android | iOS |
     * |-----|-----|---------|-----|
     * | 2   | 93  | 96 (+3) | 95 (+2) |
     * | 10  | 343 | 344 (+1)| 345 (+2) |
     * | 15  | 499 | 499 (0) | 501 (+2) |
     * | 20  | 656 | 653 (−3)| 656 (0) |
     * | 25  | 812 | 808 (−4)| 813 (+1) |
     *
     * Android's OFFSET marches −0.25px per row (+3 → −4 over 23 rows)
     * while iOS holds a constant +1..+2; web renders 27 ink groups and
     * Android only 26, the last row having fallen out of the 6px the
     * document lost. iOS's pitch already averages 31.25, which is why
     * this repair is deliberately NOT mirrored.
     *
     * ## The rule
     * Round the ACCUMULATED position, not the per-row height: a row whose
     * exact line boxes above it total [prefixExactPx] claims
     * `round(prefix + n·L) − round(prefix)`. Successive 31.25px rows then
     * read 31, 32, 31, 31, … (`Math.round` is half-UP, so the 62.5
     * boundary goes to 63) and the k-th row's TOP lands on
     * `round(k · 31.25)` — the browser's own sub-pixel stacking, expressed
     * in the integer heights Compose's layout pass can carry.
     *
     * @param prefixExactPx the EXACT (unrounded) total of every line box
     *   stacked above this one in the same stacking container. 0 for the
     *   first row, which makes this identical to the isolated rounding
     *   above — so a container with one row is byte-unchanged.
     */
    fun snappedHeightPx(prefixExactPx: Float, lineCount: Int, refLineBoxPx: Float): Int? =
        if (lineCount <= 0) null
        // Rounded, not truncated, and floored at zero — the item's snap
        // spells the same expression, and a shared rounding rule is what
        // keeps the two boxes landing on the same integer pixel.
        else (Math.round(prefixExactPx + lineCount * refLineBoxPx) -
            Math.round(prefixExactPx)).coerceAtLeast(0)

    /**
     * The snap as a `Modifier`, for the marker `Text` in the row.
     *
     * COMPOSED WPT ONLY (the caller's gate) and additionally inert when
     * [refLineBoxPx] `<= 0`, i.e. there is no CSS-computed box to snap
     * into — the declared-`normal` state, where the natural box IS the
     * answer. Both exclusions return [Modifier] itself, so the dark-stage
     * 327-pair baselines and the per-component inbox path are
     * byte-identical.
     *
     * @param refLineBoxPx see [snappedHeightPx].
     * @param lineCount a LAMBDA reading the live `TextLayoutResult`, not a
     *   captured value: the layout block must re-read it on the frame it
     *   settles instead of freezing the initial 0.
     * @param prefixExactPx wave 30 (lane 3, fix B3) — given THIS row's
     *   exact line-box total, returns the exact total of every row stacked
     *   above it (see [RowPitchAccumulator]). Defaults to the constant 0,
     *   i.e. the isolated per-row rounding this function has always done,
     *   so every caller that does not opt in is byte-unchanged. Called
     *   only once the line count has settled, so a pre-settle frame never
     *   registers a 0-height row into the accumulator's order.
     */
    fun snap(
        refLineBoxPx: Float,
        // Declared BEFORE `lineCount` although it is the optional one:
        // `lineCount` must stay last so the existing trailing-lambda call
        // form `snap(31.25f) { lines }` keeps binding to it.
        prefixExactPx: (Float) -> Float = { 0f },
        lineCount: () -> Int
    ): Modifier =
        if (refLineBoxPx <= 0f) Modifier
        else Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val lines = lineCount()
            val target =
                if (lines <= 0) null
                else snappedHeightPx(
                    prefixExactPx(lines * refLineBoxPx), lines, refLineBoxPx)
            if (target == null) {
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            } else {
                layout(placeable.width, target) {
                    // Symmetric ½px trim — the glyph band stays centred in
                    // the tightened box, so no ink is clipped AND the
                    // reported first baseline moves by the same amount the
                    // item's does. That baseline agreement is what makes
                    // the baseline-aligned Row exactly one line box tall.
                    placeable.place(0, (target - placeable.height) / 2)
                }
            }
        }
}

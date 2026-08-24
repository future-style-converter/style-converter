package com.styleconverter.runtime.layout.flexbox

// Wave 25 lane CFLEX — CAL-RC5, the COMPOSE half: a wrapping flex ROW that
// implements css-flexbox-1 §8.4 cross-axis stretch against the LINE's
// cross size, which Compose's FlowRow cannot express.
//
// WHAT FlowRow GETS WRONG: `FlowRowScope.fillMaxRowHeight()` fills the
// line's height, but the line's height is the tallest ITEM's height — and
// FlowRow never grows a line to consume a definite container cross size
// (§9.4 step 8, `align-content: stretch`, the flex default). For
// css-gaps flex-gap-decorations-001/002 every child declares a width and
// no height, so on Android every line was 0px tall and the capture came
// back empty while the browser painted two 45px (resp. 50px) lines.
//
// WHY MEASURE ONCE VIA INTRINSICS: Compose forbids measuring a Measurable
// twice in one pass, and stretch needs the line's cross size BEFORE the
// item is measured. So hypothetical sizes come from the intrinsic protocol
// (§9.2.3.E max-content, the same channel FlexIntrinsicLayout and
// GridRenderer already use), lines and line cross sizes are computed from
// those, and then every item is measured exactly once with its final
// constraints.
//
// The renderer gates this layout behind "some item actually stretches"
// (ComponentRenderer.wrapRowStretchPlan) so containers that need nothing
// from it keep the frozen FlowRow path byte-for-byte.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
// The ONE guard for Compose's optional intrinsic channel — a wrapping flex
// item is an arbitrary component subtree, so both hypothetical-size reads
// below can meet NoIntrinsicsMeasurePolicy's throw (see its banner).
import com.styleconverter.runtime.layout.IntrinsicChannel

/**
 * A wrapping flex row with real per-line cross sizing.
 *
 * @param mainArrangement the SAME justify-content-plus-column-gap
 *        arrangement the non-wrapping Row uses (FlexAxes.mainHorizontal),
 *        applied per LINE across the container's main size — css-align-3
 *        §8.3: content distribution is a per-line operation.
 * @param mainGap `column-gap` — needed numerically for §9.3 line breaking
 *        because a distributing arrangement (space-between…) carries no
 *        spacing of its own.
 * @param crossGap `row-gap` — the space between lines.
 * @param cross per-item cross placement, index-aligned with [content]'s
 *        children (the renderer emits exactly one measurable per item).
 * @param containerCross the container's align-items as a Compose vertical
 *        alignment — the fallback for DEFAULT placements.
 * @param alignContentStretches whether §9.4 step 8 applies at all —
 *        css-align-3 §5.3 / css-flexbox-1 §8.4: the line-growing step is
 *        `align-content: normal | stretch` ONLY. With `center`,
 *        `flex-start`, `space-between`, … the lines keep the cross size
 *        step 7 gave them and the leftover stays free space. (Skeptic
 *        wave-25 fix: this was unconditional, so
 *        flexbox-baseline-multi-line-horiz-003/004 — `align-content:
 *        center; height: 100px` — grew their lines to fill the box where
 *        the browser leaves them content-sized. Positioning the line BLOCK
 *        for the non-stretch keywords is still unimplemented: lines stack
 *        from the cross-start edge, exactly as the FlowRow path did, so
 *        this gate is a strict no-regression narrowing. TODO(wave26).)
 */
@Composable
fun FlexWrapRow(
    mainArrangement: Arrangement.Horizontal,
    mainGap: Dp,
    crossGap: Dp,
    cross: List<FlexCrossPlacement>,
    containerCross: Alignment.Vertical,
    alignContentStretches: Boolean,
    // Wave 47 (lane Z7) — the §9.6 POSITIONING keywords (css-align-3
    // §5.3: every non-stretch align-content leaves the leftover cross
    // space free and places the line BLOCK inside it). Null keeps the
    // packed cross-start stacking byte-for-byte — every pre-wave-47
    // call site. WPT flex-gap-decorations-047…049 (row gaps created
    // purely by content distribution between lines) are the pins.
    crossDistribution: FlexWrapLines.CrossDistribution? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val mainGapPx = mainGap.roundToPx()
        val crossGapPx = crossGap.roundToPx()
        // An unbounded container cannot overflow, so nothing wraps: MAX_VALUE
        // makes §9.3 collect every item onto one line.
        val availableMain = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val n = measurables.size
        // §9.2.3.E hypothetical main size, clamped to the line budget so an
        // over-wide item takes a line to itself instead of pushing its
        // neighbours off.
        // KNOWN GAP (skeptic wave-25, corrected comment): the clamp also
        // becomes the item's measure ceiling below, so an item wider than
        // the container is SQUEEZED to the container rather than
        // overflowing it. For the default `flex-shrink: 1` that is roughly
        // right (§9.7 would shrink it too); for `flex-shrink: 0` the
        // browser overflows and this layout does not — the wrapping path
        // runs no §9.7 resolution at all, which is the same limitation the
        // FlowRow path it replaces had. TODO(wave26): feed FlexSizeResolver
        // per line, then drop the ceiling the way CAL-RC4 did for the
        // non-wrapping Row.
        // Both hypothetical reads go through IntrinsicChannel (campaign audit
        // 2026-08-10): an item whose subtree reaches a SubcomposeLayout-based
        // renderer (multicol's BoxWithConstraints, grid, scroll, …) THROWS on
        // a raw intrinsic query, and an unguarded throw here would not
        // mis-wrap a line — it would kill the whole capture composition
        // (IntrinsicChannel's banner; the wave-39 css-multicol precedent).
        val mainSizes = IntArray(n) {
            IntrinsicChannel.probe(
                logTag = "FlexWrapLayout",
                refusalContext = "css-flexbox-1 §9.2.3.E hypothetical main size " +
                    "skipped — this wrapping flex item's subtree has no intrinsic " +
                    "channel; the item takes the full line budget as its measure " +
                    "ceiling and §9.3 wraps it onto a line of its own."
            ) {
                measurables[it].maxIntrinsicWidth(Constraints.Infinity)
            }?.coerceIn(0, availableMain)
                // REFUSED ⇒ the full line budget: the item still measures once
                // below (ceiling availableMain — Infinity stays unbounded when
                // the container is unbounded) and takes its own line, instead
                // of a 0-width squeeze that would blank the whole subtree.
                ?: availableMain
        }
        // §9.4 step 7 input: each item's hypothetical CROSS size, asked for
        // at the main size it will actually be measured with (so wrapping
        // text reports the height it will really occupy).
        val hypoCross = IntArray(n) {
            IntrinsicChannel.probe(
                logTag = "FlexWrapLayout",
                refusalContext = "css-flexbox-1 §9.4 step 7 hypothetical cross size " +
                    "skipped — this wrapping flex item's subtree has no intrinsic " +
                    "channel; the line's cross size comes from the item's MEASURED " +
                    "height via the post-measure max() net instead."
            ) {
                measurables[it].maxIntrinsicHeight(mainSizes[it])
            }
                // REFUSED ⇒ 0: the "grow the line rather than clip" max() over
                // measured heights below recovers non-stretch items exactly;
                // only a STRETCH item alone on an otherwise-0 line keeps a
                // collapsed band — one mis-sized box, logged once, instead of
                // a dead composition.
                ?: 0
        }
        val lines = FlexWrapLines.breakLines(mainSizes, availableMain, mainGapPx)
        // §9.4 step 7 — a line is as tall as its tallest hypothetical item.
        val baseCross = IntArray(lines.size) { li ->
            (lines[li].first..lines[li].last).maxOf { hypoCross[it] }
        }
        // §9.4 step 8 — a DEFINITE container cross size stretches the lines,
        // but ONLY under `align-content: normal | stretch` ([alignContentStretches]).
        // `hasFixedHeight` is exactly the definite-cross signal here: the
        // container's own Modifier.height (or fillMaxHeight) hands this
        // Layout a fixed block band, while an auto-height container arrives
        // with min 0 / max canvas and must hug its lines instead.
        val definiteCross = FlexWrapLines.definiteCrossOrNull(
            alignContentStretches, constraints.hasFixedHeight, constraints.maxHeight
        )
        val lineCross = FlexWrapLines.stretchLines(baseCross, definiteCross, crossGapPx)
        // The single measure pass: stretch items take the line's cross size
        // as a FIXED band (§8.4), everything else sizes itself.
        val placeables = arrayOfNulls<Placeable>(n)
        lines.forEachIndexed { li, line ->
            for (i in line.first..line.last) {
                val stretches = cross.getOrNull(i) == FlexCrossPlacement.STRETCH
                placeables[i] = measurables[i].measure(
                    if (stretches) Constraints(0, mainSizes[i], lineCross[li], lineCross[li])
                    else Constraints(0, mainSizes[i], 0, Constraints.Infinity)
                )
            }
        }
        // A non-stretch item may still exceed the line it was assigned (its
        // measured height can beat the intrinsic estimate); grow the line
        // rather than clip the item — CSS lines are max(), never min().
        val finalCross = IntArray(lines.size) { li ->
            var h = lineCross[li]
            for (i in lines[li].first..lines[li].last) h = maxOf(h, placeables[i]!!.height)
            h
        }
        // Main-axis size: a definite width wins; otherwise the container
        // shrink-wraps its widest line (CSS shrink-to-fit).
        val widestLine = lines.maxOfOrNull { line ->
            var w = mainGapPx.toLong() * (line.count - 1)
            for (i in line.first..line.last) w += placeables[i]!!.width
            w
        } ?: 0L
        val width = if (constraints.hasFixedWidth) constraints.maxWidth else
            widestLine.coerceIn(
                constraints.minWidth.toLong(),
                if (constraints.hasBoundedWidth) constraints.maxWidth.toLong() else Int.MAX_VALUE.toLong()
            ).toInt()
        // Cross-axis size: definite band, else the stacked lines plus gaps.
        val stackedCross = finalCross.sumOf { it.toLong() } + crossGapPx.toLong() * (lines.size - 1).coerceAtLeast(0)
        val height = if (constraints.hasFixedHeight) constraints.maxHeight else
            stackedCross.coerceIn(
                constraints.minHeight.toLong(),
                if (constraints.hasBoundedHeight) constraints.maxHeight.toLong() else Int.MAX_VALUE.toLong()
            ).toInt()
        layout(width, height) {
            // Wave 47 (lane Z7) — §9.6 line-block positioning: each
            // line's cross start comes from the pure offsets helper.
            // Null distribution (or a hugging container) reproduces the
            // old `y += finalCross + gap` accumulation bit for bit, so
            // every committed capture is untouched; a positioning
            // keyword inside a definite cross band distributes the
            // leftover the way the Chromium refs paint it.
            val lineY = FlexWrapLines.lineCrossOffsets(
                lineCross = finalCross,
                // Only a definite cross band has leftover to place in —
                // same signal step 8 uses.
                containerCross = if (crossDistribution != null && constraints.hasFixedHeight)
                    constraints.maxHeight else null,
                gap = crossGapPx,
                distribution = crossDistribution
            )
            lines.forEachIndexed { li, line ->
                val y = lineY[li]
                // Delegate main-axis positioning to the container's own
                // arrangement, per line, across the container's main size —
                // justify-content therefore behaves identically on the
                // wrapping and non-wrapping paths.
                val sizes = IntArray(line.count) { placeables[line.first + it]!!.width }
                val positions = IntArray(line.count)
                with(mainArrangement) { arrange(width, sizes, layoutDirection, positions) }
                for (k in 0 until line.count) {
                    val i = line.first + k
                    val p = placeables[i]!!
                    // Cross offset INSIDE the line (§8.3): align-self wins,
                    // else the container's align-items. STRETCH already
                    // fills the line, so it sits at the line's start edge.
                    val dy = when (cross.getOrNull(i) ?: FlexCrossPlacement.DEFAULT) {
                        FlexCrossPlacement.START, FlexCrossPlacement.STRETCH -> 0
                        FlexCrossPlacement.CENTER ->
                            Alignment.CenterVertically.align(p.height, finalCross[li])
                        FlexCrossPlacement.END -> finalCross[li] - p.height
                        FlexCrossPlacement.DEFAULT -> containerCross.align(p.height, finalCross[li])
                    }
                    // Arrangement already resolved LTR/RTL coordinates, so
                    // place (not placeRelative) — same rule as the intrinsic
                    // row layout.
                    p.place(positions[k], y + dy)
                }
            }
        }
    }
}

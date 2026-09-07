package com.styleconverter.runtime.layout.flexbox

// Wave 48 lane W7 — the COLUMN twin of FlexWrapRow: `flex-flow: column
// wrap` with real per-line (per-COLUMN) cross sizing and positioning.
//
// WHAT FlowColumn GETS WRONG (measured on the wave48-cal captures):
// css-flexbox-1 §9.4 step 8 grows flex LINES into a definite container
// CROSS size under `align-content: normal | stretch`, and for a column
// container the cross axis is INLINE — the line is a column of items and
// its cross size is a WIDTH. FlowColumn sizes each column to its widest
// item and never distributes the leftover inline space, so WPT
// css-gaps/flex/flex-gap-decorations-045 (120px-wide container, two
// 50px-wide columns, `column-gap: 5px`, `align-content: stretch`)
// packed its second column at x=55 where Chromium stretches the lines
// to (120−5)/2 = 57.5px each and places it at x=62.5 (Android-web SSIM
// 0.9232, the red column rule 8px left of the browser's).
//
// The geometry rules are IDENTICAL to the row layout's on the transposed
// axis, so every pure step delegates to the same FlexWrapLines helpers
// (§9.3 breakLines, §9.4 step 8 stretchLines, §9.6 lineCrossOffsets) the
// row twin and the JVM pins already exercise — the two directions can
// never disagree about the arithmetic, only about which Compose axis it
// lands on.
//
// STILL TODO here (named, not silently skipped): wrap-reverse ordering —
// same wall as FlowLayout/FlexWrapRow; the renderer routes ONLY
// `flex-wrap: wrap` column containers here and leaves `wrap-reverse` on
// the frozen FlowColumn path (both wave48-cal wrap-reverse column tests
// pass there today). RTL line ordering is likewise unmodeled, exactly as
// in FlexWrapRow (`place`, not `placeRelative`).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
// Same guarded intrinsic channel as FlexWrapRow — a wrapping flex item is
// an arbitrary component subtree, so an unguarded intrinsic query can hit
// NoIntrinsicsMeasurePolicy's throw (see IntrinsicChannel's banner).
import com.styleconverter.runtime.layout.IntrinsicChannel

/**
 * A wrapping flex COLUMN with real per-line cross sizing: items flow
 * top→bottom, break into a new column when the next item would exceed
 * the container's height budget, and the columns line up left→right.
 *
 * @param mainArrangement justify-content + `row-gap` folded, applied per
 *        COLUMN across the container's main (block) size — css-align-3
 *        §8.3: content distribution is a per-line operation, same
 *        delegation as FlexWrapRow's per-line horizontal arrange.
 * @param mainGap `row-gap` — the ITEM gap of a column container, needed
 *        numerically for §9.3 line breaking (a distributing arrangement
 *        carries no spacing of its own).
 * @param crossGap `column-gap` — the space between the columns (lines).
 * @param cross per-item cross placement, index-aligned with [content]'s
 *        children (the renderer emits exactly one measurable per item).
 * @param containerCross the container's align-items as a Compose
 *        HORIZONTAL alignment — the fallback for DEFAULT placements
 *        (the cross axis of a column container is inline).
 * @param alignContentStretches §9.4 step 8 gate — css-align-3 §5.1:
 *        only `align-content: normal | stretch` grows the lines; every
 *        other keyword leaves the leftover free (same gate, same
 *        rationale as the row twin's parameter).
 * @param crossDistribution the §9.6 POSITIONING keywords — places the
 *        line BLOCK inside the free INLINE space. Null keeps packed
 *        cross-start stacking, which with 0 leftover is byte-identical
 *        to FlowColumn's column packing.
 */
@Composable
fun FlexWrapColumn(
    mainArrangement: Arrangement.Vertical,
    mainGap: Dp,
    crossGap: Dp,
    cross: List<FlexCrossPlacement>,
    containerCross: Alignment.Horizontal,
    alignContentStretches: Boolean,
    crossDistribution: FlexWrapLines.CrossDistribution? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val mainGapPx = mainGap.roundToPx()
        val crossGapPx = crossGap.roundToPx()
        // An unbounded container cannot overflow, so nothing wraps:
        // MAX_VALUE makes §9.3 collect every item onto one line — the
        // column twin of the row layout's unbounded-width rule (and what
        // keeps css-values calc-size-flex-008, an auto-height column
        // wrap, a single packed column exactly like FlowColumn).
        val availableMain = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        // The INLINE-axis cap: unlike the row twin's cross (block) axis,
        // widths derive from max-content measurement, and css-sizing-3
        // §5.1 bounds an auto inline size by the AVAILABLE space — an
        // unclamped max-content read (e.g. a text run, or side-by-side
        // inline-blocks) would report a line wider than the container
        // and wreck both the line geometry and the item measure below.
        // Inert for every wave48-cal routed container (explicit item
        // widths ≤ the cap throughout css-gaps 015-018/043/045).
        val crossCap = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val n = measurables.size
        // §9.2.3.E hypothetical MAIN size — the BLOCK axis here, so the
        // height intrinsic. Clamped to the line budget so an over-tall
        // item takes a column to itself instead of pushing its
        // neighbours off. Same KNOWN GAP as the row twin: the clamp is
        // also the measure ceiling, so an item taller than the container
        // is squeezed rather than overflowed (≈ the default
        // `flex-shrink: 1`; no §9.7 resolution runs on the wrap paths).
        val mainSizes = IntArray(n) {
            IntrinsicChannel.probe(
                logTag = "FlexWrapColumn",
                refusalContext = "css-flexbox-1 §9.2 step 3.E hypothetical main size " +
                    "skipped — this wrapping column-flex item's subtree has no " +
                    "intrinsic channel; the item takes the full column budget as " +
                    "its measure ceiling and §9.3 wraps it onto a line of its own."
            ) {
                // Height asked at the inline CAP, not Infinity: an item's
                // hypothetical main (block) size is its content height at
                // its used inline size, and wrapping text reports a
                // taller (true) height at the bounded width.
                measurables[it].maxIntrinsicHeight(crossCap)
            }?.coerceIn(0, availableMain)
                // REFUSED ⇒ the full column budget: the item still
                // measures once below and takes its own line, instead of
                // a 0-height squeeze that would blank the whole subtree.
                ?: availableMain
        }
        // §9.4 step 7 input: each item's hypothetical CROSS size — the
        // INLINE axis here, so the width intrinsic, asked at the height
        // the item will actually be measured with.
        val hypoCross = IntArray(n) {
            IntrinsicChannel.probe(
                logTag = "FlexWrapColumn",
                refusalContext = "css-flexbox-1 §9.4 step 7 hypothetical cross size " +
                    "skipped — this wrapping column-flex item's subtree has no " +
                    "intrinsic channel; the line's cross size comes from the item's " +
                    "MEASURED width via the post-measure max() net instead."
            ) {
                measurables[it].maxIntrinsicWidth(mainSizes[it])
            }?.coerceIn(0, crossCap)
                // REFUSED ⇒ 0: the post-measure max() below recovers
                // non-stretch items exactly; only a STRETCH item alone on
                // an otherwise-0 line keeps a collapsed band — one
                // mis-sized box, logged once, instead of a dead
                // composition (the row twin's documented trade).
                ?: 0
        }
        // §9.3 — the SAME pure line collection the row twin and the JVM
        // pins use; only the axis feeding it differs.
        val lines = FlexWrapLines.breakLines(mainSizes, availableMain, mainGapPx)
        // §9.4 step 7 — a line is as wide as its widest hypothetical item.
        val baseCross = IntArray(lines.size) { li ->
            (lines[li].first..lines[li].last).maxOf { hypoCross[it] }
        }
        // §9.4 step 8 — a DEFINITE container cross size stretches the
        // lines, ONLY under align-content normal/stretch. For a column
        // container the definite-cross signal is a fixed WIDTH band
        // (Modifier.width / fillMaxWidth on the container's own chain).
        val definiteCross = FlexWrapLines.definiteCrossOrNull(
            alignContentStretches, constraints.hasFixedWidth, constraints.maxWidth
        )
        val lineCross = FlexWrapLines.stretchLines(baseCross, definiteCross, crossGapPx)
        // The single measure pass: stretch items take the line's cross
        // size as a FIXED inline band (§8.4); everything else sizes
        // itself under the main-axis ceiling.
        val placeables = arrayOfNulls<Placeable>(n)
        lines.forEachIndexed { li, line ->
            for (i in line.first..line.last) {
                val stretches = cross.getOrNull(i) == FlexCrossPlacement.STRETCH
                placeables[i] = measurables[i].measure(
                    if (stretches) Constraints(lineCross[li], lineCross[li], 0, mainSizes[i])
                    // Non-stretch items measure under the inline CAP, not
                    // Infinity — fit-content is bounded by the available
                    // inline space (css-sizing-3 §5.1); with Infinity a
                    // text-bearing item would lay out at max-content and
                    // overflow the container the way no browser does.
                    else Constraints(0, crossCap, 0, mainSizes[i])
                )
            }
        }
        // A non-stretch item may still measure wider than its line's
        // hypothetical estimate; grow the line rather than clip — CSS
        // lines are max(), never min() (row twin, transposed).
        val finalCross = IntArray(lines.size) { li ->
            var w = lineCross[li]
            for (i in lines[li].first..lines[li].last) w = maxOf(w, placeables[i]!!.width)
            w
        }
        // Main-axis (BLOCK) size: a definite height wins; otherwise the
        // container shrink-wraps its tallest column.
        val tallestLine = lines.maxOfOrNull { line ->
            var h = mainGapPx.toLong() * (line.count - 1)
            for (i in line.first..line.last) h += placeables[i]!!.height
            h
        } ?: 0L
        val height = if (constraints.hasFixedHeight) constraints.maxHeight else
            tallestLine.coerceIn(
                constraints.minHeight.toLong(),
                if (constraints.hasBoundedHeight) constraints.maxHeight.toLong() else Int.MAX_VALUE.toLong()
            ).toInt()
        // Cross-axis (INLINE) size: definite band, else the columns side
        // by side plus gaps (CSS shrink-to-fit).
        val stackedCross = finalCross.sumOf { it.toLong() } + crossGapPx.toLong() * (lines.size - 1).coerceAtLeast(0)
        val width = if (constraints.hasFixedWidth) constraints.maxWidth else
            stackedCross.coerceIn(
                constraints.minWidth.toLong(),
                if (constraints.hasBoundedWidth) constraints.maxWidth.toLong() else Int.MAX_VALUE.toLong()
            ).toInt()
        layout(width, height) {
            // §9.6 line-block positioning on the INLINE axis — same pure
            // helper as the row twin, so the wave-47 pins cover this
            // arithmetic too. Null distribution (or a hugging container)
            // reproduces packed left-to-right stacking; with stretched
            // lines the leftover is zero and the offsets are the packed
            // accumulation bit for bit.
            val lineX = FlexWrapLines.lineCrossOffsets(
                lineCross = finalCross,
                containerCross = if (crossDistribution != null && constraints.hasFixedWidth)
                    constraints.maxWidth else null,
                gap = crossGapPx,
                distribution = crossDistribution
            )
            lines.forEachIndexed { li, line ->
                val x = lineX[li]
                // Per-column §8.2 justify-content: delegate the vertical
                // positions to the container's own arrangement across the
                // container's main size — identical behaviour to the
                // non-wrapping Column and to FlowColumn's per-column
                // arrange, which is what keeps flex-gap-decorations-043
                // (space-between, two packed columns) byte-stable.
                val sizes = IntArray(line.count) { placeables[line.first + it]!!.height }
                val positions = IntArray(line.count)
                with(mainArrangement) { arrange(height, sizes, positions) }
                for (k in 0 until line.count) {
                    val i = line.first + k
                    val p = placeables[i]!!
                    // Cross offset INSIDE the line (§8.3): align-self
                    // wins, else the container's align-items. STRETCH
                    // already fills the line, so it sits at the start.
                    val dx = when (cross.getOrNull(i) ?: FlexCrossPlacement.DEFAULT) {
                        FlexCrossPlacement.START, FlexCrossPlacement.STRETCH -> 0
                        FlexCrossPlacement.CENTER ->
                            Alignment.CenterHorizontally.align(p.width, finalCross[li], layoutDirection)
                        FlexCrossPlacement.END -> finalCross[li] - p.width
                        FlexCrossPlacement.DEFAULT ->
                            containerCross.align(p.width, finalCross[li], layoutDirection)
                    }
                    // `place`, not `placeRelative`: RTL line ordering is
                    // unmodeled here exactly as in FlexWrapRow.
                    p.place(x + dx, positions[k])
                }
            }
        }
    }
}

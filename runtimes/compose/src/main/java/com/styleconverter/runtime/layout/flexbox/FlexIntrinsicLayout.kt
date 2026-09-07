package com.styleconverter.runtime.layout.flexbox

// css-flexbox-1 §9.2 step 3.E — the wave-9 INTRINSIC-measure pass (#40).
//
// The static resolver (FlexSizeResolver) can only run ahead of composition
// when every item's flex base is a definite px value. The most common real
// flex line — `flex: 1` items with `flex-basis: auto` and NO declared main
// size — has CONTENT-SIZED bases, which are unknowable before measurement.
// The renderer used to bail to a legacy Modifier.weight fallback for those
// lines, which distributes the WHOLE axis proportionally instead of growing
// from each item's content size (visibly wrong whenever contents differ).
//
// This Layout closes the gap: at measure time it asks each content-sized
// item for its max-content main size via Compose's intrinsic-measurement
// protocol (maxIntrinsicWidth / maxIntrinsicHeight — the same channel
// GridRenderer already uses for auto tracks), uses that as the item's flex
// base per §9.2.3.E, then runs the SAME §9.7 loop the static path uses and
// measures every child at its resolved main size.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
// The ONE guard for Compose's optional intrinsic channel — a flex item is
// an arbitrary component subtree, so the content-sized base reads below can
// meet NoIntrinsicsMeasurePolicy's throw (see IntrinsicChannel's banner).
import com.styleconverter.runtime.layout.IntrinsicChannel
import kotlin.math.roundToInt

/**
 * Cross-axis placement the renderer resolved for one flex item, from
 * `align-self` (css-flexbox-1 §8.3) with `DEFAULT` deferring to the
 * container's `align-items`. STRETCH is pre-gated by the renderer (only
 * items with an AUTO cross size actually stretch).
 */
enum class FlexCrossPlacement { DEFAULT, START, CENTER, END, STRETCH }

/**
 * One non-wrapping flex ROW line with content-sized bases.
 *
 * All px-valued inputs ([contentMainPx], [gapPx], [items]) are in IR px ==
 * dp units (the renderer's convention); they are density-converted inside
 * the measure pass where real pixels are required.
 *
 * @param contentMainPx container's content-box main size (declared width
 *        minus padding/border bands) — the free-space budget for §9.7.
 * @param gapPx main-axis gap between adjacent items (column-gap).
 * @param items one [FlexSizeResolver.Item] per child in composition order;
 *        `basisPx == null` marks a content-sized base to be measured here.
 * @param cross per-child cross placement (parallel to [items]).
 * @param arrangement the SAME main-axis arrangement the static path hands
 *        to Row (justify-content + gap folded by FlexboxApplier) so both
 *        paths space and distribute identically.
 * @param containerCross the container's align-items as a Compose vertical
 *        alignment — the fallback for `DEFAULT` cross placements.
 */
@Composable
fun FlexIntrinsicRow(
    contentMainPx: Double,
    gapPx: Double,
    items: List<FlexSizeResolver.Item>,
    cross: List<FlexCrossPlacement>,
    arrangement: Arrangement.Horizontal,
    containerCross: Alignment.Vertical,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        // Fill content-sized bases from max-content intrinsics (§9.2.3.E);
        // the height hint is the bounded cross size when known so wrapping
        // text reports its unwrapped (max-content) width consistently.
        val heightHint = if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity
        val resolved = resolveWithIntrinsics(
            contentMainRealPx = contentMainPx.toFloat().dp.toPx().toDouble(),
            gapRealPx = gapPx.toFloat().dp.toPx().toDouble(),
            items = items,
            density = density,
            // Guarded read (campaign audit 2026-08-10): an item whose subtree
            // reaches a SubcomposeLayout renderer throws on the raw query and
            // would kill the whole capture composition, not just this line.
            maxContentOf = { i ->
                IntrinsicChannel.probe(
                    logTag = "FlexIntrinsicLayout",
                    refusalContext = "css-flexbox-1 §9.2 step 3.E content-sized flex " +
                        "base skipped — this flex item's subtree has no intrinsic " +
                        "channel; the base falls to 0 and §9.7 grow distributes " +
                        "the line's free space proportionally (the legacy weight " +
                        "distribution this layout replaced)."
                ) { measurables[i].maxIntrinsicWidth(heightHint) }
                    // REFUSED ⇒ 0.0: the resolver's min clamp still applies and
                    // a grow>0 item recovers a proportional share of the budget
                    // — mis-sized at worst, never a dead composition.
                    ?.toDouble() ?: 0.0
            }
        )
        // Measure each child EXACTLY at its resolved main size — fixed
        // constraints beat the child's own width modifiers and placeholder
        // floors, same as the static path's outermost Modifier.width pin.
        val placeables = measurables.mapIndexed { i, m ->
            val w = resolved[i].roundToInt().coerceAtLeast(0)
            // STRETCH fills the container cross size when bounded — the
            // same semantics as the static path's Modifier.fillMaxHeight.
            val stretch = cross.getOrNull(i) == FlexCrossPlacement.STRETCH &&
                constraints.hasBoundedHeight
            m.measure(
                if (stretch) Constraints(w, w, constraints.maxHeight, constraints.maxHeight)
                else Constraints(w, w, 0, if (constraints.hasBoundedHeight) constraints.maxHeight else Constraints.Infinity)
            )
        }
        // The line reports the container's content main size (the §9.7
        // budget) and hugs the tallest item on the cross axis — exactly
        // what the Row reported when the static path pinned widths.
        val width = contentMainPx.toFloat().dp.roundToPx()
            .coerceIn(constraints.minWidth, if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
        val height = (placeables.maxOfOrNull { it.height } ?: 0)
            .coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
        // Delegate main-axis positioning to the SAME Arrangement object the
        // Row used — spacedBy(gap) / space-between behave identically here.
        val positions = IntArray(placeables.size)
        with(arrangement) {
            arrange(width, IntArray(placeables.size) { placeables[it].width }, layoutDirection, positions)
        }
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                // Cross offset per item: align-self override, else the
                // container default (align-items). Arrangement already
                // resolved LTR coordinates, so place (not placeRelative).
                val y = when (cross.getOrNull(i) ?: FlexCrossPlacement.DEFAULT) {
                    FlexCrossPlacement.START, FlexCrossPlacement.STRETCH -> 0
                    FlexCrossPlacement.CENTER -> Alignment.CenterVertically.align(p.height, height)
                    FlexCrossPlacement.END -> height - p.height
                    FlexCrossPlacement.DEFAULT -> containerCross.align(p.height, height)
                }
                p.place(positions[i], y)
            }
        }
    }
}

/**
 * Column twin of [FlexIntrinsicRow]: the main axis is BLOCK (height), so
 * content-sized bases come from maxIntrinsicHeight at the container's
 * content width, and the cross axis is horizontal.
 */
@Composable
fun FlexIntrinsicColumn(
    contentMainPx: Double,
    gapPx: Double,
    items: List<FlexSizeResolver.Item>,
    cross: List<FlexCrossPlacement>,
    arrangement: Arrangement.Vertical,
    containerCross: Alignment.Horizontal,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        // Content height depends on the available inline size — hint with
        // the bounded container width so text wraps like the final render.
        val widthHint = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val resolved = resolveWithIntrinsics(
            contentMainRealPx = contentMainPx.toFloat().dp.toPx().toDouble(),
            gapRealPx = gapPx.toFloat().dp.toPx().toDouble(),
            items = items,
            density = density,
            // Guarded read — the block-axis twin of the row's probe: same
            // SubcomposeLayout throw hazard, same guard, same 0-base fallback.
            maxContentOf = { i ->
                IntrinsicChannel.probe(
                    logTag = "FlexIntrinsicLayout",
                    refusalContext = "css-flexbox-1 §9.2 step 3.E content-sized flex " +
                        "base (block axis) skipped — this flex item's subtree has " +
                        "no intrinsic channel; the base falls to 0 and §9.7 grow " +
                        "distributes the column's free space proportionally (the " +
                        "legacy weight distribution this layout replaced)."
                ) { measurables[i].maxIntrinsicHeight(widthHint) }
                    // REFUSED ⇒ 0.0 — see the row twin's rationale above.
                    ?.toDouble() ?: 0.0
            }
        )
        val placeables = measurables.mapIndexed { i, m ->
            val h = resolved[i].roundToInt().coerceAtLeast(0)
            // Column items never cross-stretch here: the web reference wraps
            // unsized children in `width: fit-content` (non-auto cross size),
            // so §8.3 stretch degrades to flex-start — mirrored from the
            // static path's STRETCH → Alignment.Start mapping.
            m.measure(Constraints(0, if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity, h, h))
        }
        // Cross axis hugs the widest item; main axis reports the budget.
        val height = contentMainPx.toFloat().dp.roundToPx()
            .coerceIn(constraints.minHeight, if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE)
        val width = (placeables.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE)
        val positions = IntArray(placeables.size)
        with(arrangement) {
            arrange(height, IntArray(placeables.size) { placeables[it].height }, positions)
        }
        layout(width, height) {
            placeables.forEachIndexed { i, p ->
                // Horizontal (cross) offset: align-self override, else the
                // container's align-items horizontal alignment.
                val x = when (cross.getOrNull(i) ?: FlexCrossPlacement.DEFAULT) {
                    FlexCrossPlacement.START, FlexCrossPlacement.STRETCH -> 0
                    FlexCrossPlacement.CENTER -> Alignment.CenterHorizontally.align(p.width, width, layoutDirection)
                    FlexCrossPlacement.END -> width - p.width
                    FlexCrossPlacement.DEFAULT -> containerCross.align(p.width, width, layoutDirection)
                }
                p.place(x, positions[i])
            }
        }
    }
}

/**
 * Fill content-sized bases and run the §9.7 loop, all in REAL pixels.
 *
 * Pure except for [maxContentOf] (the intrinsic query), so the fill/convert
 * logic stays unit-testable via a stubbed lambda: [items] carry dp-unit
 * values (IR px), [density] scales them into the real-pixel space the
 * measure pass works in, and content-sized bases (basisPx == null) are
 * filled from [maxContentOf] — already real px, per §9.2.3.E max-content.
 * The resolver's min/max clamps then apply inside the loop, so a measured
 * base larger than max-width (or smaller than the placeholder floor) lands
 * exactly where the spec's hypothetical-size clamp would put it.
 */
internal fun resolveWithIntrinsics(
    contentMainRealPx: Double,
    gapRealPx: Double,
    items: List<FlexSizeResolver.Item>,
    density: Float,
    maxContentOf: (Int) -> Double
): List<Double> {
    // dp→real-px conversion for the declared fields; +∞ max stays +∞.
    val filled = items.mapIndexed { i, item ->
        FlexSizeResolver.Item(
            basisPx = item.basisPx?.times(density) ?: maxContentOf(i),
            grow = item.grow,
            shrink = item.shrink,
            minPx = item.minPx * density,
            maxPx = if (item.maxPx.isFinite()) item.maxPx * density else Double.POSITIVE_INFINITY
        )
    }
    // Same §9.7 loop as the static path. resolve() only returns null for
    // empty lines / non-positive budgets now that every base is filled —
    // degrade to clamped bases so the line still lays out deterministically.
    return FlexSizeResolver.resolve(contentMainRealPx, gapRealPx, filled)
        ?: filled.map { it.basisPx!!.coerceIn(it.minPx, maxOf(it.maxPx, it.minPx)) }
}

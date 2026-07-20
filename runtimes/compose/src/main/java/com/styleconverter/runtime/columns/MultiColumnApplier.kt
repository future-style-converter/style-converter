package com.styleconverter.runtime.columns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
// mutableStateOf bridges the measure pass (which decides the fragment list) to
// the drawWithContent pass (which consumes it) — see MultiColumnDistributionLayout.
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
// drawWithContent lets the container REPLAY its child's draw once per fragment
// (clip + translate) — the css-break-3 §4 fragmentation pass.
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
// clipRect/translate are the two DrawScope transforms the fragment pass is
// built from: clip to column i's rect, translate the continuous paint by
// (+x_i, −i*H) so band i shows through (box-decoration-break: slice).
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
// Constraints.Infinity marks an unbounded block-size — no fragmentainer, so
// the fragmentation branch never engages there.
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS multi-column layout properties to Compose.
 *
 * ## CSS Properties
 * ```css
 * .article {
 *     column-count: 3;
 *     column-width: 200px;
 *     column-gap: 20px;
 *     column-rule: 1px solid gray;
 *     column-fill: balance;
 * }
 *
 * .heading {
 *     column-span: all;
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS Property | Compose Equivalent | Notes |
 * |--------------|-------------------|-------|
 * | column-count | Custom Layout | Fixed columns |
 * | column-width | BoxWithConstraints | Adaptive columns |
 * | column-gap | Arrangement.spacedBy | Gap between columns |
 * | column-rule | Custom drawing | Divider lines |
 * | column-span | Full-width Box | Span all columns |
 * | column-fill | Distribution logic | Balance/auto |
 *
 * ## Limitations
 *
 * - Content flow between columns requires custom layout
 * - Text breaking at column boundaries not automatic
 * - column-fill: balance is approximated
 * - No automatic orphan/widow control
 *
 * ## Usage
 * ```kotlin
 * MultiColumnApplier.MultiColumnLayout(
 *     config = multiColumnConfig,
 *     modifier = Modifier.fillMaxWidth()
 * ) {
 *     Text("Paragraph 1...")
 *     Text("Paragraph 2...")
 *     Text("Paragraph 3...")
 * }
 *
 * // With spanning element
 * MultiColumnApplier.MultiColumnLayout(config = config) {
 *     MultiColumnApplier.ColumnSpanningItem {
 *         Text("Full-width heading", style = MaterialTheme.typography.headlineMedium)
 *     }
 *     Text("Column content...")
 * }
 * ```
 */
object MultiColumnApplier {

    /**
     * The used multi-column values after css-multicol §3.4 fitting: the column
     * count and per-column width that actually get laid out, both guaranteed sane
     * (count >= 1, widthPx >= 0) regardless of how narrow the container is.
     */
    data class UsedColumns(
        /** Used column count — always >= 1, never more than the specified count. */
        val count: Int,
        /** Used per-column width in px — always >= 0, never a negative constraint. */
        val widthPx: Int
    )

    /**
     * Computes used column count + width per the css-multicol §3.4 pseudo-algorithm
     * for the `column-width: auto` branch: W := max(0, (available - (N-1)*gap) / N).
     *
     * We additionally REDUCE the used count until (count-1)*gap fits in the available
     * width. The strict spec pseudo-algorithm keeps N and clamps W to 0, but a run of
     * zero-width columns is meaningless in Compose (children measured at maxWidth=0
     * collapse) and — before this guard existed — the unclamped negative width
     * ((15px - 4*16px gaps) / 5 = -9 for the WPT 3d-rendering-context-and-z-ordering-003
     * #cube, width:15px + column-count:5, fixtures/wpt/css-transforms/…-003.json) was
     * passed straight into Constraints.copy(maxWidth = -9), wedging the measure pass.
     * A container narrower than its gaps therefore renders 1 column filling the
     * available width, which matches the visible result in browsers (gaps have no
     * painted extent of their own).
     *
     * @param availableWidthPx container content-box width in px (negative treated as 0)
     * @param requestedCount specified `column-count` (values < 1 are invalid per spec —
     *   column-count is a positive `<integer>` — and coerced to 1)
     * @param gapPx used `column-gap` in px (negative is invalid per css-align and
     *   treated as 0)
     */
    fun resolveUsedColumns(availableWidthPx: Int, requestedCount: Int, gapPx: Int): UsedColumns {
        // Defensive floors: negative available space lays out as zero space, negative
        // gaps are invalid CSS (css-align §8: gaps are non-negative), count is a
        // positive <integer> per css-multicol §3.2.
        val available = maxOf(0, availableWidthPx)
        val gap = maxOf(0, gapPx)
        val requested = maxOf(1, requestedCount)
        // Largest n with (n-1)*gap <= available, i.e. the gaps alone still fit; with a
        // zero gap every requested column fits by definition (also avoids div-by-zero).
        val fitting = if (gap == 0) requested else minOf(requested, available / gap + 1)
        // Never fewer than one column — CSS multicol always produces at least one box.
        val count = maxOf(1, fitting)
        // §3.4 used width: remaining space split evenly, floored at 0 (integer division
        // can still round to 0 when available barely exceeds the gaps — that is fine).
        val width = maxOf(0, (available - (count - 1) * gap) / count)
        return UsedColumns(count, width)
    }

    /**
     * The css-break-3 §2 fragmentainer block-size decision, extracted pure
     * for the JVM suite (MultiColumnFragmentationGateTest).
     *
     * ## Why TWO definiteness signals (the wave-10 integration gap)
     * A fragmentainer only exists when the multicol container's block-size
     * is DEFINITE. The original gate read ONLY `constraints.hasFixedHeight`
     * inside MultiColumnDistributionLayout — but in the REAL render tree
     * that layout sits inside [MultiColumnLayout]'s BoxWithConstraints,
     * whose Box measure policy (propagateMinConstraints = false) LOOSENS
     * minHeight to 0 before the content measures. min != max ⇒
     * hasFixedHeight is false on every real path, so the wave-10
     * fragmentation branch never engaged outside its unit tests (which
     * handed the layout synthetic TIGHT constraints directly). The fix
     * threads [containerBlockSizeDefinite] — read from the still-tight
     * INCOMING constraints at the BoxWithConstraints boundary — down to
     * this gate; `hasFixedHeight` stays as an OR so direct callers of the
     * distribution layout under tight constraints (MasonryLayout, the
     * original unit pins) keep their behaviour.
     *
     * @param containerBlockSizeDefinite `constraints.hasFixedHeight` of the
     *   INCOMING constraints at the MultiColumnLayout boundary (before Box
     *   loosening). False for callers with no such boundary.
     * @param constraints the constraints THIS layout measures with (max
     *   height survives the Box loosening; only the min is zeroed).
     * @return the fragmentainer block-size H in px, or null when no
     *   fragmentainer exists (unbounded/auto block-size grows instead of
     *   fragmenting, css-break-3 §2).
     */
    internal fun fragmentainerBlockSizePx(
        containerBlockSizeDefinite: Boolean,
        constraints: Constraints
    ): Int? {
        // The candidate block-size: the max constraint — the Box loosening
        // preserves it even while zeroing the min.
        val h = constraints.maxHeight
        // Definite iff EITHER signal says so, and the size is a real bound
        // (0-height containers have no visible fragments; Infinity means
        // unbounded — the same three checks as the wave-10 inline gate).
        val definite = (containerBlockSizeDefinite || constraints.hasFixedHeight) &&
            h > 0 && h != Constraints.Infinity
        return if (definite) h else null
    }

    // One log line per distinct fragmentation-fallback reason for the whole
    // process — keeps the no-silent-fallthrough contract without flooding
    // logcat on every measure pass (mirrors ComponentRenderer's
    // collapseFallbacksLogged precedent).
    private val fragmentationFallbacksLogged =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Logs (once per reason) that an over-tall multicol child was NOT
     * fragmented — the css-break-3 pass bailed to the legacy greedy layout.
     * runCatching guards android.util.Log for plain-JVM callers (the JUnit
     * suite exercises the pure geometry and this layout math without
     * Robolectric, where Log.i throws "not mocked").
     */
    private fun logFragmentationFallbackOnce(reason: String) {
        // add() is atomic on the synchronized set — first caller wins the log.
        if (fragmentationFallbacksLogged.add(reason)) {
            runCatching {
                android.util.Log.i(
                    "MultiColumnFragmentation",
                    "column fragmentation skipped ($reason) — over-tall child keeps the legacy unfragmented layout"
                )
            }
        }
    }

    /**
     * CompositionLocal for passing column config to children.
     */
    val LocalMultiColumnConfig = compositionLocalOf { MultiColumnConfig() }

    /**
     * CompositionLocal for column count.
     */
    val LocalColumnCount = compositionLocalOf { 1 }

    // =========================================================================
    // MULTI-COLUMN CONTAINERS
    // =========================================================================

    /**
     * A multi-column layout container.
     *
     * @param config Multi-column configuration
     * @param modifier Modifier for the container
     * @param content Items to distribute across columns
     */
    @Composable
    fun MultiColumnLayout(
        config: MultiColumnConfig,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        BoxWithConstraints(modifier = modifier) {
            val containerWidth = maxWidth
            val columnCount = config.getEffectiveColumnCount(containerWidth)
            val gap = config.columnGap ?: 16.dp

            // css-break-3 fragmentation is modeled for horizontal-tb only —
            // vertical writing modes are classified blocked-platform, so the
            // overflow pass bails (with a one-time log at the bail site in the
            // Layout below) rather than fragmenting along the wrong axis.
            val fragmentationAllowed = !config.verticalWritingMode

            // Wave-11 gate fix: the INCOMING constraints at THIS boundary are
            // still TIGHT when the container's style chain declared a fixed
            // height (min == max — the Compose signature of Modifier.height).
            // BoxWithConstraints' inner Box measure policy
            // (propagateMinConstraints = false) loosens minHeight to 0 before
            // MultiColumnDistributionLayout measures, so hasFixedHeight read
            // INSIDE that layout is always false in the real render tree —
            // the wave-10 fragmentation gate never engaged (its unit tests
            // passed on synthetic tight constraints; an integration gap).
            // Capture the definiteness HERE and thread it down to the gate
            // (fragmentainerBlockSizePx pins the OR-composition).
            val containerBlockSizeDefinite = this.constraints.hasFixedHeight

            CompositionLocalProvider(
                LocalMultiColumnConfig provides config,
                LocalColumnCount provides columnCount
            ) {
                if (config.hasRule) {
                    MultiColumnWithRules(
                        columnCount = columnCount,
                        gap = gap,
                        config = config,
                        fragmentationAllowed = fragmentationAllowed,
                        containerBlockSizeDefinite = containerBlockSizeDefinite,
                        content = content
                    )
                } else {
                    SimpleMultiColumn(
                        columnCount = columnCount,
                        gap = gap,
                        fragmentationAllowed = fragmentationAllowed,
                        containerBlockSizeDefinite = containerBlockSizeDefinite,
                        content = content
                    )
                }
            }
        }
    }

    /**
     * Simple multi-column layout without rules.
     */
    @Composable
    private fun SimpleMultiColumn(
        columnCount: Int,
        gap: Dp,
        // Threaded from MultiColumnLayout: false under vertical writing modes,
        // where the horizontal-tb fragmentation pass must bail.
        fragmentationAllowed: Boolean,
        // Wave-11: the boundary-level block-size definiteness signal (see
        // MultiColumnLayout) — the Box loosening below this point destroys
        // hasFixedHeight, so the gate needs it threaded.
        containerBlockSizeDefinite: Boolean,
        content: @Composable () -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            repeat(columnCount) { columnIndex ->
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Content will be distributed by the custom layout
                }
            }
        }

        // Use custom layout for content distribution
        MultiColumnDistributionLayout(
            columnCount = columnCount,
            gap = gap,
            fragmentationAllowed = fragmentationAllowed,
            containerBlockSizeDefinite = containerBlockSizeDefinite,
            content = content
        )
    }

    /**
     * Multi-column layout with column rules.
     */
    @Composable
    private fun MultiColumnWithRules(
        columnCount: Int,
        gap: Dp,
        config: MultiColumnConfig,
        // Threaded from MultiColumnLayout: false under vertical writing modes,
        // where the horizontal-tb fragmentation pass must bail.
        fragmentationAllowed: Boolean,
        // Wave-11: boundary-level block-size definiteness (see SimpleMultiColumn).
        containerBlockSizeDefinite: Boolean,
        content: @Composable () -> Unit
    ) {
        val ruleColor = config.ruleColor ?: Color.Gray
        val ruleWidth = config.ruleWidth ?: 1.dp
        val ruleStyle = config.ruleStyle

        MultiColumnDistributionLayout(
            columnCount = columnCount,
            gap = gap,
            fragmentationAllowed = fragmentationAllowed,
            containerBlockSizeDefinite = containerBlockSizeDefinite,
            modifier = Modifier.drawBehind {
                val gapPx = gap.toPx()
                val ruleWidthPx = ruleWidth.toPx()
                // Same css-multicol §3.4 fitting as the measure pass: the naive
                // (width - gaps) / count formula goes negative when the container is
                // narrower than its gaps, which would paint rules at negative x. Using
                // the shared resolver keeps rule positions aligned with where the
                // layout actually placed the columns (float→int rounding is at most
                // 1px, invisible for a rule line).
                val used = resolveUsedColumns(size.width.toInt(), columnCount, gapPx.toInt())
                // Non-negative used per-column width for rule x positions.
                val columnWidth = used.widthPx.toFloat()

                // Draw rules between the USED columns only — a single-column fallback
                // (used.count == 1) correctly draws no rules at all.
                for (i in 1 until used.count) {
                    // Rule sits centered in the gap after column i (css-multicol §5).
                    val x = columnWidth * i + gapPx * (i - 0.5f)

                    val pathEffect = when (ruleStyle) {
                        ColumnRuleStyle.DASHED -> PathEffect.dashPathEffect(
                            floatArrayOf(10f, 10f)
                        )
                        ColumnRuleStyle.DOTTED -> PathEffect.dashPathEffect(
                            floatArrayOf(2f, 4f)
                        )
                        else -> null
                    }

                    if (ruleStyle != ColumnRuleStyle.NONE && ruleStyle != ColumnRuleStyle.HIDDEN) {
                        drawLine(
                            color = ruleColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = ruleWidthPx,
                            pathEffect = pathEffect
                        )
                    }
                }
            },
            content = content
        )
    }

    /**
     * Custom layout that distributes content across columns, with a
     * css-break-3 §4 fragmentation pass for the over-tall single-child case.
     *
     * ## Fragmentation shape (wave-10)
     *
     * When the container has a DEFINITE block-size H and its sole child's
     * natural block-size C exceeds H, the child fragments across columns:
     * fragment i shows the child's [i*H, (i+1)*H) band in column i (geometry
     * pinned in [FragmentGeometry]). Implementation: the child measures ONCE
     * at column width with unbounded height and is placed once at the origin;
     * a container-level drawWithContent then REPLAYS that single draw once
     * per fragment through clip(column i) + translate(+x_i, −i*H). Drawing
     * the SAME laid-out child means its background color and image tiles are
     * painted as one continuous C-tall box and merely sliced — exactly the
     * box-decoration-break: slice default of css-break-3 §6.
     *
     * drawWithContent was chosen over F subcomposed wrapper boxes because the
     * Layout receives opaque measurables: subcomposition would instantiate
     * the child F times (duplicating composition state and F-times measure
     * cost) and require rewriting this Layout as a SubcomposeLayout, while a
     * draw-level replay reuses the one real child node and is a pure paint
     * transform — Compose forbids placing one Placeable twice, but a parent
     * draw modifier may invoke drawContent() any number of times.
     *
     * The pass engages ONLY for a single over-tall child (the css-break WPT
     * fixture family); multi-child overflow and vertical writing modes keep
     * the pre-existing greedy path and log once (no silent fallthrough).
     */
    @Composable
    private fun MultiColumnDistributionLayout(
        columnCount: Int,
        gap: Dp,
        modifier: Modifier = Modifier,
        // false bails the fragmentation pass (vertical writing-mode — the
        // horizontal-tb geometry above would slice along the wrong axis).
        fragmentationAllowed: Boolean = true,
        // Wave-11: true when the MultiColumnLayout boundary saw TIGHT height
        // constraints (see fragmentainerBlockSizePx — the Box between that
        // boundary and this Layout loosens minHeight to 0, so hasFixedHeight
        // is unreadable here). Default false keeps direct callers
        // (MasonryLayout, unit pins) on the legacy incoming-constraints gate.
        containerBlockSizeDefinite: Boolean = false,
        content: @Composable () -> Unit
    ) {
        // Measure→draw bridge: the measure pass below writes the fragment list
        // (empty = unfragmented), the drawWithContent modifier reads it. A
        // snapshot state so the draw pass re-runs when the list changes;
        // written only on actual change to avoid needless draw invalidations.
        val fragmentsState = remember { mutableStateOf<List<FragmentGeometry.Fragment>>(emptyList()) }
        Layout(
            content = content,
            modifier = modifier
                .fillMaxWidth()
                // The fragmentation draw pass. Sits INSIDE the caller's
                // modifier chain, so MultiColumnWithRules' drawBehind rules
                // still paint underneath the fragment slices.
                .drawWithContent {
                    // Snapshot read — establishes the draw dependency on the
                    // measure pass's decision.
                    val fragments = fragmentsState.value
                    if (fragments.isEmpty()) {
                        // Unfragmented (every current non-overflow fixture):
                        // draw children exactly as before — byte-identical.
                        drawContent()
                    } else {
                        // css-break-3 §4: replay the child's continuous paint
                        // once per fragment.
                        fragments.forEach { fragment ->
                            // Clip to column i's rect in container coordinates
                            // — this bounds the fragment AND clips the tail of
                            // a capped child (column-fill:auto overflow).
                            clipRect(
                                left = fragment.clipLeft.toFloat(),
                                top = fragment.clipTop.toFloat(),
                                right = (fragment.clipLeft + fragment.clipWidth).toFloat(),
                                bottom = (fragment.clipTop + fragment.clipHeight).toFloat()
                            ) {
                                // Shift the continuous paint by (+x_i, −i*H)
                                // so band i lands inside the clip — the slice.
                                translate(
                                    left = fragment.translateX.toFloat(),
                                    top = fragment.translateY.toFloat()
                                ) {
                                    // Draw the SAME laid-out child (placed once
                                    // at the origin) — backgrounds/tiles slice
                                    // per box-decoration-break: slice.
                                    this@drawWithContent.drawContent()
                                }
                            }
                        }
                    }
                }
        ) { measurables, constraints ->
            val gapPx = gap.roundToPx()
            // css-multicol §3.4 fitting: yields used count >= 1 and width >= 0. The
            // naive (maxWidth - totalGap) / count formula went NEGATIVE for containers
            // narrower than their gaps (WPT …z-ordering-003 #cube: 15px wide with
            // column-count:5 and the 16dp default gap → (15-64)/5 = -9) and the -9
            // maxWidth constraint wedged the measure pass.
            val used = resolveUsedColumns(constraints.maxWidth, columnCount, gapPx)
            // Per-column measure width — guaranteed non-negative by resolveUsedColumns.
            val columnWidth = used.widthPx

            // ---- Fragmentation gate (css-break-3 §4) ----
            // A fragmentainer only exists when the container's block-size is
            // DEFINITE (css-break-3 §2: column boxes are fragmentation
            // containers of fixed block-size); an unbounded/auto block-size
            // grows instead of fragmenting. Wave-11: definiteness comes from
            // the THREADED boundary signal OR these constraints' own
            // hasFixedHeight — the shared pure gate (fragmentainerBlockSizePx)
            // documents why the local read alone was structurally dead in the
            // real render tree.
            val columnBlockSize = constraints.maxHeight
            val definiteBlockSize =
                fragmentainerBlockSizePx(containerBlockSizeDefinite, constraints) != null
            if (definiteBlockSize) {
                // Probe natural block-sizes via intrinsics — non-destructive
                // (a measurable may still be measured after an intrinsic
                // query), so the identity path below stays untouched when
                // nothing overflows.
                val naturalHeights = measurables.map { it.minIntrinsicHeight(columnWidth) }
                // Which children WOULD fragment: natural size beyond H.
                val overflowing = naturalHeights.count { it > columnBlockSize }
                if (overflowing > 0 && !fragmentationAllowed) {
                    // Vertical writing-mode bail — blocked-platform, log once.
                    logFragmentationFallbackOnce("vertical writing-mode (blocked-platform)")
                } else if (overflowing > 0 && measurables.size > 1) {
                    // Multi-child fragmentation (content flow continuing from a
                    // sibling's column) is beyond the wave-10 single-child
                    // contract — keep the legacy greedy path, but say so once.
                    logFragmentationFallbackOnce("multi-child container (only single-child fragmentation is implemented)")
                } else if (overflowing == 1) {
                    // THE fragmentation branch: sole child, C > H, horizontal-tb.
                    // Measure ONCE at column width with unbounded block-size so
                    // the child lays out (and paints) as one continuous C-tall
                    // box — the slice source.
                    val placeable = measurables[0].measure(
                        constraints.copy(
                            minWidth = 0,
                            maxWidth = columnWidth,
                            minHeight = 0,
                            maxHeight = Constraints.Infinity
                        )
                    )
                    // Geometry from the MEASURED height (the true laid-out C;
                    // intrinsics only gated entry) — pinned by the shared
                    // S-table in FragmentGeometryTest.
                    val fragments = FragmentGeometry.fragmentGeometry(
                        childBlockSizePx = placeable.height,
                        columnBlockSizePx = columnBlockSize,
                        columnWidthPx = columnWidth,
                        columnGapPx = gapPx,
                        columnCount = used.count
                    )
                    // Publish for the draw pass (write-on-change only).
                    if (fragmentsState.value != fragments) fragmentsState.value = fragments
                    // The container itself stays exactly H tall (min==max==H
                    // anyway) and full width, like the legacy path.
                    return@Layout layout(constraints.maxWidth, columnBlockSize) {
                        // Place the child ONCE at the origin; every visible
                        // copy comes from the drawWithContent replay above.
                        placeable.place(0, 0)
                    }
                }
                // overflowing == 0 falls through to the legacy path unchanged.
            }
            // Leaving the fragmentation branch (or never entering it): make
            // sure a stale fragment list from a previous size doesn't keep
            // slicing the now-fitting content.
            if (fragmentsState.value.isNotEmpty()) fragmentsState.value = emptyList()

            // Measure all children with column width constraint
            val placeables = measurables.map { measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = 0,
                        maxWidth = columnWidth
                    )
                )
            }

            // Distribute items to columns — the greedy min-height heuristic,
            // extracted PURE into MultiColumnDistribution (lane
            // ios-multichild-multicol) so the JVM suite and the iOS mirror pin
            // the identical assignments. Sized by the USED count so we never
            // allocate (or greedily fill) columns that don't fit.
            val childHeights = placeables.map { it.height }
            // One slot (columnIndex, yOffset) per child, in child order —
            // same choice rule + tie-break as the pre-extraction inline loop.
            val slots = MultiColumnDistribution.distribute(childHeights, used.count)
            // Container block-size = the tallest column (== the old
            // columnHeights.maxOrNull), derived from the same slots.
            val maxHeight = MultiColumnDistribution.containerBlockSizePx(childHeights, slots)

            layout(constraints.maxWidth, maxHeight) {
                // Place column-major (all of column 0, then column 1, …) — the
                // exact placement (= paint) order of the pre-extraction loop,
                // so any overlapping content keeps its draw order unchanged.
                for (columnIndex in 0 until used.count) {
                    // Inline position derives from the used geometry, not the
                    // distribution: column i starts at i * (width + gap).
                    val x = columnIndex * (columnWidth + gapPx)
                    slots.forEachIndexed { index, slot ->
                        // Within a column, insertion order == child order —
                        // identical to the old per-column item lists.
                        if (slot.columnIndex == columnIndex) {
                            placeables[index].place(x, slot.yOffsetPx)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // COLUMN-SPANNING ITEMS
    // =========================================================================

    /**
     * An item that spans all columns (column-span: all).
     *
     * @param modifier Modifier for the spanning item
     * @param content Content that spans all columns
     */
    @Composable
    fun ColumnSpanningItem(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = modifier.fillMaxWidth()
        ) {
            content()
        }
    }

    // =========================================================================
    // SIMPLE COLUMN LAYOUTS
    // =========================================================================

    /**
     * A simple row-based multi-column layout for fixed items.
     *
     * @param config Multi-column configuration
     * @param items List of items to display
     * @param modifier Modifier for the container
     * @param itemContent Content for each item
     */
    @Composable
    fun <T> SimpleColumnGrid(
        config: MultiColumnConfig,
        items: List<T>,
        modifier: Modifier = Modifier,
        itemContent: @Composable (T) -> Unit
    ) {
        BoxWithConstraints(modifier = modifier) {
            val columnCount = config.getEffectiveColumnCount(maxWidth)
            val gap = config.columnGap ?: 16.dp
            val rowCount = (items.size + columnCount - 1) / columnCount

            Column(
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                repeat(rowCount) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        repeat(columnCount) { columnIndex ->
                            val itemIndex = rowIndex * columnCount + columnIndex
                            Box(modifier = Modifier.weight(1f)) {
                                if (itemIndex < items.size) {
                                    itemContent(items[itemIndex])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A masonry-style column layout (sequential fill).
     *
     * @param columnCount Number of columns
     * @param gap Gap between columns and items
     * @param modifier Modifier for the container
     * @param content Items to distribute
     */
    @Composable
    fun MasonryLayout(
        columnCount: Int,
        gap: Dp = 16.dp,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        MultiColumnDistributionLayout(
            columnCount = columnCount,
            gap = gap,
            modifier = modifier,
            content = content
        )
    }

    // =========================================================================
    // COLUMN RULE DRAWING
    // =========================================================================

    /**
     * Modifier to draw a column rule (divider).
     *
     * @param color Rule color
     * @param width Rule width
     * @param style Rule style
     * @return Modifier with rule drawing
     */
    fun Modifier.columnRule(
        color: Color,
        width: Dp = 1.dp,
        style: ColumnRuleStyle = ColumnRuleStyle.SOLID
    ): Modifier = this.drawBehind {
        val widthPx = width.toPx()
        val x = size.width / 2

        val pathEffect = when (style) {
            ColumnRuleStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            ColumnRuleStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
            else -> null
        }

        if (style != ColumnRuleStyle.NONE && style != ColumnRuleStyle.HIDDEN) {
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = widthPx,
                pathEffect = pathEffect
            )
        }
    }

    /**
     * A vertical divider for use between columns.
     *
     * @param color Divider color
     * @param width Divider width
     * @param style Divider style
     * @param modifier Modifier for the divider
     */
    @Composable
    fun ColumnDivider(
        color: Color = Color.Gray,
        width: Dp = 1.dp,
        style: ColumnRuleStyle = ColumnRuleStyle.SOLID,
        modifier: Modifier = Modifier
    ) {
        val pathEffect = when (style) {
            ColumnRuleStyle.DASHED -> PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
            ColumnRuleStyle.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
            else -> null
        }

        if (style != ColumnRuleStyle.NONE && style != ColumnRuleStyle.HIDDEN) {
            Spacer(
                modifier = modifier
                    .width(width)
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(
                            color = color,
                            start = Offset(size.width / 2, 0f),
                            end = Offset(size.width / 2, size.height),
                            strokeWidth = size.width,
                            pathEffect = pathEffect
                        )
                    }
            )
        }
    }

    // =========================================================================
    // UTILITIES
    // =========================================================================

    /**
     * Calculate adaptive column count based on container width.
     *
     * @param containerWidth Available width
     * @param minColumnWidth Minimum column width
     * @param gap Gap between columns
     * @return Optimal column count
     */
    fun calculateAdaptiveColumnCount(
        containerWidth: Dp,
        minColumnWidth: Dp,
        gap: Dp = 16.dp
    ): Int {
        if (minColumnWidth.value <= 0) return 1

        val availableWidth = containerWidth.value
        val minWidth = minColumnWidth.value
        // Negative gaps are invalid CSS (css-align §8) and, unguarded, could zero the
        // divisor below (minWidth + gap == 0 → Infinity.toInt()) — floor at 0.
        val gapValue = maxOf(0f, gap.value)

        // Formula: (width + gap) / (minWidth + gap)
        return maxOf(1, ((availableWidth + gapValue) / (minWidth + gapValue)).toInt())
    }

    // =========================================================================
    // NOTES
    // =========================================================================

    object Notes {
        const val CONTENT_FLOW = """
            CSS multi-column automatically flows text between columns,
            breaking at word/character boundaries.

            Compose doesn't have automatic text flow between columns.
            For newspaper-style layouts, consider:
            1. Pre-splitting text into column-sized chunks
            2. Using a custom layout that measures and distributes
            3. Using FlowRow/FlowColumn for item-based layouts
        """

        const val COLUMN_BALANCING = """
            CSS column-fill: balance tries to equalize column heights.

            Our implementation uses a simple greedy algorithm:
            - Place each item in the shortest column
            - This approximates balanced distribution

            For true text balancing, you'd need to:
            1. Measure total content height
            2. Divide by column count
            3. Break content at those points
        """

        const val COLUMN_SPAN = """
            CSS column-span: all makes an element span all columns.

            In our implementation, ColumnSpanningItem creates a
            full-width box that interrupts the column flow.

            For proper spanning, you'd need to:
            1. Render columns up to the spanning element
            2. Render the spanning element
            3. Start new columns for remaining content
        """

        const val COLUMN_RULES = """
            CSS column-rule creates vertical lines between columns.

            Supported styles:
            - solid: Continuous line
            - dashed: Dashed line
            - dotted: Dotted line

            Not fully supported:
            - double, groove, ridge, inset, outset
            (These fallback to solid)
        """
    }
}

package com.styleconverter.test.style.columns

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
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

            CompositionLocalProvider(
                LocalMultiColumnConfig provides config,
                LocalColumnCount provides columnCount
            ) {
                if (config.hasRule) {
                    MultiColumnWithRules(
                        columnCount = columnCount,
                        gap = gap,
                        config = config,
                        content = content
                    )
                } else {
                    SimpleMultiColumn(
                        columnCount = columnCount,
                        gap = gap,
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
        content: @Composable () -> Unit
    ) {
        val ruleColor = config.ruleColor ?: Color.Gray
        val ruleWidth = config.ruleWidth ?: 1.dp
        val ruleStyle = config.ruleStyle

        MultiColumnDistributionLayout(
            columnCount = columnCount,
            gap = gap,
            modifier = Modifier.drawBehind {
                val gapPx = gap.toPx()
                val ruleWidthPx = ruleWidth.toPx()
                val columnWidth = (size.width - gapPx * (columnCount - 1)) / columnCount

                // Draw rules between columns
                for (i in 1 until columnCount) {
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
     * Custom layout that distributes content across columns.
     */
    @Composable
    private fun MultiColumnDistributionLayout(
        columnCount: Int,
        gap: Dp,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        Layout(
            content = content,
            modifier = modifier.fillMaxWidth()
        ) { measurables, constraints ->
            val gapPx = gap.roundToPx()
            val totalGap = gapPx * (columnCount - 1)
            val columnWidth = (constraints.maxWidth - totalGap) / columnCount

            // Measure all children with column width constraint
            val placeables = measurables.map { measurable ->
                measurable.measure(
                    constraints.copy(
                        minWidth = 0,
                        maxWidth = columnWidth
                    )
                )
            }

            // Distribute items to columns (balanced distribution)
            val columnHeights = IntArray(columnCount)
            val columnItems = Array(columnCount) { mutableListOf<Pair<Placeable, Int>>() }

            placeables.forEachIndexed { index, placeable ->
                // Find column with minimum height
                val targetColumn = columnHeights.indices.minByOrNull { columnHeights[it] } ?: 0
                columnItems[targetColumn].add(placeable to columnHeights[targetColumn])
                columnHeights[targetColumn] += placeable.height
            }

            val maxHeight = columnHeights.maxOrNull() ?: 0

            layout(constraints.maxWidth, maxHeight) {
                columnItems.forEachIndexed { columnIndex, items ->
                    val x = columnIndex * (columnWidth + gapPx)
                    items.forEach { (placeable, y) ->
                        placeable.place(x, y)
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
        val gapValue = gap.value

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

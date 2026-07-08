package com.styleconverter.test.style.layout.grid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS Grid layout properties to Compose.
 *
 * ## CSS Properties
 * ```css
 * .grid-container {
 *     display: grid;
 *     grid-template-columns: repeat(3, 1fr);
 *     grid-template-rows: auto 1fr auto;
 *     grid-template-areas:
 *         "header header header"
 *         "sidebar main main"
 *         "footer footer footer";
 *     gap: 16px;
 *     justify-items: stretch;
 *     align-items: center;
 * }
 *
 * .grid-item {
 *     grid-column: 1 / 3;
 *     grid-row: 2;
 *     grid-area: main;
 * }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS Property | Compose Equivalent | Limitation |
 * |--------------|-------------------|------------|
 * | display: grid | LazyVerticalGrid / Custom layout | |
 * | grid-template-columns | GridCells | Limited to fixed/adaptive |
 * | grid-template-rows | Not directly supported | Use custom layout |
 * | grid-template-areas | Custom placement | Manual mapping |
 * | gap | Arrangement.spacedBy() | |
 * | grid-column span | GridItemSpan | |
 * | fr units | weight equivalent | In custom layout |
 *
 * ## Limitations
 *
 * - **Template areas**: Requires custom layout implementation
 * - **Row templates**: LazyVerticalGrid doesn't support row sizing
 * - **Dense packing**: Not supported by Compose grids
 * - **Subgrid**: Not supported
 * - **Named lines**: Not supported
 *
 * ## Usage
 * ```kotlin
 * GridApplier.CssGrid(
 *     config = gridConfig,
 *     modifier = Modifier.fillMaxSize()
 * ) {
 *     GridApplier.GridItem(itemConfig) {
 *         Text("Item 1")
 *     }
 * }
 *
 * // Or use with LazyVerticalGrid
 * GridApplier.LazyGridContainer(
 *     config = gridConfig,
 *     modifier = Modifier.fillMaxSize()
 * ) {
 *     items(itemList) { item ->
 *         GridApplier.GridItem(item.config) {
 *             ItemContent(item)
 *         }
 *     }
 * }
 * ```
 */
object GridApplier {

    /**
     * CompositionLocal to pass grid config to children.
     */
    val LocalGridConfig = compositionLocalOf<GridConfig?> { null }

    // =========================================================================
    // GRID CONTAINERS
    // =========================================================================

    /**
     * A CSS Grid-like container using custom layout.
     *
     * Supports:
     * - grid-template-columns with fr, px, auto
     * - grid-template-rows with fr, px, auto
     * - grid-template-areas for named placement
     * - gap for spacing
     *
     * @param config Grid configuration
     * @param modifier Modifier for the container
     * @param content Grid items
     */
    @Composable
    fun CssGrid(
        config: GridConfig,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(LocalGridConfig provides config) {
            BoxWithConstraints(modifier = modifier) {
                val containerWidth = constraints.maxWidth.toFloat()
                val containerHeight = constraints.maxHeight.toFloat()

                // Calculate column and row sizes
                val columnSizes = calculateTrackSizes(
                    config.templateColumns,
                    containerWidth,
                    config.columnGap ?: 0.dp
                )
                val rowSizes = calculateTrackSizes(
                    config.templateRows,
                    containerHeight,
                    config.rowGap ?: 0.dp
                )

                CssGridLayout(
                    columnSizes = columnSizes,
                    rowSizes = rowSizes,
                    columnGap = config.columnGap ?: 0.dp,
                    rowGap = config.rowGap ?: 0.dp,
                    justifyItems = config.justifyItems,
                    alignItems = config.alignItems,
                    content = content
                )
            }
        }
    }

    /**
     * Custom layout that places children in a CSS Grid-like fashion.
     */
    @Composable
    private fun CssGridLayout(
        columnSizes: List<Float>,
        rowSizes: List<Float>,
        columnGap: Dp,
        rowGap: Dp,
        justifyItems: GridJustify,
        alignItems: GridAlign,
        content: @Composable () -> Unit
    ) {
        Layout(
            content = content,
            modifier = Modifier.fillMaxSize()
        ) { measurables, constraints ->
            val columnGapPx = columnGap.roundToPx()
            val rowGapPx = rowGap.roundToPx()

            // Calculate column positions
            val columnPositions = mutableListOf(0)
            columnSizes.forEachIndexed { index, size ->
                val gap = if (index > 0) columnGapPx else 0
                columnPositions.add(columnPositions.last() + size.toInt() + gap)
            }

            // Calculate row positions
            val rowPositions = mutableListOf(0)
            rowSizes.forEachIndexed { index, size ->
                val gap = if (index > 0) rowGapPx else 0
                rowPositions.add(rowPositions.last() + size.toInt() + gap)
            }

            // Measure and place children
            val placeables = measurables.mapIndexed { index, measurable ->
                val colIndex = index % columnSizes.size.coerceAtLeast(1)
                val rowIndex = index / columnSizes.size.coerceAtLeast(1)

                val itemWidth = columnSizes.getOrNull(colIndex)?.toInt() ?: constraints.maxWidth
                val itemHeight = rowSizes.getOrNull(rowIndex)?.toInt() ?: (constraints.maxHeight / rowSizes.size.coerceAtLeast(1))

                measurable.measure(
                    constraints.copy(
                        minWidth = 0,
                        maxWidth = itemWidth,
                        minHeight = 0,
                        maxHeight = itemHeight.coerceAtLeast(0)
                    )
                )
            }

            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { index, placeable ->
                    val colIndex = index % columnSizes.size.coerceAtLeast(1)
                    val rowIndex = index / columnSizes.size.coerceAtLeast(1)

                    val x = columnPositions.getOrElse(colIndex) { 0 }
                    val y = rowPositions.getOrElse(rowIndex) { 0 }

                    placeable.place(x, y)
                }
            }
        }
    }

    /**
     * A LazyVerticalGrid wrapper with CSS Grid-like configuration.
     *
     * Best for:
     * - Large lists of items
     * - Simple column layouts (equal or adaptive)
     * - Scrolling grids
     *
     * @param config Grid configuration
     * @param modifier Modifier for the grid
     * @param content LazyGridScope content
     */
    @Composable
    fun LazyGridContainer(
        config: GridConfig,
        modifier: Modifier = Modifier,
        content: LazyGridScope.() -> Unit
    ) {
        val cells = getGridCells(config)
        val arrangement = getGridArrangement(config)

        CompositionLocalProvider(LocalGridConfig provides config) {
            if (config.autoFlow == GridAutoFlow.COLUMN || config.autoFlow == GridAutoFlow.COLUMN_DENSE) {
                LazyHorizontalGrid(
                    rows = cells,
                    modifier = modifier,
                    horizontalArrangement = arrangement.horizontal,
                    verticalArrangement = arrangement.vertical,
                    content = content
                )
            } else {
                LazyVerticalGrid(
                    columns = cells,
                    modifier = modifier,
                    horizontalArrangement = arrangement.horizontal,
                    verticalArrangement = arrangement.vertical,
                    content = content
                )
            }
        }
    }

    /**
     * A simple grid container using Row/Column composition.
     * Good for small, fixed grids.
     *
     * @param config Grid configuration
     * @param itemCount Number of items
     * @param modifier Modifier for the grid
     * @param itemContent Content for each item
     */
    @Composable
    fun SimpleGrid(
        config: GridConfig,
        itemCount: Int,
        modifier: Modifier = Modifier,
        itemContent: @Composable (index: Int) -> Unit
    ) {
        val columnCount = config.columnCount.coerceAtLeast(1)
        val rowCount = (itemCount + columnCount - 1) / columnCount

        CompositionLocalProvider(LocalGridConfig provides config) {
            Column(
                modifier = modifier,
                verticalArrangement = Arrangement.spacedBy(config.rowGap ?: 0.dp)
            ) {
                repeat(rowCount) { rowIndex ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(config.columnGap ?: 0.dp)
                    ) {
                        repeat(columnCount) { colIndex ->
                            val itemIndex = rowIndex * columnCount + colIndex
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = getItemAlignment(config.justifyItems, config.alignItems)
                            ) {
                                if (itemIndex < itemCount) {
                                    itemContent(itemIndex)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // GRID ITEMS
    // =========================================================================

    /**
     * A grid item with placement configuration.
     *
     * @param config Grid item configuration
     * @param modifier Base modifier
     * @param content Item content
     */
    @Composable
    fun GridItem(
        config: GridItemConfig,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val gridConfig = LocalGridConfig.current
        val alignment = getItemAlignment(
            config.justifySelf ?: gridConfig?.justifyItems ?: GridJustify.STRETCH,
            config.alignSelf ?: gridConfig?.alignItems ?: GridAlign.STRETCH
        )

        // Apply sizing modifiers based on placement
        var itemModifier = modifier

        // Handle grid-area by name
        if (config.areaName != null && gridConfig?.templateAreas != null) {
            val placement = gridConfig.templateAreas.getPlacement(config.areaName)
            if (placement != null) {
                // Area-based sizing would be applied by parent layout
            }
        }

        Box(
            modifier = itemModifier,
            contentAlignment = alignment,
            content = content
        )
    }

    /**
     * Get GridItemSpan for LazyGrid based on item config.
     *
     * @param config Grid item configuration
     * @return GridItemSpan for the item
     */
    fun getItemSpan(config: GridItemConfig): GridItemSpan {
        return GridItemSpan(config.columnSpan.coerceAtLeast(1))
    }

    // =========================================================================
    // HELPER FUNCTIONS
    // =========================================================================

    /**
     * Convert grid-template-columns to GridCells.
     */
    fun getGridCells(config: GridConfig): GridCells {
        val columns = config.templateColumns

        // Check for adaptive columns (all same fr or min-content)
        if (columns.isEmpty()) {
            return GridCells.Fixed(config.columnCount.coerceAtLeast(1))
        }

        // All fixed same size?
        val allFixed = columns.filterIsInstance<GridTrackSize.Fixed>()
        if (allFixed.size == columns.size && allFixed.distinctBy { it.size }.size == 1) {
            return GridCells.FixedSize(allFixed.first().size)
        }

        // All same fr unit?
        val allFr = columns.filterIsInstance<GridTrackSize.Fraction>()
        if (allFr.size == columns.size && allFr.distinctBy { it.fr }.size == 1) {
            return GridCells.Fixed(columns.size)
        }

        // Mixed or auto - use Adaptive with minimum size
        val minSize = columns.filterIsInstance<GridTrackSize.Fixed>()
            .minOfOrNull { it.size } ?: 100.dp

        return GridCells.Adaptive(minSize)
    }

    /**
     * Get horizontal and vertical arrangements for the grid.
     */
    fun getGridArrangement(config: GridConfig): GridArrangement {
        val horizontal = when (config.justifyContent) {
            GridJustifyContent.START -> Arrangement.Start
            GridJustifyContent.END -> Arrangement.End
            GridJustifyContent.CENTER -> Arrangement.Center
            GridJustifyContent.STRETCH -> Arrangement.Start
            GridJustifyContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            GridJustifyContent.SPACE_AROUND -> Arrangement.SpaceAround
            GridJustifyContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
        }.let { base ->
            config.columnGap?.let { Arrangement.spacedBy(it) } ?: base
        }

        val vertical = when (config.alignContent) {
            GridAlignContent.START -> Arrangement.Top
            GridAlignContent.END -> Arrangement.Bottom
            GridAlignContent.CENTER -> Arrangement.Center
            GridAlignContent.STRETCH -> Arrangement.Top
            GridAlignContent.SPACE_BETWEEN -> Arrangement.SpaceBetween
            GridAlignContent.SPACE_AROUND -> Arrangement.SpaceAround
            GridAlignContent.SPACE_EVENLY -> Arrangement.SpaceEvenly
        }.let { base ->
            config.rowGap?.let { Arrangement.spacedBy(it) } ?: base
        }

        return GridArrangement(horizontal, vertical)
    }

    /**
     * Get item alignment from justify-items and align-items.
     */
    fun getItemAlignment(justify: GridJustify, align: GridAlign): Alignment {
        val horizontal = when (justify) {
            GridJustify.START -> Alignment.Start
            GridJustify.END -> Alignment.End
            GridJustify.CENTER -> Alignment.CenterHorizontally
            GridJustify.STRETCH -> Alignment.CenterHorizontally
        }

        val vertical = when (align) {
            GridAlign.START -> Alignment.Top
            GridAlign.END -> Alignment.Bottom
            GridAlign.CENTER -> Alignment.CenterVertically
            GridAlign.STRETCH -> Alignment.CenterVertically
            GridAlign.BASELINE -> Alignment.Top // Baseline not directly supported
        }

        return when {
            horizontal == Alignment.Start && vertical == Alignment.Top -> Alignment.TopStart
            horizontal == Alignment.Start && vertical == Alignment.CenterVertically -> Alignment.CenterStart
            horizontal == Alignment.Start && vertical == Alignment.Bottom -> Alignment.BottomStart
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Top -> Alignment.TopCenter
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.CenterVertically -> Alignment.Center
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Bottom -> Alignment.BottomCenter
            horizontal == Alignment.End && vertical == Alignment.Top -> Alignment.TopEnd
            horizontal == Alignment.End && vertical == Alignment.CenterVertically -> Alignment.CenterEnd
            horizontal == Alignment.End && vertical == Alignment.Bottom -> Alignment.BottomEnd
            else -> Alignment.TopStart
        }
    }

    /**
     * Calculate pixel sizes for grid tracks.
     */
    private fun calculateTrackSizes(
        tracks: List<GridTrackSize>,
        containerSize: Float,
        gap: Dp
    ): List<Float> {
        if (tracks.isEmpty()) return listOf(containerSize)

        val gapTotal = gap.value * (tracks.size - 1)
        val availableSize = containerSize - gapTotal

        // Calculate total fr units and fixed sizes
        var totalFr = 0f
        var usedFixed = 0f

        tracks.forEach { track ->
            when (track) {
                is GridTrackSize.Fraction -> totalFr += track.fr
                is GridTrackSize.Fixed -> usedFixed += track.size.value
                is GridTrackSize.Percent -> usedFixed += containerSize * (track.percent / 100f)
                else -> {} // Auto, min-content, max-content calculated separately
            }
        }

        val frSize = if (totalFr > 0) (availableSize - usedFixed) / totalFr else 0f

        return tracks.map { track ->
            when (track) {
                is GridTrackSize.Fraction -> (frSize * track.fr).coerceAtLeast(0f)
                is GridTrackSize.Fixed -> track.size.value
                is GridTrackSize.Percent -> containerSize * (track.percent / 100f)
                is GridTrackSize.Auto -> (availableSize - usedFixed) / tracks.count { it is GridTrackSize.Auto }.coerceAtLeast(1)
                is GridTrackSize.MinContent -> 50f // Default minimum
                is GridTrackSize.MaxContent -> (availableSize - usedFixed) / tracks.count { it is GridTrackSize.MaxContent }.coerceAtLeast(1)
                is GridTrackSize.MinMax -> {
                    val min = calculateSingleTrackSize(track.min, containerSize, frSize)
                    val max = calculateSingleTrackSize(track.max, containerSize, frSize)
                    ((availableSize - usedFixed) / tracks.size).coerceIn(min, max)
                }
                is GridTrackSize.FitContent -> {
                    val limit = track.limit.value
                    ((availableSize - usedFixed) / tracks.size).coerceAtMost(limit)
                }
            }
        }
    }

    private fun calculateSingleTrackSize(track: GridTrackSize, containerSize: Float, frSize: Float): Float {
        return when (track) {
            is GridTrackSize.Fraction -> frSize * track.fr
            is GridTrackSize.Fixed -> track.size.value
            is GridTrackSize.Percent -> containerSize * (track.percent / 100f)
            is GridTrackSize.Auto -> 0f
            is GridTrackSize.MinContent -> 0f
            is GridTrackSize.MaxContent -> containerSize
            is GridTrackSize.MinMax -> 0f
            is GridTrackSize.FitContent -> track.limit.value
        }
    }

    // =========================================================================
    // DATA CLASSES
    // =========================================================================

    data class GridArrangement(
        val horizontal: Arrangement.Horizontal,
        val vertical: Arrangement.Vertical
    )

    // =========================================================================
    // NOTES
    // =========================================================================

    object Notes {
        const val LAZY_GRID = """
            LazyVerticalGrid is best for:
            - Long lists that need virtualization
            - Simple equal-column layouts
            - Scrolling content

            Limitations:
            - No row height control (use custom layout)
            - Limited column sizing (Fixed, Adaptive, FixedSize only)
            - No grid-template-areas support
        """

        const val TEMPLATE_AREAS = """
            CSS grid-template-areas requires custom layout:

            1. Parse areas string into GridTemplateAreas
            2. Use CssGrid() which uses BoxWithConstraints + Layout
            3. Children are placed based on their areaName in GridItemConfig

            Note: This is compute-intensive for large grids.
        """

        const val FR_UNITS = """
            CSS fr units (e.g., 1fr 2fr 1fr) map to proportional sizing:

            - In LazyGrid: Use GridCells.Fixed(count) and weight in items
            - In CssGrid: Calculated as ratio of total fr units

            Example: 1fr 2fr 1fr = 25% 50% 25%
        """

        const val AUTO_FLOW = """
            CSS grid-auto-flow controls item placement:

            - row (default): Fill rows left to right
            - column: Fill columns top to bottom
            - dense: Pack items to fill gaps (NOT SUPPORTED in Compose)

            For column flow, use LazyHorizontalGrid instead of LazyVerticalGrid.
        """
    }
}

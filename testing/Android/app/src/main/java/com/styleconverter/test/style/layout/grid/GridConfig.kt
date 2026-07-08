package com.styleconverter.test.style.layout.grid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Configuration for a CSS grid container.
 * Maps CSS grid container properties to Compose-compatible values.
 */
data class GridConfig(
    val templateColumns: List<GridTrackSize> = emptyList(),
    val templateRows: List<GridTrackSize> = emptyList(),
    val templateAreas: GridTemplateAreas? = null,
    val autoColumns: GridTrackSize? = null,
    val autoRows: GridTrackSize? = null,
    val autoFlow: GridAutoFlow = GridAutoFlow.ROW,
    val rowGap: Dp? = null,
    val columnGap: Dp? = null,
    val justifyItems: GridJustify = GridJustify.STRETCH,
    val alignItems: GridAlign = GridAlign.STRETCH,
    val justifyContent: GridJustifyContent = GridJustifyContent.START,
    val alignContent: GridAlignContent = GridAlignContent.START
) {
    /** Whether this config represents an actual grid layout */
    val hasGrid: Boolean
        get() = templateColumns.isNotEmpty() || templateRows.isNotEmpty() || templateAreas != null

    /** Number of columns in the grid (at least 1) */
    val columnCount: Int
        get() = templateAreas?.columnCount ?: templateColumns.size.coerceAtLeast(1)

    /** Number of rows in the grid */
    val rowCount: Int
        get() = templateAreas?.rowCount ?: templateRows.size.coerceAtLeast(1)

    /** Whether grid-template-areas is defined */
    val hasTemplateAreas: Boolean
        get() = templateAreas != null
}

/**
 * Represents CSS grid-template-areas.
 *
 * Example CSS:
 * ```css
 * grid-template-areas:
 *   "header header header"
 *   "sidebar main main"
 *   "footer footer footer";
 * ```
 *
 * Stored as a 2D structure where each named area can be looked up
 * to find its grid placement (row/column start and end).
 */
data class GridTemplateAreas(
    /** The areas grid as rows of area names. "." represents an empty cell. */
    val grid: List<List<String>>,
    /** Computed area placements for quick lookup */
    val areaMap: Map<String, GridAreaPlacement>
) {
    val rowCount: Int get() = grid.size
    val columnCount: Int get() = grid.firstOrNull()?.size ?: 0

    /** Get the placement for a named area */
    fun getPlacement(areaName: String): GridAreaPlacement? = areaMap[areaName]

    /** Check if an area name exists */
    fun hasArea(areaName: String): Boolean = areaMap.containsKey(areaName)

    companion object {
        /** Parse grid-template-areas from a list of row strings */
        fun parse(rows: List<String>): GridTemplateAreas {
            val grid = rows.map { row ->
                row.trim().split(Regex("\\s+"))
            }

            // Build area map by finding bounds of each named area
            val areaMap = mutableMapOf<String, GridAreaPlacement>()
            grid.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { colIndex, areaName ->
                    if (areaName != ".") {
                        val existing = areaMap[areaName]
                        if (existing == null) {
                            areaMap[areaName] = GridAreaPlacement(
                                rowStart = rowIndex + 1,
                                rowEnd = rowIndex + 2,
                                columnStart = colIndex + 1,
                                columnEnd = colIndex + 2
                            )
                        } else {
                            // Expand the area to include this cell
                            areaMap[areaName] = GridAreaPlacement(
                                rowStart = minOf(existing.rowStart, rowIndex + 1),
                                rowEnd = maxOf(existing.rowEnd, rowIndex + 2),
                                columnStart = minOf(existing.columnStart, colIndex + 1),
                                columnEnd = maxOf(existing.columnEnd, colIndex + 2)
                            )
                        }
                    }
                }
            }

            return GridTemplateAreas(grid, areaMap)
        }
    }
}

/**
 * Grid area placement with 1-based line numbers (CSS convention).
 */
data class GridAreaPlacement(
    val rowStart: Int,
    val rowEnd: Int,
    val columnStart: Int,
    val columnEnd: Int
) {
    /** Number of rows this area spans */
    val rowSpan: Int get() = rowEnd - rowStart

    /** Number of columns this area spans */
    val columnSpan: Int get() = columnEnd - columnStart
}

/**
 * Grid track sizing.
 * Maps CSS grid track size values like fr, px, min-content, etc.
 */
sealed interface GridTrackSize {
    /** Fixed size in dp (from px, pt, etc.) */
    data class Fixed(val size: Dp) : GridTrackSize

    /** Fractional unit (fr) - takes a fraction of available space */
    data class Fraction(val fr: Float) : GridTrackSize

    /** Percentage of container size */
    data class Percent(val percent: Float) : GridTrackSize

    /** Size based on minimum content size */
    data object MinContent : GridTrackSize

    /** Size based on maximum content size */
    data object MaxContent : GridTrackSize

    /** Automatic sizing based on content */
    data object Auto : GridTrackSize

    /** minmax(min, max) - size between min and max */
    data class MinMax(val min: GridTrackSize, val max: GridTrackSize) : GridTrackSize

    /** fit-content(limit) - like auto but capped at limit */
    data class FitContent(val limit: Dp) : GridTrackSize
}

/**
 * CSS grid-auto-flow property values.
 */
enum class GridAutoFlow {
    ROW,
    COLUMN,
    ROW_DENSE,
    COLUMN_DENSE
}

/**
 * CSS justify-items property values for grid.
 */
enum class GridJustify {
    START, END, CENTER, STRETCH
}

/**
 * CSS align-items property values for grid.
 */
enum class GridAlign {
    START, END, CENTER, STRETCH, BASELINE
}

/**
 * CSS justify-content property values for grid.
 */
enum class GridJustifyContent {
    START, END, CENTER, STRETCH, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY
}

/**
 * CSS align-content property values for grid.
 */
enum class GridAlignContent {
    START, END, CENTER, STRETCH, SPACE_BETWEEN, SPACE_AROUND, SPACE_EVENLY
}

/**
 * Grid item placement configuration.
 * Maps CSS grid item properties for positioning within the grid.
 * Uses simple Int for basic line-based positioning.
 */
data class GridItemConfig(
    val columnStart: Int? = null,
    val columnEnd: Int? = null,
    val rowStart: Int? = null,
    val rowEnd: Int? = null,
    val columnSpan: Int = 1,
    val rowSpan: Int = 1,
    val justifySelf: GridJustify? = null,
    val alignSelf: GridAlign? = null,
    val areaName: String? = null
)

/**
 * CSS grid repeat count value.
 */
sealed interface GridRepeatCount {
    data class Number(val count: Int) : GridRepeatCount
    data object AutoFill : GridRepeatCount
    data object AutoFit : GridRepeatCount
}

/**
 * CSS grid line placement value.
 */
sealed interface GridLinePlacement {
    data object Auto : GridLinePlacement
    data class LineNumber(val line: Int) : GridLinePlacement
    data class LineName(val name: String) : GridLinePlacement
    data class Span(val count: Int, val lineName: String? = null) : GridLinePlacement
}

/**
 * Grid template track with optional repeat.
 */
data class GridTemplateTrack(
    val repeatCount: GridRepeatCount?,
    val sizes: List<GridTrackSize>
)

/**
 * Grid area result from parsing grid-area.
 */
sealed interface GridAreaResult {
    data class Named(val name: String) : GridAreaResult
    data class Placement(
        val rowStart: GridLinePlacement,
        val columnStart: GridLinePlacement,
        val rowEnd: GridLinePlacement,
        val columnEnd: GridLinePlacement
    ) : GridAreaResult
}

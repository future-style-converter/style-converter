package com.styleconverter.test.style.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies CSS table properties to Compose layouts.
 *
 * ## CSS Properties
 * ```css
 * table {
 *     display: table;
 *     table-layout: fixed;
 *     border-collapse: collapse;
 *     border-spacing: 2px 4px;
 *     caption-side: bottom;
 *     empty-cells: hide;
 * }
 *
 * tr { display: table-row; }
 * td { display: table-cell; }
 * ```
 *
 * ## Compose Mapping
 *
 * | CSS | Compose | Notes |
 * |-----|---------|-------|
 * | display: table | Column + Row | Custom composition |
 * | table-layout: fixed | Equal weights | All columns same width |
 * | border-collapse | Border merging | Custom drawing |
 * | border-spacing | Arrangement.spacedBy | Gap between cells |
 * | caption-side | Column ordering | Caption above/below |
 * | empty-cells | Conditional visibility | Hide empty boxes |
 *
 * ## Limitations
 *
 * - No native table element in Compose
 * - Complex colspan/rowspan requires custom layout
 * - border-collapse requires custom border drawing
 * - Fixed layout with intrinsic sizing is approximated
 *
 * ## Usage
 * ```kotlin
 * TableApplier.Table(
 *     config = tableConfig,
 *     modifier = Modifier.fillMaxWidth()
 * ) {
 *     TableRow {
 *         TableCell { Text("Header 1") }
 *         TableCell { Text("Header 2") }
 *     }
 *     TableRow {
 *         TableCell { Text("Data 1") }
 *         TableCell { Text("Data 2") }
 *     }
 * }
 * ```
 */
object TableApplier {

    /**
     * CompositionLocal for passing table config to cells.
     */
    val LocalTableConfig = compositionLocalOf { TableConfig() }

    /**
     * CompositionLocal for border color (used in collapse mode).
     */
    val LocalTableBorderColor = compositionLocalOf { Color.Black }

    /**
     * CompositionLocal for border width.
     */
    val LocalTableBorderWidth = compositionLocalOf { 1.dp }

    // =========================================================================
    // TABLE CONTAINER
    // =========================================================================

    /**
     * A table container that applies CSS table properties.
     *
     * @param config Table configuration
     * @param caption Optional caption content
     * @param borderColor Border color for cells
     * @param borderWidth Border width for cells
     * @param modifier Modifier for the table
     * @param content Table rows
     */
    @Composable
    fun Table(
        config: TableConfig,
        caption: (@Composable () -> Unit)? = null,
        borderColor: Color = Color.Black,
        borderWidth: Dp = 1.dp,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            LocalTableConfig provides config,
            LocalTableBorderColor provides borderColor,
            LocalTableBorderWidth provides borderWidth
        ) {
            Column(modifier = modifier) {
                // Caption at top
                if (caption != null && config.captionSide == CaptionSide.TOP) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        caption()
                    }
                    Spacer(modifier = Modifier.height(config.effectiveSpacingVertical))
                }

                // Table body with vertical spacing
                Column(
                    verticalArrangement = if (config.borderCollapse == BorderCollapse.SEPARATE) {
                        Arrangement.spacedBy(config.effectiveSpacingVertical)
                    } else {
                        Arrangement.Top
                    }
                ) {
                    content()
                }

                // Caption at bottom
                if (caption != null && config.captionSide == CaptionSide.BOTTOM) {
                    Spacer(modifier = Modifier.height(config.effectiveSpacingVertical))
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        caption()
                    }
                }
            }
        }
    }

    /**
     * A scrollable table for large datasets.
     *
     * @param config Table configuration
     * @param horizontalScroll Enable horizontal scrolling
     * @param verticalScroll Enable vertical scrolling
     * @param modifier Modifier for the table
     * @param content Table rows
     */
    @Composable
    fun ScrollableTable(
        config: TableConfig,
        horizontalScroll: Boolean = true,
        verticalScroll: Boolean = false,
        borderColor: Color = Color.Black,
        borderWidth: Dp = 1.dp,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        var tableModifier = modifier
        if (horizontalScroll) {
            tableModifier = tableModifier.horizontalScroll(rememberScrollState())
        }
        if (verticalScroll) {
            tableModifier = tableModifier.verticalScroll(rememberScrollState())
        }

        Table(
            config = config,
            borderColor = borderColor,
            borderWidth = borderWidth,
            modifier = tableModifier,
            content = content
        )
    }

    // =========================================================================
    // TABLE ROW
    // =========================================================================

    /**
     * A table row container.
     *
     * @param modifier Modifier for the row
     * @param content Row cells
     */
    @Composable
    fun TableRow(
        modifier: Modifier = Modifier,
        content: @Composable RowScope.() -> Unit
    ) {
        val config = LocalTableConfig.current

        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = if (config.borderCollapse == BorderCollapse.SEPARATE) {
                Arrangement.spacedBy(config.effectiveSpacingHorizontal)
            } else {
                Arrangement.Start
            }
        ) {
            content()
        }
    }

    /**
     * A table header row with optional background.
     *
     * @param backgroundColor Background color for header
     * @param modifier Modifier for the row
     * @param content Header cells
     */
    @Composable
    fun TableHeaderRow(
        backgroundColor: Color = Color.LightGray,
        modifier: Modifier = Modifier,
        content: @Composable RowScope.() -> Unit
    ) {
        TableRow(
            modifier = modifier.background(backgroundColor),
            content = content
        )
    }

    // =========================================================================
    // TABLE CELL
    // =========================================================================

    /**
     * A table cell.
     *
     * @param weight Column weight (for table-layout: fixed, use equal weights)
     * @param isEmpty Whether this cell is empty (for empty-cells: hide)
     * @param modifier Modifier for the cell
     * @param content Cell content
     */
    @Composable
    fun RowScope.TableCell(
        weight: Float = 1f,
        isEmpty: Boolean = false,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val config = LocalTableConfig.current
        val borderColor = LocalTableBorderColor.current
        val borderWidth = LocalTableBorderWidth.current

        // Handle empty-cells: hide
        if (isEmpty && config.emptyCells == EmptyCells.HIDE) {
            Spacer(modifier = Modifier.weight(weight))
            return
        }

        val cellModifier = when (config.layout) {
            TableLayout.FIXED -> Modifier.weight(weight)
            TableLayout.AUTO -> Modifier
        }

        val borderModifier = when (config.borderCollapse) {
            BorderCollapse.SEPARATE -> {
                Modifier.border(borderWidth, borderColor)
            }
            BorderCollapse.COLLAPSE -> {
                // Draw only right and bottom borders to avoid double borders
                Modifier.drawBehind {
                    val strokeWidth = borderWidth.toPx()
                    // Right border
                    drawLine(
                        color = borderColor,
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = strokeWidth
                    )
                    // Bottom border
                    drawLine(
                        color = borderColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = strokeWidth
                    )
                }
            }
        }

        Box(
            modifier = modifier
                .then(cellModifier)
                .fillMaxHeight()
                .then(borderModifier)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart,
            content = content
        )
    }

    /**
     * A table header cell with bold styling hint.
     *
     * @param weight Column weight
     * @param modifier Modifier for the cell
     * @param content Cell content
     */
    @Composable
    fun RowScope.TableHeaderCell(
        weight: Float = 1f,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        TableCell(
            weight = weight,
            modifier = modifier,
            content = content
        )
    }

    // =========================================================================
    // FIXED-WIDTH TABLE
    // =========================================================================

    /**
     * A table with fixed column widths.
     *
     * @param columnWidths List of column widths
     * @param config Table configuration
     * @param modifier Modifier for the table
     * @param content Table rows
     */
    @Composable
    fun FixedWidthTable(
        columnWidths: List<Dp>,
        config: TableConfig = TableConfig(layout = TableLayout.FIXED),
        borderColor: Color = Color.Black,
        borderWidth: Dp = 1.dp,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            LocalTableConfig provides config.copy(layout = TableLayout.FIXED),
            LocalTableBorderColor provides borderColor,
            LocalTableBorderWidth provides borderWidth,
            LocalColumnWidths provides columnWidths
        ) {
            Column(
                modifier = modifier,
                verticalArrangement = if (config.borderCollapse == BorderCollapse.SEPARATE) {
                    Arrangement.spacedBy(config.effectiveSpacingVertical)
                } else {
                    Arrangement.Top
                }
            ) {
                content()
            }
        }
    }

    /**
     * CompositionLocal for fixed column widths.
     */
    val LocalColumnWidths = compositionLocalOf<List<Dp>> { emptyList() }

    /**
     * A row for fixed-width tables.
     */
    @Composable
    fun FixedWidthRow(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        val config = LocalTableConfig.current

        Row(
            modifier = modifier
                .height(IntrinsicSize.Min),
            horizontalArrangement = if (config.borderCollapse == BorderCollapse.SEPARATE) {
                Arrangement.spacedBy(config.effectiveSpacingHorizontal)
            } else {
                Arrangement.Start
            }
        ) {
            content()
        }
    }

    /**
     * A cell with fixed width.
     *
     * @param columnIndex Index of the column (for width lookup)
     * @param fallbackWidth Width if column index not found
     * @param modifier Modifier for the cell
     * @param content Cell content
     */
    @Composable
    fun FixedWidthCell(
        columnIndex: Int,
        fallbackWidth: Dp = 100.dp,
        modifier: Modifier = Modifier,
        content: @Composable BoxScope.() -> Unit
    ) {
        val config = LocalTableConfig.current
        val columnWidths = LocalColumnWidths.current
        val borderColor = LocalTableBorderColor.current
        val borderWidth = LocalTableBorderWidth.current

        val cellWidth = columnWidths.getOrNull(columnIndex) ?: fallbackWidth

        val borderModifier = when (config.borderCollapse) {
            BorderCollapse.SEPARATE -> Modifier.border(borderWidth, borderColor)
            BorderCollapse.COLLAPSE -> Modifier.drawBehind {
                val strokeWidth = borderWidth.toPx()
                drawLine(
                    color = borderColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidth
                )
            }
        }

        Box(
            modifier = modifier
                .width(cellWidth)
                .fillMaxHeight()
                .then(borderModifier)
                .padding(8.dp),
            contentAlignment = Alignment.CenterStart,
            content = content
        )
    }

    // =========================================================================
    // COLLAPSED BORDER HELPERS
    // =========================================================================

    /**
     * Apply collapsed border styling to the first row (add top border).
     */
    fun Modifier.firstRowBorder(
        borderColor: Color,
        borderWidth: Dp
    ): Modifier = this.drawBehind {
        val strokeWidth = borderWidth.toPx()
        // Top border
        drawLine(
            color = borderColor,
            start = Offset(0f, 0f),
            end = Offset(size.width, 0f),
            strokeWidth = strokeWidth
        )
        // Left border
        drawLine(
            color = borderColor,
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = strokeWidth
        )
    }

    /**
     * Apply collapsed border styling to the first cell (add left border).
     */
    fun Modifier.firstCellBorder(
        borderColor: Color,
        borderWidth: Dp
    ): Modifier = this.drawBehind {
        val strokeWidth = borderWidth.toPx()
        // Left border
        drawLine(
            color = borderColor,
            start = Offset(0f, 0f),
            end = Offset(0f, size.height),
            strokeWidth = strokeWidth
        )
    }

    // =========================================================================
    // NOTES
    // =========================================================================

    object Notes {
        const val TABLE_LAYOUT = """
            CSS table-layout controls column sizing:

            - auto (default): Column widths based on content
            - fixed: First row determines all column widths

            In Compose, we approximate:
            - auto: Let content determine width (no weight)
            - fixed: Use equal weights or specified widths
        """

        const val BORDER_COLLAPSE = """
            CSS border-collapse controls cell borders:

            - separate (default): Each cell has its own border with spacing
            - collapse: Adjacent borders merge into one

            In Compose, we simulate collapse by:
            - Drawing only right/bottom borders on most cells
            - Adding left/top borders on first row/column
            - Using zero spacing between cells
        """

        const val LIMITATIONS = """
            Compose table limitations:

            1. No colspan/rowspan: Use Row/Column nesting or Box with absolute positioning
            2. No automatic column sizing: Must specify weights or widths
            3. No native border-collapse: Custom drawing required
            4. No sticky headers: Use LazyColumn with stickyHeader
            5. No accessibility table semantics: Add custom semantics
        """

        const val DATA_TABLE = """
            For data tables with many rows, consider:

            1. LazyColumn for virtualization
            2. Horizontal scroll for wide tables
            3. Sticky header row
            4. Sort/filter capabilities

            Example:
            LazyColumn {
                stickyHeader { TableHeaderRow { ... } }
                items(data) { item -> TableRow { ... } }
            }
        """
    }
}

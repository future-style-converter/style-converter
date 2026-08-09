package com.styleconverter.runtime.table

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

    /**
     * May a cell paint the DEMO default border in the SEPARATE model?
     *
     * ## What this switches off, and why it has to exist
     * [TableCell] paints `Modifier.border(borderWidth, borderColor)` —
     * 1dp solid black by default — on every cell of a `border-collapse:
     * separate` table. That border has NO CSS basis: CSS 2.1 §17.6.1's
     * separated model says each cell paints ITS OWN borders and the table
     * paints nothing between them, and the wire already carries the cell's
     * declared `BorderTopWidth`/`…Color`/… which `RenderComponent` applies
     * to the cell component itself (wave 32 — a `table-cell` renders
     * through the BLOCK path). The default is a demo affordance from this
     * applier's public API, kept so a hand-written
     * `TableApplier.Table { TableRow { TableCell { … } } }` still looks
     * like a table.
     *
     * It was harmless while no real HTML `<table>` reached this applier.
     * The wave-38 UA display fold routed every bare `<table>` here, and
     * the fabrication immediately became the dominant ink error on the
     * shapes the fold otherwise renders CORRECTLY: a `<td>` takes the
     * composed-WPT block-fill width, so the fabricated border draws as a
     * full-canvas-width black rectangle around content the reference shows
     * unadorned. MEASURED on the frozen wave38-final Android captures —
     * `CSS2/css21-errata/s-11-1-1b-003` 0.9805 → 0.9280, `-004`
     * 0.9805 → 0.9280, `-007` 0.9817 → 0.9328,
     * `css-display/display-contents-td-001` 0.9666 → 0.9190,
     * `css-text-decor/text-decoration-propagation-05` 0.9712 → 0.9471 —
     * five frozen Android passes lost to one fabricated rectangle.
     *
     * ONLY the separated model is switched: the COLLAPSE branch's
     * right/bottom strokes stand for §17.6.2's table-owned collapsed
     * border, which is a real (if still placeholder-coloured) box-model
     * feature, and the wave-38 collapsed-border gains
     * (`collapsed-border-positioned-tr-td` f→P, `border-conflict-resolution`
     * 0.6453 → 0.8007, `border-collapse-empty-cell` 0.9073 → 0.9290) were
     * measured WITH those strokes.
     *
     * Default `true` keeps every existing call site — the demo API and
     * every declared-`display: table` box in the frozen corpus — byte
     * identical.
     */
    val LocalTableFabricatedCellBorder = compositionLocalOf { true }

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
     * @param fabricatedCellBorder Whether cells may paint the DEMO default
     *   border in the separated model — see [LocalTableFabricatedCellBorder]
     *   for the five measured Android captures that made this a parameter.
     *   `true` (the default) is the frozen behaviour.
     * @param shrinkToFit CSS 2.1 §17.5.2 auto table width — see the
     *   `Modifier.width(IntrinsicSize.Max)` banner inside. `false` (the
     *   default) is the frozen behaviour for every existing call site.
     * @param modifier Modifier for the table
     * @param content Table rows
     */
    @Composable
    fun Table(
        config: TableConfig,
        caption: (@Composable () -> Unit)? = null,
        borderColor: Color = Color.Black,
        borderWidth: Dp = 1.dp,
        fabricatedCellBorder: Boolean = true,
        shrinkToFit: Boolean = false,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        CompositionLocalProvider(
            LocalTableConfig provides config,
            LocalTableBorderColor provides borderColor,
            LocalTableBorderWidth provides borderWidth,
            LocalTableFabricatedCellBorder provides fabricatedCellBorder
        ) {
            // Wave 39 (lane A6) — CSS 2.1 §17.5.2 auto table width, ENFORCED.
            //
            // TableBoxTree.shrinkToFitBox already stops the table BOX from
            // taking ComponentRenderer's composed-WPT block-fill channel
            // (`blockFlowWidth`), but that only removes one `fillMaxWidth`.
            // [TableRow] below adds its OWN, unconditionally — and a
            // `fillMaxWidth` child makes its parent Column take the incoming
            // max constraint, so the table stretched to the full composed
            // canvas anyway. The guard was therefore inert: the fill simply
            // moved one level down.
            //
            // MEASURED on the frozen wave38-final Android captures. The
            // reference for `css-tables/background-clip-001` is a 100×100
            // green square (a 40×40 inline-block inside 30px collapsed
            // borders); Android painted a ~358×100 bar — the full canvas
            // content width — while iOS painted the square and passed.
            // Same picture, same cause: `box-shadow-001` (where the stretch
            // also exposed the red decoy the square should cover, 0.8897),
            // `anonymous-table-cell-margin-collapsing` (0.8891), and
            // `height-distribution/extra-height-given-to-all-row-groups-00{1,2,5}`
            // (0.8882 ×3) against iOS passes of 0.9541–0.9974.
            //
            // `Modifier.width(IntrinsicSize.Max)` is the exact CSS primitive:
            // Compose measures the content's max intrinsic width — for this
            // Column that is the widest row, i.e. the sum of the columns'
            // max-content widths — and `IntrinsicSizeModifier` enforces the
            // INCOMING constraints on top of it, so the result is
            // `min(max-content, available)`: §17.5.2's used width. The rows'
            // own `fillMaxWidth` then fills THAT instead of the canvas, which
            // is also right — §17.5.2 sizes a row to the table's used width.
            //
            // Chained AFTER the caller's `modifier` so the table's own
            // background/border (applied inside it) paints at the constrained
            // width rather than the canvas width — the visible half of the
            // same defect.
            val tableModifier =
                if (shrinkToFit) modifier.width(IntrinsicSize.Max) else modifier
            Column(modifier = tableModifier) {
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

                // Table body with vertical spacing, inside §17.6.1's OUTER
                // band.
                //
                // Wave 34 (lane T) — CSS 2.1 §17.6.1: "the distance between
                // the border of the table box and the borders of the cells
                // on the edge of the table is the border-spacing". Before
                // this the runtime spent border-spacing only BETWEEN rows
                // and cells, so a table's first cell sat flush against the
                // table's own edge — half of why Android painted
                // abspos-container-change-dynamic-001's lime abspos at
                // x=30 instead of the reference's 33.
                //
                // `effectiveSpacing*` is 0 for the collapsing model and for
                // every table whose used spacing is 0, and the band is then
                // skipped entirely rather than applied as `padding(0.dp)` —
                // no extra layout node, so those tables stay byte-identical.
                val bandH = config.effectiveSpacingHorizontal
                val bandV = config.effectiveSpacingVertical
                val bodyModifier = if (bandH > 0.dp || bandV > 0.dp) {
                    Modifier.padding(horizontal = bandH, vertical = bandV)
                } else {
                    Modifier
                }
                Column(
                    modifier = bodyModifier,
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
                // CSS 2.1 §17.6.1: in the separated model a cell paints only
                // its OWN declared borders, which the wire carries and
                // `RenderComponent` applies to the cell component itself.
                // This stroke is the applier's demo default; a caller that
                // owns real cell styling opts out through
                // `TableApplier.Table(fabricatedCellBorder = false)`. See
                // LocalTableFabricatedCellBorder for the measured captures.
                if (LocalTableFabricatedCellBorder.current) {
                    Modifier.border(borderWidth, borderColor)
                } else {
                    Modifier
                }
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
                .then(borderModifier),
            // Wave 32 (lane P) — the fabricated `.padding(8.dp)` that used
            // to sit here is GONE. It had no CSS basis: the HTML UA sheet
            // gives `td { padding: 1px }`, and the wire already carries that
            // as the cell's own PaddingTop/Right/Bottom/Left, which the cell
            // now applies itself (a `table-cell` renders through the BLOCK
            // path from this wave on — see TableBoxTree.rendersAsBlockContainer).
            // The 8dp was therefore a pure DOUBLE-COUNT, and it compounds
            // across a row: it inflated every preceding cell by 16dp and then
            // offset the current cell's content by another 8. MEASURED on
            // css-tables/abspos-container-change-dynamic-001, whose lime
            // abspos landed at [54,33] against the reference's [33,18] —
            // exactly the (+21,+15) the two paddings plus the inflated first
            // cell predict.
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

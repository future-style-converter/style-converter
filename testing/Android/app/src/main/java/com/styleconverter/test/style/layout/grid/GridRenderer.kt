package com.styleconverter.test.style.layout.grid

import com.styleconverter.test.style.core.renderer.ComponentRenderer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.test.style.core.ir.IRComponent
import com.styleconverter.test.style.core.ir.IRProperty
import com.styleconverter.test.style.core.types.ValueExtractors
import com.styleconverter.test.style.layout.grid.GridExtractor
import com.styleconverter.test.style.layout.grid.GridTemplateAreas
import kotlinx.serialization.json.*

/**
 * Handles CSS Grid layout properties.
 *
 * ## Supported Properties
 * - grid-template-columns: fixed, fr, auto, repeat(), minmax()
 * - grid-template-rows: fixed, fr, auto
 * - grid-gap / gap: spacing between grid items
 * - grid-column-start/end: item placement
 * - grid-row-start/end: item placement
 * - grid-auto-flow: row, column, dense, row dense, column dense
 * - grid-auto-columns: sizes for implicitly created columns (px, fr, min-content, max-content, auto, fit-content, minmax)
 * - grid-auto-rows: sizes for implicitly created rows (px, fr, min-content, max-content, auto, fit-content, minmax)
 *
 * ## Compose Mapping
 * - CSS Grid -> Non-lazy Column/Row grid (to work inside LazyColumn)
 * - fr units -> proportional widths via weight
 * - auto-fill/auto-fit -> fixed column count
 *
 * ## Limitations
 * - Complex track sizing (minmax with both values) limited
 * - Explicit row sizing partially supported
 */
object GridRenderer {

    /**
     * Render a grid container with its children.
     * Uses non-lazy Column/Row to avoid crashes when nested in LazyColumn.
     * Supports grid-template-areas for named area placement.
     */
    @Composable
    fun RenderGrid(
        component: IRComponent,
        modifier: Modifier,
        displayConfig: ComponentRenderer.DisplayConfig,
        textColor: Color?
    ) {
        val gridConfig = extractGridConfig(component.properties)
        val templateAreas = extractGridTemplateAreas(component.properties)

        if (component.children.isNullOrEmpty()) {
            // No children - render placeholder
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                PlaceholderContent(component.name, textColor)
            }
            return
        }

        val horizontalSpacing = displayConfig.columnGap
        val verticalSpacing = displayConfig.rowGap

        // Check if we should use template areas for placement
        if (templateAreas != null) {
            RenderGridWithTemplateAreas(
                component = component,
                modifier = modifier,
                templateAreas = templateAreas,
                rowHeights = gridConfig.rowHeights,
                horizontalSpacing = horizontalSpacing,
                verticalSpacing = verticalSpacing,
                textColor = textColor
            )
            return
        }

        // Standard grid rendering (no template areas)
        val columnCount = gridConfig.columnCount ?: 2

        // Use non-lazy grid implementation to work inside LazyColumn
        // Sort children by order property, then split into rows
        val sortedChildren = ComponentRenderer.sortByOrder(component.children)
        val rows = sortedChildren.chunked(columnCount)
        val rowHeights = gridConfig.rowHeights

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            rows.forEachIndexed { rowIndex, rowChildren ->
                // Get row height: use explicit height if available, otherwise use default
                val rowHeight = rowHeights?.getOrNull(rowIndex)

                Row(
                    modifier = if (rowHeight != null) {
                        Modifier.fillMaxWidth().height(rowHeight)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                ) {
                    rowChildren.forEach { child ->
                        // Extract justify-self and align-self for individual item alignment
                        val justifySelf = ComponentRenderer.extractJustifySelf(child.properties)
                        val alignSelf = extractAlignSelf(child.properties)

                        // Calculate content alignment from justify-self and align-self
                        val contentAlignment = getContentAlignment(justifySelf, alignSelf)

                        Box(
                            modifier = if (rowHeight != null) {
                                Modifier.weight(1f).fillMaxHeight()
                            } else {
                                Modifier.weight(1f)
                            },
                            contentAlignment = contentAlignment
                        ) {
                            ComponentRenderer.RenderComponent(child)
                        }
                    }
                    // Fill remaining space if row is not full
                    repeat(columnCount - rowChildren.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    /**
     * Render a grid using grid-template-areas for named area placement.
     *
     * Children are placed according to their grid-area property matching
     * the template area names.
     */
    @Composable
    private fun RenderGridWithTemplateAreas(
        component: IRComponent,
        modifier: Modifier,
        templateAreas: GridTemplateAreas,
        rowHeights: List<Dp>?,
        horizontalSpacing: Dp,
        verticalSpacing: Dp,
        textColor: Color?
    ) {
        val children = component.children ?: return
        val columnCount = templateAreas.columnCount
        val rowCount = templateAreas.rowCount

        // Build a map of area name -> child component
        val childByArea = mutableMapOf<String, IRComponent>()
        children.forEach { child ->
            val areaName = extractChildAreaName(child.properties)
            if (areaName != null && templateAreas.hasArea(areaName)) {
                childByArea[areaName] = child
            }
        }

        // Track which cells are occupied by multi-cell areas
        val occupiedCells = mutableSetOf<Pair<Int, Int>>()
        templateAreas.areaMap.forEach { (areaName, placement) ->
            for (row in placement.rowStart until placement.rowEnd) {
                for (col in placement.columnStart until placement.columnEnd) {
                    occupiedCells.add(row to col)
                }
            }
        }

        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            for (rowIndex in 0 until rowCount) {
                val rowHeight = rowHeights?.getOrNull(rowIndex)

                Row(
                    modifier = if (rowHeight != null) {
                        Modifier.fillMaxWidth().height(rowHeight)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                    horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
                ) {
                    var colIndex = 0
                    while (colIndex < columnCount) {
                        val areaName = templateAreas.grid.getOrNull(rowIndex)?.getOrNull(colIndex) ?: "."
                        val placement = templateAreas.getPlacement(areaName)
                        val child = childByArea[areaName]

                        // Check if this is the start cell for this area
                        val isAreaStart = placement != null &&
                                placement.rowStart == rowIndex + 1 &&
                                placement.columnStart == colIndex + 1

                        when {
                            areaName == "." -> {
                                // Empty cell
                                Box(modifier = Modifier.weight(1f))
                                colIndex++
                            }
                            isAreaStart && child != null -> {
                                // Render the child in this cell, spanning multiple columns if needed
                                val columnSpan = placement!!.columnSpan
                                val weight = columnSpan.toFloat()

                                Box(
                                    modifier = if (rowHeight != null) {
                                        Modifier.weight(weight).fillMaxHeight()
                                    } else {
                                        Modifier.weight(weight)
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ComponentRenderer.RenderComponent(child)
                                }
                                colIndex += columnSpan
                            }
                            placement != null && placement.rowStart < rowIndex + 1 -> {
                                // This cell is part of a multi-row area that started on a previous row
                                // Skip it (it's handled by the area's starting cell)
                                colIndex++
                            }
                            else -> {
                                // Cell occupied by an area not starting here, or area without child
                                if (!isAreaStart && templateAreas.grid.getOrNull(rowIndex)?.getOrNull(colIndex) != ".") {
                                    // Skip cells that are continuations of areas
                                    colIndex++
                                } else {
                                    Box(modifier = Modifier.weight(1f))
                                    colIndex++
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Extract grid-template-areas from properties using the GridExtractor.
     */
    private fun extractGridTemplateAreas(properties: List<IRProperty>): GridTemplateAreas? {
        val propertyPairs = properties.map { it.type to it.data }
        val config = GridExtractor.extractGridConfig(propertyPairs)
        return config.templateAreas
    }

    /**
     * Extract grid-area name from a child's properties.
     */
    private fun extractChildAreaName(properties: List<IRProperty>): String? {
        properties.forEach { prop ->
            if (prop.type == "GridArea") {
                return when (val data = prop.data) {
                    is JsonPrimitive -> data.contentOrNull?.trim()?.takeIf {
                        it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '-' || c == '_' }
                    }
                    is JsonObject -> data["name"]?.jsonPrimitive?.contentOrNull
                        ?: data["value"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
        }
        return null
    }

    /**
     * Get content alignment from justify-self and align-self values.
     * Combines horizontal (justify-self) and vertical (align-self) alignment.
     */
    private fun getContentAlignment(
        justifySelf: ComponentRenderer.JustifySelf,
        alignSelf: ComponentRenderer.AlignSelf
    ): Alignment {
        val horizontal = justifySelfToHorizontal(justifySelf)
        val vertical = alignSelfToVertical(alignSelf)

        return when {
            horizontal == Alignment.Start && vertical == Alignment.Top -> Alignment.TopStart
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Top -> Alignment.TopCenter
            horizontal == Alignment.End && vertical == Alignment.Top -> Alignment.TopEnd
            horizontal == Alignment.Start && vertical == Alignment.CenterVertically -> Alignment.CenterStart
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.CenterVertically -> Alignment.Center
            horizontal == Alignment.End && vertical == Alignment.CenterVertically -> Alignment.CenterEnd
            horizontal == Alignment.Start && vertical == Alignment.Bottom -> Alignment.BottomStart
            horizontal == Alignment.CenterHorizontally && vertical == Alignment.Bottom -> Alignment.BottomCenter
            horizontal == Alignment.End && vertical == Alignment.Bottom -> Alignment.BottomEnd
            else -> Alignment.Center
        }
    }

    /**
     * Convert justify-self to horizontal alignment.
     */
    private fun justifySelfToHorizontal(justifySelf: ComponentRenderer.JustifySelf): Alignment.Horizontal {
        return when (justifySelf) {
            ComponentRenderer.JustifySelf.START,
            ComponentRenderer.JustifySelf.FLEX_START,
            ComponentRenderer.JustifySelf.SELF_START,
            ComponentRenderer.JustifySelf.LEFT -> Alignment.Start

            ComponentRenderer.JustifySelf.END,
            ComponentRenderer.JustifySelf.FLEX_END,
            ComponentRenderer.JustifySelf.SELF_END,
            ComponentRenderer.JustifySelf.RIGHT -> Alignment.End

            ComponentRenderer.JustifySelf.CENTER -> Alignment.CenterHorizontally

            // Auto, Normal, Stretch, Baseline default to center
            else -> Alignment.CenterHorizontally
        }
    }

    /**
     * Extract align-self value from properties for grid items.
     */
    private fun extractAlignSelf(properties: List<IRProperty>): ComponentRenderer.AlignSelf {
        properties.forEach { prop ->
            if (prop.type == "AlignSelf") {
                val keyword = ValueExtractors.extractKeyword(prop.data)?.uppercase()
                return when (keyword) {
                    "FLEX_START", "FLEX-START", "START" -> ComponentRenderer.AlignSelf.FLEX_START
                    "FLEX_END", "FLEX-END", "END" -> ComponentRenderer.AlignSelf.FLEX_END
                    "CENTER" -> ComponentRenderer.AlignSelf.CENTER
                    "STRETCH" -> ComponentRenderer.AlignSelf.STRETCH
                    "BASELINE" -> ComponentRenderer.AlignSelf.BASELINE
                    else -> ComponentRenderer.AlignSelf.AUTO
                }
            }
        }
        return ComponentRenderer.AlignSelf.AUTO
    }

    /**
     * Convert align-self to vertical alignment.
     */
    private fun alignSelfToVertical(alignSelf: ComponentRenderer.AlignSelf): Alignment.Vertical {
        return when (alignSelf) {
            ComponentRenderer.AlignSelf.FLEX_START -> Alignment.Top
            ComponentRenderer.AlignSelf.FLEX_END -> Alignment.Bottom
            ComponentRenderer.AlignSelf.CENTER -> Alignment.CenterVertically
            // Auto, Stretch, Baseline default to center
            else -> Alignment.CenterVertically
        }
    }

    /**
     * Grid configuration extracted from properties.
     */
    data class GridConfig(
        val columnCount: Int?,
        val rowHeights: List<Dp>?,
        val rowGap: Dp,
        val columnGap: Dp,
        val autoFlow: GridAutoFlow,
        val autoConfig: GridAutoConfig = GridAutoConfig()
    )

    enum class GridAutoFlow {
        ROW, COLUMN, DENSE, ROW_DENSE, COLUMN_DENSE
    }

    /**
     * Configuration for implicitly created grid tracks.
     * These define sizes for tracks created when items are placed outside the explicit grid.
     */
    data class GridAutoConfig(
        val autoColumns: List<AutoTrackSize>? = null,
        val autoRows: List<AutoTrackSize>? = null
    )

    /**
     * Track size for auto-created grid tracks.
     */
    sealed interface AutoTrackSize {
        data class Fixed(val size: Dp) : AutoTrackSize
        data class Flex(val fr: Double) : AutoTrackSize
        data object MinContent : AutoTrackSize
        data object MaxContent : AutoTrackSize
        data object Auto : AutoTrackSize
        data class FitContent(val limit: Dp) : AutoTrackSize
        data class MinMax(val min: AutoTrackSize, val max: AutoTrackSize) : AutoTrackSize
    }

    /**
     * Extract grid configuration from IR properties.
     */
    private fun extractGridConfig(properties: List<IRProperty>): GridConfig {
        var columnCount: Int? = null
        var rowHeights: List<Dp>? = null
        var rowGap = 0.dp
        var columnGap = 0.dp
        var autoFlow = GridAutoFlow.ROW
        var autoColumns: List<AutoTrackSize>? = null
        var autoRows: List<AutoTrackSize>? = null

        properties.forEach { prop ->
            try {
                when (prop.type) {
                    "GridTemplateColumns" -> {
                        columnCount = extractColumnCount(prop.data)
                    }
                    "GridTemplateRows" -> {
                        rowHeights = extractRowHeights(prop.data)
                    }
                    "GridAutoFlow" -> {
                        autoFlow = extractAutoFlow(prop.data)
                    }
                    "GridAutoColumns" -> {
                        autoColumns = extractAutoTrackSizes(prop.data)
                    }
                    "GridAutoRows" -> {
                        autoRows = extractAutoTrackSizes(prop.data)
                    }
                    "Gap" -> {
                        ValueExtractors.extractDp(prop.data)?.let {
                            rowGap = it
                            columnGap = it
                        }
                    }
                    "RowGap" -> {
                        ValueExtractors.extractDp(prop.data)?.let { rowGap = it }
                    }
                    "ColumnGap" -> {
                        ValueExtractors.extractDp(prop.data)?.let { columnGap = it }
                    }
                }
            } catch (e: Exception) {
                // Skip properties that fail to parse
            }
        }

        return GridConfig(
            columnCount = columnCount,
            rowHeights = rowHeights,
            rowGap = rowGap,
            columnGap = columnGap,
            autoFlow = autoFlow,
            autoConfig = GridAutoConfig(
                autoColumns = autoColumns,
                autoRows = autoRows
            )
        )
    }

    /**
     * Extract grid-auto-flow value from property data.
     *
     * Handles formats:
     * - String keyword: "ROW", "COLUMN", "ROW_DENSE", "COLUMN_DENSE", "DENSE"
     * - Object with direction and dense: {"direction": "ROW", "dense": true}
     */
    private fun extractAutoFlow(data: JsonElement): GridAutoFlow {
        return try {
            when (data) {
                is JsonPrimitive -> {
                    val keyword = data.contentOrNull?.uppercase()
                    when (keyword) {
                        "COLUMN" -> GridAutoFlow.COLUMN
                        "DENSE" -> GridAutoFlow.DENSE
                        "ROW_DENSE", "ROW DENSE" -> GridAutoFlow.ROW_DENSE
                        "COLUMN_DENSE", "COLUMN DENSE" -> GridAutoFlow.COLUMN_DENSE
                        else -> GridAutoFlow.ROW
                    }
                }
                is JsonObject -> {
                    val direction = data["direction"]?.jsonPrimitive?.contentOrNull?.uppercase()
                    val dense = data["dense"]?.jsonPrimitive?.booleanOrNull ?: false
                    when {
                        direction == "COLUMN" && dense -> GridAutoFlow.COLUMN_DENSE
                        direction == "COLUMN" -> GridAutoFlow.COLUMN
                        dense -> GridAutoFlow.ROW_DENSE
                        else -> GridAutoFlow.ROW
                    }
                }
                else -> GridAutoFlow.ROW
            }
        } catch (e: Exception) {
            GridAutoFlow.ROW
        }
    }

    /**
     * Extract auto track sizes for grid-auto-columns or grid-auto-rows.
     *
     * Handles formats:
     * - Single value: {"px": 100.0} or {"fr": 1.0} or {"keyword": "MIN_CONTENT"}
     * - Array of values: [{"px": 100.0}, {"fr": 1.0}]
     * - String: "100px" or "1fr" or "min-content"
     */
    private fun extractAutoTrackSizes(data: JsonElement): List<AutoTrackSize>? {
        return try {
            when (data) {
                is JsonArray -> {
                    data.mapNotNull { extractSingleAutoTrackSize(it) }.takeIf { it.isNotEmpty() }
                }
                is JsonObject, is JsonPrimitive -> {
                    extractSingleAutoTrackSize(data)?.let { listOf(it) }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract a single auto track size value.
     */
    private fun extractSingleAutoTrackSize(data: JsonElement): AutoTrackSize? {
        return try {
            when (data) {
                is JsonObject -> {
                    // Check for pixel value
                    data["px"]?.jsonPrimitive?.doubleOrNull?.let {
                        return AutoTrackSize.Fixed(it.dp)
                    }
                    // Check for fr value
                    data["fr"]?.jsonPrimitive?.doubleOrNull?.let {
                        return AutoTrackSize.Flex(it)
                    }
                    // Check for fit-content
                    data["fit"]?.let { fitData ->
                        val limit = when (fitData) {
                            is JsonObject -> fitData["px"]?.jsonPrimitive?.doubleOrNull?.dp
                            is JsonPrimitive -> fitData.doubleOrNull?.dp
                            else -> null
                        }
                        return if (limit != null) AutoTrackSize.FitContent(limit) else null
                    }
                    // Check for minmax
                    if (data.containsKey("min") && data.containsKey("max")) {
                        val min = extractSingleAutoTrackSize(data["min"]!!) ?: return null
                        val max = extractSingleAutoTrackSize(data["max"]!!) ?: return null
                        return AutoTrackSize.MinMax(min, max)
                    }
                    // Check for keyword
                    data["keyword"]?.jsonPrimitive?.contentOrNull?.uppercase()?.let { keyword ->
                        return when (keyword) {
                            "MIN_CONTENT", "MIN-CONTENT" -> AutoTrackSize.MinContent
                            "MAX_CONTENT", "MAX-CONTENT" -> AutoTrackSize.MaxContent
                            "AUTO" -> AutoTrackSize.Auto
                            else -> null
                        }
                    }
                    null
                }
                is JsonPrimitive -> {
                    val str = data.contentOrNull?.lowercase() ?: return null
                    when {
                        str == "auto" -> AutoTrackSize.Auto
                        str == "min-content" -> AutoTrackSize.MinContent
                        str == "max-content" -> AutoTrackSize.MaxContent
                        str.endsWith("px") -> {
                            str.dropLast(2).toDoubleOrNull()?.let { AutoTrackSize.Fixed(it.dp) }
                        }
                        str.endsWith("fr") -> {
                            str.dropLast(2).toDoubleOrNull()?.let { AutoTrackSize.Flex(it) }
                        }
                        else -> data.doubleOrNull?.let { AutoTrackSize.Fixed(it.dp) }
                    }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the default size for auto-created tracks.
     * Returns the first auto track size as Dp, or null if using intrinsic sizing.
     */
    fun getAutoColumnSize(autoConfig: GridAutoConfig): Dp? {
        val firstSize = autoConfig.autoColumns?.firstOrNull() ?: return null
        return autoTrackSizeToDp(firstSize)
    }

    /**
     * Get the default row size for auto-created tracks.
     * Returns the first auto track size as Dp, or null if using intrinsic sizing.
     */
    fun getAutoRowSize(autoConfig: GridAutoConfig): Dp? {
        val firstSize = autoConfig.autoRows?.firstOrNull() ?: return null
        return autoTrackSizeToDp(firstSize)
    }

    /**
     * Convert an AutoTrackSize to Dp if possible.
     */
    private fun autoTrackSizeToDp(trackSize: AutoTrackSize): Dp? {
        return when (trackSize) {
            is AutoTrackSize.Fixed -> trackSize.size
            is AutoTrackSize.FitContent -> trackSize.limit
            is AutoTrackSize.MinMax -> {
                // For minmax, use the max value if it's fixed, otherwise the min
                autoTrackSizeToDp(trackSize.max) ?: autoTrackSizeToDp(trackSize.min)
            }
            // Intrinsic sizes (Auto, MinContent, MaxContent, Flex) can't be converted to fixed Dp
            else -> null
        }
    }

    /**
     * Extract row heights from grid-template-rows data.
     *
     * Handles formats like:
     * - Array of lengths: [{"px": 80.0}, {"px": 60.0}, {"px": 40.0}]
     * - String: "80px 60px 40px"
     */
    private fun extractRowHeights(data: JsonElement): List<Dp>? {
        return try {
            when (data) {
                is JsonArray -> {
                    if (data.isEmpty()) return null
                    data.mapNotNull { element ->
                        when (element) {
                            is JsonObject -> {
                                element["px"]?.jsonPrimitive?.doubleOrNull?.dp
                            }
                            is JsonPrimitive -> {
                                element.doubleOrNull?.dp
                            }
                            else -> null
                        }
                    }.takeIf { it.isNotEmpty() }
                }
                is JsonPrimitive -> {
                    val str = data.contentOrNull ?: return null
                    str.split(Regex("\\s+")).mapNotNull { part ->
                        val match = Regex("([\\d.]+)px").matchEntire(part)
                        match?.groupValues?.get(1)?.toDoubleOrNull()?.dp
                    }.takeIf { it.isNotEmpty() }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extract column count from grid-template-columns data.
     *
     * Handles formats like:
     * - Array of track sizes: [{"fr": 1.0}, {"fr": 1.0}, {"fr": 1.0}] -> 3 columns
     * - Array with repeat: [{"repeat": 3, "tracks": [...]}] -> 3 columns
     * - Object with repeat: {"repeat": 3, "tracks": [...]} -> 3 columns
     * - String: "1fr 1fr 1fr" -> 3 columns
     */
    private fun extractColumnCount(data: JsonElement): Int? {
        return try {
            when (data) {
                is JsonArray -> {
                    if (data.isEmpty()) return null

                    // Check if first element has "repeat" field (repeat function)
                    val firstElement = data.firstOrNull()
                    if (firstElement is JsonObject && firstElement.containsKey("repeat")) {
                        firstElement["repeat"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                    } else {
                        // Array of individual track sizes
                        data.size.takeIf { it > 0 }
                    }
                }
                is JsonObject -> {
                    // Direct repeat object
                    data["repeat"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                        ?: data["count"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                }
                is JsonPrimitive -> {
                    data.contentOrNull?.split(Regex("\\s+"))?.size?.takeIf { it > 0 }
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    @Composable
    private fun PlaceholderContent(name: String, textColor: Color?) {
        Text(
            text = name.replace("_", " "),
            fontSize = 11.sp,
            color = textColor ?: Color(0xFF888888),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp)
        )
    }
}

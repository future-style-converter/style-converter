package com.styleconverter.runtime.layout.grid

import com.styleconverter.runtime.core.renderer.ComponentRenderer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.types.ValueExtractors
import com.styleconverter.runtime.layout.grid.GridExtractor
import com.styleconverter.runtime.layout.grid.GridTemplateAreas
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
            // No children — render the CANONICAL placeholder, top-start.
            //
            // ComponentRenderer normally demotes empty grid containers to
            // block layout before reaching here (mirroring the web
            // harness's `display:block` override for empty grid/flex), so
            // this branch is a defensive fallback for direct RenderGrid
            // callers. It must still match web: the placeholder <span>
            // inherits the body 16px font and sits top-left of the block —
            // NOT the old local 11.sp / gray / centered / 2-line label
            // that dragged Grid_Simple/ThreeCol/FixedTracks Android-web
            // SSIM to 0.80-0.86.
            Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
                ComponentRenderer.PlaceholderContent(
                    name = component.name,
                    textColor = textColor,
                    properties = component.properties
                )
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

                // Track-aware row. The previous implementation gave every
                // column `Modifier.weight(1f)`, which silently rewrote
                // `grid-template-columns: 80px 120px 80px` (or 25% 50% 25%,
                // or 1fr 2fr 1fr) into three equal columns — the whole
                // GTC fixture family diverged from web at SSIM 0.76-0.80.
                // GridTrackRow measures/places cells per the parsed specs
                // (css-grid-1 §7.2 track sizing, approximated: px / %
                // literal, fr shares of free space, auto = max-content +
                // an equal share of leftover per Chrome's stretch
                // behaviour, minmax(min,fr) = max(min, fr share)).
                GridTrackRow(
                    tracks = gridConfig.columnTracks,
                    columnCount = columnCount,
                    gap = horizontalSpacing,
                    rowHeight = rowHeight
                ) {
                    rowChildren.forEach { child ->
                        // Extract justify-self and align-self for individual item alignment
                        val justifySelf = ComponentRenderer.extractJustifySelf(child.properties)
                        val alignSelf = extractAlignSelf(child.properties)

                        // Calculate content alignment from justify-self and align-self
                        val contentAlignment = getContentAlignment(justifySelf, alignSelf)

                        Box(
                            modifier = if (rowHeight != null) Modifier.fillMaxHeight() else Modifier,
                            contentAlignment = contentAlignment
                        ) {
                            ComponentRenderer.RenderComponent(child)
                        }
                    }
                }
            }
        }
    }

    /**
     * One grid row laid out against explicit column tracks.
     *
     * Each direct child is one cell (a Box wrapping the grid item). The
     * measure policy:
     *   1. resolves every track to a pixel width via [computeTrackWidths]
     *      (pure function — unit-tested on the JVM),
     *   2. measures cell i with exactly track[i]'s width so the cell Box
     *      spans the track and its contentAlignment (justify-self /
     *      align-self) positions the item inside it,
     *   3. places cells left-to-right at the cumulative track offsets with
     *      [gap] between tracks (css-align-3 column-gap).
     *
     * Auto tracks need the item's max-content width (css-grid-1 §7.2.1);
     * we read `maxIntrinsicWidth` before the real measure pass.
     */
    @Composable
    private fun GridTrackRow(
        tracks: List<TrackSpec>?,
        columnCount: Int,
        gap: Dp,
        rowHeight: Dp?,
        content: @Composable () -> Unit
    ) {
        Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
            val gapPx = gap.roundToPx().toFloat()
            val rowHeightPx = rowHeight?.roundToPx()
            // Unbounded-width guard: inside a horizontal scroller the max
            // constraint is Infinity, and fr/percent shares of infinity
            // are meaningless. CSS sizes fr against a definite containing
            // block; when there is none we fall back to the min width so
            // fixed/auto tracks still lay out and flexible ones collapse
            // to their bases (same degenerate outcome as CSS min-content
            // sizing under an infinite available space).
            val containerW = if (constraints.hasBoundedWidth)
                constraints.maxWidth.toFloat()
            else
                constraints.minWidth.toFloat()
            // Normalize the specs to columnCount entries. No parsed specs
            // (legacy IR / unsupported expr) → all-fr(1), byte-compatible
            // with the old equal-weight behaviour.
            val specs = (0 until columnCount).map { i ->
                tracks?.getOrNull(i) ?: TrackSpec.Fr(1f)
            }
            // Max-content width per cell — only consulted for auto/fit
            // tracks; cheap no-op for the rest.
            val intrinsics = measurables.mapIndexed { i, m ->
                when (specs.getOrNull(i)) {
                    is TrackSpec.Auto, is TrackSpec.Fit ->
                        m.maxIntrinsicWidth(rowHeightPx ?: Int.MAX_VALUE).toFloat()
                    else -> 0f
                }
            }
            val widths = computeTrackWidths(specs, intrinsics, containerW, gapPx)
            val placeables = measurables.mapIndexed { i, m ->
                val w = widths.getOrElse(i) { 0f }.toInt().coerceAtLeast(0)
                m.measure(
                    if (rowHeightPx != null)
                        Constraints.fixed(w, rowHeightPx)
                    else
                        Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = constraints.maxHeight)
                )
            }
            val rowH = rowHeightPx ?: (placeables.maxOfOrNull { it.height } ?: 0)
            // Row width: the bounded container width, or (degenerate
            // unbounded case) the tracks' own footprint — never Infinity,
            // which Compose would reject at layout() time.
            val rowW = if (constraints.hasBoundedWidth)
                constraints.maxWidth
            else
                (widths.sum() + gapPx * (widths.size - 1).coerceAtLeast(0)).toInt()
            layout(rowW, rowH) {
                var x = 0f
                placeables.forEachIndexed { i, p ->
                    p.placeRelative(x.toInt(), 0)
                    x += widths.getOrElse(i) { 0f } + gapPx
                }
            }
        }
    }

    /**
     * Column track specification parsed from the GridTemplateColumns IR.
     * Wire shapes (see GridTemplateColumnsPropertyParser):
     *   {"px": 80}          → Px       (fixed length, resolved by the parser)
     *   {"fr": 2}           → Fr       (flexible fraction, css-grid-1 §7.2.4)
     *   25.0 (bare number)  → Percent  (of the grid container's content box)
     *   "auto"              → Auto     (max-content + stretch share)
     *   {"fit": {"px": N}}  → Fit      (fit-content(N) ≈ min(max-content, N))
     *   {"repeat": n, "tracks": [...]} → n expanded copies of the inner list
     *   {"expr": "minmax(80px, 1fr) …"} → best-effort minmax / named-lines
     *     parse; unsupported exprs (auto-fill/auto-fit) return null so the
     *     caller keeps the legacy equal-fr fallback.
     */
    sealed interface TrackSpec {
        data class Px(val px: Float) : TrackSpec
        data class Fr(val fr: Float) : TrackSpec
        data class Percent(val pct: Float) : TrackSpec
        data object Auto : TrackSpec
        data class Fit(val limitPx: Float) : TrackSpec
        /** minmax(<min-px>, <fr>) — the only minmax form the IR keeps as text. */
        data class MinMax(val minPx: Float, val fr: Float) : TrackSpec
    }

    /**
     * Resolve track specs to pixel widths. Pure function (JVM-testable).
     *
     * Approximation of css-grid-1 §7.2 track sizing for the shapes the IR
     * carries:
     *   - Px / Percent resolve literally (percent against the container's
     *     content-box width, like Chrome).
     *   - Fr tracks split the free space left after px/percent/auto/minmax
     *     bases, proportionally to their factors (§7.2.4).
     *   - MinMax(min, fr) takes the larger of its min and its fr share.
     *   - Auto tracks start at max-content and, when NO fr track exists,
     *     absorb the remaining free space in equal parts — Chrome's
     *     `justify-content: normal` stretch of auto tracks. When fr tracks
     *     exist they soak up all free space instead, so autos stay at
     *     max-content.
     *   - Fit(limit) = min(max-content, limit) (fit-content(), §7.2.2).
     */
    internal fun computeTrackWidths(
        specs: List<TrackSpec>,
        maxContentWidths: List<Float>,
        containerWidth: Float,
        gapPx: Float
    ): List<Float> {
        val n = specs.size
        if (n == 0) return emptyList()
        val gaps = gapPx * (n - 1).coerceAtLeast(0)
        // Base widths: everything except fr shares.
        val base = specs.mapIndexed { i, s ->
            when (s) {
                is TrackSpec.Px -> s.px
                is TrackSpec.Percent -> s.pct / 100f * containerWidth
                is TrackSpec.Auto -> maxContentWidths.getOrElse(i) { 0f }
                is TrackSpec.Fit -> minOf(maxContentWidths.getOrElse(i) { 0f }, s.limitPx)
                is TrackSpec.MinMax -> s.minPx
                is TrackSpec.Fr -> 0f
            }
        }.toMutableList()
        val frSum = specs.sumOf { s ->
            when (s) {
                is TrackSpec.Fr -> s.fr.toDouble()
                is TrackSpec.MinMax -> s.fr.toDouble()
                else -> 0.0
            }
        }.toFloat()
        if (frSum > 0f) {
            // Free space for flexible tracks: container minus gaps minus
            // every non-flexible base (minmax mins are NOT subtracted —
            // their fr share replaces the min when it's larger, mirroring
            // the "greater of base and fr share" §7.2.4 outcome).
            val fixed = specs.indices.sumOf { i ->
                if (specs[i] is TrackSpec.Fr || specs[i] is TrackSpec.MinMax) 0.0
                else base[i].toDouble()
            }.toFloat()
            val free = (containerWidth - gaps - fixed).coerceAtLeast(0f)
            specs.forEachIndexed { i, s ->
                when (s) {
                    is TrackSpec.Fr -> base[i] = free * s.fr / frSum
                    is TrackSpec.MinMax -> base[i] = maxOf(s.minPx, free * s.fr / frSum)
                    else -> Unit
                }
            }
        } else {
            // No fr tracks: leftover space stretches auto tracks equally
            // (Chrome's normal-alignment auto-track stretch).
            val autoIdx = specs.indices.filter { specs[it] is TrackSpec.Auto }
            if (autoIdx.isNotEmpty()) {
                val used = base.sum() + gaps
                val extra = ((containerWidth - used) / autoIdx.size).coerceAtLeast(0f)
                autoIdx.forEach { base[it] = base[it] + extra }
            }
        }
        return base
    }

    /**
     * Parse the GridTemplateColumns IR payload into [TrackSpec]s.
     * Returns null when the payload uses a form we can't size honestly
     * (repeat(auto-fill/auto-fit), subgrid, …) — the caller then keeps the
     * legacy equal-fr layout instead of guessing.
     */
    internal fun parseColumnTracks(data: JsonElement): List<TrackSpec>? {
        return try {
            when (data) {
                is JsonArray -> {
                    val out = mutableListOf<TrackSpec>()
                    for (el in data) {
                        when (el) {
                            is JsonObject -> when {
                                el.containsKey("px") ->
                                    out.add(TrackSpec.Px(el["px"]!!.jsonPrimitive.float))
                                el.containsKey("fr") ->
                                    out.add(TrackSpec.Fr(el["fr"]!!.jsonPrimitive.float))
                                el.containsKey("fit") ->
                                    out.add(TrackSpec.Fit(
                                        (el["fit"] as? JsonObject)?.get("px")?.jsonPrimitive?.floatOrNull
                                            ?: return null))
                                el.containsKey("repeat") -> {
                                    // {"repeat": n, "tracks": [...]} → expand n copies.
                                    val count = el["repeat"]!!.jsonPrimitive.int
                                    val inner = (el["tracks"] as? JsonArray)
                                        ?.let { parseColumnTracks(it) } ?: return null
                                    repeat(count) { out.addAll(inner) }
                                }
                                else -> return null
                            }
                            is JsonPrimitive -> when {
                                el.isString && el.content.equals("auto", true) ->
                                    out.add(TrackSpec.Auto)
                                el.doubleOrNull != null ->
                                    // Bare number = percentage (parser drops the unit).
                                    out.add(TrackSpec.Percent(el.float))
                                else -> return null
                            }
                            else -> return null
                        }
                    }
                    out.ifEmpty { null }
                }
                is JsonObject -> {
                    // {"expr": "..."} — raw text the parser couldn't model.
                    val expr = data["expr"]?.jsonPrimitive?.contentOrNull ?: return null
                    parseTrackExpr(expr)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Best-effort parse of a raw track-list expression. Handles the two
     * shapes the CSS parser leaves as text:
     *   - `minmax(<len>px, <n>fr)` sequences → [TrackSpec.MinMax]
     *   - named lines `[name]` interleaved with px/fr/auto tokens
     * Anything else (repeat(auto-fill…), subgrid) → null (legacy fallback).
     */
    internal fun parseTrackExpr(expr: String): List<TrackSpec>? {
        // Strip named-line groups: `[start] 1fr [mid] 1fr [end]` → `1fr 1fr`.
        val cleaned = expr.replace(Regex("\\[[^\\]]*\\]"), " ").trim()
        if (cleaned.isEmpty()) return null
        // Tokenize on top-level whitespace (parens keep minmax() together).
        val tokens = mutableListOf<String>()
        var depth = 0
        val cur = StringBuilder()
        for (ch in cleaned) {
            when {
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth--; cur.append(ch) }
                ch.isWhitespace() && depth == 0 -> {
                    if (cur.isNotEmpty()) { tokens.add(cur.toString()); cur.clear() }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) tokens.add(cur.toString())
        val out = mutableListOf<TrackSpec>()
        for (t in tokens) {
            val mm = Regex("^minmax\\(\\s*([0-9.]+)px\\s*,\\s*([0-9.]+)fr\\s*\\)$", RegexOption.IGNORE_CASE).find(t)
            when {
                mm != null -> out.add(TrackSpec.MinMax(mm.groupValues[1].toFloat(), mm.groupValues[2].toFloat()))
                t.endsWith("fr") -> t.dropLast(2).toFloatOrNull()?.let { out.add(TrackSpec.Fr(it)) } ?: return null
                t.endsWith("px") -> t.dropLast(2).toFloatOrNull()?.let { out.add(TrackSpec.Px(it)) } ?: return null
                t.equals("auto", true) -> out.add(TrackSpec.Auto)
                else -> return null // repeat(auto-fill…), %, calc() — bail honestly.
            }
        }
        return out.ifEmpty { null }
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

            // Auto / Normal / Stretch / Baseline: css-align-3 §6 — `auto`
            // resolves to `normal`, which for grid items behaves as
            // `stretch`. Our items are rendered fit-content (both the web
            // harness wrapper and Compose hug the content), and a
            // non-stretchable item under `normal` is start-aligned. The
            // old Center default shifted EVERY default-aligned grid item
            // to the middle of its track while web pinned it at the track
            // start (GTC fixtures diverged at SSIM 0.76-0.80).
            else -> Alignment.Start
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
            // Auto / Stretch / Baseline: same css-align-3 §6 reasoning as
            // justifySelfToHorizontal — `normal` on a fixed-height item is
            // start(top)-aligned, matching web. Center was wrong.
            else -> Alignment.Top
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
        val autoConfig: GridAutoConfig = GridAutoConfig(),
        /**
         * Parsed grid-template-columns track list. null when the IR uses a
         * form parseColumnTracks can't size (auto-fill/auto-fit/subgrid) —
         * the renderer then falls back to equal-fr columns.
         */
        val columnTracks: List<TrackSpec>? = null
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
        var columnTracks: List<TrackSpec>? = null

        properties.forEach { prop ->
            try {
                when (prop.type) {
                    "GridTemplateColumns" -> {
                        // Full track parse first (sizes + count together);
                        // extractColumnCount stays as the fallback for
                        // shapes parseColumnTracks declines (auto-fill…).
                        columnTracks = parseColumnTracks(prop.data)
                        columnCount = columnTracks?.size ?: extractColumnCount(prop.data)
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
            ),
            columnTracks = columnTracks
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

}

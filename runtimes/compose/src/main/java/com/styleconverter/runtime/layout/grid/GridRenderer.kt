package com.styleconverter.runtime.layout.grid

import com.styleconverter.runtime.core.renderer.ComponentRenderer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.placement.itemPlacement
import com.styleconverter.runtime.core.types.ValueExtractors
// The ONE guard for Compose's optional intrinsic channel — a grid item is
// an arbitrary component subtree, so the auto-track max-content read below
// can meet NoIntrinsicsMeasurePolicy's throw (see IntrinsicChannel's banner).
import com.styleconverter.runtime.layout.IntrinsicChannel
// Wave 19: ambient layout direction — RTL grids may inherit direction from
// an ancestor via the ComponentRenderer RTL provider (css-writing-modes §2).
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
// Wave 19: the single owner of the Direction property parse (typography
// Phase 6) — reused so grid and text can never disagree about rtl.
import com.styleconverter.runtime.typography.TextStyleApplier
// §9.2 static-position machinery shared with the flex abspos path (wave 10) —
// Spec/Base domain + the safe-aware align-self resolver + the inset gate.
import com.styleconverter.runtime.layout.flexbox.AbsposStaticAlignment
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

        // v2 wave-5: template areas no longer take a separate weight-based
        // rendering path. The converter's GridAreaExpander lowers
        // `grid-area: name` to a single `grid-row-start: name` longhand, so
        // the browser (our reference) resolves the NAME to the area's row
        // line and auto-flows the column (css-grid-1 §8.3 <custom-ident> →
        // §8.5 step 2) — verified pixel-exact against the PL_Areas* web
        // captures: `media`/`title` share row 1, `body` lands (2,1), the
        // dangling `ghost` name creates an implicit row AFTER an empty one.
        // The old RenderGridWithTemplateAreas branch (childByArea keyed on a
        // "GridArea" IR type the converter never emits) rendered every child
        // as an EMPTY weighted cell and was unreachable for v2 areas wire
        // anyway (GridExtractor didn't parse the rows-of-arrays shape). The
        // areas grid now feeds ONLY named-line resolution below.
        val areasGridForCount = extractAreasGridV2(component.properties)

        // Definite-width detection. The web reference gives every component
        // `width: fit-content` unless the IR declares a width, so a grid
        // with NO declared width HUGS its tracks (fr resolves to content
        // size). Compose Layout constraints can't tell us this — the parent
        // Box always hands down a bounded canvas width — so we read the IR.
        // Without this, nested-3level's inner `grid` blew out to the full
        // 358px canvas while web hugged it at ~130px (0.5408 / 42.7% px).
        val definiteWidth = ComponentRenderer.hasDefiniteSize(
            component.properties, widthAxis = true
        )

        // Named-area map for §8.3 <custom-ident> line resolution. Parsed
        // from the v2 areas wire ({"type":"areas","rows":[["a","a"],…]}) —
        // the legacy GridTemplateAreas class only understood string rows.
        val areasGrid = areasGridForCount
        val areaMap = buildAreaMap(areasGrid)

        // Explicit row count: template rows list, else the areas grid's row
        // count (each areas string row defines one explicit row, css-grid-2
        // §7.3). Needed for negative row lines + dangling-name resolution.
        val rowHeights = gridConfig.rowHeights
        val explicitRowCount = rowHeights?.size ?: areasGrid?.size ?: 0

        // Sort children by order property (CSS `order` participates in
        // auto-placement order, css-flexbox-1 §5.4 / css-grid-1 §8.5),
        // then run the placement algorithm — explicit grid-column/row
        // lines, spans, and named-area lines are honored; everything else
        // auto-flows row-major (dense re-scans from the grid start when
        // grid-auto-flow requests dense packing, §8.5 "dense" variant).
        val dense = gridConfig.autoFlow == GridAutoFlow.DENSE ||
            gridConfig.autoFlow == GridAutoFlow.ROW_DENSE ||
            gridConfig.autoFlow == GridAutoFlow.COLUMN_DENSE
        val sortedChildren = ComponentRenderer.sortByOrder(component.children)
        // css-grid-1 §9: an absolutely-positioned child of a grid container
        // is NOT a grid item — it does not participate in auto-placement and
        // "does not affect the sizing of the grid tracks". Partition it out
        // BEFORE the placement algorithm; without this filter abspos
        // children were auto-placed as REAL items (occupying cell 1,1,
        // feeding track intrinsics, size clamped by their track). They
        // render in the §9.2 overlay after the placed grid below.
        // WAVE-8 INDEX LESSON: every list derived from here (claims →
        // specs → placements.childIndex → the render loop's child lookup)
        // must index the SAME partitioned list, so the partition happens
        // exactly once, first.
        val (flowChildren, outOfFlowChildren) = partitionOutOfFlow(sortedChildren)
        val claims = flowChildren.map {
            com.styleconverter.runtime.core.placement.ItemPlacementExtractor
                .extract(it.properties)
        }
        // Wave-19 RC-B3: the explicit column count comes from the template /
        // areas grid as before, but a TEMPLATE-LESS grid now enumerates its
        // IMPLICIT columns per css-grid-1 §7.5 instead of the legacy magic
        // "2": row auto-flow puts every item in the single implicit column 1;
        // column auto-flow opens one implicit column per item; explicit
        // numeric grid-column claims grow the count. Crucially this counts
        // the POST-SPLICE in-flow claims (flowChildren — display:contents
        // children were already spliced out by ContentsUnboxing upstream in
        // RenderComponent), so a contents wrapper can no longer leave a
        // phantom second track: display-contents-alignment-002's single blue
        // item now gets ONE full-width auto column like iOS/web, not half.
        val columnCount = gridConfig.columnCount
            ?: areasGridForCount?.maxOfOrNull { it.size }
            ?: implicitColumnCount(gridConfig.autoFlow, claims.map { it.grid })
        val specs = claims.map {
            resolvePlacementSpec(it.grid, columnCount, explicitRowCount, areaMap)
        }
        val placements = placeItems(specs, columnCount, dense)
        // The grid's row extent: every EXPLICIT template row exists even
        // with no item in it (css-grid-1 §7.1 — explicit tracks are always
        // laid out), and placements can extend it with implicit rows.
        val rowCount = maxOf(
            placements.maxOfOrNull { it.row + it.rowSpan } ?: 0,
            explicitRowCount,
            1
        )

        // Per-row heights: explicit template rows first, then implicit rows
        // take grid-auto-rows sizes (cycling through the list, css-grid-1
        // §7.6), then null = auto (max content height of the row's cells).
        val autoRowDp = gridConfig.autoConfig.autoRows
            ?.mapNotNull { autoTrackSizeToDp(it) }
            ?.takeIf { it.isNotEmpty() }
        val resolvedRowHeights: List<Dp?> = (0 until rowCount).map { r ->
            rowHeights?.getOrNull(r) ?: run {
                val implicitIndex = r - (rowHeights?.size ?: 0)
                if (implicitIndex >= 0 && autoRowDp != null)
                    autoRowDp[implicitIndex % autoRowDp.size]
                else null
            }
        }

        // Container-level item alignment defaults (css-align-3 §6.2/§6.4):
        // justify-self:auto resolves to the container's justify-items;
        // align-self:auto resolves to the container's align-items. These
        // fallbacks are what PL_GridJustifyOverride pinned — `d` (no
        // justify-self) must CENTER under `justify-items: center` while its
        // siblings' explicit start/end/stretch win (Android previously
        // ignored justify-items entirely, A-w 0.868).
        val justifyItems = extractJustifyItems(component.properties)

        // Wave-19 RC-A2: container-level CONTENT distribution + direction.
        // justify-content moves the whole track group inside the content box
        // (css-align-3 §5.3 — a distinct axis from the per-item justify-self
        // handled above); DisplayConfig already parses the keyword for the
        // flex paths, so grid reuses the same single owner.
        val justifyContent = GridContentDistribution.justifyOf(displayConfig.justifyContent)
        // direction: rtl — the grid's own Direction property (the wire these
        // WPT captures carry, e.g. descendant-static-position-002/004) OR an
        // inherited RTL context (ComponentRenderer provides
        // LocalLayoutDirection=Rtl for RTL components, and CompositionLocals
        // flow into descendants — css-writing-modes §2 inheritance).
        val rtl = TextStyleApplier.extractDirection(component.properties) ==
            TextStyleApplier.DirectionMode.RTL ||
            LocalLayoutDirection.current == LayoutDirection.Rtl

        // Single-Layout placed grid: both axes resolved in one measure pass
        // so ROW SPANS render (the Column-of-rows structure could only give
        // a cell its own row's height — `grid-row: 3 / 5` items showed one
        // track tall and implicit rows never materialized, PL_LineSpans /
        // PL_DenseBackfill / PL_AreaLineSyntax).
        // The placed-cell content, hoisted so both render shapes below (bare
        // grid / grid + §9.2 abspos overlay) share ONE cell loop — a second
        // copy would be the exact fix-lands-in-a-dead-file hazard the v2
        // placement contract forbids.
        val placedCellContent: @Composable () -> Unit = {
            placements.forEach { cell ->
                // Index alignment (the wave-8 lesson): cell.childIndex was
                // assigned by placeItems over the PARTITIONED in-flow specs
                // list, so it must look up flowChildren — indexing the
                // pre-partition sortedChildren would shift every child after
                // the first abspos sibling by one.
                val child = flowChildren[cell.childIndex]
                // v2 placement contract: read the child's ITEM claims through
                // the single placement union — this grid consumes only the
                // alignment claims it owns (justify-self / align-self,
                // css-align-3 §6); the flex block on the same child is inert.
                val placement = claims[cell.childIndex]

                // css-align-3 §6.2: justify-self:auto → container
                // justify-items (default normal → start for our
                // fit-content items).
                val effJustify =
                    if (placement.justifySelf == ComponentRenderer.JustifySelf.AUTO &&
                        justifyItems != null) justifyItems
                    else placement.justifySelf
                val contentAlignment = getContentAlignment(effJustify, placement.alignSelf)

                // Whether every row this cell spans has a definite height —
                // only then can the cell Box fill and stretch its content.
                val cellHeightDefinite = (cell.row until cell.row + cell.rowSpan)
                    .all { resolvedRowHeights.getOrNull(it) != null }

                // css-align-3 §6.6: `align-self: stretch` (and the
                // `normal`/auto default) makes an AUTO-height item
                // fill its row track. Web stretches these (the
                // harness only suppresses INLINE-axis stretch via
                // width:fit-content; heights stay auto), Android
                // kept content height (G2_AlignSelf `d` sat 30px
                // in a 60px track, 0.9644). Only when the row has
                // a definite height and the child's height is auto.
                val childHeightDefinite = ComponentRenderer.hasDefiniteSize(
                    child.properties, widthAxis = false
                )
                val stretchHeight = cellHeightDefinite && !childHeightDefinite &&
                    (placement.alignSelf == ComponentRenderer.AlignSelf.STRETCH ||
                     placement.alignSelf == ComponentRenderer.AlignSelf.AUTO)

                Box(
                    // itemPlacement publishes the child's claims as
                    // parent-data on the measurable that GridPlacedGrid's
                    // Layout measures — the v2 parent-data channel (inert
                    // for measurement today: placement is resolved
                    // pre-measure by placeItems; a fully measure-time
                    // StyleGrid can consume it later, design §3.1).
                    modifier = (if (cellHeightDefinite) Modifier.fillMaxHeight() else Modifier)
                        .itemPlacement(placement),
                    contentAlignment = contentAlignment
                ) {
                    // This cell already applied justify-self/align-self
                    // as contentAlignment — suppress the block-level
                    // self-alignment wrapper inside RenderComponent so
                    // grid items don't double-align (css-align-3 §6).
                    androidx.compose.runtime.CompositionLocalProvider(
                        ComponentRenderer.LocalSelfAlignmentHandled provides true
                    ) {
                        ComponentRenderer.RenderComponent(
                            child,
                            itemModifier = if (stretchHeight) Modifier.fillMaxHeight() else Modifier
                        )
                    }
                }
            }
        }

        if (outOfFlowChildren.isEmpty()) {
            // No out-of-flow children — the exact pre-partition render shape
            // (grid Layout carries the container's whole style chain), so
            // every committed grid baseline stays byte-identical.
            GridPlacedGrid(
                tracks = gridConfig.columnTracks,
                columnCount = columnCount,
                columnGap = horizontalSpacing,
                rowGap = verticalSpacing,
                rowHeights = resolvedRowHeights,
                definiteWidth = definiteWidth,
                cells = placements,
                // Wave-19 RC-A2: content distribution + rtl ride into the
                // measure policy so every grid-area origin shifts together.
                justify = justifyContent,
                rtl = rtl,
                modifier = modifier,
                content = placedCellContent
            )
        } else {
            // css-grid-1 §9.2 overlay: the grid container's style chain moves
            // to this Box, whose INNER area is the container's CONTENT box
            // (StyleApplier chains padding innermost — step 8 — so content
            // composed inside the chain sits within the padding band). That
            // content box is precisely the §9.2 alignment container: "as if
            // it were the sole grid item in a grid area whose edges coincide
            // with the content edges of the grid container".
            Box(modifier = modifier) {
                // The real (in-flow) grid renders first, unchanged except the
                // chain hand-off; abspos children can no longer reach its
                // placement or track sizing (the §9 partition above).
                GridPlacedGrid(
                    tracks = gridConfig.columnTracks,
                    columnCount = columnCount,
                    columnGap = horizontalSpacing,
                    rowGap = verticalSpacing,
                    rowHeights = resolvedRowHeights,
                    definiteWidth = definiteWidth,
                    cells = placements,
                    // Same wave-19 distribution inputs as the bare-grid shape
                    // above — the abspos grandchildren anchored inside these
                    // in-flow items inherit the shift (their static position
                    // follows their parent box, css-position-3 §3.1), which
                    // is exactly the descendant-static-position-002/003/004
                    // failure mode.
                    justify = justifyContent,
                    rtl = rtl,
                    modifier = Modifier,
                    content = placedCellContent
                )
                outOfFlowChildren.forEach { child ->
                    // §9.2 static position: sole-item alignment from the
                    // self properties, falling back to the container's
                    // align-items / justify-items (css-align-3 §6.2/§6.4) —
                    // pure resolution pinned in GridAbsposPartitionTest.
                    val (inlineSpec, blockSpec) = absposStaticSpecs(
                        childProperties = child.properties,
                        containerJustifyItems = justifyItems,
                        containerAlignItems = displayConfig.alignItems
                    )
                    Box(
                        // matchParentSize: span the content box WITHOUT
                        // participating in the wrapper Box's sizing — the
                        // abspos child must never size its containing block
                        // (css-position-3 §3), mirroring §9's "does not
                        // affect the sizing of the grid tracks".
                        modifier = Modifier.matchParentSize(),
                        // Position the (constraint-fitting) reported box at
                        // the resolved static alignment; abspos children
                        // paint after (above) the in-flow grid, matching
                        // CSS paint order for positioned boxes.
                        contentAlignment = staticOverlayAlignment(inlineSpec.base, blockSpec.base)
                    ) {
                        // Alignment is fully handled here — suppress the
                        // block-level self-alignment wrapper inside
                        // RenderComponent, same rule as the placed cells.
                        androidx.compose.runtime.CompositionLocalProvider(
                            ComponentRenderer.LocalSelfAlignmentHandled provides true
                        ) {
                            ComponentRenderer.RenderComponent(
                                child,
                                // Wave-8 unbounded measure (css-position-3
                                // §2.1: the box is sized by its OWN
                                // properties and may overflow); the block
                                // (vertical) spec rides along so overflowing
                                // ink keeps its safe-aware alignment — the
                                // same machinery the flex row path threads.
                                // Inline-axis overflow ink stays anchored at
                                // the reported box (documented crossOffset
                                // single-axis scope), not a silent gap.
                                itemModifier = ComponentRenderer.absposOverflowMeasure(
                                    blockSpec, crossIsVertical = true
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * One grid item's explicit placement request, RESOLVED to 1-based
     * numeric CSS grid lines (negative integers and named lines already
     * translated by [resolvePlacementSpec]). Null = auto. [colSpanReq] /
     * [rowSpanReq] carry a bare `span N` claim whose anchor line is auto —
     * the item auto-places but occupies N tracks (css-grid-1 §8.3.1).
     */
    internal data class GridPlacementSpec(
        val colStart: Int? = null,
        val colEnd: Int? = null,
        val rowStart: Int? = null,
        val rowEnd: Int? = null,
        val colSpanReq: Int? = null,
        val rowSpanReq: Int? = null
    )

    /** A resolved item: 0-based row/col plus its column and row spans. */
    internal data class PlacedItem(
        val childIndex: Int,
        val row: Int,
        val col: Int,
        val colSpan: Int,
        val rowSpan: Int = 1
    )

    /** One named area's rectangle in 1-based grid lines (ends exclusive). */
    internal data class AreaRect(
        val rowStart: Int,
        val rowEnd: Int,
        val colStart: Int,
        val colEnd: Int
    )

    /**
     * Pull the four explicit-placement longhands off a child's IR.
     * Legacy entry point kept for the campaign pinning tests — resolves
     * with no areas context and the given column count defaults.
     */
    internal fun extractPlacementSpec(properties: List<IRProperty>): GridPlacementSpec {
        val claims = com.styleconverter.runtime.core.placement.ItemPlacementExtractor
            .extract(properties).grid
        return GridPlacementSpec(
            colStart = claims.colStart,
            colEnd = claims.colEnd,
            rowStart = claims.rowStart,
            rowEnd = claims.rowEnd
        )
    }

    /**
     * Resolve one child's raw grid claims into numeric lines + span
     * requests (css-grid-1 §8.3). Pure function (JVM unit-tested).
     *
     * v2 placement routing: the claims come from the CHILD-side placement
     * union (core/placement/ItemPlacementExtractor — the same object
     * ComponentHost publishes as parent-data); this container consumes
     * ONLY its own kind's block; flex claims on the same child are inert.
     *
     *  - Negative integers count backward from the end of the explicit
     *    grid (§8.3: line -1 is the explicit grid's last line), so
     *    `grid-column: 1 / -1` spans every explicit column track.
     *  - <custom-ident> names resolve against the template-areas implicit
     *    line names: `<name>-start` for a *-start longhand, `<name>-end`
     *    for *-end (§8.3 + css-grid-1 §7.3.2). An ident with NO matching
     *    area resolves to the FIRST IMPLICIT line past the explicit grid
     *    (§8.3 "all implicit grid lines are counted as having that name"),
     *    verified against Chrome: PL_AreasDangling's `ghost` lands in
     *    implicit row 4 leaving an EMPTY implicit row 3.
     *  - `span N` with a definite opposite line resolves to a concrete
     *    start/end pair; with both lines auto it becomes a span REQUEST
     *    that auto-placement honors (§8.3.1).
     */
    internal fun resolvePlacementSpec(
        claims: com.styleconverter.runtime.core.placement.GridClaims,
        columnCount: Int,
        explicitRowCount: Int,
        areas: Map<String, AreaRect>
    ): GridPlacementSpec {
        // The explicit grid has trackCount+1 lines; line -1 = last line.
        fun negCol(n: Int?): Int? = n?.let { if (it < 0) columnCount + 2 + it else it }
        fun negRow(n: Int?): Int? = n?.let { if (it < 0) explicitRowCount + 2 + it else it }
        // Named lines: area edge if the ident names an area, else the first
        // implicit line past the explicit grid (index lastLine+1).
        fun rowLine(name: String?, isEnd: Boolean): Int? = name?.let { n ->
            areas[n]?.let { if (isEnd) it.rowEnd else it.rowStart }
                ?: (explicitRowCount + 2)
        }
        fun colLine(name: String?, isEnd: Boolean): Int? = name?.let { n ->
            areas[n]?.let { if (isEnd) it.colEnd else it.colStart }
                ?: (columnCount + 2)
        }
        var colStart = negCol(claims.colStart) ?: colLine(claims.colStartName, isEnd = false)
        var colEnd = negCol(claims.colEnd) ?: colLine(claims.colEndName, isEnd = true)
        var rowStart = negRow(claims.rowStart) ?: rowLine(claims.rowStartName, isEnd = false)
        var rowEnd = negRow(claims.rowEnd) ?: rowLine(claims.rowEndName, isEnd = true)
        var colSpanReq: Int? = null
        var rowSpanReq: Int? = null
        // §8.3.1: span against the opposite definite line, else a request.
        claims.colStartSpan?.let { s -> if (colEnd != null) colStart = colEnd!! - s else colSpanReq = s }
        claims.colEndSpan?.let { s -> if (colStart != null) colEnd = colStart!! + s else colSpanReq = s }
        claims.rowStartSpan?.let { s -> if (rowEnd != null) rowStart = rowEnd!! - s else rowSpanReq = s }
        claims.rowEndSpan?.let { s -> if (rowStart != null) rowEnd = rowStart!! + s else rowSpanReq = s }
        return GridPlacementSpec(colStart, colEnd, rowStart, rowEnd, colSpanReq, rowSpanReq)
    }

    /**
     * css-grid-1 §8.5 auto-placement for row-flow grids, in spec phase
     * order. Pure function (JVM unit-tested). Supports:
     *   1. items with definite row AND column → anchored exactly there
     *      (never moves the auto cursor);
     *   2. items with definite ROW only → first free column in that row,
     *      after any item this phase already placed there (sparse) or from
     *      the row start (dense) — this is the phase that places
     *      named-area claims (`grid-area: media` → row-locked item);
     *   3. remaining items in order: definite column → cursor drops rows
     *      until the tracks are free; fully-auto → row-major scan. Dense
     *      packing resets the cursor to the grid start per item (§8.5
     *      "dense" variant, PL_DenseBackfill).
     * Column spans clamp to the explicit column count; ROW spans occupy
     * real cells so later items flow around them, and the single-Layout
     * renderer sizes the item across all its rows.
     */
    internal fun placeItems(
        specs: List<GridPlacementSpec>,
        columnCount: Int,
        dense: Boolean = false
    ): List<PlacedItem> {
        val occupied = mutableSetOf<Pair<Int, Int>>() // (row, col)
        val out = arrayOfNulls<PlacedItem>(specs.size)

        fun spanOf(start: Int?, end: Int?, req: Int?): Int =
            req ?: if (start != null && end != null && end > start) end - start else 1

        fun colSpanOf(spec: GridPlacementSpec): Int =
            spanOf(spec.colStart, spec.colEnd, spec.colSpanReq).coerceIn(1, columnCount)

        fun rowSpanOf(spec: GridPlacementSpec): Int =
            spanOf(spec.rowStart, spec.rowEnd, spec.rowSpanReq).coerceAtLeast(1)

        fun fits(row: Int, col: Int, colSpan: Int, rowSpan: Int): Boolean =
            col + colSpan <= columnCount && (0 until rowSpan).none { r ->
                (0 until colSpan).any { c -> (row + r to col + c) in occupied }
            }

        fun mark(row: Int, col: Int, colSpan: Int, rowSpan: Int) {
            for (r in 0 until rowSpan) for (c in 0 until colSpan) {
                occupied.add(row + r to col + c)
            }
        }

        // ── Phase 1 (§8.5 step 1): both axes definite — anchored. ──
        specs.forEachIndexed { index, spec ->
            if (spec.colStart == null || spec.rowStart == null) return@forEachIndexed
            val row = (spec.rowStart - 1).coerceAtLeast(0)
            val col = (spec.colStart - 1).coerceIn(0, columnCount - 1)
            val cs = colSpanOf(spec)
            val rs = rowSpanOf(spec)
            out[index] = PlacedItem(index, row, col, cs, rs)
            mark(row, col, cs, rs)
        }

        // ── Phase 2 (§8.5 step 2): definite row, auto column. ──
        // Sparse keeps a per-row cursor ("past any items previously placed
        // in this row BY THIS STEP"); dense rescans from the row start.
        val rowCursor = mutableMapOf<Int, Int>()
        specs.forEachIndexed { index, spec ->
            if (out[index] != null || spec.rowStart == null) return@forEachIndexed
            val row = (spec.rowStart - 1).coerceAtLeast(0)
            val cs = colSpanOf(spec)
            val rs = rowSpanOf(spec)
            var col = if (dense) 0 else (rowCursor[row] ?: 0)
            // Walk right until the area is free; if the row runs out of
            // columns the item overlaps at the last legal start (degenerate
            // overflow case — CSS would grow implicit columns we don't have).
            while (col + cs <= columnCount && !fits(row, col, cs, rs)) col++
            if (col + cs > columnCount) col = (columnCount - cs).coerceAtLeast(0)
            out[index] = PlacedItem(index, row, col, cs, rs)
            mark(row, col, cs, rs)
            rowCursor[row] = col + cs
        }

        // ── Phase 3 (§8.5 step 4): everything else, shared cursor. ──
        var cursorRow = 0
        var cursorCol = 0
        specs.forEachIndexed { index, spec ->
            if (out[index] != null) return@forEachIndexed
            val cs = colSpanOf(spec)
            val rs = rowSpanOf(spec)
            // Dense packing: restart the scan at the grid origin per item.
            if (dense) { cursorRow = 0; cursorCol = 0 }
            if (spec.colStart != null) {
                // Definite column, auto row: drop the cursor down rows until
                // the requested tracks are free (§8.5 "column position is
                // definite" branch). If the cursor already passed that
                // column on the current row, start from the next row.
                val col = (spec.colStart - 1).coerceIn(0, columnCount - 1)
                var row = if (cursorCol > col) cursorRow + 1 else cursorRow
                while (!fits(row, col, cs, rs)) row++
                out[index] = PlacedItem(index, row, col, cs, rs)
                mark(row, col, cs, rs)
                cursorRow = row
                cursorCol = col + cs
            } else {
                // Fully auto: row-major scan from the cursor.
                var row = cursorRow
                var col = cursorCol
                while (true) {
                    if (col + cs > columnCount) { row++; col = 0; continue }
                    if (fits(row, col, cs, rs)) break
                    col++
                }
                out[index] = PlacedItem(index, row, col, cs, rs)
                mark(row, col, cs, rs)
                cursorRow = row
                cursorCol = col + cs
            }
        }
        return out.filterNotNull()
    }

    /**
     * Parse the v2 GridTemplateAreas wire into a rows×cols name grid.
     * Canonical shape (GridTemplateAreasProperty serializer):
     * `{"type":"areas","rows":[["a","a"],["b","."]]}` — rows of ARRAYS.
     * Legacy carriers (rows of strings / bare string rows) are folded in
     * for fixture compatibility. "." = unnamed cell (css-grid-1 §7.3).
     */
    internal fun parseAreasGrid(data: JsonElement?): List<List<String>>? {
        if (data == null) return null
        fun rowOf(el: JsonElement): List<String>? = when (el) {
            // v2: one row is already an array of cell names.
            is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                .takeIf { it.isNotEmpty() }
            // legacy: one row is a space-separated string.
            is JsonPrimitive -> el.contentOrNull?.trim()
                ?.removeSurrounding("\"")?.removeSurrounding("'")
                ?.takeIf { it.isNotEmpty() }
                ?.split(Regex("\\s+"))
            else -> null
        }
        val rowsEl: List<JsonElement>? = when (data) {
            is JsonObject -> (data["rows"] as? JsonArray) ?: (data["areas"] as? JsonArray)
            is JsonArray -> data
            else -> null
        }
        return rowsEl?.mapNotNull { rowOf(it) }?.takeIf { it.isNotEmpty() }
    }

    /** [parseAreasGrid] over a component's property list. */
    internal fun extractAreasGridV2(properties: List<IRProperty>): List<List<String>>? =
        properties.firstOrNull { it.type == "GridTemplateAreas" }?.let { parseAreasGrid(it.data) }

    /**
     * Fold a name grid into per-area rectangles (1-based lines, exclusive
     * ends). Non-rectangular repetitions keep their bounding box — invalid
     * per css-grid-1 §7.3 but harmless as a best effort.
     */
    internal fun buildAreaMap(grid: List<List<String>>?): Map<String, AreaRect> {
        if (grid == null) return emptyMap()
        val out = mutableMapOf<String, AreaRect>()
        grid.forEachIndexed { r, row ->
            row.forEachIndexed { c, name ->
                if (name == "." || name.isEmpty()) return@forEachIndexed
                val prev = out[name]
                out[name] = if (prev == null) {
                    AreaRect(r + 1, r + 2, c + 1, c + 2)
                } else {
                    AreaRect(
                        minOf(prev.rowStart, r + 1), maxOf(prev.rowEnd, r + 2),
                        minOf(prev.colStart, c + 1), maxOf(prev.colEnd, c + 2)
                    )
                }
            }
        }
        return out
    }

    /**
     * Container-level justify-items keyword → the JustifySelf domain used
     * for per-cell alignment (css-align-3 §6.2: justify-self:auto resolves
     * to the container's justify-items). Null when the container doesn't
     * declare it (callers then keep the child's own AUTO → start default).
     */
    internal fun extractJustifyItems(properties: List<IRProperty>): ComponentRenderer.JustifySelf? {
        val data = properties.firstOrNull { it.type == "JustifyItems" }?.data ?: return null
        return when (ValueExtractors.extractKeyword(data)?.uppercase()) {
            "START", "SELF_START", "SELF-START", "FLEX_START", "FLEX-START", "LEFT" ->
                ComponentRenderer.JustifySelf.START
            "END", "SELF_END", "SELF-END", "FLEX_END", "FLEX-END", "RIGHT" ->
                ComponentRenderer.JustifySelf.END
            "CENTER" -> ComponentRenderer.JustifySelf.CENTER
            "STRETCH" -> ComponentRenderer.JustifySelf.STRETCH
            "BASELINE" -> ComponentRenderer.JustifySelf.BASELINE
            "NORMAL" -> ComponentRenderer.JustifySelf.NORMAL
            else -> null
        }
    }

    /**
     * css-grid-1 §9 partition: split a grid container's (already
     * order-sorted) children into in-flow grid items and out-of-flow
     * (abspos/fixed) boxes, PRESERVING relative order inside each half.
     * The in-flow half is the ONLY input to placement — placements'
     * childIndex values index it 1:1 (the wave-8 index-alignment lesson,
     * pinned in GridAbsposPartitionTest). Pure over the IR.
     */
    /**
     * Wave-19 RC-B3: implicit column count for a TEMPLATE-LESS grid
     * (css-grid-1 §7.5 — the implicit grid). Replaces the legacy hard-coded
     * "2" that manufactured a phantom second track: a grid with no
     * grid-template-columns has ZERO explicit column tracks, and implicit
     * columns exist only where placement puts items —
     *   • row auto-flow (the default): the auto-placement cursor never
     *     leaves column 1, so ONE implicit column holds every item
     *     (each on its own row, §8.5 row-major cursor);
     *   • column auto-flow: the cursor opens a NEW implicit column per
     *     auto-placed item (§8.5 column-major variant) — one per claim;
     *   • explicit numeric grid-column lines grow the implicit grid to
     *     reach the requested tracks (line N needs N−1 columns; a start
     *     line with `span s` needs start+s−1; a bare span needs s).
     * The [claims] list is the POST-SPLICE in-flow set (abspos children are
     * partitioned out per §9 and display:contents wrappers were spliced by
     * ContentsUnboxing before RenderGrid ran) — counting pre-splice children
     * was the display-contents-alignment-002 phantom-track failure. Pure
     * over the claims — pinned in GridContentDistributionTest.
     */
    internal fun implicitColumnCount(
        autoFlow: GridAutoFlow,
        claims: List<com.styleconverter.runtime.core.placement.GridClaims>
    ): Int {
        // Auto-flow contribution: column flow opens a column per item.
        val flowCols = when (autoFlow) {
            GridAutoFlow.COLUMN, GridAutoFlow.COLUMN_DENSE -> claims.size
            // row / row-dense / plain dense: single implicit column.
            else -> 1
        }
        // Explicit-claim contribution: the furthest column any positive
        // numeric line or span request reaches (§7.5 implicit growth).
        // Negative lines resolve against the explicit grid and are skipped
        // here (a template-less explicit grid has only line 1/-1).
        val claimed = claims.maxOfOrNull { c ->
            // A span request occupies `s` tracks wherever it anchors.
            val span = c.colEndSpan ?: c.colStartSpan ?: 1
            maxOf(
                // Start line N (+ span) reaches track N+span−1.
                (c.colStart?.takeIf { it > 0 } ?: 1) + span - 1,
                // End line N bounds track N−1.
                (c.colEnd?.takeIf { it > 1 } ?: 0) - 1,
                // A bare span with auto lines still needs s tracks.
                span
            )
        } ?: 0
        // At least one implicit column always exists once items place (§7.5).
        return maxOf(1, flowCols, claimed)
    }

    internal fun partitionOutOfFlow(
        children: List<IRComponent>
    ): Pair<List<IRComponent>, List<IRComponent>> =
        // isOutOfFlowChild is the single §2.1 out-of-flow test the flex and
        // block paths already use (absolute | fixed; relative/sticky stay
        // in flow) — one owner, no drift.
        children.partition { !ComponentRenderer.isOutOfFlowChild(it.properties) }

    /**
     * css-grid-1 §9.2 static-position alignment for one out-of-flow child:
     * resolve BOTH axes of the sole-item-in-content-area alignment.
     * Returns (inline/horizontal spec, block/vertical spec) in the shared
     * [AbsposStaticAlignment.Spec] domain so the block axis can ride into
     * absposOverflowMeasure's safe-aware overflow offset unchanged.
     *
     * Per-axis resolution order (css-align-3 §6.2/§6.4 + css-position-3 §3.5):
     *   1. an EXPLICIT inset on the axis replaces the static position
     *      entirely — the child's own PositionApplier offsets from the
     *      containing-block corner, so the alignment stands down to START
     *      (a zero shift; anything else would double-offset);
     *   2. the child's own self property (align-self for block — via the
     *      wave-10 resolveCross, which reads the typed keyword AND the
     *      Generic `safe|unsafe <pos>` escape hatch; justify-self for
     *      inline — the typed ItemPlacementExtractor claim);
     *   3. `auto` falls back to the container's align-items /
     *      justify-items (§6.2: *-self:auto resolves to the parent's
     *      *-items value);
     *   4. nothing declared → START (normal behaves as start for a
     *      non-stretchable abspos box, same rule the placed-cell path uses).
     * Pure over the IR + enums — pinned in GridAbsposPartitionTest.
     */
    internal fun absposStaticSpecs(
        childProperties: List<IRProperty>,
        containerJustifyItems: ComponentRenderer.JustifySelf?,
        containerAlignItems: ComponentRenderer.AlignItems
    ): Pair<AbsposStaticAlignment.Spec, AbsposStaticAlignment.Spec> {
        // ── Inline (horizontal) axis ────────────────────────────────────
        val inline = if (AbsposStaticAlignment.hasCrossInset(childProperties, vertical = false)) {
            // Rule 1: left/right (or logical inline) inset wins the axis.
            AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, safe = false)
        } else {
            // Rule 2: the child's own justify-self claim (typed wire).
            justifyBase(
                com.styleconverter.runtime.core.placement.ItemPlacementExtractor
                    .extract(childProperties).justifySelf
            )
                // Rule 3: container justify-items (extractJustifyItems).
                ?.let { AbsposStaticAlignment.Spec(it, safe = false) }
                ?: justifyBase(containerJustifyItems)
                    ?.let { AbsposStaticAlignment.Spec(it, safe = false) }
                // Rule 4: default start.
                ?: AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, safe = false)
        }
        // ── Block (vertical) axis ───────────────────────────────────────
        val block = if (AbsposStaticAlignment.hasCrossInset(childProperties, vertical = true)) {
            // Rule 1: top/bottom (or logical block) inset wins the axis.
            AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, safe = false)
        } else {
            // Rule 2: align-self through the wave-10 resolver — the ONLY
            // reader of the safe/unsafe Generic wire, reused verbatim.
            AbsposStaticAlignment.resolveCross(childProperties)
                // Rule 3: container align-items (the DisplayConfig value the
                // flex paths already extract — one owner for the keyword).
                ?: alignItemsBase(containerAlignItems)
                    ?.let { AbsposStaticAlignment.Spec(it, safe = false) }
                // Rule 4: default start.
                ?: AbsposStaticAlignment.Spec(AbsposStaticAlignment.Base.START, safe = false)
        }
        return inline to block
    }

    /**
     * JustifySelf keyword → static-position Base, or null when the keyword
     * makes no static-position claim (css-align-3 §6.2: auto defers to the
     * container; normal/stretch behave as start for a non-stretchable
     * abspos box — returning null lets the resolution chain fall through
     * to the container level first). Same keyword grouping as
     * justifySelfToHorizontal so the two tables cannot disagree.
     */
    internal fun justifyBase(
        j: ComponentRenderer.JustifySelf?
    ): AbsposStaticAlignment.Base? = when (j) {
        ComponentRenderer.JustifySelf.START,
        ComponentRenderer.JustifySelf.SELF_START,
        ComponentRenderer.JustifySelf.FLEX_START,
        ComponentRenderer.JustifySelf.LEFT -> AbsposStaticAlignment.Base.START
        ComponentRenderer.JustifySelf.CENTER -> AbsposStaticAlignment.Base.CENTER
        ComponentRenderer.JustifySelf.END,
        ComponentRenderer.JustifySelf.SELF_END,
        ComponentRenderer.JustifySelf.FLEX_END,
        ComponentRenderer.JustifySelf.RIGHT -> AbsposStaticAlignment.Base.END
        // AUTO/NORMAL/STRETCH/BASELINE/null → no claim on this level.
        else -> null
    }

    /**
     * Container align-items → static-position Base for the block axis.
     * STRETCH (the initial value) and BASELINE make no positional claim for
     * an abspos box (stretch cannot stretch an out-of-flow child —
     * css-flexbox-1 §4.1's precedent, same as the flex path) → null lets
     * the chain hit the START default.
     */
    internal fun alignItemsBase(
        a: ComponentRenderer.AlignItems
    ): AbsposStaticAlignment.Base? = when (a) {
        ComponentRenderer.AlignItems.CENTER -> AbsposStaticAlignment.Base.CENTER
        ComponentRenderer.AlignItems.FLEX_END -> AbsposStaticAlignment.Base.END
        ComponentRenderer.AlignItems.FLEX_START -> AbsposStaticAlignment.Base.START
        // STRETCH (initial) / BASELINE → no static-position claim.
        else -> null
    }

    /**
     * Fold the two per-axis bases into one Compose 2D alignment for the
     * §9.2 overlay Box. BiasAlignment with the canonical -1/0/+1 biases —
     * exactly the values Alignment.TopStart/Center/BottomEnd etc. carry,
     * kept as a pure constructor so GridAbsposPartitionTest pins the
     * mapping by equality.
     */
    internal fun staticOverlayAlignment(
        inline: AbsposStaticAlignment.Base,
        block: AbsposStaticAlignment.Base
    ): Alignment = androidx.compose.ui.BiasAlignment(
        // Inline axis: START -1, CENTER 0, END +1 (LTR horizontal-tb).
        horizontalBias = when (inline) {
            AbsposStaticAlignment.Base.START -> -1f
            AbsposStaticAlignment.Base.CENTER -> 0f
            AbsposStaticAlignment.Base.END -> 1f
        },
        // Block axis: top -1, center 0, bottom +1.
        verticalBias = when (block) {
            AbsposStaticAlignment.Base.START -> -1f
            AbsposStaticAlignment.Base.CENTER -> 0f
            AbsposStaticAlignment.Base.END -> 1f
        }
    )

    /**
     * The WHOLE placed grid as one Layout: explicit column tracks on the
     * inline axis, template/auto/content rows on the block axis, cells at
     * their PLACED (row, col) offsets with both column AND row spans.
     * Replaces the old per-row GridPlacedRow (a Column of independent row
     * Layouts) which could not express row spans — a `grid-row: 3 / 5`
     * item only ever got its start row's height and implicit rows created
     * by row-end claims never materialized.
     *
     * Each direct child is one cell (a Box wrapping the grid item), 1:1
     * with [cells]. The measure policy:
     *   1. resolves every column track to a pixel width via
     *      [computeTrackWidths] (pure function — unit-tested on the JVM),
     *   2. resolves row heights: [rowHeights] entries are template /
     *      grid-auto-rows sizes; null entries are AUTO rows sized to the
     *      max measured height of their single-row cells (css-grid-1
     *      §7.2.1 content sizing, same simplification as column autos),
     *   3. measures cell i with the sum of its spanned tracks in BOTH axes
     *      (plus the gaps BETWEEN them, css-align-3) so the cell Box spans
     *      its area and its contentAlignment (justify-self / align-self)
     *      positions the item inside it,
     *   4. places cells at the cumulative offsets of their start tracks.
     *
     * [definiteWidth] mirrors the web reference's `width: fit-content`
     * default: when the grid declares NO definite width, flexible tracks
     * (fr / %) size to their max-content instead of splitting the incoming
     * constraint, and the grid reports its own footprint — the grid HUGS.
     *
     * Auto tracks need the item's max-content width (css-grid-1 §7.2.1);
     * we read `maxIntrinsicWidth` before the real measure pass.
     */
    @Composable
    private fun GridPlacedGrid(
        tracks: List<TrackSpec>?,
        columnCount: Int,
        columnGap: Dp,
        rowGap: Dp,
        rowHeights: List<Dp?>,
        definiteWidth: Boolean,
        cells: List<PlacedItem>,
        // Wave-19 RC-A2: container justify-content (css-align-3 §5.3) — the
        // whole track group's distribution inside the content box.
        justify: GridContentDistribution.Justify,
        // Wave-19 RC-A2: direction:rtl — inline start becomes the RIGHT edge
        // (column order reversed + group packed right, css-grid-1 §7.1).
        rtl: Boolean,
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        val gridModifier = modifier.then(
            if (definiteWidth) Modifier.fillMaxWidth() else Modifier
        )
        Layout(content = content, modifier = gridModifier) { measurables, constraints ->
            val gapPx = columnGap.roundToPx().toFloat()
            val rowGapPx = rowGap.roundToPx()
            val rowCount = rowHeights.size.coerceAtLeast(1)
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
            val rawSpecs = (0 until columnCount).map { i ->
                tracks?.getOrNull(i) ?: TrackSpec.Fr(1f)
            }
            // Indefinite grid width → flexible tracks degrade to content
            // sizing (fr under a max-content sizing pass behaves as auto,
            // css-grid-1 §7.2.4 "indefinite available space").
            val specs = if (definiteWidth) rawSpecs else rawSpecs.map { s ->
                when (s) {
                    is TrackSpec.Fr, is TrackSpec.Percent -> TrackSpec.Auto
                    is TrackSpec.MinMax -> TrackSpec.Auto
                    else -> s
                }
            }
            // Pre-resolved row heights in px; null = auto (content-sized
            // after measurement). Height hint for intrinsic width queries:
            // the cell's own row height when definite.
            val rowHpx: List<Int?> = (0 until rowCount).map { r ->
                rowHeights.getOrNull(r)?.roundToPx()
            }
            fun cellHeightPx(cell: PlacedItem): Int? {
                var h = 0
                for (r in cell.row until cell.row + cell.rowSpan) {
                    h += rowHpx.getOrNull(r) ?: return null // any auto row → defer
                }
                // Gaps BETWEEN spanned rows belong to the cell area.
                return h + rowGapPx * (cell.rowSpan - 1).coerceAtLeast(0)
            }
            // Max-content width per TRACK: the widest single-track cell
            // that STARTS there (spanning cells excluded — their space
            // distribution is a §7.2.3 refinement we skip). Only consulted
            // for auto/fit tracks.
            val intrinsics = (0 until columnCount).map { t ->
                when (specs.getOrNull(t)) {
                    is TrackSpec.Auto, is TrackSpec.Fit ->
                        cells.withIndex().filter { it.value.col == t && it.value.colSpan == 1 }
                            // Guarded read (campaign audit 2026-08-10): a cell
                            // whose subtree reaches a SubcomposeLayout renderer
                            // (multicol's BoxWithConstraints, scroll, …) THROWS
                            // on the raw query, and an unguarded throw would
                            // kill the whole capture composition, not just
                            // mis-size this track. mapNotNull drops refusals so
                            // the track still sizes from the cells that answer.
                            .mapNotNull { (i, cell) ->
                                IntrinsicChannel.probe(
                                    logTag = "GridRenderer",
                                    refusalContext = "css-grid-1 §7.2 auto-track " +
                                        "max-content contribution skipped — this " +
                                        "grid item's subtree has no intrinsic " +
                                        "channel; the track sizes from its other " +
                                        "items (or collapses to the 0 base when " +
                                        "none answer)."
                                ) {
                                    measurables[i].maxIntrinsicWidth(
                                        cellHeightPx(cell) ?: Int.MAX_VALUE
                                    )
                                }?.toFloat()
                            // Same 0 base as an EMPTY track: an all-refused
                            // track degrades to its flexible/base share, which
                            // is what CSS gives a track with no content-sized
                            // contribution either.
                            }.maxOrNull() ?: 0f
                    else -> 0f
                }
            }
            val widths = computeTrackWidths(
                specs, intrinsics, containerW, gapPx, stretch = definiteWidth
            )
            // The track group's own footprint (sum + inner gaps) — the hug
            // width for indefinite grids AND the distribution no-op extent.
            val footprint = (widths.sum() + gapPx * (widths.size - 1).coerceAtLeast(0)).toInt()
            // Grid width: definite grids report the bounded container width;
            // indefinite ones report the tracks' own footprint (the hug).
            // (Hoisted above measurement in wave 19 — the distribution
            // extent below must equal the box the group aligns within.)
            val gridW = if (definiteWidth && constraints.hasBoundedWidth)
                constraints.maxWidth
            else
                footprint.coerceAtMost(
                    if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
                )
            // Wave-19 RC-A2: physical LEFT-edge origin of every column track
            // — css-align-3 §5.3 content distribution (justify-content) plus
            // the rtl mirror, replacing the old LTR start-packed prefix sums.
            // Indefinite grids pass their own footprint → leftover 0 →
            // distribution no-op, rtl a pure order mirror inside the hug.
            val origins = GridContentDistribution.trackOrigins(
                trackWidths = widths.map { it.toDouble() },
                gap = gapPx.toDouble(),
                contentExtent = gridW.toDouble(),
                justify = justify,
                rtl = rtl
            )
            // A cell's physical left edge: the leftmost origin among its
            // spanned tracks (in LTR that's the start track's origin; in RTL
            // the LAST spanned logical track sits leftmost). NOTE: spanning
            // cells under space-* keep the plain gap in cellWidth below, so
            // a widened distribution gap inside a span is not covered yet —
            // documented, not silent (no such shape in the corpus).
            fun cellX(cell: PlacedItem): Float =
                (cell.col until (cell.col + cell.colSpan).coerceAtMost(columnCount))
                    .minOfOrNull { origins.getOrElse(it) { 0.0 }.toFloat() } ?: 0f
            fun cellWidth(cell: PlacedItem): Float {
                var w = 0f
                for (i in cell.col until (cell.col + cell.colSpan).coerceAtMost(columnCount)) {
                    w += widths.getOrElse(i) { 0f }
                }
                // Gaps BETWEEN spanned tracks belong to the cell area.
                return w + gapPx * (cell.colSpan - 1).coerceAtLeast(0)
            }
            // Measure every cell: definite both-axes cells get exact
            // constraints (the Box then stretches/aligns its item); cells
            // touching an auto row get a fixed width and free height so
            // their measured height can SIZE that row.
            val placeables = measurables.mapIndexed { i, m ->
                val cell = cells.getOrNull(i)
                val w = cell?.let { cellWidth(it) }?.toInt()?.coerceAtLeast(0) ?: 0
                val h = cell?.let { cellHeightPx(it) }
                m.measure(
                    if (h != null)
                        Constraints.fixed(w, h)
                    else
                        Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = constraints.maxHeight)
                )
            }
            // Resolve AUTO rows to the tallest single-row cell they host
            // (spanning cells excluded, mirroring the column intrinsics
            // simplification). Rows with no cells collapse to 0 — matching
            // Chrome, where PL_AreasDangling's empty implicit row 3 is 0px
            // tall between two 6px gaps.
            val resolvedRowH = IntArray(rowCount) { r ->
                rowHpx[r] ?: cells.withIndex()
                    .filter { it.value.row == r && it.value.rowSpan == 1 }
                    .maxOfOrNull { (i, _) -> placeables[i].height }
                ?: 0
            }
            // Cumulative row start offsets.
            val rowOffsets = IntArray(rowCount)
            for (r in 1 until rowCount) {
                rowOffsets[r] = rowOffsets[r - 1] + resolvedRowH[r - 1] + rowGapPx
            }
            val totalH = resolvedRowH.sum() + rowGapPx * (rowCount - 1).coerceAtLeast(0)
            layout(gridW, totalH.coerceIn(constraints.minHeight, constraints.maxHeight)) {
                placeables.forEachIndexed { i, p ->
                    val cell = cells.getOrNull(i) ?: return@forEachIndexed
                    // place(), NOT placeRelative(): the wave-19 origins are
                    // already PHYSICAL (rtl mirrored by the pure math above);
                    // placeRelative would re-mirror them under the ambient
                    // LocalLayoutDirection=Rtl the RTL provider sets —
                    // a double flip. In LTR place == placeRelative exactly,
                    // so every committed LTR baseline is byte-identical.
                    p.place(
                        cellX(cell).toInt(),
                        rowOffsets.getOrElse(cell.row) { 0 }
                    )
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
        gapPx: Float,
        // When false (grid width indefinite — the web fit-content hug),
        // auto tracks do NOT absorb leftover container space: they stay at
        // max-content. Default true preserves the historical behaviour for
        // definite-width grids and existing unit tests.
        stretch: Boolean = true
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
            // (Chrome's normal-alignment auto-track stretch) — but ONLY
            // when the grid's width is definite; a fit-content grid keeps
            // autos at max-content (the hug).
            val autoIdx = if (stretch) specs.indices.filter { specs[it] is TrackSpec.Auto } else emptyList()
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

    // NOTE (wave 5): RenderGridWithTemplateAreas + extractGridTemplateAreas +
    // extractChildAreaName were DELETED here. The branch keyed child lookup
    // on a "GridArea" IR type the converter never emits (grid-area expands
    // to grid-row-start in GridAreaExpander), rendered every child as an
    // empty weighted cell, and was unreachable for the v2 areas wire anyway.
    // Named-area placement now flows through resolvePlacementSpec/placeItems
    // above, matching the browser's §8.3/§8.5 semantics pixel-for-pixel.

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

    // NOTE (v2 placement contract): GridRenderer's private extractAlignSelf
    // copy was DELETED here — it was the exact "fix lands in a dead file"
    // hazard the contract forbids (this copy still lacked the
    // ANCHOR_CENTER→CENTER fold the live flex path gained in wave 4).
    // Grid cells now read alignment claims from the single ITEM union
    // (core/placement/ItemPlacementExtractor) in RenderGrid above; no
    // committed baseline exercises anchor-center inside a grid, so the
    // consolidation is pixel-neutral on the frozen set.

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

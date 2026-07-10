package com.styleconverter.runtime.layout.grid

// Fidelity wave 1 — pinning tests for explicit grid placement
// (grid-column/row-start/end lines + spans, css-grid-1 §8.5 simplified
// auto-placement) and for the indefinite-width track sizing hug.
//
// Reference behaviour (web harness, grid-2col tree fixture):
//   G2_Placement: `a` (grid-column: 1 / 3) spans the full first row ALONE,
//   b + c auto-flow into row 2, `d` (col 2, row 3) sits alone bottom-right.
//   G2_MixedTracks: `c` (1 / 3) spans row 2; `d` (2 / 3) sits row 3 col 2.
// The old renderer chunked children 2-by-2 and ignored the lines entirely.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridPlacementTest {

    private fun prop(t: String, j: String) = IRProperty(t, Json.parseToJsonElement(j))

    // ── extractPlacementSpec: IR wire shape ──────────────────────────────

    @Test
    fun `placement longhands parse from the number wire shape`() {
        // Canonical converter emission: {"type":"number","number":N}.
        val spec = GridRenderer.extractPlacementSpec(listOf(
            prop("GridColumnStart", """{"type":"number","number":2}"""),
            prop("GridColumnEnd", """{"type":"number","number":3}"""),
            prop("GridRowStart", """{"type":"number","number":3}"""),
            prop("GridRowEnd", """{"type":"number","number":4}""")
        ))
        assertEquals(2, spec.colStart)
        assertEquals(3, spec.colEnd)
        assertEquals(3, spec.rowStart)
        assertEquals(4, spec.rowEnd)
    }

    @Test
    fun `absent placement properties yield an all-auto spec`() {
        val spec = GridRenderer.extractPlacementSpec(listOf(
            prop("Height", """{"type":"length","px":40.0}""")
        ))
        assertNull(spec.colStart)
        assertNull(spec.rowStart)
    }

    // ── placeItems: the G2_Placement scenario ────────────────────────────

    @Test
    fun `G2_Placement lines produce span row then autoflow then anchored cell`() {
        val placements = GridRenderer.placeItems(
            listOf(
                // a: grid-column 1 / 3 (span both tracks), row auto
                GridRenderer.GridPlacementSpec(colStart = 1, colEnd = 3),
                // b, c: fully auto
                GridRenderer.GridPlacementSpec(),
                GridRenderer.GridPlacementSpec(),
                // d: col 2 / 3, row 3 / 4
                GridRenderer.GridPlacementSpec(colStart = 2, colEnd = 3, rowStart = 3, rowEnd = 4)
            ),
            columnCount = 2
        )
        // a spans row 0 alone.
        assertEquals(GridRenderer.PlacedItem(0, row = 0, col = 0, colSpan = 2), placements[0])
        // b and c auto-flow into row 1.
        assertEquals(GridRenderer.PlacedItem(1, row = 1, col = 0, colSpan = 1), placements[1])
        assertEquals(GridRenderer.PlacedItem(2, row = 1, col = 1, colSpan = 1), placements[2])
        // d is anchored at row 3 (0-based 2), column 2 (0-based 1).
        assertEquals(GridRenderer.PlacedItem(3, row = 2, col = 1, colSpan = 1), placements[3])
    }

    @Test
    fun `G2_MixedTracks explicit spans drop below the autoflow cursor`() {
        val placements = GridRenderer.placeItems(
            listOf(
                GridRenderer.GridPlacementSpec(),                      // a → (0,0)
                GridRenderer.GridPlacementSpec(),                      // b → (0,1)
                GridRenderer.GridPlacementSpec(colStart = 1, colEnd = 3), // c → row 1 span 2
                GridRenderer.GridPlacementSpec(colStart = 2, colEnd = 3)  // d → row 2 col 1
            ),
            columnCount = 2
        )
        assertEquals(GridRenderer.PlacedItem(0, 0, 0, 1), placements[0])
        assertEquals(GridRenderer.PlacedItem(1, 0, 1, 1), placements[1])
        assertEquals(GridRenderer.PlacedItem(2, 1, 0, 2), placements[2])
        assertEquals(GridRenderer.PlacedItem(3, 2, 1, 1), placements[3])
    }

    @Test
    fun `plain auto items keep the legacy chunked layout`() {
        // Byte-compat: four auto items in a 2-col grid = 2×2, same as the
        // old children.chunked(columnCount) behaviour.
        val placements = GridRenderer.placeItems(
            List(4) { GridRenderer.GridPlacementSpec() },
            columnCount = 2
        )
        assertEquals(GridRenderer.PlacedItem(0, 0, 0, 1), placements[0])
        assertEquals(GridRenderer.PlacedItem(1, 0, 1, 1), placements[1])
        assertEquals(GridRenderer.PlacedItem(2, 1, 0, 1), placements[2])
        assertEquals(GridRenderer.PlacedItem(3, 1, 1, 1), placements[3])
    }

    // ── computeTrackWidths: indefinite-width hug ─────────────────────────

    @Test
    fun `auto tracks do not stretch when the grid width is indefinite`() {
        // stretch=false is the fit-content hug: autos stay at max-content
        // instead of absorbing the container's leftover space.
        val widths = GridRenderer.computeTrackWidths(
            specs = listOf(GridRenderer.TrackSpec.Auto, GridRenderer.TrackSpec.Auto),
            maxContentWidths = listOf(54f, 54f),
            containerWidth = 358f,
            gapPx = 6f,
            stretch = false
        )
        assertEquals(54f, widths[0], 0.01f)
        assertEquals(54f, widths[1], 0.01f)
    }

    @Test
    fun `auto tracks still stretch for definite-width grids`() {
        // Default stretch=true preserves the historical Chrome-like
        // auto-track stretch for grids that declare a width.
        val widths = GridRenderer.computeTrackWidths(
            specs = listOf(GridRenderer.TrackSpec.Auto, GridRenderer.TrackSpec.Auto),
            maxContentWidths = listOf(54f, 54f),
            containerWidth = 320f,
            gapPx = 0f
        )
        assertEquals(160f, widths[0], 0.01f)
        assertEquals(160f, widths[1], 0.01f)
    }

    // ═══ Wave 5 — spans / names / negatives / dense / row-locked phase ═══
    // Every expectation below is verified against the fresh PL_* web
    // captures (Chrome geometry decoded pixel-by-pixel from
    // tools/visual/report/images/web on the wave-5 run).

    private fun claims(
        colStart: Int? = null, colEnd: Int? = null,
        rowStart: Int? = null, rowEnd: Int? = null,
        colStartSpan: Int? = null, rowStartSpan: Int? = null,
        rowEndSpan: Int? = null,
        rowStartName: String? = null
    ) = com.styleconverter.runtime.core.placement.GridClaims(
        colStart = colStart, colEnd = colEnd, rowStart = rowStart, rowEnd = rowEnd,
        colStartSpan = colStartSpan, rowStartSpan = rowStartSpan,
        rowEndSpan = rowEndSpan, rowStartName = rowStartName
    )

    @Test
    fun `negative column end counts from the explicit grid end`() {
        // PL_LineSpans `c`: grid-column 1 / -1 in a 3-track grid → span 3.
        val spec = GridRenderer.resolvePlacementSpec(
            claims(colStart = 1, colEnd = -1), columnCount = 3,
            explicitRowCount = 0, areas = emptyMap()
        )
        assertEquals(1, spec.colStart)
        assertEquals(4, spec.colEnd)
    }

    @Test
    fun `bare span start becomes a span request`() {
        // PL_LineSpans `a`: grid-column-start: span 2, both lines auto.
        val spec = GridRenderer.resolvePlacementSpec(
            claims(colStartSpan = 2), columnCount = 3,
            explicitRowCount = 0, areas = emptyMap()
        )
        assertNull(spec.colStart)
        assertEquals(2, spec.colSpanReq)
    }

    @Test
    fun `area name on row-start resolves to the area's row line`() {
        // grid-area:media → converter emits ONLY grid-row-start:media; the
        // browser resolves the ident to the media area's start row and
        // auto-flows the column (PL_AreasCard web: media (1,1), title (1,2),
        // body (2,1) — NOT full-area rectangles).
        val areas = mapOf(
            "media" to GridRenderer.AreaRect(1, 3, 1, 2),
            "body" to GridRenderer.AreaRect(2, 3, 2, 3)
        )
        val media = GridRenderer.resolvePlacementSpec(
            claims(rowStartName = "media"), columnCount = 2,
            explicitRowCount = 2, areas = areas
        )
        assertEquals(1, media.rowStart)
        assertNull(media.colStart)
        val body = GridRenderer.resolvePlacementSpec(
            claims(rowStartName = "body"), columnCount = 2,
            explicitRowCount = 2, areas = areas
        )
        assertEquals(2, body.rowStart)
    }

    @Test
    fun `dangling area name lands on the first implicit line`() {
        // PL_AreasDangling `ghost`: no such area → §8.3 counts implicit
        // lines as bearing the name → row 4 in a 2-explicit-row grid,
        // leaving implicit row 3 EMPTY (web: ghost at y=120 = 40+6+40+6+0+6
        // past the padding edge).
        val spec = GridRenderer.resolvePlacementSpec(
            claims(rowStartName = "ghost"), columnCount = 2,
            explicitRowCount = 2, areas = mapOf("a" to GridRenderer.AreaRect(1, 2, 1, 3))
        )
        assertEquals(4, spec.rowStart)
    }

    @Test
    fun `row-locked items place before autos - the PL_AreasDecoupled scenario`() {
        // Children in DOM order: box1(row2), box2(row1), box3(row2) — all
        // columns auto. §8.5 step 2 places them row-by-row with per-row
        // cursors; web shows box1 (2,1), box2 (1,1), box3 (2,2).
        val placements = GridRenderer.placeItems(
            listOf(
                GridRenderer.GridPlacementSpec(rowStart = 2),
                GridRenderer.GridPlacementSpec(rowStart = 1),
                GridRenderer.GridPlacementSpec(rowStart = 2)
            ),
            columnCount = 3
        )
        assertEquals(GridRenderer.PlacedItem(0, 1, 0, 1), placements[0])
        assertEquals(GridRenderer.PlacedItem(1, 0, 0, 1), placements[1])
        assertEquals(GridRenderer.PlacedItem(2, 1, 1, 1), placements[2])
    }

    @Test
    fun `row spans occupy cells and autos flow around them - PL_LineSpans`() {
        // a: span 2 (col request); b: auto; c: 1 / -1 (resolved 1..4);
        // d: col 2, rows 3..5 → rowSpan 2 with implicit 4th row.
        val placements = GridRenderer.placeItems(
            listOf(
                GridRenderer.GridPlacementSpec(colSpanReq = 2),
                GridRenderer.GridPlacementSpec(),
                GridRenderer.GridPlacementSpec(colStart = 1, colEnd = 4),
                GridRenderer.GridPlacementSpec(colStart = 2, rowStart = 3, rowEnd = 5)
            ),
            columnCount = 3
        )
        // Web geometry: a (1,1-2), b (1,3), c (2,1-3), d (3..4, 2).
        assertEquals(GridRenderer.PlacedItem(0, 0, 0, 2), placements[0])
        assertEquals(GridRenderer.PlacedItem(1, 0, 2, 1), placements[1])
        assertEquals(GridRenderer.PlacedItem(2, 1, 0, 3), placements[2])
        assertEquals(GridRenderer.PlacedItem(3, 2, 1, 1, rowSpan = 2), placements[3])
    }

    @Test
    fun `dense packing backfills holes - PL_DenseBackfill`() {
        // wide: span 2; u1 auto; tall: col 2/4 row 2 (anchored);
        // u2/u3 auto — dense rescans from the origin per item.
        val placements = GridRenderer.placeItems(
            listOf(
                GridRenderer.GridPlacementSpec(colSpanReq = 2),
                GridRenderer.GridPlacementSpec(),
                GridRenderer.GridPlacementSpec(colStart = 2, colEnd = 4, rowStart = 2),
                GridRenderer.GridPlacementSpec(),
                GridRenderer.GridPlacementSpec()
            ),
            columnCount = 3,
            dense = true
        )
        // Web geometry: wide (1,1-2), u1 (1,3), tall (2,2-3), u2 (2,1), u3 (3,1).
        assertEquals(GridRenderer.PlacedItem(0, 0, 0, 2), placements[0])
        assertEquals(GridRenderer.PlacedItem(1, 0, 2, 1), placements[1])
        assertEquals(GridRenderer.PlacedItem(2, 1, 1, 2), placements[2])
        assertEquals(GridRenderer.PlacedItem(3, 1, 0, 1), placements[3])
        assertEquals(GridRenderer.PlacedItem(4, 2, 0, 1), placements[4])
    }

    @Test
    fun `span requests on both axes auto-place - PL_AreaLineSyntax c`() {
        // a: 1/1/2/3; b: 2/2/4/4; c: span 2 / span 1 → auto-placed at the
        // first slot whose 2×1 area is free = rows 2-3, col 1 (web-verified).
        val placements = GridRenderer.placeItems(
            listOf(
                GridRenderer.GridPlacementSpec(colStart = 1, colEnd = 3, rowStart = 1, rowEnd = 2),
                GridRenderer.GridPlacementSpec(colStart = 2, colEnd = 4, rowStart = 2, rowEnd = 4),
                GridRenderer.GridPlacementSpec(rowSpanReq = 2, colSpanReq = 1)
            ),
            columnCount = 3
        )
        assertEquals(GridRenderer.PlacedItem(2, 1, 0, 1, rowSpan = 2), placements[2])
    }

    // ── Areas-grid parsing (v2 wire) + area map ─────────────────────────

    @Test
    fun `v2 areas wire parses rows of arrays`() {
        val grid = GridRenderer.parseAreasGrid(
            Json.parseToJsonElement("""{"type":"areas","rows":[["head","head"],["side","main"]]}""")
        )
        assertEquals(listOf(listOf("head", "head"), listOf("side", "main")), grid)
    }

    @Test
    fun `area map folds cells into 1-based rectangles`() {
        val map = GridRenderer.buildAreaMap(
            listOf(listOf("head", "head", "head"), listOf("side", "main", "main"))
        )
        assertEquals(GridRenderer.AreaRect(1, 2, 1, 4), map["head"])
        assertEquals(GridRenderer.AreaRect(2, 3, 2, 4), map["main"])
        assertEquals(GridRenderer.AreaRect(2, 3, 1, 2), map["side"])
        assertNull(map["."])
    }
}

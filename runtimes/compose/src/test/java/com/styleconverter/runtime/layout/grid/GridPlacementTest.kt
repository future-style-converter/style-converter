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
}

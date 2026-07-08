package com.styleconverter.test.style.layout.grid

// Phase 7b grid extractor unit tests. Each fixture shape is taken verbatim
// from examples/properties/layout/grid-*.json so the extractor stays in sync
// with the CSS parser's output format.

import com.styleconverter.test.style.layout.GridLine
import com.styleconverter.test.style.layout.GridLinePair
import com.styleconverter.test.style.layout.GridPlacement
import com.styleconverter.test.style.layout.GridTrackList
import com.styleconverter.test.style.layout.Track
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GridLayoutExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    // --- Track list parsing -------------------------------------------------

    @Test fun `fr units parse to Flexible tracks`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"fr":1.0},{"fr":2.0},{"fr":1.0}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(3, list.tracks.size)
        assertEquals(Track.Flexible(1f), list.tracks[0])
        assertEquals(Track.Flexible(2f), list.tracks[1])
        assertEquals(Track.Flexible(1f), list.tracks[2])
    }

    @Test fun `px tracks parse to Fixed`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"px":80.0},{"px":120.0},{"px":80.0}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(Track.Fixed(80f), list.tracks[0])
        assertEquals(Track.Fixed(120f), list.tracks[1])
    }

    @Test fun `percent tracks parse to Percent`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"percent":25.0},{"percent":50.0},{"percent":25.0}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(Track.Percent(25f), list.tracks[0])
        assertEquals(Track.Percent(50f), list.tracks[1])
    }

    @Test fun `auto keyword parses to Track Auto`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"keyword":"auto"},{"keyword":"auto"}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(Track.Auto, list.tracks[0])
    }

    @Test fun `mixed fr px percent preserved in order`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"px":80.0},{"fr":1.0},{"percent":20.0}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(3, list.tracks.size)
        assertTrue(list.tracks[0] is Track.Fixed)
        assertTrue(list.tracks[1] is Track.Flexible)
        assertTrue(list.tracks[2] is Track.Percent)
    }

    @Test fun `minmax parses to MinMax track`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"minmax":{"min":{"px":80.0},"max":{"fr":1.0}}}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        val mm = list.tracks[0] as Track.MinMax
        assertEquals(Track.Fixed(80f), mm.min)
        assertEquals(Track.Flexible(1f), mm.max)
    }

    @Test fun `repeat N expands inline`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"repeat":{"count":4,"tracks":[{"fr":1.0}]}}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        // 4 fr flexible tracks — all equal → applier picks Fixed(4) cells.
        assertEquals(4, list.tracks.size)
        assertTrue(list.tracks.all { it == Track.Flexible(1f) })
    }

    @Test fun `repeat auto-fill minmax yields Adaptive`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"repeat":{"count":"auto-fill","tracks":[{"minmax":{"min":{"px":100.0},"max":{"fr":1.0}}}]}}]""")
        ))
        val a = e.templateColumns as GridTrackList.Adaptive
        assertEquals(100f, a.minSize, 0.01f)
        assertEquals(false, a.autoFit)
    }

    @Test fun `repeat auto-fit minmax yields Adaptive with autoFit true`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"repeat":{"count":"auto-fit","tracks":[{"minmax":{"min":{"px":120.0},"max":{"fr":1.0}}}]}}]""")
        ))
        val a = e.templateColumns as GridTrackList.Adaptive
        assertEquals(120f, a.minSize, 0.01f)
        assertTrue(a.autoFit)
    }

    @Test fun `fit-content parses to FitContent track`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"fitContent":{"px":200.0}}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(Track.FitContent(200f), list.tracks[0])
    }

    @Test fun `primitive fr string parses`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", "\"1fr\"")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(Track.Flexible(1f), list.tracks[0])
    }

    @Test fun `min-content and max-content parse to Intrinsic`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateColumns", """[{"keyword":"min-content"},{"keyword":"max-content"}]""")
        ))
        val list = e.templateColumns as GridTrackList.Explicit
        assertEquals(Track.Intrinsic(Track.IntrinsicKind.MinContent), list.tracks[0])
        assertEquals(Track.Intrinsic(Track.IntrinsicKind.MaxContent), list.tracks[1])
    }

    // --- Template areas -----------------------------------------------------

    @Test fun `template-areas 2x2 parses to grid`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateAreas", """["header header","main side"]""")
        ))
        val areas = e.templateAreas!!
        assertEquals(2, areas.size)
        assertEquals(listOf("header", "header"), areas[0])
        assertEquals(listOf("main", "side"), areas[1])
    }

    @Test fun `template-areas 3x3 with dot cells`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridTemplateAreas", """["a a a",". b .","c c c"]""")
        ))
        val areas = e.templateAreas!!
        assertEquals(3, areas.size)
        assertEquals(".", areas[1][0])
        assertEquals("b", areas[1][1])
    }

    // --- Grid placement -----------------------------------------------------

    @Test fun `grid-area named from primitive string`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridArea", "\"header\"")
        ))
        val p = e.gridArea as GridPlacement.Named
        assertEquals("header", p.name)
    }

    @Test fun `grid-column-start and end yield a GridLinePair`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridColumnStart", "1"),
            pair("GridColumnEnd", "3"),
        ))
        val pair = e.gridColumn!!
        assertEquals(GridLine.Line(1), pair.start)
        assertEquals(GridLine.Line(3), pair.end)
    }

    @Test fun `grid-row span parses`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridRowStart", "1"),
            pair("GridRowEnd", "\"span 2\""),
        ))
        val pair = e.gridRow!!
        assertEquals(GridLine.Line(1), pair.start)
        assertEquals(GridLine.Span(2), pair.end)
    }

    @Test fun `grid-area four-line object parses`() {
        val e = GridLayoutExtractor.extract(listOf(
            pair("GridArea", """{"rowStart":1,"columnStart":2,"rowEnd":3,"columnEnd":4}""")
        ))
        val lines = e.gridArea as GridPlacement.Lines
        assertEquals(GridLine.Line(1), lines.rowStart)
        assertEquals(GridLine.Line(2), lines.columnStart)
        assertEquals(GridLine.Line(3), lines.rowEnd)
        assertEquals(GridLine.Line(4), lines.columnEnd)
    }

    // --- Auto flow ----------------------------------------------------------

    @Test fun `grid-auto-flow row parses`() {
        val e = GridLayoutExtractor.extract(listOf(pair("GridAutoFlow", "\"row\"")))
        assertEquals(com.styleconverter.test.style.layout.GridAutoFlow.Row, e.autoFlow)
    }

    @Test fun `grid-auto-flow column dense parses`() {
        val e = GridLayoutExtractor.extract(listOf(pair("GridAutoFlow", "\"column dense\"")))
        assertEquals(com.styleconverter.test.style.layout.GridAutoFlow.ColumnDense, e.autoFlow)
    }

    // --- Alignment keywords -------------------------------------------------

    @Test fun `justify-items keywords map correctly`() {
        val e = GridLayoutExtractor.extract(listOf(pair("JustifyItems", "\"center\"")))
        assertEquals(com.styleconverter.test.style.layout.AlignmentKeyword.Center, e.justifyItems)
    }

    @Test fun `justify-self stretch parses`() {
        val e = GridLayoutExtractor.extract(listOf(pair("JustifySelf", "\"stretch\"")))
        assertEquals(com.styleconverter.test.style.layout.AlignmentKeyword.Stretch, e.justifySelf)
    }

    // --- Applier plan translation ------------------------------------------

    @Test fun `applier uniform fr yields FixedCount cells`() {
        val list = GridTrackList.Explicit(listOf(
            Track.Flexible(1f), Track.Flexible(1f), Track.Flexible(1f)
        ))
        val plan = GridLayoutApplier.cellsPlan(list)
        assertEquals(GridCellsPlan.FixedCount(3), plan)
    }

    @Test fun `applier uniform px yields FixedSize cells`() {
        val list = GridTrackList.Explicit(listOf(Track.Fixed(80f), Track.Fixed(80f)))
        val plan = GridLayoutApplier.cellsPlan(list) as GridCellsPlan.FixedSize
        assertEquals(80f, plan.size.value, 0.01f)
    }

    @Test fun `applier adaptive yields Adaptive cells`() {
        val a = GridTrackList.Adaptive(minSize = 100f, autoFit = false)
        val plan = GridLayoutApplier.cellsPlan(a) as GridCellsPlan.Adaptive
        assertEquals(100f, plan.minSize.value, 0.01f)
    }

    @Test fun `applier mixed fr and px yields Unsupported`() {
        val list = GridTrackList.Explicit(listOf(Track.Fixed(80f), Track.Flexible(1f), Track.Percent(20f)))
        assertEquals(GridCellsPlan.Unsupported, GridLayoutApplier.cellsPlan(list))
    }

    @Test fun `applier null track list yields Unsupported`() {
        assertEquals(GridCellsPlan.Unsupported, GridLayoutApplier.cellsPlan(null))
    }
}

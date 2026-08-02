package com.styleconverter.runtime.columns

// JUnit4 plumbing — matches the suite style used by FragmentGeometryTest.
import androidx.compose.ui.graphics.Color
import com.styleconverter.runtime.PropertyRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins [GapDecorationExtractor] against the wave-24 WIRE CONTRACT the
 * converter lane implements: RowRule* shaped byte-identically to the
 * existing ColumnRule* longhands, UPPERCASE break/overlap keywords, and
 * length-px insets that may be negative.
 */
class GapDecorationExtractorTest {

    // Wire helpers — the exact shapes a live conversion emits.
    private fun length(px: Double): JsonElement = buildJsonObject {
        put("type", "length"); put("px", px)
    }

    private fun srgb(r: Double, g: Double, b: Double): JsonElement = buildJsonObject {
        put("srgb", buildJsonObject { put("r", r); put("g", g); put("b", b) })
    }

    @Test
    fun `decodes the full row and column families`() {
        val config = GapDecorationExtractor.extract(
            listOf(
                "ColumnRuleStyle" to JsonPrimitive("SOLID"),
                "ColumnRuleWidth" to length(10.0),
                "ColumnRuleColor" to srgb(1.0, 0.0, 0.0),
                "ColumnRuleBreak" to JsonPrimitive("INTERSECTION"),
                "ColumnRuleInset" to length(-2.0),
                "RowRuleStyle" to JsonPrimitive("DOUBLE"),
                "RowRuleWidth" to length(2.0),
                "RowRuleColor" to srgb(0.0, 0.0, 1.0),
                "RowRuleBreak" to JsonPrimitive("SPANNING_ITEM"),
                "RowRuleInset" to length(0.0),
                "RuleOverlap" to JsonPrimitive("COLUMN_OVER_ROW")
            )
        )
        assertEquals(ColumnRuleStyle.SOLID, config.column.style)
        assertEquals(10f, config.column.effectiveWidthPx, 0f)
        assertEquals(Color(1f, 0f, 0f), config.column.color)
        assertEquals(GapRuleBreak.INTERSECTION, config.column.breakMode)
        // Negative insets are explicitly legal (WPT 011) — no clamping.
        assertEquals(-2f, config.column.insetPx, 0f)
        assertEquals(ColumnRuleStyle.DOUBLE, config.row.style)
        assertEquals(2f, config.row.effectiveWidthPx, 0f)
        assertEquals(Color(0f, 0f, 1f), config.row.color)
        assertEquals(GapRuleBreak.SPANNING_ITEM, config.row.breakMode)
        assertEquals(GapRuleOverlap.COLUMN_OVER_ROW, config.overlap)
        assertTrue(config.active)
    }

    @Test
    fun `accepts the hyphenated CSS spellings too`() {
        // A fixture fed straight from CSS (rather than through the reader)
        // spells these `spanning-item` / `column-over-row`.
        val config = GapDecorationExtractor.extract(
            listOf(
                "ColumnRuleStyle" to JsonPrimitive("solid"),
                "ColumnRuleWidth" to length(1.0),
                "ColumnRuleBreak" to JsonPrimitive("spanning-item"),
                "RuleOverlap" to JsonPrimitive("column-over-row")
            )
        )
        assertEquals(GapRuleBreak.SPANNING_ITEM, config.column.breakMode)
        assertEquals(GapRuleOverlap.COLUMN_OVER_ROW, config.overlap)
    }

    @Test
    fun `CSS initial values apply when properties are absent`() {
        val config = GapDecorationExtractor.extract(
            listOf("ColumnRuleStyle" to JsonPrimitive("SOLID"))
        )
        // `medium` is the initial line width (3px, same as multicol's).
        assertEquals(GapRuleSpec.MEDIUM_WIDTH_PX, config.column.effectiveWidthPx, 0f)
        assertEquals(GapRuleBreak.NORMAL, config.column.breakMode)
        assertEquals(0f, config.column.insetPx, 0f)
        assertEquals(GapRuleOverlap.ROW_OVER_COLUMN, config.overlap)
        // The row family stays inert — nothing declared it.
        assertFalse(config.row.paints)
    }

    @Test
    fun `line-width keywords resolve like the multicol rule`() {
        fun width(kw: String) = GapDecorationExtractor.extract(
            listOf("RowRuleStyle" to JsonPrimitive("SOLID"), "RowRuleWidth" to JsonPrimitive(kw))
        ).row.effectiveWidthPx
        assertEquals(1f, width("thin"), 0f)
        assertEquals(3f, width("medium"), 0f)
        assertEquals(5f, width("thick"), 0f)
    }

    @Test
    fun `unresolvable inset degrades to zero rather than guessing`() {
        // Percentages / overlap-join are outside the wave-24 contract and
        // arrive without a px value (WPT 014, 053-055, 064).
        val config = GapDecorationExtractor.extract(
            listOf(
                "ColumnRuleStyle" to JsonPrimitive("SOLID"),
                "ColumnRuleWidth" to length(2.0),
                "ColumnRuleInset" to JsonPrimitive("overlap-join")
            )
        )
        assertEquals(0f, config.column.insetPx, 0f)
    }

    @Test
    fun `none and hidden keep the family inert`() {
        for (kw in listOf("NONE", "HIDDEN")) {
            val config = GapDecorationExtractor.extract(
                listOf("ColumnRuleStyle" to JsonPrimitive(kw), "ColumnRuleWidth" to length(10.0))
            )
            assertFalse("style $kw must not paint", config.active)
        }
    }

    @Test
    fun `claims the eight new IR names for the columns category`() {
        // Touch the object so its init block runs before we assert.
        GapDecorationExtractor.hashCode()
        for (name in GapDecorationExtractor.GAP_DECORATION_PROPERTIES) {
            assertTrue("$name unregistered", PropertyRegistry.isMigrated(name))
            assertEquals("columns", PropertyRegistry.ownerOf(name))
        }
    }
}

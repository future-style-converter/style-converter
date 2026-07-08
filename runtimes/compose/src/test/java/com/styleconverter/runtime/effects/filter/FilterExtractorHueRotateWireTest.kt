package com.styleconverter.runtime.effects.filter

// Regression test for the hue-rotate key drift: the IR serializer writes
// the angle under "a" ({"fn":"hue-rotate","a":{"deg":90}} — verified by
// converting fixtures/properties/effects/filter-functions.json), but the
// extractor only read a legacy "angle" key. Result: every hue-rotate
// parsed as 0deg → identity ColorMatrix → the filter was a no-op on
// Android while Chrome rotated the hue (013_Filter_HueRotate_Deg:
// #e67e22 orange stayed orange on Android, went green on web — 28.4%
// mismatched pixels, Lab ΔE p95 ≈ 52.7).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterExtractorHueRotateWireTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)

    @Test
    fun `hue-rotate reads the canonical 'a' wire key`() {
        // Exact wire shape emitted for `filter: hue-rotate(90deg)`.
        val cfg = FilterExtractor.extractFilterConfig(
            listOf("Filter" to parse("""[{"fn":"hue-rotate","a":{"deg":90.0}}]"""))
        )
        val hue = cfg.filters.first() as FilterFunction.HueRotate
        assertEquals(90f, hue.degrees, 0.01f)
    }

    @Test
    fun `hue-rotate turn units arrive pre-normalized to degrees`() {
        // `hue-rotate(0.25turn)` — the parser normalizes to deg and keeps
        // the original as metadata; the extractor must use the deg value.
        val cfg = FilterExtractor.extractFilterConfig(
            listOf("Filter" to parse(
                """[{"fn":"hue-rotate","a":{"deg":90.0,"original":{"v":0.25,"u":"TURN"}}}]"""))
        )
        val hue = cfg.filters.first() as FilterFunction.HueRotate
        assertEquals(90f, hue.degrees, 0.01f)
    }

    @Test
    fun `legacy 'angle' key still parses as fallback`() {
        // Older snapshots / hand-written fixtures used {"angle": ...} —
        // keep them working so the fallback path stays honest.
        val cfg = FilterExtractor.extractFilterConfig(
            listOf("Filter" to parse("""[{"fn":"hue-rotate","angle":{"deg":45.0}}]"""))
        )
        val hue = cfg.filters.first() as FilterFunction.HueRotate
        assertEquals(45f, hue.degrees, 0.01f)
    }
}

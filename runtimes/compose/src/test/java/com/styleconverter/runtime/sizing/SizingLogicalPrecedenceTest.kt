package com.styleconverter.runtime.sizing

// Wave-2: css-logical-1 §1.1 — logical and physical sizing properties are
// CASCADED TOGETHER; in horizontal-tb LTR `inline-size` IS `width`, so the
// declaration that appears LAST wins. The extractor previously stored the
// two spellings in separate slots and the applier hard-preferred physical
// (`width ?: inlineSize`), so Sizing_BoxModel's later `inline-size: 250px`
// lost to the earlier `width: 240px` and `block-size: auto` never released
// the earlier `height: 48px` (Android 240×48 vs web 250×60, 0.806).

import com.styleconverter.runtime.core.types.LengthValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SizingLogicalPrecedenceTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test
    fun `later inline-size overrides earlier width`() {
        val cfg = SizingExtractor.extractSizingConfig(
            listOf(
                "Width" to j("""{"type":"length","px":240.0}"""),
                "InlineSize" to j("""{"px":250.0}""")
            )
        )
        assertEquals(250.0, (cfg.width as LengthValue.Exact).px, 0.001)
    }

    @Test
    fun `later width overrides earlier inline-size`() {
        val cfg = SizingExtractor.extractSizingConfig(
            listOf(
                "InlineSize" to j("""{"px":250.0}"""),
                "Width" to j("""{"type":"length","px":240.0}""")
            )
        )
        assertEquals(240.0, (cfg.width as LengthValue.Exact).px, 0.001)
    }

    @Test
    fun `later block-size auto releases an earlier explicit height`() {
        val cfg = SizingExtractor.extractSizingConfig(
            listOf(
                "Height" to j("""{"type":"length","px":48.0}"""),
                "BlockSize" to j("\"auto\"")
            )
        )
        // Auto in the height slot → applyHeight leaves the axis unset and
        // the box collapses to content height, exactly like web's 250×60.
        assertTrue(cfg.height is LengthValue.Auto)
    }

    @Test
    fun `min and max logical spellings fold into the same axis`() {
        val cfg = SizingExtractor.extractSizingConfig(
            listOf(
                "MinWidth" to j("""{"type":"length","px":100.0}"""),
                "MinInlineSize" to j("""{"px":120.0}""")
            )
        )
        assertEquals(120.0, (cfg.minWidth as LengthValue.Exact).px, 0.001)
    }
}

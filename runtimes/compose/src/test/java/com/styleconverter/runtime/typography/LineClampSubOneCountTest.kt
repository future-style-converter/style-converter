package com.styleconverter.runtime.typography

// Wave-19 skeptic regression — a sub-1 line-clamp count must clamp OFF on
// BOTH Compose decode sites, mirroring iOS's LineClampExtractor (the RC-B6a
// twin) and the css-overflow-4 §5 grammar (<integer [1,∞]>).
//
// Why it matters: the LIVE converter accepts `line-clamp: 0` and emits
// {"type":"lines","count":0.0} (executed converter probe, wave-19 review).
// Before the guard that wire flowed into Text(maxLines = 0), which Compose's
// TextDelegate rejects with require(maxLines > 0) — a render CRASH on
// Android only (iOS clamps off, web ignores the invalid declaration).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineClampSubOneCountTest {

    private fun parse(s: String): JsonElement = Json.parseToJsonElement(s)

    // ── TypographyExtractor site (TypographyConfig.lineClamp) ────────────

    private fun typographyClamp(json: String): Int? =
        TypographyExtractor.extractTypographyConfig(
            listOf("LineClamp" to parse(json))
        ).lineClamp

    @Test
    fun `typography extractor clamps off for zero and negative counts`() {
        // The live `line-clamp: 0` wire (converter emits count as a double).
        assertNull(typographyClamp("""{"type":"lines","count":0.0}"""))
        assertNull(typographyClamp("""{"type":"lines","count":-3}"""))
        // Valid counts keep decoding (live block-ellipsis-001 shape).
        assertEquals(2, typographyClamp("""{"type":"lines","count":2}"""))
        assertEquals(3, typographyClamp("""{"type":"lines","count":3.0}"""))
    }

    // ── TextStyleApplier site (ComponentRenderer's maxLines path) ────────

    private fun textStyleClamp(json: String): Int? =
        TextStyleApplier.extractMaxLines(
            listOf(IRProperty(type = "LineClamp", data = parse(json)))
        )

    @Test
    fun `text style applier clamps off for zero and negative counts`() {
        // This is the site that actually feeds Text(maxLines=...) — the
        // crash path (TextDelegate require(maxLines > 0)).
        assertNull(textStyleClamp("""{"type":"lines","count":0.0}"""))
        assertNull(textStyleClamp("""{"type":"lines","count":-1}"""))
        assertEquals(2, textStyleClamp("""{"type":"lines","count":2.0}"""))
    }
}

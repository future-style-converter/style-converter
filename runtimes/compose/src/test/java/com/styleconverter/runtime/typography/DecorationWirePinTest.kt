package com.styleconverter.runtime.typography

// Wave 21 (lane TEXTDECOR, B-RC8) — LIVE-WIRE pins for the decoration
// config the renderer's owned pass now consumes (style + thickness were
// previously extracted and then silently dropped by the painter).
//
// Kept SEPARATE from DecorationOpsTest so that suite stays pure Kotlin
// (standalone-compilable, no IR/serialization deps); this file pins the
// WIRE → config seam with the exact JSON the wave-21 converter emitted
// (tools/titan/runs/wave21-gate/sections/css-text-decor/per-test-ir/
// wpt__css-text-decor__text-decoration-dotted-001.json, component 1).
//
// iOS twin: DecorationOpsTests.testLiveLengthWireReachesTheThickness-
// Config pins the same envelope through its extractor (where the same
// wire was ACTUALLY dropped — extractKeyword read the "type"
// discriminator as a keyword; the fix is value-matched now).

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DecorationWirePinTest {

    /** Production decode path — same helpers as the pipeline suite. */
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    @Test
    fun `the dotted-001 live wire yields dotted style and explicit 10px thickness`() {
        // The EXACT component-1 property set (color/margins elided —
        // they don't feed this extractor).
        val cfg = TextStyleApplier.extractTextDecorationConfig(listOf(
            prop("TextDecorationLine", """["UNDERLINE"]"""),
            prop("TextDecorationStyle", "\"DOTTED\""),
            prop("TextDecorationThickness", """{"type":"length","px":10}""")
        ))!!
        // Style reaches the renderer's DecorationOps mapping…
        assertEquals(TextStyleApplier.TextDecorationStyleType.DOTTED, cfg.style)
        // …and the explicit thickness survives the length envelope
        // (extractDp reads the top-level px — the B-RC8 feed).
        assertEquals(10f, cfg.thickness!!, 0f)
    }

    @Test
    fun `auto thickness stays null so the legacy capture-pinned path runs`() {
        // `auto` (css-text-decor-4 §2.4 initial) must NOT look like an
        // explicit length — null keeps decorationSegments' wave-5 rows.
        val cfg = TextStyleApplier.extractTextDecorationConfig(listOf(
            prop("TextDecorationLine", """["UNDERLINE"]"""),
            prop("TextDecorationThickness", "\"AUTO\"")
        ))!!
        assertNull(cfg.thickness)
        // Absent style → SOLID (the §2.3 initial).
        assertEquals(TextStyleApplier.TextDecorationStyleType.SOLID, cfg.style)
    }
}

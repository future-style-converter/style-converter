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

    /**
     * Wave 22 (lane DECOR, B-RC4b) — the DARK-STAGE 327 GUARD.
     *
     * The owned pass no longer calls TextStyleApplier.decorationSegments;
     * it calls DecorationColorOps.bands with the resolved request list.
     * That is only safe if the two produce IDENTICAL geometry on the
     * legacy (no-merged-wire) path, so this pins them equal across the
     * flag lattice and both capture-pinned font sizes — 22px (the wave-5
     * decoration oracle: overline rows 18-19, strike 33-34, underline
     * 43-44 at baseline 41) and 16px (the wave-21/22 WPT default face).
     *
     * Lives here rather than in DecorationColorOpsTest because that suite
     * is deliberately dependency-free and this comparison needs
     * TextStyleApplier.
     */
    @Test
    fun `colored bands reproduce decorationSegments exactly on the legacy path`() {
        // Both capture-pinned sizes, with their measured baselines.
        for ((fontSize, baseline) in listOf(22f to 41f, 16f to 236f)) {
            // The auto thickness the legacy emitter would have used.
            val autoT = TextStyleApplier.decorationThicknessPx(fontSize)
            // Every flag combination that reaches the painter (0..7).
            for (mask in 0 until 8) {
                val u = mask and 1 != 0
                val o = mask and 2 != 0
                val l = mask and 4 != 0
                // Legacy: the fixed-order segment emitter.
                val legacy = TextStyleApplier.decorationSegments(
                    lineCount = 2, fontSizePx = fontSize,
                    flags = TextStyleApplier.DecorationLineFlags(u, o, l),
                    lineBaseline = { baseline + it * 26f },
                    lineLeft = { 20f }, lineRight = { 232f }
                )
                // Wave 22: the colored bands off the synthesized (no-wire)
                // request list — same order by construction.
                val colored = DecorationColorOps.bands(
                    lineCount = 2, fontSizePx = fontSize,
                    autoThicknessPx = autoT, explicitThicknessPx = null,
                    lines = DecorationColorOps.resolve(null, u, o, l),
                    lineBaseline = { baseline + it * 26f },
                    lineLeft = { 20f }, lineRight = { 232f }
                )
                // Same count, same left/top/width/thickness, in order —
                // and every color null so the painter substitutes the one
                // resolved color it always did.
                assertEquals("flags=$mask fs=$fontSize", legacy.size, colored.size)
                legacy.forEachIndexed { i, seg ->
                    assertEquals("flags=$mask fs=$fontSize band=$i", seg.left, colored[i].left, 0f)
                    assertEquals("flags=$mask fs=$fontSize band=$i", seg.top, colored[i].top, 0f)
                    assertEquals("flags=$mask fs=$fontSize band=$i", seg.width, colored[i].width, 0f)
                    assertEquals(
                        "flags=$mask fs=$fontSize band=$i",
                        seg.thickness, colored[i].thickness, 0f
                    )
                    assertNull(colored[i].color)
                }
            }
        }
    }

    /**
     * The same guard for the EXPLICIT-thickness path, which used to run
     * through DecorationOps.explicitBands (wave 21, ref-pinned to the
     * dotted-001 band tops 207/363/519 at 92px).
     */
    @Test
    fun `colored bands reproduce explicitBands exactly on the explicit path`() {
        for (t in listOf(10f, 20f, 30f)) {
            for (mask in 1 until 8) {
                val u = mask and 1 != 0
                val o = mask and 2 != 0
                val l = mask and 4 != 0
                val legacy = DecorationOps.explicitBands(
                    lineCount = 1, fontSizePx = 92f, thicknessPx = t,
                    underline = u, overline = o, lineThrough = l,
                    lineBaseline = { 202f }, lineLeft = { 62f }, lineRight = { 522f }
                )
                val colored = DecorationColorOps.bands(
                    lineCount = 1, fontSizePx = 92f,
                    // Deliberately WRONG auto thickness: the explicit one
                    // must win, so this value may never surface.
                    autoThicknessPx = 6f, explicitThicknessPx = t,
                    lines = DecorationColorOps.resolve(null, u, o, l),
                    lineBaseline = { 202f }, lineLeft = { 62f }, lineRight = { 522f }
                )
                assertEquals("t=$t flags=$mask", legacy.size, colored.size)
                legacy.forEachIndexed { i, band ->
                    assertEquals("t=$t flags=$mask band=$i", band.top, colored[i].top, 0f)
                    assertEquals("t=$t flags=$mask band=$i", band.thickness, colored[i].thickness, 0f)
                }
            }
        }
    }

    // ── wave 38, lane N3: the §2.1 `||` gate at the WIRE seam ─────────────
    // These are the EXACT TextDecorationLine payloads the wave37-final
    // per-test IR carries for css/css-text-decor/text-decoration-line.html
    // (components 31/33/35/37 — the four `blink …` divs). The browser ref
    // paints NOTHING on all four; before this gate the owned pass painted
    // 6 spurious full-width bands across them. iOS twin:
    // DecorationColorOpsTests' live-wire gate cases.

    @Test
    fun `the live blink-blink wire owns no decoration line`() {
        val flags = TextStyleApplier.extractDecorationLineFlags(listOf(
            prop("TextDecorationLine", """["BLINK","BLINK"]""")))
        assertEquals(false, flags.any)
    }

    @Test
    fun `the live blink-underline-blink wire owns no decoration line`() {
        // The surviving `underline` must NOT be painted: the duplicate
        // `blink` invalidates the whole declaration, so the property keeps
        // its initial `none`.
        val flags = TextStyleApplier.extractDecorationLineFlags(listOf(
            prop("TextDecorationLine", """["BLINK","UNDERLINE","BLINK"]""")))
        assertEquals(false, flags.underline)
        assertEquals(false, flags.any)
    }

    @Test
    fun `the live blink-underline-overline-linethrough-blink wire owns nothing`() {
        val flags = TextStyleApplier.extractDecorationLineFlags(listOf(
            prop("TextDecorationLine",
                 """["BLINK","UNDERLINE","OVERLINE","LINE_THROUGH","BLINK"]""")))
        assertEquals(false, flags.underline)
        assertEquals(false, flags.overline)
        assertEquals(false, flags.lineThrough)
    }

    @Test
    fun `the built-in TextStyle decoration is dropped for an invalid value too`() {
        // Both emitters must agree: extractTextDecorationConfig feeds
        // Compose's BUILT-IN TextDecoration on the non-label paths. If only
        // the owned pass honoured the gate the built-in would keep painting
        // the line the ref does not have.
        val cfg = TextStyleApplier.extractTextDecorationConfig(listOf(
            prop("TextDecorationLine", """["BLINK","UNDERLINE","BLINK"]""")))
        assertNull(cfg)
    }

    @Test
    fun `a VALID multi-keyword wire still owns all three lines`() {
        // The regression guard for the gate itself — component 22 of the
        // same test (`all-decorations`), which the ref DOES paint.
        val flags = TextStyleApplier.extractDecorationLineFlags(listOf(
            prop("TextDecorationLine", """["UNDERLINE","OVERLINE","LINE_THROUGH"]""")))
        assertEquals(true, flags.underline)
        assertEquals(true, flags.overline)
        assertEquals(true, flags.lineThrough)
    }
}

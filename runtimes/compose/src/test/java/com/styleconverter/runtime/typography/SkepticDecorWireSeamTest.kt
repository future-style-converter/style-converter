package com.styleconverter.runtime.typography

import com.styleconverter.runtime.core.ir.IRDecoration
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SKEPTIC SEAM PROBE (wave-22 lane DECOR adversarial review).
 *
 * The lane's own DecorationWireTest pins the bridge against HAND-WRITTEN
 * wires. This probe drives the LIVE converter artifact through the REAL
 * decode path instead, and pins the two cross-platform claims the lane's
 * risk list makes — so a future edit that breaks either shows up here
 * rather than in a device SSIM run nobody ran.
 */
class SkepticDecorWireSeamTest {

    // Repo root by walking up from the module dir (SchemaConformanceTest rule).
    private val repoRoot: File by lazy {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "schema/ir-v1.schema.json").exists()) dir = dir.parentFile
        requireNotNull(dir) { "repo root not found above ${System.getProperty("user.dir")}" }
    }

    /**
     * P1 — the LIVE golden walks the real decoder into the real bridge.
     * Reads schema/conformance/fixtures/v2/decorations.json (the byte-shape
     * the converter emits for fixtures/wpt/css-text-decor/
     * text-decoration-color.json) through IRDocumentDecoder, not a literal.
     */
    @Test
    fun liveGoldenDecodesToTheThreeColourChain() {
        val doc = IRDocumentDecoder.decode(
            File(repoRoot, "schema/conformance/fixtures/v2/decorations.json").readText()
        )
        val chain = doc.components.first { it.id == "decor-chain-002" }
        val lines = DecorationWire.toDecorationLines(chain.decorations)
        assertNotNull("wire must survive decode", lines)
        assertEquals(
            listOf(
                DecorationColorOps.DecorationLine(
                    DecorationColorOps.LineKind.UNDERLINE,
                    DecorationColorOps.Rgba(0f, 0f, 1f, 1f)
                ),
                DecorationColorOps.DecorationLine(
                    DecorationColorOps.LineKind.OVERLINE,
                    DecorationColorOps.Rgba(128 / 255f, 128 / 255f, 128 / 255f, 1f)
                ),
                DecorationColorOps.DecorationLine(
                    DecorationColorOps.LineKind.LINE_THROUGH,
                    DecorationColorOps.Rgba(0f, 128 / 255f, 0f, 1f)
                )
            ),
            lines
        )
    }

    /**
     * P2 — EMPTY-WIRE HOLE, end to end. A chain whose every entry is an
     * unpaintable keyword must resolve to a PRESENT-but-EMPTY list, and
     * `resolve` must NOT fall back to the component's own flags there
     * (that is the ComponentRenderer `paintedTextStyle` precondition).
     */
    @Test
    fun blinkOnlyChainResolvesToPresentButEmptyAndNeverFallsBack() {
        val wire = DecorationWire.toDecorationLines(
            listOf(IRDecoration("blink", "blue"), IRDecoration("none"))
        )
        assertNotNull("a fully-filtered wire is still PRESENT", wire)
        assertTrue("every entry was unpaintable", wire!!.isEmpty())
        // The painter gate: flags all true, wire empty-but-present.
        val requests = DecorationColorOps.resolve(
            wire = wire, underline = true, overline = true, lineThrough = true
        )
        assertTrue("authoritative empty wire must paint nothing", requests.isEmpty())
        // And nothing is drawable, so bands() is empty too.
        assertTrue(
            DecorationColorOps.bands(
                lineCount = 1, fontSizePx = 16f, autoThicknessPx = 1f,
                explicitThicknessPx = null, lines = requests,
                lineBaseline = { 236f }, lineLeft = { 0f }, lineRight = { 100f }
            ).isEmpty()
        )
    }

    /**
     * P3 — the COLOUR-COVERAGE HOLE the lane's risk list does not name.
     *
     * fixtures/wpt/css-text-decor/text-decoration-style-multiple.json is
     * LIVE in the corpus today and authors `coral` / `skyblue`. The
     * converter resolves both into the IR sRGB leaf (ColorConversion.kt
     * carries the full 148-name css-color-4 table), but `meta.decorations`
     * forwards the raw token, and ValueExtractors.parseCssColorLiteral —
     * a var()-substitution parser, not a CSS colour parser — knows neither
     * name. Both entries therefore lose their colour and paint the run's
     * merged `text-decoration-color` instead.
     *
     * This test DOCUMENTS the current behaviour so the divergence is
     * visible in CI; it is not an endorsement. See the review notes.
     */
    @Test
    fun extendedNamedColoursInTheLiveCorpusDoNotResolve() {
        val lines = DecorationWire.toDecorationLines(
            listOf(
                IRDecoration("underline", "coral"),
                IRDecoration("overline", "skyblue"),
                IRDecoration("line-through", "green")
            )
        )!!
        assertEquals(3, lines.size)
        // Wave 22: the wire now resolves through the FULL CSS parser —
        // coral = #FF7F50 (css-color-4 §6.1), byte-parallel with iOS.
        assertEquals(0xFF / 255f, lines[0].color!!.r, 1e-4f)
        assertEquals(0x7F / 255f, lines[0].color!!.g, 1e-4f)
        assertEquals(0x50 / 255f, lines[0].color!!.b, 1e-4f)
        // skyblue = #87CEEB.
        assertEquals(0x87 / 255f, lines[1].color!!.r, 1e-4f)
        assertEquals(0xCE / 255f, lines[1].color!!.g, 1e-4f)
        assertEquals(0xEB / 255f, lines[1].color!!.b, 1e-4f)
        assertEquals(
            DecorationColorOps.Rgba(0f, 128 / 255f, 0f, 1f), lines[2].color
        )
    }

    /**
     * P4 — wave 22 closed the twin asymmetry: both natives now route the
     * wire token through their FULL CSS color parser (Compose
     * CssVariableResolver.parseColorValue; iOS CSSTokenParser.color), so
     * rgb()/rgba() resolve identically on both platforms.
     */
    @Test
    fun functionalColourTokenResolvesOnCompose() {
        val lines = DecorationWire.toDecorationLines(
            listOf(IRDecoration("underline", "rgb(0, 0, 255)"))
        )!!
        assertEquals(DecorationColorOps.Rgba(0f, 0f, 1f, 1f), lines[0].color)
        // …while the hex twin in the same golden entry DOES resolve.
        assertEquals(
            DecorationColorOps.Rgba(0f, 1f, 0f, 1f),
            DecorationWire.toDecorationLines(
                listOf(IRDecoration("line-through", "#00ff00"))
            )!![0].color
        )
    }

    /** P5 — decoder strictness: the shapes the schema rejects must throw. */
    @Test
    fun decoderRejectsEveryMalformedDecorationShape() {
        fun doc(meta: String) = """
            {"irVersion":2,"minReaderVersion":2,"components":[
              {"id":"a","name":"A","properties":[],"text":"x","meta":{$meta}}]}
        """.trimIndent()
        val bad = listOf(
            """"decorations":[]""",
            """"decorations":"underline"""",
            """"decorations":[["underline"]]""",
            """"decorations":[{"color":"blue"}]""",
            """"decorations":[{"line":"underline","style":"wavy"}]""",
            """"decorations":[{"line":"underline","color":123}]""",
            """"decorations":[{"line":7}]"""
        )
        for (m in bad) {
            var threw = false
            try {
                IRDocumentDecoder.decode(doc(m))
            } catch (e: Exception) {
                threw = true
            }
            assertTrue("must reject: $m", threw)
        }
        // …and the TOLERATED case (spec-05 rule 1) must decode.
        val ok = IRDocumentDecoder.decode(doc(""""decorations":[{"line":"spelling-error"}]"""))
        assertEquals("spelling-error", ok.components[0].decorations!![0].line)
    }
}

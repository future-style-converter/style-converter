package com.styleconverter.runtime.color

// ColorExtractorImageNotationTest — wave 48, lane F3 (S2 must-fix 1 +
// S4 defect 2): the applied image() seam (css-images-4 §2.5, wave-48
// lane W5) shipped with ZERO unit pins, and the executed S4 twin probe
// showed the natives DIVERGING on a colour key that fails to parse
// (Kotlin fell through `?:` to srcs; Swift painted .color(.unknown) =
// clear). These pins freeze the Kotlin half of the now twin-aligned
// precedence table; BackgroundImageExtractorImageNotationTests.swift is
// the byte-parallel iOS mirror asserting the SAME wire → same outcome.
//
// Wires are VERBATIM: the corpus rows (001/002/005) are the freshly
// converted WPT css-image-fallbacks-and-annotations layers captured by
// skeptic S2 (probe5-wires.json, re-derivable from tools/titan/runs/
// wave48-cal/sections/css-images/per-test-ir/); the adversarial rows
// (garbage-colour, object-src, bare image()) are skeptic S4's executed
// probe payloads (S4ImageNotationSeamProbe.kt).
//
// WAVE-49 LANE A3 UPDATE. The extractor no longer COLLAPSES the value — it
// emits BackgroundImageConfig.ImageNotation carrying the WHOLE candidate list
// and ColorApplier resolves css-images-4 §2.5 at paint time (see
// images/ImageCandidateChain.kt for why that is a move of the wave-48
// precedence note, not a reversal). These pins therefore assert the
// UNRESOLVED value; the resolution itself is pinned by the twin
// ColorApplierImageNotationTest.kt (iOS mirror:
// BackgroundImageNotationResolutionTests.swift). Wires are still verbatim;
// only the expected config shape moved.

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorExtractorImageNotationTest {

    /** Feed one raw BackgroundImage wire string through the SHIPPED
     *  extractor entry point — the exact seam the probes executed. */
    private fun layers(json: String): List<BackgroundImageConfig> =
        ColorExtractor.extractBackgroundImages(Json.parseToJsonElement(json))

    @Test
    fun `verbatim 001 wire - carries both the srcs list and the fallback colour`() {
        // fallbacks-and-annotations-001: `image("green.png", green)` — the
        // source is deliberately missing in WPT, so §2.1's outcome is the
        // colour. The EXTRACTOR does not decide that (it cannot attempt a
        // decode); it must hand both halves on intact.
        val out = layers(
            """[{"type":"image","srcs":["green.png"],"color":{"srgb":{"r":0.0,"g":0.5019607843137255,"b":0.0},"original":"green"}}]"""
        )
        // Exactly one layer, held UNRESOLVED: candidate list AND fallback
        // colour both survive extraction (alpha defaults to 1.0 when the
        // srgb block omits `a`). Which of the two paints is §2.1's
        // question, answered by ColorApplier.resolveImageNotation.
        assertEquals(
            listOf(BackgroundImageConfig.ImageNotation(
                listOf("green.png"),
                Color(0.0f, 0.5019607843137255.toFloat(), 0.0f, 1.0f))),
            out
        )
    }

    @Test
    fun `verbatim 002 wire - carries its lone candidate with no fallback colour`() {
        // fallbacks-and-annotations-002: `image("support/1x1-green.png")`
        // — no fallback colour on the wire, so the candidate list is the
        // whole value (the S4-probed srcs-fallback row).
        val out = layers("""[{"type":"image","srcs":["support/1x1-green.png"]}]""")
        // One image() layer carrying the src string byte-for-byte.
        assertEquals(
            listOf(BackgroundImageConfig.ImageNotation(
                listOf("support/1x1-green.png"), null)),
            out
        )
    }

    @Test
    fun `object-wrapped src - the data-uri IRUrl shape reaches the candidate list`() {
        // The IRUrl wire has TWO shapes (BackgroundImageSerializer.kt):
        // bare string, or {url, data:true} for data URIs — this pins the
        // object arm of the srcs read (S4's object-src probe payload).
        val out = layers(
            """[{"type":"image","srcs":[{"url":"data:image/png;base64,AA==","data":true}]}]"""
        )
        // The url string inside the object is authoritative.
        assertEquals(
            listOf(BackgroundImageConfig.ImageNotation(
                listOf("data:image/png;base64,AA=="), null)),
            out
        )
    }

    @Test
    fun `unparseable colour falls through to srcs - the twin-aligned rule`() {
        // S4 defect 2's exact divergence wire: the colour key is present
        // but garbage, so ValueExtractors.extractColor yields null and the
        // seam's `?:` must fall through to the sources instead of eating
        // them. The iOS twin now mirrors this (its `.unknown` sentinel
        // falls through too — before wave-48 F3 it painted clear here).
        val out = layers("""[{"type":"image","srcs":["x.png"],"color":{"garbage":true}}]""")
        // The sources survive and the unusable colour becomes an ABSENT
        // fallback — never a dropped layer.
        assertEquals(
            listOf(BackgroundImageConfig.ImageNotation(listOf("x.png"), null)), out)
    }

    @Test
    fun `verbatim 005 wire - empty srcs with a colour paints the colour alone`() {
        // fallbacks-and-annotations-005: `image(rgba(0,0,255,0.5))` — the
        // colour is declared ALONE (srcs is EMPTY, not missing-source like
        // 001), and §2.1 makes the colour the outcome; the test's second
        // layer is the plain data-URI url() the same WPT file composites
        // beneath it, riding the untagged {url, data} layer shape.
        val out = layers(
            """[{"type":"image","srcs":[],"color":{"srgb":{"r":0.0,"g":0.0,"b":1.0,"a":0.5},"original":{"r":0,"g":0,"b":255,"a":0.5}}},{"url":"data:image/png,%89%50%4e%47%0d%0a%1a%0a%00%00%00%0d%49%48%44%52%00%00%00%01%00%00%00%01%01%03%00%00%00%25%db%56%ca%00%00%00%04%67%41%4d%41%00%00%af%c8%37%05%8a%e9%00%00%00%03%50%4c%54%45%00%80%00%9c%f9%a5%91%00%00%00%0a%49%44%41%54%78%da%63%60%00%00%00%02%00%01%e5%27%de%fc%00%00%00%19%74%45%58%74%53%6f%66%74%77%61%72%65%00%41%64%6f%62%65%20%49%6d%61%67%65%52%65%61%64%79%71%c9%65%3c%00%00%00%00%49%45%4e%44%ae%42%60%82","data":true}]"""
        )
        // Layer 0: EMPTY candidate list + the half-alpha blue colour — the
        // colour is the only §2.1 outcome available, and the resolver will
        // say so without any candidate walk to run.
        assertEquals(
            BackgroundImageConfig.ImageNotation(emptyList(), Color(0.0f, 0.0f, 1.0f, 0.5f)),
            out[0]
        )
        // Layer 1: the sibling data-URI layer survives untouched, so the
        // image() arm cannot have swallowed its neighbours.
        assertTrue(out[1] is BackgroundImageConfig.Url)
        assertTrue((out[1] as BackgroundImageConfig.Url).url.startsWith("data:image/png,"))
        // Exactly the two wire layers — nothing invented, nothing dropped.
        assertEquals(2, out.size)
    }

    @Test
    fun `bare image() with neither colour nor srcs extracts no layer`() {
        // Neither branch of the precedence has anything to paint: the
        // entry maps to null and mapNotNull drops it — an honest empty
        // list, not a phantom None/clear layer (S4's empty-image probe).
        assertEquals(emptyList<BackgroundImageConfig>(), layers("""[{"type":"image"}]"""))
    }
}

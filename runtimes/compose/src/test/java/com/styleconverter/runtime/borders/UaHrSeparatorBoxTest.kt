package com.styleconverter.runtime.borders

// Wave 50 — lane F1, prescription 9 (skeptic S7). THE COMPOSE HALF OF THE UA
// `<hr>` SEPARATOR BOX.
//
// ## Why this pin exists
// Lane B7's `ua-hr-separator-box.patch` bakes an HTML-Rendering §15.3.11
// UA-origin box onto every rule-less `<hr>`, replacing the extractor's
// `{width:100px, height:100px}` empty-node placeholder. B7 predicts +3 cells
// (`wave49-final css-images/gradient/gradient-hue-direction` f→P on all three:
// web f 0.6241 · ios f 0.6235 · android f 0.6230, capture 390×894 against the
// 390×600 ref = 3 × (100 − 2) of surplus).
//
// Skeptic S7 re-measured that claim and found it MIXED: removing the three
// 98px displacements is NOT the whole failure. With the shift removed but no
// rule painted, both natives land BELOW the bar (web P 0.9521, iOS f 0.9491,
// Android f 0.9495); with the rule painted they clear it (P 1.0000 / P 0.9970
// / P 0.9973). S7's sensitivity sweep says ANY painted rule passes — even a
// single row (0.9960/0.9963) or pure black (0.9506/0.9510). **So B7's +2
// native cells hinge entirely on the baked box actually painting a rule on
// Compose**, and nothing in the tree checked that the Compose extractors read
// the baked bag the way the prediction assumes.
//
// This suite is that check, at the only level a device-less lane can reach.
//
// ## What IS proved here
//  1. `SizingExtractor` reads the bag as an EXPLICIT content-box height 0,
//     and arms `contentBoxInflateY` with the 2px border band — so
//     `SizingApplier.inflateForContentBox` turns the declared 0px CONTENT
//     height into a 2px BORDER-BOX frame (css-sizing-3 §3: frame = content +
//     padding + border). This is the key B7's banner calls "the Android
//     repair": without the explicit `box-sizing: content-box` the 0px would be
//     the frame height and the rule would have no rows to paint.
//  2. `BorderSideExtractor` reads four 1px `inset` #eeeeee sides, i.e. all
//     four `hasBorder` and `isUniform` — the ink the S7 sensitivity sweep says
//     the flip depends on.
//  3. The bag declares NO width of any kind, so nothing pins the block's
//     inline size and it takes its container's (CSS 2.1 §10.3.3) — which is
//     what makes the rule span x 16–373 in the ref rather than 100px.
//
// ## What is NOT proved here, stated plainly
//  * **THE RASTER.** Nothing below draws a pixel. Whether Compose actually
//    paints a 2px-tall two-tone `inset` band at the right shade — the
//    rgb(154,154,154)/rgb(238,238,238)/rgb(196,196,196) Blink two-tone model
//    B7 measured off the frozen ref — is a DEVICE question, and wave 50 ran no
//    device gate. `BorderShadeBlinkGateTest` pins the shade arithmetic; the
//    composition of shade × 2px band × container width is unpinned anywhere.
//  * The extractor change itself (`tools/titan/extract-fixture.mjs`) is lane
//    B7's and is pinned by its own `extract-fixture-ua-hr.test.mjs`. This
//    suite starts from the IR the bake produces and asks only what the Compose
//    runtime makes of it.
//  * The margin-block 8px and `overflow: hidden` keys of the bake are outside
//    this suite: they move the rule's POSITION, not whether it paints, and the
//    S7 sensitivity sweep is about ink.
//
// ## Where the wire came from
// The base component is `tools/titan/runs/wave49-final/sections/css-images/
// per-test-ir/wpt__css-images__gradient__gradient-hue-direction.json`
// component `…__4-070`, VERBATIM. The baked keys are B7's `UA_HR_PROPS`
// (`tools/titan/results/wave50-B7/ua-hr-separator-box.patch`) written in the
// wire shapes the corpus already carries for each type — `"BLOCK"`,
// `"CONTENT_BOX"`, `{"px":1}`, `"INSET"`, `{"srgb":…,"original":"#eeeeee"}` —
// and the change S2 recorded for this document
// (`tools/titan/results/wave50-S2/changed-documents.json`: "Width 100px
// DROPPED, Height 100px -> 0px, + Display BLOCK, BoxSizing CONTENT_BOX,
// Border{Top,Right,Bottom,Left}{Width 1px, Style INSET, Color #eeeeee},
// MarginTop/Bottom 8px, OverflowX/Y HIDDEN").

import com.styleconverter.runtime.borders.sides.BorderSideExtractor
import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.core.types.ValueExtractors.LineStyle
import com.styleconverter.runtime.sizing.BoxSizingKeyword
import com.styleconverter.runtime.sizing.SizingApplier
import com.styleconverter.runtime.sizing.SizingExtractor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UaHrSeparatorBoxTest {

    // Same helper shape as BoxSizingContentBoxTest / BorderExtractorsTest, so
    // a wire-parser drift fails in one familiar place.
    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(type: String, json: String) = type to parse(json)

    /**
     * The baked `<hr>` property bag, in IR wire order.
     *
     * 238/255 = 0.9333333333333333 is `#eeeeee` in the IR's sRGB 0–1 floats
     * (schema/spec/02-values.md: colours normalise to sRGB floats and keep the
     * authored text in `original`).
     */
    private val bakedHrBag: List<Pair<String, JsonElement?>> = listOf(
        // css-display-3 §2.1 — the UA outer display for <hr>.
        pair("Display", """"BLOCK""""),
        // CSS 2.1 §10.6.3 — a block box with no in-flow content is 0 tall.
        // Stated explicitly because neither native derives it.
        pair("Height", """{"type":"length","px":0}"""),
        // css-ui-3 §2.1 — content-box IS the initial value, stated because
        // Compose only inflates a declared size for an EXPLICIT content-box.
        pair("BoxSizing", """"CONTENT_BOX""""),
        // The UA `border: 1px inset #eeeeee`, as longhands (B7 keeps them
        // longhand so one author-declared side can decline without the rest).
        pair("BorderTopWidth", """{"px":1}"""),
        pair("BorderRightWidth", """{"px":1}"""),
        pair("BorderBottomWidth", """{"px":1}"""),
        pair("BorderLeftWidth", """{"px":1}"""),
        pair("BorderTopStyle", """"INSET""""),
        pair("BorderRightStyle", """"INSET""""),
        pair("BorderBottomStyle", """"INSET""""),
        pair("BorderLeftStyle", """"INSET""""),
        pair(
            "BorderTopColor",
            """{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}""",
        ),
        pair(
            "BorderRightColor",
            """{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}""",
        ),
        pair(
            "BorderBottomColor",
            """{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}""",
        ),
        pair(
            "BorderLeftColor",
            """{"srgb":{"r":0.9333333333333333,"g":0.9333333333333333,"b":0.9333333333333333},"original":"#eeeeee"}""",
        ),
        // The block-axis margins and the overflow keys ride along in the real
        // bag; included so this list IS the bag, not a convenient subset.
        pair("MarginTop", """{"px":8}"""),
        pair("MarginBottom", """{"px":8}"""),
        pair("OverflowX", """"HIDDEN""""),
        pair("OverflowY", """"HIDDEN""""),
    )

    // ── 1. The sizing half — a 0px CONTENT box becomes a 2px BORDER box ──────

    @Test
    fun `the baked hr declares an explicit content-box height of zero`() {
        // wptCaptureMode = true because the titan feeder captures in WPT mode
        // and that is the only path this bake exists for. The keyword is
        // DECLARED, so effectiveBoxSizing's WPT default is not what answers.
        val cfg = SizingExtractor.extractSizingConfig(bakedHrBag, wptCaptureMode = true)
        assertEquals(
            "the UA bake states box-sizing: content-box, which is what arms the " +
                "band inflation Compose needs (B7's banner: without it the 0px " +
                "would be the FRAME height and the rule paints nothing)",
            BoxSizingKeyword.CONTENT_BOX, cfg.boxSizing)
        assertEquals(
            "CSS 2.1 §10.6.3 — the declared CONTENT height is 0",
            LengthValue.Exact(0.0), cfg.height)
    }

    @Test
    fun `the 1px band inflates the zero content box to a 2px border box`() {
        val cfg = SizingExtractor.extractSizingConfig(bakedHrBag, wptCaptureMode = true)
        // css-sizing-3 §3: frame = content + padding + border. No padding on
        // the bake, so the whole vertical band is the two 1px borders.
        assertEquals(
            "top + bottom border = the whole vertical inflation band",
            2f, cfg.contentBoxInflateY, 0.001f)
        // And the applier's pure arithmetic turns the declared 0 into 2.
        // THIS is the number the rule's two painted rows live in — and it is
        // where the proof stops: that Compose then rasterises those 2 rows as
        // an `inset` two-tone band is a device fact, not a JVM one.
        assertEquals(
            "a 0px content box + a 2px band is a 2px border box — the rule's rows",
            LengthValue.Exact(2.0),
            SizingApplier.inflateForContentBox(
                cfg.height, cfg.boxSizing, cfg.contentBoxInflateY))
    }

    @Test
    fun `the baked hr pins no inline size, so the block takes its container width`() {
        val cfg = SizingExtractor.extractSizingConfig(bakedHrBag, wptCaptureMode = true)
        // CSS 2.1 §10.3.3 — a block-level box with `width: auto` fills its
        // containing block. The bake DROPS the placeholder's `width: 100px`
        // (S2's recorded diff), which is what lets the rule span the full
        // x 16–373 of the frozen ref instead of a 100px stub.
        assertNull("no Width may survive the bake", cfg.width)
        assertNull("and no logical inline size either", cfg.inlineSize)
        assertNull("nor a minimum that would floor it", cfg.minWidth)
        // The horizontal band is still computed (there ARE left/right
        // borders); it simply has no declared width to inflate.
        assertEquals(2f, cfg.contentBoxInflateX, 0.001f)
    }

    // ── 2. The border half — four 1px inset #eeeeee sides ────────────────────

    @Test
    fun `the baked hr carries four 1px inset #eeeeee sides`() {
        // wptCaptureMode = true for the same reason: it is the capture path,
        // and it changes the currentColor bottom-out (not reached here — every
        // side declares its colour).
        val borders = BorderSideExtractor.extractBorderConfig(bakedHrBag, wptCaptureMode = true)
        // 238/255 rounded back out of the sRGB float — the base colour B7
        // measured off the frozen ref, before Blink's `inset` shading.
        val expected = androidx.compose.ui.graphics.Color(0xFFEEEEEE)
        for ((name, side) in listOf(
            "top" to borders.top, "end" to borders.end,
            "bottom" to borders.bottom, "start" to borders.start,
        )) {
            assertNotNull("$name width must decode", side.width)
            assertEquals("$name is 1px", 1f, side.width!!.value, 0.001f)
            assertEquals(
                "$name is css-backgrounds-3 §3.2 `inset` — the two-tone 3D style " +
                    "whose Blink shading B7 derived from the ref pixels",
                LineStyle.INSET, side.style)
            assertNotNull("$name colour must decode", side.color)
            // Compare on the 8-bit channels: the wire carries 0.93333… floats
            // and Color stores a packed float, so an exact object equality
            // would be pinning float representation rather than the colour.
            assertEquals(
                "$name base colour is #eeeeee",
                expected.value.toString(16).take(4),
                side.color!!.value.toString(16).take(4))
            assertTrue(
                "$name must count as a painted border — a side whose style is " +
                    "none/hidden has a USED width of 0 and neither paints nor " +
                    "reserves the row the rule needs",
                side.hasBorder)
        }
        assertTrue("all four sides agree — one uniform rule", borders.isUniform)
        assertTrue("and the box has borders at all", borders.hasBorders)
    }
}

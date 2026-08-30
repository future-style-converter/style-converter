package com.styleconverter.runtime.color

// Wave-49 lane A5 — `background-color: currentcolor` (css-color-4 §6.4).
//
// Every JSON payload below is copied VERBATIM out of the wave-48 corpus run:
//   tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//     wpt__css-color__currentcolor-001.json   (inner div: Color green +
//                                              BackgroundColor currentColor)
//     wpt__css-color__currentcolor-002.json   (outer div: Color red +
//                                              BackgroundColor currentColor)
// plus the bare-string shape from fixtures/properties/color/color-named.json
// ("Color_CurrentColor": color red, background-color currentColor).
//
// The defect these pin: ValueExtractors.extractColor returns null for the
// keyword, ColorExtractor stored that null, and the box painted NOTHING — so
// the ancestor's red showed through and the runtime rendered the WPT FAIL
// square (iOS ssim 0.9988 / colorFailed / labDeltaE.mean 11.24).

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentColorBackgroundTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    // Verbatim wire shapes from the per-test-ir files named above.
    private val CURRENTCOLOR = """{"original":"currentColor"}"""
    private val GREEN = """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""
    private val RED = """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""

    // css-color-4 §6.4: the SAME element's `color`, i.e. currentcolor-001's
    // inner box must paint GREEN — not the parent's red.
    @Test fun `currentcolor background resolves to the element's own color`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", GREEN),
            pair("BackgroundColor", CURRENTCOLOR),
        ))
        assertEquals(Color(0f, 0.5019608f, 0f, 1f), cfg.backgroundColor)
    }

    // Declaration ORDER must not matter — the extractor is a single pass and
    // the IR puts BackgroundColor before Color on currentcolor-002's outer div.
    @Test fun `resolution is order-independent`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundColor", CURRENTCOLOR),
            pair("Color", RED),
        ))
        assertEquals(Color(1f, 0f, 0f, 1f), cfg.backgroundColor)
    }

    // The inherited channel is merged UNDER the own declarations before
    // extraction, so an inherited-only Color is the computed color and must be
    // the resolution target (currentcolor-002's middle div works this way).
    @Test fun `inherited-only Color is a valid resolution target`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", RED),          // arrived via INHERITED_PROPERTY_TYPES
            pair("BackgroundColor", CURRENTCOLOR),
        ))
        assertEquals(Color(1f, 0f, 0f, 1f), cfg.backgroundColor)
    }

    // The bare-string wire form (fixtures/properties/color/color-named.json).
    @Test fun `bare-string currentColor is recognised`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", RED),
            pair("BackgroundColor", "\"currentColor\""),
        ))
        assertEquals(Color(1f, 0f, 0f, 1f), cfg.backgroundColor)
    }

    // CSS keywords are ASCII case-insensitive (css-values-4 §4.1).
    @Test fun `keyword match is case-insensitive`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", RED),
            pair("BackgroundColor", """{"original":"CURRENTCOLOR"}"""),
        ))
        assertEquals(Color(1f, 0f, 0f, 1f), cfg.backgroundColor)
    }

    // Bottom-out: no Color anywhere on the merged list. Deliberately still
    // null — the UA-ink bottom-out is capture-mode split and lives in
    // ComponentRenderer, not here (see CurrentColorBackground's doc).
    @Test fun `currentcolor with no Color on the list stays unresolved`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("BackgroundColor", CURRENTCOLOR),
        ))
        assertNull(cfg.backgroundColor)
    }

    // Regression guard: static payloads must be byte-identical to the old
    // ValueExtractors.extractColor path — this is what keeps the blast radius
    // at zero for every background colour already rendering correctly.
    @Test fun `static srgb payloads are unchanged`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", GREEN),                      // must NOT hijack a real colour
            pair("BackgroundColor", RED),
        ))
        assertEquals(Color(1f, 0f, 0f, 1f), cfg.backgroundColor)
    }

    // The srgb color-mix() the static decoder already handles keeps working
    // and is NOT rerouted through the currentcolor branch.
    @Test fun `srgb color-mix payloads are unchanged`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair(
                "BackgroundColor",
                """{"original":{"type":"color-mix","colorSpace":"srgb",""" +
                    """"color1":"red","percent1":50,"color2":"blue"}}""",
            ),
        ))
        assertEquals(Color(0.5f, 0f, 0.5f, 1f), cfg.backgroundColor)
    }

    // A color-mix() CONTAINING currentcolor now resolves too — §6.4 applies
    // inside the function exactly as it does outside it (css-color-5 §3 is
    // defined on the USED colours). This is the PARENT of
    // css-color/color-mix-currentcolor-001, whose `color` is red: Chrome 151
    // computes color(srgb 0.5 0.25098 0). Evaluating it here is only safe
    // because BackgroundColorInheritance now hands the child the parent's
    // UNRESOLVED mix, so the child re-resolves it to green and the top box
    // paints green — the two changes are a pair.
    @Test fun `color-mix containing currentcolor resolves against own color`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", RED),
            pair(
                "BackgroundColor",
                """{"original":{"type":"color-mix","colorSpace":"srgb",""" +
                    """"color1":"currentColor","percent1":50,"color2":"green"}}""",
            ),
        ))
        assertEquals(Color(0.5f, 0.25098f, 0f, 1f), cfg.backgroundColor)
    }

    // …and the SAME wire on the child (color: green) is plain green —
    // color-mix(in srgb, green 50%, green). Chrome 151 agrees:
    // color(srgb 0 0.501961 0).
    @Test fun `the same mix on a green element is green`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", GREEN),
            pair(
                "BackgroundColor",
                """{"original":{"type":"color-mix","colorSpace":"srgb",""" +
                    """"color1":"currentColor","percent1":50,"color2":"green"}}""",
            ),
        ))
        assertEquals(Color(0f, 0.5019608f, 0f, 1f), cfg.backgroundColor)
    }

    // css-cascade-5 §7.3.2 `inherit` stays unresolved AT THIS LEVEL: the
    // keyword names the parent's computed value, which an extractor holding
    // one component's declarations cannot know. BackgroundColorInheritance
    // substitutes it on the composed tree BEFORE extraction, so by the time
    // a real document reaches here the keyword is already gone; a stray one
    // must still not be guessed at.
    @Test fun `inherit keyword stays unresolved in the extractor`() {
        val cfg = ColorExtractor.extractColorConfig(listOf(
            pair("Color", GREEN),
            pair("BackgroundColor", """{"original":"inherit"}"""),
        ))
        assertNull(cfg.backgroundColor)
    }
}

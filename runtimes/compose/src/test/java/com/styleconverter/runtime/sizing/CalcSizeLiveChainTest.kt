package com.styleconverter.runtime.sizing

// Wave 46 (lane Y6) — the calc-size() MIN lane through the LIVE chain.
//
// Why this pin exists: wave45-final still shows css-values/calc-size/
// calc-size-flex-001..006 painting NOTHING on Android (six identical 0.9542
// presence-veto captures) two waves after the typed wire + floor lane
// (wave 42, lane W3) landed. The first hypothesis — "the resolved px never
// reaches the item's main-size constraint" (X7 classification) — is what
// this test REFUTES end to end: the flex-001 item's declarations, run
// through exactly what ComponentRenderer runs (DynamicValueResolver →
// StyleApplier.applyProperties in WPT capture mode), produce a modifier
// chain whose OUTER element is the CalcSizeMinModifier carrying the decoded
// `calc-size(auto, size + 20px)` and whose inner element is the green
// background. So the sizing tree is intact; the blank capture is owned by
// the flex loop's CROSS axis (an `align-self: auto` item under the default
// `align-items: normal` is never stretched in the legacy Row/Column loops,
// so the 100-wide item has a 0px height — see the wave-46 Y6 lane notes),
// which is the flexbox tree's, not sizing's.
//
// Wire shapes verbatim from tools/titan/runs/wave45-final/sections/
// css-values/per-test-ir/wpt__css-values__calc-size__calc-size-flex-001.json.

import androidx.compose.ui.Modifier
import com.styleconverter.runtime.StyleApplier
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.variables.DynamicValueResolver
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcSizeLiveChainTest {

    // Parse one JSON literal into the IRProperty data slot.
    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // The flex-001 ITEM: `min-width: calc-size(auto, size + 20px);
    // background: green` — no width, no align-self.
    private val flexItem = listOf(
        prop("MinWidth", """{"type":"calc-size","basis":"auto","factor":1,"offsetPx":20,"original":"calc-size(auto, size + 20px)"}"""),
        prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
    )

    // The flex-item context ComponentRenderer builds: a flex child has
    // LocalSelfAlignmentHandled = true, i.e. blockLevelWidthAuto = false.
    private val flexItemCtx = DynamicValueResolver.Context(blockLevelWidthAuto = false)

    @Test
    fun `the typed calc-size wire survives dynamic-value resolution untouched`() {
        // Nothing in the list is dynamic (no var()/calc()/em), so the
        // resolver's fast path returns the SAME instance — the typed
        // calc-size object is never rewritten or dropped.
        val resolution = DynamicValueResolver.resolve(flexItem, emptyMap(), flexItemCtx)
        assertSame(flexItem, resolution.properties)
    }

    @Test
    fun `the extractor routes MinWidth calc-size into the min floor slot`() {
        val cfg = SizingExtractor.extractSizingConfig(
            flexItem.map { it.type to it.data }, wptCaptureMode = true)
        // The floor lane's slot carries the decoded value; the plain
        // min-width slot stays empty (the pair is exclusive) and no width
        // was declared (null specified-size suggestion for §4.5).
        assertEquals(CalcSizeValue(CalcSizeBasis.AUTO, 1.0, 20.0), cfg.minWidthCalc)
        assertEquals(null, cfg.minWidth)
        assertEquals(null, cfg.width)
    }

    @Test
    fun `applyProperties chains the min floor OUTSIDE the background`() {
        val chain = StyleApplier.applyProperties(flexItem, null, wptCaptureMode = true)
        // foldIn walks outermost → innermost.
        val elements = chain.foldIn(emptyList<Modifier.Element>()) { acc, e -> acc + e }
        assertEquals(2, elements.size)
        // Outer: the floor lane, so its measure band (floorBand ⇒ 100..100
        // for the flex-001 shape: content-min 80 + 20) is what the inner
        // background node is sized by.
        assertTrue(
            "outer element must be the calc-size min floor, was ${elements[0].javaClass.name}",
            elements[0].javaClass.name.endsWith("CalcSizeMinModifier"))
        // Inner: the green fill paints at the floored size.
        assertTrue(
            "inner element must be the background, was ${elements[1].javaClass.name}",
            elements[1].javaClass.name.endsWith("BackgroundElement"))
    }

    @Test
    fun `the floor the chain will apply is the corpus-pinned 100px`() {
        // The arithmetic the modifier runs at measure time, with the
        // flex-001 intrinsics (grandchild width 80 ⇒ content-min 80, no
        // specified size): §4.5 auto minimum 80, calc-size ⇒ 100, and a
        // zero-width container's band opens to exactly 100..100.
        val spec = SizingExtractor.extractSizingConfig(
            flexItem.map { it.type to it.data }, wptCaptureMode = true).minWidthCalc
        assertNotNull(spec)
        val floor = spec!!.targetPx(CalcSizeMath.autoMinBasis(specifiedPx = null, contentMin = 80))
        assertEquals(100, floor)
        assertEquals(100 to 100, CalcSizeMath.floorBand(incomingMin = 0, incomingMax = 0, floorPx = floor))
    }
}

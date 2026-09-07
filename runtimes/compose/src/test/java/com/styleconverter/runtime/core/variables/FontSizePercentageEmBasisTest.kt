package com.styleconverter.runtime.core.variables

// Retrospective R3 (finding A3#0) — a PERCENTAGE font-size must reach the em
// basis of the element's own Width/Height, and its descendants must inherit
// the RESOLVED size.
//
// Verbatim IR: tools/titan/runs/wave49-final/sections/css-color/per-test-ir/
// wpt__css-color__currentcolor-002.json — outer `…__1-251`, middle
// `currentcolor-002__1__0-252`, inner `currentcolor-002__1__0__0-253`; the
// WPT source is `.outer { font-size: 200%; width: 6em; height: 6em }` with
// the middle/inner also `6em`. Chromium (the frozen ref) paints ONE 192×192
// green square: 200% of the 16px body = 32px (css-fonts-4 §2.5 — a
// percentage font-size resolves against the INHERITED size; the <percentage>
// type, css-values-4 §5.5), 6em of 32 = 192 (css-values-4 §6.1.1), and both
// descendants inherit the COMPUTED 32px (css-cascade-5 §7.2: "the inherited
// value … is the computed value of the property on the element's parent").
//
// Android painted 96×96 on the wave-49 gate (Android-web 0.8957 on -001 and
// -002; measured green n=9216 bbox [16,88]-[111,183] vs the ref's n=36864
// bbox [16,88]-[207,279]) because DynamicValueResolver.resolveOriginalObject
// had no `{type:percentage}` branch: resolveOwnFontSize fell to the parent's
// 16px and every em on the element resolved against it; the FontSize was
// never rewritten with a px either, so children inherited nothing better.
// iOS paints 192×192 (iOS-web 0.9990).
//
// The walk below mirrors ComponentRenderer.RenderComponent exactly:
// mergeInherited(own, inherited) → DynamicValueResolver.resolve(merged,
// parentFontSizePx = fontSizePxOf(inherited) ?: 16) → children inherit the
// RESOLVED list filtered to INHERITED_PROPERTY_TYPES. Mutation proof: on the
// tree WITHOUT the resolver's percentage branch every 192/32 assertion below
// fails at 96/16 (executed during the lane, fail-first then pass).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FontSizePercentageEmBasisTest {

    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** Top-level resolved px of a property, the shape every applier reads. */
    private fun pxOf(props: List<IRProperty>, type: String): Double? =
        ((props.firstOrNull { it.type == type }?.data as? JsonObject)?.get("px") as? JsonPrimitive)?.doubleOrNull

    // ── the three components, verbatim ────────────────────────────────────

    /** `.outer`: color red; background currentcolor; font-size 200%; 6em box. */
    private val outer = listOf(
        prop("Color", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
        prop("BackgroundColor", """{"original":"currentColor"}"""),
        prop("FontSize", """{"original":{"type":"percentage","value":200}}"""),
        prop("Width", """{"type":"length","original":{"v":6,"u":"EM"}}"""),
        prop("Height", """{"type":"length","original":{"v":6,"u":"EM"}}"""),
    )

    /** `.middle`: background inherit; 6em box; no font-size of its own. */
    private val middle = listOf(
        prop("BackgroundColor", """{"original":"inherit"}"""),
        prop("Width", """{"type":"length","original":{"v":6,"u":"EM"}}"""),
        prop("Height", """{"type":"length","original":{"v":6,"u":"EM"}}"""),
    )

    /** `.inner`: color green; background inherit; 6em box; bold "FAIL". */
    private val inner = listOf(
        prop("Color", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""),
        prop("BackgroundColor", """{"original":"inherit"}"""),
        prop("Width", """{"type":"length","original":{"v":6,"u":"EM"}}"""),
        prop("Height", """{"type":"length","original":{"v":6,"u":"EM"}}"""),
        prop("FontWeight", """{"weight":700,"original":"bold"}"""),
    )

    /** The renderer's context minus the channels this test does not need. */
    private fun ctx(parentFs: Float) = DynamicValueResolver.Context(
        viewportWidthPx = 390f, viewportHeightPx = 844f,
        parentFontSizePx = parentFs, rootFontSizePx = 16f,
    )

    /** One cascade level, exactly as RenderComponent runs it. */
    private fun level(own: List<IRProperty>, inherited: List<IRProperty>): DynamicValueResolver.Resolution {
        val merged = ComponentRenderer.mergeInherited(own, inherited)
        val parentFs = DynamicValueResolver.fontSizePxOf(inherited) ?: DynamicValueResolver.DEFAULT_FONT_SIZE_PX
        return DynamicValueResolver.resolve(merged, emptyMap(), ctx(parentFs))
    }

    /** What the renderer publishes to children (LocalInheritedProperties). */
    private fun inheritable(res: DynamicValueResolver.Resolution): List<IRProperty> =
        res.properties.filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }

    @Test
    fun `outer - 200 percent of the 16px body is 32px and its 6em box is 192`() {
        val r = level(outer, emptyList())
        // The element's own font size (the em base handed to the rest).
        assertEquals(32f, r.fontSizePx, 1e-4f)
        // Published to children as a RESOLVED px (fontSizePxOf reads this).
        assertEquals(32.0, pxOf(r.properties, "FontSize")!!, 1e-6)
        // 6em × 32 = 192 on both axes — the ref's square.
        assertEquals(192.0, pxOf(r.properties, "Width")!!, 1e-6)
        assertEquals(192.0, pxOf(r.properties, "Height")!!, 1e-6)
    }

    @Test
    fun `middle and inner inherit the computed 32px and resolve 6em to 192`() {
        val o = level(outer, emptyList())
        val m = level(middle, inheritable(o))
        assertEquals(32f, m.fontSizePx, 1e-4f)
        assertEquals(192.0, pxOf(m.properties, "Width")!!, 1e-6)
        assertEquals(192.0, pxOf(m.properties, "Height")!!, 1e-6)
        val i = level(inner, inheritable(m))
        assertEquals(32f, i.fontSizePx, 1e-4f)
        assertEquals(192.0, pxOf(i.properties, "Width")!!, 1e-6)
        assertEquals(192.0, pxOf(i.properties, "Height")!!, 1e-6)
    }

    @Test
    fun `the percentage resolves against the threaded inherited size not a constant`() {
        // A 20px parent → 200% = 40, 6em = 240: the base is the channel.
        val r = DynamicValueResolver.resolve(outer, emptyMap(), ctx(20f))
        assertEquals(40f, r.fontSizePx, 1e-4f)
        assertEquals(240.0, pxOf(r.properties, "Width")!!, 1e-6)
    }

    @Test
    fun `percentage envelopes on other properties stay untouched`() {
        // Blast-radius pin. The corpus carries `{"original":{"type":"percentage"}}`
        // on exactly two other types — LineHeight (css-counter-styles, 48
        // components: `line-height: 150%` ships a resolved multiplier) and
        // Opacity (css-anchor-position, 48: `opacity: 20%` ships alpha) —
        // both verbatim below. Neither threads a % base into the resolver,
        // so the branch must return them byte-identically (Outcome.Keep).
        val lh = prop("LineHeight", """{"multiplier":1.5,"original":{"type":"percentage","value":150}}""")
        val op = prop("Opacity", """{"alpha":0.2,"original":{"type":"percentage","value":20}}""")
        val r = DynamicValueResolver.resolve(listOf(lh, op), emptyMap(), ctx(16f))
        assertNull(pxOf(r.properties, "LineHeight"))
        assertNull(pxOf(r.properties, "Opacity"))
        assertEquals(lh.data, r.properties[0].data)
        assertEquals(op.data, r.properties[1].data)
    }
}

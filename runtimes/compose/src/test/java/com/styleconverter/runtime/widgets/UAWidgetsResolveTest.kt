package com.styleconverter.runtime.widgets

// Lane W2 (wave 20) — widget IDENTITY pins: (sourceTag, meta.attrs,
// Appearance, AccentColor) → Spec, plus the meta.attrs wire-contract
// decode through IRDocumentDecoder against LIVE-wire-shaped documents.
// Swift twin: UAWidgetsTests.swift (same decision table, same shapes).

import com.styleconverter.runtime.core.ir.IRAttrs
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.widgets.UAWidgetsGeometry.Kind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UAWidgetsResolveTest {

    /** Property helper: type + raw JSON data (wire-shaped). */
    private fun prop(type: String, dataJson: String) =
        IRProperty(type, Json.parseToJsonElement(dataJson))

    /** Component helper — only the fields resolve() reads. */
    private fun comp(
        tag: String?,
        attrs: IRAttrs? = null,
        properties: List<IRProperty> = emptyList(),
        text: String? = null,
        children: List<IRComponent>? = null,
    ) = IRComponent(
        id = "t", name = "t", properties = properties,
        children = children, _text = text, _tag = tag, attrs = attrs
    )

    // ── identity table (html.css UA widget mapping) ─────────────────────

    @Test
    fun kindTable_tagPlusTypeSelectsTheWidget() {
        // The full appearance-auto-001 element roster, one line each.
        assertNull(UAWidgetsResolve.resolve(comp("a", text = "a")))               // <a> is no widget
        assertEquals(Kind.BUTTON, UAWidgetsResolve.resolve(comp("button", text = "button"))?.kind)
        assertEquals(Kind.TEXTFIELD, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "text", value = "x")))?.kind)
        assertEquals(Kind.TEXTFIELD, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "search")))?.kind)
        assertEquals(Kind.TEXTFIELD, UAWidgetsResolve.resolve(comp("input"))?.kind) // absent type → text
        assertEquals(Kind.TEXTAREA, UAWidgetsResolve.resolve(comp("textarea", text = "textarea"))?.kind)
        assertEquals(Kind.BUTTON, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "submit", value = "s")))?.kind)
        assertEquals(Kind.RANGE, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "range")))?.kind)
        assertEquals(Kind.CHECKBOX, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "checkbox")))?.kind)
        assertEquals(Kind.RADIO, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "radio")))?.kind)
        assertEquals(Kind.COLOR, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "color")))?.kind)
        assertEquals(Kind.MENULIST, UAWidgetsResolve.resolve(comp("select"))?.kind)
        assertEquals(Kind.LISTBOX, UAWidgetsResolve.resolve(comp("select", IRAttrs(multiple = true)))?.kind)
        assertEquals(Kind.METER, UAWidgetsResolve.resolve(comp("meter", IRAttrs(valueNumber = 0.5)))?.kind)
        assertEquals(Kind.PROGRESS, UAWidgetsResolve.resolve(comp("progress", IRAttrs(valueNumber = 0.5)))?.kind)
    }

    @Test
    fun appearanceGate_noneDevolves_aliasesStayNative() {
        val cb = IRAttrs(type = "checkbox")
        // appearance: none → NO widget (devolved side of the -001 tests).
        assertNull(UAWidgetsResolve.resolve(comp("input", cb, listOf(prop("Appearance", """{"type":"none"}""")))))
        // auto and every compat alias → native (aliases "alias to auto").
        for (v in listOf("auto", "button", "checkbox", "listbox", "menulist", "square-button", "textfield")) {
            assertEquals("appearance:$v must stay native", Kind.CHECKBOX,
                UAWidgetsResolve.resolve(comp("input", cb, listOf(prop("Appearance", """{"type":"$v"}"""))))?.kind)
        }
        // Missing Appearance → the UA sheet's auto (bare accent tests).
        assertEquals(Kind.CHECKBOX, UAWidgetsResolve.resolve(comp("input", cb))?.kind)
        // `initial` computes to the property's initial value `none`.
        assertNull(UAWidgetsResolve.resolve(comp("input", cb,
            listOf(prop("Appearance", """{"type":"keyword","keyword":"initial"}""")))))
    }

    @Test
    fun nonWidgetInputs_ignoreAppearanceEntirely() {
        // appearance-auto-input-non-widget-001: auto on hidden/image/file
        // changes nothing — they paint their special content regardless.
        val auto = listOf(prop("Appearance", """{"type":"auto"}"""))
        assertEquals(Kind.HIDDEN, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "hidden", value = "abc"), auto))?.kind)
        val img = UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "image", value = "def"), auto))
        assertEquals(Kind.IMAGE, img?.kind)
        assertEquals("def", img?.label) // alt absent → value fallback (ref ink)
        assertEquals(Kind.FILE, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "file"), auto))?.kind)
    }

    // ── value plumbing ──────────────────────────────────────────────────

    @Test
    fun checkedAndFractionPlumbing() {
        // checked rides the boolean attr (accent-color-visited's input).
        assertTrue(UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "checkbox", checked = true)))!!.checked)
        // Range: no value → the UA midpoint; string value maps via min/max.
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "range")))!!.fraction)
        assertEquals(0.25f, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "range", value = "25")))!!.fraction)
        // Progress: numeric value/max; absent → indeterminate (null).
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("progress", IRAttrs(valueNumber = 0.5)))!!.fraction)
        assertNull(UAWidgetsResolve.resolve(comp("progress"))!!.fraction)
        // Meter: value within [min,max] (defaults 0..1).
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("meter", IRAttrs(valueNumber = 0.5)))!!.fraction)
    }

    @Test
    fun fractionEdgeCases_stayFiniteAndTwinAligned() {
        // Wave-20 skeptic pins. "NaN"/"Infinity" parse in both natives'
        // Double parsers but are NOT valid HTML floats (§2.3.5.1) — they
        // must fall back to the UA midpoint, never propagate NaN into
        // the thumb geometry.
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "range", value = "NaN")))!!.fraction)
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "range", value = "Infinity")))!!.fraction)
        // Degenerate travel (min == max) → midpoint, not 0/0 NaN.
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("input", IRAttrs(type = "range", value = "5", min = 5.0, max = 5.0)))!!.fraction)
        // progress max=0 — HTML §4.10.13 pins max > 0, so a zero wire max
        // falls back to 1.0 (0.5/0.0 was NaN plan geometry before).
        assertEquals(0.5f, UAWidgetsResolve.resolve(comp("progress", IRAttrs(valueNumber = 0.5, max = 0.0)))!!.fraction)
        // meter max <= min stays guarded at 0.
        assertEquals(0f, UAWidgetsResolve.resolve(comp("meter", IRAttrs(valueNumber = 1.0, min = 2.0, max = 2.0)))!!.fraction)
    }

    @Test
    fun selectLabelsComeFromOptionChildren() {
        val options = listOf(
            comp("option", text = "select"),
            comp("option", IRAttrs(selected = true), text = "picked"),
        )
        // Menulist shows the SELECTED option (UA default: the first).
        assertEquals("picked", UAWidgetsResolve.resolve(comp("select", children = options))!!.label)
        // Listbox lists every option row in order.
        assertEquals(listOf("select", "picked"),
            UAWidgetsResolve.resolve(comp("select", IRAttrs(multiple = true), children = options))!!.options)
    }

    // ── accent resolution (css-ui-4 §7.1) ───────────────────────────────

    @Test
    fun accent_defaultExplicitAndCurrentColor() {
        val cb = IRAttrs(type = "checkbox", checked = true)
        // No AccentColor → the Chromium default #0075FF.
        assertEquals(0xFF0075FFL, UAWidgetsResolve.resolve(comp("input", cb))!!.accent)
        // Explicit srgb red → packed red.
        assertEquals(0xFFFF0000L, UAWidgetsResolve.resolve(comp("input", cb, listOf(
            prop("AccentColor", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}""")
        )))!!.accent)
        // currentColor resolves against the element's used color (the
        // merged list — accent-color-parent-currentcolor's red chain).
        assertEquals(0xFFFF0000L, UAWidgetsResolve.resolve(comp("input", cb, listOf(
            prop("AccentColor", """{"type":"color","original":"currentColor"}"""),
            prop("Color", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}""")
        )))!!.accent)
    }

    // ── meta.attrs wire decode (the W1 contract, through the decoder) ───

    @Test
    fun metaAttrs_decodeTypedChannels() {
        // A live-wire-shaped v2 document exercising every attrs type class:
        // strings, booleans, and numbers (meter value/min/max).
        val doc = IRDocumentDecoder.decode("""{"irVersion":2,"minReaderVersion":2,"components":[
            {"id":"a","name":"a","properties":[],
             "meta":{"sourceTag":"input","attrs":{"type":"checkbox","checked":true}}},
            {"id":"b","name":"b","properties":[],
             "meta":{"sourceTag":"meter","attrs":{"value":0.5,"min":0,"max":1}}},
            {"id":"c","name":"c","properties":[],
             "meta":{"sourceTag":"select","attrs":{"multiple":true,"size":"4"}}},
            {"id":"d","name":"d","properties":[],
             "meta":{"sourceTag":"input","attrs":{"type":"image","value":"def","alt":"alt-text"}}}
        ]}""")
        val (a, b, c, d) = doc.components
        // Booleans decode as booleans (the wire contract's boolean four).
        assertEquals(true, a.attrs?.checked)
        assertEquals("checkbox", a.attrs?.type)
        // Numeric value rides the numeric channel (meter/progress).
        assertEquals(0.5, b.attrs?.valueNumber)
        assertEquals(0.0, b.attrs?.min)
        assertEquals(1.0, b.attrs?.max)
        // size is a STRING per the contract; multiple a boolean.
        assertEquals(true, c.attrs?.multiple)
        assertEquals("4", c.attrs?.size)
        // alt + string value coexist.
        assertEquals("def", d.attrs?.value)
        assertEquals("alt-text", d.attrs?.alt)
        // Components without attrs keep null (no phantom capsule).
        assertNull(IRDocumentDecoder.decode(
            """{"irVersion":2,"minReaderVersion":2,"components":[
               {"id":"e","name":"e","properties":[],"meta":{"sourceTag":"p"}}]}"""
        ).components.single().attrs)
    }

    @Test
    fun metaAttrs_typedChannelsNeverCoerceStrings() {
        // Twin-alignment pin (wave-20 skeptic): the iOS reader
        // (IRAttrs.from over IRValue.doubleValue/boolValue) never coerces
        // quoted strings into the numeric/boolean channels, so the
        // Compose decoder must not either — an off-contract writer
        // sending {"min":"5"} must decode IDENTICALLY on both natives
        // (string-only; numeric channel empty), not paint different
        // widget geometry per platform.
        val doc = IRDocumentDecoder.decode("""{"irVersion":2,"minReaderVersion":2,"components":[
            {"id":"a","name":"a","properties":[],
             "meta":{"sourceTag":"input","attrs":{"type":"range","value":"50","min":"5","max":"10","checked":"true"}}}]}""")
        val a = doc.components.single()
        assertEquals("50", a.attrs?.value) // string channel verbatim
        assertNull(a.attrs?.valueNumber)   // no numeric coercion from strings
        assertNull(a.attrs?.min)
        assertNull(a.attrs?.max)
        assertNull(a.attrs?.checked)       // no boolean coercion either
    }

    @Test
    fun metaAttrs_unknownKeyIsLoud() {
        // Strict envelope posture (spec 05 rule 2): a key outside the
        // pinned ten is a writer bug and must error, not silently drop.
        try {
            IRDocumentDecoder.decode("""{"irVersion":2,"minReaderVersion":2,"components":[
                {"id":"a","name":"a","properties":[],
                 "meta":{"sourceTag":"input","attrs":{"placeholder":"x"}}}]}""")
            throw AssertionError("unknown attrs key must throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("meta.attrs"))
        }
    }
}

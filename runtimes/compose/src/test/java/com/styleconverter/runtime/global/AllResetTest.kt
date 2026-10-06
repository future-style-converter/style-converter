package com.styleconverter.runtime.global

// Wave 52 (lane L11, brief colour-not-reaching-text F1) — pins for the
// ORDER-AWARE `all` reset (AllReset.kt). The IR documents below are VERBATIM
// wave51-fix per-test IR (tools/titan/runs/wave51-fix/sections/css-cascade/
// per-test-ir/…), so a pin speaks for the exact gate cell it names.
//
// MUTATION RECORD (executed 2026-10-05 by mutate-compose.sh in
// tools/titan/results/wave52-all-reset-postload-colour/, AllReset.kt restored
// byte-exact after each — sha256 55f4572f… before == after, mutations.log):
//   M1 `Result(emptyList(), emptyList())` (the pre-wave-52 drop) → 6/8 FAIL
//      (colour quartet, all-prop-001, display-contents-button, both channel
//      pins, last-all).
//   M2 own[0,i) kept whole → `color red then all initial`, all-prop-001 and
//      last-all FAIL.
//   M3 §3.1 exemption removed from own[0,i) → all-prop-001 FAILS.
//   M4 INITIAL keeps the channel like INHERIT → 4 channel pins FAIL.
//   M5 `indexOfFirst` for `indexOfLast` → `last all governs` FAILS.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRDocumentDecoder
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AllResetTest {

    // ── helpers ─────────────────────────────────────────────────────────

    /** Decode a v2 document and flatten its slot tree (the decoder nests). */
    private fun components(json: String): List<IRComponent> {
        // Pre-order walk so lookups by id see every level.
        fun walk(c: IRComponent): List<IRComponent> = listOf(c) + (c.children ?: emptyList()).flatMap(::walk)
        return IRDocumentDecoder.decode(json).components.flatMap(::walk)
    }

    /** One component by id from a decoded document. */
    private fun byId(json: String, id: String): IRComponent = components(json).first { it.id == id }

    /** The parent's published channel: its own list filtered to inherited types. */
    private fun channelOf(parent: IRComponent): List<IRProperty> =
        parent.properties.filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES }

    /** What RenderComponent folds after the reset (the seam-1 order: reset → merge). */
    private fun resolved(own: List<IRProperty>, inherited: List<IRProperty>): List<IRProperty> {
        // The reset runs BEFORE mergeInherited — exactly the seam-1 call order.
        val r = AllReset.apply(own, inherited)
        return ComponentRenderer.mergeInherited(r.own, r.inherited)
    }

    /** The green channel of a Color entry's srgb payload (0 when absent). */
    private fun green(p: IRProperty): Double =
        ((p.data as JsonObject)["srgb"] as JsonObject)["g"]!!.jsonPrimitive.double

    /** A bare keyword property (`All`, `Direction`, …) — the wire's string payload. */
    private fun kw(type: String, value: String) = IRProperty(type, JsonPrimitive(value))

    /** A colour property in the wire's {srgb, original} shape. */
    private fun color(r: Double, g: Double, b: Double) = IRProperty(
        "Color", JsonObject(mapOf("srgb" to JsonObject(mapOf(
            "r" to JsonPrimitive(r), "g" to JsonPrimitive(g), "b" to JsonPrimitive(b))))))

    // ── verbatim wave51-fix IR ──────────────────────────────────────────

    private val initialColor = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-initial-color__0-014","name":"wpt__css-cascade__all-prop-initial-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-initial-color__0__0-015","name":"all-prop-initial-color__0__0","properties":[{"type":"All","data":"INITIAL"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-initial-color__0-014"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"""

    private val inheritColor = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-inherit-color__0-012","name":"wpt__css-cascade__all-prop-inherit-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-inherit-color__0__0-013","name":"all-prop-inherit-color__0__0","properties":[{"type":"All","data":"INHERIT"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-inherit-color__0-012"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"""

    private val unsetColor = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-unset-color__0-021","name":"wpt__css-cascade__all-prop-unset-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-unset-color__0__0-022","name":"all-prop-unset-color__0__0","properties":[{"type":"All","data":"UNSET"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-unset-color__0-021"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"""

    private val revertColor = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-revert-color__0-017","name":"wpt__css-cascade__all-prop-revert-color__0","properties":[{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}}],"meta":{"sourceTag":"p"}},{"id":"all-prop-revert-color__0__0-018","name":"all-prop-revert-color__0__0","properties":[{"type":"All","data":"REVERT"},{"type":"Color","data":{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}}],"slot":{"parent":"wpt__css-cascade__all-prop-revert-color__0-017"},"text":"Test passes if this text is green.","meta":{"sourceTag":"span"}}]}"""

    // ── pins ────────────────────────────────────────────────────────────

    /** The four `all-prop-*-color` cells: the span's own green follows `all`
     *  and wins (§6.4); the parent's red never paints. */
    @Test
    fun `all-prop color quartet resolves the own green after all`() {
        // (document, parent id, span id) for each of the four wave51-fix tests.
        val cases = listOf(
            Triple(initialColor, "wpt__css-cascade__all-prop-initial-color__0-014", "all-prop-initial-color__0__0-015"),
            Triple(inheritColor, "wpt__css-cascade__all-prop-inherit-color__0-012", "all-prop-inherit-color__0__0-013"),
            Triple(unsetColor, "wpt__css-cascade__all-prop-unset-color__0-021", "all-prop-unset-color__0__0-022"),
            Triple(revertColor, "wpt__css-cascade__all-prop-revert-color__0-017", "all-prop-revert-color__0__0-018"),
        )
        for ((doc, parentId, spanId) in cases) {
            // Inherited channel = what the red <p> publishes to the span.
            val merged = resolved(byId(doc, spanId).properties, channelOf(byId(doc, parentId)))
            // Exactly one Color survives, and it is the span's green.
            val colors = merged.filter { it.type == "Color" }
            assertEquals("$spanId: one Color", 1, colors.size)
            assertEquals("$spanId: green wins", 0.50196, green(colors[0]), 1e-4)
            // The `All` entry itself never survives the reset.
            assertTrue("$spanId: All removed", merged.none { it.type == "All" })
        }
    }

    /** `color: red; all: initial` — the declaration BEFORE `all` is reset. */
    @Test
    fun `color red then all initial leaves no colour`() {
        // The inherited channel carries blue: INITIAL must drop it too.
        val merged = resolved(listOf(color(1.0, 0.0, 0.0), kw("All", "INITIAL")), listOf(color(0.0, 0.0, 1.0)))
        assertTrue("no Color survives: $merged", merged.none { it.type == "Color" })
    }

    /** VERBATIM `css-cascade/all-prop-001` — the §3.1 exemption carrier
     *  (the P→f-risk cell named in PLAN.md L11). */
    @Test
    fun `all-prop-001 keeps direction and unicode-bidi through all initial`() {
        val doc = allProp001
        // The `.test` div: 32 declarations BEFORE `all: initial`.
        val test = byId(doc, "wpt__css-cascade__all-prop-001__1-002")
        val r = AllReset.apply(test.properties, emptyList())
        assertEquals(listOf("Direction", "UnicodeBidi"), r.own.map { it.type })
        // The `<bdo>`: `direction: rtl` (dir=rtl) FOLLOWS `all` — kept.
        val bdo = byId(doc, "all-prop-001__1__0-003")
        // Its channel = what `.test` publishes after ITS reset (inherited types only).
        val rb = AllReset.apply(bdo.properties, r.own.filter { it.type in ComponentRenderer.INHERITED_PROPERTY_TYPES })
        assertEquals(listOf("Direction"), rb.own.map { it.type })
        // INITIAL still lets the exempt inherited `direction` flow (§3.1).
        assertEquals(listOf("Direction"), rb.inherited.map { it.type })
    }

    /** INHERIT keeps the channel; INITIAL drops it (§7.3.1 vs §7.3.2). */
    @Test
    fun `initial drops the inherited channel and inherit keeps it`() {
        val red = listOf(color(1.0, 0.0, 0.0))
        // `all: inherit` alone → the parent's red is the element's colour.
        assertTrue(resolved(listOf(kw("All", "INHERIT")), red).any { it.type == "Color" })
        // `all: initial` alone → no colour at all (initial = canvastext).
        assertTrue(resolved(listOf(kw("All", "INITIAL")), red).none { it.type == "Color" })
        // UNSET and both REVERTs behave like INHERIT for the inherited channel.
        for (k in listOf("UNSET", "REVERT", "REVERT_LAYER", "revert-layer")) {
            assertTrue("$k keeps the channel", resolved(listOf(kw("All", k)), red).any { it.type == "Color" })
        }
    }

    /** INITIAL drops the channel EXCEPT an inherited `direction` (§3.1). */
    @Test
    fun `initial keeps an inherited direction`() {
        val r = AllReset.apply(listOf(kw("All", "INITIAL")), listOf(kw("Direction", "RTL"), kw("FontFamily", "serif")))
        assertEquals(listOf("Direction"), r.inherited.map { it.type })
    }

    /** The LAST `all` governs (§6.4): unset after initial keeps the channel. */
    @Test
    fun `last all governs`() {
        val ch = listOf(color(1.0, 0.0, 0.0))
        // initial, then unset, then green: unset governs → channel kept, green own.
        val r = AllReset.apply(listOf(kw("All", "INITIAL"), kw("All", "UNSET"), color(0.0, 0.5, 0.0)), ch)
        assertEquals(1, r.inherited.size)
        assertEquals(listOf("Color"), r.own.map { it.type })
        // unset, then initial: initial governs → channel dropped.
        assertTrue(AllReset.apply(listOf(kw("All", "UNSET"), kw("All", "INITIAL")), ch).inherited.isEmpty())
    }

    /** All-free lists keep their identity — the whole corpus's fast path. */
    @Test
    fun `all-free lists are returned as the same instances`() {
        val own = listOf(color(0.0, 0.0, 0.0))
        val inh = listOf(kw("FontFamily", "serif"))
        val r = AllReset.apply(own, inh)
        assertSame(own, r.own)
        assertSame(inh, r.inherited)
    }

    /** VERBATIM `css-display/display-contents-button` button: everything after
     *  `all: initial` is kept by the reset itself. (On the natives the RC6
     *  root self-strip — ContentsUnboxing.resolve — runs BEFORE the reset and
     *  leaves only inheritable types, so the 10px red border never reaches
     *  it; this pin covers the reset in isolation, i.e. the web shape.) */
    @Test
    fun `display-contents-button keeps the declarations after all`() {
        val button = byId(displayContentsButton, "wpt__css-display__display-contents-button__2-036")
        val kept = AllReset.apply(button.properties, emptyList()).own.map { it.type }
        // 15 declarations follow `all: initial` in the source; all 15 survive.
        assertEquals(button.properties.drop(1).map { it.type }, kept)
        assertEquals("Display", kept.last())
    }

    companion object {
        /** VERBATIM tools/titan/runs/wave51-fix/sections/css-cascade/per-test-ir/
         *  wpt__css-cascade__all-prop-001.json (4.3 kB; runs/ is gitignored, so
         *  the payload rides in the test). */
        private const val allProp001 = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-cascade__all-prop-001__0-001","name":"wpt__css-cascade__all-prop-001__0","properties":[],"text":"Test passes if the digits are in order and there is no red.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-cascade__all-prop-001__1-002","name":"wpt__css-cascade__all-prop-001__1","properties":[{"type":"Direction","data":"RTL"},{"type":"UnicodeBidi","data":"BIDI_OVERRIDE"},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"},{"type":"FontVariantCaps","data":"SMALL_CAPS"},{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"OutlineStyle","data":"SOLID"},{"type":"OutlineColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Float","data":"LEFT"},{"type":"LetterSpacing","data":{"px":0,"original":{"type":"length","original":{"v":1,"u":"EM"}}}},{"type":"Display","data":"LIST_ITEM"},{"type":"TextAlign","data":"CENTER"},{"type":"Width","data":{"type":"length","original":{"v":0.5,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":10,"u":"EM"}}},{"type":"OverflowX","data":"SCROLL"},{"type":"OverflowY","data":"SCROLL"},{"type":"All","data":"INITIAL"}],"text":"321","meta":{"runs":[{"child":"all-prop-001__1__0"},{"text":" 321"}]}},{"id":"all-prop-001__1__0-003","name":"all-prop-001__1__0","properties":[{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BackgroundColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Color","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"TextDecorationLine","data":["LINE_THROUGH"]},{"type":"FontWeight","data":{"weight":700,"original":"bold"}},{"type":"FontStyle","data":"italic"},{"type":"FontVariantCaps","data":"SMALL_CAPS"},{"type":"FontSize","data":{"px":20,"original":{"type":"length","px":20}}},{"type":"FontFamily","data":["monospace"]},{"type":"LineHeight","data":{"multiplier":1.2,"original":"normal"}},{"type":"OutlineStyle","data":"SOLID"},{"type":"OutlineColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Float","data":"LEFT"},{"type":"LetterSpacing","data":{"px":0,"original":{"type":"length","original":{"v":1,"u":"EM"}}}},{"type":"Display","data":"LIST_ITEM"},{"type":"TextAlign","data":"CENTER"},{"type":"Width","data":{"type":"length","original":{"v":0.5,"u":"EM"}}},{"type":"MarginTop","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginRight","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginBottom","data":{"original":{"v":10,"u":"EM"}}},{"type":"MarginLeft","data":{"original":{"v":10,"u":"EM"}}},{"type":"OverflowX","data":"SCROLL"},{"type":"OverflowY","data":"SCROLL"},{"type":"All","data":"INITIAL"},{"type":"Direction","data":"RTL"}],"slot":{"parent":"wpt__css-cascade__all-prop-001__1-002"},"text":"987 654","meta":{"sourceTag":"bdo"}}]}"""

        /** VERBATIM tools/titan/runs/wave51-fix/sections/css-display/per-test-ir/
         *  wpt__css-display__display-contents-button.json. */
        private const val displayContentsButton = """{"irVersion":2,"minReaderVersion":2,"components":[{"id":"wpt__css-display__display-contents-button__0-034","name":"wpt__css-display__display-contents-button__0","properties":[{"type":"FontKerning","data":"NONE"},{"type":"FontFeatureSettings","data":{"type":"features","features":[{"tag":"kern","value":0}]}}],"meta":{"role":"body-root"}},{"id":"wpt__css-display__display-contents-button__1-035","name":"wpt__css-display__display-contents-button__1","properties":[],"text":"You should see the word PASS below.","meta":{"sourceTag":"p","role":"ws-after"}},{"id":"wpt__css-display__display-contents-button__2-036","name":"wpt__css-display__display-contents-button__2","properties":[{"type":"All","data":"INITIAL"},{"type":"FontKerning","data":"NONE"},{"type":"FontFeatureSettings","data":{"type":"features","features":[{"tag":"kern","value":0}]}},{"type":"BorderTopWidth","data":{"px":10}},{"type":"BorderRightWidth","data":{"px":10}},{"type":"BorderBottomWidth","data":{"px":10}},{"type":"BorderLeftWidth","data":{"px":10}},{"type":"BorderTopStyle","data":"SOLID"},{"type":"BorderRightStyle","data":"SOLID"},{"type":"BorderBottomStyle","data":"SOLID"},{"type":"BorderLeftStyle","data":"SOLID"},{"type":"BorderTopColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderRightColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderBottomColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"BorderLeftColor","data":{"srgb":{"r":1,"g":0,"b":0},"original":"red"}},{"type":"Display","data":"CONTENTS"}],"text":"PASS","meta":{"sourceTag":"button"}}]}"""
    }
}

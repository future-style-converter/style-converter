package com.styleconverter.runtime.core.renderer

// Wave 18 (RC6) — JVM pins for `display: contents` unboxing
// (css-display-3 §2.5/§2.7). The rewrite is pure over the IR; property
// shapes are copied from the LIVE wave-18 css-display IRs
// (tools/titan/runs/wave18-gate/sections/css-display/per-test-ir), so
// wire drift fails here before it fails on a device.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentsUnboxingTest {

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    // The live Display wire keyword for `display: contents`.
    private val displayContents = prop("Display", "\"CONTENTS\"")

    private fun comp(
        id: String,
        properties: List<IRProperty> = emptyList(),
        children: List<IRComponent>? = null,
        text: String? = null,
        pseudos: String? = null,
    ) = IRComponent(
        id = id, name = id, properties = properties, children = children,
        _text = text,
        pseudos = pseudos?.let { Json.parseToJsonElement(it) as JsonObject },
    )

    // ── The gate (pin C1/C3) ───────────────────────────────────────────────

    @Test fun `only a base display-contents declaration unboxes`() {
        assertTrue(ContentsUnboxing.isUnboxable(comp("c", listOf(displayContents))))
        assertFalse(ContentsUnboxing.isUnboxable(comp("b", listOf(prop("Display", "\"BLOCK\"")))))
        assertFalse(ContentsUnboxing.isUnboxable(comp("none", emptyList())))
    }

    @Test fun `positioned or floated contents is blockified and keeps its box`() {
        // css-display-3 §2.7: absolute/fixed positioning and float blockify
        // `display: contents` — the wrapper box must stay.
        assertFalse(ContentsUnboxing.isUnboxable(
            comp("abs", listOf(displayContents, prop("Position", "\"ABSOLUTE\"")))))
        assertFalse(ContentsUnboxing.isUnboxable(
            comp("float", listOf(displayContents, prop("Float", "\"LEFT\"")))))
        // ANY non-static position keeps the box — deliberately wider than
        // §2.7 (which blockifies abspos/float only): stripping a
        // relatively positioned wrapper would desync the hoist walk's
        // positioned-ancestor threading (CSS 2.1 §10.1 over the ORIGINAL
        // tree) from the composition's stripped tree, and a box with no
        // overlay slot is dropped outright. Documented approximation.
        assertFalse(ContentsUnboxing.isUnboxable(
            comp("rel", listOf(displayContents, prop("Position", "\"RELATIVE\"")))))
        // An explicit `static` keyword stays unboxable (the initial value).
        assertTrue(ContentsUnboxing.isUnboxable(
            comp("stat", listOf(displayContents, prop("Position", "\"STATIC\"")))))
    }

    @Test fun `pseudo-bearing contents keeps the legacy wrapper - the before-after greens stay`() {
        // The four currently-green display-contents-before-after tests
        // carry pseudo payloads on their contents nodes — pinned untouched.
        val withPseudo = comp("p", listOf(displayContents), pseudos = """{"before":{}}""")
        assertFalse(ContentsUnboxing.isUnboxable(withPseudo))
        // resolve() must be the identity INSTANCE for such a component.
        assertSame(withPseudo, ContentsUnboxing.resolve(withPseudo))
    }

    // ── The splice (pin C1/C2) — live display-contents-alignment-002 ──────

    @Test fun `grid wrapper splice - the grandchild becomes the direct grid item`() {
        // Live shape: grid container → contents wrapper (JustifyItems
        // START) → 100×100 blue square (JustifySelf auto).
        val square = comp("square", listOf(
            prop("Width", """{"type":"length","px":100}"""),
            prop("Height", """{"type":"length","px":100}"""),
            prop("JustifySelf", """{"type":"auto"}"""),
        ))
        val wrapper = comp("wrapper", listOf(displayContents, prop("JustifyItems", "\"START\"")), listOf(square))
        val grid = comp("grid", listOf(prop("Display", "\"GRID\""), prop("JustifyItems", "\"CENTER\"")), listOf(wrapper))

        val resolved = ContentsUnboxing.resolve(grid)
        // The wrapper is GONE; the square is the sole direct child (= the
        // grid item), so the container's justify-items reaches it.
        assertEquals(1, resolved.children!!.size)
        val item = resolved.children!![0]
        assertEquals("square", item.id)
        // The wrapper's JustifyItems is NOT inherited (css-align-3 §6.4:
        // justify-items does not inherit) and must NOT leak onto the item.
        assertFalse(item.properties.any { it.type == "JustifyItems" })
        // The item's own declarations survive verbatim.
        assertTrue(item.properties.any { it.type == "JustifySelf" })
    }

    @Test fun `non-inherited box declarations drop - the block-002 margin and the border set`() {
        // display-contents-block-002: the wrapper's MarginTop must vanish
        // (no box to apply to); -block-001/-button: the border set too.
        val pass = comp("pass", text = "PASS")
        val wrapper = comp("wrapper", listOf(
            displayContents,
            prop("MarginTop", """{"px":2124}"""),
            prop("BorderTopWidth", """{"px":10}"""),
        ), listOf(pass))
        val parent = comp("parent", children = listOf(wrapper))
        val resolved = ContentsUnboxing.resolve(parent)
        val spliced = resolved.children!!
        assertEquals(listOf("pass"), spliced.map { it.id })
        assertTrue(spliced[0].properties.none { it.type.startsWith("Margin") || it.type.startsWith("Border") })
    }

    @Test fun `inheritable declarations DO flow through the removed box`() {
        // css-display-3 §2.5 removes the BOX, not the element: color still
        // inherits into the spliced grandchild (under its own declarations).
        val child = comp("leaf", listOf(prop("FontSize", """{"px":20}""")))
        val wrapper = comp("wrapper", listOf(
            displayContents,
            prop("Color", """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""),
            prop("FontSize", """{"px":12}"""),
        ), listOf(child))
        val parent = comp("parent", children = listOf(wrapper))
        val item = ContentsUnboxing.resolve(parent).children!![0]
        // Wrapper's Color fills the gap…
        assertTrue(item.properties.any { it.type == "Color" })
        // …but the child's own FontSize wins over the wrapper's.
        assertEquals("""{"px":20}""", item.properties.last { it.type == "FontSize" }.data.toString())
    }

    @Test fun `text runs survive as carriers - the details chain flattens recursively`() {
        // Live display-contents-details: details(contents, text S) →
        // summary(contents, text A). Both boxes vanish; both text runs stay
        // in document order.
        val summary = comp("summary", listOf(displayContents, prop("BorderTopWidth", """{"px":10}""")), text = "A")
        val details = comp("details", listOf(displayContents, prop("BorderTopWidth", """{"px":10}""")), listOf(summary), text = "S")
        // details is a ROOT here → self-strip + recursive child splice.
        val resolved = ContentsUnboxing.resolve(details)
        // Self-strip: no border survives on the stripped root…
        assertTrue(resolved.properties.none { it.type.startsWith("Border") })
        // …its text stays (renders as the leading text run)…
        assertEquals("S", resolved._text)
        // …and the summary child became a bare text carrier for "A".
        assertEquals(1, resolved.children!!.size)
        val carrier = resolved.children!![0]
        assertEquals("A", carrier._text)
        assertTrue(carrier.properties.none { it.type.startsWith("Border") })
        assertNull(carrier.children)
    }

    @Test fun `button root self-strips - text kept, border and All dropped`() {
        // Live display-contents-button: root button with all:initial +
        // 10px red border + display:contents + text PASS. The self-strip
        // removes every non-inherited declaration INCLUDING `All`, so the
        // renderer's all-reset never fires on the pass-through.
        val button = comp("button", listOf(
            prop("All", "\"INITIAL\""),
            prop("BorderTopWidth", """{"px":10}"""),
            prop("BorderTopStyle", "\"SOLID\""),
            displayContents,
        ), text = "PASS")
        val resolved = ContentsUnboxing.resolve(button)
        assertEquals("PASS", resolved._text)
        assertTrue(resolved.properties.none { it.type == "All" || it.type.startsWith("Border") || it.type == "Display" })
    }

    // ── Identity + normalization pins ──────────────────────────────────────

    @Test fun `contents-free trees resolve to the SAME instance - baseline byte-stability`() {
        val plain = comp("plain", listOf(prop("Display", "\"BLOCK\"")),
            listOf(comp("kid", listOf(prop("Width", """{"type":"length","px":10}""")))))
        assertSame(plain, ContentsUnboxing.resolve(plain))
    }

    @Test fun `an empty splice normalizes children to null - the decode contract`() {
        // A container whose ONLY child was an empty contents wrapper must
        // render like a wire-decoded childless leaf (children nil, not []).
        val emptyWrapper = comp("wrapper", listOf(displayContents))
        val parent = comp("parent", children = listOf(emptyWrapper))
        assertNull(ContentsUnboxing.resolve(parent).children)
    }
}

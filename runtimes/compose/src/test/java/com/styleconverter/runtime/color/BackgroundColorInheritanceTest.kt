package com.styleconverter.runtime.color

// Wave-49 lane A5 — `background-color: inherit` (css-cascade-5 §7.3.2).
//
// The document under test is the VERBATIM css-color/currentcolor-002 IR:
//   tools/titan/runs/wave48-final/sections/css-color/per-test-ir/
//     wpt__css-color__currentcolor-002.json
// ids, slot parents and property payloads are copied unchanged; only the
// properties irrelevant to colour (Width/Height/FontSize) are omitted, which
// this pass never reads.
//
// Chrome 151 on that exact nesting computes the three backgrounds as
// rgb(255,0,0) / rgb(255,0,0) / rgb(0,128,0) — measured with
// getComputedStyle, not assumed. The pass's job is to make the natives able
// to reach the same answer, by handing each child the parent's UNRESOLVED
// computed value so §6.4 re-resolves per element.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundColorInheritanceTest {

    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)
    private fun p(t: String, s: String) = IRProperty(t, j(s))

    private val RED = """{"srgb":{"r":1,"g":0,"b":0},"original":"red"}"""
    private val GREEN = """{"srgb":{"r":0,"g":0.5019607843137255,"b":0},"original":"green"}"""
    private val CURRENTCOLOR = """{"original":"currentColor"}"""
    private val INHERIT = """{"original":"inherit"}"""

    private fun c(
        id: String,
        props: List<IRProperty>,
        kids: List<IRComponent>? = null,
    ) = IRComponent(id = id, name = id, properties = props, children = kids)

    /** The winning BackgroundColor payload after the pass, or null. */
    private fun bg(c: IRComponent): JsonElement? =
        c.properties.lastOrNull { it.type == "BackgroundColor" }?.data

    // ── The corpus document ──────────────────────────────────────────────

    private fun currentcolor002(): IRComponent = c(
        "wpt__css-color__currentcolor-002__1-251",
        listOf(p("Color", RED), p("BackgroundColor", CURRENTCOLOR)),
        listOf(
            c(
                "currentcolor-002__1__0-252",
                listOf(p("BackgroundColor", INHERIT)),
                listOf(
                    c(
                        "currentcolor-002__1__0__0-253",
                        listOf(p("Color", GREEN), p("BackgroundColor", INHERIT)),
                    ),
                ),
            ),
        ),
    )

    @Test fun `inherit copies the parent's computed value UNRESOLVED`() {
        val out = BackgroundColorInheritance.resolve(currentcolor002())
        val middle = out.children!!.single()
        val inner = middle.children!!.single()
        // §7.3.2 hands down the parent's computed value; §6.4 says
        // `currentcolor` computes to ITSELF, so the keyword — not red —
        // must arrive at both descendants.
        assertEquals(j(CURRENTCOLOR), bg(middle))
        assertEquals(j(CURRENTCOLOR), bg(inner))
    }

    @Test fun `the keyword then resolves per element against its own color`() {
        // The whole point: middle inherits red (no own Color → the inherited
        // red), inner resolves to GREEN because it declares color: green.
        // This is the composition of this pass with CurrentColorBackground,
        // exercised through the real extractor entry point.
        val out = BackgroundColorInheritance.resolve(currentcolor002())
        val middle = out.children!!.single()
        val inner = middle.children!!.single()

        // ComponentRenderer merges the inherited `color` under a component's
        // own declarations before extraction, so `middle` sees the ancestor's
        // red on its list — model that explicitly.
        val middleCfg = ColorExtractor.extractColorConfig(
            listOf("Color" to j(RED), "BackgroundColor" to bg(middle)))
        assertEquals(androidx.compose.ui.graphics.Color(1f, 0f, 0f, 1f), middleCfg.backgroundColor)

        val innerCfg = ColorExtractor.extractColorConfig(
            inner.properties.map { it.type to it.data })
        assertEquals(
            androidx.compose.ui.graphics.Color(0f, 0.5019608f, 0f, 1f),
            innerCfg.backgroundColor)
    }

    // ── Structural contract ──────────────────────────────────────────────

    @Test fun `inherit on a root drops to the initial value`() {
        // css-backgrounds-3 §2.2: the initial value is transparent, and "no
        // declaration" is how every applier already spells that.
        val out = BackgroundColorInheritance.resolve(
            c("root", listOf(p("BackgroundColor", INHERIT))))
        assertNull(bg(out))
        assertTrue(out.properties.none { it.type == "BackgroundColor" })
    }

    @Test fun `an earlier real colour does not resurface behind a losing inherit`() {
        // Last-wins: `background-color: red; background-color: inherit` on a
        // root computes to transparent, NOT red.
        val out = BackgroundColorInheritance.resolve(
            c("root", listOf(p("BackgroundColor", RED), p("BackgroundColor", INHERIT))))
        assertTrue(out.properties.none { it.type == "BackgroundColor" })
    }

    @Test fun `a child's own colour beats the parent's`() {
        val out = BackgroundColorInheritance.resolve(
            c("root", listOf(p("BackgroundColor", RED)),
                listOf(c("kid", listOf(p("BackgroundColor", GREEN))))))
        assertEquals(j(GREEN), bg(out.children!!.single()))
    }

    @Test fun `inherit chains through a parent that also inherits`() {
        // currentcolor-002's middle div is exactly this: inherit → inherit.
        val out = BackgroundColorInheritance.resolve(
            c("root", listOf(p("BackgroundColor", RED)),
                listOf(c("mid", listOf(p("BackgroundColor", INHERIT)),
                    listOf(c("leaf", listOf(p("BackgroundColor", INHERIT))))))))
        assertEquals(j(RED), bg(out.children!!.single().children!!.single()))
    }

    @Test fun `a document without the keyword is returned by identity`() {
        // The blast-radius guarantee: no allocation, no reordering, no
        // property rewrite for the 99.8% of corpus tests that never use it.
        val doc = c("root", listOf(p("BackgroundColor", RED), p("Color", GREEN)),
            listOf(c("kid", listOf(p("Color", RED)))))
        val out = BackgroundColorInheritance.resolve(doc)
        assertSame(doc, out)
        assertSame(doc.children!!.single(), out.children!!.single())
    }

    @Test fun `leaves keep children null rather than becoming empty`() {
        // The composer's absent-vs-empty discipline: the renderer's
        // placeholder branch keys on `children == null`.
        val out = BackgroundColorInheritance.resolve(c("root", listOf(p("Color", RED))))
        assertNull(out.children)
    }

    @Test fun `the keyword is matched case-insensitively in both wire shapes`() {
        // css-values-4 §4.1 — CSS keywords are ASCII case-insensitive; the
        // bare-string form is the shape fixtures/properties carry.
        for (form in listOf("""{"original":"INHERIT"}""", "\"Inherit\"")) {
            val out = BackgroundColorInheritance.resolve(
                c("root", listOf(p("BackgroundColor", RED)),
                    listOf(c("kid", listOf(p("BackgroundColor", form))))))
            assertEquals(j(RED), bg(out.children!!.single()))
        }
    }

    @Test fun `the other CSS-wide keywords are not claimed`() {
        // initial/unset/revert all compute to the initial value on a
        // non-inherited property, which the absence of a declaration already
        // produces — so they must pass through untouched, not be rewritten.
        for (kw in listOf("initial", "unset", "revert", "revert-layer")) {
            val wire = """{"original":"$kw"}"""
            val out = BackgroundColorInheritance.resolve(
                c("root", listOf(p("BackgroundColor", RED)),
                    listOf(c("kid", listOf(p("BackgroundColor", wire))))))
            assertEquals(j(wire), bg(out.children!!.single()))
        }
    }

    @Test fun `a color-mix parent is handed down unresolved too`() {
        // color-mix-currentcolor-001: the child must receive the MIX, not the
        // parent's evaluation of it, so `currentColor` inside it re-resolves
        // against the child's own green (Chrome 151: the child computes
        // color(srgb 0 0.501961 0) — plain green).
        val mixWire = """{"original":{"type":"color-mix","colorSpace":"srgb",""" +
            """"color1":"currentColor","percent1":50,"color2":"green"}}"""
        val out = BackgroundColorInheritance.resolve(
            c("outer", listOf(p("Color", RED), p("BackgroundColor", mixWire)),
                listOf(c("inner", listOf(p("Color", GREEN), p("BackgroundColor", INHERIT))))))
        val inner = out.children!!.single()
        assertEquals(j(mixWire), bg(inner))
        val cfg = ColorExtractor.extractColorConfig(inner.properties.map { it.type to it.data })
        assertEquals(androidx.compose.ui.graphics.Color(0f, 0.5019608f, 0f, 1f), cfg.backgroundColor)
    }
}

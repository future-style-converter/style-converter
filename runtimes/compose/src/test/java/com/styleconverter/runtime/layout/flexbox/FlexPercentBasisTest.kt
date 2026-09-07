package com.styleconverter.runtime.layout.flexbox

// Retro R2 (A7#0) — percent flex-basis on the Compose nowrap line, and the
// css-flexbox-1 §4.1 out-of-flow exclusion that shares the flexLineSpec seam.
//
// Twin of runtimes/swiftui/Tests/.../FlowLayoutColumnWrapTests.swift
// `testPercentBasisSizesWrapItems_025Shape` (wave 48, lane W7): the SAME
// verbatim css-gaps flex-gap-decorations-025 children (`"FlexBasis": 100`,
// height 50, no width) under a container that — exactly like the Swift test
// — takes a declared 300px width as the stand-in for the composed canvas's
// definite main. The iOS twin renders a wrap container; Android's wrap paths
// (FlowRow / FlexWrapRow) have never read flex-basis, so THIS twin pins the
// nowrap line spec, which is the Android path the finding measured
// (ComponentRenderer.flexLineSpec → FlexSizeResolver).
//
// Before this lane the bare-number wire resolved to NO basis
// (ItemPlacementExtractor.flexBasisPx read px shapes only), the line was
// declared content-sized, and each item measured at the 50px placeholder
// floor. The legacy FlexExtractor.parseFlexBasis read the same wire as
// Length(100dp) — N% as N px — into a slot nothing consumed; it is deleted.

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FlexPercentBasisTest {

    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    private fun comp(id: String, props: List<IRProperty>, children: List<IRComponent>? = null) =
        IRComponent(id = id, name = id, properties = props, children = children)

    /** One flex-gap-decorations-025 child, VERBATIM from the wave49-final per-test IR. */
    private fun child025(i: Int) = comp("flex__flex-gap-decorations-025__0__$i", listOf(
        prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}"""),
        prop("Height", """{"type":"length","px":50}"""),
        prop("FlexBasis", "100")
    ))

    /**
     * The 025 container's own declarations (Display/FlexWrap/gaps verbatim)
     * plus the 300px stand-in width the Swift twin uses for its definite main.
     * FlexWrap is carried but irrelevant to flexLineSpec: the renderer only
     * calls it from the nowrap Row/Column loops.
     */
    private fun container025(children: List<IRComponent>) = comp("wpt__css-gaps__flex__flex-gap-decorations-025__1", listOf(
        prop("Display", "\"FLEX\""),
        prop("FlexWrap", "\"WRAP\""),
        prop("RowGap", """{"type":"length","px":10}"""),
        prop("ColumnGap", """{"type":"length","px":10}"""),
        prop("Width", """{"type":"length","px":300}""")
    ), children)

    // ── The twin pin: 100% of a 300px main is a 300px base ─────────────────

    @Test
    fun `025 shape - each percent basis resolves to the full 300px main`() {
        val children = listOf(child025(0), child025(1))
        val spec = ComponentRenderer.flexLineSpec(container025(children), children, rowAxis = true)
        assertNotNull("a line with percent bases is statically resolvable now", spec)
        // css-flexbox-1 §7.2.3: 100% × the container's inner main (300, no
        // padding/border) = 300 per item; before the lane both were null.
        assertEquals(300.0, spec!!.items[0].basisPx!!, 0.0)
        assertEquals(300.0, spec.items[1].basisPx!!, 0.0)
        // The column-gap feeds the line as before — nothing else moved.
        assertEquals(10.0, spec.gapPx, 0.0)
        assertEquals(300.0, spec.contentMainPx, 0.0)
        // Nowrap consequence, stated: two 300-based items in a 300 line
        // shrink (flex-shrink initial 1, §9.7.4.c) to 145 each — 300 minus
        // the 10px gap, split by equal scaled shrink factors.
        val sizes = FlexSizeResolver.resolve(spec.contentMainPx, spec.gapPx, spec.items)
        assertNotNull(sizes)
        assertEquals(145.0, sizes!![0], 0.01)
        assertEquals(145.0, sizes[1], 0.01)
    }

    @Test
    fun `percent basis resolves against the CONTENT main - padding and border come off`() {
        // background-clip-content-box-002 shape: container width 100, two
        // children `"FlexBasis": 50` — but with bands added to prove the
        // §7.2.3 basis is the INNER main: 100 − 10 − 10 padding − 2 − 2
        // border = 76 → 50% = 38 each.
        val children = listOf(
            comp("a", listOf(prop("FlexBasis", "50"), prop("Height", """{"type":"length","px":100}"""))),
            comp("b", listOf(prop("FlexBasis", "50"), prop("Height", """{"type":"length","px":100}""")))
        )
        val container = comp("c", listOf(
            prop("Display", "\"FLEX\""),
            prop("Width", """{"type":"length","px":100}"""),
            prop("PaddingLeft", """{"px":10}"""), prop("PaddingRight", """{"px":10}"""),
            prop("BorderLeftWidth", """{"px":2}"""), prop("BorderRightWidth", """{"px":2}""")
        ), children)
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = true)!!
        assertEquals(76.0, spec.contentMainPx, 0.0)
        assertEquals(38.0, spec.items[0].basisPx!!, 0.0)
        assertEquals(38.0, spec.items[1].basisPx!!, 0.0)
    }

    @Test
    fun `percent basis is clamped to the main - the iOS twin rule`() {
        // ComponentRenderer.swift:2601 (nowrap) / :2733 (wrap plan) and
        // FlowLayout.swift:213: min(mainAvail × pct / 100, mainAvail).
        // 150% of 200 → 200, not 300.
        val children = listOf(comp("a", listOf(prop("FlexBasis", "150"))))
        val container = comp("c", listOf(prop("Display", "\"FLEX\""), prop("Width", """{"type":"length","px":200}""")), children)
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = true)!!
        assertEquals(200.0, spec.items[0].basisPx!!, 0.0)
    }

    @Test
    fun `px basis wins over percent and percent wins over the main-size property`() {
        // Precedence of the used flex basis (css-flexbox-1 §9.2 step 3.A: flex-basis
        // over the main size property). A child can only carry one FlexBasis
        // wire, so px-vs-percent is exercised on two siblings.
        val children = listOf(
            comp("px", listOf(prop("FlexBasis", """{"value":{"px":40},"normalizedPixels":40}"""), prop("Width", """{"type":"length","px":90}"""))),
            comp("pct", listOf(prop("FlexBasis", "25"), prop("Width", """{"type":"length","px":90}""")))
        )
        val container = comp("c", listOf(prop("Display", "\"FLEX\""), prop("Width", """{"type":"length","px":400}""")), children)
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = true)!!
        assertEquals(40.0, spec.items[0].basisPx!!, 0.0)
        assertEquals(100.0, spec.items[1].basisPx!!, 0.0)
        // The declared width still sets the automatic minimum (web-wrapper
        // rule: min = main-size ?? min-size ?? floor) — unchanged.
        assertEquals(90.0, spec.items[1].minPx, 0.0)
    }

    @Test
    fun `column axis resolves percent against the inner HEIGHT`() {
        val children = listOf(comp("a", listOf(prop("FlexBasis", "50"))))
        val container = comp("c", listOf(
            prop("Display", "\"FLEX\""), prop("FlexDirection", "\"COLUMN\""),
            prop("Height", """{"type":"length","px":120}"""),
            prop("PaddingTop", """{"px":10}""")
        ), children)
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = false)!!
        assertEquals(110.0, spec.contentMainPx, 0.0)
        assertEquals(55.0, spec.items[0].basisPx!!, 0.0)
    }

    // ── §4.1: out-of-flow children are not flex items ──────────────────────

    @Test
    fun `abspos-child-002 shape - a lone abspos child does not engage the line`() {
        // wave49-final css-flexbox flexbox-abspos-child-002 __2 container +
        // its __2__0 child, VERBATIM: the only child is `position: absolute`
        // with `"FlexBasis": 80`. css-flexbox-1 §4.1: it is not a flex item,
        // so there is nothing for the line to resolve → null → the legacy
        // loop, whose absposStaticMeasure sizes the child. Before the lane
        // the line engaged on the abspos child's basis and (with no px
        // basis) fell into the intrinsic pass, which measured it as an
        // in-flow 50px-floor item — the 50px-wide teal bar in the capture.
        val abspos = comp("abspos__flexbox-abspos-child-002__2__0", listOf(
            prop("Position", "\"ABSOLUTE\""),
            prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}"""),
            prop("Height", """{"type":"length","px":10}"""),
            prop("FlexBasis", "80")
        ))
        val container = comp("wpt__css-flexbox__abspos__flexbox-abspos-child-002__2", listOf(
            prop("Display", "\"FLEX\""),
            prop("Height", """{"type":"length","px":10}"""),
            prop("Width", """{"type":"length","px":10}"""),
            prop("BackgroundColor", """{"srgb":{"r":0.5019607843137255,"g":0,"b":0.5019607843137255},"original":"purple"}"""),
            prop("MarginBottom", """{"px":5}"""),
            prop("Position", "\"RELATIVE\"")
        ), listOf(abspos))
        assertNull(ComponentRenderer.flexLineSpec(container, listOf(abspos), rowAxis = true))
        // Same for the `"content"` (__3__0) and px (__0__0) flavors — the
        // gate is the child's out-of-flow position, not its basis shape.
        for (basis in listOf("\"content\"", """{"value":{"px":2},"normalizedPixels":2}""")) {
            val c = comp("x", listOf(prop("Position", "\"ABSOLUTE\""), prop("FlexBasis", basis)))
            assertNull(ComponentRenderer.flexLineSpec(container.copy(children = listOf(c)), listOf(c), rowAxis = true))
        }
    }

    @Test
    fun `abspos sibling keeps its slot but takes no main space`() {
        // A MIXED line: two in-flow `flex-grow: 1` items with px bases and an
        // abspos sibling declaring a width. §4.1: the sibling is not an item
        // — it must consume 0 of the 300px main (both in-flow items grow to
        // 150) while the item list stays 1:1 with the children so the loops'
        // positional indexing (resolvedSizes[index]) still lines up.
        val grow = """{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number","value":1},"normalizedValue":1}"""
        val children = listOf(
            comp("a", listOf(prop("FlexGrow", grow), prop("FlexBasis", """{"value":{"px":50},"normalizedPixels":50}"""))),
            comp("abs", listOf(prop("Position", "\"ABSOLUTE\""), prop("Width", """{"type":"length","px":80}"""))),
            comp("b", listOf(prop("FlexGrow", grow), prop("FlexBasis", """{"value":{"px":50},"normalizedPixels":50}""")))
        )
        val container = comp("c", listOf(prop("Display", "\"FLEX\""), prop("Width", """{"type":"length","px":300}""")), children)
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = true)!!
        assertEquals(3, spec.items.size)
        // The neutral slot: zero base, inflexible, zero band.
        assertEquals(0.0, spec.items[1].basisPx!!, 0.0)
        assertEquals(0.0, spec.items[1].grow, 0.0)
        assertEquals(0.0, spec.items[1].shrink, 0.0)
        assertEquals(0.0, spec.items[1].minPx, 0.0)
        assertEquals(0.0, spec.items[1].maxPx, 0.0)
        val sizes = FlexSizeResolver.resolve(spec.contentMainPx, spec.gapPx, spec.items)!!
        assertEquals(150.0, sizes[0], 0.01)
        assertEquals(0.0, sizes[1], 0.0)
        assertEquals(150.0, sizes[2], 0.01)
    }
}

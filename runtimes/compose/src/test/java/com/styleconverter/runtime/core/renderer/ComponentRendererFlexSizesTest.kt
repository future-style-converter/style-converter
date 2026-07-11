package com.styleconverter.runtime.core.renderer

// Wave-2: pins resolveFlexMainSizes — the IR-level bridge that feeds
// FlexSizeResolver from real converter output. Property shapes below are
// verbatim from `--to ir` on fixtures/fidelity/trees/flex-row.json /
// flex-column.json (FlexBasis {"value":{"px":40},"normalizedPixels":40},
// FlexGrow nested-Number, padding {"px":8}, gap {"type":"length","px":8}).

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ComponentRendererFlexSizesTest {

    private fun prop(type: String, json: String): IRProperty =
        IRProperty(type, Json.parseToJsonElement(json) as JsonElement)

    private fun comp(name: String, props: List<IRProperty>, children: List<IRComponent>? = null) =
        IRComponent(id = name, name = name, properties = props, children = children)

    private fun flexRowContainer(widthPx: Int) = listOf(
        prop("Width", """{"type":"length","px":$widthPx.0}"""),
        prop("Display", "\"FLEX\""),
        prop("ColumnGap", """{"type":"length","px":8.0}"""),
        prop("PaddingLeft", """{"px":8.0}"""),
        prop("PaddingRight", """{"px":8.0}""")
    )

    private fun growChild(grow: Double, basisPx: Double) = comp(
        "c$grow", listOf(
            prop("Height", """{"type":"length","px":40.0}"""),
            prop(
                "FlexGrow",
                """{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number","value":$grow},"normalizedValue":$grow}"""
            ),
            prop("FlexBasis", """{"value":{"px":$basisPx},"normalizedPixels":$basisPx}""")
        )
    )

    @Test
    fun `FR_GrowBasis IR resolves to the pixel-verified web sizes`() {
        val children = listOf(growChild(0.0, 40.0), growChild(1.0, 40.0), growChild(2.0, 40.0))
        val container = comp("FR_GrowBasis", flexRowContainer(320), children)
        val sizes = ComponentRenderer.resolveFlexMainSizes(container, children, rowAxis = true)
        assertNotNull(sizes)
        assertEquals(50.0, sizes!![0], 0.51)   // min floor 50 clamps basis 40
        assertEquals(92.67, sizes[1], 0.51)    // web: b ≈ 93
        assertEquals(145.33, sizes[2], 0.51)   // web: c ≈ 145
    }

    @Test
    fun `plain row without flex properties stays on the legacy path`() {
        val children = listOf(
            comp("a", listOf(prop("Width", """{"type":"length","px":60.0}"""))),
            comp("b", listOf(prop("Width", """{"type":"length","px":60.0}""")))
        )
        val container = comp("row", flexRowContainer(320), children)
        // No FlexGrow/FlexShrink/FlexBasis anywhere → null → legacy render.
        assertNull(ComponentRenderer.resolveFlexMainSizes(container, children, rowAxis = true))
    }

    @Test
    fun `indefinite container main size bails to legacy`() {
        val children = listOf(growChild(1.0, 40.0))
        val container = comp(
            "unsized",
            listOf(prop("Display", "\"FLEX\"")),
            children
        )
        assertNull(ComponentRenderer.resolveFlexMainSizes(container, children, rowAxis = true))
    }

    // ---- wave 9 (#40): content-sized bases hand off to the intrinsic pass --

    /** A flex child with grow but NO width/basis — content-sized base. */
    private fun autoBasisChild(name: String, grow: Double) = comp(
        name, listOf(
            prop(
                "FlexGrow",
                """{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number","value":$grow},"normalizedValue":$grow}"""
            )
        )
    )

    @Test
    fun `auto basis nulls the item base but keeps the line spec`() {
        // The static resolver must still bail (null) — but flexLineSpec now
        // survives with basisPx == null so the renderer routes the line to
        // FlexIntrinsicRow instead of the legacy weight fallback.
        val children = listOf(growChild(0.0, 40.0), autoBasisChild("b", 1.0))
        val container = comp("FR_AutoBasis", flexRowContainer(320), children)
        assertNull(ComponentRenderer.resolveFlexMainSizes(container, children, rowAxis = true))
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = true)
        assertNotNull(spec)
        assertEquals(304.0, spec!!.contentMainPx, 0.001)   // 320 − 2×8 padding
        assertEquals(8.0, spec.gapPx, 0.001)
        assertEquals(40.0, spec.items[0].basisPx!!, 0.001) // declared base kept
        assertNull(spec.items[1].basisPx)                   // content-sized
        assertEquals(1.0, spec.items[1].grow, 0.001)
        assertEquals(50.0, spec.items[1].minPx, 0.001)      // inline floor
    }

    @Test
    fun `column axis spec reads height row-gap and the 30px block floor`() {
        val children = listOf(autoBasisChild("a", 1.0), autoBasisChild("b", 2.0))
        val container = comp(
            "FC_AutoBasis",
            listOf(
                prop("Height", """{"type":"length","px":220.0}"""),
                prop("Display", "\"FLEX\""),
                prop("RowGap", """{"type":"length","px":6.0}"""),
                prop("PaddingTop", """{"px":6.0}"""),
                prop("PaddingBottom", """{"px":6.0}""")
            ),
            children
        )
        val spec = ComponentRenderer.flexLineSpec(container, children, rowAxis = false)
        assertNotNull(spec)
        assertEquals(208.0, spec!!.contentMainPx, 0.001)   // 220 − 2×6 padding
        assertEquals(6.0, spec.gapPx, 0.001)
        assertNull(spec.items[0].basisPx)
        assertEquals(30.0, spec.items[0].minPx, 0.001)      // block floor
    }

    @Test
    fun `declared max-width feeds the item max clamp`() {
        val child = comp(
            "capped", listOf(
                prop(
                    "FlexGrow",
                    """{"value":{"type":"app.irmodels.properties.layout.flexbox.FlexGrowProperty.FlexGrowValue.Number","value":1.0},"normalizedValue":1.0}"""
                ),
                prop("MaxWidth", """{"type":"length","px":120.0}""")
            )
        )
        val container = comp("FR_MaxWidth", flexRowContainer(320), listOf(child))
        val spec = ComponentRenderer.flexLineSpec(container, listOf(child), rowAxis = true)
        assertNotNull(spec)
        assertEquals(120.0, spec!!.items[0].maxPx, 0.001)
    }

    @Test
    fun `percentage container width stays unresolvable - legacy fallback`() {
        // A percentage main size has no px at convert time: flexLineSpec
        // must return null so the (logged) legacy weight path handles it.
        val children = listOf(autoBasisChild("a", 1.0))
        val container = comp(
            "FR_PercentWidth",
            listOf(prop("Width", """{"type":"percentage","value":50.0}""")),
            children
        )
        assertNull(ComponentRenderer.flexLineSpec(container, children, rowAxis = true))
    }

    @Test
    fun `logical bare px InlineSize counts as definite width`() {
        // Sizing_BoxModel regression: `inline-size: 250px` ships as
        // {"px":250.0} with no type tag — hasDefiniteSize must accept it.
        val props = listOf(prop("InlineSize", """{"px":250.0}"""))
        org.junit.Assert.assertTrue(
            ComponentRenderer.hasDefiniteSize(props, widthAxis = true)
        )
        // `block-size: auto` (primitive) is NOT definite.
        val auto = listOf(prop("BlockSize", "\"auto\""))
        org.junit.Assert.assertFalse(
            ComponentRenderer.hasDefiniteSize(auto, widthAxis = false)
        )
    }
}

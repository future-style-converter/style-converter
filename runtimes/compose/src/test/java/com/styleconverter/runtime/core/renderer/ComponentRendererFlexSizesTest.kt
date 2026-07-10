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

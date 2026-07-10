package com.styleconverter.runtime.core.placement

// ItemPlacementExtractor pinning — the single ITEM-scope owner must keep
// producing EXACTLY the values the renderer's (now delegating) inline
// extractors produced before the v2 refactor, or the pixel freeze breaks.
// Wire shapes below mirror the live parser output byte-for-byte (see the
// converter longhands + placement-claims.json golden).

import com.styleconverter.runtime.core.ir.IRProperty
import com.styleconverter.runtime.core.renderer.ComponentRenderer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemPlacementExtractorTest {

    private fun prop(type: String, json: String) =
        IRProperty(type, Json.parseToJsonElement(json))

    @Test
    fun `defaults are the CSS initial values`() {
        val p = ItemPlacementExtractor.extract(emptyList())
        // design §2.2: absent fields resolve to CSS initials.
        assertEquals(0f, p.flex.grow)
        assertEquals(1f, p.flex.shrink)
        assertNull(p.flex.basisPx)
        assertEquals(0, p.order)
        assertEquals(ComponentRenderer.AlignSelf.AUTO, p.alignSelf)
        assertEquals(ComponentRenderer.JustifySelf.AUTO, p.justifySelf)
        assertNull(p.zIndex)
        assertNull(p.grid.colStart)
        assertNull(p.zIndex)
        assertEquals(ItemPlacement.Default, p)
    }

    @Test
    fun `align-self keyword matrix including the anchor-center fold`() {
        fun of(kw: String) = ItemPlacementExtractor.alignSelf(
            listOf(IRProperty("AlignSelf", JsonPrimitive(kw))))
        assertEquals(ComponentRenderer.AlignSelf.CENTER, of("ANCHOR_CENTER"))   // css-anchor-position §6 fold
        assertEquals(ComponentRenderer.AlignSelf.CENTER, of("anchor-center"))   // kebab flavor
        assertEquals(ComponentRenderer.AlignSelf.FLEX_START, of("START"))
        assertEquals(ComponentRenderer.AlignSelf.FLEX_END, of("flex-end"))
        assertEquals(ComponentRenderer.AlignSelf.STRETCH, of("STRETCH"))
        assertEquals(ComponentRenderer.AlignSelf.BASELINE, of("BASELINE"))
        assertEquals(ComponentRenderer.AlignSelf.AUTO, of("BOGUS"))
    }

    @Test
    fun `grid line claims decode both wire forms`() {
        // Object form {"type":"number","number":N} (the parser's shape)…
        val obj = ItemPlacementExtractor.extract(listOf(
            prop("GridColumnStart", """{"type":"number","number":2}"""),
            prop("GridRowEnd", """{"type":"number","number":4}""")
        ))
        assertEquals(2, obj.grid.colStart)
        assertEquals(4, obj.grid.rowEnd)
        // …and the bare-int legacy primitive.
        val bare = ItemPlacementExtractor.extract(listOf(
            prop("GridRowStart", "3")
        ))
        assertEquals(3, bare.grid.rowStart)
        // span / named lines serialize without `number` → auto (honest fallback).
        val span = ItemPlacementExtractor.extract(listOf(
            prop("GridColumnStart", """{"type":"span","span":2}""")
        ))
        assertNull(span.grid.colStart)
    }

    @Test
    fun `flex claims decode the nested-Number and dual-basis shapes`() {
        val p = ItemPlacementExtractor.extract(listOf(
            // FlexGrow nested sealed-class shape (parser output).
            prop("FlexGrow", """{"value":{"type":"Number","value":2.0}}"""),
            // FlexShrink same family.
            prop("FlexShrink", """{"value":{"type":"Number","value":0.0}}"""),
            // FlexBasis dual storage: normalizedPixels wins.
            prop("FlexBasis", """{"value":{"px":40.0},"normalizedPixels":40.0}""")
        ))
        assertEquals(2f, p.flex.grow)
        assertEquals(0f, p.flex.shrink)
        assertEquals(40.0, p.flex.basisPx!!, 0.0)
        // Percentage basis carries no px → null (line not statically resolvable).
        val pct = ItemPlacementExtractor.extract(listOf(
            prop("FlexBasis", """{"value":{"percent":50.0}}""")
        ))
        assertNull(pct.flex.basisPx)
    }

    @Test
    fun `order and z-index claims`() {
        val p = ItemPlacementExtractor.extract(listOf(
            prop("Order", "-1"),                    // bare int primitive
            prop("ZIndex", """{"value":3}""")       // wrapped int (ZIndex parser shape)
        ))
        assertEquals(-1, p.order)
        assertEquals(3, p.zIndex)
    }

    @Test
    fun `renderer delegates return identical values to the union`() {
        // The pinning contract of the refactor: the (kept) renderer entry
        // points and the union must NEVER diverge — same single owner.
        val props = listOf(
            prop("AlignSelf", "\"CENTER\""),
            prop("JustifySelf", "\"END\""),
            prop("Order", "5"),
            prop("FlexGrow", """{"value":{"type":"Number","value":1.5}}""")
        )
        val union = ItemPlacementExtractor.extract(props)
        assertEquals(ComponentRenderer.extractAlignSelf(props), union.alignSelf)
        assertEquals(ComponentRenderer.extractJustifySelf(props), union.justifySelf)
        assertEquals(ComponentRenderer.extractOrder(props), union.order)
        assertEquals(1.5f, union.flex.grow)
    }
}

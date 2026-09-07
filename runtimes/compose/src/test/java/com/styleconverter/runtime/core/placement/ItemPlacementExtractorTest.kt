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
        // The LIVE span wire is `{"type":"span","count":N}` — probe
        // `:converter:run` 2026-09-05: `grid-column-start: span 2` →
        // `{"type":"span","count":2}` (css-grid-2 §8.3 <integer> && span);
        // the numeric claim stays auto because no line NUMBER was given.
        val span = ItemPlacementExtractor.extract(listOf(
            prop("GridColumnStart", """{"type":"span","count":2}""")
        ))
        assertNull(span.grid.colStart)
        // TOLERANCE, not a live wire (retro R10, A8#5): the `span` key
        // spelling below was never emitted by the converter — the reader
        // must still not mistake it for a line number.
        assertNull(ItemPlacementExtractor.extract(listOf(
            prop("GridColumnStart", """{"type":"span","span":2}""")
        )).grid.colStart)
    }

    @Test
    fun `wave5 span and name wire flavors populate the new claim fields`() {
        // Canonical converter shapes: GridLine.Span → {"type":"span","count":N},
        // GridLine.LineName → {"type":"name","name":X} (the grid-area lowering).
        val p = ItemPlacementExtractor.extract(listOf(
            prop("GridColumnStart", """{"type":"span","count":2}"""),
            prop("GridRowStart", """{"type":"name","name":"media"}"""),
            prop("GridRowEnd", """{"type":"span","count":3}""")
        ))
        // Numeric fields stay auto (pinned semantics untouched)…
        assertNull(p.grid.colStart)
        assertNull(p.grid.rowStart)
        // …while the additive fields carry the claims.
        assertEquals(2, p.grid.colStartSpan)
        assertEquals("media", p.grid.rowStartName)
        assertEquals(3, p.grid.rowEndSpan)
        // A number wire never leaks into span/name fields.
        val num = ItemPlacementExtractor.extract(listOf(
            prop("GridColumnStart", """{"type":"number","number":2}""")
        ))
        assertNull(num.grid.colStartSpan)
        assertNull(num.grid.colStartName)
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
        // A px basis is never ALSO a percent claim (the two wire shapes are
        // exclusive — FlexBasisSerializer emits one per declaration).
        assertNull(p.flex.basisPercent)
        // An object-shaped percent (NOT a shape the converter emits) carries
        // no px → null, and is not the bare-number percent wire either.
        val pct = ItemPlacementExtractor.extract(listOf(
            prop("FlexBasis", """{"value":{"percent":50.0}}""")
        ))
        assertNull(pct.flex.basisPx)
        assertNull(pct.flex.basisPercent)
    }

    // ── Retro R2 (A7#0): the bare-number PERCENT wire ─────────────────────

    @Test
    fun `bare-number FlexBasis is a percent claim - verbatim corpus payloads`() {
        // wave49-final css-gaps flex-gap-decorations-025 child: `"FlexBasis": 100`
        // (flex-basis: 100% through IRPercentage's bare-number serializer).
        val p100 = ItemPlacementExtractor.extract(listOf(
            prop("BackgroundColor", """{"srgb":{"r":0,"g":0.5019607843137255,"b":0.5019607843137255},"original":"teal"}"""),
            prop("Height", """{"type":"length","px":50}"""),
            prop("FlexBasis", "100")
        ))
        assertEquals(100.0, p100.flex.basisPercent!!, 0.0)
        // The percent is NOT a px claim — the line resolves it against its
        // own inner main size (css-flexbox-1 §7.2.3), never the extractor.
        assertNull(p100.flex.basisPx)
        // css-flexbox flexbox-abspos-child-002 __2__0: `"FlexBasis": 80`;
        // css-backgrounds background-clip-content-box-002: `50`. A double
        // primitive decodes identically to an int one.
        assertEquals(80.0, ItemPlacementExtractor.flexBasisPercent(listOf(prop("FlexBasis", "80")))!!, 0.0)
        assertEquals(50.0, ItemPlacementExtractor.flexBasisPercent(listOf(prop("FlexBasis", "50.0")))!!, 0.0)
        // css-values calc-size-flex-007: `flex-basis: 0%` → `0` — a zero
        // percent is a REAL zero basis (flex: 1 0 0%), not "absent".
        assertEquals(0.0, ItemPlacementExtractor.flexBasisPercent(listOf(prop("FlexBasis", "0")))!!, 0.0)
    }

    @Test
    fun `keyword and px FlexBasis shapes are not percent claims`() {
        // flexbox-abspos-child-002 __3__0: `"FlexBasis": "content"` — a
        // STRING primitive must not be coerced into a number.
        assertNull(ItemPlacementExtractor.flexBasisPercent(listOf(prop("FlexBasis", "\"content\""))))
        assertNull(ItemPlacementExtractor.flexBasisPercent(listOf(prop("FlexBasis", "\"auto\""))))
        // The px object shape belongs to flexBasisPx only.
        assertNull(ItemPlacementExtractor.flexBasisPercent(listOf(
            prop("FlexBasis", """{"value":{"px":2},"normalizedPixels":2}"""))))
        // No FlexBasis at all → CSS initial `auto` → no percent claim.
        assertNull(ItemPlacementExtractor.flexBasisPercent(emptyList()))
        assertNull(ItemPlacementExtractor.extract(emptyList()).flex.basisPercent)
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

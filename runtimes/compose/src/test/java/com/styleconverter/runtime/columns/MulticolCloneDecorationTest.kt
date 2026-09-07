package com.styleconverter.runtime.columns

// WAVE-46 LANE Y3 — css-break-3 §5.4 `box-decoration-break: clone`: the
// IR → bands reader the sole-child clone fragmenter consumes, pinned
// against the LIVE wave45-final IR shapes of the corpus' clone family
// (tools/titan/runs/wave45-final/sections/css-break/per-test-ir/):
// borders-008 (10px solid borders, 50px radius) and background-image-007
// (no bands at all).

import com.styleconverter.runtime.columns.MulticolCloneGeometry.Bands
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticolCloneDecorationTest {

    /** An IR property from its wire JSON (keywords are bare string primitives). */
    private fun prop(type: String, json: String) = IRProperty(type, Json.parseToJsonElement(json))

    /** A leaf component carrying [props]. */
    private fun leaf(vararg props: IRProperty) =
        IRComponent(id = "c", name = "c", properties = props.toList())

    /** borders-008's child, verbatim wire shapes (minus color/size, irrelevant here). */
    private fun borders008() = leaf(
        prop("BoxDecorationBreak", "\"CLONE\""),
        prop("BorderTopWidth", """{"px":10}"""),
        prop("BorderBottomWidth", """{"px":10}"""),
        prop("BorderTopStyle", "\"SOLID\""),
        prop("BorderBottomStyle", "\"SOLID\""),
        prop("BorderBottomRightRadius", """{"px":50}"""),
        prop("BorderBottomLeftRadius", """{"px":50}""")
    )

    @Test
    fun `D1 - borders-008 resolves 10px bands with the end band widened to the 50px bottom arc`() {
        assertTrue(MulticolCloneDecoration.declaresClone(borders008()))
        assertEquals(Bands(10, 10, 50), MulticolCloneDecoration.bandsFor(borders008()))
    }

    @Test
    fun `D2 - background-image-007's bandless clone child resolves zero bands`() {
        val child = leaf(
            prop("BoxDecorationBreak", "\"CLONE\""),
            prop("Height", """{"type":"length","px":600}""")
        )
        assertEquals(Bands(0, 0, 0), MulticolCloneDecoration.bandsFor(child))
    }

    @Test
    fun `D3 - slice (declared or default) never builds bands`() {
        // The css-break-3 §5.4 default — no property at all…
        val silent = leaf(prop("BorderTopWidth", """{"px":10}"""))
        assertFalse(MulticolCloneDecoration.declaresClone(silent))
        assertNull(MulticolCloneDecoration.bandsFor(silent))
        // …and an explicit `slice`.
        val explicit = leaf(prop("BoxDecorationBreak", "\"SLICE\""))
        assertFalse(MulticolCloneDecoration.declaresClone(explicit))
        assertNull(MulticolCloneDecoration.bandsFor(explicit))
    }

    @Test
    fun `D4 - padding joins the bands - physical first then the logical fallback`() {
        // background-image-004's shape: 10px borders + 10px padding → 20/20.
        val physical = leaf(
            prop("BoxDecorationBreak", "\"CLONE\""),
            prop("BorderTopWidth", """{"px":10}"""), prop("BorderTopStyle", "\"SOLID\""),
            prop("BorderBottomWidth", """{"px":10}"""), prop("BorderBottomStyle", "\"SOLID\""),
            prop("PaddingTop", """{"px":10}"""), prop("PaddingBottom", """{"px":10}""")
        )
        assertEquals(Bands(20, 20, 20), MulticolCloneDecoration.bandsFor(physical))
        // Logical longhands fold onto top/bottom under horizontal-tb
        // (css-logical-1 §4.1) when the physical ones are absent.
        val logical = leaf(
            prop("BoxDecorationBreak", "\"CLONE\""),
            prop("PaddingBlockStart", """{"px":5}"""), prop("PaddingBlockEnd", """{"px":7}""")
        )
        assertEquals(Bands(5, 7, 7), MulticolCloneDecoration.bandsFor(logical))
    }

    @Test
    fun `D5 - a border side styled none has USED width zero`() {
        // CSS 2.1 §8.5.3: `border-style: none` zeroes the used width even
        // with a declared width — the same gate the painter applies.
        val child = leaf(
            prop("BoxDecorationBreak", "\"CLONE\""),
            prop("BorderTopWidth", """{"px":10}"""), prop("BorderTopStyle", "\"NONE\""),
            prop("BorderBottomWidth", """{"px":10}"""), prop("BorderBottomStyle", "\"SOLID\"")
        )
        assertEquals(Bands(0, 10, 10), MulticolCloneDecoration.bandsFor(child))
    }

    @Test
    fun `D6 - a non-px padding band is not statically resolvable - null bails to slice`() {
        // em padding needs the child's font-size at layout time.
        val em = leaf(
            prop("BoxDecorationBreak", "\"CLONE\""),
            prop("PaddingTop", """{"original":{"v":1,"u":"EM"}}""")
        )
        assertNull(MulticolCloneDecoration.bandsFor(em))
        // The clone declaration is still visible so the fragmenter LOGS.
        assertTrue(MulticolCloneDecoration.declaresClone(em))
    }

    @Test
    fun `D7 - a percentage corner radius resolves only at paint time - null bails to slice`() {
        val pct = leaf(
            prop("BoxDecorationBreak", "\"CLONE\""),
            prop("BorderBottomLeftRadius", """{"original":{"v":50,"u":"PERCENT"}}""")
        )
        assertNull(MulticolCloneDecoration.bandsFor(pct))
    }

    @Test
    fun `D8 - specsFor carries the clone signal and bands on the ChildSpec`() {
        // The fragmenter reads both off the spec; a slice sibling stays default.
        val slice = IRComponent(id = "s", name = "s",
            properties = listOf(IRProperty("Height", JsonPrimitive(30))))
        val specs = MulticolSpannerFlow.specsFor(listOf(borders008(), slice))
        assertTrue(specs[0].cloneDeclared)
        assertEquals(Bands(10, 10, 50), specs[0].cloneBands)
        // A childless, textless clone box is the leaf the pass may clone.
        assertTrue(specs[0].monolithicContent)
        assertFalse(specs[1].cloneDeclared)
        assertNull(specs[1].cloneBands)
    }
}

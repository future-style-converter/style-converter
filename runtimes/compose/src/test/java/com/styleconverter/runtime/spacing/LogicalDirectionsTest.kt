package com.styleconverter.runtime.spacing

// Wave-47 lane Z2 — pins the css-writing-modes-4 §6.4 abstract-to-physical
// side table (LogicalDirections) and its integration into Margin/Padding
// resolve. The identical table is pinned on the iOS runtime
// (LogicalDirectionsTests), so drift here is a cross-platform divergence.

import com.styleconverter.runtime.core.types.LengthValue
import com.styleconverter.runtime.typography.text.DirectionValue
import com.styleconverter.runtime.typography.text.WritingModeValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogicalDirectionsTest {

    // §6.4 rows, ltr column: (mode → bs/be/is/ie physical sides).
    @Test
    fun `table - ltr rows match css-writing-modes-4 §6-4`() {
        assertEquals(
            LogicalSides(PhysicalSide.TOP, PhysicalSide.BOTTOM, PhysicalSide.LEFT, PhysicalSide.RIGHT),
            LogicalSides.of(WritingModeValue.HORIZONTAL_TB, DirectionValue.LTR))
        assertEquals(
            LogicalSides(PhysicalSide.RIGHT, PhysicalSide.LEFT, PhysicalSide.TOP, PhysicalSide.BOTTOM),
            LogicalSides.of(WritingModeValue.VERTICAL_RL, DirectionValue.LTR))
        assertEquals(
            LogicalSides(PhysicalSide.LEFT, PhysicalSide.RIGHT, PhysicalSide.TOP, PhysicalSide.BOTTOM),
            LogicalSides.of(WritingModeValue.VERTICAL_LR, DirectionValue.LTR))
        // sideways-rl boxes lay out exactly like vertical-rl (§4).
        assertEquals(
            LogicalSides.of(WritingModeValue.VERTICAL_RL, DirectionValue.LTR),
            LogicalSides.of(WritingModeValue.SIDEWAYS_RL, DirectionValue.LTR))
        // sideways-lr: the one ltr mode whose inline-start is the BOTTOM edge.
        assertEquals(
            LogicalSides(PhysicalSide.LEFT, PhysicalSide.RIGHT, PhysicalSide.BOTTOM, PhysicalSide.TOP),
            LogicalSides.of(WritingModeValue.SIDEWAYS_LR, DirectionValue.LTR))
    }

    // rtl flips ONLY the inline sides of each row.
    @Test
    fun `table - rtl flips inline sides only`() {
        assertEquals(
            LogicalSides(PhysicalSide.TOP, PhysicalSide.BOTTOM, PhysicalSide.RIGHT, PhysicalSide.LEFT),
            LogicalSides.of(WritingModeValue.HORIZONTAL_TB, DirectionValue.RTL))
        assertEquals(
            LogicalSides(PhysicalSide.RIGHT, PhysicalSide.LEFT, PhysicalSide.BOTTOM, PhysicalSide.TOP),
            LogicalSides.of(WritingModeValue.VERTICAL_RL, DirectionValue.RTL))
        assertEquals(
            LogicalSides(PhysicalSide.LEFT, PhysicalSide.RIGHT, PhysicalSide.BOTTOM, PhysicalSide.TOP),
            LogicalSides.of(WritingModeValue.VERTICAL_LR, DirectionValue.RTL))
    }

    // The blast-radius contract: horizontal modes yield NULL (legacy path).
    @Test
    fun `verticalOrNull - horizontal modes stay null even under rtl`() {
        assertNull(LogicalSides.verticalOrNull(emptyList()))
        assertNull(LogicalSides.verticalOrNull(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("HORIZONTAL_TB"),
            "Direction" to JsonPrimitive("RTL"))))
    }

    // Wire-shaped read: the SHOUTY IR keyword + inherited Direction.
    @Test
    fun `verticalOrNull - vertical-rl wire keyword maps block-end to LEFT`() {
        val sides = LogicalSides.verticalOrNull(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("VERTICAL_RL")))
        assertEquals(PhysicalSide.LEFT, sides?.blockEnd)
        assertEquals(PhysicalSide.RIGHT, sides?.blockStart)
    }

    // The css-break wall's exact shape: margin-block-end 20 under
    // vertical-rl resolves to a LEFT margin; vertical-lr to a RIGHT one.
    @Test
    fun `margin resolve - block-end lands left under vertical-rl and right under vertical-lr`() {
        val px20 = buildJsonObject { put("px", 20) }
        fun resolved(mode: String) = MarginExtractor.extract(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive(mode),
            "MarginBlockEnd" to px20,
        )).resolve(isRtl = false)
        val rl = resolved("VERTICAL_RL")
        assertEquals(MarginValue.Length(LengthValue.Exact(20.0)), rl.left)
        assertNull(rl.bottom)
        val lr = resolved("VERTICAL_LR")
        assertEquals(MarginValue.Length(LengthValue.Exact(20.0)), lr.right)
        assertNull(lr.bottom)
    }

    // Physical declarations still beat the mapped logical value (cascade
    // tie already ordered by the extractor) and horizontal-tb is untouched.
    @Test
    fun `padding resolve - physical wins over mapped logical, horizontal path unchanged`() {
        val px8 = buildJsonObject { put("px", 8) }
        val px3 = buildJsonObject { put("px", 3) }
        val vertical = PaddingExtractor.extract(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("VERTICAL_RL"),
            "PaddingBlockStart" to px8,  // → RIGHT under vertical-rl
            "PaddingRight" to px3,       // physical right beats it
        )).resolve(isRtl = false)
        assertEquals(LengthValue.Exact(3.0), vertical.right)
        assertNull(vertical.top)
        // Legacy horizontal behaviour byte-identical: block-start → top.
        val horizontal = PaddingExtractor.extract(listOf<Pair<String, JsonElement?>>(
            "PaddingBlockStart" to px8,
        )).resolve(isRtl = false)
        assertEquals(LengthValue.Exact(8.0), horizontal.top)
    }

    // Inline sides under vertical-rl+ltr land on TOP/BOTTOM.
    @Test
    fun `margin resolve - inline sides are vertical under vertical-rl`() {
        val px5 = buildJsonObject { put("px", 5) }
        val px7 = buildJsonObject { put("px", 7) }
        val r = MarginExtractor.extract(listOf<Pair<String, JsonElement?>>(
            "WritingMode" to JsonPrimitive("VERTICAL_RL"),
            "MarginInlineStart" to px5,
            "MarginInlineEnd" to px7,
        )).resolve(isRtl = false)
        assertEquals(MarginValue.Length(LengthValue.Exact(5.0)), r.top)
        assertEquals(MarginValue.Length(LengthValue.Exact(7.0)), r.bottom)
        assertNull(r.left)
        assertNull(r.right)
    }
}

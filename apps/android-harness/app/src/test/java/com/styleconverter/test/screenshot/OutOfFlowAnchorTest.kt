package com.styleconverter.test.screenshot

import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the out-of-flow capture-canvas anchoring predicates in
 * [ScreenshotCaptureScreen]: [isOutOfFlowRoot] plus the per-axis auto-inset
 * rule ([hasHorizontalInset] / [hasVerticalInset]). These are the pure
 * decisions behind the Layout branch's per-axis placement — an axis with NO
 * declared inset keeps the box at its CSS static position (content origin
 * inside the canvas padding, CSS 2.1 §10.3.7 / §10.6.4), while a declared
 * inset anchors at the containing block's padding edge (canvas outer edge).
 *
 * Plain junit:4.13.2 (app/build.gradle testImplementation), no Android
 * runtime — same device-free pattern as WptCaptureModeTest; the IR model +
 * kotlinx JSON types ride the `:runtime` project dependency.
 */
class OutOfFlowAnchorTest {

    /** Build a minimal component carrying only the given IR property types
     *  (data payloads are irrelevant to the presence-based predicates). */
    private fun component(vararg types: String) = IRComponent(
        id = "test",
        name = "Test",
        properties = types.map { IRProperty(type = it, data = JsonPrimitive("20px")) }
    )

    // ── isOutOfFlowRoot — the branch gate ───────────────────────────────────

    @Test
    fun outOfFlowRoot_firesOnAbsoluteAndFixed() {
        // The out-of-flow contract applies to abs/fixed roots only (§9.6).
        // The Kotlin enum serializer uppercases; a lowercase wire spelling
        // must match too (the predicate normalises via uppercase()).
        assertTrue(isOutOfFlowRoot(IRComponent("t", "T",
            listOf(IRProperty("Position", JsonPrimitive("ABSOLUTE"))))))
        assertTrue(isOutOfFlowRoot(IRComponent("t", "T",
            listOf(IRProperty("Position", JsonPrimitive("fixed"))))))
    }

    @Test
    fun outOfFlowRoot_ignoresInFlowPositions() {
        // static / relative / sticky roots keep the normal block-flow canvas.
        assertFalse(isOutOfFlowRoot(IRComponent("t", "T",
            listOf(IRProperty("Position", JsonPrimitive("RELATIVE"))))))
        assertFalse(isOutOfFlowRoot(component()))
    }

    // ── Per-axis inset presence (all-auto → static position) ────────────────

    @Test
    fun allAutoInsets_neitherAxisAnchorsAtPaddingEdge() {
        // No inset properties at all — CSS keeps BOTH axes at the static
        // position, so the Layout must NOT back out the canvas padding.
        val c = component("Position", "Width", "Height")
        assertFalse(hasHorizontalInset(c))
        assertFalse(hasVerticalInset(c))
    }

    @Test
    fun leftInset_anchorsHorizontalAxisOnly() {
        // `left: 20px; top/bottom: auto` — x anchors at the padding edge,
        // y stays at the static position (the per-axis split, §10.3.7).
        val c = component("Left")
        assertTrue(hasHorizontalInset(c))
        assertFalse(hasVerticalInset(c))
    }

    @Test
    fun topInset_anchorsVerticalAxisOnly() {
        // Mirror case: `top: 20px; left/right: auto` (§10.6.4).
        val c = component("Top")
        assertFalse(hasHorizontalInset(c))
        assertTrue(hasVerticalInset(c))
    }

    @Test
    fun rightAndBottom_countAsInsetsToo() {
        // An axis is "declared" when EITHER side carries an inset — right
        // and bottom anchor their axes exactly like left and top.
        assertTrue(hasHorizontalInset(component("Right")))
        assertTrue(hasVerticalInset(component("Bottom")))
    }

    @Test
    fun logicalInsets_mapToTheSameAxes() {
        // Logical sides resolve to physical per the runtime's LTR
        // horizontal-tb mapping (PositionExtractor): inline → horizontal,
        // block → vertical. The canvas predicate must agree.
        assertTrue(hasHorizontalInset(component("InsetInlineStart")))
        assertTrue(hasHorizontalInset(component("InsetInlineEnd")))
        assertTrue(hasVerticalInset(component("InsetBlockStart")))
        assertTrue(hasVerticalInset(component("InsetBlockEnd")))
        // And they never leak onto the OTHER axis.
        assertFalse(hasVerticalInset(component("InsetInlineStart")))
        assertFalse(hasHorizontalInset(component("InsetBlockEnd")))
    }
}

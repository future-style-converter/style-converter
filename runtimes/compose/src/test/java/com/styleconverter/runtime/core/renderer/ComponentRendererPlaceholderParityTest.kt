package com.styleconverter.runtime.core.renderer

// Pins the three web-parity rules added for the Android-web visual-test
// divergences found in the 2026-07 web+Android campaign:
//
//   1. demotesEmptyContainer — an empty grid/flex container renders as
//      display:block (mirrors the web harness's explicit display:block
//      override for childless grid/flex wrappers). Regression source:
//      Flex_JustifyCenter / Flex_AlignCenter / Grid_* placeholders were
//      re-positioned by flex arrangement / grid tracks on Android while
//      web pinned them top-left (SSIM 0.80-0.90).
//
//   2. placeholderFillsParentWidth — the placeholder Text spans the
//      parent's content box exactly when the IR declares a definite
//      width, which is what makes `text-align: center` observable
//      (TextAlign_Center rendered left-flushed on Android, centered on
//      web). Indefinite (auto / unresolved px:null) widths must NOT
//      fill, or wrap-content boxes would explode to canvas width.

import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComponentRendererPlaceholderParityTest {

    // ── demotesEmptyContainer ────────────────────────────────────────────

    @Test
    fun `empty grid container demotes to block`() {
        assertTrue(ComponentRenderer.demotesEmptyContainer(
            ComponentRenderer.DisplayType.GRID, childrenEmpty = true))
    }

    @Test
    fun `empty flex row and column demote to block`() {
        assertTrue(ComponentRenderer.demotesEmptyContainer(
            ComponentRenderer.DisplayType.FLEX_ROW, childrenEmpty = true))
        assertTrue(ComponentRenderer.demotesEmptyContainer(
            ComponentRenderer.DisplayType.FLEX_COLUMN, childrenEmpty = true))
    }

    @Test
    fun `grid with children keeps grid layout`() {
        // The demotion is strictly an empty-container rule — real grids
        // with items must keep their tracks.
        assertFalse(ComponentRenderer.demotesEmptyContainer(
            ComponentRenderer.DisplayType.GRID, childrenEmpty = false))
    }

    @Test
    fun `empty block container is not demoted`() {
        // BLOCK is already the target layout — demotion must not fire so
        // the regular Box path (with its alignItems mapping) is used.
        assertFalse(ComponentRenderer.demotesEmptyContainer(
            ComponentRenderer.DisplayType.BLOCK, childrenEmpty = true))
    }

    // ── placeholderFillsParentWidth ──────────────────────────────────────

    // IR wire shapes, matching WidthPropertyParser output serialized into
    // tmpOutput.json: {"type":"length","px":200.0} for absolute lengths,
    // {"type":"percentage","value":50.0} for percentages.
    private fun lengthWidth(px: Double?) = IRProperty(
        type = "Width",
        data = JsonObject(mapOf(
            "type" to JsonPrimitive("length"),
            "px" to (px?.let { JsonPrimitive(it) } ?: JsonNull),
        ))
    )

    private fun percentWidth(value: Double) = IRProperty(
        type = "Width",
        data = JsonObject(mapOf(
            "type" to JsonPrimitive("percentage"),
            "value" to JsonPrimitive(value),
        ))
    )

    @Test
    fun `definite px width fills parent`() {
        // TextAlign_Center: width:200px + text-align:center — the text
        // node must span the 200px content box for centering to show.
        assertTrue(ComponentRenderer.placeholderFillsParentWidth(
            listOf(lengthWidth(200.0))))
    }

    @Test
    fun `percentage width fills parent`() {
        // Percentages resolve at layout time via fillMaxWidth(fraction) in
        // SizingApplier, so the parent box is definite.
        assertTrue(ComponentRenderer.placeholderFillsParentWidth(
            listOf(percentWidth(50.0))))
    }

    @Test
    fun `unresolved px-null width does not fill`() {
        // em / vw / calc widths serialize px:null (runtime-dependent) —
        // the Compose applier can't produce a definite box, so filling
        // would stretch a wrap-content parent to the full canvas.
        assertFalse(ComponentRenderer.placeholderFillsParentWidth(
            listOf(lengthWidth(null))))
    }

    @Test
    fun `no width property does not fill`() {
        // fit-content default (web wrapper) — box hugs the text, block
        // span adds nothing; alignment is a no-op on both platforms.
        assertFalse(ComponentRenderer.placeholderFillsParentWidth(emptyList()))
    }

    @Test
    fun `inline-size behaves like width`() {
        // InlineSize is the logical alias of Width in horizontal writing
        // modes (css-logical-1 §4.1) and flows through the same sizing
        // applier.
        assertTrue(ComponentRenderer.placeholderFillsParentWidth(listOf(
            IRProperty(
                type = "InlineSize",
                data = JsonObject(mapOf(
                    "type" to JsonPrimitive("length"),
                    "px" to JsonPrimitive(120.0),
                ))
            )
        )))
    }
}

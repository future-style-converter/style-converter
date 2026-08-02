package com.styleconverter.test.screenshot

import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.core.ir.IRComponent
import com.styleconverter.runtime.core.ir.IRProperty
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * wave-24 B-RC5 — unit pins for [resolveComposedCanvasPadding], the composed
 * canvas's per-side pad resolver.
 *
 * WHY it exists: capture-browser-ref.mjs frames every reference page with a
 * ZERO-specificity `:where(body) { padding: 16px }`. Per CSS Selectors L4 §17
 * `:where()` contributes no specificity, so a ref declaring its OWN
 * `body { padding: 0 }` (0,0,1) WINS and renders with no body pad. The
 * composed canvas hardcoded 16dp, so every such test's whole render sat
 * (+16,+16) off its ref — MEASURED as the ENTIRE divergence of
 * css-masking/clip-path-circle-007, whose test AND ref both open with
 * `body, div { padding: 0; margin: 0 }`.
 *
 * The contract pinned here is the SAME one the web harness
 * (resolveCanvasPadding) and iOS (ComposedCaptureCanvas.resolvedPadding)
 * implement, so the three composed canvases can never drift apart.
 *
 * Plain junit:4.13.2 — the resolver is a pure function over the IR, no
 * Compose runtime involved (only the Dp value type).
 */
class ComposedCanvasPaddingTest {

    // Build an IRProperty from a raw IR leaf, through the real serializer so
    // the test exercises the same JsonElement shapes the wire carries.
    private fun prop(type: String, json: String) =
        IRProperty(type = type, data = Json.parseToJsonElement(json))

    // A body-root component (meta.role == "body-root") carrying `props`.
    private fun bodyRoot(vararg props: IRProperty) =
        IRComponent(id = "b", name = "t__body", properties = props.toList(), role = "body-root")

    // A plain (non-body) root — never consulted by the resolver.
    private fun plainRoot() = IRComponent(id = "r", name = "t__0")

    @Test
    fun noBodyRoot_keepsTheRefPadOnEverySide() {
        // The ~most common document shape. Must be byte-identical to every
        // pre-wave-24 capture, so all four sides stay at the 16dp default.
        assertEquals(CanvasPadding.DEFAULT, resolveComposedCanvasPadding(listOf(plainRoot())))
    }

    @Test
    fun bodyRootWithoutPadding_keepsTheRefPad() {
        // The a98rgb-003 shape: a body-root exists (it declares a background)
        // but says nothing about padding — that capture must not move.
        val roots = listOf(bodyRoot(prop("BackgroundColor", """{"srgb":{"r":0.5,"g":0.5,"b":0.5,"a":1.0}}""")))
        assertEquals(CanvasPadding.DEFAULT, resolveComposedCanvasPadding(roots))
    }

    @Test
    fun zeroedBodyPad_landsOnEverySide() {
        // clip-path-circle-007: `body, div { padding: 0 }` expands to the four
        // longhands at px 0. This is the whole fix — 0 must reach the canvas.
        val roots = listOf(bodyRoot(
            prop("PaddingTop", """{"px":0.0}"""),
            prop("PaddingRight", """{"px":0.0}"""),
            prop("PaddingBottom", """{"px":0.0}"""),
            prop("PaddingLeft", """{"px":0.0}"""),
        ))
        assertEquals(CanvasPadding(0.dp, 0.dp, 0.dp, 0.dp), resolveComposedCanvasPadding(roots))
    }

    @Test
    fun resolutionIsPerSide_undeclaredSidesKeepTheDefault() {
        // The cascade is per-LONGHAND: `body { padding-left: 40px }` leaves
        // the injected `:where(body)` 16px standing on the other three sides.
        val roots = listOf(bodyRoot(prop("PaddingLeft", """{"px":40.0}""")))
        assertEquals(
            CanvasPadding(top = 16.dp, right = 16.dp, bottom = 16.dp, left = 40.dp),
            resolveComposedCanvasPadding(roots),
        )
    }

    @Test
    fun runtimeDependentLength_keepsTheDefaultInsteadOfGuessing() {
        // The converter emits `padding: 1%` with no absolute px (its base is
        // the containing block, which this canvas is still deciding). No
        // honest answer exists here, so the side keeps the default — the
        // documented contract, not a silent fallthrough.
        val roots = listOf(bodyRoot(prop("PaddingTop", """{"original":{"v":1.0,"u":"PERCENT"}}""")))
        assertEquals(CanvasPadding.DEFAULT, resolveComposedCanvasPadding(roots))
    }

    @Test
    fun negativePadClampsToZero() {
        // CSS 2.1 §8.4 forbids negative padding; a malformed IR must never
        // pull canvas content outside the frame (and thus outside the crop).
        val roots = listOf(bodyRoot(prop("PaddingTop", """{"px":-8.0}""")))
        assertEquals(0.dp, resolveComposedCanvasPadding(roots).top)
    }

    @Test
    fun horizontalBandFeedsTheContainingBlock() {
        // The canvas hands `canvasWidth − horizontal` to the runtime's
        // containing-block channel: 358dp at the default, the full 390dp when
        // the ref zeroes its body pad (matching Chromium's unpadded body).
        assertEquals(32.dp, CanvasPadding.DEFAULT.horizontal)
        assertEquals(0.dp, CanvasPadding(0.dp, 0.dp, 0.dp, 0.dp).horizontal)
    }
}

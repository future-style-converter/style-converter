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
 * WHY it exists (wave 24): capture-browser-ref.mjs used to frame every
 * reference page with a ZERO-specificity `:where(body) { padding: 16px }`.
 * Per CSS Selectors L4 §17 `:where()` contributes no specificity, so a ref
 * declaring its OWN `body { padding: 0 }` (0,0,1) WON and rendered with no
 * body pad. The composed canvas hardcoded 16dp, so every such test's whole
 * render sat (+16,+16) off its ref — MEASURED as the ENTIRE divergence of
 * css-masking/clip-path-circle-007.
 *
 * WHAT CHANGED (wave 25 round 3): at CAL-RC1 the ref stopped injecting a body
 * padding at all — it renders at 358 wide with `padding: 0` and the 16px
 * frame is memcpy'd around the finished PNG. An image-space translation has
 * no cascade, so the frame is UNCONDITIONAL and the author's body padding is
 * an ADDITIONAL inset inside it. Each side is therefore `frame + declared`,
 * declared defaulting to 0. The pins below track that split: the two
 * "nothing declared" rows are UNCHANGED at 16dp (every capture without a body
 * pad is byte-identical), while `padding: 0` now yields 16 and `padding-left:
 * 40px` yields 56.
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
    fun zeroedBodyPad_keepsTheImageFrameOnEverySide() {
        // clip-path-circle-007: `body, div { padding: 0 }` expands to the four
        // longhands at px 0. Wave 24 let that zero the canvas inset entirely,
        // because the frame WAS the (author-beatable) injected body padding.
        // Wave 25 round 3: the frame is applied to the ref PNG in image space,
        // which no author rule can cancel — this ref's content sits at
        // (+16,+16) in the ref image exactly like every other ref's, so the
        // canvas keeps the frame and adds the declared zero to it.
        val roots = listOf(bodyRoot(
            prop("PaddingTop", """{"px":0.0}"""),
            prop("PaddingRight", """{"px":0.0}"""),
            prop("PaddingBottom", """{"px":0.0}"""),
            prop("PaddingLeft", """{"px":0.0}"""),
        ))
        assertEquals(CanvasPadding.DEFAULT, resolveComposedCanvasPadding(roots))
    }

    @Test
    fun resolutionIsPerSide_undeclaredSidesKeepTheBareFrame() {
        // The cascade is per-LONGHAND: `body { padding-left: 40px }` leaves
        // the other three sides at the bare frame, and stacks 40 INSIDE the
        // frame on the left (16 + 40 = 56) — the ref renders that 40px pad in
        // its 358-wide viewport and the image frame adds 16 on top.
        val roots = listOf(bodyRoot(prop("PaddingLeft", """{"px":40.0}""")))
        assertEquals(
            CanvasPadding(top = 16.dp, right = 16.dp, bottom = 16.dp, left = 56.dp),
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
    fun negativePadClampsToZero_leavingTheBareFrame() {
        // CSS 2.1 §8.4 forbids negative padding; a malformed IR must never
        // pull canvas content outside the frame (and thus outside the crop).
        // The AUTHOR contribution clamps at 0, so the side resolves to the
        // bare frame — never inside it.
        val roots = listOf(bodyRoot(prop("PaddingTop", """{"px":-8.0}""")))
        assertEquals(16.dp, resolveComposedCanvasPadding(roots).top)
    }

    @Test
    fun horizontalBandFeedsTheContainingBlock() {
        // The canvas hands `canvasWidth − horizontal` to the runtime's
        // containing-block channel: 358dp at the bare frame — the ref's own
        // 358-wide render viewport — shrinking further by twice whatever the
        // body-root declares (a `padding: 40px` body gives 390 − 112 = 278,
        // which is 358 − 80, the content box Chromium gives that ref).
        assertEquals(32.dp, CanvasPadding.DEFAULT.horizontal)
        assertEquals(112.dp, CanvasPadding(56.dp, 56.dp, 56.dp, 56.dp).horizontal)
    }
}

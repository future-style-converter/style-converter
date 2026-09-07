package com.styleconverter.runtime.effects.backdrop

// Pins the scope gate and the colour maths of the two-pass backdrop render.
// Context: `backdrop-filter` shipped as an explicit no-op on Compose (see
// FilterApplier.applyBackdropFilters) because Modifier.blur/RenderEffect
// filter the ELEMENT, not what is behind it. The composed WPT canvas is a
// controlled tree, so it can be rendered twice instead — and the first thing
// that has to be right is which chains this lane will claim at all.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.effects.filter.FilterFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropChainTest {

    // ── scope gate ────────────────────────────────────────────────────────

    @Test
    fun `invert chain is claimed and keeps its amount`() {
        // 9 of the 11 measured filter-effects bucket-A tests are invert(1).
        val chain = BackdropChain.of(listOf(FilterFunction.Invert(1f)))
        assertNotNull(chain)
        assertEquals(listOf(BackdropOp.Invert(1f)), chain!!.ops)
    }

    @Test
    fun `blur chain is claimed and keeps its radius`() {
        // backdrop-filter-basic-blur.html: blur(10px) on three boxes.
        val chain = BackdropChain.of(listOf(FilterFunction.Blur(10.dp)))
        assertNotNull(chain)
        assertEquals(listOf(BackdropOp.Blur(10.dp)), chain!!.ops)
        assertTrue(chain.hasBlur)
    }

    @Test
    fun `css order is preserved`() {
        // invert then blur is NOT the same image as blur then invert; the
        // painter builds its RenderEffect chain from this list in order.
        val chain = BackdropChain.of(
            listOf(FilterFunction.Blur(4.dp), FilterFunction.Invert(1f)),
        )!!
        assertEquals(BackdropOp.Blur(4.dp), chain.ops[0])
        assertEquals(BackdropOp.Invert(1f), chain.ops[1])
    }

    @Test
    fun `out of scope function refuses the whole chain`() {
        // Refusing wholesale (not rendering the recognised prefix) is what
        // keeps the cross-platform comparison honest: a half-applied chain
        // reads as a rendering bug, an absent one as an unimplemented value.
        assertNull(BackdropChain.of(listOf(FilterFunction.Grayscale(1f))))
        assertNull(
            BackdropChain.of(
                listOf(FilterFunction.Invert(1f), FilterFunction.Sepia(1f)),
            ),
        )
        assertNull(
            BackdropChain.of(
                listOf(FilterFunction.DropShadow(1.dp, 1.dp, 1.dp, Color.Black)),
            ),
        )
    }

    @Test
    fun `empty and none chains carry no work`() {
        // `backdrop-filter: none` is the identity — no ops, so no suppression
        // during pass A and no patch during pass B.
        assertNull(BackdropChain.of(emptyList()))
        assertNull(BackdropChain.of(listOf(FilterFunction.None)))
    }

    // ── invert maths (filter-effects-1 §6.1) ──────────────────────────────

    @Test
    fun `invert one flips every channel`() {
        assertEquals(1f, BackdropChain.invertChannel(1f, 0f), 1e-6f)
        assertEquals(0f, BackdropChain.invertChannel(1f, 1f), 1e-6f)
        // The tests' green backdrop (0, 0.502, 0) inverts to magenta.
        assertEquals(0.498f, BackdropChain.invertChannel(1f, 0.502f), 1e-6f)
    }

    @Test
    fun `invert zero is the identity`() {
        assertEquals(0.25f, BackdropChain.invertChannel(0f, 0.25f), 1e-6f)
    }

    @Test
    fun `invert half collapses every channel to a half`() {
        // c' = 0.5 + c·0 — the spec's fixed point, and the sharpest check
        // that the slope is (1 − 2a) rather than (1 − a).
        assertEquals(0.5f, BackdropChain.invertChannel(0.5f, 0f), 1e-6f)
        assertEquals(0.5f, BackdropChain.invertChannel(0.5f, 1f), 1e-6f)
    }

    @Test
    fun `colour matrix agrees with the scalar curve`() {
        // The matrix is what the platform actually runs; the scalar is what
        // the spec says. Row 0: [scale, 0, 0, 0, translate(0..255)].
        val m = BackdropChain.invertColorMatrix(0.75f)
        val scale = m[0]
        val translate255 = m[4]
        val channel = 0.2f
        assertEquals(
            BackdropChain.invertChannel(0.75f, channel),
            translate255 / 255f + scale * channel,
            1e-6f,
        )
        // Alpha row untouched — invert is colour-only.
        assertEquals(1f, m[18], 0f)
        assertEquals(0f, m[19], 0f)
    }

    @Test
    fun `invert amount clamps above one`() {
        // invert(150%) clamps to 1 per §8.6 rather than over-rotating.
        assertEquals(
            BackdropChain.invertColorMatrix(1f).toList(),
            BackdropChain.invertColorMatrix(1.5f).toList(),
        )
    }

    // ── blur sigma ────────────────────────────────────────────────────────

    @Test
    fun `blur length is the standard deviation`() {
        // NOT r/2 — that is the shadow families' convention
        // (FilterApplier.dropShadowMaskRadius). blur(10px) means σ = 10.
        assertEquals(10f, BackdropChain.blurSigmaPx(10f), 0f)
        assertEquals(0f, BackdropChain.blurSigmaPx(0f), 0f)
        assertEquals(0f, BackdropChain.blurSigmaPx(-3f), 0f)
    }

    @Test
    fun `chain sigma composes gaussians in quadrature`() {
        // Two 3px blurs are one √(9+9) ≈ 4.24px blur, not a 6px one — the
        // sample padding is sized from this.
        val chain = BackdropChain(listOf(BackdropOp.Blur(3.dp), BackdropOp.Blur(3.dp)))
        assertEquals(4.2426f, chain.totalBlurSigmaPx { it.value }, 1e-3f)
    }

    @Test
    fun `chain without blur reports no spread`() {
        val chain = BackdropChain(listOf(BackdropOp.Invert(1f)))
        assertEquals(0f, chain.totalBlurSigmaPx { it.value }, 0f)
        assertTrue(!chain.hasBlur)
    }
}

package com.styleconverter.runtime.borders

// Applier campaign, lane BI-ANDROID — pinning tests for the border-image
// outset direction fix:
//
//  css-backgrounds-3 §6.4: border-image-outset EXTENDS the border image
//  area OUTSIDE the border box with NO layout effect. The pre-fix
//  BorderImageBox applied outset as an inward .padding(...) — the exact
//  inverse: it shrank the border box and pulled the image in. The fix
//  drops the padding and expands the drawBehind destination geometry
//  instead: origin translated by (−outsetLeft, −outsetTop), size grown by
//  the per-axis outset sums (outsetDestRect).
//
//  NOTE: this file used to also pin the wave-3 extraContentInset max()
//  rule. That rule was DELETED by the skeptic fixes — css-backgrounds-3
//  §6: border-image properties do not affect layout, content is inset by
//  border-width ONLY (Chromium never insets for border-image-width) —
//  see BorderImageSpecFixesTest for the successor pins.

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import com.styleconverter.runtime.borders.image.BorderImageApplier
import com.styleconverter.runtime.borders.image.BorderImageDimension
import org.junit.Assert.assertEquals
import org.junit.Test

class BorderImageOutsetTest {

    // Border box used by every dest-rect case: 100 × 60 px at origin (0,0),
    // i.e. the DrawScope-local coordinates drawBorderImage sees.
    private val w = 100f
    private val h = 60f

    // ── 1. Outset dest-rect arithmetic (origin/size for 0 / 3 / 6 px) ────

    @Test
    fun `outset 0 leaves the dest rect exactly the border box`() {
        // §6.4 initial value 0: the border image area IS the border box —
        // origin stays (0,0), size stays (w,h).
        val dest = BorderImageApplier.outsetDestRect(w, h, 0f, 0f, 0f, 0f)
        assertEquals(Rect(0f, 0f, 100f, 60f), dest)
    }

    @Test
    fun `outset 3 shifts the origin outward and grows each axis by 6`() {
        // Uniform 3px outset: origin (−3,−3) — OUTSIDE the border box —
        // and each axis grows by the outset SUM (3 + 3).
        val dest = BorderImageApplier.outsetDestRect(
            w, h,
            outsetTop = 3f, outsetRight = 3f, outsetBottom = 3f, outsetLeft = 3f
        )
        assertEquals(-3f, dest.left, 0f)     // origin.x = −outsetLeft
        assertEquals(-3f, dest.top, 0f)      // origin.y = −outsetTop
        assertEquals(106f, dest.width, 0f)   // w + left + right = 100 + 6
        assertEquals(66f, dest.height, 0f)   // h + top + bottom = 60 + 6
    }

    @Test
    fun `outset 6 doubles the expansion of outset 3`() {
        // Linearity check at the second pinned magnitude: origin (−6,−6),
        // size grown by 12 per axis.
        val dest = BorderImageApplier.outsetDestRect(
            w, h,
            outsetTop = 6f, outsetRight = 6f, outsetBottom = 6f, outsetLeft = 6f
        )
        assertEquals(Rect(-6f, -6f, 106f, 66f), dest)
    }

    @Test
    fun `asymmetric outsets expand each edge independently`() {
        // §6.4 is per-side: top 1 / right 2 / bottom 3 / left 4 must move
        // exactly its own edge — no cross-side coupling.
        val dest = BorderImageApplier.outsetDestRect(
            w, h,
            outsetTop = 1f, outsetRight = 2f, outsetBottom = 3f, outsetLeft = 4f
        )
        assertEquals(Rect(-4f, -1f, 102f, 63f), dest)
    }

    // ── 2. Outset value resolution feeding the dest rect ─────────────────

    @Test
    fun `outset numbers multiply the computed border width`() {
        // §6.4 <number>: a multiple of the COMPUTED border-width — 2 × 3px
        // border = 6px outward, the "outset 6" dest-rect case above.
        with(BorderImageApplier) {
            assertEquals(6.dp, BorderImageDimension.Number(2f).resolveOutset(3.dp))
            // Lengths are literal regardless of the border width.
            assertEquals(3.dp, BorderImageDimension.Length(3.dp).resolveOutset(20.dp))
            // Initial value (absent property) is 0 — no expansion.
            assertEquals(0.dp, (null as BorderImageDimension?).resolveOutset(20.dp))
        }
    }

    // ── 3. Content inset: NONE (spec reversal of the wave-3 rule) ────────
    //
    // css-backgrounds-3 §6: border-image properties do not affect layout —
    // content is inset by the computed border-width ONLY, which
    // ComponentRenderer already applies via StyleApplier.borderContentInset
    // before wrapping in BorderImageBox. The extraContentInset padding
    // (max(0, resolved − computed)) that used to be pinned here was
    // deleted: Chromium leaves the content inset at 10px for border 10px +
    // border-image-width 15px, so the extra 5px shrank Android's content
    // box vs web/iOS. The dest-expansion successor pins (border + padding
    // compensation) live in BorderImageSpecFixesTest.
}

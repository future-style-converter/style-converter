package com.styleconverter.runtime.layout.position

// Pins PositionApplier.resolvedOffset — the VALUE form of the `absoluteOffset`
// applyPosition chains at StyleApplier step 4.
//
// Why this function has to exist at all: the effects step (step 3) is chained
// OUTSIDE the layout step (step 4), so a draw node installed there sits at the
// element's UN-offset slot while the element's own background and borders
// (steps 5–6, INNER of the offset) paint at the offset one. The backdrop lane
// is the consumer that must reconcile the two — it samples the composed canvas
// at a window coordinate and paints into that draw space, so a dropped offset
// mis-places both halves and produces a perfect filter of the wrong rectangle
// (measured: `backdrop-filter-basic.html` on Android put the inverted patch at
// the parent colorbox's origin, its own `left:50px; top:50px` short).
//
// The contract these tests defend is DRIFT, not arithmetic: resolvedOffset must
// return exactly what applyOffset would have applied, under exactly the same
// gates — otherwise the backdrop patch and the element's box move by different
// amounts and the divergence is invisible until a device capture.

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionResolvedOffsetTest {

    // ── the gates: which elements get an offset at all ────────────────────

    @Test
    fun `a default config resolves to zero`() {
        // No positioning properties → applyPosition returns the modifier
        // untouched (its `if (!config.hasPosition) return modifier` line), so
        // there is no offset for the backdrop node to compensate for.
        val offset = PositionApplier.resolvedOffset(PositionConfig())
        assertEquals(0f, offset.x.value, 0f)
        assertEquals(0f, offset.y.value, 0f)
    }

    @Test
    fun `a static element ignores its insets`() {
        // css-position-3 §2: top/left have no effect on a static box, and
        // applyPosition's STATIC branch is the one type that does NOT route
        // through applyOffset. hasPosition is true here (the insets are set),
        // so this pins the SECOND gate, not the first.
        val cfg = PositionConfig(type = PositionType.STATIC, top = 40.dp, start = 25.dp)
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(0f, offset.x.value, 0f)
        assertEquals(0f, offset.y.value, 0f)
    }

    @Test
    fun `a positioned element with no insets resolves to zero`() {
        // `position: absolute` with auto insets: applyOffset's own
        // "only apply offset if there's actual movement" branch emits no
        // modifier, so the backdrop node must not shift either.
        val cfg = PositionConfig(type = PositionType.ABSOLUTE)
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(0f, offset.x.value, 0f)
        assertEquals(0f, offset.y.value, 0f)
    }

    // ── the value: same numbers applyOffset feeds absoluteOffset ──────────

    @Test
    fun `an absolute box resolves its start and top insets`() {
        // backdrop-filter-basic.html's filterbox, verbatim.
        val cfg = PositionConfig(type = PositionType.ABSOLUTE, start = 50.dp, top = 50.dp)
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(50f, offset.x.value, 0f)
        assertEquals(50f, offset.y.value, 0f)
        // And it is literally the config's own accessors — the same two reads
        // applyOffset makes — so the two cannot drift apart.
        assertEquals(cfg.offsetX.value, offset.x.value, 0f)
        assertEquals(cfg.offsetY.value, offset.y.value, 0f)
    }

    @Test
    fun `a relative box resolves too`() {
        // `position: relative` also routes through applyOffset, so a relatively
        // positioned backdrop element has the same reconciliation to make.
        val cfg = PositionConfig(type = PositionType.RELATIVE, start = 12.dp, top = (-8).dp)
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(12f, offset.x.value, 0f)
        assertEquals(-8f, offset.y.value, 0f)
    }

    @Test
    fun `end insets keep their negative sign`() {
        // Wave 22 (B-RC3): a `right`/`bottom` inset resolves NEGATIVE, because
        // the mounting slot anchors the box at the end edge and this offset
        // pulls it back inward. The backdrop node reads the same signed value,
        // so it follows the box in whichever direction the pair composes.
        val cfg = PositionConfig(type = PositionType.ABSOLUTE, end = 30.dp, bottom = 20.dp)
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(-30f, offset.x.value, 0f)
        assertEquals(-20f, offset.y.value, 0f)
    }

    @Test
    fun `start wins over end exactly as applyOffset does`() {
        // CSS precedence (left over right, top over bottom) lives in
        // PositionConfig.offsetX/offsetY — reading them, rather than
        // re-deciding here, is the whole point of this function.
        val cfg = PositionConfig(
            type = PositionType.ABSOLUTE,
            start = 10.dp, end = 90.dp, top = 15.dp, bottom = 70.dp,
        )
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(10f, offset.x.value, 0f)
        assertEquals(15f, offset.y.value, 0f)
    }

    @Test
    fun `a z-index-only element resolves to zero`() {
        // hasPosition is true (z-index != 0) but the type is still STATIC, so
        // applyPosition chains zIndex and nothing else. A backdrop element that
        // only declares z-index must not move.
        val cfg = PositionConfig(zIndex = 5f)
        val offset = PositionApplier.resolvedOffset(cfg)
        assertEquals(0f, offset.x.value, 0f)
        assertEquals(0f, offset.y.value, 0f)
    }
}

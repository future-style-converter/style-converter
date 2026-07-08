package com.styleconverter.runtime.effects.clip

// Pins clip-path xywh() support and the px-position circle center fix,
// both found via the clip-path-basic-shapes fixture:
//   - xywh(x y w h [round r]) extracted as null → element rendered
//     UNCLIPPED on Android (012/013: 16% mismatched pixels vs web).
//   - circle(60px at 20px 30px): readCenterPercent is percent-only, so
//     px centers silently defaulted to 50%/50% (006: SSIM 0.86).

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPathXywhTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun cfg(shapeJson: String) = ClipPathExtractor.extractClipPathConfig(
        listOf("ClipPath" to parse(shapeJson))
    )

    @Test
    fun `xywh extracts rect origin and extent`() {
        // Wire shape for `clip-path: xywh(10px 20px 120px 80px)`.
        val shape = cfg(
            """{"type":"xywh","x":{"px":10.0},"y":{"px":20.0},"w":{"px":120.0},"h":{"px":80.0}}"""
        ).shape as ClipShape.Xywh
        assertEquals(10f, shape.x.value, 0.01f)
        assertEquals(20f, shape.y.value, 0.01f)
        assertEquals(120f, shape.w.value, 0.01f)
        assertEquals(80f, shape.h.value, 0.01f)
        assertEquals(0f, shape.round.value, 0.01f)
    }

    @Test
    fun `xywh round radius extracts`() {
        // `clip-path: xywh(10px 20px 120px 80px round 12px)`.
        val shape = cfg(
            """{"type":"xywh","x":{"px":10.0},"y":{"px":20.0},"w":{"px":120.0},"h":{"px":80.0},"round":{"px":12.0}}"""
        ).shape as ClipShape.Xywh
        assertEquals(12f, shape.round.value, 0.01f)
    }

    @Test
    fun `circle px center is surfaced as Dp coordinates`() {
        // `circle(60px at 20px 30px)` — the definite px center must reach
        // the applier; the percent fields stay at their 50% default and
        // are overridden by the non-null Dp pair at draw time.
        val shape = cfg(
            """{"type":"circle","r":{"px":60.0},"pos":{"x":{"px":20.0},"y":{"px":30.0}}}"""
        ).shape as ClipShape.Circle
        assertEquals(20f, shape.centerXDp!!.value, 0.01f)
        assertEquals(30f, shape.centerYDp!!.value, 0.01f)
    }

    @Test
    fun `circle percent center leaves Dp coordinates null`() {
        // `circle(40% at 50% 50%)` — percent positions keep the legacy
        // percent path; Dp overrides must stay null so the applier
        // resolves against the box size.
        val shape = cfg(
            """{"type":"circle","r":{"original":{"v":40.0,"u":"PERCENT"}},
                "pos":{"x":{"original":{"v":50.0,"u":"PERCENT"}},"y":{"original":{"v":50.0,"u":"PERCENT"}}}}"""
        ).shape as ClipShape.Circle
        assertNull(shape.centerXDp)
        assertNull(shape.centerYDp)
        assertEquals(50f, shape.centerX, 0.01f)
        assertTrue(shape.radius is ClipRadius.Percentage)
    }
}

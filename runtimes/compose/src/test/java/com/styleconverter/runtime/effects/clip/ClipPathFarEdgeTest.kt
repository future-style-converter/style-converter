package com.styleconverter.runtime.effects.clip

// Wave-37 lane W3 twin of the converter's `<position>` repair.
//
// The converter's ClipPathPropertyParser used to accept only bare lengths
// plus a literal `center` inside an `at` clause, so every keyword form
// (`circle(50% at left bottom)`, `circle(50% at right 40px bottom 40px)`)
// was dropped before it reached any runtime. Now the full css-values-4
// `<position>` grammar reaches the wire, and with it two new optional
// fields — `pos.xEdge` / `pos.yEdge` — which mark the one arm that cannot
// be normalised to the default origin without the box extent:
// `[right|bottom] <length-percentage>`.
//
// These tests pin the decode side only (a Config-level assertion); the
// draw-time subtraction lives in ClipPathApplier.axisCenter and is
// exercised by the visual gate.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPathFarEdgeTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    private fun circle(json: String): ClipShape.Circle =
        ClipPathExtractor.extractClipPathConfig(listOf(pair("ClipPath", json))).shape as ClipShape.Circle

    private fun ellipse(json: String): ClipShape.Ellipse =
        ClipPathExtractor.extractClipPathConfig(listOf(pair("ClipPath", json))).shape as ClipShape.Ellipse

    @Test
    fun `circle at right-bottom offsets sets both far-edge anchors`() {
        // `circle(50% at right 40px bottom 40px)`
        val c = circle(
            """{"type":"circle","r":{"original":{"v":50.0,"u":"PERCENT"}},""" +
                """"pos":{"x":{"px":40.0},"y":{"px":40.0},"xEdge":"right","yEdge":"bottom"}}"""
        )
        assertTrue("x must anchor to the right edge", c.centerFromRight)
        assertTrue("y must anchor to the bottom edge", c.centerFromBottom)
        assertEquals(40f, c.centerXDp?.value)
        assertEquals(40f, c.centerYDp?.value)
    }

    @Test
    fun `circle at left-top offsets carries no anchor`() {
        // `circle(50% at left 40px top 40px)` — already measured from the
        // default origin, so the wire omits the edge fields entirely.
        val c = circle(
            """{"type":"circle","r":{"original":{"v":50.0,"u":"PERCENT"}},""" +
                """"pos":{"x":{"px":40.0},"y":{"px":40.0}}}"""
        )
        assertFalse(c.centerFromRight)
        assertFalse(c.centerFromBottom)
    }

    @Test
    fun `a pre-wave-37 at-center wire reads exactly as before`() {
        // The only position form the old parser understood. Its decode must
        // not shift, or every committed Android baseline moves with it.
        val c = circle(
            """{"type":"circle","r":{"original":{"v":50.0,"u":"PERCENT"}},""" +
                """"pos":{"x":{"original":{"v":50.0,"u":"PERCENT"}},"y":{"original":{"v":50.0,"u":"PERCENT"}}}}"""
        )
        assertFalse(c.centerFromRight)
        assertFalse(c.centerFromBottom)
        assertEquals(50f, c.centerX)
        assertEquals(50f, c.centerY)
    }

    @Test
    fun `ellipse gains the same absolute-length and far-edge reading`() {
        // `ellipse(farthest-side closest-side at right 60px top 40px)`.
        // The px centre was previously unreadable on ellipse at all — only
        // the percent path existed — so a px-positioned ellipse recentred.
        val e = ellipse(
            """{"type":"ellipse","rx":"farthest-side","ry":"closest-side",""" +
                """"pos":{"x":{"px":60.0},"y":{"px":40.0},"xEdge":"right"}}"""
        )
        assertTrue(e.centerFromRight)
        assertFalse(e.centerFromBottom)
        assertEquals(60f, e.centerXDp?.value)
        assertEquals(40f, e.centerYDp?.value)
    }
}

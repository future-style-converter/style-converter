package com.styleconverter.runtime.effects.clip

// Phase 8 functional tests for ClipPathExtractor — covering the four basic
// shape types (circle, ellipse, inset, polygon) plus the path("M…") form.
// Fixture shapes mirror the CSS parser's emission for clip-path longhands.

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipPathExtractorTest {

    private fun parse(s: String) = Json.parseToJsonElement(s)
    private fun pair(t: String, j: String) = t to parse(j)

    @Test
    fun `circle clip-path extracts Circle with percentage radius`() {
        // Canonical serializer wire form (ClipPathShapeSerializer +
        // IRLengthSerializer): a percent radius is an IRLength object
        // `{"original":{"v":N,"u":"PERCENT"}}` under the `r` key — the old
        // `{"radius":{"percentage":N}}` shape was never emitted by any
        // serializer and is not accepted by readShapeRadiusAxis.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"circle\",\"r\":{\"original\":{\"v\":50.0,\"u\":\"PERCENT\"}},\"x\":50.0,\"y\":50.0}"))
        )
        val shape = cfg.shape
        assertTrue("expected Circle", shape is ClipShape.Circle)
        val circle = shape as ClipShape.Circle
        assertTrue(circle.radius is ClipRadius.Percentage)
    }

    @Test
    fun `ellipse clip-path extracts Ellipse with dual radii`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"ellipse\",\"rx\":{\"percentage\":40.0},\"ry\":{\"percentage\":25.0},\"x\":50.0,\"y\":50.0}"))
        )
        assertTrue(cfg.shape is ClipShape.Ellipse)
    }

    @Test
    fun `inset clip-path extracts Inset with four edges`() {
        // inset(10px 20px 30px 40px) — four-argument form.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"inset\"," +
                    "\"top\":{\"px\":10.0},\"right\":{\"px\":20.0}," +
                    "\"bottom\":{\"px\":30.0},\"left\":{\"px\":40.0}}"))
        )
        val shape = cfg.shape as ClipShape.Inset
        assertEquals(10f, shape.top.value, 0.01f)
        assertEquals(40f, shape.left.value, 0.01f)
    }

    @Test
    fun `polygon clip-path extracts all points`() {
        // Triangle polygon — three points, legacy percent wire form.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"polygon\",\"points\":[" +
                    "{\"x\":50.0,\"y\":0.0}," +
                    "{\"x\":100.0,\"y\":100.0}," +
                    "{\"x\":0.0,\"y\":100.0}" +
                "]}"))
        )
        val shape = cfg.shape as ClipShape.Polygon
        assertEquals(3, shape.points.size)
        // Each axis must round-trip as Percent (raw-number wire form).
        shape.points.forEach { pt ->
            assertTrue("x should be Percent", pt.x is ClipShape.PolygonAxis.Percent)
            assertTrue("y should be Percent", pt.y is ClipShape.PolygonAxis.Percent)
        }
    }

    @Test
    fun `polygon with px IRLength coordinates preserves all 6 vertices`() {
        // Regression for swarm-002 root cause: the WPT
        // css-masking/clip-path-blending-offset polygon
        // `polygon(0 0, 100px 0, 100px 30px, 30px 30px, 30px 100px, 0 100px)`
        // collapsed to a single vertex before the parser + Point IR fix.
        // Now each x/y is an IRLength object `{px: N}` — the extractor
        // must produce a PolygonAxis.Length per axis.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"polygon\",\"points\":[" +
                    "{\"x\":{\"px\":0.0},\"y\":{\"px\":0.0}}," +
                    "{\"x\":{\"px\":100.0},\"y\":{\"px\":0.0}}," +
                    "{\"x\":{\"px\":100.0},\"y\":{\"px\":30.0}}," +
                    "{\"x\":{\"px\":30.0},\"y\":{\"px\":30.0}}," +
                    "{\"x\":{\"px\":30.0},\"y\":{\"px\":100.0}}," +
                    "{\"x\":{\"px\":0.0},\"y\":{\"px\":100.0}}" +
                "]}"))
        )
        val shape = cfg.shape as ClipShape.Polygon
        assertEquals("all 6 vertices preserved", 6, shape.points.size)
        // Confirm a length-form vertex resolves to the right Dp.
        val second = shape.points[1]
        val sx = second.x as ClipShape.PolygonAxis.Length
        assertEquals(100f, sx.dp.value, 0.01f)
    }

    @Test
    fun `polygon with mixed percent and length axes preserves per-axis kind`() {
        // (0%, 0%), (100px, 50%), (50%, 100px) — verifies the per-axis
        // dispatch in readPolygonAxis works independently for x and y.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"polygon\",\"points\":[" +
                    "{\"x\":0.0,\"y\":0.0}," +
                    "{\"x\":{\"px\":100.0},\"y\":50.0}," +
                    "{\"x\":50.0,\"y\":{\"px\":100.0}}" +
                "]}"))
        )
        val shape = cfg.shape as ClipShape.Polygon
        assertEquals(3, shape.points.size)
        assertTrue(shape.points[0].x is ClipShape.PolygonAxis.Percent)
        assertTrue(shape.points[1].x is ClipShape.PolygonAxis.Length)
        assertTrue(shape.points[1].y is ClipShape.PolygonAxis.Percent)
        assertTrue(shape.points[2].x is ClipShape.PolygonAxis.Percent)
        assertTrue(shape.points[2].y is ClipShape.PolygonAxis.Length)
    }

    @Test
    fun `unknown clip-path type yields null shape`() {
        // Unsupported shape types don't crash — they just produce no clip.
        // This keeps the test-android pipeline resilient to IR drift.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath", "{\"type\":\"bogus\"}"))
        )
        assertNull(cfg.shape)
        assertTrue(!cfg.hasClipPath)
    }

    @Test
    fun `empty properties produces empty config`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(emptyList())
        assertNull(cfg.shape)
    }

    @Test
    fun `path clip-path captures raw SVG string`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath", "{\"type\":\"path\",\"d\":\"M10 10 L 90 10 L 50 90 Z\"}"))
        )
        assertNotNull("Path shape must be extracted", cfg.shape)
        assertTrue(cfg.shape is ClipShape.Path)
    }

    /**
     * swarm-003 css-masking/clip-path-borderBox-1a regression: the new
     * `<shape-radius>` keyword wire form (`r` as a JSON string primitive
     * rather than an IRLength object) must land in the matching
     * ClipRadius keyword variant rather than silently falling back to
     * ClosestSide.
     */
    @Test
    fun `circle with farthest-side keyword radius lands in FarthestSide`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath", "{\"type\":\"circle\",\"r\":\"farthest-side\"}"))
        )
        val circle = cfg.shape as ClipShape.Circle
        assertEquals(ClipRadius.FarthestSide, circle.radius)
    }

    @Test
    fun `circle with closest-side keyword radius lands in ClosestSide`() {
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath", "{\"type\":\"circle\",\"r\":\"closest-side\"}"))
        )
        val circle = cfg.shape as ClipShape.Circle
        assertEquals(ClipRadius.ClosestSide, circle.radius)
    }

    @Test
    fun `ellipse with per-axis keyword radii lands in matching ClipRadius variants`() {
        // ellipse(closest-side farthest-side) — each axis is its own
        // keyword. Verifies the per-axis dispatch in readShapeRadiusAxis.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath",
                "{\"type\":\"ellipse\",\"rx\":\"closest-side\",\"ry\":\"farthest-side\"}"))
        )
        val ellipse = cfg.shape as ClipShape.Ellipse
        assertEquals(ClipRadius.ClosestSide, ellipse.radiusX)
        assertEquals(ClipRadius.FarthestSide, ellipse.radiusY)
    }

    @Test
    fun `circle with px IRLength radius still extracts as Fixed (backward compat)`() {
        // Pre-fix wire form: `r` is an IRLength object — must keep working
        // unchanged so existing fixtures don't regress.
        val cfg = ClipPathExtractor.extractClipPathConfig(
            listOf(pair("ClipPath", "{\"type\":\"circle\",\"r\":{\"px\":40.0}}"))
        )
        val circle = cfg.shape as ClipShape.Circle
        val radius = circle.radius as ClipRadius.Fixed
        assertEquals(40f, radius.dp.value, 0.01f)
    }
}

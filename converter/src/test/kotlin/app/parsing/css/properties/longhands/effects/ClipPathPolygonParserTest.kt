package app.parsing.css.properties.longhands.effects

// Regression suite for the clip-path polygon px-coordinate fix
// (tools/titan/investigations/swarm-002/css-masking__clip-path-blending-
// offset.json). Prior to this fix, polygon() vertices with length units
// (px/em/rem/etc) were silently dropped by `parsePercentageValue` because
// it only matched trailing `%` or unitless numerics. The WPT fixture
// `polygon(0 0, 100px 0, 100px 30px, 30px 30px, 30px 100px, 0 100px)`
// collapsed to a single (0%, 0%) vertex — 5 of 6 points lost — producing
// a degenerate clip that hid the entire green box.
//
// We pin five invariants:
//   1. Pure-percent polygons still parse (backward compat).
//   2. Pure-px polygons parse with all vertices preserved.
//   3. Mixed px / % / unitless-zero polygons preserve every vertex
//      with the correct unit kind on each axis.
//   4. The exact WPT-failure polygon round-trips through the parser
//      to 6 points (the root-cause fixture).
//   5. Other length units (em, rem) reach the IR as IRLengthPercentage.Length.

import app.irmodels.IRLength
import app.irmodels.IRLengthPercentage
import app.irmodels.properties.effects.ClipPathProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClipPathPolygonParserTest {

    /**
     * Unwrap the clip-path -> BasicShape -> Polygon path so each test
     * can assert against the points list directly. Returns null on any
     * mismatch — assertions in the test body will then fail loudly.
     */
    private fun polygonOf(css: String): ClipPathProperty.Shape.Polygon? {
        val prop = ClipPathPropertyParser.parse(css) ?: return null
        val basic = prop.value as? ClipPathProperty.ClipPath.BasicShape ?: return null
        return basic.shape as? ClipPathProperty.Shape.Polygon
    }

    /** (1) Backward compat: pure percentage polygon still parses. */
    @Test
    fun `pure percent polygon parses every vertex as Percentage`() {
        val poly = polygonOf("polygon(0% 0%, 100% 0%, 100% 100%, 0% 100%)")
        assertNotNull(poly, "polygon() should parse")
        assertEquals(4, poly.points.size, "all 4 vertices should survive")
        // Each axis must be the Percentage branch (raw-number wire form,
        // matches the legacy serialization every platform extractor reads).
        poly.points.forEach { p ->
            assertTrue(p.x is IRLengthPercentage.Percentage, "x axis kind")
            assertTrue(p.y is IRLengthPercentage.Percentage, "y axis kind")
        }
        // Spot-check values.
        val first = poly.points[0]
        assertEquals(0.0, (first.x as IRLengthPercentage.Percentage).percentage.value)
        val second = poly.points[1]
        assertEquals(100.0, (second.x as IRLengthPercentage.Percentage).percentage.value)
    }

    /** (2) Pure px polygon: every vertex carries an IRLength on each axis. */
    @Test
    fun `pure px polygon parses every vertex as Length`() {
        val poly = polygonOf("polygon(0px 0px, 100px 0px, 100px 100px, 0px 100px)")
        assertNotNull(poly)
        assertEquals(4, poly.points.size)
        poly.points.forEach { p ->
            assertTrue(p.x is IRLengthPercentage.Length, "x must be Length")
            assertTrue(p.y is IRLengthPercentage.Length, "y must be Length")
        }
        // Third vertex is (100px, 100px) — confirm the actual pixel value.
        val third = poly.points[2]
        val tx = (third.x as IRLengthPercentage.Length).length
        assertEquals(100.0, tx.pixels, "(100px) third vertex x")
    }

    /**
     * (3+4) Mixed-unit polygon: the WPT failure case. This is the canary
     * test — if it regresses, the swarm-002 investigation reopens.
     * Vertices: (0,0) (100px,0) (100px,30px) (30px,30px) (30px,100px) (0,100px)
     * The leading `0` (unitless) is treated as `0px` per CSS Values 4 §5.3.
     */
    @Test
    fun `WPT blending-offset polygon preserves all 6 points`() {
        val css = "polygon(0 0, 100px 0, 100px 30px, 30px 30px, 30px 100px, 0 100px)"
        val poly = polygonOf(css)
        assertNotNull(poly, "polygon must parse")
        assertEquals(6, poly.points.size, "all 6 vertices must survive — root cause of swarm-002 regression")

        // Vertex 0: (0, 0) — unitless zero, must be Length (so it lives in
        // the same coordinate space as the px vertices, not percent).
        val v0 = poly.points[0]
        assertTrue(v0.x is IRLengthPercentage.Length, "v0.x = 0 → Length(0px)")
        assertTrue(v0.y is IRLengthPercentage.Length, "v0.y = 0 → Length(0px)")

        // Vertex 1: (100px, 0) — first explicit px coordinate, was the
        // point originally dropped by the buggy parser.
        val v1 = poly.points[1]
        assertEquals(100.0, (v1.x as IRLengthPercentage.Length).length.pixels)
        assertEquals(0.0, (v1.y as IRLengthPercentage.Length).length.pixels)

        // Vertex 4: (30px, 100px) — interior turn of the inverted-L.
        val v4 = poly.points[4]
        assertEquals(30.0, (v4.x as IRLengthPercentage.Length).length.pixels)
        assertEquals(100.0, (v4.y as IRLengthPercentage.Length).length.pixels)
    }

    /**
     * (3-cont) Mixed % + px on different vertices: each axis preserves
     * its own kind discriminator independently.
     */
    @Test
    fun `mixed percent and px vertices preserve per-axis kind`() {
        val poly = polygonOf("polygon(0% 0%, 100px 50%, 50% 100px)")
        assertNotNull(poly)
        assertEquals(3, poly.points.size)
        // (0%, 0%)
        assertTrue(poly.points[0].x is IRLengthPercentage.Percentage)
        assertTrue(poly.points[0].y is IRLengthPercentage.Percentage)
        // (100px, 50%)
        assertTrue(poly.points[1].x is IRLengthPercentage.Length)
        assertTrue(poly.points[1].y is IRLengthPercentage.Percentage)
        // (50%, 100px)
        assertTrue(poly.points[2].x is IRLengthPercentage.Percentage)
        assertTrue(poly.points[2].y is IRLengthPercentage.Length)
    }

    /** (5) em and rem coordinates land as IRLengthPercentage.Length. */
    @Test
    fun `em and rem coordinates parse as Length`() {
        val poly = polygonOf("polygon(0 0, 2em 0, 2em 1.5rem, 0 1.5rem)")
        assertNotNull(poly)
        assertEquals(4, poly.points.size)
        val v1 = poly.points[1]
        val v1x = v1.x as IRLengthPercentage.Length
        assertEquals(IRLength.LengthUnit.EM, v1x.length.unit, "2em unit")
        assertEquals(2.0, v1x.length.value, "2em value")
        val v2 = poly.points[2]
        val v2y = v2.y as IRLengthPercentage.Length
        assertEquals(IRLength.LengthUnit.REM, v2y.length.unit, "1.5rem unit")
    }
}

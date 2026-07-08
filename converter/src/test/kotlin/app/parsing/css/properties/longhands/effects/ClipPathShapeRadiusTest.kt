package app.parsing.css.properties.longhands.effects

// Regression suite for the clip-path <shape-radius> keyword fix (CSS
// Shapes 1 §3.1 — `<shape-radius>` = `<length-percentage> | closest-side
// | farthest-side`). Prior to this fix the parser only accepted lengths /
// percentages; the two keyword forms returned null from the length
// helper and were silently dropped — see swarm-003
// css-masking__clip-path-borderBox-1a.json for the root-cause walk.
//
// Pinned invariants:
//   1. circle(closest-side) parses with radius = Keyword("closest-side").
//   2. circle(farthest-side) parses with radius = Keyword("farthest-side").
//   3. circle(50%) still parses as Length(IRLength PERCENT) — backward compat.
//   4. ellipse(closest-side farthest-side) parses with each axis as a Keyword.
//   5. ellipse(40px 30%) still parses with Length px / Length percent.
//   6. The wire format round-trips: keyword → JSON string primitive,
//      length → JSON object.

import app.irmodels.IRLength
import app.irmodels.properties.effects.ClipPathProperty
import app.irmodels.properties.effects.ClipPathShapeSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClipPathShapeRadiusTest {

    private val json = Json

    /** Unwrap helper — convert CSS string to a Circle shape via the parser. */
    private fun circleOf(css: String): ClipPathProperty.Shape.Circle? {
        val prop = ClipPathPropertyParser.parse(css) ?: return null
        val basic = prop.value as? ClipPathProperty.ClipPath.BasicShape ?: return null
        return basic.shape as? ClipPathProperty.Shape.Circle
    }

    /** Unwrap helper for ellipse — same shape as circleOf. */
    private fun ellipseOf(css: String): ClipPathProperty.Shape.Ellipse? {
        val prop = ClipPathPropertyParser.parse(css) ?: return null
        val basic = prop.value as? ClipPathProperty.ClipPath.BasicShape ?: return null
        return basic.shape as? ClipPathProperty.Shape.Ellipse
    }

    /** (1) Keyword: closest-side → Keyword variant on Circle. */
    @Test
    fun `circle closest-side parses as Keyword`() {
        val circle = circleOf("circle(closest-side)")
        assertNotNull(circle, "circle() should parse")
        val radius = circle.radius
        assertNotNull(radius, "radius must not be null — pre-fix bug")
        assertTrue(radius is ClipPathProperty.ShapeRadius.Keyword,
            "expected Keyword variant, got ${radius::class.simpleName}")
        assertEquals("closest-side", radius.keyword)
    }

    /** (2) Keyword: farthest-side — the exact WPT failure case. */
    @Test
    fun `circle farthest-side parses as Keyword`() {
        // This is the literal value from WPT
        // css-masking/clip-path/clip-path-borderBox-1a.html — was
        // previously dropped silently by parseLengthOrPercentage.
        val circle = circleOf("circle(farthest-side)")
        assertNotNull(circle)
        val radius = circle.radius
        assertNotNull(radius, "swarm-003 borderBox-1a root cause: radius must survive")
        assertTrue(radius is ClipPathProperty.ShapeRadius.Keyword)
        assertEquals("farthest-side", radius.keyword)
    }

    /** (3) Backward compat: percent radius still parses as Length. */
    @Test
    fun `circle 50 percent radius parses as Length percent`() {
        val circle = circleOf("circle(50%)")
        assertNotNull(circle)
        val radius = circle.radius as? ClipPathProperty.ShapeRadius.Length
        assertNotNull(radius, "50% must still land in the Length branch — byte-compat")
        assertEquals(IRLength.LengthUnit.PERCENT, radius.length.unit)
        assertEquals(50.0, radius.length.value)
    }

    /** (4) Per-axis keyword on ellipse: each axis lands independently. */
    @Test
    fun `ellipse closest-side farthest-side parses with per-axis keywords`() {
        val ellipse = ellipseOf("ellipse(closest-side farthest-side)")
        assertNotNull(ellipse, "ellipse() should parse")
        val rx = ellipse.radiusX as? ClipPathProperty.ShapeRadius.Keyword
        val ry = ellipse.radiusY as? ClipPathProperty.ShapeRadius.Keyword
        assertNotNull(rx, "rx must be Keyword(closest-side)")
        assertNotNull(ry, "ry must be Keyword(farthest-side)")
        assertEquals("closest-side", rx.keyword)
        assertEquals("farthest-side", ry.keyword)
    }

    /** (5) Mixed keyword + length: each axis preserves its own variant. */
    @Test
    fun `ellipse with keyword and length per axis preserves both`() {
        val ellipse = ellipseOf("ellipse(40px closest-side)")
        assertNotNull(ellipse)
        val rx = ellipse.radiusX as? ClipPathProperty.ShapeRadius.Length
        val ry = ellipse.radiusY as? ClipPathProperty.ShapeRadius.Keyword
        assertNotNull(rx, "rx must be Length(40px)")
        assertNotNull(ry, "ry must be Keyword(closest-side)")
        assertEquals(40.0, rx.length.pixels)
        assertEquals("closest-side", ry.keyword)
    }

    /**
     * (6) Wire-format round trip: a Circle with a Keyword radius
     * serializes to `{"type":"circle", "r":"farthest-side"}` and
     * deserializes back to the same shape. A Length radius keeps the
     * legacy IRLength JSON object form unchanged.
     */
    @Test
    fun `keyword shape radius round-trips through serializer as JSON string`() {
        val original = ClipPathProperty.Shape.Circle(
            radius = ClipPathProperty.ShapeRadius.Keyword("farthest-side"),
            position = null
        )
        val encoded = json.encodeToString(ClipPathShapeSerializer, original)
        val obj = json.parseToJsonElement(encoded).jsonObject
        // Discriminator stays "circle".
        assertEquals("circle", obj["type"]!!.jsonPrimitive.content)
        // Wire form for keyword: a JSON string primitive at `r`.
        val r = obj["r"]
        assertTrue(r is JsonPrimitive && r.isString,
            "Keyword radius must serialize as a JSON string primitive, got $r")
        assertEquals("farthest-side", r.content)

        val decoded = json.decodeFromString(ClipPathShapeSerializer, encoded)
            as ClipPathProperty.Shape.Circle
        val radius = decoded.radius as? ClipPathProperty.ShapeRadius.Keyword
        assertNotNull(radius)
        assertEquals("farthest-side", radius.keyword)
    }

    /**
     * Length wire-form regression: a Circle with a Length radius
     * still serializes to an IRLength JSON object (no shape shift from
     * pre-keyword fixtures). Protects the byte-stable BASELINE=1 gate
     * on every existing length-form clip-path fixture.
     */
    @Test
    fun `length shape radius serializes as IRLength object byte-compat`() {
        val original = ClipPathProperty.Shape.Circle(
            radius = ClipPathProperty.ShapeRadius.Length(IRLength.fromPx(40.0)),
            position = null
        )
        val encoded = json.encodeToString(ClipPathShapeSerializer, original)
        val obj = json.parseToJsonElement(encoded).jsonObject
        // Wire form for length: IRLength JSON object (`{"px": N}`) at `r`.
        val r = obj["r"] as? JsonObject
        assertNotNull(r, "Length radius must serialize as a JSON object")
        assertEquals(40.0, r["px"]!!.jsonPrimitive.content.toDouble())

        // And round-trip:
        val decoded = json.decodeFromString(ClipPathShapeSerializer, encoded)
            as ClipPathProperty.Shape.Circle
        val radius = decoded.radius as? ClipPathProperty.ShapeRadius.Length
        assertNotNull(radius)
        assertEquals(40.0, radius.length.pixels)
    }
}

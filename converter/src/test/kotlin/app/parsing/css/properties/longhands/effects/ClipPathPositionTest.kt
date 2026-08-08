package app.parsing.css.properties.longhands.effects

// Regression suite for the wave-37 lane W3 clip-path repairs. Three
// separate defects, all of which silently produced a plausible-looking
// but wrong clip rather than failing loudly:
//
//   1. `parsePosition` understood only bare lengths plus a literal
//      `center`, so every keyword `at` clause in the corpus
//      (`circle(50% at left bottom)`, `circle(50% at right 40px bottom
//      40px)`, `ellipse(40px 60px at right top)`) resolved to null and
//      the shape re-centred. It now delegates to the shared
//      `PositionParser` (the css-values-4 <position> grammar extracted
//      from the wave-36 ObjectPositionPropertyParser rewrite).
//   2. The `rect()` branch of ClipPathShapeSerializer used
//      `value.top?.let { put("t", …) } ?: put("t", "auto")`. Because
//      `JsonObjectBuilder.put` returns the PREVIOUS element for the key —
//      always null on a fresh key — the elvis fired on the success path
//      too and every side was overwritten with "auto".
//   3. `shape()` (css-shapes-2 §4) had no parser branch at all, so the
//      declaration fell out of the IR entirely as an unmapped Generic.
//
// Pinned invariants below, one per behaviour, plus the byte-stability
// guarantee for the position forms that DID parse before this wave.

import app.irmodels.IRLength
import app.irmodels.properties.effects.ClipPathProperty
import app.irmodels.properties.effects.ClipPathShapeSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClipPathPositionTest {

    private val json = Json

    /** Parse a bare `<basic-shape>` value down to its Shape. */
    private fun shapeOf(css: String): ClipPathProperty.Shape? {
        val prop = ClipPathPropertyParser.parse(css) ?: return null
        return when (val v = prop.value) {
            is ClipPathProperty.ClipPath.BasicShape -> v.shape
            is ClipPathProperty.ClipPath.GeometryBoxShape -> v.shape
            else -> null
        }
    }

    private fun circleOf(css: String) = shapeOf(css) as? ClipPathProperty.Shape.Circle
    private fun ellipseOf(css: String) = shapeOf(css) as? ClipPathProperty.Shape.Ellipse

    /** Assert one axis: value + unit + edge anchor. */
    private fun assertAxis(
        expectedValue: Double,
        expectedUnit: IRLength.LengthUnit,
        expectedEdge: String?,
        actualLength: IRLength,
        actualEdge: String?,
        label: String,
    ) {
        assertEquals(expectedValue, actualLength.originalValue, 1e-9, "$label value")
        assertEquals(expectedUnit, actualLength.originalUnit, "$label unit")
        assertEquals(expectedEdge, actualEdge, "$label edge")
    }

    // ── 1. the <position> grammar ────────────────────────────────────────

    @Test
    fun `two keywords resolve per axis, not positionally`() {
        // `left bottom`: x is the horizontal keyword, y the vertical one.
        // Normalised to the default origin — left is 0% from the left
        // edge, bottom is 100% from the top edge — so no edge anchor is
        // needed and consumers that ignore the edge field stay correct.
        val pos = assertNotNull(circleOf("circle(50% at left bottom)")?.position)
        assertAxis(0.0, IRLength.LengthUnit.PERCENT, null, pos.x, pos.xEdge, "x")
        assertAxis(100.0, IRLength.LengthUnit.PERCENT, null, pos.y, pos.yEdge, "y")
    }

    @Test
    fun `the unordered arm assigns by axis, so 'top right' is x=right`() {
        // The third arm of the grammar is an `&&`, i.e. unordered. A
        // positional reading would put `top` on x and swap the whole shape.
        val pos = assertNotNull(ellipseOf("ellipse(closest-side closest-side at top right)")?.position)
        assertAxis(100.0, IRLength.LengthUnit.PERCENT, null, pos.x, pos.xEdge, "x")
        assertAxis(0.0, IRLength.LengthUnit.PERCENT, null, pos.y, pos.yEdge, "y")
    }

    @Test
    fun `right and bottom offsets carry an edge anchor`() {
        // `right 40px` is 40px in from the RIGHT edge — not expressible as
        // a left-origin length without the reference-box width, which the
        // converter never sees.
        val pos = assertNotNull(circleOf("circle(50% at right 40px bottom 40px) border-box")?.position)
        assertAxis(40.0, IRLength.LengthUnit.PX, "right", pos.x, pos.xEdge, "x")
        assertAxis(40.0, IRLength.LengthUnit.PX, "bottom", pos.y, pos.yEdge, "y")
    }

    @Test
    fun `left and top offsets need no edge anchor`() {
        // `top 40px left 60px` — both offsets are already measured from
        // the default origin, and the unordered arm still has to route
        // them to the right axes.
        val pos = assertNotNull(
            ellipseOf("ellipse(farthest-side closest-side at top 40px left 60px) border-box")?.position
        )
        assertAxis(60.0, IRLength.LengthUnit.PX, null, pos.x, pos.xEdge, "x")
        assertAxis(40.0, IRLength.LengthUnit.PX, null, pos.y, pos.yEdge, "y")
    }

    @Test
    fun `a lone keyword centres the other axis`() {
        // Arm 1: `at left` is `left center`, NOT `left left`. The old
        // parser copied a single value onto both axes.
        val pos = assertNotNull(circleOf("circle(50% at left)")?.position)
        assertAxis(0.0, IRLength.LengthUnit.PERCENT, null, pos.x, pos.xEdge, "x")
        assertAxis(50.0, IRLength.LengthUnit.PERCENT, null, pos.y, pos.yEdge, "y")
    }

    @Test
    fun `'at center' keeps the exact pre-wave-37 wire shape`() {
        // The one position form the old parser did understand. Its bytes
        // must not move or every committed baseline shifts with it.
        val pos = assertNotNull(circleOf("circle(50% at center)")?.position)
        assertAxis(50.0, IRLength.LengthUnit.PERCENT, null, pos.x, pos.xEdge, "x")
        assertAxis(50.0, IRLength.LengthUnit.PERCENT, null, pos.y, pos.yEdge, "y")
        val obj = json.encodeToJsonElement(ClipPathShapeSerializer, circleOf("circle(50% at center)")!!).jsonObject
        // Edges default to null and `encodeDefaults` is off on the
        // converter's Json instance, so they must not appear on the wire.
        assertEquals(setOf("type", "r", "pos"), obj.keys)
        assertEquals(setOf("x", "y"), obj["pos"]!!.jsonObject.keys)
    }

    @Test
    fun `two bare percentages still parse as the ordered arm`() {
        val pos = assertNotNull(circleOf("circle(50% at 25% 75%)")?.position)
        assertAxis(25.0, IRLength.LengthUnit.PERCENT, null, pos.x, pos.xEdge, "x")
        assertAxis(75.0, IRLength.LengthUnit.PERCENT, null, pos.y, pos.yEdge, "y")
    }

    @Test
    fun `an invalid position drops the at-clause instead of guessing`() {
        // `top 10px` is not a position: `top` cannot name the horizontal
        // axis in the ordered two-component arm. A browser drops the whole
        // declaration; we drop the clause rather than emit a swapped guess.
        assertNull(circleOf("circle(50% at top 10px)")?.position)
        assertNull(circleOf("circle(50% at left right)")?.position)
    }

    // ── 2. the rect() elvis-on-put bug ───────────────────────────────────

    @Test
    fun `rect sides survive serialization`() {
        val shape = assertNotNull(shapeOf("rect(50px 200px 150px 50px)")) as ClipPathProperty.Shape.Rect
        assertEquals(50.0, shape.top?.originalValue)
        assertEquals(200.0, shape.right?.originalValue)
        val obj = json.encodeToJsonElement(ClipPathShapeSerializer, shape).jsonObject
        // The pre-fix wire was {"t":"auto","r":"auto","b":"auto","l":"auto"}
        // for EVERY rect() in the corpus.
        assertTrue(obj["t"]!!.jsonObject.containsKey("px"), "top must not be 'auto'")
        assertTrue(obj["l"]!!.jsonObject.containsKey("px"), "left must not be 'auto'")
        assertEquals(50.0, obj["t"]!!.jsonObject["px"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun `rect auto sides still serialise as the auto string`() {
        val shape = assertNotNull(shapeOf("rect(auto 200px auto 50px)")) as ClipPathProperty.Shape.Rect
        val obj = json.encodeToJsonElement(ClipPathShapeSerializer, shape).jsonObject
        assertEquals("auto", obj["t"]!!.jsonPrimitive.content)
        assertEquals("auto", obj["b"]!!.jsonPrimitive.content)
        assertTrue(obj["r"]!!.jsonObject.containsKey("px"))
    }

    // ── 3. shape() ───────────────────────────────────────────────────────

    @Test
    fun `shape() is preserved verbatim with whitespace collapsed`() {
        // WPT sources wrap these declarations across lines; the raw
        // newlines survive fixture extraction and must not reach the wire.
        val css = "shape(evenodd from 10px 10px,\n    hline by 80px, vline by 80%,\n    close)"
        val shape = assertNotNull(shapeOf(css)) as ClipPathProperty.Shape.ShapeFunction
        assertEquals(
            "shape(evenodd from 10px 10px, hline by 80px, vline by 80%, close)",
            shape.text,
        )
        val obj = json.encodeToJsonElement(ClipPathShapeSerializer, shape).jsonObject
        assertEquals("shape", obj["type"]!!.jsonPrimitive.content)
        assertEquals(shape.text, obj["fn"]!!.jsonPrimitive.content)
    }

    @Test
    fun `shape() keeps a trailing geometry box`() {
        val prop = assertNotNull(
            ClipPathPropertyParser.parse("shape(from center left, curve to center right with center top / center top, close) content-box")
        )
        val combined = prop.value as ClipPathProperty.ClipPath.GeometryBoxShape
        assertEquals("content-box", combined.box)
        assertTrue(combined.shape is ClipPathProperty.Shape.ShapeFunction)
    }

    // ── 4. path() fill-rule ──────────────────────────────────────────────

    @Test
    fun `path() splits its optional fill-rule off the path data`() {
        // Pre-fix, `d` came out as "nonzero, 'M0,0 L100,0 L0,100 L0,0'" —
        // fill rule and inner quotes baked into the path string.
        val shape = assertNotNull(shapeOf("path(nonzero, 'M0,0 L100,0  L0,100  L0,0')"))
            as ClipPathProperty.Shape.Path
        assertEquals("nonzero", shape.fillRule)
        assertEquals("M0,0 L100,0  L0,100  L0,0", shape.d)
    }

    @Test
    fun `path() without a fill-rule keeps its exact pre-fix wire shape`() {
        val shape = assertNotNull(shapeOf("path('M 0 0 L 10 10 Z')")) as ClipPathProperty.Shape.Path
        assertNull(shape.fillRule)
        assertEquals("M 0 0 L 10 10 Z", shape.d)
        val obj = json.encodeToJsonElement(ClipPathShapeSerializer, shape).jsonObject
        // `rule` must not appear — otherwise every existing path() fixture
        // shifts bytes.
        assertEquals(setOf("type", "d"), obj.keys)
    }

    @Test
    fun `a comma inside unquoted path data is not mistaken for a fill-rule`() {
        // Only `nonzero` / `evenodd` in the first slot are a fill rule; any
        // other head text is left attached so the quote strip can decide.
        val shape = assertNotNull(shapeOf("path('M0,0 L10,10')")) as ClipPathProperty.Shape.Path
        assertNull(shape.fillRule)
        assertEquals("M0,0 L10,10", shape.d)
    }

    @Test
    fun `an empty shape() is rejected`() {
        // Emitting an empty clip would hide the element entirely — worse
        // than dropping the declaration, which is what a browser does.
        assertNull(shapeOf("shape()"))
    }
}

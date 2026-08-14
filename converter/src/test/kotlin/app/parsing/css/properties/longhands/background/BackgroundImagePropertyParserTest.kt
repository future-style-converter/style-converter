package app.parsing.css.properties.longhands.background

// Wave-21 IMAGES lane pins — parser-side fixes for the gradient/image
// family, each pinned against LIVE wire artifacts or the WPT test-source
// declarations they recover:
//   A-RC4 — double-position color stops + bare `0` positions
//           (tools/wpt/css/css-images/conic-gradient-angle-negative.html)
//   A-RC8 — <length> gradient centers (`at 100px 50px`, `at 1lh 50px`)
//   A-RC2 — cross-fade() modern + legacy syntax (css-images-4 §2.6.2;
//           tools/wpt/css/css-images/cross-fade-target-alpha.html and
//           cross-fade-premultiplied-alpha.html declarations)
// Wire-shape pins use the same kotlinx Json the converter emits with, so
// a serializer regression fails here before it reaches the runtimes.

import app.irmodels.IRLengthPercentage
import app.irmodels.IRLength
import app.irmodels.properties.background.BackgroundImageProperty
import app.irmodels.properties.background.BackgroundImageSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackgroundImagePropertyParserTest {

    private val json = Json

    // Parse one background-image value and return the single layer.
    private fun layer(css: String): BackgroundImageProperty.BackgroundImage {
        val prop = BackgroundImagePropertyParser.parse(css) as BackgroundImageProperty
        assertEquals(1, prop.images.size, "expected a single layer for: $css")
        return prop.images[0]
    }

    // ---- A-RC4: double-position stops --------------------------------

    @Test
    fun `double-position stop expands into two stops sharing the color`() {
        // css-images-4 §3.4.3: `red 25% 50%` ≡ `red 25%, red 50%`.
        val g = layer("linear-gradient(red 25% 50%, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals(3, g.colorStops.size)
        assertEquals(25.0, g.colorStops[0].position!!.value)
        assertEquals(50.0, g.colorStops[1].position!!.value)
        // Both expanded stops carry the SAME color.
        assertEquals(g.colorStops[0].color, g.colorStops[1].color)
        assertNull(g.colorStops[2].position)
    }

    @Test
    fun `wpt conic-gradient-angle-negative declaration yields eight hard stops`() {
        // EXACT declaration from the WPT test source (the 0.944 failure on
        // all three platforms): four double-position stops + bare `0`.
        val g = layer("conic-gradient(from -90deg, blue 0 25%, black 25% 50%, red 50% 75%, green 75% 100%)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
        assertEquals(-90.0, g.angle!!.degrees)
        // 4 double-position segments → 8 stops.
        assertEquals(8, g.colorStops.size)
        // Bare `0` parses as 0% (css-values-4 §5.1 unitless zero length) —
        // PercentageParser alone rejects it, which used to null the stop.
        assertEquals(0.0, g.colorStops[0].position!!.value)
        assertEquals(25.0, g.colorStops[1].position!!.value)
        // Hard-stop boundaries: black spans 25→50, red 50→75, green 75→100.
        assertEquals(listOf(25.0, 50.0, 50.0, 75.0, 75.0, 100.0),
            g.colorStops.drop(2).map { it.position!!.value })
    }

    @Test
    fun `single-position stops keep their pre-fix shape`() {
        // Regression guard: the expansion must not disturb 1-position stops.
        val g = layer("linear-gradient(90deg, red 0%, blue 100%)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals(2, g.colorStops.size)
        assertEquals(0.0, g.colorStops[0].position!!.value)
        assertEquals(100.0, g.colorStops[1].position!!.value)
    }

    // ---- A-RC8: length gradient centers ------------------------------

    @Test
    fun `px gradient center parses as absolute lengths`() {
        val g = layer("radial-gradient(at 100px 50px, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.RadialGradient>(g)
        val x = g.position!!.x
        assertIs<IRLengthPercentage.Length>(x)
        assertEquals(100.0, x.length.pixels)
        val y = g.position!!.y
        assertIs<IRLengthPercentage.Length>(y)
        assertEquals(50.0, y.length.pixels)
    }

    @Test
    fun `lh gradient center stays typed runtime-dependent`() {
        // conic-gradient-line-height-relative-units WPT family: `1lh` has
        // no static px value — pixels=null marks it runtime-dependent per
        // the IR convention; engines resolve it where font metrics live.
        val g = layer("conic-gradient(at 1lh 50px, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
        val x = g.position!!.x
        assertIs<IRLengthPercentage.Length>(x)
        assertNull(x.length.pixels)
        assertEquals(IRLength.LengthUnit.LH, x.length.originalUnit)
        assertEquals(1.0, x.length.originalValue)
    }

    @Test
    fun `percent and keyword centers keep the legacy raw-number wire shape`() {
        // Byte-shape pin against the live wave21-gate artifact
        // wpt__css-images__conic-gradient-center.json: `at 25% 25%` must
        // stay `"pos":{"x":25,"y":25}` (raw numbers, no object wrapper).
        val g = layer("conic-gradient(at 25% 25%, red, green)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
        val wire = json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        val pos = wire["pos"]!!.jsonObject
        // Raw JSON numbers — the IRLengthPercentage percentage branch.
        assertEquals(25.0, pos["x"]!!.jsonPrimitive.double)
        assertEquals(25.0, pos["y"]!!.jsonPrimitive.double)
    }

    @Test
    fun `px center wire shape is the IRLength object form`() {
        val g = layer("radial-gradient(at 100px 50px, red, blue)")
        val wire = json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        val pos = wire["pos"]!!.jsonObject
        // Length axes ride {"px": N} — dispatchable from the number form.
        assertEquals(100.0, pos["x"]!!.jsonObject["px"]!!.jsonPrimitive.double)
        assertEquals(50.0, pos["y"]!!.jsonObject["px"]!!.jsonPrimitive.double)
    }

    @Test
    fun `mixed keyword-length center resolves the keyword axis`() {
        // `at left 10px` — keyword x + length y.
        val g = layer("radial-gradient(at left 10px, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.RadialGradient>(g)
        val x = g.position!!.x
        assertIs<IRLengthPercentage.Percentage>(x)
        assertEquals(0.0, x.percentage.value)
        assertIs<IRLengthPercentage.Length>(g.position!!.y)
    }

    // ---- A-RC2: cross-fade() -----------------------------------------

    @Test
    fun `wpt target-alpha six-arg cross-fade parses with authored weights`() {
        // EXACT declaration from cross-fade-target-alpha.html (whitespace
        // collapsed): six 10% gradients — total 60% coverage.
        val g = layer("cross-fade(" + (1..6).joinToString(", ") {
            "10% linear-gradient(#e66465, #9198e5)"
        } + ")")
        assertIs<BackgroundImageProperty.BackgroundImage.CrossFade>(g)
        assertEquals(6, g.args.size)
        assertTrue(g.args.all { it.weight!!.value == 10.0 })
        assertTrue(g.args.all { it.image is BackgroundImageProperty.BackgroundImage.LinearGradient })
        assertEquals(false, g.legacy)
    }

    @Test
    fun `wpt premultiplied-alpha color arguments parse as color layers`() {
        // EXACT declaration from cross-fade-premultiplied-alpha.html —
        // `color(srgb …)` args contain SPACES; tokenization must keep the
        // function as one token (paren-aware split).
        val g = layer("cross-fade(color(srgb 1.0 0.0 0.0 / 0.01), color(srgb 0.0 1.0 0.0 / 1.0))")
        assertIs<BackgroundImageProperty.BackgroundImage.CrossFade>(g)
        assertEquals(2, g.args.size)
        // No authored weights — runtimes normalize to 50/50 (§2.6.2).
        assertTrue(g.args.all { it.weight == null })
        val c0 = g.args[0].image
        assertIs<BackgroundImageProperty.BackgroundImage.ColorLayer>(c0)
        // color(srgb 1 0 0 / 0.01) → r=1 with alpha 0.01.
        assertEquals(1.0, c0.color.srgb!!.r, 1e-9)
        assertEquals(0.01, c0.color.srgb!!.a, 1e-9)
    }

    @Test
    fun `legacy two-arg cross-fade resolves both weights and flags legacy`() {
        // css-images-4 §2.6.2 older syntax: trailing percentage applies to
        // the SECOND image; the first receives the remainder.
        val g = layer("cross-fade(url(a.png), url(b.png), 25%)")
        assertIs<BackgroundImageProperty.BackgroundImage.CrossFade>(g)
        assertTrue(g.legacy)
        assertEquals(75.0, g.args[0].weight!!.value)
        assertEquals(25.0, g.args[1].weight!!.value)
        assertTrue(g.args.all { it.image is BackgroundImageProperty.BackgroundImage.Url })
    }

    @Test
    fun `cross-fade wire shape carries type args weights and legacy flag`() {
        val g = layer("cross-fade(url(a.png), url(b.png), 25%)")
        val wire = json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        assertEquals("cross-fade", wire["type"]!!.jsonPrimitive.content)
        val args = wire["args"]!!.jsonArray
        assertEquals(2, args.size)
        assertEquals(75.0, args[0].jsonObject["weight"]!!.jsonPrimitive.double)
        assertEquals("a.png", args[0].jsonObject["image"]!!.jsonPrimitive.content)
        assertEquals(true, wire["legacy"]!!.jsonPrimitive.content.toBoolean())
    }

    @Test
    fun `modern cross-fade omits the legacy key on the wire`() {
        val g = layer("cross-fade(10% url(a.png), url(b.png))")
        val wire = json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        // Modern syntax: minimal wire — no legacy marker.
        assertNull(wire["legacy"])
        // First arg authored 10%, second omitted (no weight key at all).
        assertEquals(10.0, wire["args"]!!.jsonArray[0].jsonObject["weight"]!!.jsonPrimitive.double)
        assertNull(wire["args"]!!.jsonArray[1].jsonObject["weight"])
    }

    @Test
    fun `cross-fade round-trips through the serializer`() {
        val g = layer("cross-fade(10% linear-gradient(red, blue), green)")
        val wire = json.encodeToJsonElement(BackgroundImageSerializer, g)
        val back = json.decodeFromJsonElement(BackgroundImageSerializer, wire)
        // Full IR→JSON→IR loop must be lossless for the new union member.
        assertEquals(g, back)
    }

    @Test
    fun `radial and conic gradients round-trip through the serializer`() {
        // The pre-split deserializer decoded these to None — pin the fix.
        for (css in listOf(
            "radial-gradient(circle closest-side at 100px 50px, red 0 25%, blue)",
            "conic-gradient(from -90deg at 25% 75%, red, green 50%)")) {
            val g = layer(css)
            val back = json.decodeFromJsonElement(
                BackgroundImageSerializer, json.encodeToJsonElement(BackgroundImageSerializer, g))
            assertEquals(g, back, "round-trip mismatch for: $css")
        }
    }

    // ---- interpolation-method prefix (skeptic follow-up) -------------
    // css-images-4 lets `in <colorspace> [<hue> hue]?` ride beside the
    // angle/direction (`||`); before the fix the whole prefix segment was
    // silently dropped and WPT srgb-gradient et al lost their `to right`.

    @Test
    fun `direction survives a trailing interpolation method`() {
        // EXACT prefix from WPT css-images srgb-gradient.html.
        val g = layer("linear-gradient(to right in srgb, rgb(255 0 0), rgb(0 255 0))")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals(90.0, g.angle!!.degrees)
        assertEquals(2, g.colorStops.size)
    }

    @Test
    fun `direction survives a hue interpolation method`() {
        // EXACT prefix from WPT gradient-hue-direction.html.
        val g = layer("linear-gradient(to right in hsl increasing hue, red, orange)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals(90.0, g.angle!!.degrees)
        assertEquals(2, g.colorStops.size)
    }

    @Test
    fun `method-only prefix is consumed without inventing a stop or angle`() {
        // `in oklab` alone is a valid prefix (angle defaults per spec).
        val g = layer("linear-gradient(in oklab, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertNull(g.angle)
        assertEquals(2, g.colorStops.size)
    }

    @Test
    fun `conic from-angle survives an interpolation method`() {
        val g = layer("conic-gradient(from 45deg in oklch, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
        assertEquals(45.0, g.angle!!.degrees)
        assertEquals(2, g.colorStops.size)
    }

    @Test
    fun `color-mix stop is not corrupted by the method stripper`() {
        // The `in srgb` INSIDE color-mix() sits inside parens — the paren-
        // aware tokenizer must not treat it as a gradient-prefix method.
        // ColorParser handles color-mix() as a dynamic color (srgb=null),
        // so BOTH stops must survive with no angle invented.
        val g = layer("linear-gradient(color-mix(in srgb, red, blue), green)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertNull(g.angle)
        // Both stops survive — the color-mix stop as a dynamic color.
        assertEquals(2, g.colorStops.size)
    }

    // ---- interpolation method CARRIED, not just stripped (wave-37 W2) --
    // Peeling the method off saved the angle beside it; DROPPING it made
    // every polar-space gradient render an sRGB ramp on the one platform
    // that can interpolate correctly. The method now rides the optional
    // `interp` key — absent whenever the author wrote none, so the frozen
    // wire for a plain gradient does not move.

    @Test
    fun `hue interpolation method is carried on the linear gradient`() {
        val g = layer("linear-gradient(to right in hsl increasing hue, red, orange)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals("in hsl increasing hue", g.interp)
        assertEquals(90.0, g.angle!!.degrees)          // direction still survives
    }

    @Test
    fun `space-only interpolation method is carried`() {
        val g = layer("linear-gradient(in oklab, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals("in oklab", g.interp)
    }

    @Test
    fun `radial and conic carry their interpolation methods too`() {
        val r = layer("radial-gradient(circle in lch longer hue, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.RadialGradient>(r)
        assertEquals("in lch longer hue", r.interp)
        val c = layer("conic-gradient(from 45deg in oklch, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(c)
        assertEquals("in oklch", c.interp)
    }

    @Test
    fun `a gradient without a method carries a null interp and no wire key`() {
        val g = layer("linear-gradient(to right, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertNull(g.interp)
        // Wire pin: the key must be ABSENT, not null — that is what makes
        // this additive rather than a byte-shape change.
        val wire = Json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        assertTrue("interp" !in wire)
    }

    @Test
    fun `interp round-trips through the wire`() {
        val g = layer("linear-gradient(to right in hsl longer hue, red, blue)")
        val wire = Json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        assertEquals("in hsl longer hue", wire["interp"]!!.jsonPrimitive.content)
        val back = Json.decodeFromJsonElement(BackgroundImageSerializer, wire)
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(back)
        assertEquals("in hsl longer hue", back.interp)
    }

    @Test
    fun `a color-mix stop does not fabricate an interpolation method`() {
        // The `in srgb` inside color-mix() is inside parens — the same
        // paren-aware tokenizer guards the CARRY path as the strip path.
        val g = layer("linear-gradient(color-mix(in srgb, red, blue), green)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertNull(g.interp)
    }

    // ---- position keyword axes (skeptic follow-up) -------------------
    // css-values-4 §5.4: side keywords are axis-locked, never positional —
    // `at top` is top-CENTER and `at bottom left` ≡ `at left bottom`.
    // Before the fix `at top` resolved to LEFT-center (x=0) and reversed
    // keyword pairs swapped both axes.

    @Test
    fun `single vertical keyword centers the x axis`() {
        val g = layer("conic-gradient(at top, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
        val pos = g.position!!
        assertEquals(50.0, (pos.x as IRLengthPercentage.Percentage).percentage.value)
        assertEquals(0.0, (pos.y as IRLengthPercentage.Percentage).percentage.value)
    }

    @Test
    fun `keyword pair parses order-free`() {
        // Both orders must produce x=0 (left), y=100 (bottom).
        for (css in listOf(
            "conic-gradient(at bottom left, red, blue)",
            "conic-gradient(at left bottom, red, blue)")) {
            val g = layer(css)
            assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
            val pos = g.position!!
            assertEquals(0.0, (pos.x as IRLengthPercentage.Percentage).percentage.value)
            assertEquals(100.0, (pos.y as IRLengthPercentage.Percentage).percentage.value)
        }
    }

    @Test
    fun `same-axis keyword pair is rejected not mis-assigned`() {
        // `at left right` is invalid CSS — the clause must drop (position
        // null → default center), never resolve to a made-up point.
        val g = layer("conic-gradient(at left right, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.ConicGradient>(g)
        assertNull(g.position)
    }

    // ---- cross-fade weight clamping (skeptic follow-up) --------------

    @Test
    fun `legacy cross-fade percent clamps to the 0-100 range`() {
        // §2.6.2 clamping: 150% → 100%, so weights are exactly [0, 100]
        // (pre-fix the wire carried −50/150 and Compose's alpha would
        // reject the out-of-range values).
        val g = layer("cross-fade(red, blue, 150%)")
        assertIs<BackgroundImageProperty.BackgroundImage.CrossFade>(g)
        assertTrue(g.legacy)
        assertEquals(0.0, g.args[0].weight!!.value)
        assertEquals(100.0, g.args[1].weight!!.value)
    }

    @Test
    fun `modern cross-fade negative weight clamps to zero`() {
        val g = layer("cross-fade(red -20%, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.CrossFade>(g)
        assertEquals(0.0, g.args[0].weight!!.value)
        assertNull(g.args[1].weight)
    }

    @Test
    fun `unparseable cross-fade falls back to raw not silence`() {
        // A cross-fade with an unparseable arg must surface as Raw (the
        // author bytes preserved) — never a partially-dropped arg list.
        val prop = BackgroundImagePropertyParser.parse("cross-fade(10% notacolorimage!!)") as BackgroundImageProperty
        assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
    }

    // ---- wave-40 T6: the angle-unit table is now a GATE ---------------
    // The wave-39 A5 deferral. Every declaration below is one of WPT
    // css-values/angle-units-001's four "invalid; <valid form> is valid"
    // comments; the capture browser drops all four
    // (CSS.supports('background-image', …) === false, measured), and so
    // must we — as Raw author bytes, not an invented ramp.

    @Test
    fun `plural turn unit invalidates the whole gradient`() {
        val prop = BackgroundImagePropertyParser.parse("linear-gradient(0.25turns, red, red)") as BackgroundImageProperty
        val raw = assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
        // Author bytes preserved verbatim for the web runtime's passthrough.
        assertEquals("linear-gradient(0.25turns, red, red)", raw.value)
    }

    @Test
    fun `spelled-out angle units invalidate the whole gradient`() {
        for (css in listOf(
            "linear-gradient(90degree, red, red)",
            "linear-gradient(100gradian, red, red)",
            "linear-gradient(1.57radian, red, red)",
        )) {
            val prop = BackgroundImagePropertyParser.parse(css) as BackgroundImageProperty
            assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0], css)
        }
    }

    @Test
    fun `the four real angle units still parse`() {
        // The other half of the gate: angle-units-002..005 (mixed case) must
        // keep resolving, so strictness never costs a valid unit.
        for ((css, deg) in listOf(
            "linear-gradient(90DeG, green, green)" to 90.0,
            "linear-gradient(100gRaD, green, green)" to 90.0,
            "linear-gradient(1.57rAd, green, green)" to 89.954,
            "linear-gradient(0.25tUrN, green, green)" to 90.0,
        )) {
            val g = layer(css)
            assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g, css)
            assertEquals(deg, Math.round(g.angle!!.degrees * 1000.0) / 1000.0, css)
        }
    }

    @Test
    fun `unresolvable direction keyword invalidates the whole gradient`() {
        // `to bottom left top` names two vertical sides — no <side-or-corner>
        // production matches, so the function is invalid (it used to render
        // as a default top-to-bottom ramp of the remaining stops).
        val prop = BackgroundImagePropertyParser.parse("linear-gradient(to bottom left top, red, blue)") as BackgroundImageProperty
        assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
    }

    @Test
    fun `bad angle riding beside a valid interpolation method invalidates too`() {
        // The method is recognised and peeled, but the leftover is neither an
        // angle nor a direction — §3.1's `||` combinator allows nothing else.
        val prop = BackgroundImagePropertyParser.parse("linear-gradient(0.25turns in srgb, red, blue)") as BackgroundImageProperty
        assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
    }

    @Test
    fun `conic from-angle with an invalid unit invalidates the whole gradient`() {
        val prop = BackgroundImagePropertyParser.parse("conic-gradient(from 0.25turns, red, blue)") as BackgroundImageProperty
        assertIs<BackgroundImageProperty.BackgroundImage.Raw>(prop.images[0])
    }

    @Test
    fun `strictness does not touch colour-first stop segments`() {
        // The guard must reject only `to …` and leading <dimension>s. A
        // colour-function first stop — including one carrying its own
        // top-level-looking `in` inside parens — keeps its old path.
        val g = layer("linear-gradient(color-mix(in srgb, red, blue) 10%, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertNull(g.angle)
        val legacyZero = layer("linear-gradient(0, red, blue)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(legacyZero)
        assertEquals(0.0, legacyZero.angle!!.degrees)
    }

    // ---- wave-40 T6: <length> color-stop positions --------------------

    @Test
    fun `wpt gradient-border-box declaration keeps its 30px repeat period`() {
        // EXACT declaration from tools/wpt/css/css-images/gradient-border-box
        // .html — the 30px stop is the REPEAT PERIOD, and dropping it painted
        // one full-box ramp instead of stripes (web 0.6458).
        val g = layer("repeating-linear-gradient(to bottom right, white, black, white 30px)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertTrue(g.repeating)
        assertEquals(3, g.colorStops.size)
        // The percentage arm stays empty — the length rides its own key.
        assertNull(g.colorStops[2].position)
        assertEquals(30.0, g.colorStops[2].positionLength!!.pixels)
    }

    @Test
    fun `relative-unit stop position rides typed with null pixels`() {
        // Same "null means runtime-dependent" convention the gradient CENTRE
        // uses: the engines resolve em/lh where the font metrics live.
        val g = layer("linear-gradient(red, blue 2em)")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertNull(g.colorStops[1].positionLength!!.pixels)
        assertEquals(2.0, g.colorStops[1].positionLength!!.originalValue)
        assertEquals(IRLength.LengthUnit.EM, g.colorStops[1].positionLength!!.originalUnit)
    }

    @Test
    fun `percentage stops keep byte-identical wire bytes`() {
        // The whole point of making positionLength an ADDITIVE key: a stop
        // that carries a percentage must not gain one byte. `encodeDefaults`
        // is off on the converter's Json instance, so the key is absent.
        val g = layer("linear-gradient(red 25%, blue)")
        val el = json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject
        val stops = el["stops"]!!.jsonArray
        assertEquals(25.0, stops[0].jsonObject["position"]!!.jsonPrimitive.double)
        assertTrue(!stops[0].jsonObject.containsKey("positionLength"))
        assertTrue(!stops[1].jsonObject.containsKey("positionLength"))
    }

    @Test
    fun `length stop serializes as the additive positionLength key`() {
        val g = layer("linear-gradient(red, blue 30px)")
        val stops = json.encodeToJsonElement(BackgroundImageSerializer, g).jsonObject["stops"]!!.jsonArray
        val second = stops[1].jsonObject
        // The frozen `position` key is still present and still null.
        assertTrue(second.containsKey("position"))
        assertEquals(30.0, second["positionLength"]!!.jsonObject["px"]!!.jsonPrimitive.double)
    }

    // ---- wave-40 T6: display-p3-linear as an interpolation space ------

    @Test
    fun `display-p3-linear is a recognised interpolation method`() {
        // WPT css-images gradient/display-p3-linear-gradient. The space was
        // missing from the table, so the method was not peeled and `to right`
        // died with it — the gradient rendered top-to-bottom in sRGB (0.9762).
        val g = layer("linear-gradient(to right in display-p3-linear, rgb(255, 0, 0), rgb(0, 255, 0))")
        assertIs<BackgroundImageProperty.BackgroundImage.LinearGradient>(g)
        assertEquals(90.0, g.angle!!.degrees)
        assertEquals("in display-p3-linear", g.interp)
    }
}

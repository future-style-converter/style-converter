package app.parsing.css.properties.longhands.effects

// A-RC9 pins — MaskImagePropertyParser now DELEGATES the <image> grammar
// to BackgroundImagePropertyParser (one grammar, two wrappers). The old
// duplicated copy had drifted: no `from <angle>` / `at <pos>` handling on
// conic (the prefix was fed to the color-stop parser and dropped), no
// radial size keywords/positions, no double-position stops. These tests
// pin the delegated behaviors AND the wire-parallel mask shapes.

import app.irmodels.IRLengthPercentage
import app.irmodels.properties.effects.MaskImageProperty
import app.irmodels.properties.effects.MaskImageValue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaskImagePropertyParserTest {

    private val json = Json

    // Parse one mask-image value and return the single layer.
    private fun layer(css: String): MaskImageValue {
        val prop = MaskImagePropertyParser.parse(css)!!
        assertEquals(1, prop.values.size, "expected a single layer for: $css")
        return prop.values[0]
    }

    @Test
    fun `conic from-angle prefix is honoured (old twin dropped it)`() {
        // Pre-delegation: "from 45deg" was parsed as a color stop, failed,
        // and the angle was silently lost — the degraded-twin bug.
        val g = layer("conic-gradient(from 45deg, red, blue)")
        assertIs<MaskImageValue.ConicGradient>(g)
        assertEquals(45.0, g.angle!!.degrees)
        assertEquals(2, g.colorStops.size)
    }

    @Test
    fun `conic at-position prefix is honoured`() {
        val g = layer("conic-gradient(at 25% 75%, red, blue)")
        assertIs<MaskImageValue.ConicGradient>(g)
        val x = g.position!!.x
        assertIs<IRLengthPercentage.Percentage>(x)
        assertEquals(25.0, x.percentage.value)
    }

    @Test
    fun `radial size keyword and length center now survive`() {
        // Pre-delegation the radial branch only understood a leading
        // circle/ellipse keyword — size + position were dropped.
        val g = layer("radial-gradient(circle closest-side at 100px 50px, red, blue)")
        assertIs<MaskImageValue.RadialGradient>(g)
        assertEquals(MaskImageValue.GradientShape.CIRCLE, g.shape)
        assertEquals(MaskImageValue.GradientSize.CLOSEST_SIDE, g.size)
        val x = g.position!!.x
        assertIs<IRLengthPercentage.Length>(x)
        assertEquals(100.0, x.length.pixels)
    }

    @Test
    fun `double-position stops expand in mask gradients too`() {
        // A-RC4 lands for mask automatically via the delegation.
        val g = layer("linear-gradient(black 0 50%, transparent 50%)")
        assertIs<MaskImageValue.LinearGradient>(g)
        assertEquals(3, g.colorStops.size)
        assertEquals(0.0, g.colorStops[0].position!!.value)
        assertEquals(50.0, g.colorStops[1].position!!.value)
    }

    @Test
    fun `mask wire keeps the pos key with raw-number percent axes`() {
        // Wire-parallel twin pin: mask radial "pos" shape must match the
        // background twin byte-for-byte (raw numbers for percents).
        val g = layer("radial-gradient(at 25% 75%, red, blue)")
        val wire = json.encodeToJsonElement(MaskImageValue.serializer(), g).jsonObject
        val pos = wire["pos"]!!.jsonObject
        assertEquals(25.0, pos["x"]!!.jsonPrimitive.double)
        assertEquals(75.0, pos["y"]!!.jsonPrimitive.double)
    }

    @Test
    fun `url and none layers pass through the delegation`() {
        assertIs<MaskImageValue.Image>(layer("url(mask.svg)"))
        assertIs<MaskImageValue.None>(layer("none"))
        // Multi-layer split still works through the shared splitter.
        val multi = MaskImagePropertyParser.parse("url(a.svg), linear-gradient(black, transparent)")!!
        assertEquals(2, multi.values.size)
    }

    @Test
    fun `url payload bytes stay case-preserved`() {
        // The case-preservation contract must survive the delegation
        // (background's parseImage extracts url() from ORIGINAL bytes).
        val g = layer("url(Data/CaseSensitive.PNG)")
        assertIs<MaskImageValue.Image>(g)
        assertEquals("Data/CaseSensitive.PNG", g.url.url)
    }

    @Test
    fun `unmappable layer fails the whole property loudly`() {
        // cross-fade() is not modelled in the mask union — the property
        // must FAIL (null → recorded unparsed) rather than emit a partial
        // layer list (the no-silent-fallthrough rule).
        assertNull(MaskImagePropertyParser.parse("cross-fade(url(a.png), url(b.png), 25%)"))
        // Garbage still fails like before the delegation.
        assertNull(MaskImagePropertyParser.parse("not-an-image(1234)"))
    }

    @Test
    fun `stops map structurally between the twin unions`() {
        val g = layer("linear-gradient(45deg, red 10%, blue 90%)")
        assertIs<MaskImageValue.LinearGradient>(g)
        assertEquals(45.0, g.angle!!.degrees)
        assertTrue(g.colorStops.all { it.color.srgb != null })
        assertEquals(listOf(10.0, 90.0), g.colorStops.map { it.position!!.value })
    }
}

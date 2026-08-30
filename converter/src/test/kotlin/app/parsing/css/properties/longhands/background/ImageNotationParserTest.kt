package app.parsing.css.properties.longhands.background

// wave-48 lane W5 — image() notation (css-images-4 §2.1 + the legacy
// css-images-3 multi-src grammar). Every payload below is the VERBATIM
// declaration of one WPT css-image-fallbacks-and-annotations test; before
// this parser existed all five fell to Raw and every platform painted the
// forbidden red background-color (wave48-cal css-images, all three F).

import app.irmodels.properties.background.BackgroundImageProperty
import app.irmodels.properties.background.BackgroundImageProperty.BackgroundImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ImageNotationParserTest {

    private fun layers(css: String): List<BackgroundImage> =
        (BackgroundImagePropertyParser.parse(css) as BackgroundImageProperty).images

    @Test
    fun `src plus fallback colour — fallbacks-and-annotations 001`() {
        // VERBATIM (via the background shorthand's image slot).
        val layer = layers("""image("green.png", green)""").single()
        val img = assertIs<BackgroundImage.ImageNotation>(layer)
        assertEquals(listOf("green.png"), img.srcs.map { it.url })
        val fallback = assertNotNull(img.color, "the fallback colour must be carried")
        // `green` = rgb(0,128,0): srgb g-channel 128/255.
        val srgb = assertNotNull(fallback.srgb)
        assertEquals(0.0, srgb.r)
        assertEquals(128.0 / 255.0, srgb.g)
    }

    @Test
    fun `single src without colour — 002`() {
        val layer = layers("""image("support/1x1-green.png")""").single()
        val img = assertIs<BackgroundImage.ImageNotation>(layer)
        assertEquals(listOf("support/1x1-green.png"), img.srcs.map { it.url })
        assertNull(img.color)
    }

    @Test
    fun `legacy multi-src candidate list — 003 and 004`() {
        val layer = layers("""image("1x1-green.svg", "support/1x1-green.png","support/1x1-green.gif")""").single()
        val img = assertIs<BackgroundImage.ImageNotation>(layer)
        // Author order is the try-order; nothing is re-sorted or dropped.
        assertEquals(
            listOf("1x1-green.svg", "support/1x1-green.png", "support/1x1-green.gif"),
            img.srcs.map { it.url },
        )
    }

    @Test
    fun `colour-only image is a solid-colour image — 005 layer 1`() {
        // VERBATIM two-layer declaration of 005: colour image OVER a url.
        val ls = layers("""image(rgba(0,0,255,0.5)), url("support/1x1-green.png")""")
        assertEquals(2, ls.size)
        val img = assertIs<BackgroundImage.ImageNotation>(ls[0])
        assertEquals(emptyList(), img.srcs)
        val srgb = assertNotNull(assertNotNull(img.color).srgb)
        assertEquals(0.5, srgb.a)
        assertEquals(1.0, srgb.b)
        assertIs<BackgroundImage.Url>(ls[1])
    }

    @Test
    fun `outside the modelled grammar falls back to Raw, bytes intact`() {
        // <image-tags> prefix, colour before a src, and unresolvable colours
        // are refused — the Raw route keeps pure author bytes (file banner).
        for (bad in listOf(
            """image(ltr "a.png")""",          // image-tags not modelled
            """image(green, "a.png")""",       // colour must be LAST (§2.1 order)
            """image(var(--c))""",             // unresolvable fallback colour
        )) {
            val raw = assertIs<BackgroundImage.Raw>(layers(bad).single(), "expected Raw for $bad")
            assertEquals(bad, raw.value)
        }
    }

    @Test
    fun `wire shape is additive — type image, srcs list, optional colour`() {
        // Pin the serialized bytes the runtimes read (BackgroundImageSerializer).
        val prop = BackgroundImagePropertyParser.parse("""image("green.png", green)""")!!
        val el = Json.encodeToJsonElement(
            BackgroundImageProperty.serializer(), prop as BackgroundImageProperty)
        val layer = el.jsonObject["images"]!!.jsonArray[0].jsonObject
        assertEquals("image", layer["type"]!!.jsonPrimitive.content)
        assertEquals("green.png", layer["srcs"]!!.jsonArray[0].jsonPrimitive.content)
        assertNotNull(layer["color"], "fallback colour key must be present when authored")
        // Round-trip: the deserializer restores the same typed layer.
        val back = Json.decodeFromJsonElement(BackgroundImageProperty.serializer(), el)
        assertEquals(prop, back)
    }
}

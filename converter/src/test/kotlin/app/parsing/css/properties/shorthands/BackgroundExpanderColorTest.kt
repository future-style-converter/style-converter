package app.parsing.css.properties.shorthands

// A-RC5 regression pin. BackgroundExpander used to carry a PRIVATE 26-name
// colour list, so `background: skyblue` (and 120-odd other perfectly legal
// named colours) expanded to `background-image: skyblue` — an invalid
// declaration that paints nothing. The membership test now routes through
// ColorConversion's complete named-colour table, the same table ColorParser
// uses, so named / hex / functional notations all agree.

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackgroundExpanderColorTest {

    @Test
    fun `named colours outside the old 26-name set expand to background-color`() {
        // Every one of these was previously misrouted to background-image.
        // `skyblue` is the exact value the wave23 css-gaps IR shows as
        // BackgroundImage [{raw:"skyblue"}] on flex-gap-decorations-002.
        val names = listOf(
            "skyblue", "rebeccapurple", "gold", "coral", "salmon",
            "darkslategray", "lightgoldenrodyellow", "mediumaquamarine",
            "papayawhip", "peachpuff", "thistle", "seagreen"
        )
        names.forEach { name ->
            val out = BackgroundExpander.expand(name)
            assertEquals(mapOf("background-color" to name), out, "background: $name")
        }
    }

    @Test
    fun `the original 26 names keep expanding exactly as before`() {
        // No regression on the pre-existing behaviour the visual baselines pin.
        listOf("red", "green", "blue", "black", "white", "transparent",
               "gray", "grey", "navy", "teal", "maroon").forEach { name ->
            assertEquals(mapOf("background-color" to name), BackgroundExpander.expand(name))
        }
    }

    @Test
    fun `CSS Color 4 functional notations expand to background-color`() {
        // The old prefix list only knew rgb/rgba/hsl/hsla, so every modern
        // colour function fell through to background-image.
        listOf(
            "oklch(0.5 0.1 30)", "oklab(0.5 0.1 0.1)", "lab(50% 40 59.5)",
            "lch(50% 60 40)", "hwb(90 10% 10%)", "color(display-p3 1 0 0)",
            "color-mix(in srgb, red, blue)"
        ).forEach { value ->
            assertEquals(mapOf("background-color" to value), BackgroundExpander.expand(value),
                "background: $value")
        }
    }

    @Test
    fun `non-colour idents are still NOT treated as colours`() {
        // The named-colour table returns null for layout keywords, so the
        // other background token roles are unchanged. `none` keeps its own
        // early-return branch (background-image: none).
        assertEquals(mapOf("background-image" to "none"), BackgroundExpander.expand("none"))
        // A repeat keyword alone parses as a repeat, not a colour.
        assertEquals(mapOf("background-repeat" to "no-repeat"), BackgroundExpander.expand("no-repeat"))
        // An attachment keyword alone parses as an attachment.
        assertEquals(mapOf("background-attachment" to "fixed"), BackgroundExpander.expand("fixed"))
    }

    @Test
    fun `named colour still classifies correctly inside a multi-token layer`() {
        // Per-token path (parseSingleLayerBackground) shares isSimpleColor,
        // so the fix has to hold there too — image + repeat + colour.
        val out = BackgroundExpander.expand("url(bg.png) no-repeat skyblue")
        assertEquals("url(bg.png)", out["background-image"])
        assertEquals("no-repeat", out["background-repeat"])
        assertEquals("skyblue", out["background-color"])
    }

    @Test
    fun `var() keeps its background-image precedence`() {
        // wave-6 preservation contract: unresolvable substitutions are held
        // verbatim on the image longhand. isSimpleColor short-circuits on
        // var() so this behaviour is untouched by the colour-table change.
        val out = BackgroundExpander.expand("var(--bg)")
        assertTrue(out.containsKey("background-image"), "var() must stay on background-image")
        assertEquals("var(--bg)", out["background-image"])
    }
}

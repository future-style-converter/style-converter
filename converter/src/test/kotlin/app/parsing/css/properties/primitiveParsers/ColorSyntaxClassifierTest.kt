package app.parsing.css.properties.primitiveParsers

// Direct pin for the shared colour-syntax classifier extracted in A-RC5.
// It is the single membership test shorthand expanders use to decide
// colour-longhand vs image-longhand, so both directions matter: every
// colour notation must be recognised, and no layout keyword may be.

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorSyntaxClassifierTest {

    @Test
    fun `recognises the complete named-colour table, not a subset`() {
        // A spread across the alphabet, all outside the old 26-name list.
        listOf("skyblue", "rebeccapurple", "gold", "coral", "aliceblue",
               "papayawhip", "seagreen", "thistle", "yellowgreen",
               // and the classic names, which must not regress
               "red", "black", "transparent")
            .forEach { assertTrue(ColorSyntaxClassifier.isColorValue(it), it) }
    }

    @Test
    fun `recognises hex, legacy functions and CSS Color 4-5 functions`() {
        listOf("#fff", "#FF00FF", "#ff00ff80",
               "rgb(1,2,3)", "rgba(1,2,3,0.5)", "hsl(0 0% 0%)", "hsla(0,0%,0%,1)",
               "hwb(90 10% 10%)", "lab(50% 40 59.5)", "lch(50% 60 40)",
               "oklab(0.5 0.1 0.1)", "oklch(0.5 0.1 30)",
               "color(display-p3 1 0 0)", "color-mix(in srgb, red, blue)",
               "light-dark(white, black)")
            .forEach { assertTrue(ColorSyntaxClassifier.isColorValue(it), it) }
    }

    @Test
    fun `recognises currentcolor and the CSS-wide keywords`() {
        listOf("currentcolor", "currentColor", "inherit", "initial",
               "unset", "revert", "revert-layer")
            .forEach { assertTrue(ColorSyntaxClassifier.isColorValue(it), it) }
    }

    @Test
    fun `rejects the layout keywords other shorthand token roles claim`() {
        // If any of these flipped to true, BackgroundExpander would start
        // routing repeat/attachment/position/size tokens to background-color.
        listOf("repeat", "repeat-x", "no-repeat", "space", "round",
               "scroll", "fixed", "local",
               "top", "right", "bottom", "left", "center",
               "border-box", "padding-box", "content-box",
               "cover", "contain", "auto", "none",
               "url(x.png)", "linear-gradient(red, blue)", "10px", "50%")
            .forEach { assertFalse(ColorSyntaxClassifier.isColorValue(it), it) }
    }

    @Test
    fun `is case-insensitive and whitespace tolerant like CSS idents`() {
        assertTrue(ColorSyntaxClassifier.isColorValue("  SkyBlue  "))
        assertTrue(ColorSyntaxClassifier.isColorValue("RGB(1,2,3)"))
        assertFalse(ColorSyntaxClassifier.isColorValue("  NoRepeat  "))
    }
}

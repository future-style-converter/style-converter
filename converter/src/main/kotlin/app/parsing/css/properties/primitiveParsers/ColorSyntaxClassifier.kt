package app.parsing.css.properties.primitiveParsers

// The complete CSS named-colour table (all 148 idents + `transparent`)
// lives on ColorConversion; this classifier is the ONE place that answers
// "does this token use colour syntax?" so no caller has to keep a private
// hand-picked subset again.
import app.irmodels.ColorConversion

/**
 * Syntactic classifier for CSS colour values.
 *
 * Motivation (A-RC5): shorthand expanders have to decide, token by token,
 * whether a value belongs on a colour longhand or an image longhand. Before
 * this file, BackgroundExpander carried a PRIVATE 26-name colour list, so
 * `background: skyblue` expanded to `background-image: skyblue` — an invalid
 * declaration that paints nothing on every platform. Routing that test here
 * means named / hex / functional notations agree with [ColorParser], which
 * consults the same [ColorConversion] table.
 *
 * This is deliberately SYNTACTIC, not a parse: it answers "is this colour
 * syntax?" without building an IRColor. [ColorParser.parse] is intentionally
 * permissive (it accepts ANY bare ident as a possibly-named colour with a
 * null sRGB), which would misclassify layout keywords like `repeat`,
 * `cover` or `fixed`, so it cannot be used as the membership test itself.
 */
object ColorSyntaxClassifier {

    // CSS-wide + context keywords that are colour-valid but have no entry in
    // the named-colour table. `transparent` is absent on purpose — it IS in
    // ColorConversion's table, so the named lookup below already covers it.
    private val contextKeywords = setOf(
        "currentcolor", "inherit", "initial", "unset", "revert", "revert-layer"
    )

    // Functional colour notations across CSS Color 4 and 5. `color(` is a
    // prefix of `color-mix(`; both are listed so a startsWith scan matches
    // either without special-casing.
    private val functionPrefixes = setOf(
        "rgb(", "rgba(", "hsl(", "hsla(", "hwb(",
        "lab(", "lch(", "oklab(", "oklch(",
        "color(", "color-mix(", "light-dark("
    )

    /**
     * True when [value] is written in CSS colour syntax.
     *
     * Covers, in order: the complete named-colour table, the CSS-wide /
     * currentcolor keywords, hex notation, and every CSS Color 4/5 function.
     * Returns false for every non-colour ident (`repeat`, `cover`, `fixed`,
     * `none`, …) so callers can keep their own token roles unambiguous.
     */
    fun isColorValue(value: String): Boolean {
        // CSS idents and function names are ASCII case-insensitive.
        val lower = value.trim().lowercase()
        // Named colours — the COMPLETE table, not a hand-picked subset.
        if (ColorConversion.namedColorToSrgb(lower) != null) return true
        // Keywords with no sRGB entry (currentcolor, the CSS-wide set).
        if (lower in contextKeywords) return true
        // Hex notation: #rgb / #rgba / #rrggbb / #rrggbbaa.
        if (lower.startsWith("#")) return true
        // Functional notations.
        return functionPrefixes.any { lower.startsWith(it) }
    }
}

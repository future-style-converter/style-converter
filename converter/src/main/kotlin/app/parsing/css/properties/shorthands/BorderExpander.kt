package app.parsing.css.properties.shorthands

/**
 * Expands the `border` shorthand property into width, style, and color for all sides.
 *
 * Syntax: border: <width> <style> <color>
 * Order doesn't matter, but typically: width style color
 *
 * Examples:
 * - "2px solid #000" → width=2px, style=solid, color=#000 (all sides)
 * - "1px solid" → width=1px, style=solid (all sides)
 * - "solid red" → style=solid, color=red (all sides)
 */
object BorderExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        // First, extract width, style, and color from the value
        val parts = parseBorderValue(value)

        // Then expand to all 4 sides
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { width ->
            result["border-top-width"] = width
            result["border-right-width"] = width
            result["border-bottom-width"] = width
            result["border-left-width"] = width
        }

        parts["style"]?.let { style ->
            result["border-top-style"] = style
            result["border-right-style"] = style
            result["border-bottom-style"] = style
            result["border-left-style"] = style
        }

        parts["color"]?.let { color ->
            result["border-top-color"] = color
            result["border-right-color"] = color
            result["border-bottom-color"] = color
            result["border-left-color"] = color
        }

        return result
    }

    /**
     * Parse border shorthand value into width, style, and color components.
     */
    /**
     * Shared by [BorderTopExpander] and friends, so INTERNAL rather than
     * private.
     *
     * It used to be private, and a file-level EXTENSION function of the same
     * name sat at the bottom of this file carrying a second, older copy of
     * the classification logic — added with the comment "make
     * parseBorderValue and tokenizer accessible to side expanders". Because
     * the member was private, every `BorderExpander.parseBorderValue(...)`
     * call from the four side objects resolved to that EXTENSION instead, so
     * `border-top` / `-right` / `-bottom` / `-left` ran the old code while
     * `border` ran this one. Fixing this function alone left four of the five
     * shorthands unfixed, and nothing said so — the two copies had drifted
     * silently.
     */
    internal fun parseBorderValue(value: String): Map<String, String> {
        // Use smart tokenizer that respects parentheses in color functions
        val tokens = tokenizeBorderValue(value.trim())
        val result = mutableMapOf<String, String>()

        for (token in tokens) {
            val lower = token.lowercase()
            // Function name for a `name(...)` token; "" when there is no paren.
            val fn = if (token.contains('(')) lower.substringBefore('(') else ""
            when {
                // <line-style> first: `none` and `hidden` are styles, and
                // would otherwise be swallowed by the bare-ident color branch.
                lower in BORDER_STYLES -> result["style"] = token

                // <line-width> keywords. These are bare idents, so WITHOUT
                // this branch they matched the `^[a-zA-Z]+$` color test and
                // were filed as border-*-color — the width was lost and a
                // colour was invented. Measured: `border: thin solid red`
                // produced no width at all.
                lower in WIDTH_KEYWORDS -> result["width"] = token

                // <length>. The old pattern was
                //   ^\d+\.?\d*(px|em|rem|%|pt|cm|mm|in|pc|ex|ch|vw|vh|vmin|vmax|fr)?$
                // which dropped, silently, every one of:
                //   .5px      (no leading digit)
                //   2PX       (no IGNORE_CASE)
                //   3Q        (unit absent from the list)
                // A dropped token hits no branch and the `when` had no else,
                // so the declaration simply lost its width.
                LENGTH.matches(token) -> result["width"] = token

                // Math functions resolve to a length here, not a colour.
                // Checked before the colour-function branch so `calc(...)`
                // is not mistaken for one.
                fn in MATH_FUNCTIONS -> result["width"] = token

                token.startsWith("#") -> result["color"] = token

                // css-color-4/5 colour functions. The old test was
                // `startsWith("rgb") || startsWith("hsl")`, so oklch(), lab(),
                // lch(), oklab(), hwb(), color() and color-mix() matched
                // nothing and were dropped — the border silently rendered
                // with no colour at all. Measured on all six.
                fn in COLOR_FUNCTIONS -> result["color"] = token

                // Named colours, `currentcolor`, `transparent`.
                token.matches(BARE_IDENT) -> result["color"] = token

                // No silent fallthrough: an unrecognised token means the
                // shorthand was parsed incompletely, which is exactly how
                // the bugs above stayed invisible.
                else -> println(
                    "[CSS Parser] border shorthand: unrecognised component '$token' in '$value' — ignored"
                )
            }
        }

        return result
    }

    /** css-backgrounds-3 §4.2 <line-style>. */
    private val BORDER_STYLES = setOf(
        "none", "hidden", "dotted", "dashed", "solid", "double",
        "groove", "ridge", "inset", "outset"
    )

    /** css-backgrounds-3 §4.1 <line-width> keywords. */
    private val WIDTH_KEYWORDS = setOf("thin", "medium", "thick")

    /**
     * <length> for a border width. Case-insensitive (CSS units are), accepts a
     * leading dot (`.5px` is a valid <number>), and carries the css-values-4
     * unit set rather than a partial list. `%` and `fr` are kept only because
     * the previous pattern accepted them; neither is valid for border-width,
     * and narrowing that is a separate change from fixing the drops.
     */
    private val LENGTH = (
        """^(?:\d+\.?\d*|\.\d+)""" +
        """(?:px|em|rem|ex|rex|ch|rch|cap|ic|lh|rlh|""" +
        """vw|vh|vi|vb|vmin|vmax|svw|svh|lvw|lvh|dvw|dvh|""" +
        """cm|mm|q|in|pt|pc|%|fr)?$"""
        ).toRegex(RegexOption.IGNORE_CASE)

    /** css-color-4/5 colour functions, by function name. */
    private val COLOR_FUNCTIONS = setOf(
        "rgb", "rgba", "hsl", "hsla", "hwb",
        "lab", "lch", "oklab", "oklch",
        "color", "color-mix", "light-dark", "device-cmyk"
    )

    /** css-values-4 math functions — these resolve to the <length> slot. */
    private val MATH_FUNCTIONS = setOf("calc", "min", "max", "clamp", "round", "mod", "rem", "abs", "sign")

    /** A bare identifier: named colours, `currentcolor`, `transparent`. */
    private val BARE_IDENT = """^[a-zA-Z][a-zA-Z-]*$""".toRegex()

    /**
     * Tokenize border value, respecting parentheses in color functions.
     * Example: "1px solid rgba(255, 255, 255, 0.2)" → ["1px", "solid", "rgba(255, 255, 255, 0.2)"]
     */
    internal fun tokenizeBorderValue(value: String): List<String> {
        val tokens = mutableListOf<String>()
        var current = StringBuilder()
        var parenDepth = 0

        for (char in value) {
            when {
                char == '(' -> {
                    parenDepth++
                    current.append(char)
                }
                char == ')' -> {
                    parenDepth--
                    current.append(char)
                }
                char.isWhitespace() && parenDepth == 0 -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current = StringBuilder()
                    }
                }
                else -> current.append(char)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }
}

/**
 * Expands individual border side shorthands like `border-top`, `border-right`, etc.
 */
object BorderTopExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-top-width"] = it }
        parts["style"]?.let { result["border-top-style"] = it }
        parts["color"]?.let { result["border-top-color"] = it }

        return result
    }
}

object BorderRightExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-right-width"] = it }
        parts["style"]?.let { result["border-right-style"] = it }
        parts["color"]?.let { result["border-right-color"] = it }

        return result
    }
}

object BorderBottomExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-bottom-width"] = it }
        parts["style"]?.let { result["border-bottom-style"] = it }
        parts["color"]?.let { result["border-bottom-color"] = it }

        return result
    }
}

object BorderLeftExpander : ShorthandExpander {
    override fun expand(value: String): Map<String, String> {
        val parts = BorderExpander.parseBorderValue(value)
        val result = mutableMapOf<String, String>()

        parts["width"]?.let { result["border-left-width"] = it }
        parts["style"]?.let { result["border-left-style"] = it }
        parts["color"]?.let { result["border-left-color"] = it }

        return result
    }
}

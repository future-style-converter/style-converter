package app.parsing.css.properties.shorthands

// The ONE syntactic colour classifier (A-RC5): named table + CSS-wide
// keywords + hex + every CSS Color 4/5 function. Wave 50 (lane B6, BACKLOG
// queue 9b): this expander used to test only the `#`/`rgb`/`hsl` prefixes,
// so `text-decoration: underline oklch(…)` silently produced NO colour
// longhand — the identical defect RuleShorthandExpansion fixed in wave 24
// for column-rule/row-rule, and the same classifier closes it here.
import app.parsing.css.properties.primitiveParsers.ColorSyntaxClassifier
// A6#14 — clone consolidation: this expander used to carry private
//   `tokenize` (1 copy), byte-identical to TokenizationUtils.tokenizeByWhitespace
//   — its 1 call site now calls the shared utility instead.
// One tokenizer rule, one body: a per-expander copy is exactly how the
// wave-47 border-shorthand defect (fix one, miss six) became possible.
import app.parsing.css.properties.primitiveParsers.TokenizationUtils

/**
 * Expands the `text-decoration` shorthand property.
 *
 * Syntax (css-text-decor-4 §2.5, the `text-decoration` shorthand):
 * `<'text-decoration-line'> || <'text-decoration-thickness'> ||
 * <'text-decoration-style'> || <'text-decoration-color'>` — order doesn't
 * matter, which is why this is a token-role classifier and not a positional
 * parse.
 *
 * Examples:
 * - "underline" → text-decoration-line: underline
 * - "underline dotted" → text-decoration-line: underline, text-decoration-style: dotted
 * - "underline dotted red" → text-decoration-line: underline, text-decoration-style: dotted, text-decoration-color: red
 * - "underline oklch(0.7 0.15 200)" → …-line: underline, …-color: oklch(0.7 0.15 200)
 * - "green underline auto" → …-color: green, …-line: underline, …-thickness: auto
 */
object TextDecorationExpander : ShorthandExpander {
    // The `<'text-decoration-line'>` keywords, mirroring exactly what
    // TextDecorationLinePropertyParser.parseLine accepts (css-text-decor-4
    // §2.1; `blink` is the CSS 2.1 §16.3.1 legacy value that parser still
    // takes, so the shorthand must not reject it either).
    private val lines = setOf("none", "underline", "overline", "line-through", "blink")

    // The `<'text-decoration-style'>` keywords, mirroring
    // TextDecorationStylePropertyParser (css-text-decor-4 §2.2).
    private val styles = setOf("solid", "double", "dotted", "dashed", "wavy")

    // The KEYWORD half of `<'text-decoration-thickness'>`:
    // `auto | from-font | <length> | <percentage>` (css-text-decor-4 §2.4,
    // mirroring TextDecorationThicknessPropertyParser's two keyword arms).
    //
    // Wave 50 (lane B6, BACKLOG 9b) — this set is the second half of the
    // 9b defect and the half that COSTS INK TODAY. Without it `auto` was a
    // bare alpha ident, so the "any remaining ident is a named colour"
    // fallthrough at the bottom claimed it and OVERWROTE an already-parsed
    // colour: wpt `css-text-decor/text-decoration-shorthands-001`
    // (`text-decoration: green underline auto`) expanded to
    // text-decoration-color:auto — an invalid declaration that every
    // platform resolves back to currentColor. Measured against the frozen
    // ref (tools/wpt/refs/9b5435e5…/…/css-text-decor/
    // text-decoration-shorthands-001.png): the ref paints 48 px of pure
    // rgb(0,128,0) on row 85 and all three wave49-final captures paint
    // ZERO — a black underline where the test asserts a green one, passing
    // only because 48 px of 234 000 sits under the SSIM floor (the wave-49
    // "a score is not a look" class). `from-font` never reached this branch
    // at all: the hyphen fails the bare-ident regex, so it was dropped in
    // silence — the exact fallthrough the else-arm below now makes loud.
    private val thicknessKeywords = setOf("auto", "from-font")

    // `<'text-decoration-thickness'>` written as a bare `<length-percentage>`
    // (css-text-decor-4 §2.4) — the same numeric shapes LengthParser /
    // PercentageParser accept downstream. Hoisted to a val so the regex is
    // compiled once instead of per token.
    private val lengthPercentage = """^\d+\.?\d*(px|em|rem|%|pt)?$""".toRegex()

    // The legacy "any remaining alphabetic ident is a named colour" net.
    // Kept because ColorSyntaxClassifier only knows the CLOSED named table:
    // a vendor/unknown ident still has to reach text-decoration-color so the
    // downstream ColorParser can decide, exactly as before wave 50.
    private val bareIdent = """^[a-zA-Z]+$""".toRegex()

    override fun expand(value: String): Map<String, String> {
        // Paren-depth-aware split, so `rgb(1, 2, 3)` / `oklch(0.7 0.15 200)`
        // stay ONE token each (the shared tokenizer rule).
        val tokens = TokenizationUtils.tokenizeByWhitespace(value.trim())
        val result = mutableMapOf<String, String>()
        val lineValues = mutableListOf<String>()

        for (token in tokens) {
            // CSS idents and function names are ASCII case-insensitive
            // (css-values-4 §4.1), so every membership test reads the folded
            // form while `result` keeps the token VERBATIM for the longhand
            // parsers.
            val lower = token.lowercase()
            when {
                // Line keywords accumulate: `underline overline` is one
                // multi-value longhand (§2.1 `underline || overline || …`).
                lower in lines -> lineValues.add(token)

                // Style keywords — tested BEFORE the colour classifier so no
                // ident in `styles` can ever be read as a named colour.
                lower in styles -> result["text-decoration-style"] = token

                // Thickness keywords — likewise tested before the colour
                // arms, which is the whole point (see `thicknessKeywords`).
                lower in thicknessKeywords -> result["text-decoration-thickness"] = token

                // `<'text-decoration-color'>` (css-text-decor-4 §2.3) in ANY
                // colour syntax — named / CSS-wide / hex / every Color 4-5
                // function — through the shared classifier. A colour token
                // can never match the numeric shape below, so ordering
                // against it is free; what matters is that the classifier's
                // function prefixes (`oklch(`, `lab(`, `color-mix(`, …) are
                // the branch the old `#|rgb|hsl` prefix test could not reach.
                ColorSyntaxClassifier.isColorValue(token) -> result["text-decoration-color"] = token

                // `<'text-decoration-thickness'>` as a bare length/percentage.
                lengthPercentage.matches(token) -> result["text-decoration-thickness"] = token

                // Unknown ident → possibly-named colour (see `bareIdent`).
                bareIdent.matches(token) -> result["text-decoration-color"] = token

                // No silent fallthrough (the campaign rule, and the same
                // else-arm BorderExpander.kt:122 carries): an unrecognised
                // component means this shorthand was expanded INCOMPLETELY,
                // which is precisely how `from-font` and the Color 4/5
                // functions stayed invisible for 49 waves.
                else -> println(
                    "[CSS Parser] text-decoration shorthand: unrecognised component '$token' in '$value' — ignored"
                )
            }
        }

        // `text-decoration-line` is ONE declaration carrying the whole
        // `underline || overline || line-through` list (§2.1), so it is
        // joined after the walk rather than written per token.
        if (lineValues.isNotEmpty()) {
            result["text-decoration-line"] = lineValues.joinToString(" ")
        }

        return result
    }
}

package app.parsing.css.properties.longhands.effects

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.effects.*
import app.parsing.css.properties.primitiveParsers.LengthParser

/**
 * Parser for `clip` property (legacy).
 */
object ClipPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        if (trimmed == "auto") {
            return ClipProperty(ClipValue.Auto)
        }
        if (trimmed.startsWith("rect(") && trimmed.endsWith(")")) {
            val params = trimmed.substring(5, trimmed.length - 1).trim()
            val parts = splitRectArgs(params) ?: return null
            // CSS 2.1 §11.1.2: each side accepts a <length> or the
            // keyword `auto` (which the spec resolves to 0 for top/left
            // and to the element's full extent for bottom/right). We
            // surface `auto` as a null IR field so per-platform
            // appliers can resolve against actual draw bounds — the
            // previous strict parse dropped the whole declaration the
            // moment any axis was `auto` (e.g. the audit fixture
            // `rect(auto, 100px, 200px, 0)` fell through to GenericProperty).
            fun side(s: String) = if (s == "auto") null else LengthParser.parse(s)
            return ClipProperty(ClipValue.Rect(
                top = side(parts[0]),
                right = side(parts[1]),
                bottom = side(parts[2]),
                left = side(parts[3]),
            ))
        }
        return null
    }

    /**
     * Split the four `rect()` arguments of the legacy `clip` property.
     *
     * CSS 2.1 §11.1.2 printed the commas, but the shape has always also
     * admitted the comma-LESS spelling, and the separator style must be
     * used CONSISTENTLY: `rect(50px, 150px, 150px, 50px)` and
     * `rect(50px 150px 150px 50px)` are both valid, while any mix —
     * `rect(50px 150px, 150px, 50px)` — is a syntax error the whole
     * declaration must be dropped for. WPT pins all four cases:
     * clip-rect-comma-001 (all-space, must clip) against
     * clip-rect-comma-002/003/004 (mixed, must NOT clip).
     *
     * The parser used to `split(",")` only, so the all-space form produced
     * one part, tripped the arity guard and fell through to an unmapped
     * Generic — nothing clipped where the test expects a clip. Accepting
     * "comma or whitespace" indiscriminately would fix that one test and
     * break the other three, so the rule is encoded exactly:
     *
     *  - a comma anywhere ⇒ comma-separated; every comma-delimited part
     *    must then be a SINGLE token (an internal space means a mix), and
     *  - otherwise ⇒ whitespace-separated.
     *
     * Either way exactly four components must result. Returns null on any
     * violation so the caller drops the declaration.
     */
    private fun splitRectArgs(params: String): List<String>? {
        val parts = if (params.contains(',')) {
            val byComma = params.split(",").map { it.trim() }
            // A part holding two whitespace-separated tokens is the mixed
            // spelling — reject rather than silently pick one.
            if (byComma.any { it.isEmpty() || it.contains(Regex("""\s""")) }) return null
            byComma
        } else {
            params.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        }
        return if (parts.size == 4) parts else null
    }
}

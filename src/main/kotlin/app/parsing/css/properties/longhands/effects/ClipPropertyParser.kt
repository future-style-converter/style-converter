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
            val parts = params.split(",").map { it.trim() }
            if (parts.size != 4) return null
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
}

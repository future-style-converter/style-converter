package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollbarGutterProperty
import app.irmodels.properties.scrolling.ScrollbarGutter
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `scrollbar-gutter` property.
 */
object ScrollbarGutterPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "auto") {
            return ScrollbarGutterProperty(ScrollbarGutter.Auto)
        }

        if (!trimmed.contains("stable")) return null

        val bothEdges = trimmed.contains("both-edges")

        return ScrollbarGutterProperty(ScrollbarGutter.Stable(bothEdges))
    }
}

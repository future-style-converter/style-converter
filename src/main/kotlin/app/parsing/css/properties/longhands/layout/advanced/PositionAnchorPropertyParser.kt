package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionAnchorProperty
import app.parsing.css.properties.longhands.PropertyParser

object PositionAnchorPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.lowercase() == "auto") {
            return PositionAnchorProperty("auto")
        }
        // Anchor names typically start with --
        return PositionAnchorProperty(trimmed)
    }
}

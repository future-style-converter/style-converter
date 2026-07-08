package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextJustifyProperty
import app.irmodels.properties.typography.TextJustifyValue
import app.parsing.css.properties.longhands.PropertyParser

/**
 * Parser for `text-justify` property.
 *
 * Syntax: auto | none | inter-word | inter-character | distribute
 */
object TextJustifyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val justifyValue = when (trimmed) {
            "auto" -> TextJustifyValue.AUTO
            "none" -> TextJustifyValue.NONE
            "inter-word" -> TextJustifyValue.INTER_WORD
            "inter-character" -> TextJustifyValue.INTER_CHARACTER
            "distribute" -> TextJustifyValue.DISTRIBUTE
            else -> return null
        }

        return TextJustifyProperty(justifyValue)
    }
}

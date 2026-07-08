package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextIndentProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser
import app.parsing.css.properties.primitiveParsers.PercentageParser

/**
 * Parser for `text-indent` property.
 *
 * Syntax: <length-percentage> [ hanging || each-line ]?
 *
 * Note: The IR model currently only supports the basic length/percentage value.
 * The hanging and each-line modifiers are not yet supported in the IR.
 */
object TextIndentPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()

        // Split by whitespace to handle potential modifiers (hanging, each-line)
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        // The first part should be the length/percentage
        val firstPart = parts[0].lowercase()

        // Try parsing as percentage first
        val indent = PercentageParser.parse(firstPart)?.let {
            TextIndentProperty.TextIndent.PercentageValue(it)
        } ?: LengthParser.parse(firstPart)?.let {
            TextIndentProperty.TextIndent.LengthValue(it)
        } ?: return null

        // Note: hanging and each-line modifiers are ignored as they're not in the IR model
        // A future enhancement could add these to the IR model

        return TextIndentProperty(indent)
    }
}

package app.parsing.css.properties.longhands.images

import app.irmodels.IRProperty
import app.irmodels.properties.images.ObjectViewBoxProperty
import app.irmodels.properties.images.ObjectViewBoxValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object ObjectViewBoxPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        if (trimmed == "none") {
            return ObjectViewBoxProperty(ObjectViewBoxValue.None)
        }

        // Parse inset() function
        if (trimmed.startsWith("inset(") && trimmed.endsWith(")")) {
            val inner = trimmed.removePrefix("inset(").removeSuffix(")").trim()
            val parts = inner.split("\\s+".toRegex())

            val lengths = parts.mapNotNull { LengthParser.parse(it) }
            if (lengths.isEmpty()) return null

            val (top, right, bottom, left) = when (lengths.size) {
                1 -> listOf(lengths[0], lengths[0], lengths[0], lengths[0])
                2 -> listOf(lengths[0], lengths[1], lengths[0], lengths[1])
                3 -> listOf(lengths[0], lengths[1], lengths[2], lengths[1])
                4 -> lengths
                else -> return null
            }

            return ObjectViewBoxProperty(ObjectViewBoxValue.Inset(top, right, bottom, left))
        }

        return null
    }
}

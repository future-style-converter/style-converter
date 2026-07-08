package app.parsing.css.properties.longhands.performance

import app.irmodels.IRProperty
import app.irmodels.properties.performance.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

private fun parseContainIntrinsicValue(value: String): ContainIntrinsicValue? {
    val trimmed = value.trim().lowercase()
    return when {
        trimmed == "none" -> ContainIntrinsicValue.None
        trimmed == "auto" -> ContainIntrinsicValue.Auto
        trimmed.startsWith("auto ") -> {
            val lengthPart = trimmed.removePrefix("auto ").trim()
            val length = LengthParser.parse(lengthPart) ?: return null
            ContainIntrinsicValue.AutoLength(length)
        }
        else -> {
            val length = LengthParser.parse(trimmed) ?: return null
            ContainIntrinsicValue.Length(length)
        }
    }
}

object ContainIntrinsicWidthPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseContainIntrinsicValue(value) ?: return null
        return ContainIntrinsicWidthProperty(v)
    }
}

object ContainIntrinsicHeightPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseContainIntrinsicValue(value) ?: return null
        return ContainIntrinsicHeightProperty(v)
    }
}

object ContainIntrinsicBlockSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseContainIntrinsicValue(value) ?: return null
        return ContainIntrinsicBlockSizeProperty(v)
    }
}

object ContainIntrinsicInlineSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = parseContainIntrinsicValue(value) ?: return null
        return ContainIntrinsicInlineSizeProperty(v)
    }
}

object ContainIntrinsicSizePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split("\\s+".toRegex())
        val width = parseContainIntrinsicValue(parts[0]) ?: return null
        val height = if (parts.size > 1) parseContainIntrinsicValue(parts[1]) else null
        return ContainIntrinsicSizeProperty(width, height)
    }
}

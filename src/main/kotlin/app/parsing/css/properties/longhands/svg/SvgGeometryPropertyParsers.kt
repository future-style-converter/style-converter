package app.parsing.css.properties.longhands.svg

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.svg.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

private fun parseSvgLengthValue(value: String): SvgLengthValue {
    val trimmed = value.trim()

    // Handle calc(), var(), and other expressions
    if (trimmed.contains("(")) {
        return SvgLengthValue.Raw(trimmed)
    }

    return if (trimmed.endsWith("%")) {
        val pct = trimmed.removeSuffix("%").toDoubleOrNull()
        if (pct != null) SvgLengthValue.Percentage(IRPercentage(pct))
        else SvgLengthValue.Raw(trimmed)
    } else {
        val length = LengthParser.parse(trimmed)
        if (length != null) SvgLengthValue.Length(length)
        else SvgLengthValue.Raw(trimmed)
    }
}

object CxPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = CxProperty(parseSvgLengthValue(value))
}

object CyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = CyProperty(parseSvgLengthValue(value))
}

object RPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = RProperty(parseSvgLengthValue(value))
}

object RxPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = RxProperty(parseSvgLengthValue(value))
}

object RyPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = RyProperty(parseSvgLengthValue(value))
}

object XPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = XProperty(parseSvgLengthValue(value))
}

object YPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty = YProperty(parseSvgLengthValue(value))
}

object DPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        return DProperty(trimmed)
    }
}

package app.parsing.css.properties.longhands.background

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundPositionInlineProperty
import app.irmodels.properties.background.BackgroundPositionInlineProperty.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BackgroundPositionInlinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val position = when (trimmed) {
            "start" -> PositionInline.Keyword(InlineKeyword.START)
            "center" -> PositionInline.Keyword(InlineKeyword.CENTER)
            "end" -> PositionInline.Keyword(InlineKeyword.END)
            else -> {
                if (trimmed.endsWith("%")) {
                    val p = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                    PositionInline.PercentageValue(IRPercentage(p))
                } else {
                    val length = LengthParser.parse(trimmed) ?: return null
                    PositionInline.LengthValue(length)
                }
            }
        }
        return BackgroundPositionInlineProperty(position)
    }
}

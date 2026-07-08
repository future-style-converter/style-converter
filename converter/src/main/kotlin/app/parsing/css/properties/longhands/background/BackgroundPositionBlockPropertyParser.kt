package app.parsing.css.properties.longhands.background

import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundPositionBlockProperty
import app.irmodels.properties.background.BackgroundPositionBlockProperty.*
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.LengthParser

object BackgroundPositionBlockPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val position = when (trimmed) {
            "start" -> PositionBlock.Keyword(BlockKeyword.START)
            "center" -> PositionBlock.Keyword(BlockKeyword.CENTER)
            "end" -> PositionBlock.Keyword(BlockKeyword.END)
            else -> {
                if (trimmed.endsWith("%")) {
                    val p = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                    PositionBlock.PercentageValue(IRPercentage(p))
                } else {
                    val length = LengthParser.parse(trimmed) ?: return null
                    PositionBlock.LengthValue(length)
                }
            }
        }
        return BackgroundPositionBlockProperty(position)
    }
}

package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.PositionTryOrderProperty
import app.irmodels.properties.layout.advanced.PositionTryOrderValue
import app.parsing.css.properties.longhands.PropertyParser

object PositionTryOrderPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "normal" -> PositionTryOrderValue.NORMAL
            "most-width" -> PositionTryOrderValue.MOST_WIDTH
            "most-height" -> PositionTryOrderValue.MOST_HEIGHT
            "most-block-size" -> PositionTryOrderValue.MOST_BLOCK_SIZE
            "most-inline-size" -> PositionTryOrderValue.MOST_INLINE_SIZE
            else -> return null
        }
        return PositionTryOrderProperty(v)
    }
}

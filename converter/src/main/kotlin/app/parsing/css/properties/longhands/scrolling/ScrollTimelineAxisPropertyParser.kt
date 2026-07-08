package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollTimelineAxis
import app.irmodels.properties.scrolling.ScrollTimelineAxisProperty
import app.parsing.css.properties.longhands.PropertyParser

object ScrollTimelineAxisPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val axis = when (trimmed) {
            "block" -> ScrollTimelineAxis.BLOCK
            "inline" -> ScrollTimelineAxis.INLINE
            "x" -> ScrollTimelineAxis.X
            "y" -> ScrollTimelineAxis.Y
            else -> return null
        }
        return ScrollTimelineAxisProperty(axis)
    }
}

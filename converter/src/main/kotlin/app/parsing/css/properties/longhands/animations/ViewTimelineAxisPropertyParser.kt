package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTimelineAxis
import app.irmodels.properties.animations.ViewTimelineAxisProperty
import app.parsing.css.properties.longhands.PropertyParser

object ViewTimelineAxisPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val axis = when (trimmed) {
            "block" -> ViewTimelineAxis.BLOCK
            "inline" -> ViewTimelineAxis.INLINE
            "x" -> ViewTimelineAxis.X
            "y" -> ViewTimelineAxis.Y
            else -> return null
        }
        return ViewTimelineAxisProperty(axis)
    }
}

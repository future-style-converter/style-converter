package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.ViewTimelineAxis
import app.irmodels.properties.animations.ViewTimelineProperty
import app.parsing.css.properties.longhands.PropertyParser

object ViewTimelinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        var name: String? = null
        var axis: ViewTimelineAxis = ViewTimelineAxis.BLOCK

        for (part in parts) {
            val lowered = part.lowercase()
            when (lowered) {
                "block" -> axis = ViewTimelineAxis.BLOCK
                "inline" -> axis = ViewTimelineAxis.INLINE
                "x" -> axis = ViewTimelineAxis.X
                "y" -> axis = ViewTimelineAxis.Y
                "none" -> name = null
                else -> name = part
            }
        }

        return ViewTimelineProperty(name, axis)
    }
}

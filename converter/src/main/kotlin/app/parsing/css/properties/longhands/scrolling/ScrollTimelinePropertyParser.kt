package app.parsing.css.properties.longhands.scrolling

import app.irmodels.IRProperty
import app.irmodels.properties.scrolling.ScrollTimelineAxis
import app.irmodels.properties.scrolling.ScrollTimelineName
import app.irmodels.properties.scrolling.ScrollTimelineProperty
import app.parsing.css.properties.longhands.PropertyParser

object ScrollTimelinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val parts = value.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        var name = "none"
        var axis = ScrollTimelineAxis.BLOCK

        for (part in parts) {
            val lowered = part.lowercase()
            when (lowered) {
                "block" -> axis = ScrollTimelineAxis.BLOCK
                "inline" -> axis = ScrollTimelineAxis.INLINE
                "x" -> axis = ScrollTimelineAxis.X
                "y" -> axis = ScrollTimelineAxis.Y
                else -> name = part
            }
        }

        return ScrollTimelineProperty(ScrollTimelineName(name), axis)
    }
}

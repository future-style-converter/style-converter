package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.AlignContentProperty
import app.parsing.css.properties.longhands.PropertyParser

object AlignContentPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val alignContent = when (trimmed) {
            "flex-start" -> AlignContentProperty.AlignContent.FLEX_START
            "flex-end" -> AlignContentProperty.AlignContent.FLEX_END
            "center" -> AlignContentProperty.AlignContent.CENTER
            "space-between" -> AlignContentProperty.AlignContent.SPACE_BETWEEN
            "space-around" -> AlignContentProperty.AlignContent.SPACE_AROUND
            "space-evenly" -> AlignContentProperty.AlignContent.SPACE_EVENLY
            "stretch" -> AlignContentProperty.AlignContent.STRETCH
            "start" -> AlignContentProperty.AlignContent.START
            "end" -> AlignContentProperty.AlignContent.END
            "normal" -> AlignContentProperty.AlignContent.NORMAL
            "baseline" -> AlignContentProperty.AlignContent.BASELINE
            else -> return null
        }
        return AlignContentProperty(alignContent)
    }
}

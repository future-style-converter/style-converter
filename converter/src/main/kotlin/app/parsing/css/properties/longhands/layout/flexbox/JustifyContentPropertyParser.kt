package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.JustifyContentProperty
import app.parsing.css.properties.longhands.PropertyParser

object JustifyContentPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val justifyContent = when (trimmed) {
            "flex-start" -> JustifyContentProperty.JustifyContent.FLEX_START
            "flex-end" -> JustifyContentProperty.JustifyContent.FLEX_END
            "center" -> JustifyContentProperty.JustifyContent.CENTER
            "space-between" -> JustifyContentProperty.JustifyContent.SPACE_BETWEEN
            "space-around" -> JustifyContentProperty.JustifyContent.SPACE_AROUND
            "space-evenly" -> JustifyContentProperty.JustifyContent.SPACE_EVENLY
            "start" -> JustifyContentProperty.JustifyContent.START
            "end" -> JustifyContentProperty.JustifyContent.END
            "left" -> JustifyContentProperty.JustifyContent.LEFT
            "right" -> JustifyContentProperty.JustifyContent.RIGHT
            "normal" -> JustifyContentProperty.JustifyContent.NORMAL
            "stretch" -> JustifyContentProperty.JustifyContent.STRETCH
            else -> return null
        }
        return JustifyContentProperty(justifyContent)
    }
}

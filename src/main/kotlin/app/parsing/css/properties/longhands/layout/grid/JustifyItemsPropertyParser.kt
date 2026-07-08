package app.parsing.css.properties.longhands.layout.grid

import app.irmodels.IRProperty
import app.irmodels.properties.layout.grid.JustifyItemsProperty
import app.parsing.css.properties.longhands.PropertyParser
object JustifyItemsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val justifyItems = when (trimmed) {
            "normal" -> JustifyItemsProperty.JustifyItems.NORMAL
            "stretch" -> JustifyItemsProperty.JustifyItems.STRETCH
            "center" -> JustifyItemsProperty.JustifyItems.CENTER
            "start" -> JustifyItemsProperty.JustifyItems.START
            "end" -> JustifyItemsProperty.JustifyItems.END
            "flex-start" -> JustifyItemsProperty.JustifyItems.FLEX_START
            "flex-end" -> JustifyItemsProperty.JustifyItems.FLEX_END
            "self-start" -> JustifyItemsProperty.JustifyItems.SELF_START
            "self-end" -> JustifyItemsProperty.JustifyItems.SELF_END
            "left" -> JustifyItemsProperty.JustifyItems.LEFT
            "right" -> JustifyItemsProperty.JustifyItems.RIGHT
            "baseline" -> JustifyItemsProperty.JustifyItems.BASELINE
            else -> return null
        }
        return JustifyItemsProperty(justifyItems)
    }
}

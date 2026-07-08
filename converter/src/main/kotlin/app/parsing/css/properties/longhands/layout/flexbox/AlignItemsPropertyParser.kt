package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.irmodels.properties.layout.flexbox.AlignItemsProperty
import app.parsing.css.properties.longhands.PropertyParser

object AlignItemsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val alignItems = when (trimmed) {
            "flex-start" -> AlignItemsProperty.AlignItems.FLEX_START
            "flex-end" -> AlignItemsProperty.AlignItems.FLEX_END
            "center" -> AlignItemsProperty.AlignItems.CENTER
            "baseline" -> AlignItemsProperty.AlignItems.BASELINE
            "stretch" -> AlignItemsProperty.AlignItems.STRETCH
            "start" -> AlignItemsProperty.AlignItems.START
            "end" -> AlignItemsProperty.AlignItems.END
            "self-start" -> AlignItemsProperty.AlignItems.SELF_START
            "self-end" -> AlignItemsProperty.AlignItems.SELF_END
            "normal" -> AlignItemsProperty.AlignItems.NORMAL
            else -> return null
        }
        return AlignItemsProperty(alignItems)
    }
}

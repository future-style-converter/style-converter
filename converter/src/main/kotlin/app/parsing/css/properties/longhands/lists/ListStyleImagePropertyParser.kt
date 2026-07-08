package app.parsing.css.properties.longhands.lists

import app.irmodels.IRProperty
import app.irmodels.properties.lists.ListStyleImageProperty
import app.parsing.css.properties.longhands.PropertyParser

object ListStyleImagePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val image: ListStyleImageProperty.ListImage = when {
            trimmed == "none" -> ListStyleImageProperty.ListImage.None()
            trimmed.startsWith("url(") && trimmed.endsWith(")") -> {
                val url = trimmed.removePrefix("url(").removeSuffix(")")
                    .trim().removeSurrounding("\"").removeSurrounding("'")
                ListStyleImageProperty.ListImage.Url(url)
            }
            else -> return null
        }

        return ListStyleImageProperty(image)
    }
}

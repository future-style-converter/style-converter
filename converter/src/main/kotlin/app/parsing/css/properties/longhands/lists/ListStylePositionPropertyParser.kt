package app.parsing.css.properties.longhands.lists

import app.irmodels.IRProperty
import app.irmodels.properties.lists.ListStylePositionProperty
import app.parsing.css.properties.longhands.PropertyParser

object ListStylePositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val position = when (value.trim().lowercase()) {
            "inside" -> ListStylePositionProperty.ListPosition.INSIDE
            "outside" -> ListStylePositionProperty.ListPosition.OUTSIDE
            else -> return null
        }
        return ListStylePositionProperty(position)
    }
}

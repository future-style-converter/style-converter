package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.UserSelectProperty
import app.parsing.css.properties.longhands.PropertyParser

object UserSelectPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val userSelect = when (trimmed) {
            "auto" -> UserSelectProperty.UserSelect.AUTO
            "text" -> UserSelectProperty.UserSelect.TEXT
            "none" -> UserSelectProperty.UserSelect.NONE
            "contain" -> UserSelectProperty.UserSelect.CONTAIN
            "all" -> UserSelectProperty.UserSelect.ALL
            else -> return null
        }
        return UserSelectProperty(userSelect)
    }
}

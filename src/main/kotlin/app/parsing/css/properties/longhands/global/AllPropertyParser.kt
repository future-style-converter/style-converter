package app.parsing.css.properties.longhands.global

import app.irmodels.IRProperty
import app.irmodels.properties.global.AllProperty
import app.irmodels.properties.global.AllValue
import app.parsing.css.properties.longhands.PropertyParser

object AllPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "initial" -> AllValue.INITIAL
            "inherit" -> AllValue.INHERIT
            "unset" -> AllValue.UNSET
            "revert" -> AllValue.REVERT
            "revert-layer" -> AllValue.REVERT_LAYER
            else -> return null
        }
        return AllProperty(v)
    }
}

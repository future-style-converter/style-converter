package app.parsing.css.properties.longhands.layout.advanced

import app.irmodels.IRProperty
import app.irmodels.properties.layout.advanced.AnchorScopeProperty
import app.irmodels.properties.layout.advanced.AnchorScopeValue
import app.parsing.css.properties.longhands.PropertyParser

object AnchorScopePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when (trimmed) {
            "none" -> AnchorScopeValue.NONE
            "all" -> AnchorScopeValue.ALL
            else -> return null
        }
        return AnchorScopeProperty(v)
    }
}

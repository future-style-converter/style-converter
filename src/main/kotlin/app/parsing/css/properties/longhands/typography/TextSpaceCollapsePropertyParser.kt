package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextSpaceCollapseProperty
import app.irmodels.properties.typography.TextSpaceCollapseValue
import app.parsing.css.properties.longhands.PropertyParser

object TextSpaceCollapsePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val collapseValue = when (trimmed) {
            "collapse" -> TextSpaceCollapseValue.COLLAPSE
            "preserve" -> TextSpaceCollapseValue.PRESERVE
            "preserve-breaks" -> TextSpaceCollapseValue.PRESERVE_BREAKS
            "preserve-spaces" -> TextSpaceCollapseValue.PRESERVE_SPACES
            "break-spaces" -> TextSpaceCollapseValue.BREAK_SPACES
            else -> return null
        }
        return TextSpaceCollapseProperty(collapseValue)
    }
}

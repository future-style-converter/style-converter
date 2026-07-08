package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WhiteSpaceCollapseProperty
import app.irmodels.properties.typography.WhiteSpaceCollapseValue
import app.parsing.css.properties.longhands.PropertyParser

object WhiteSpaceCollapsePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val v = when (value.trim().lowercase().replace("-", "_")) {
            "collapse" -> WhiteSpaceCollapseValue.COLLAPSE
            "preserve" -> WhiteSpaceCollapseValue.PRESERVE
            "preserve_breaks" -> WhiteSpaceCollapseValue.PRESERVE_BREAKS
            "preserve_spaces" -> WhiteSpaceCollapseValue.PRESERVE_SPACES
            "break_spaces" -> WhiteSpaceCollapseValue.BREAK_SPACES
            else -> return null
        }
        return WhiteSpaceCollapseProperty(v)
    }
}

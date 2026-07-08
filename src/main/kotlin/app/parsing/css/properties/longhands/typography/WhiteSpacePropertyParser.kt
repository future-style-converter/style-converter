package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.WhiteSpaceProperty
import app.parsing.css.properties.longhands.PropertyParser

object WhiteSpacePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val whiteSpace = when (trimmed) {
            "normal" -> WhiteSpaceProperty.WhiteSpace.NORMAL
            "nowrap" -> WhiteSpaceProperty.WhiteSpace.NOWRAP
            "pre" -> WhiteSpaceProperty.WhiteSpace.PRE
            "pre-wrap" -> WhiteSpaceProperty.WhiteSpace.PRE_WRAP
            "pre-line" -> WhiteSpaceProperty.WhiteSpace.PRE_LINE
            "break-spaces" -> WhiteSpaceProperty.WhiteSpace.BREAK_SPACES
            else -> return null
        }
        return WhiteSpaceProperty(whiteSpace)
    }
}

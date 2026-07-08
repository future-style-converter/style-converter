package app.parsing.css.properties.longhands.paging

import app.irmodels.IRProperty
import app.irmodels.properties.paging.BreakInsideProperty
import app.irmodels.properties.paging.BreakInsideValue
import app.parsing.css.properties.longhands.PropertyParser

object BreakInsidePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val breakValue = when (value.trim().lowercase()) {
            "auto" -> BreakInsideValue.AUTO
            "avoid" -> BreakInsideValue.AVOID
            "avoid-page" -> BreakInsideValue.AVOID_PAGE
            "avoid-column" -> BreakInsideValue.AVOID_COLUMN
            "avoid-region" -> BreakInsideValue.AVOID_REGION
            else -> return null
        }
        return BreakInsideProperty(breakValue)
    }
}

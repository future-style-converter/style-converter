package app.parsing.css.properties.longhands.paging

import app.irmodels.IRProperty
import app.irmodels.properties.paging.BreakBeforeProperty
import app.irmodels.properties.paging.BreakValue
import app.parsing.css.properties.longhands.PropertyParser

object BreakBeforePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val breakValue = when (value.trim().lowercase()) {
            "auto" -> BreakValue.AUTO
            "avoid" -> BreakValue.AVOID
            "always" -> BreakValue.ALWAYS
            "all" -> BreakValue.ALL
            "avoid-page" -> BreakValue.AVOID_PAGE
            "page" -> BreakValue.PAGE
            "left" -> BreakValue.LEFT
            "right" -> BreakValue.RIGHT
            "recto" -> BreakValue.RECTO
            "verso" -> BreakValue.VERSO
            "avoid-column" -> BreakValue.AVOID_COLUMN
            "column" -> BreakValue.COLUMN
            "avoid-region" -> BreakValue.AVOID_REGION
            "region" -> BreakValue.REGION
            else -> return null
        }
        return BreakBeforeProperty(breakValue)
    }
}

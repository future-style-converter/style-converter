package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.DominantBaselineProperty
import app.irmodels.properties.typography.DominantBaselineValue
import app.parsing.css.properties.longhands.PropertyParser

object DominantBaselinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val baselineValue = when (trimmed) {
            "auto" -> DominantBaselineValue.AUTO
            "text-bottom" -> DominantBaselineValue.TEXT_BOTTOM
            "alphabetic" -> DominantBaselineValue.ALPHABETIC
            "ideographic" -> DominantBaselineValue.IDEOGRAPHIC
            "middle" -> DominantBaselineValue.MIDDLE
            "central" -> DominantBaselineValue.CENTRAL
            "mathematical" -> DominantBaselineValue.MATHEMATICAL
            "hanging" -> DominantBaselineValue.HANGING
            "text-top" -> DominantBaselineValue.TEXT_TOP
            else -> return null
        }
        return DominantBaselineProperty(baselineValue)
    }
}

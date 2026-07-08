package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.AlignmentBaselineProperty
import app.irmodels.properties.typography.AlignmentBaselineValue
import app.parsing.css.properties.longhands.PropertyParser

object AlignmentBaselinePropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val baselineValue = when (trimmed) {
            "auto" -> AlignmentBaselineValue.AUTO
            "baseline" -> AlignmentBaselineValue.BASELINE
            "before-edge" -> AlignmentBaselineValue.BEFORE_EDGE
            "text-before-edge" -> AlignmentBaselineValue.TEXT_BEFORE_EDGE
            "middle" -> AlignmentBaselineValue.MIDDLE
            "central" -> AlignmentBaselineValue.CENTRAL
            "after-edge" -> AlignmentBaselineValue.AFTER_EDGE
            "text-after-edge" -> AlignmentBaselineValue.TEXT_AFTER_EDGE
            "ideographic" -> AlignmentBaselineValue.IDEOGRAPHIC
            "alphabetic" -> AlignmentBaselineValue.ALPHABETIC
            "hanging" -> AlignmentBaselineValue.HANGING
            "mathematical" -> AlignmentBaselineValue.MATHEMATICAL
            else -> return null
        }
        return AlignmentBaselineProperty(baselineValue)
    }
}

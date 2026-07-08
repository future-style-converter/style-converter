package app.parsing.css.properties.longhands.typography

import app.irmodels.IRProperty
import app.irmodels.properties.typography.TextEmphasisPositionProperty
import app.irmodels.properties.typography.TextEmphasisVertical
import app.irmodels.properties.typography.TextEmphasisHorizontal
import app.parsing.css.properties.longhands.PropertyParser

object TextEmphasisPositionPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val parts = trimmed.split(Regex("\\s+"))

        var vertical: TextEmphasisVertical? = null
        var horizontal: TextEmphasisHorizontal? = null

        for (part in parts) {
            when (part) {
                "over" -> vertical = TextEmphasisVertical.OVER
                "under" -> vertical = TextEmphasisVertical.UNDER
                "left" -> horizontal = TextEmphasisHorizontal.LEFT
                "right" -> horizontal = TextEmphasisHorizontal.RIGHT
            }
        }

        if (vertical == null) return null

        return TextEmphasisPositionProperty(vertical, horizontal)
    }
}

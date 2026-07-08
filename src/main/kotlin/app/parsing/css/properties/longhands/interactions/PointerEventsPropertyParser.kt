package app.parsing.css.properties.longhands.interactions

import app.irmodels.IRProperty
import app.irmodels.properties.interactions.PointerEventsProperty
import app.irmodels.properties.interactions.PointerEventsValue
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object PointerEventsPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        // Handle global keywords first
        if (GlobalKeywords.isGlobalKeyword(trimmed)) {
            return PointerEventsProperty(PointerEventsValue.Keyword(trimmed))
        }

        // Handle var(), env() expressions
        if (ExpressionDetector.startsWithExpression(trimmed)) {
            return PointerEventsProperty(PointerEventsValue.Raw(value.trim()))
        }

        // Handle standard pointer-events values
        val pointerEventsValue: PointerEventsValue = when (trimmed) {
            "auto" -> PointerEventsValue.Auto
            "none" -> PointerEventsValue.None
            "visiblepainted", "visible-painted" -> PointerEventsValue.VisiblePainted
            "visiblefill", "visible-fill" -> PointerEventsValue.VisibleFill
            "visiblestroke", "visible-stroke" -> PointerEventsValue.VisibleStroke
            "visible" -> PointerEventsValue.Visible
            "painted" -> PointerEventsValue.Painted
            "fill" -> PointerEventsValue.Fill
            "stroke" -> PointerEventsValue.Stroke
            "all" -> PointerEventsValue.All
            "bounding-box" -> PointerEventsValue.BoundingBox
            else -> PointerEventsValue.Raw(value.trim())
        }

        return PointerEventsProperty(pointerEventsValue)
    }
}

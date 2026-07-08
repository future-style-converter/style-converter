package app.parsing.css.properties.longhands.layout.flexbox

import app.irmodels.IRProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.irmodels.properties.layout.grid.JustifySelfProperty
import app.irmodels.properties.layout.grid.JustifySelfValue
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

/**
 * Parser for `justify-self` property.
 */
object JustifySelfPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // Handle global keywords
        if (GlobalKeywords.isGlobalKeyword(lower)) {
            return JustifySelfProperty(JustifySelfValue.Keyword(lower))
        }

        // Handle var(), env(), calc() expressions
        if (ExpressionDetector.containsExpression(lower)) {
            return JustifySelfProperty(JustifySelfValue.Raw(trimmed))
        }

        val justifySelf = when (lower) {
            "auto" -> JustifySelfValue.Auto
            "normal" -> JustifySelfValue.Normal
            "stretch" -> JustifySelfValue.Stretch
            "center" -> JustifySelfValue.Center
            "start" -> JustifySelfValue.Start
            "end" -> JustifySelfValue.End
            "flex-start" -> JustifySelfValue.FlexStart
            "flex-end" -> JustifySelfValue.FlexEnd
            "self-start" -> JustifySelfValue.SelfStart
            "self-end" -> JustifySelfValue.SelfEnd
            "left" -> JustifySelfValue.Left
            "right" -> JustifySelfValue.Right
            "baseline" -> JustifySelfValue.Baseline
            "anchor-center" -> JustifySelfValue.AnchorCenter
            else -> JustifySelfValue.Raw(trimmed)
        }

        return JustifySelfProperty(justifySelf)
    }
}

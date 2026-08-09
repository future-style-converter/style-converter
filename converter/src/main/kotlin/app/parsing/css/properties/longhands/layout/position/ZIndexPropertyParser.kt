package app.parsing.css.properties.longhands.layout.position

import app.irmodels.IRProperty
import app.irmodels.properties.layout.position.ZIndex
import app.irmodels.properties.layout.position.ZIndexProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.ExpressionDetector
import app.parsing.css.properties.primitiveParsers.GlobalKeywords
import app.parsing.css.properties.primitiveParsers.NumericCalcEvaluator

object ZIndexPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim()
        val lower = trimmed.lowercase()

        // css-values-4 §10 makes calc() legal wherever an <integer> is, and
        // §10.11 resolves a UNITLESS calculation at computed-value time — so
        // `z-index: calc(3 / 2)` is a concrete stacking level (2: halfway
        // values round towards +∞), not a runtime-dependent value. Folded
        // BEFORE the `when` so only the values NumericCalcEvaluator REFUSES
        // (units, percentages, var(), nested functions) keep the unresolved
        // expression envelope — every refusal falls through byte-identically.
        // MEASURED: WPT css-values/calc-positive-fraction-001 captured an
        // all-RED square against an all-green ref because the unresolved
        // z-index left the green box at `auto` under the red box's explicit 2.
        val folded = NumericCalcEvaluator.evaluate(trimmed)?.let { NumericCalcEvaluator.toIntegerValue(it) }
        if (folded != null) return ZIndexProperty(ZIndex.fromInteger(folded))

        val zIndex = when {
            // Handle 'auto' keyword
            lower == "auto" -> ZIndex.auto()

            // Handle global keywords
            GlobalKeywords.isGlobalKeyword(lower) -> ZIndex.fromGlobalKeyword(lower)

            // Handle expressions (calc, var, etc.)
            ExpressionDetector.containsExpression(lower) -> ZIndex.fromExpression(trimmed)

            // Try parsing as integer
            else -> {
                val intValue = trimmed.toIntOrNull() ?: return null
                ZIndex.fromInteger(intValue)
            }
        }

        return ZIndexProperty(zIndex)
    }
}

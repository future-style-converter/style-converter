package app.parsing.css.properties.longhands.typography

import app.irmodels.IRNumber
import app.irmodels.IRProperty
import app.irmodels.properties.typography.FontMetric
import app.irmodels.properties.typography.FontSizeAdjustProperty
import app.irmodels.properties.typography.FontSizeAdjustValue
import app.parsing.css.properties.longhands.PropertyParser

object FontSizeAdjustPropertyParser : PropertyParser {

    private val metricMap = mapOf(
        "ex-height" to FontMetric.EX_HEIGHT,
        "cap-height" to FontMetric.CAP_HEIGHT,
        "ch-width" to FontMetric.CH_WIDTH,
        "ic-width" to FontMetric.IC_WIDTH,
        "ic-height" to FontMetric.IC_HEIGHT
    )

    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()

        val adjustValue = when (trimmed) {
            "none" -> FontSizeAdjustValue.None
            "from-font" -> FontSizeAdjustValue.FromFont
            "inherit", "initial", "unset", "revert" -> FontSizeAdjustValue.Keyword(trimmed)
            else -> {
                val num = trimmed.toDoubleOrNull()
                if (num != null) {
                    FontSizeAdjustValue.Number(IRNumber(num))
                } else {
                    // Handle metric keywords with values like "ex-height 0.5"
                    parseMetricValue(trimmed) ?: FontSizeAdjustValue.Keyword(trimmed)
                }
            }
        }

        return FontSizeAdjustProperty(adjustValue)
    }

    private fun parseMetricValue(value: String): FontSizeAdjustValue.MetricValue? {
        val parts = value.split("\\s+".toRegex())
        if (parts.size == 2) {
            val metric = metricMap[parts[0]]
            val num = parts[1].toDoubleOrNull()
            if (metric != null && num != null) {
                return FontSizeAdjustValue.MetricValue(metric, IRNumber(num))
            }
        }
        return null
    }
}

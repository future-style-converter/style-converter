package app.parsing.css.properties.longhands.rendering

import app.irmodels.IRNumber
import app.irmodels.IRPercentage
import app.irmodels.IRProperty
import app.irmodels.properties.rendering.ZoomProperty
import app.irmodels.properties.rendering.ZoomValue
import app.parsing.css.properties.longhands.PropertyParser

object ZoomPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val v = when {
            trimmed == "normal" -> ZoomValue.Normal
            trimmed == "reset" -> ZoomValue.Reset
            trimmed.endsWith("%") -> {
                val pct = trimmed.removeSuffix("%").toDoubleOrNull() ?: return null
                ZoomValue.Percentage(IRPercentage(pct))
            }
            else -> {
                val num = trimmed.toDoubleOrNull() ?: return null
                ZoomValue.Number(IRNumber(num))
            }
        }
        return ZoomProperty(v)
    }
}

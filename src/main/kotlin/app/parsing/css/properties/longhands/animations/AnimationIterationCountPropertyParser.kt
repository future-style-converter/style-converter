package app.parsing.css.properties.longhands.animations

import app.irmodels.IRProperty
import app.irmodels.properties.animations.AnimationIterationCountProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.NumberParser

object AnimationIterationCountPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val counts = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            when (part) {
                "infinite" -> AnimationIterationCountProperty.IterationCount.Infinite()
                else -> {
                    val number = NumberParser.parse(part) ?: return null
                    AnimationIterationCountProperty.IterationCount.Number(number)
                }
            }
        }
        if (counts.isEmpty()) return null
        return AnimationIterationCountProperty(counts)
    }
}

package app.parsing.css.properties.longhands.background

import app.irmodels.IRProperty
import app.irmodels.properties.background.BackgroundRepeatProperty
import app.parsing.css.properties.longhands.PropertyParser
import app.parsing.css.properties.primitiveParsers.GlobalKeywords

object BackgroundRepeatPropertyParser : PropertyParser {
    override fun parse(value: String): IRProperty? {
        val trimmed = value.trim().lowercase()
        val repeats = trimmed.split(Regex("\\s*,\\s*")).mapNotNull { part ->
            parseRepeat(part)
        }
        if (repeats.isEmpty()) return null
        return BackgroundRepeatProperty(repeats)
    }

    private fun parseRepeat(value: String): BackgroundRepeatProperty.BackgroundRepeat? {
        // Handle global keywords first
        if (GlobalKeywords.isGlobalKeyword(value)) {
            return BackgroundRepeatProperty.BackgroundRepeat.Keyword(value)
        }

        val parts = value.split(Regex("\\s+"))

        return when {
            parts.size == 1 -> {
                // Handle special repeat-x and repeat-y shorthands
                when (parts[0]) {
                    "repeat-x" -> BackgroundRepeatProperty.BackgroundRepeat.TwoValue(
                        BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.REPEAT,
                        BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.NO_REPEAT
                    )
                    "repeat-y" -> BackgroundRepeatProperty.BackgroundRepeat.TwoValue(
                        BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.NO_REPEAT,
                        BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.REPEAT
                    )
                    else -> {
                        val keyword = when (parts[0]) {
                            "repeat" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatKeyword.REPEAT
                            "space" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatKeyword.SPACE
                            "round" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatKeyword.ROUND
                            "no-repeat" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatKeyword.NO_REPEAT
                            else -> return null
                        }
                        BackgroundRepeatProperty.BackgroundRepeat.OneValue(keyword)
                    }
                }
            }
            parts.size == 2 -> {
                val x = parseRepeatValue(parts[0]) ?: return null
                val y = parseRepeatValue(parts[1]) ?: return null
                BackgroundRepeatProperty.BackgroundRepeat.TwoValue(x, y)
            }
            else -> null
        }
    }

    private fun parseRepeatValue(value: String): BackgroundRepeatProperty.BackgroundRepeat.RepeatValue? {
        return when (value) {
            "repeat" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.REPEAT
            "no-repeat" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.NO_REPEAT
            "space" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.SPACE
            "round" -> BackgroundRepeatProperty.BackgroundRepeat.RepeatValue.ROUND
            else -> null
        }
    }
}
